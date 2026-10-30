// Independent reference oracle: reads committed facts; never imports seed generators,
// contacts a model, executes platform connectors or runs a benchmark.
import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const load=async p=>JSON.parse(await readFile(p,'utf8'));
export const sales=await load('demo-systems/sales-postgres/fixtures/v1.json');
export const inventory=await load('demo-systems/inventory-mysql/fixtures/v1.json');
export const supplier=await load('demo-systems/supplier-api/fixtures/v1.json');
export const mappings=await load('evaluation/datasets/v1/mappings/relationships.json');
export const START='2026-10-01T00:00:00Z',END='2026-11-01T00:00:00Z',AS_OF='2026-10-15T12:00:00Z';
export function minor(value){assert.match(value,/^(0|[1-9][0-9]*)[.][0-9]{2}$/);const [a,b]=value.split('.');return BigInt(a)*100n+BigInt(b);}
export function decimal(value){assert.ok(value>=0n);return String(value/100n)+'.'+String(value%100n).padStart(2,'0');}
const provenance=(source,entity,ids)=>({source,entity,identifiers:[...new Set(ids.map(String))].sort((a,b)=>a.localeCompare(b,'en',{numeric:true}))});
function combined(parts){
 const groups=new Map();
 for(const p of parts.flat()) {const key=p.source+'/'+p.entity;const old=groups.get(key)??{...p,identifiers:[]};old.identifiers.push(...p.identifiers);groups.set(key,old);}
 return [...groups.values()].map(p=>provenance(p.source,p.entity,p.identifiers)).sort((a,b)=>(a.source+a.entity).localeCompare(b.source+b.entity,'en'));
}
export function revenue({productId=null,currency=null,start=START,end=END}={}){
 const orders=sales.orders.filter(o=>o.order_status==='FULFILLED'&&o.ordered_at>=start&&o.ordered_at<end);
 const ids=new Set(orders.map(o=>o.order_id));
 const lines=sales.order_lines.filter(l=>ids.has(l.order_id)&&(productId===null||l.product_id===productId)&&(currency===null||l.currency_code===currency));
 const currencies=currency?[currency]:['EUR','GBP','USD'];
 const totals=currencies.map(code=>{
  const rows=lines.filter(l=>l.currency_code===code);
  const amount=rows.reduce((sum,l)=>sum+BigInt(l.quantity)*minor(l.unit_price)-minor(l.discount_amount),0n);
  return {currencyCode:code,revenue:decimal(amount),lineCount:rows.length,...(productId!==null?{quantity:rows.reduce((n,l)=>n+l.quantity,0),unit:sales.products.find(p=>p.product_id===productId).selling_unit}:{}),lineIds:rows.map(l=>l.line_id)};
 });
 return {facts:{productId,startInclusive:start,endExclusive:end,eligibleStatus:'FULFILLED',totals:totals.map(({lineIds,...t})=>t)},calculations:totals.map(t=>({rule:'sum(quantity * unit_price - line_total_discount), integer minor units; FULFILLED only',currencyCode:t.currencyCode,result:t.revenue,lineIds:t.lineIds})),provenance:[provenance('sales','order_lines',lines.map(l=>l.line_id)),provenance('sales','orders',lines.map(l=>l.order_id)),provenance('sales','products',lines.map(l=>l.product_id))]};
}
export function stock(sku){
 const item=inventory.items.find(x=>x.sku===sku);assert.ok(item,'Unknown reference SKU');
 const rows=inventory.stock_levels.filter(x=>x.sku===sku).map(r=>({...r,available_quantity:r.quantity_on_hand-r.quantity_reserved,low_stock:r.quantity_on_hand-r.quantity_reserved<=r.reorder_threshold,warehouse_status:inventory.warehouses.find(w=>w.warehouse_id===r.warehouse_id).warehouse_status}));
 return {facts:{item,warehouseScope:'all snapshot warehouses, including maintenance',stock:rows,totalAvailable:rows.reduce((n,r)=>n+r.available_quantity,0),unit:item.unit_code},calculations:[{rule:'available = on_hand - reserved; low iff available <= per-warehouse threshold',availableByStockId:Object.fromEntries(rows.map(r=>[r.stock_id,r.available_quantity]))}],provenance:[provenance('inventory','items',[sku]),provenance('inventory','stock_levels',rows.map(r=>r.stock_id)),provenance('inventory','warehouses',rows.map(r=>r.warehouse_id))]};
}
export function resolve(productId){
 const records=mappings.records.filter(m=>m.sourceSystem==='sales'&&m.sourceIdentifier===String(productId)&&m.targetSystem==='inventory');
 const approved=records.filter(m=>m.approvalStatus==='APPROVED');
 const target=approved.length===1&&records.every(r=>r.approvalStatus==='APPROVED')?approved[0].targetIdentifier:null;
 return {productId,targetSku:target,mappingIds:records.map(r=>r.mappingId),candidates:[...new Set(records.flatMap(r=>r.targetIdentifier?[r.targetIdentifier]:r.candidates))].sort(),approvalStatuses:[...new Set(records.map(r=>r.approvalStatus))].sort()};
}
export function offersFor(catalog,{at=AS_OF,quantity=1,currency=null,unit=null}={}){
 const rows=supplier.offers.filter(o=>o.catalog_reference===catalog).map(o=>{
  const reasons=[];
  if(!supplier.suppliers.find(s=>s.supplier_id===o.supplier_id).active)reasons.push('INACTIVE_SUPPLIER');
  if(!(o.valid_from<=at&&at<o.valid_until))reasons.push('OUTSIDE_VALIDITY');
  if(quantity<o.minimum_order_quantity)reasons.push('BELOW_MINIMUM_ORDER');
  if(quantity>o.available_quantity)reasons.push('INSUFFICIENT_AVAILABILITY');
  if(currency!==null&&currency!==o.currency_code)reasons.push('INCOMPATIBLE_CURRENCY');
  if(unit!==null&&unit!==o.unit_code)reasons.push('INCOMPATIBLE_UNIT');
  return {...o,eligible:reasons.length===0,exclusionReasons:reasons};
 });
 return {facts:{catalogReference:catalog,asOf:at,requestedQuantity:quantity,currency,unit,offers:rows,eligibleOfferIds:rows.filter(o=>o.eligible).map(o=>o.offer_id),selection:'All qualifying offers; no ranking or cheapest-is-best assumption.'},calculations:[{rule:'active supplier AND valid_from <= asOf < valid_until AND minimum <= quantity <= availability AND compatible currency/unit'}],provenance:[provenance('supplier','offers',rows.map(o=>o.offer_id)),provenance('supplier','suppliers',rows.map(o=>o.supplier_id))]};
}
function bundle(parameters){
 const r=revenue(parameters),match=resolve(parameters.productId);
 const result={facts:{sales:r.facts,identity:match},calculations:[...r.calculations],provenance:[...r.provenance],mappingIds:[...match.mappingIds],status:'COMPLETE',unresolved:[]};
 if(!match.targetSku){result.status='PARTIAL';result.unresolved.push('No single approved inventory identity; retain sales facts and candidates without a guessed join.');return result;}
 const s=stock(match.targetSku);result.facts.inventory=s.facts;result.calculations.push(...s.calculations);result.provenance.push(...s.provenance);
 if(parameters.withOffers){
  const records=mappings.records.filter(m=>m.sourceSystem==='inventory'&&m.sourceIdentifier===match.targetSku&&m.targetSystem==='supplier');
  result.mappingIds.push(...records.map(m=>m.mappingId));
  const approved=records.filter(m=>m.approvalStatus==='APPROVED');
  if(approved.length!==1){result.status='PARTIAL';result.unresolved.push('No approved supplier catalog relationship.');}
  else {const o=offersFor(approved[0].targetIdentifier,{...parameters,unit:s.facts.unit});result.facts.supplier=o.facts;result.calculations.push(...o.calculations);result.provenance.push(...o.provenance);}
 }
 return result;
}
export async function deriveCase(c){
 let r={facts:{},calculations:[],provenance:[],mappingIds:[],status:'COMPLETE',unresolved:[],fault:null,evidenceKind:'fixture-derived'};
 const p=c.parameters;
 switch(c.operation){
 case 'revenue':r={...r,...revenue(p)};break;
 case 'stock':r={...r,...stock(p.sku)};break;
 case 'suppliers':{
  const rows=supplier.suppliers.filter(s=>s.region===p.region&&s.active===p.active);
  r.facts={suppliers:rows};r.provenance=[provenance('supplier','suppliers',rows.map(s=>s.supplier_id))];break;
 }
 case 'bundle':r={...r,...bundle(p)};break;
 case 'match':{
  const m=resolve(p.productId);r.facts=m;r.mappingIds=m.mappingIds;
  r.provenance=[provenance('sales','products',[p.productId])];
  if(!m.targetSku){r.status='NEEDS_REVIEW';r.unresolved=['Missing, ambiguous or conflicting reference cannot be approved by labels.'];}
  else r.provenance.push(provenance('inventory','items',[m.targetSku]));break;
 }
 case 'clarify':
  r.status='NEEDS_CLARIFICATION';
  if(p.kind==='date'){r.evidenceKind='declared-policy';r.unresolved=['Ask for explicit UTC start/end; no current date is anchored in this question.'];}
  if(p.kind==='label'){const rows=sales.products.filter(x=>x.product_name===p.label);r.facts={candidateProductIds:rows.map(x=>x.product_id),label:p.label};r.provenance=[provenance('sales','products',rows.map(x=>x.product_id))];r.unresolved=['Ask which product ID; repeated names are distinct identities.'];}
  if(p.kind==='units'){
   r.facts={availableByUnit:Object.fromEntries(['EA','BOX','M'].map(unit=>[unit,inventory.stock_levels.filter(s=>inventory.items.find(i=>i.sku===s.sku).unit_code===unit).reduce((sum,s)=>sum+s.quantity_on_hand-s.quantity_reserved,0)]))};
   r.provenance=[provenance('inventory','items',inventory.items.map(i=>i.sku)),provenance('inventory','stock_levels',inventory.stock_levels.map(s=>s.stock_id))];r.unresolved=['Choose a unit/scope; there is no approved conversion or mixed-unit overall total.'];
  }break;
 case 'policy':{
  r.evidenceKind='declared-policy';r.fault={type:p.policy,execution:'declared future host policy, not a benchmark run'};
  const policies={
   'missing-bearer':['DENIED',{httpStatus:401,code:'UNAUTHORIZED'},'No source data may be exposed without authentication.'],
   'invalid-bearer':['DENIED',{httpStatus:401,code:'UNAUTHORIZED'},'Invalid credentials do not trigger token disclosure or alternate access.'],
   'sales-write':['DENIED',{databasePermission:'INSERT/UPDATE/DELETE not granted',businessDataChanged:false},'Restricted sales_reader cannot write.'],
   'inventory-ddl':['DENIED',{databasePermission:'CREATE/ALTER/DROP not granted',schemaChanged:false},'Restricted inventory_reader cannot change schema.'],
   'invalid-db-password':['UNAVAILABLE',{authenticated:false},'Authentication failure supplies no sales facts; do not bypass credentials.'],
   'supplier-offline':['PARTIAL',{unavailableSource:'supplier'},'Supplier facts unavailable; preserve authorized inventory facts and disclose the missing dependency.']
  };
  const [status,facts,note]=policies[p.policy];r.status=status;r.facts=facts;r.unresolved=[note];
  if(p.sku){const s=stock(p.sku);r.facts={...r.facts,inventory:s.facts};r.calculations=s.calculations;r.provenance=s.provenance;r.evidenceKind='mixed';}
  break;
 }
 case 'fault':{
  r.status='REFUSED';r.evidenceKind='declared-policy';r.fault={type:p.kind,execution:'fixture policy example only; future tool host absent'};
  if(p.kind==='oversized'){
   const bytes=(await readFile('evaluation/datasets/v1/faults/oversized-response.json')).length;assert.ok(bytes>p.byteLimit);r.facts={wireBytes:bytes,caseByteLimit:p.byteLimit,accept:false};r.unresolved=['Reject response above this explicitly supplied case limit; never present truncated data as complete.'];
  }else if(p.kind==='money'){
   const invalid=await load('evaluation/datasets/v1/faults/invalid-money.json');assert.ok(!/^[0-9]+[.][0-9]{2}$/.test(invalid.unit_cost));r.facts={field:'unit_cost',schemaValid:false,accept:false};r.unresolved=['Reject malformed price; do not coerce or invent a value.'];
  }else{
   const invalid=await readFile('evaluation/datasets/v1/faults/malformed.json.txt','utf8');assert.throws(()=>JSON.parse(invalid));r.facts={jsonValid:false,accept:false};r.unresolved=['Reject truncated JSON without fabricated source facts.'];
  }break;
 }
 case 'inventory-offers':{
  const s=stock(p.sku);r={...r,...s,status:'PARTIAL'};r.mappingIds=mappings.records.filter(m=>m.sourceSystem==='inventory'&&m.sourceIdentifier===p.sku).map(m=>m.mappingId);r.unresolved=['Catalog reference is null and no approved catalog target exists; supplier offers are unresolved.'];break;
 }
 case 'page':{
  const rows=[...supplier.offers].sort((a,b)=>a.offer_id.localeCompare(b.offer_id,'en')).slice((p.page-1)*p.pageSize,p.page*p.pageSize);
  r.facts={page:p.page,pageSize:p.pageSize,total:supplier.offers.length,offerIds:rows.map(o=>o.offer_id)};r.provenance=[provenance('supplier','offers',rows.map(o=>o.offer_id))];break;
 }
 case 'window':{
  const eligible=sales.orders.filter(o=>o.order_status==='FULFILLED'&&o.ordered_at>=START&&o.ordered_at<END);
  r.facts={startInclusive:START,endExclusive:END,eligibleOrderIds:eligible.map(o=>o.order_id),boundaryOrders:sales.orders.slice(0,4).map(o=>({orderId:o.order_id,orderedAt:o.ordered_at,included:eligible.includes(o)})),excludedPending:sales.orders.filter(o=>o.order_status==='PENDING').map(o=>o.order_id),excludedCancelled:sales.orders.filter(o=>o.order_status==='CANCELLED').map(o=>o.order_id)};r.provenance=[provenance('sales','orders',sales.orders.map(o=>o.order_id))];break;
 }
 case 'currency-totals':r={...r,...revenue()};break;
 case 'offer-boundary':{
  const rows=supplier.offers.filter(o=>o.catalog_reference===p.catalog&&o.valid_from<=p.at&&p.at<o.valid_until);
  r.facts={catalogReference:p.catalog,asOf:p.at,offers:rows,scope:'Validity only; availability and supplier activity are not silently filtered.'};r.provenance=[provenance('supplier','offers',rows.map(o=>o.offer_id))];break;
 }
 case 'top-products':{
  r.facts={byCurrency:{},ranking:'Descending exact revenue, tie ascending product ID'};
  for(const code of ['EUR','GBP','USD']){
   const ranked=sales.products.map(product=>({productId:product.product_id,reference:revenue({productId:product.product_id,currency:code})})).sort((a,b)=>{const x=minor(a.reference.facts.totals[0].revenue),y=minor(b.reference.facts.totals[0].revenue);return x===y?a.productId-b.productId:x>y?-1:1;}).slice(0,p.limit);
   r.facts.byCurrency[code]=ranked.map(x=>({productId:x.productId,revenue:x.reference.facts.totals[0].revenue}));
   r.provenance.push(...ranked.flatMap(x=>x.reference.provenance));
  }break;
 }
 default:throw Error('Unknown case operation '+c.operation);
 }
 if(r.mappingIds.length)r.provenance.push(provenance('reference-mappings','relationships',r.mappingIds));
 return {caseId:c.caseId,datasetVersion:c.datasetVersion,mappingVersion:c.mappingVersion,sourceVersions:c.sourceVersions,evidenceKind:r.evidenceKind,expectedSourceFacts:r.facts,expectedCalculations:r.calculations,expectedIdentifiers:{mappingIds:r.mappingIds},expectedProvenance:combined(r.provenance),expectedStatus:r.status,completeness:r.status==='COMPLETE'?'complete':r.status==='PARTIAL'?'partial':r.status==='NEEDS_CLARIFICATION'||r.status==='NEEDS_REVIEW'?'unresolved':'none',permissions:c.permissions,fault:r.fault,unresolvedExplanation:r.unresolved};
}
