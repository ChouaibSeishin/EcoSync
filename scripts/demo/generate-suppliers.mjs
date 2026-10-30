import {SEED,json,artifact} from './fixture-tools.mjs';
const names=['Northstar Components','Cedar Office Supply','Atlas Peripherals','Bluefin Displays','Orchard Printworks','Pioneer Networks','Harbor Storage','Summit Ergonomics','Legacy Accessories','Westwind Wholesale'];
const suppliers=names.map((name,i)=>({supplier_id:'SUP-'+String(i+1).padStart(3,'0'),supplier_code:'MDN-'+String(31+i*7),supplier_name:name,region:['NORTH','SOUTH','WEST'][i%3],active:i<8}));
const offers=Array.from({length:40},(_,i)=>{
 const c=i%27;
 return {offer_id:'OF-'+String(i+1).padStart(4,'0'),catalog_reference:'CAT-'+String(810+c*13).padStart(4,'0'),supplier_id:suppliers[i%10].supplier_id,supplier_sku:'VND-'+(4000+i*23),unit_cost:((799+(i*719+SEED)%15000)/100).toFixed(2),currency_code:['USD','EUR','GBP'][c%3],unit_code:[10,19,25].includes(c)?'BOX':c===26?'M':'EA',minimum_order_quantity:1+i%5*5,available_quantity:i%9===0?0:30+i*7,lead_time_days:2+i%6*3,valid_from:i===27?'2026-10-15T12:00:00Z':'2026-10-01T00:00:00Z',valid_until:c===8?'2026-10-10T00:00:00Z':'2026-11-01T00:00:00Z'};
});
await artifact('demo-systems/supplier-api/fixtures/v1.json',json({fixture_version:'supplier-v1',seed:SEED,suppliers,offers}),process.argv.includes('--check'));
console.log('PASS: reproducible supplier v1: 10 suppliers, 40 offers.');
