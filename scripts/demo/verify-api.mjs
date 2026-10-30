import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import {docker} from './runtime.mjs';
export function apiUrl(){
 const port=docker(['port','supplier','8081']).stdout.trim();
 assert.match(port,/^127[.]0[.]0[.]1:[0-9]+$/,'API must publish only on loopback');
 return 'http://'+port;
}
export async function verifyApi(){
 const base=apiUrl(),fixture=JSON.parse(await readFile('demo-systems/supplier-api/fixtures/v1.json','utf8'));
 const env=Object.fromEntries((await readFile(process.env.DEMO_ENV_FILE??'infra/docker/demo.env','utf8')).trim().split(/\r?\n/).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1)];}));
 const headers={authorization:'Bearer '+env.SUPPLIER_API_TOKEN};
 async function request(path,options={}){
  return fetch(base+path,{...options,signal:AbortSignal.timeout(10000)});
 }
 assert.equal((await request('/health/ready')).status,200);
 assert.deepEqual(await(await request('/openapi.json')).json(),JSON.parse(await readFile('demo-systems/supplier-api/openapi.json','utf8')));
 assert.equal((await request('/v1/offers')).status,401);
 assert.equal((await request('/v1/offers',{headers:{authorization:'Bearer invalid'}})).status,401);
 const actual={};
 for(const key of ['suppliers','offers']){
  const rows=[];let total=0;
  for(let page=1;page<=8;page++){
   const r=await request('/v1/'+key+'?pageSize=7&page='+page,{headers});
   assert.equal(r.status,200);const body=await r.json();total=body.total;
   rows.push(...body.items);if(rows.length>=total)break;
  }
  actual[key]=rows;
  assert.equal(rows.length,total);assert.deepEqual(rows,fixture[key],'Actual HTTP snapshot rows');
 }
 assert.equal((await request('/v1/offers?pageSize=51',{headers})).status,400);
 assert.equal((await request('/v1/offers/OF-9999',{headers})).status,404);
 console.log('PASS: real supplier container health, exact OpenAPI, 50 exact paginated rows, 401/400/404 behavior and loopback binding.');
 return actual;
}
if(import.meta.url===pathToFileURL(process.argv[1]).href)await verifyApi();
