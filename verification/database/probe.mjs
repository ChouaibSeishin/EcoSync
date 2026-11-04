import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {request as httpRequest} from 'node:http';
const require=createRequire('/app/package.json');
const {Client,StreamableHTTPClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
const payload=JSON.parse(readFileSync(0,'utf8')),base='http://database-gateway:8093/mcp',token=process.env.DATABASE_GATEWAY_TOKEN;
const checks=[];
async function connect(assertion,mode='legacy'){
 const client=new Client({name:'ecosync-phase4-official-ts-probe',version:'1.0.0'},{versionNegotiation:mode==='modern'?{mode:{pin:'2026-07-28'}}:{mode:'legacy'}});
 const transport=new StreamableHTTPClientTransport(new URL(base),{authProvider:{token:async()=>token},requestInit:{signal:AbortSignal.timeout(25000),headers:{'X-Ecosync-Delegation':assertion}}});
 try{await client.connect(transport,{timeout:4000});}catch(e){await client.close().catch(()=>{});throw e;}
 return {client,transport,async close(){await Promise.race([transport.terminateSession().catch(()=>{}),new Promise(r=>setTimeout(r,2000))]);await Promise.race([client.close(),new Promise(r=>setTimeout(r,2000))]);}};
}
for(const test of payload.cases){try{
 if(test.kind==='http'){
  const headers={authorization:'Bearer '+token,'X-Ecosync-Delegation':test.assertion,'content-type':'application/json',accept:'application/json, text/event-stream',...test.headers};if(test.noService)delete headers.authorization;if(test.noContext)delete headers['X-Ecosync-Delegation'];
  if(test.headers?.Host){const status=await new Promise((resolve,reject)=>{const r=httpRequest(base,{method:'POST',headers,timeout:5000},res=>{res.resume();resolve(res.statusCode);});r.on('error',reject);r.on('timeout',()=>r.destroy(Error('Probe deadline')));r.end(JSON.stringify({jsonrpc:'2.0',id:1,method:'tools/list'}));});assert.equal(status,test.status,test.name);}else{const response=await fetch(base,{method:'POST',headers,body:JSON.stringify({jsonrpc:'2.0',id:1,method:'tools/list'}),signal:AbortSignal.timeout(5000)});assert.equal(response.status,test.status,test.name);await response.body?.cancel();}
 }else if(test.kind==='modern'){
  let denied=false;try{const c=await connect(test.assertion,'modern');await c.close();}catch{denied=true;}assert.equal(denied,true,test.name);
 }else{
  const c=await connect(test.assertion);try{assert.equal(c.client.getNegotiatedProtocolVersion(),'2025-11-25');const tools=await c.client.listTools(undefined,{timeout:8000});if(test.visible)assert.deepEqual(tools.tools.map(t=>t.name).sort(),['database_describe_table','database_read_rows']);else assert.equal(tools.tools.length,0,test.name+' list');
   if(test.arguments){let result;try{result=await c.client.callTool({name:'database_read_rows',arguments:test.arguments},{timeout:12000});}catch(error){if(test.success||error.code!==-32602)throw error;result={isError:true};}if(test.success){assert.equal(result.isError,false);assert.ok(result.structuredContent.rows.length>0);const replay=await c.client.callTool({name:'database_read_rows',arguments:test.arguments},{timeout:5000});assert.equal(replay.isError,true,'Execution assertion cannot authorize a second call');}else assert.equal(result.isError,true,test.name+' call');}
  }finally{await c.close();}
 }
 checks.push(test.name);
 }catch(error){console.log(JSON.stringify({result:"FAIL",check:test.name,error:error.name,code:Number.isInteger(error.code)?error.code:null}));process.exit(1);}
}
console.log(JSON.stringify({protocol:'2025-11-25',checks,result:'PASS'}));
// This disposable CLI has already asserted every response; bound teardown of stable-SDK streams.
process.exit(0);
