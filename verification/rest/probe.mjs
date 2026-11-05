import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
const require=createRequire('/app/package.json');
const {Client,StreamableHTTPClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
import assert from 'node:assert/strict';
import {request as httpRequest} from 'node:http';
let input='';for await(const chunk of process.stdin)input+=chunk;const {cases,args}=JSON.parse(input),checks=[];
for(const c of cases){try{const headers={Authorization:'Bearer '+process.env.REST_GATEWAY_TOKEN,...c.headers};if(c.assertion)headers['X-Ecosync-Delegation']=c.assertion;if(c.noService)delete headers.Authorization;
 if(c.http&&c.headers?.Host){const status=await new Promise((resolve,reject)=>{const req=httpRequest('http://rest-gateway:8094/mcp',{method:'POST',headers:{...headers,'Content-Type':'application/json',Accept:'application/json, text/event-stream'},timeout:5000},res=>{res.resume();resolve(res.statusCode);});req.on('error',reject);req.on('timeout',()=>req.destroy(Error('Deadline')));req.end(JSON.stringify({jsonrpc:'2.0',id:1,method:'tools/list'}));});assert.equal(status,c.http,c.name);}
 else if(c.http){const r=await fetch('http://rest-gateway:8094/mcp',{method:'POST',headers:{...headers,'Content-Type':'application/json',Accept:'application/json, text/event-stream'},body:JSON.stringify({jsonrpc:'2.0',id:1,method:'initialize',params:{protocolVersion:'2025-11-25',capabilities:{},clientInfo:{name:'rest-security-probe',version:'1'}}}),redirect:'manual'});assert.equal(r.status,c.http,c.name);}
 else{const transport=new StreamableHTTPClientTransport(new URL('http://rest-gateway:8094/mcp'),{authProvider:{token:async()=>process.env.REST_GATEWAY_TOKEN},requestInit:{headers,signal:AbortSignal.timeout(20000)}}),client=new Client({name:'rest-wire-proof',version:'1.0.0'},{versionNegotiation:{mode:'legacy'}});try{await client.connect(transport,{timeout:4000});const list=await client.listTools(undefined,{timeout:8000});if(c.success)assert.deepEqual(list.tools.map(t=>t.name).sort(),['rest_describe_operation','rest_invoke_operation']);let result;try{result=await client.callTool({name:'rest_invoke_operation',arguments:args},{timeout:12000});}catch(error){if(c.success||error.code!==-32602)throw error;result={isError:true};}assert.equal(result.isError??false,!c.success,c.name);if(c.success){assert.equal(result.structuredContent.data.records.length,2);const replay=await client.callTool({name:'rest_invoke_operation',arguments:args});assert.equal(replay.isError,true,'assertion replay');}}finally{await Promise.race([transport.terminateSession().catch(()=>{}),new Promise(r=>setTimeout(r,1000))]);await Promise.race([client.close(),new Promise(r=>setTimeout(r,1000))]);}}
 checks.push(c.name);
 }catch(error){console.log(JSON.stringify({result:'FAIL',check:c.name,error:error.name,code:Number.isInteger(error.code)?error.code:null}));process.exit(1);}
}console.log(JSON.stringify({checks,result:'PASS'}));

process.exit(0);
