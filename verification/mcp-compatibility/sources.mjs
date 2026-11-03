import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
const root='/repo';
const sales=JSON.parse(await readFile(root+'/demo-systems/sales-postgres/fixtures/v1.json'));
const inventory=JSON.parse(await readFile(root+'/demo-systems/inventory-mysql/fixtures/v1.json'));
const post=async(path,data)=>{
 const response=await fetch('http://mcp-client:8090/v1/'+path,{method:'POST',headers:{Authorization:'Bearer '+process.env.MCP_INTERNAL_TOKEN,'Content-Type':'application/json'},body:JSON.stringify(data),signal:AbortSignal.timeout(30000)});
 const body=await response.json();assert.equal(response.status,200,JSON.stringify(body));return body;
};
const evidence=[];
for(const kind of ['sales','inventory']){
 const input={organizationId:randomUUID(),integrationId:randomUUID(),configurationVersion:1,configurationFingerprint:'a'.repeat(64),ownerId:randomUUID(),endpoint:'http://'+kind+'-mcp:'+(kind==='sales'?8091:8092)+'/mcp',credential:process.env[kind==='sales'?'SALES_MCP_TOKEN':'INVENTORY_MCP_TOKEN'],protocolPolicy:kind==='sales'?'LEGACY_2025':'MODERN_2026'};
 const snapshot=await post('connect',input),handle={key:snapshot.key,leaseId:snapshot.leaseId};
 assert.equal(snapshot.tools.length,3);assert.ok(snapshot.tools.every(t=>t.supported));
 const invoke=async(name,args)=>{
  const tool=snapshot.tools.find(t=>t.name===name);assert.ok(tool);
  const outcome=await post('invoke',{...handle,name,expectedFingerprint:tool.fingerprint,arguments:args,operationId:randomUUID()});
  assert.notEqual(outcome.result.isError,true);return outcome.result.structuredContent;
 };
 if(kind==='sales'){
  const window={startInclusive:'2026-10-01T00:00:00Z',endExclusive:'2026-11-01T00:00:00Z'};
  const result=await invoke('sales_get_revenue_summary',window);
  const eligible=new Set(sales.orders.filter(o=>o.order_status==='FULFILLED'&&o.ordered_at>=window.startInclusive&&o.ordered_at<window.endExclusive).map(o=>o.order_id));
  const minor=s=>BigInt(s.replace('.',''));
  for(const row of result.rows){
   const total=sales.order_lines.filter(l=>eligible.has(l.order_id)&&l.currency_code===row.currency).reduce((s,l)=>s+BigInt(l.quantity)*minor(l.unit_price)-minor(l.discount_amount),0n);
   assert.equal(minor(row.revenue),total);
  }
  const top=await invoke('sales_get_top_products',{...window,currency:'USD',limit:2});assert.equal(top.rows.length,2);assert.equal(top.truncated,true);
  const totals=new Map();for(const line of sales.order_lines.filter(l=>eligible.has(l.order_id)&&l.currency_code==='USD')){
   const old=totals.get(line.product_id)??{product:line.product_id,revenue:0n,quantity:0};old.revenue+=BigInt(line.quantity)*minor(line.unit_price)-minor(line.discount_amount);old.quantity+=line.quantity;totals.set(line.product_id,old);
  }
  const ranking=[...totals.values()].sort((a,b)=>a.revenue>b.revenue?-1:a.revenue<b.revenue?1:a.product-b.product).slice(0,2);
  for(let n=0;n<2;n++){assert.equal(top.rows[n].product_id,ranking[n].product);assert.equal(minor(top.rows[n].revenue),ranking[n].revenue);assert.equal(top.rows[n].quantity,ranking[n].quantity);assert.equal(top.rows[n].selling_unit,sales.products.find(p=>p.product_id===ranking[n].product).selling_unit);}
  const pending=await invoke('sales_get_order_summary',{...window,status:'PENDING',limit:100});assert.ok(pending.rows.length>0);assert.equal(pending.truncated,false);
  for(const row of pending.rows){const order=sales.orders.find(o=>o.order_id===row.order_id);assert.equal(order.order_status,'PENDING');assert.equal(row.ordered_at,order.ordered_at);assert.ok(order.ordered_at>=window.startInclusive&&order.ordered_at<window.endExclusive);assert.equal(BigInt(row.realized_revenue.replace('.','')),0n);
   const amount=sales.order_lines.filter(l=>l.order_id===row.order_id&&l.currency_code===row.currency).reduce((s,l)=>s+BigInt(l.quantity)*minor(l.unit_price)-minor(l.discount_amount),0n);assert.equal(minor(row.order_amount),amount);}
  evidence.push({source:kind,protocol:snapshot.protocol,server:snapshot.serverInfo,tools:snapshot.tools.map(t=>t.name),revenue:result.rows,checks:['BigInt fixture totals equal SQL results','top-N explicit truncation','pending revenue zero']});
 }else{
  const sku=inventory.items[0].sku,result=await invoke('inventory_get_stock_by_sku',{sku});
  const expected=inventory.stock_levels.filter(s=>s.sku===sku);assert.equal(result.rows.length,expected.length);
  for(const row of result.rows){const source=expected.find(s=>s.stock_id===row.stock_id);assert.equal(row.available_quantity,source.quantity_on_hand-source.quantity_reserved);assert.equal(row.low_stock,row.available_quantity<=source.reorder_threshold);}
  assert.equal((await invoke('inventory_get_stock_by_sku',{sku:sku.toLowerCase()})).rows.length,0);
  const low=await invoke('inventory_list_low_stock',{limit:1});assert.equal(low.rows.length,1);assert.ok(low.truncated);assert.ok(low.nextCursor);
  const eligibleLow=inventory.stock_levels.filter(s=>s.quantity_on_hand-s.quantity_reserved<=s.reorder_threshold&&inventory.items.find(i=>i.sku===s.sku).active&&inventory.warehouses.find(w=>w.warehouse_id===s.warehouse_id).warehouse_status==='OPEN').sort((a,b)=>a.stock_id-b.stock_id);
  assert.equal(low.rows[0].stock_id,eligibleLow[0].stock_id);assert.equal(low.nextCursor,eligibleLow[0].stock_id);
  const wh=await invoke('inventory_get_warehouse_stock',{warehouseId:expected[0].warehouse_id,limit:2});assert.equal(wh.rows.length,2);assert.ok(wh.truncated);
  const warehouseRows=inventory.stock_levels.filter(s=>s.warehouse_id===expected[0].warehouse_id).sort((a,b)=>a.sku<b.sku?-1:a.sku>b.sku?1:0).slice(0,2);assert.deepEqual(wh.rows.map(r=>r.stock_id),warehouseRows.map(r=>r.stock_id));assert.equal(wh.nextCursor,warehouseRows[1].sku);
  evidence.push({source:kind,protocol:snapshot.protocol,server:snapshot.serverInfo,tools:snapshot.tools.map(t=>t.name),checks:['fixture quantities and thresholds equal SQL results','case-sensitive SKU','low stock and warehouse pagination']});
 }
 await post('close',{...handle,revoke:true});
 for(const headers of [{},{Authorization:'Bearer invalid'},{Authorization:'Bearer '+input.credential,Origin:'https://untrusted.invalid'}]){
  const response=await fetch(input.endpoint,{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:'{}'});assert.ok([401,403].includes(response.status));
 }
}
console.log(JSON.stringify({observedOn:'2026-10-31',result:'PASS',evidence},null,2));
