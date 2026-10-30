import {artifact,json} from './fixture-tools.mjs';
const text={type:'string',minLength:1,maxLength:120};
const id=pattern=>({type:'string',pattern});
const timestamp={type:'string',format:'date-time',pattern:'^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$'};
const unit={type:'string',enum:['EA','BOX','M']};
const currency={type:'string',enum:['USD','EUR','GBP']};
const integer=(min,max=1000000)=>({type:'integer',minimum:min,maximum:max});
const object=properties=>({type:'object',additionalProperties:false,required:Object.keys(properties),properties});
const supplier=object({supplier_id:id('^SUP-[0-9]{3}$'),supplier_code:text,supplier_name:text,region:{type:'string',enum:['NORTH','SOUTH','WEST']},active:{type:'boolean'}});
const offer=object({offer_id:id('^OF-[0-9]{4}$'),catalog_reference:id('^CAT-[0-9]{4}$'),supplier_id:id('^SUP-[0-9]{3}$'),supplier_sku:text,unit_cost:{type:'string',pattern:'^(0|[1-9][0-9]{0,8})[.][0-9]{2}$'},currency_code:currency,unit_code:unit,minimum_order_quantity:integer(1),available_quantity:integer(0),lead_time_days:integer(0,365),valid_from:timestamp,valid_until:timestamp});
const ref=name=>({'$ref':'#/components/schemas/'+name});
const list=name=>object({items:{type:'array',maxItems:50,items:ref(name)},page:integer(1),pageSize:integer(1,50),total:integer(0),snapshotVersion:text});
const error=object({error:object({code:text,message:text,requestId:text})});
const schemas={Supplier:supplier,Offer:offer,SupplierList:list('Supplier'),OfferList:list('Offer'),Error:error,Health:object({status:{type:'string',enum:['live','ready']},snapshotVersion:text})};
const response=name=>({description:name,content:{'application/json':{schema:ref(name)}}});
const params=(properties,detail=false)=>Object.entries(properties).map(([name,schema])=>({name,in:detail?'path':'query',required:detail,schema}));
const pagination={page:{...integer(1),default:1},pageSize:{...integer(1,50),default:10}};
const supplierFilters={active:{type:'boolean'},region:{type:'string',enum:['NORTH','SOUTH','WEST']}};
const offerFilters={supplierId:id('^SUP-[0-9]{3}$'),catalogReference:id('^CAT-[0-9]{4}$'),currency,unitCode:unit,supplierActive:{type:'boolean'},validAt:timestamp};
const paths={};
for(const [resource,name,filters,param,pattern] of [['suppliers','Supplier',supplierFilters,'supplierId','^SUP-[0-9]{3}$'],['offers','Offer',offerFilters,'offerId','^OF-[0-9]{4}$']]){
 const common={security:[{demoBearer:[]}],responses:{400:response('Error'),401:{...response('Error'),headers:{'WWW-Authenticate':{schema:{type:'string'}}}},503:response('Error'),405:response('Error')}};
 paths['/v1/'+resource]={get:{...common,operationId:'list'+name+'s',description:'Ascending stable ID. Unknown or duplicate query parameters are rejected. Beyond-end pages return an empty array.',parameters:params({...pagination,...filters}),responses:{...common.responses,200:response(name+'List')}}};
 paths['/v1/'+resource+'/{'+param+'}']={get:{...common,operationId:'get'+name,parameters:params({[param]:id(pattern)},true),responses:{...common.responses,200:response(name),404:response('Error')}}};
}
for(const health of ['live','ready'])paths['/health/'+health]={get:{operationId:health,security:[],responses:{200:response('Health'),503:response('Error')}}};
paths['/openapi.json']={get:{operationId:'describeApi',security:[],responses:{200:{description:'This versioned OpenAPI document',content:{'application/json':{schema:{type:'object'}}}}}}};
await artifact('demo-systems/supplier-api/openapi.json',json({openapi:'3.0.3',info:{title:'Meridian Supplier Snapshot API',version:'1.0.0',description:'Independent synthetic read-only supplier system. No database connections. Exact money strings; explicit validity filtering.'},servers:[{url:'http://localhost:18081'}],paths,components:{securitySchemes:{demoBearer:{type:'http',scheme:'bearer',description:'Generated local demo token; never embedded in the contract.'}},schemas}}),process.argv.includes('--check'));
console.log('PASS: reproducible OpenAPI 3.0.3 artifact.');
