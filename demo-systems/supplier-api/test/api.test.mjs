import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {Ajv} from 'ajv';
import {buildApp,snapshot,contract,utcDate,validateSnapshot} from '../dist/app.js';
const token=randomBytes(32).toString('base64url');
const app=buildApp({token});
const headers={authorization:'Bearer '+token};
before(async()=>{await app.ready();});
after(async()=>{await app.close();});
const get=(url,auth=headers)=>app.inject({url,headers:auth});
function error(response,status,code){
 assert.equal(response.statusCode,status);
 const body=response.json();assert.equal(body.error.code,code);
 assert.equal(typeof body.error.requestId,'string');
 assert.ok(!response.body.includes(token));
 assert.deepEqual(Object.keys(body.error).sort(),['code','message','requestId']);
}
test('public live/ready endpoints and exact versioned OpenAPI',async()=>{
 assert.deepEqual((await get('/health/live',{})).json(),{status:'live',snapshotVersion:'supplier-v1'});
 assert.deepEqual((await get('/health/ready',{})).json(),{status:'ready',snapshotVersion:'supplier-v1'});
 assert.deepEqual((await get('/openapi.json',{})).json(),contract);
});
test('missing, wrong and malformed bearer credentials are rejected before data access',async()=>{
 for(const auth of [{},{authorization:'Bearer invalid'},{authorization:token},{authorization:'bearer '+token}]){
  const r=await get('/v1/offers',auth);error(r,401,'UNAUTHORIZED');assert.match(r.headers['www-authenticate'],/^Bearer/);
 }
});
test('authorized stable suppliers, filter and detail',async()=>{
 const r=await get('/v1/suppliers');assert.equal(r.statusCode,200);
 assert.deepEqual(r.json().items,snapshot.suppliers);assert.equal(r.json().total,10);
 const active=(await get('/v1/suppliers?active=false&region=WEST')).json();
 assert.deepEqual(active.items.map(x=>x.supplier_id),['SUP-009']);
 assert.deepEqual((await get('/v1/suppliers/SUP-001')).json(),snapshot.suppliers[0]);
});
test('exact monetary strings and distinct multiple supplier offers',async()=>{
 const r=await get('/v1/offers?catalogReference=CAT-0810');
 assert.equal(r.statusCode,200);assert.deepEqual(r.json().items.map(x=>x.offer_id),['OF-0001','OF-0028']);
 for(const row of r.json().items)assert.match(row.unit_cost,/^[0-9]+[.][0-9]{2}$/);
 assert.deepEqual((await get('/v1/offers/OF-0001')).json(),snapshot.offers[0]);
});
test('bounded pagination, stable order and beyond-end results',async()=>{
 assert.deepEqual((await get('/v1/offers?page=2&pageSize=5')).json().items.map(x=>x.offer_id),['OF-0006','OF-0007','OF-0008','OF-0009','OF-0010']);
 const max=(await get('/v1/offers?pageSize=50')).json();assert.equal(max.items.length,40);assert.equal(max.total,40);
 const end=(await get('/v1/offers?page=1000000')).json();assert.deepEqual(end.items,[]);assert.equal(end.total,40);
 error(await get('/v1/offers?pageSize=51'),400,'INVALID_INPUT');
});
test('empty results are successful and preserve total',async()=>{
 const r=await get('/v1/offers?supplierId=SUP-999');assert.equal(r.statusCode,200);assert.deepEqual(r.json().items,[]);assert.equal(r.json().total,0);
});
test('unknown resources versus malformed identifiers',async()=>{
 error(await get('/v1/offers/OF-9999'),404,'NOT_FOUND');
 error(await get('/v1/suppliers/SUP-999'),404,'NOT_FOUND');
 error(await get('/v1/offers/invalid'),400,'INVALID_INPUT');
 error(await get('/unknown'),404,'NOT_FOUND');
});
test('strict query inputs reject unknown, duplicates, coercions and invalid dates',async()=>{
 for(const query of ['page=0','page=-1','page=1.2','page=01','page=1e2','page=1000001','pageSize=0','pageSize=999999999999','unknown=yes','page=1&page=2','currency=CAD','unitCode=KG','supplierActive=yes','validAt=2026-02-31T00%3A00%3A00Z','validAt=2026-10-01','supplierId=wrong']){
  error(await get('/v1/offers?'+query),400,'INVALID_INPUT');
 }
 error(await get('/v1/suppliers?active=1'),400,'INVALID_INPUT');
 error(await get('/v1/suppliers?region=unknown'),400,'INVALID_INPUT');
 error(await get('/v1/offers/OF-0001?page=1'),400,'INVALID_INPUT');
});
test('explicit date filters use inclusive start/exclusive end; no wall-clock filtering',async()=>{
 const before=(await get('/v1/offers?catalogReference=CAT-0810&validAt=2026-10-01T00%3A00%3A00Z')).json();
 assert.deepEqual(before.items.map(x=>x.offer_id),['OF-0001']);
 const at=(await get('/v1/offers?catalogReference=CAT-0810&validAt=2026-10-15T12%3A00%3A00Z')).json();
 assert.deepEqual(at.items.map(x=>x.offer_id),['OF-0001','OF-0028']);
 assert.deepEqual((await get('/v1/offers?validAt=2026-11-01T00%3A00%3A00Z')).json().items,[]);
 assert.equal((await get('/v1/offers?pageSize=50')).json().items.length,40);
});
test('supplier activity, currency and unit filters apply explicitly',async()=>{
 const rows=(await get('/v1/offers?supplierActive=true&currency=EUR&unitCode=BOX')).json().items;
 assert.ok(rows.length>0);
 for(const o of rows){assert.equal(o.currency_code,'EUR');assert.equal(o.unit_code,'BOX');assert.ok(snapshot.suppliers.find(s=>s.supplier_id===o.supplier_id).active);}
});
test('all success response bodies conform to the committed schemas',async()=>{
 const ajv=new Ajv({strict:true});ajv.addFormat('date-time',utcDate);
 for(const[name,schema]of Object.entries(contract.components.schemas))ajv.addSchema({...JSON.parse(JSON.stringify(schema).replaceAll('#/components/schemas/','')),$id:name});
 for(const [url,name] of [['/v1/suppliers','SupplierList'],['/v1/offers?pageSize=50','OfferList'],['/v1/suppliers/SUP-001','Supplier'],['/v1/offers/OF-0001','Offer']]){
  const valid=ajv.getSchema(name),response=await get(url);assert.ok(valid(response.json()),JSON.stringify(valid.errors));
 }
});
test('read-only methods, malformed JSON and oversized bodies produce bounded errors',async()=>{
 const r=await app.inject({method:'POST',url:'/v1/offers',headers});error(r,405,'METHOD_NOT_ALLOWED');assert.equal(r.headers.allow,'GET, HEAD');
 error(await app.inject({method:'POST',url:'/v1/offers',headers:{...headers,'content-type':'application/json'},payload:'{bad'}),400,'INVALID_REQUEST');
 error(await app.inject({method:'POST',url:'/v1/offers',headers:{...headers,'content-type':'application/json'},payload:JSON.stringify({value:'x'.repeat(2000)})}),413,'PAYLOAD_TOO_LARGE');
 const long=await get('/v1/offers?x='+'a'.repeat(2100));error(long,414,'URI_TOO_LONG');
});
test('snapshot validation rejects bad FK, duplicate identities, money and validity',()=>{
 assert.ok(validateSnapshot(snapshot));
 for(const change of [
  d=>d.offers[0].supplier_id='SUP-999',
  d=>d.suppliers[1].supplier_id=d.suppliers[0].supplier_id,
  d=>d.suppliers[1].supplier_code=d.suppliers[0].supplier_code,
  d=>d.offers[0].unit_cost='1.234',
  d=>d.offers[0].valid_until='2026-02-31T00:00:00Z',
  d=>d.offers[0].valid_until=d.offers[0].valid_from
 ]){const data=structuredClone(snapshot);change(data);assert.equal(validateSnapshot(data),false);}
});
test('invalid snapshots remain live but fail readiness and data access',async()=>{
 const data=structuredClone(snapshot);data.offers[0].supplier_id='SUP-999';
 const invalid=buildApp({token,data});
 try{
  assert.equal((await invalid.inject('/health/live')).statusCode,200);
  error(await invalid.inject('/health/ready'),503,'NOT_READY');
  error(await invalid.inject({url:'/v1/offers',headers}),503,'NOT_READY');
 }finally{await invalid.close();}
 assert.throws(()=>buildApp({token:''}),/token/);
});
