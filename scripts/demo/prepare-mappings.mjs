import {readFile} from 'node:fs/promises';
import {artifact,json} from './fixture-tools.mjs';
const sales=JSON.parse(await readFile('demo-systems/sales-postgres/fixtures/v1.json','utf8'));
const inv=JSON.parse(await readFile('demo-systems/inventory-mysql/fixtures/v1.json','utf8'));
const version='1.0.0';
const records=[];
function add(id,sourceSystem,sourceIdentifier,targetSystem,targetIdentifier,candidates,approvalStatus,notes){
 records.push({mappingId:id,mappingVersion:version,sourceSystem,sourceIdentifier:String(sourceIdentifier),targetSystem,targetIdentifier,candidates,approvalStatus,reviewMethod:'Curated synthetic reference assertion; validated identity/unit evidence, never label inference.',notes});
}
sales.products.forEach((p,i)=>{
 if(i<22)add('m-sales-'+p.product_id,'sales',p.product_id,'inventory',inv.items[i].sku,[],'APPROVED','Synthetic product identity is explicitly asserted; equal selling/inventory unit; labels deliberately differ.');
 else if(i===22)add('m-sales-'+p.product_id,'sales',p.product_id,'inventory',null,[inv.items[22].sku,inv.items[23].sku],'AMBIGUOUS','Same label is insufficient; two plausible candidates await review.');
 else if(i===23){
  for(const j of [23,24])add('m-sales-'+p.product_id+'-'+j,'sales',p.product_id,'inventory',inv.items[j].sku,[],'CONFLICT','Competing unapproved records for the same source identifier; neither is usable.');
 }else if(i===28)add('m-sales-'+p.product_id,'sales',p.product_id,'inventory',inv.items[25].sku,[],'REJECTED_UNIT','Sales M and inventory BOX have no approved conversion.');
 else add('m-sales-'+p.product_id,'sales',p.product_id,'inventory',null,[],'MISSING',i===33?'Sales-only travel charger: no inventory identity.':'No reviewed reference relationship; do not guess from product labels.');
});
inv.items.forEach((item,i)=>add('m-catalog-'+item.sku,'inventory',item.sku,'supplier',i<27?item.supplier_catalog_ref:null,[] ,i<27?'APPROVED':'MISSING',i<27?'Explicit synthetic catalog membership; multiple offers are allowed; this does not select a supplier.':item.supplier_catalog_ref===null?'Optional catalog reference is absent.':'Inventory-only item has a catalog reference with no supplier offer in this snapshot.'));
await artifact('evaluation/datasets/v1/mappings/relationships.json',json({mappingVersion:version,records}),process.argv.includes('--check'));
console.log('PASS: mappings reproducible; 22 sales identities and 27 catalog memberships approved; ambiguity/conflict/missing/unit rejections preserved.');
