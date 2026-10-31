import {Client,StreamableHTTPClientTransport} from '@modelcontextprotocol/client';
import {McpServer,createMcpHandler} from '@modelcontextprotocol/server';
import {toNodeHandler} from '@modelcontextprotocol/node';
import * as z from 'zod/v4';
import {createServer} from 'node:http';
import {spawn} from 'node:child_process';
import {randomBytes,timingSafeEqual} from 'node:crypto';
import {once} from 'node:events';
import {mkdir,writeFile} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
const root=fileURLToPath(new URL('../../../',import.meta.url)),out=path.join(root,'.verification/phase3');
await mkdir(out,{recursive:true});
const token=randomBytes(32).toString('base64url'),java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
const jar=path.join(root,'verification/mcp-compatibility/java/target/mcp-compatibility-java-1.0.0.jar');
const wire=[],matrix=[];
let javaProcess,javaLog='';
const handler=createMcpHandler(()=>{
 const server=new McpServer({name:'ecosync-ts-modern-proof',version:'1.0.0'});
 const input=z.object({value:z.string().max(40)});
 server.registerTool('proof_echo',{description:'Disposable interoperability proof.',inputSchema:input,outputSchema:z.object({value:z.string(),amount:z.string()})},async({value})=>({content:[{type:'text',text:'Wire proof'}],structuredContent:{value,amount:'12.34'}}));
 server.registerTool('proof_delay',{description:'Disposable timeout proof.',inputSchema:input},async()=>{await new Promise(r=>setTimeout(r,1500));return{content:[{type:'text',text:'Delay complete'}]};});
 return server;
},{legacy:'reject',keepAliveMs:0,maxRequestBodySize:16384});
const adapter=toNodeHandler({fetch:async request=>{
 const auth=request.headers.get('authorization')??'',expected='Bearer '+token;
 if(auth.length!==expected.length||!timingSafeEqual(Buffer.from(auth),Buffer.from(expected)))return new Response(null,{status:401,headers:{'www-authenticate':'Bearer'}});
 const origin=request.headers.get('origin');
 if(origin&&!['http://localhost','http://127.0.0.1'].includes(origin))return new Response(null,{status:403});
 if(!['127.0.0.1','localhost'].includes(new URL(request.url).hostname))return new Response(null,{status:403});
 return handler.fetch(request);
}});
const nodeServer=createServer((req,res)=>{if(req.url==='/mcp')void adapter(req,res).catch(()=>{if(!res.headersSent)res.writeHead(500);res.end();});else {res.writeHead(404);res.end();}});
await new Promise(r=>nodeServer.listen(0,'127.0.0.1',r));
const modern='http://127.0.0.1:'+nodeServer.address().port;
async function unusedPort(){const s=createServer();await new Promise(r=>s.listen(0,'127.0.0.1',r));const port=s.address().port;await new Promise(r=>s.close(r));return port;}
function processRun(args){
 return new Promise((resolve,reject)=>{
  const p=spawn(java,args,{env:{...process.env,POC_TOKEN:token},windowsHide:true,stdio:['ignore','pipe','pipe']});
  let output='';p.stdout.on('data',b=>{output+=b;if(output.length>50000)output=output.slice(-50000);});p.stderr.on('data',()=>{});
  const timer=setTimeout(()=>{p.kill();reject(Error('Java probe deadline'));},12000);
  p.on('error',e=>{clearTimeout(timer);reject(e);});p.on('close',code=>{clearTimeout(timer);const line=output.split(/\r?\n/).find(l=>l.startsWith('PROBE_JSON='));if(!line)return reject(Error('No Java result; exit '+code));resolve(JSON.parse(line.slice(11)));});
 });
}
async function tsProof(base,era){
 const events=[];
 const tracingFetch=async(input,init)=>{
  const req=new Request(input,init);let body;
  if(req.method==='POST'){try{body=await req.clone().json();}catch{}}
  events.push({httpMethod:req.method,method:body?.method??null,header:req.headers.get('mcp-protocol-version'),meta:body?.params?._meta?.['io.modelcontextprotocol/protocolVersion']??null,session:req.headers.has('mcp-session-id'),authorized:req.headers.has('authorization')});
  return fetch(req,{redirect:'error'});
 };
 const transport=new StreamableHTTPClientTransport(new URL(base+'/mcp'),{authProvider:{token:async()=>token},fetch:tracingFetch,requestInit:{redirect:'error'}});
 const client=new Client({name:'ecosync-official-ts-proof',version:'1.0.0'},{versionNegotiation:{mode:'auto',probe:{timeoutMs:2000,maxRetries:0}}});
 let timedOut=false;
 try{
  await client.connect(transport,{timeout:3000});
  assert.equal(client.getProtocolEra(),era);
  const revision=client.getNegotiatedProtocolVersion();assert.equal(revision,era==='modern'?'2026-07-28':'2025-11-25');
  const tools=await client.listTools(undefined,{timeout:2000});
  assert.deepEqual(tools.tools.map(t=>t.name).sort(),['proof_delay','proof_echo']);
  assert.equal(tools.tools.find(t=>t.name==='proof_echo').inputSchema.type,'object');
  const result=await client.callTool({name:'proof_echo',arguments:{value:'wire'}},{timeout:2000});
  assert.deepEqual(result.structuredContent,{value:'wire',amount:'12.34'});
  try{await client.callTool({name:'proof_delay',arguments:{value:'wire'}},{timeout:150});}catch{timedOut=true;}
  assert.ok(timedOut,'Actual remote request deadline');
  if(era==='modern'){
   const requests=events.filter(e=>e.httpMethod==='POST');
   assert.ok(requests.some(e=>e.method==='server/discover'));
   for(const e of requests){assert.equal(e.header,'2026-07-28');assert.equal(e.meta,e.header);assert.equal(e.session,false);}
  }else assert.ok(events.some(e=>e.method==='initialize'));
  matrix.push({client:'official TypeScript SDK 2.4.0',server:client.getServerVersion()?.name,revision,era,result:'PASS',discoveredTools:tools.tools.length,authenticated:true,timeout:true,structuredContent:true});
 }finally{
  if(era==='legacy')await transport.terminateSession().catch(()=>{});
  await client.close();
  wire.push({era,events});
 }
 const bad=new Client({name:'bad-auth-proof',version:'1.0.0'},{versionNegotiation:{mode:'auto',probe:{timeoutMs:1500,maxRetries:0}}});
 let denied=false;try{await bad.connect(new StreamableHTTPClientTransport(new URL(base+'/mcp'),{authProvider:{token:async()=>randomBytes(32).toString('base64url')},requestInit:{redirect:'error'}}),{timeout:2000});}catch{denied=true;}finally{await bad.close();}
 assert.ok(denied,'Bad bearer rejected');
}
try{
 const port=await unusedPort(),legacy='http://127.0.0.1:'+port;
 javaProcess=spawn(java,['-jar',jar,'--server.address=127.0.0.1','--server.port='+port,'--logging.level.root=WARN'],{env:{...process.env,POC_TOKEN:token},windowsHide:true,stdio:['ignore','pipe','pipe']});
 javaProcess.stdout.on('data',b=>{javaLog=(javaLog+b).slice(-30000);});javaProcess.stderr.on('data',b=>{javaLog=(javaLog+b).slice(-30000);});
 const deadline=Date.now()+60000;let ready=false;
 while(Date.now()<deadline){try{if((await fetch(legacy+'/health',{signal:AbortSignal.timeout(500)})).ok){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,250));}
 assert.ok(ready,'Real Java server starts');
 const old=await processRun(['-jar',jar,'--probe',legacy]);assert.equal(old.supported,true);assert.equal(old.protocol,'2025-11-25');assert.deepEqual(old.structuredContent,{value:'wire',amount:'12.34'});
 matrix.push({client:'official Java SDK 2.1.0',server:'Java SDK 2.1.0 legacy',revision:'2025-11-25',result:'PASS'});
 const incompatible=await processRun(['-jar',jar,'--probe',modern]);assert.equal(incompatible.supported,false);assert.equal(incompatible.stage,'initialize');
 matrix.push({client:'official Java SDK 2.1.0',server:'TypeScript SDK 2.4.0 modern-only',revision:'2026-07-28',result:'UNSUPPORTED',failure:incompatible});
 await tsProof(legacy,'legacy');await tsProof(modern,'modern');
 if(process.env.MCP_FOUNDATION_MODULE){
  const {ClientManager}=await import(pathToFileURL(process.env.MCP_FOUNDATION_MODULE).href);
  const {DestinationPolicy}=await import(pathToFileURL(path.join(path.dirname(process.env.MCP_FOUNDATION_MODULE),'network.js')).href);
  const manager=new ClientManager(new DestinationPolicy([legacy+'/mcp',modern+'/mcp'],true),randomBytes(32).toString('base64url'));
  try{
   for(const [base,era,revision]of [[legacy,'legacy','2025-11-25'],[modern,'modern','2026-07-28']]){
    const connected=await manager.connect({organizationId:crypto.randomUUID(),integrationId:crypto.randomUUID(),configurationVersion:1,configurationFingerprint:'a'.repeat(64),ownerId:crypto.randomUUID(),endpoint:base+'/mcp',credential:token,protocolPolicy:'AUTO_DUAL'});
    const tool=connected.tools.find(t=>t.name==='proof_echo');assert.ok(tool.supported);assert.equal(connected.protocol,revision);
    const call=await manager.invoke(connected.key,connected.leaseId,tool.name,tool.fingerprint,{value:'wire'});
    assert.deepEqual(call.result.structuredContent,{value:'wire',amount:'12.34'});
    await manager.close(connected.key,connected.leaseId,true);
    matrix.push({client:'Selected Ecosync official-SDK client foundation',server:connected.serverInfo.name,revision,era,result:'PASS',authenticated:true,structuredContent:true,closedLease:true});
   }
  }finally{await manager.shutdown();}
 }
 const headers={'authorization':'Bearer '+token,'content-type':'application/json','accept':'application/json, text/event-stream','mcp-protocol-version':'2026-07-28','mcp-method':'server/discover'};
 const envelope={jsonrpc:'2.0',id:'probe',method:'server/discover',params:{_meta:{'io.modelcontextprotocol/protocolVersion':'2026-07-28','io.modelcontextprotocol/clientInfo':{name:'raw-proof',version:'1.0.0'},'io.modelcontextprotocol/clientCapabilities':{}}}};
 const mismatch=await fetch(modern+'/mcp',{method:'POST',headers:{...headers,'mcp-protocol-version':'2025-11-25'},body:JSON.stringify(envelope)});assert.equal(mismatch.status,400);
 assert.equal((await fetch(modern+'/mcp',{headers,method:'GET'})).status,405);
 assert.equal((await fetch(modern+'/mcp',{method:'POST',headers:{...headers,origin:'https://untrusted.invalid'},body:JSON.stringify(envelope)})).status,403);
 await writeFile(path.join(out,'compatibility-proof.json'),JSON.stringify({observedOn:'2026-10-31',matrix,wire,negativeChecks:{javaModernRejected:true,badCredentials:true,timeout:true,headerMismatch:true,invalidOrigin:true,modernGetRemoved:true},scope:'Disposable wire proof, not full MCP conformance or a research benchmark.'},null,2)+'\n');
 console.log(JSON.stringify(matrix,null,2));
 console.log('PASS: actual loopback Java/TypeScript SDK peers, both eras, schemas, structured tools, auth, timeout, metadata, Origin and disconnect.');
}finally{
 await handler.close();nodeServer.closeAllConnections();await new Promise(r=>nodeServer.close(r));
 if(javaProcess){javaProcess.kill();await once(javaProcess,'close').catch(()=>{});}
 await writeFile(path.join(out,'java-proof-server.log'),javaLog);
}
