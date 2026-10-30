import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
import {sales,inventory,supplier,mappings,minor,revenue,stock,AS_OF} from '../../evaluation/oracle/v1.mjs';
const require=createRequire(new URL('../../demo-systems/supplier-api/package.json',import.meta.url));
const {Ajv}=require('ajv'),ajv=new Ajv({strict:true});
const load=async p=>JSON.parse(await readFile(p,'utf8')),dir='evaluation/datasets/v1/';
const unique=(rows,key)=>assert.equal(new Set(rows.map(r=>r[key])).size,rows.length,'Unique '+key);
for(const [rows,key] of [[sales.products,'product_id'],[sales.customers,'customer_id'],[sales.orders,'order_id'],[sales.order_lines,'line_id'],[inventory.items,'sku'],[inventory.warehouses,'warehouse_id'],[inventory.stock_levels,'stock_id'],[supplier.suppliers,'supplier_id'],[supplier.offers,'offer_id'],[mappings.records,'mappingId']])unique(rows,key);
const utc=s=>/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$/.test(s)&&Number.isFinite(new Date(s).valueOf())&&new Date(s).toISOString()===s.replace('Z','.000Z');
for(const p of sales.products){assert.ok(utc(p.created_at));assert.ok(['EA','BOX','M'].includes(p.selling_unit));}
for(const c of sales.customers)assert.ok(utc(c.created_at));
for(const o of sales.orders){assert.ok(sales.customers.some(c=>c.customer_id===o.customer_id));assert.ok(utc(o.ordered_at));assert.ok(['FULFILLED','PENDING','CANCELLED'].includes(o.order_status));}
for(const l of sales.order_lines){
 assert.ok(sales.orders.some(o=>o.order_id===l.order_id));assert.ok(sales.products.some(p=>p.product_id===l.product_id));
 assert.ok(Number.isInteger(l.quantity)&&l.quantity>0);assert.ok(BigInt(l.quantity)*minor(l.unit_price)>=minor(l.discount_amount));assert.ok(['USD','EUR','GBP'].includes(l.currency_code));
}
unique(inventory.stock_levels.map(s=>({pair:s.sku+'/'+s.warehouse_id})),'pair');
for(const s of inventory.stock_levels){
 assert.ok(inventory.items.some(i=>i.sku===s.sku));assert.ok(inventory.warehouses.some(w=>w.warehouse_id===s.warehouse_id));
 for(const k of ['quantity_on_hand','quantity_reserved','reorder_threshold'])assert.ok(Number.isInteger(s[k])&&s[k]>=0);
 assert.ok(s.quantity_reserved<=s.quantity_on_hand);assert.ok(utc(s.last_updated_at));
}
const openapi=await load('demo-systems/supplier-api/openapi.json');
ajv.addFormat('date-time',utc);
for(const name of ['Supplier','Offer'])ajv.addSchema({...openapi.components.schemas[name],$id:name});
for(const s of supplier.suppliers)assert.ok(ajv.getSchema('Supplier')(s));
for(const o of supplier.offers){assert.ok(ajv.getSchema('Offer')(o));assert.ok(supplier.suppliers.some(s=>s.supplier_id===o.supplier_id));assert.ok(o.valid_from<o.valid_until);}
const validators=Object.fromEntries(await Promise.all(['case','expected','mapping'].map(async k=>[k,ajv.compile(await load(dir+'schema/'+k+'.schema.json'))])));
assert.ok(validators.mapping(mappings),JSON.stringify(validators.mapping.errors));
const approvedSalesTargets=[];
for(const m of mappings.records){
 const src=m.sourceSystem==='sales'?sales.products.find(p=>String(p.product_id)===m.sourceIdentifier):inventory.items.find(i=>i.sku===m.sourceIdentifier);
 assert.ok(src,'Mapping source exists');
 if(m.sourceSystem==='sales'){
  assert.equal(m.targetSystem,'inventory');
  for(const id of [...m.candidates,...(m.targetIdentifier?[m.targetIdentifier]:[])])assert.ok(inventory.items.some(i=>i.sku===id));
  if(m.approvalStatus==='APPROVED'){
   assert.equal(src.selling_unit,inventory.items.find(i=>i.sku===m.targetIdentifier).unit_code);
   assert.equal(mappings.records.filter(r=>r.sourceSystem==='sales'&&r.sourceIdentifier===m.sourceIdentifier).length,1);
   approvedSalesTargets.push(m.targetIdentifier);
  }
  if(m.approvalStatus==='REJECTED_UNIT')assert.notEqual(src.selling_unit,inventory.items.find(i=>i.sku===m.targetIdentifier).unit_code);
 }else{
  assert.equal(m.targetSystem,'supplier');
  if(m.approvalStatus==='APPROVED'){assert.equal(m.targetIdentifier,src.supplier_catalog_ref);assert.ok(supplier.offers.some(o=>o.catalog_reference===m.targetIdentifier));}
 }
 if(m.approvalStatus==='MISSING')assert.equal(m.targetIdentifier,null);
 if(m.approvalStatus==='AMBIGUOUS'){assert.equal(m.targetIdentifier,null);assert.ok(m.candidates.length>=2);}
 if(m.approvalStatus==='CONFLICT')assert.equal(mappings.records.filter(r=>r.sourceSystem===m.sourceSystem&&r.sourceIdentifier===m.sourceIdentifier&&r.approvalStatus==='CONFLICT').length,2);
}
assert.equal(approvedSalesTargets.length,22);assert.equal(new Set(approvedSalesTargets).size,22);
assert.equal(inventory.items.filter(i=>!approvedSalesTargets.includes(i.sku)).length,8);
const entities={
 sales:{products:sales.products.map(p=>String(p.product_id)),orders:sales.orders.map(o=>String(o.order_id)),order_lines:sales.order_lines.map(l=>String(l.line_id))},
 inventory:{items:inventory.items.map(i=>i.sku),stock_levels:inventory.stock_levels.map(s=>String(s.stock_id)),warehouses:inventory.warehouses.map(w=>w.warehouse_id)},
 supplier:{suppliers:supplier.suppliers.map(s=>s.supplier_id),offers:supplier.offers.map(o=>o.offer_id)},
 'reference-mappings':{relationships:mappings.records.map(m=>m.mappingId)}
};
const allIds=new Set();
for(const split of ['development','held-out']){
 const cases=await load(dir+split+'/cases.json'),expected=await load(dir+split+'/expected.json');
 assert.equal(cases.length,split==='development'?30:6);assert.equal(expected.length,cases.length);
 const categories={};
 for(const c of cases){
  assert.ok(validators.case(c),JSON.stringify(validators.case.errors));assert.equal(c.split,split);assert.ok(!allIds.has(c.caseId));allIds.add(c.caseId);
  assert.equal(c.expectedRef,split+'/expected.json#'+c.caseId);assert.deepEqual(Object.keys(c.permissions).sort(),[...c.requiredSources].sort());
  categories[c.category]=(categories[c.category]??0)+1;
  const e=expected.find(e=>e.caseId===c.caseId);assert.ok(e);assert.ok(validators.expected(e),JSON.stringify(validators.expected.errors));
  for(const p of e.expectedProvenance)for(const id of p.identifiers)assert.ok(entities[p.source]?.[p.entity]?.includes(id),'Existing provenance '+p.source+'/'+p.entity+'/'+id);
  for(const id of e.expectedIdentifiers.mappingIds)assert.ok(entities['reference-mappings'].relationships.includes(id));
  if(e.expectedStatus!=='COMPLETE')assert.ok(e.unresolvedExplanation.length>0);
 }
 if(split==='development'){assert.equal(Object.keys(categories).length,10);for(const n of Object.values(categories))assert.equal(n,3);}
}
const manifest=await load(dir+'manifest.json');
assert.deepEqual(manifest.counts,{salesProducts:sales.products.length,salesCustomers:sales.customers.length,salesOrders:sales.orders.length,salesOrderLines:sales.order_lines.length,inventorySkus:inventory.items.length,warehouses:inventory.warehouses.length,stockLevels:inventory.stock_levels.length,suppliers:supplier.suppliers.length,offers:supplier.offers.length,developmentCases:30,heldOutCases:6});
assert.ok(sales.products.some(p=>p.description===null));assert.equal(sales.products.filter(p=>p.product_name==='USB-C Dock').length,3);
assert.ok(inventory.items.some(i=>i.supplier_catalog_ref===null));
assert.ok(inventory.stock_levels.some(s=>s.quantity_on_hand===0));assert.ok(inventory.stock_levels.some(s=>s.quantity_reserved>0));
assert.ok(inventory.stock_levels.some(s=>s.quantity_on_hand-s.quantity_reserved<=s.reorder_threshold));assert.ok(inventory.stock_levels.some(s=>s.quantity_on_hand-s.quantity_reserved>s.reorder_threshold));
assert.deepEqual([...new Set(inventory.items.map(i=>i.unit_code))].sort(),['BOX','EA','M']);
assert.equal(stock('WH-700').facts.totalAvailable,2);
assert.ok(minor(revenue({productId:1001,currency:'USD'}).facts.totals[0].revenue)>minor(revenue({productId:1002,currency:'USD'}).facts.totals[0].revenue));
assert.ok(supplier.suppliers.some(s=>!s.active));assert.ok(supplier.offers.some(o=>o.valid_until<AS_OF));assert.ok(supplier.offers.some(o=>o.minimum_order_quantity>1));
assert.ok(supplier.offers.some(o=>supplier.offers.filter(r=>r.catalog_reference===o.catalog_reference).length>1));
console.log('PASS: dataset schemas, 36 unique cases, 10 x 3 development categories, provenance, reference approvals/conflicts/units and semantic edge cases.');
