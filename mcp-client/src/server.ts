import {createServer,type IncomingMessage,type ServerResponse} from 'node:http';
import {pathToFileURL} from 'node:url';
import {randomUUID,timingSafeEqual,createHash} from 'node:crypto';
import {DestinationPolicy} from './network.js';
import {ClientManager,type ConnectionInput} from './manager.js';
import {ClientFailure,failureCode} from './errors.js';
export function createClientService(secret:string,endpoints:string[],development:boolean){
 if(!/^[A-Za-z0-9_-]{32,128}$/.test(secret))throw new Error('A generated internal service credential is required.');
 const manager=new ClientManager(new DestinationPolicy(endpoints,development),secret);
 const send=(response:ServerResponse,status:number,body:unknown)=>{
  const payload=JSON.stringify(body);
  response.writeHead(status,{'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'});response.end(payload);
 };
 async function body(request:IncomingMessage):Promise<Record<string,unknown>>{
  if(!request.headers['content-type']?.startsWith('application/json'))throw new ClientFailure('INVALID_REQUEST',415);
  const chunks:Buffer[]=[];let bytes=0;
  for await(const chunk of request){const b=Buffer.from(chunk);bytes+=b.length;if(bytes>65536)throw new ClientFailure('REQUEST_TOO_LARGE',413);chunks.push(b);}
  let parsed:unknown;try{parsed=JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch{throw new ClientFailure('INVALID_REQUEST',400);}
  if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))throw new ClientFailure('INVALID_REQUEST',400);
  return parsed as Record<string,unknown>;
 }
 function exact(value:Record<string,unknown>,allowed:string[],required=allowed){
  if(Object.keys(value).some(k=>!allowed.includes(k))||required.some(k=>!Object.hasOwn(value,k)))throw new ClientFailure('INVALID_REQUEST',400);
 }
 const server=createServer({maxHeaderSize:8192,requestTimeout:35000,headersTimeout:5000,keepAliveTimeout:1000},async(request,response)=>{
  const correlationId=randomUUID(),controller=new AbortController();
  request.on('aborted',()=>controller.abort());response.on('close',()=>{if(!response.writableEnded)controller.abort();});
  try{
   if(request.url==='/health/ready'&&request.method==='GET'){send(response,200,{status:'ready'});return;}
   const supplied=request.headers.authorization??'',expected='Bearer '+secret;
   if(!timingSafeEqual(createHash('sha256').update(supplied).digest(),createHash('sha256').update(expected).digest())){response.setHeader('www-authenticate','Bearer');send(response,401,{error:{code:'UNAUTHORIZED',correlationId}});return;}
   // This interface is backend-to-client-service only, never browser-accessible.
   if(request.headers.origin){send(response,403,{error:{code:'ORIGIN_DENIED',correlationId}});return;}
   if(request.method!=='POST'||!request.url||!['/v1/connect','/v1/discover','/v1/invoke','/v1/close','/v1/cancel'].includes(request.url)){send(response,404,{error:{code:'NOT_FOUND',correlationId}});return;}
   const data=await body(request);let result:unknown;
   if(request.url==='/v1/connect'){
    exact(data,['organizationId','integrationId','configurationVersion','configurationFingerprint','ownerId','endpoint','credential','protocolPolicy']);
    for(const name of ['organizationId','integrationId','configurationFingerprint','ownerId','endpoint','credential','protocolPolicy'])if(typeof data[name]!=='string')throw new ClientFailure('INVALID_REQUEST',400);
    result=await manager.connect(data as unknown as ConnectionInput,controller.signal);
   }else{
    if(typeof data.key!=='string'||data.key.length>100||typeof data.leaseId!=='string'||data.leaseId.length>40)throw new ClientFailure('INVALID_REQUEST',400);
    if(request.url==='/v1/discover'){exact(data,['key','leaseId']);result=await manager.discover(data.key,data.leaseId);}
    else if(request.url==='/v1/close'){exact(data,['key','leaseId','revoke'],['key','leaseId']);if(data.revoke!==undefined&&typeof data.revoke!=='boolean')throw new ClientFailure('INVALID_REQUEST',400);await manager.close(data.key,data.leaseId,data.revoke===true);result={closed:true};}
    else if(request.url==='/v1/cancel'){exact(data,['key','leaseId','operationId']);if(typeof data.operationId!=='string')throw new ClientFailure('INVALID_REQUEST',400);result={cancelled:manager.cancel(data.key,data.leaseId,data.operationId)};}
    else{
     exact(data,['key','leaseId','name','expectedFingerprint','arguments','operationId']);
     if(typeof data.name!=='string'||typeof data.expectedFingerprint!=='string'||typeof data.operationId!=='string')throw new ClientFailure('INVALID_REQUEST',400);
     result=await manager.invoke(data.key,data.leaseId,data.name,data.expectedFingerprint,data.arguments,{signal:controller.signal,operationId:data.operationId as string});
    }
   }
   if(!response.destroyed)send(response,200,result);
  }catch(error){
   const code=failureCode(error),status=error instanceof ClientFailure?error.status:502;
   if(!response.destroyed)send(response,status,{error:{code,correlationId}});
  }
 });
 server.maxConnections=64;server.maxHeadersCount=32;
 return {server,manager,close:async()=>{await manager.shutdown();server.closeAllConnections();await new Promise<void>(resolve=>server.close(()=>resolve()));}};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 const endpoints=(process.env.MCP_ALLOWED_ENDPOINTS??'').split(',').filter(Boolean);
 const app=createClientService(process.env.MCP_INTERNAL_TOKEN??'',endpoints,process.env.MCP_ENVIRONMENT==='development');
 app.server.listen(8090,'0.0.0.0',()=>console.log('Native MCP client service ready on 8090.'));
 for(const signal of ['SIGTERM','SIGINT'])process.on(signal,()=>{void app.close().then(()=>process.exit(0));});
}
