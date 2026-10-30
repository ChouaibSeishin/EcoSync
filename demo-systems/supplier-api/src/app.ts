import Fastify from 'fastify';
import {Ajv} from 'ajv';
import {readFileSync} from 'node:fs';
import type {ServerResponse} from 'node:http';
import {createHash,timingSafeEqual,randomUUID} from 'node:crypto';

export interface Supplier {supplier_id:string;supplier_code:string;supplier_name:string;region:string;active:boolean}
export interface Offer {offer_id:string;catalog_reference:string;supplier_id:string;supplier_sku:string;unit_cost:string;currency_code:string;unit_code:string;minimum_order_quantity:number;available_quantity:number;lead_time_days:number;valid_from:string;valid_until:string}
export interface Snapshot {fixture_version:string;seed:number;suppliers:Supplier[];offers:Offer[]}
export const contract=JSON.parse(readFileSync(new URL('../openapi.json',import.meta.url),'utf8'));
export const snapshot:Snapshot=JSON.parse(readFileSync(new URL('../fixtures/v1.json',import.meta.url),'utf8'));
export function utcDate(value:string):boolean {
 if(!/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$/.test(value))return false;
 const d=new Date(value);
 return Number.isFinite(d.valueOf())&&d.toISOString()===value.replace('Z','.000Z');
}
const schemas=Object.entries(contract.components.schemas).map(([name,schema])=>({
 ...JSON.parse(JSON.stringify(schema).replaceAll('#/components/schemas/','')),$id:name
}));
export function validateSnapshot(data:Snapshot):boolean {
 const ajv=new Ajv({strict:true,allErrors:false});
 ajv.addFormat('date-time',utcDate);
 for(const schema of schemas)ajv.addSchema(schema);
 const supplier=ajv.getSchema('Supplier')!,offer=ajv.getSchema('Offer')!;
 if(!data||data.fixture_version!=='supplier-v1'||data.seed!==20261030||!Array.isArray(data.suppliers)||!Array.isArray(data.offers)||!data.suppliers.length||!data.offers.length)return false;
 if(!data.suppliers.every(s=>supplier(s))||!data.offers.every(o=>offer(o)))return false;
 const ids=new Set(data.suppliers.map(s=>s.supplier_id));
 return ids.size===data.suppliers.length&&new Set(data.suppliers.map(s=>s.supplier_code)).size===data.suppliers.length&&new Set(data.offers.map(o=>o.offer_id)).size===data.offers.length&&data.offers.every(o=>ids.has(o.supplier_id)&&o.valid_from<o.valid_until);
}
class InputError extends Error {}
const hash=(value:string)=>createHash('sha256').update(value).digest();
export function buildApp(options:{token:string;data?:Snapshot}){
 if(!/^[A-Za-z0-9_-]{32,128}$/.test(options.token))throw Error('A generated demo bearer token is required.');
 const tokenHash=hash(options.token);
 const data:Snapshot=structuredClone(options.data??snapshot);
 const ready=validateSnapshot(data);
 const routerReject=(response:ServerResponse,status:number,code:string)=>{response.writeHead(status,{'content-type':'application/json; charset=utf-8','cache-control':'no-store'});response.end(JSON.stringify({error:{code,message:'Request target is malformed or exceeds router limits.',requestId:randomUUID()}}));};
 const app=Fastify({logger:false,bodyLimit:1024,routerOptions:{maxParamLength:64,onMaxParamLength:(_path,_request,response)=>routerReject(response,414,'URI_TOO_LONG'),onBadUrl:(_path,_request,response)=>routerReject(response,400,'INVALID_REQUEST')},requestTimeout:5000,connectionTimeout:5000,keepAliveTimeout:5000,genReqId:()=>randomUUID(),ajv:{customOptions:{coerceTypes:false,removeAdditional:false}}});
 for(const schema of schemas)app.addSchema(schema);
 const fail=(reply:import('fastify').FastifyReply,status:number,code:string,message:string,requestId:string)=>reply.code(status).send({error:{code,message,requestId}});
 app.setErrorHandler((error,request,reply)=>{
  const status=(error as {statusCode?:number}).statusCode;
  return fail(reply,error instanceof InputError?400:status&&status>=400&&status<500?status:500,error instanceof InputError?'INVALID_INPUT':status===413?'PAYLOAD_TOO_LARGE':status===414?'URI_TOO_LONG':status&&status<500?'INVALID_REQUEST':'INTERNAL_ERROR',error instanceof InputError?'Request parameters do not match the documented contract.':'Request could not be completed.',request.id);
 });
 app.addHook('onRequest',async(request,reply)=>{
  reply.header('Cache-Control','no-store');
  if(request.url.length>2048)return fail(reply,414,'URI_TOO_LONG','Request URL exceeds limit.',request.id);
  if(request.url.startsWith('/v1/')){
   const auth=request.headers.authorization;
   if(typeof auth!=='string'||auth.length>140||!auth.startsWith('Bearer ')||!timingSafeEqual(hash(auth.slice(7)),tokenHash)){
    reply.header('WWW-Authenticate','Bearer realm="meridian-demo"');
    return fail(reply,401,'UNAUTHORIZED','A valid bearer token is required.',request.id);
   }
   if(!ready)return fail(reply,503,'NOT_READY','Supplier snapshot is unavailable.',request.id);
  }
 });
 app.get('/health/live',{schema:{response:{200:{$ref:'Health'}}}},async()=>({status:'live',snapshotVersion:'supplier-v1'}));
 app.get('/health/ready',async(request,reply)=>ready?{status:'ready',snapshotVersion:data.fixture_version}:fail(reply,503,'NOT_READY','Supplier snapshot is unavailable.',request.id));
 app.get('/openapi.json',async()=>contract);
 function query(url:string,allowed:string[]){
  const p=new URL(url,'http://local').searchParams;
  const seen=new Set<string>();
  for(const key of p.keys()){if(!allowed.includes(key)||seen.has(key))throw new InputError();seen.add(key);}
  return p;
 }
 function number(p:URLSearchParams,key:string,defaultValue:number,max:number){
  const v=p.get(key);if(v===null)return defaultValue;
  if(!/^[1-9][0-9]{0,6}$/.test(v)||Number(v)>max)throw new InputError();
  return Number(v);
 }
 function boolean(p:URLSearchParams,key:string){
  const v=p.get(key);if(v!==null&&v!=='true'&&v!=='false')throw new InputError();return v===null?null:v==='true';
 }
 function pattern(p:URLSearchParams,key:string,re:RegExp){
  const v=p.get(key);if(v!==null&&!re.test(v))throw new InputError();return v;
 }
 for(const resource of ['suppliers','offers'] as const){
  const name=resource==='suppliers'?'Supplier':'Offer';
  app.get('/v1/'+resource,{schema:{response:{200:{$ref:name+'List'}}}},async(request)=>{
   const common=['page','pageSize'];
   const p=query(request.url,common.concat(resource==='suppliers'?['active','region']:['supplierId','catalogReference','currency','unitCode','supplierActive','validAt']));
   const page=number(p,'page',1,1000000),pageSize=number(p,'pageSize',10,50);
   let rows:(Supplier|Offer)[];
   if(resource==='suppliers'){
    const active=boolean(p,'active'),region=pattern(p,'region',/^(NORTH|SOUTH|WEST)$/);
    rows=data.suppliers.filter(s=>(active===null||s.active===active)&&(region===null||s.region===region));
   }else{
    const supplierId=pattern(p,'supplierId',/^SUP-[0-9]{3}$/),catalog=pattern(p,'catalogReference',/^CAT-[0-9]{4}$/),currency=pattern(p,'currency',/^(USD|EUR|GBP)$/),unit=pattern(p,'unitCode',/^(EA|BOX|M)$/),active=boolean(p,'supplierActive'),at=p.get('validAt');
    if(at!==null&&!utcDate(at))throw new InputError();
    rows=data.offers.filter(o=>(supplierId===null||o.supplier_id===supplierId)&&(catalog===null||o.catalog_reference===catalog)&&(currency===null||o.currency_code===currency)&&(unit===null||o.unit_code===unit)&&(active===null||data.suppliers.find(s=>s.supplier_id===o.supplier_id)!.active===active)&&(at===null||(o.valid_from<=at&&at<o.valid_until)));
   }
   rows.sort((a,b)=>('supplier_id'in a&&!('offer_id'in a)?a.supplier_id:(a as Offer).offer_id).localeCompare('supplier_id'in b&&!('offer_id'in b)?b.supplier_id:(b as Offer).offer_id,'en'));
   return {items:rows.slice((page-1)*pageSize,page*pageSize),page,pageSize,total:rows.length,snapshotVersion:data.fixture_version};
  });
  app.get<{Params:{id:string}}>('/v1/'+resource+'/:id',{schema:{response:{200:{$ref:name}}}},async(request,reply)=>{
   query(request.url,[]);
   const id=request.params.id;
   if(!(resource==='suppliers'?/^SUP-[0-9]{3}$/:/^OF-[0-9]{4}$/).test(id))throw new InputError();
   const row=resource==='suppliers'?data.suppliers.find(s=>s.supplier_id===id):data.offers.find(o=>o.offer_id===id);
   return row??fail(reply,404,'NOT_FOUND','Resource does not exist.',request.id);
  });
 }
 app.setNotFoundHandler((request,reply)=>{
  const known=/^\/(health\/(live|ready)|openapi[.]json|v1\/(suppliers|offers)(\/[^/]+)?)$/.test(request.url.split('?')[0]!);
  if(known&&!['GET','HEAD'].includes(request.method)){reply.header('Allow','GET, HEAD');return fail(reply,405,'METHOD_NOT_ALLOWED','Only read operations are supported.',request.id);}
  return fail(reply,404,'NOT_FOUND','Resource does not exist.',request.id);
 });
 return app;
}
