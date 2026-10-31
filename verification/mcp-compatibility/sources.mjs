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
  const pending=await invoke('sales_get_order_summary',{...window,status:'PENDING',limit:100});assert.ok(pending.rows.length>0);assert.ok(pending.rows.every(r=>Number(r.realized_revenue)===0));
  evidence.push({source:kind,protocol:snapshot.protocol,server:snapshot.serverInfo,tools:snapshot.tools.map(t=>t.name),revenue:result.rows,checks:['BigInt fixture totals equal SQL results','top-N explicit truncation','pending revenue zero']});
 }else{
  const sku=inventory.items[0].sku,result=await invoke('inventory_get_stock_by_sku',{sku});
  const expected=inventory.stock_levels.filter(s=>s.sku===sku);assert.equal(result.rows.length,expected.length);
  for(const row of result.rows){const source=expected.find(s=>s.stock_id===row.stock_id);assert.equal(row.available_quantity,source.quantity_on_hand-source.quantity_reserved);assert.equal(row.low_stock,row.available_quantity<=source.reorder_threshold);}
  assert.equal((await invoke('inventory_get_stock_by_sku',{sku:sku.toLowerCase()})).rows.length,0);
  const low=await invoke('inventory_list_low_stock',{limit:1});assert.equal(low.rows.length,1);assert.ok(low.truncated);assert.ok(low.nextCursor);
  const wh=await invoke('inventory_get_warehouse_stock',{warehouseId:expected[0].warehouse_id,limit:2});assert.equal(wh.rows.length,2);assert.ok(wh.truncated);
  evidence.push({source:kind,protocol:snapshot.protocol,server:snapshot.serverInfo,tools:snapshot.tools.map(t=>t.name),checks:['fixture quantities and thresholds equal SQL results','case-sensitive SKU','low stock and warehouse pagination']});
 }
 await post('close',{...handle,revoke:true});
 for(const headers of [{},{Authorization:'Bearer invalid'},{Authorization:'Bearer '+input.credential,Origin:'https://untrusted.invalid'}]){
  const response=await fetch(input.endpoint,{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:'{}'});assert.ok([401,403].includes(response.status));
 }
}
console.log(JSON.stringify({observedOn:'2026-10-31',result:'PASS',evidence},null,2));
