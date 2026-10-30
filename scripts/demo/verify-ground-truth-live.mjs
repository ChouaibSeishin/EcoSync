import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import {pg,mysql} from './runtime.mjs';
import {sales,inventory,revenue,START,END} from '../../evaluation/oracle/v1.mjs';
export function verifyLiveGroundTruth(){
 for(const [start,end]of [[START,END],['2026-10-10T00:00:00Z','2026-10-20T00:00:00Z']]){
  const query="SELECT json_agg(t ORDER BY product_id,currency_code) FROM (SELECT product_id,currency_code,sum(quantity*unit_price-discount_amount)::text AS revenue,sum(quantity)::integer AS quantity,count(*)::integer AS line_count FROM sales.order_lines JOIN sales.orders USING(order_id) WHERE order_status='FULFILLED' AND ordered_at>='"+start+"' AND ordered_at<'"+end+"' GROUP BY product_id,currency_code) t;";
  const actual=JSON.parse(pg(query).stdout);
  const expected=sales.products.flatMap(p=>revenue({productId:p.product_id,start,end}).facts.totals.filter(t=>t.lineCount>0).map(t=>({product_id:p.product_id,currency_code:t.currencyCode,revenue:t.revenue,quantity:t.quantity,line_count:t.lineCount}))).sort((a,b)=>a.product_id-b.product_id||a.currency_code.localeCompare(b.currency_code,'en'));
  assert.deepEqual(actual,expected,'Independent PostgreSQL NUMERIC vs BigInt oracle, all product/currency cells and explicit UTC range');
 }
 const rows=mysql("SELECT JSON_OBJECT('stock_id',stock_id,'sku',s.sku,'unit_code',i.unit_code,'available',quantity_on_hand-quantity_reserved,'low',quantity_on_hand-quantity_reserved<=reorder_threshold) FROM stock_levels s JOIN items i ON i.sku=s.sku ORDER BY stock_id;").stdout.trim().split('\n').map(s=>JSON.parse(s));
 const expected=inventory.stock_levels.map(s=>({stock_id:s.stock_id,sku:s.sku,unit_code:inventory.items.find(i=>i.sku===s.sku).unit_code,available:s.quantity_on_hand-s.quantity_reserved,low:s.quantity_on_hand-s.quantity_reserved<=s.reorder_threshold}));
 assert.deepEqual(rows,expected,'Independent MySQL arithmetic/low-stock comparison for all 90 rows');
 const boundaries=JSON.parse(pg("SELECT json_agg(t ORDER BY order_id) FROM (SELECT order_id,ordered_at>='2026-10-01T00:00:00Z' AND ordered_at<'2026-11-01T00:00:00Z' AND order_status='FULFILLED' AS included FROM sales.orders WHERE order_id BETWEEN 5001 AND 5004) t;").stdout);
 assert.deepEqual(boundaries,[{order_id:5001,included:false},{order_id:5002,included:true},{order_id:5003,included:true},{order_id:5004,included:false}]);
 console.log('PASS: live PostgreSQL exact currency/product revenue in two UTC windows, all MySQL stock expressions and four inclusive/exclusive boundary orders match the independent oracle.');
}
if(import.meta.url===pathToFileURL(process.argv[1]).href)verifyLiveGroundTruth();
