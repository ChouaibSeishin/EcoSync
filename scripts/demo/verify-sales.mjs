import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import {pg,docker,denied} from './runtime.mjs';
export async function verifySales(){
 const data=JSON.parse(await readFile('demo-systems/sales-postgres/fixtures/v1.json','utf8'));
 for(const table of ['products','customers','orders','order_lines']){
  const rows=JSON.parse(pg('SELECT coalesce(json_agg(t),\'[]\') FROM (SELECT * FROM sales.'+table+' ORDER BY '+Object.keys(data[table][0])[0]+') t;').stdout);
  for(const row of rows){
   for(const key of ['created_at','ordered_at'])if(row[key])row[key]=new Date(row[key]).toISOString().replace('.000Z','Z');
   for(const key of ['unit_price','discount_amount'])if(key in row)row[key]=Number(row[key]).toFixed(2);
  }
  assert.deepEqual(rows,data[table],table+' exact initialized rows');
 }
 for(const sql of [
  'INSERT INTO sales.products SELECT * FROM sales.products WHERE false;',
  'UPDATE sales.products SET active=active WHERE false;',
  'DELETE FROM sales.products WHERE false;',
  'CREATE TABLE sales.reader_test(id int);','CREATE SCHEMA reader_test;',
  'CREATE ROLE reader_test;','SELECT * FROM sales.schema_history;'
 ])denied(pg(sql,'reader',true));
 const bad=docker(['exec','-T','sales','sh','-c','PGPASSWORD=deliberately-invalid psql -X -h 127.0.0.1 -U sales_reader -d sales -Atc "SELECT 1"'],undefined,true);
 denied(bad,/password authentication failed/i);
 const constraints=[
  "UPDATE sales.order_lines SET quantity=0 WHERE line_id=9001;",
  "UPDATE sales.order_lines SET unit_price=-1 WHERE line_id=9001;",
  "UPDATE sales.order_lines SET discount_amount=99999999 WHERE line_id=9001;",
  "UPDATE sales.orders SET order_status='UNKNOWN' WHERE order_id=5001;",
  "UPDATE sales.order_lines SET product_id=999999 WHERE line_id=9001;"
 ];
 for(const sql of constraints)denied(pg('BEGIN;'+sql+'ROLLBACK;','migrator',true),/violates check constraint|violates foreign key constraint/i);
 const summary=pg("SELECT currency_code||':'||sum(quantity*unit_price-discount_amount)::text FROM sales.order_lines l JOIN sales.orders o USING(order_id) WHERE order_status='FULFILLED' AND ordered_at>='2026-10-01T00:00:00Z' AND ordered_at<'2026-11-01T00:00:00Z' GROUP BY currency_code ORDER BY currency_code;").stdout.trim();
 assert.equal(summary.split('\n').length,3);
 console.log('PASS: sales exact rows, 7 reader denials, invalid password, 5 enforced constraints; UTC revenue by currency: '+summary.replaceAll('\n',', '));
}
if(import.meta.url===pathToFileURL(process.argv[1]).href)await verifySales();
