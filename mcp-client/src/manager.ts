import {Client,StreamableHTTPClientTransport} from '@modelcontextprotocol/client';
import {randomUUID} from 'node:crypto';
import {DestinationPolicy,guardedFetch,operations,type OperationContext} from './network.js';
import {ClientFailure,failureCode} from './errors.js';
import {fingerprint,redact,schemaIssue,validateArguments} from './schema.js';
export interface ConnectionInput {organizationId:string;integrationId:string;configurationVersion:number;configurationFingerprint:string;ownerId:string;endpoint:string;credential:string;protocolPolicy:'LEGACY_2025'|'MODERN_2026'|'AUTO_DUAL'}
export interface ToolView {name:string;description:string;inputSchema:Record<string,unknown>;outputSchema?:Record<string,unknown>;annotations?:unknown;metadata?:unknown;fingerprint:string;supported:boolean;unsupportedReason:string|null}
interface Slot {key:string;leaseId:string;input:ConnectionInput;client:Client;transport:StreamableHTTPClientTransport;tools:ToolView[];closed:boolean;active:Set<AbortController>;lastUsed:number;failures:number}
export function connectionKey(input:Pick<ConnectionInput,'organizationId'|'integrationId'|'configurationVersion'>):string{
 const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
 if(!uuid.test(input.organizationId)||!uuid.test(input.integrationId)||!Number.isSafeInteger(input.configurationVersion)||input.configurationVersion<1)throw new ClientFailure('MCP_INVALID_CONTEXT',400);
 return input.organizationId+'/'+input.integrationId+'/'+input.configurationVersion;
}
function boundedTree(value:unknown):void{
 const stack=[{value,depth:0}];let nodes=0;
 while(stack.length){const item=stack.pop()!;if(item.depth>24||++nodes>10000)throw new ClientFailure('MCP_RESPONSE_COMPLEXITY');
  if(item.value&&typeof item.value==='object')for(const child of Object.values(item.value))stack.push({value:child,depth:item.depth+1});
 }
}
export class ClientManager {
 private readonly clients=new Map<string,Slot>();
 private readonly connecting=new Map<string,Promise<unknown>>();
 private readonly retired=new Map<string,number>();
 private readonly pendingOperations=new Map<string,{slot:Slot;context:OperationContext}>();
 private readonly timer:ReturnType<typeof setInterval>;
 constructor(readonly policy:DestinationPolicy,private readonly serviceSecret:string){
  this.timer=setInterval(()=>{for(const[k,t]of this.retired)if(Date.now()-t>300000)this.retired.delete(k);for(const slot of this.clients.values())if(Date.now()-slot.lastUsed>300000)void this.retire(slot);},30000);this.timer.unref();
 }
 private async operation<T>(slot:Slot,callback:(context:OperationContext)=>Promise<T>,timeout=10000,operationId?:string):Promise<T>{
  if(slot.closed)throw new ClientFailure('MCP_CLIENT_RETIRED',409);
  if(slot.active.size>=4)throw new ClientFailure('MCP_CONCURRENCY_LIMIT',429);
  const controller=new AbortController(),context:OperationContext={controller,deadline:Date.now()+timeout,bytes:0,limitExceeded:false,redirected:false,authenticationFailed:false,cancelled:false};
  const operationKey=operationId?slot.key+'/'+operationId:undefined;
  if(operationKey&&this.pendingOperations.has(operationKey))throw new ClientFailure('MCP_OPERATION_EXISTS',409);
  const timer=setTimeout(()=>controller.abort(),timeout);slot.active.add(controller);slot.lastUsed=Date.now();
  if(operationKey)this.pendingOperations.set(operationKey,{slot,context});
  try {return await operations.run(context,()=>callback(context));}
  catch(error){
   slot.failures++;
   if(context.limitExceeded)throw new ClientFailure('MCP_RESPONSE_TOO_LARGE',413);
   if(context.redirected)throw new ClientFailure('MCP_REDIRECT_DENIED',422);
   if(context.authenticationFailed)throw new ClientFailure('MCP_AUTHENTICATION_FAILED',422);
   if(controller.signal.aborted)throw new ClientFailure(slot.closed?'MCP_CLIENT_RETIRED':context.cancelled?'MCP_CANCELLED':'MCP_TIMEOUT',slot.closed||context.cancelled?409:504);
   if(context.transportCode)throw new ClientFailure(context.transportCode,context.transportCode==='MCP_DESTINATION_DENIED'?422:502);
   if(error instanceof ClientFailure)throw error;
   throw new ClientFailure(failureCode(error));
  }finally{clearTimeout(timer);slot.active.delete(controller);if(operationKey)this.pendingOperations.delete(operationKey);}
 }
 private metadata(slot:Slot){
  return {key:slot.key,leaseId:slot.leaseId,protocol:slot.client.getNegotiatedProtocolVersion(),era:slot.client.getProtocolEra(),
   serverInfo:redact(slot.client.getServerVersion(),[slot.input.credential,this.serviceSecret]),serverCapabilities:slot.client.getServerCapabilities(),tools:slot.tools};
 }
 async connect(input:ConnectionInput,signal?:AbortSignal):Promise<unknown>{
  const key=connectionKey(input);
  this.policy.validate(input.endpoint);
  if(!/^[a-f0-9]{64}$/.test(input.configurationFingerprint)||!input.ownerId||input.ownerId.length>80||input.credential.length<16||input.credential.length>2048||/[\r\n]/.test(input.credential))throw new ClientFailure('MCP_INVALID_CONTEXT',400);
  if(!['LEGACY_2025','MODERN_2026','AUTO_DUAL'].includes(input.protocolPolicy))throw new ClientFailure('MCP_UNSUPPORTED_PROTOCOL',422);
  if(this.connecting.has(key))throw new ClientFailure('MCP_CONNECTION_BUSY',409);
  if(this.retired.has(key))throw new ClientFailure('MCP_CLIENT_RETIRED',409);
  const existing=this.clients.get(key);
  if(existing&&!existing.closed&&existing.input.configurationFingerprint===input.configurationFingerprint&&existing.input.ownerId===input.ownerId)return this.metadata(existing);
  if(this.clients.size>=64)throw new ClientFailure('MCP_CLIENT_LIMIT',429);
  if(existing)await this.retire(existing);
  const generation=randomUUID();
  const mode=input.protocolPolicy==='LEGACY_2025'?'legacy':input.protocolPolicy==='MODERN_2026'?{pin:'2026-07-28' as const}:'auto';
  const client=new Client({name:'ecosync-native-mcp-client',version:'0.3.0'},{versionNegotiation:{mode,probe:{timeoutMs:5000,maxRetries:0}},listMaxPages:8,capabilities:{}});
  const transport=new StreamableHTTPClientTransport(new URL(input.endpoint),{authProvider:{token:async()=>input.credential},fetch:guardedFetch(this.policy,input.endpoint),requestInit:{redirect:'error'},reconnectionOptions:{maxRetries:0,initialReconnectionDelay:1000,maxReconnectionDelay:1000,reconnectionDelayGrowFactor:1}});
  const slot:Slot={key,leaseId:generation,input:{...input},client,transport,tools:[],closed:false,active:new Set(),lastUsed:Date.now(),failures:0};
  this.clients.set(key,slot);
  const job=this.operation(slot,async context=>{
   const abort=()=>{context.cancelled=true;context.controller.abort();};if(signal?.aborted)abort();signal?.addEventListener('abort',abort,{once:true});
   try{
    await client.connect(transport,{timeout:5000,signal:context.controller.signal});
    const identity=client.getServerVersion();boundedTree(client.getServerCapabilities());
    if(!identity||identity.name.length>120||identity.version.length>64||Buffer.byteLength(JSON.stringify(client.getServerCapabilities()))>32768)throw new ClientFailure('MCP_INVALID_RESPONSE');
    const protocol=client.getNegotiatedProtocolVersion();
    if(!['2025-11-25','2026-07-28'].includes(protocol??''))throw new ClientFailure('MCP_UNSUPPORTED_PROTOCOL',422);
    if(input.protocolPolicy==='LEGACY_2025'&&protocol!=='2025-11-25'||input.protocolPolicy==='MODERN_2026'&&protocol!=='2026-07-28')throw new ClientFailure('MCP_UNSUPPORTED_PROTOCOL',422);
    await this.discoverWithin(slot,context);
    if(slot.closed||this.retired.has(key))throw new ClientFailure('MCP_CLIENT_RETIRED',409);
    return this.metadata(slot);
   }finally{signal?.removeEventListener('abort',abort);}
  },30000);
  this.connecting.set(key,job);
  try{return await job;}catch(error){await this.retire(slot);throw error;}finally{this.connecting.delete(key);}
 }
 private async discoverWithin(slot:Slot,context:OperationContext):Promise<ToolView[]>{
  const collected:ToolView[]=[],names=new Set<string>(),cursors=new Set<string>();let cursor:string|undefined;
  for(let page=0;page<8;page++){
   const result=await slot.client.request({method:'tools/list',params:cursor===undefined?{}:{cursor}},{timeout:Math.min(10000,Math.max(1,context.deadline-Date.now())),signal:context.controller.signal});
   boundedTree(result);
   for(const raw of result.tools){
    if(collected.length>=100||names.has(raw.name))throw new ClientFailure('MCP_DISCOVERY_LIMIT',422);
    if(!/^[A-Za-z0-9_.:-]{1,128}$/.test(raw.name)||Buffer.byteLength(JSON.stringify(raw))>65536||(raw.description?.length??0)>4096)throw new ClientFailure('MCP_SCHEMA_TOO_LARGE',422);
    names.add(raw.name);
    const safe=redact(raw,[slot.input.credential,this.serviceSecret]) as typeof raw;
    const issue=schemaIssue(safe.inputSchema)??(safe.outputSchema?schemaIssue(safe.outputSchema):null);
    const view:Omit<ToolView,'fingerprint'>={name:safe.name,description:safe.description??'',inputSchema:safe.inputSchema,outputSchema:safe.outputSchema,annotations:safe.annotations,metadata:safe._meta,supported:issue===null,unsupportedReason:issue};
    collected.push({...view,fingerprint:fingerprint(view)});
   }
   if(!result.nextCursor){slot.tools=collected;return collected;}
   if(result.nextCursor.length>512||cursors.has(result.nextCursor))throw new ClientFailure('MCP_DISCOVERY_LIMIT',422);
   cursors.add(result.nextCursor);cursor=result.nextCursor;
  }
  throw new ClientFailure('MCP_DISCOVERY_LIMIT',422);
 }
 private require(key:string,leaseId:string):Slot{
  const slot=this.clients.get(key);if(!slot||slot.closed||slot.leaseId!==leaseId)throw new ClientFailure('MCP_CLIENT_RETIRED',409);return slot;
 }
 async discover(key:string,leaseId:string):Promise<unknown>{
  const slot=this.require(key,leaseId);return this.operation(slot,async c=>{await this.discoverWithin(slot,c);return this.metadata(slot);},30000);
 }
 async invoke(key:string,leaseId:string,name:string,expectedFingerprint:string,args:unknown,options:{signal?:AbortSignal;operationId?:string}={}):Promise<unknown>{
  const slot=this.require(key,leaseId),signal=options.signal;
  if(options.operationId&&!/^[0-9a-f-]{36}$/i.test(options.operationId))throw new ClientFailure('MCP_INVALID_CONTEXT',400);
  return this.operation(slot,async context=>{
   const abort=()=>{context.cancelled=true;context.controller.abort();};if(signal?.aborted)abort();signal?.addEventListener('abort',abort,{once:true});
   try{
    await this.discoverWithin(slot,context);
    const tool=slot.tools.find(t=>t.name===name);
    if(!tool||tool.fingerprint!==expectedFingerprint)throw new ClientFailure('MCP_SCHEMA_CHANGED',409);
    if(!tool.supported)throw new ClientFailure('MCP_SCHEMA_UNSUPPORTED',422);
    validateArguments(tool.inputSchema,args);
    if(slot.closed)throw new ClientFailure('MCP_CLIENT_RETIRED',409);
    const result=await slot.client.callTool({name,arguments:args as Record<string,unknown>},{timeout:10000,signal:context.controller.signal});
    boundedTree(result);
    if(result.isError)throw new ClientFailure('MCP_TOOL_FAILED',422);
    if(tool.outputSchema){if(!result.structuredContent)throw new ClientFailure('MCP_INVALID_RESPONSE');validateArguments(tool.outputSchema,result.structuredContent,1048576);}
    slot.failures=0;
    return {protocol:slot.client.getNegotiatedProtocolVersion(),toolFingerprint:tool.fingerprint,result:redact(result,[slot.input.credential,this.serviceSecret])};
   }finally{signal?.removeEventListener('abort',abort);}
  },30000,options.operationId);
 }
 cancel(key:string,leaseId:string,operationId:string):boolean{
  const slot=this.require(key,leaseId),pending=this.pendingOperations.get(key+'/'+operationId);
  if(!pending||pending.slot!==slot)return false;pending.context.cancelled=true;pending.context.controller.abort();return true;
 }
 async close(key:string,leaseId?:string,revoke=false):Promise<void>{
  if(revoke){if(this.retired.size>=4096)throw new ClientFailure('MCP_CLIENT_LIMIT',429);this.retired.set(key,Date.now());}const slot=this.clients.get(key);if(slot&&(!leaseId||leaseId===slot.leaseId))await this.retire(slot);
 }
 private async retire(slot:Slot):Promise<void>{
  if(slot.closed)return;slot.closed=true;
  for(const controller of slot.active)controller.abort();
  if(this.clients.get(slot.key)===slot)this.clients.delete(slot.key);
  try{await Promise.race([slot.transport.terminateSession().catch(()=>{}),new Promise(r=>setTimeout(r,1000))]);}finally{await slot.client.close().catch(()=>{});}
 }
 async shutdown():Promise<void>{clearInterval(this.timer);await Promise.all([...this.clients.values()].map(slot=>this.retire(slot)));}
 stats(){return{clients:this.clients.size,connecting:this.connecting.size,active:[...this.clients.values()].reduce((n,s)=>n+s.active.size,0)};}
}
