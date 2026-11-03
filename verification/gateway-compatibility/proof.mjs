import {Client,StreamableHTTPClientTransport} from '@modelcontextprotocol/client';
import {generateKeyPairSync,randomBytes,randomUUID,sign,verify} from 'node:crypto';
import {createServer} from 'node:http';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';
const out='/evidence';await mkdir(out,{recursive:true});
const token=randomBytes(32).toString('base64url'),{privateKey,publicKey}=generateKeyPairSync('ed25519');
const orgA=randomUUID(),orgB=randomUUID(),actor=randomUUID(),tableA=randomUUID(),tableB=randomUUID();
const stateFile=out+'/proof-authority.json';
await writeFile(stateFile,JSON.stringify({active:true,members:[orgA],tables:{[tableA]:{org:orgA,value:'source-a'},[tableB]:{org:orgB,value:'source-b'}}}));
function assertion(overrides={}){let now=Math.floor(Date.now()/1000);const p=[{alg:'EdDSA',typ:'JWT'},{iss:'ecosync-platform',aud:'ecosync-db-gateway',sub:actor,org:orgA,iat:now,exp:now+30,jti:randomUUID(),...overrides}].map(o=>Buffer.from(JSON.stringify(o)).toString('base64url')).join('.');return p+'.'+sign(null,Buffer.from(p),privateKey).toString('base64url');}
let authorityUp=true,admissions=0;
const authority=createServer(async(req,res)=>{
 try{
  if(!authorityUp||req.headers.authorization!=='Bearer '+token)throw Error();
  const a=req.headers['x-ecosync-delegation'].split('.');assert.ok(verify(null,Buffer.from(a[0]+'.'+a[1]),publicKey,Buffer.from(a[2],'base64url')));
  const c=JSON.parse(Buffer.from(a[1],'base64url'));const state=JSON.parse(await readFile(stateFile));
  assert.ok(state.active&&state.members.includes(c.org));
  let text='';for await(const chunk of req){text+=chunk;if(text.length>4096)throw Error();}const {table}=JSON.parse(text);
  const result=table?state.tables[table]:null;if(table)assert.equal(result?.org,c.org);
  if(table)admissions++;
  res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify({visible:true,...(result?{value:result.value}:{})}));
 }catch{res.writeHead(403);res.end('{}');}
});await new Promise(r=>authority.listen(0,'127.0.0.1',r));
let processRef,logs='',base,notifications=0;const clients=[];const checks=[];
async function start(){
 const portServer=createServer();await new Promise(r=>portServer.listen(0,'127.0.0.1',r));const port=portServer.address().port;await new Promise(r=>portServer.close(r));base='http://127.0.0.1:'+port;
 processRef=spawn('java',['-jar','verification/gateway-compatibility/java/target/gateway-proof-1.0.0.jar','--server.address=127.0.0.1','--server.port='+port,'--logging.level.root=ERROR'],{env:{...process.env,PROOF_TOKEN:token,PROOF_PUBLIC_KEY:publicKey.export({format:'der',type:'spki'}).toString('base64'),PROOF_AUTHORITY:'http://127.0.0.1:'+authority.address().port},stdio:['ignore','pipe','pipe']});
 processRef.stdout.on('data',b=>logs=(logs+b).slice(-20000));processRef.stderr.on('data',b=>logs=(logs+b).slice(-20000));
 let ready=false;for(let i=0;i<200;i++){try{if((await fetch(base+'/health')).ok){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,150));}assert.ok(ready,'Java gateway proof starts');
 await reconcile(JSON.parse(await readFile(stateFile)).active);
}
async function reconcile(active){const r=await fetch(base+'/proof/reconcile',{method:'POST',headers:{Authorization:'Bearer '+token,'content-type':'application/json'},body:JSON.stringify({active})});assert.equal(r.status,200);}
async function stop(){if(processRef){const p=processRef;p.kill();const timer=setTimeout(()=>p.kill('SIGKILL'),2500);await once(p,'close');clearTimeout(timer);processRef=null;}}
async function client(claims={}){
 const transport=new StreamableHTTPClientTransport(new URL(base+'/mcp'),{authProvider:{token:async()=>token},requestInit:{headers:{'X-Ecosync-Delegation':assertion(claims)}}});
 const c=new Client({name:'phase3-official-client-gateway-proof',version:'1.0.0'},{versionNegotiation:{mode:'legacy',legacyProtocolVersion:'2025-11-25'}});
 await c.connect(transport,{timeout:3000});assert.equal(c.getNegotiatedProtocolVersion(),'2025-11-25');clients.push(c);return c;
}
async function check(name,fn){await fn();checks.push(name);console.log('PASS '+name);}
try{
 await start();const a=await client(),b=await client({org:orgB});
 await check('authenticated legacy HTTP discovery; no tenant schema in catalog',async()=>{const t=await a.listTools();assert.deepEqual(t.tools.map(t=>t.name),['database_read_rows']);assert.ok(!JSON.stringify(t).includes(tableA));assert.ok(!JSON.stringify(t).includes(orgA));});
 await check('tenant-isolated discovery',async()=>assert.equal((await b.listTools()).tools.length,0));
 await check('authorized structured result',async()=>assert.deepEqual((await a.callTool({name:'database_read_rows',arguments:{tableId:tableA}})).structuredContent,{value:'source-a'}));
 await check('cross-tenant call denied independently of list filter',async()=>{const n=admissions;assert.equal((await a.callTool({name:'database_read_rows',arguments:{tableId:tableB}})).isError,true);assert.equal((await b.callTool({name:'database_read_rows',arguments:{tableId:tableA}})).isError,true);assert.equal(admissions,n);});
 await check('missing malformed expired wrong-audience forged delegation; absent service credential; Origin',async()=>{
  for(const headers of [{Authorization:'Bearer '+token},{Authorization:'Bearer '+token,'X-Ecosync-Delegation':'malformed'},{Authorization:'Bearer '+token,'X-Ecosync-Delegation':assertion({exp:1})},{Authorization:'Bearer '+token,'X-Ecosync-Delegation':assertion({aud:'other'})},{Authorization:'Bearer '+token,'X-Ecosync-Delegation':assertion().slice(0,-8)+'bad'},{'X-Ecosync-Delegation':assertion()},{Authorization:'Bearer '+token,'X-Ecosync-Delegation':assertion(),Origin:'https://evil.invalid'}]){
   const r=await fetch(base+'/mcp',{method:'POST',headers:{...headers,'content-type':'application/json',accept:'application/json, text/event-stream'},body:JSON.stringify({jsonrpc:'2.0',id:1,method:'tools/list'})});assert.ok([401,403].includes(r.status));
  }
 });
 await check('authority unavailable fails closed for list and call',async()=>{authorityUp=false;const n=admissions;assert.equal((await a.listTools()).tools.length,0);assert.equal((await a.callTool({name:'database_read_rows',arguments:{tableId:tableA}})).isError,true);assert.equal(admissions,n);authorityUp=true;});
 await check('runtime removal and concurrent discovery/call do not bypass persisted revoke',async()=>{
  let state=JSON.parse(await readFile(stateFile));state.active=false;await writeFile(stateFile,JSON.stringify(state));
  await Promise.all([reconcile(false),...Array.from({length:6},async()=>{assert.equal((await a.listTools()).tools.length,0);try{const r=await a.callTool({name:'database_read_rows',arguments:{tableId:tableA}});assert.equal(r.isError,true);}catch{}})]);assert.equal((await a.listTools()).tools.length,0);
 });
 await check('runtime addition and duplicate reconciliation are idempotent',async()=>{let state=JSON.parse(await readFile(stateFile));state.active=true;await writeFile(stateFile,JSON.stringify(state));await reconcile(true);await reconcile(true);assert.equal((await a.listTools()).tools.length,1);});
 await check('failed registration and absent-tool removal preserve catalog and live authorization',async()=>{for(const operation of ['fail-add','fail-remove']){const r=await fetch(base+'/proof/'+operation,{method:'POST',headers:{Authorization:'Bearer '+token}});assert.equal(r.status,operation==='fail-add'?500:200);assert.equal((await a.listTools()).tools.length,1);assert.equal((await b.listTools()).tools.length,0);}});
 await check('notifications emitted by stable SDK when explicitly enabled',async()=>{
  // Listen on an actual authenticated GET stream; ordinary TS client may reconnect it.
  const transportHeaders={Authorization:'Bearer '+token,'X-Ecosync-Delegation':assertion(),accept:'text/event-stream'};
  const raw=await fetch(base+'/mcp',{method:'POST',headers:{...transportHeaders,accept:'application/json, text/event-stream','content-type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:99,method:'initialize',params:{protocolVersion:'2025-11-25',capabilities:{},clientInfo:{name:'notification-proof',version:'1'}}})});await raw.text();const session=raw.headers.get('mcp-session-id');assert.ok(session);
  const ctrl=new AbortController();let stream='';const consume=fetch(base+'/mcp',{headers:{...transportHeaders,'mcp-session-id':session,'mcp-protocol-version':'2025-11-25'},signal:ctrl.signal}).then(async r=>{for await(const chunk of r.body){stream+=Buffer.from(chunk).toString();}}).catch(()=>{});
  await new Promise(r=>setTimeout(r,100));await reconcile(false);await reconcile(true);for(let i=0;i<30&&!stream.includes('notifications/tools/list_changed');i++)await new Promise(r=>setTimeout(r,50));ctrl.abort();await consume;assert.ok(stream.includes('notifications/tools/list_changed'));notifications++;
 });
 await check('restart reconstructs approved catalog from persistent authority',async()=>{await stop();for(const c of clients)await Promise.race([c.close(),new Promise(r=>setTimeout(r,2000))]);await start();const fresh=await client();assert.equal((await fresh.listTools()).tools.length,1);assert.equal((await fresh.callTool({name:'database_read_rows',arguments:{tableId:tableA}})).structuredContent.value,'source-a');});
 await writeFile(out+'/gateway-proof.json',JSON.stringify({observedOn:new Date().toISOString(),sdk:'2.1.0',boot:'4.1.2',springAi:'2.0.2',protocol:'2025-11-25',checks,notifications,claims:'Tools interoperability only. No modern protocol claim. Separate persistent proof authority, not a source DB connector.'},null,2)+'\n');
 console.log('PASS '+checks.length+' real gateway compatibility checks');
}finally{await stop();for(const c of clients)await Promise.race([c.close().catch(()=>{}),new Promise(r=>setTimeout(r,2000))]);authority.closeAllConnections();await new Promise(r=>authority.close(r));await writeFile(out+'/gateway-proof.log',logs);}
