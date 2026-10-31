import assert from 'node:assert/strict';
import {request} from 'node:http';
for(const kind of ['sales','inventory']){
 const endpoint='http://'+kind+'-mcp:'+(kind==='sales'?8091:8092)+'/mcp',auth='Bearer '+process.env[kind==='sales'?'SALES_MCP_TOKEN':'INVENTORY_MCP_TOKEN'];
 for(const [label,headers] of [['missing',{}],['invalid',{Authorization:'Bearer invalid'}],['Origin',{Authorization:auth,Origin:'https://bad.invalid'}]]){
  const r=await fetch(endpoint,{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:'{}'});console.log(kind+' '+label+' '+r.status);assert.ok([401,403].includes(r.status),kind+' '+label+' rejected');
 }
 const hostStatus=await new Promise((resolve,reject)=>{
  const req=request(endpoint,{method:'POST',headers:{Authorization:auth,Host:'bad.invalid','Content-Type':'application/json','Content-Length':'2'}},response=>{response.resume();resolve(response.statusCode);});req.on('error',reject);req.end('{}');
 });console.log(kind+' Host '+hostStatus);assert.equal(hostStatus,403);
 const malformed=await fetch(endpoint,{method:'POST',headers:{Authorization:auth,'Content-Type':'application/json',Accept:'application/json, text/event-stream'},body:'{malformed'});console.log(kind+' malformed JSON '+malformed.status);assert.ok(malformed.status>=400&&malformed.status<500,kind+' malformed JSON rejected');
}
