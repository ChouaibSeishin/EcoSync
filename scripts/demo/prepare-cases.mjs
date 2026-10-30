import {artifact,json} from './fixture-tools.mjs';
const version='1.0.0';
const sk=i=>'WH-'+(700+i*17%47);
const categories=['single-source','two-source','three-source','identifier-matching','ambiguous','unauthorized','unavailable-invalid-credentials','malformed-oversized','partial-results','complexity-result-size'];
const defs=[
 ['What was product 1001 realized USD revenue during October 2026 UTC?','revenue',{productId:1001,currency:'USD'},['sales']],
 ['How much WH-700 stock is available per warehouse and in total EA?','stock',{sku:sk(0)},['inventory']],
 ['Which active suppliers operate in NORTH?','suppliers',{region:'NORTH',active:true},['supplier']],
 ['Compare October revenue and stock for product 1001 using reviewed mappings.','bundle',{productId:1001},['sales','inventory']],
 ['Compare October revenue and stock for product 1002 using reviewed mappings.','bundle',{productId:1002},['sales','inventory']],
 ['Give October revenue and stock for sales-only product 1034; explain missing stock.','bundle',{productId:1034},['sales','inventory']],
 ['Show October USD sales, stock and all eligible offers for product 1001, order quantity 10, at the fixed snapshot.','bundle',{productId:1001,currency:'USD',quantity:10,withOffers:true},['sales','inventory','supplier']],
 ['Show October GBP sales, stock and eligible offers for product 1003, order quantity 20, at the fixed snapshot.','bundle',{productId:1003,currency:'GBP',quantity:20,withOffers:true},['sales','inventory','supplier']],
 ['Show October GBP sales, stock and offers for product 1009, including why no offer is eligible at the snapshot.','bundle',{productId:1009,currency:'GBP',quantity:30,withOffers:true},['sales','inventory','supplier']],
 ['What reviewed inventory identity represents sales product 1007?','match',{productId:1007},['sales','inventory']],
 ['Can sales product 1034 be resolved to a stock SKU?','match',{productId:1034},['sales','inventory']],
 ['Resolve competing references for sales product 1024 without choosing arbitrarily.','match',{productId:1024},['sales','inventory']],
 ['What are this month sales? No current date or timezone was supplied.','clarify',{kind:'date'},['sales']],
 ['Which exact sales product does USB-C Dock identify?','clarify',{kind:'label',label:'USB-C Dock'},['sales']],
 ['How much inventory is there overall when units differ?','clarify',{kind:'units'},['inventory']],
 ['List offers with no bearer credential.','policy',{policy:'missing-bearer'},['supplier']],
 ['Update a sales product using sales_reader.','policy',{policy:'sales-write'},['sales']],
 ['Create an inventory table using inventory_reader.','policy',{policy:'inventory-ddl'},['inventory']],
 ['Return known WH-700 stock when the supplier source is offline.','policy',{policy:'supplier-offline',sku:sk(0)},['inventory','supplier']],
 ['Query sales using an invalid database password.','policy',{policy:'invalid-db-password'},['sales']],
 ['List offers with an invalid bearer token.','policy',{policy:'invalid-bearer'},['supplier']],
 ['Handle a hypothetical tool response exceeding this case limit of 65536 UTF-8 bytes.','fault',{kind:'oversized',byteLimit:65536},['supplier']],
 ['Handle a response with a malformed monetary decimal string.','fault',{kind:'money'},['supplier']],
 ['Handle a truncated JSON source response.','fault',{kind:'json'},['supplier']],
 ['Return sales facts for ambiguous product 1023 and explain unresolved inventory candidates.','bundle',{productId:1023},['sales','inventory']],
 ['Return sales for 1034 even though no reviewed inventory relationship exists.','bundle',{productId:1034},['sales','inventory']],
 ['Show stock for inventory-only cleaning SKU with no catalog reference and explain unavailable offers.','inventory-offers',{sku:sk(29)},['inventory','supplier']],
 ['Return the first five stable offer IDs and total result count.','page',{page:1,pageSize:5},['supplier']],
 ['List revenue-eligible order IDs in October UTC and classify the four timestamp boundary orders.','window',{},['sales']],
 ['Compute total October realized revenue with each currency kept separate.','currency-totals',{},['sales']]
];
const held=[
 ['What is product 1011 realized EUR revenue from October 10 inclusive to October 20 exclusive UTC?','revenue',{productId:1011,currency:'EUR',start:'2026-10-10T00:00:00Z',end:'2026-10-20T00:00:00Z'},['sales']],
 ['Show stock totals for the A4 paper carton in BOX without converting to EA.','stock',{sku:sk(10)},['inventory']],
 ['List catalog CAT-0810 offers valid exactly at October 1 UTC, including zero availability.','offer-boundary',{catalog:'CAT-0810',at:'2026-10-01T00:00:00Z'},['supplier']],
 ['Compare October sales and three-warehouse stock for approved product 1022.','bundle',{productId:1022},['sales','inventory']],
 ['Give sales for product 1024 while keeping competing stock candidates unresolved.','bundle',{productId:1024},['sales','inventory']],
 ['Give the top three products by October revenue separately in each currency; break ties by product ID.','top-products',{limit:3},['sales']]
];
const build=(rows,split)=>rows.map(([question,operation,parameters,requiredSources],i)=>({
 caseId:(split==='development'?'D':'H')+String(i+1).padStart(2,'0'),split,category:split==='development'?categories[Math.floor(i/3)]:['single-source','single-source','single-source','two-source','partial-results','complexity-result-size'][i],
 question,operation,parameters,requiredSources,permissions:Object.fromEntries(requiredSources.map(s=>[s,s==='sales'?(parameters.policy==='invalid-db-password'?'invalid-password':'sales_reader'):s==='inventory'?'inventory_reader':parameters.policy==='missing-bearer'?'unauthenticated':parameters.policy==='invalid-bearer'?'invalid-bearer':'bearer-read'])),
 datasetVersion:version,mappingVersion:version,sourceVersions:{sales:'sales-v1',inventory:'inventory-v1',supplier:'supplier-v1'},
 expectedRef:(split==='development'?'development':'held-out')+'/expected.json#'+(split==='development'?'D':'H')+String(i+1).padStart(2,'0')
}));
const check=process.argv.includes('--check');
await artifact('evaluation/datasets/v1/development/cases.json',json(build(defs,'development')),check);
await artifact('evaluation/datasets/v1/held-out/cases.json',json(build(held,'held-out')),check);
await artifact('evaluation/datasets/v1/faults/oversized-response.json',json({untrustedData:'x'.repeat(65537)}),check);
await artifact('evaluation/datasets/v1/faults/invalid-money.json',json({offer_id:'OF-0001',unit_cost:'12.3USD'}),check);
await artifact('evaluation/datasets/v1/faults/malformed.json.txt','{"items":[\n',check);
console.log('PASS: 30 development cases (3/category) and 6 separately held-out cases; reproducible fault inputs.');
