import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import {mysql,docker,denied} from './runtime.mjs';
export async function verifyInventory(){
 const actual={};
 const data=JSON.parse(await readFile('demo-systems/inventory-mysql/fixtures/v1.json','utf8'));
 for(const table of ['items','warehouses','stock_levels']){
  const keys=Object.keys(data[table][0]);
  const fields=keys.map(k=>"'"+k+"',"+(k==='last_updated_at'?"DATE_FORMAT(last_updated_at,'%Y-%m-%dT%H:%i:%sZ')":k)).join(',');
  const rows=mysql('SELECT JSON_OBJECT('+fields+') FROM '+table+' ORDER BY '+keys[0]+';').stdout.trim().split('\n').map(x=>JSON.parse(x));
  for(const r of rows)if('active'in r)r.active=Boolean(r.active);
  const order=(a,b)=>typeof a[keys[0]]==='number'?a[keys[0]]-b[keys[0]]:a[keys[0]].localeCompare(b[keys[0]],'en');
  actual[table]=rows.sort(order);
  assert.deepEqual(rows.sort(order),[...data[table]].sort(order),table+' exact initialized rows');
 }
 for(const sql of [
  'INSERT INTO items SELECT * FROM items WHERE false;',
  'UPDATE items SET active=active WHERE false;','DELETE FROM items WHERE false;',
  'CREATE TABLE reader_test(id int);','CREATE DATABASE reader_test;',
  "CREATE USER 'reader_test'@'%';",'SELECT * FROM schema_history;','SELECT * FROM mysql.user;'
 ])denied(mysql(sql,'reader',true));
 denied(docker(['exec','-T','inventory','sh','-c','MYSQL_PWD=deliberately-invalid mysql --protocol=TCP -h 127.0.0.1 -u inventory_reader -e "SELECT 1"'],undefined,true));
 for(const sql of [
  'UPDATE stock_levels SET quantity_on_hand=-1 WHERE stock_id=3001;',
  'UPDATE stock_levels SET quantity_reserved=99999 WHERE stock_id=3001;',
  'UPDATE stock_levels SET reorder_threshold=-1 WHERE stock_id=3001;',
  "UPDATE stock_levels SET sku='missing-sku' WHERE stock_id=3001;",
  "UPDATE items SET unit_code='UNKNOWN' WHERE sku='WH-700';"
 ])denied(mysql('START TRANSACTION;'+sql+'ROLLBACK;','migrator',true),/check constraint|foreign key constraint/i);
 assert.equal(mysql("SELECT SUM(quantity_on_hand-quantity_reserved) FROM stock_levels WHERE sku='WH-700';").stdout.trim(),'2');
 console.log('PASS: inventory 123 exact rows, 8 reader denials, invalid password, 5 enforced CHECK/FK constraints; dock stock 2 EA across 3 warehouses.');
 return actual;
}
if(import.meta.url===pathToFileURL(process.argv[1]).href)await verifyInventory();
