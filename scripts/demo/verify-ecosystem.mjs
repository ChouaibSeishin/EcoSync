import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {docker,pg,mysql} from './runtime.mjs';
import {verifyApi,apiUrl} from './verify-api.mjs';
const sha=x=>createHash('sha256').update(x).digest('hex');
const config=JSON.parse(docker(['config','--format','json']).stdout);
assert.deepEqual(Object.keys(config.services).sort(),['inventory','sales','supplier']);
const images=JSON.parse(await readFile('config/container-images.json','utf8'));
assert.equal(config.services.sales.image,images.postgres);
assert.equal(config.services.inventory.image,images.mysql);
for(const [name,service]of Object.entries(config.services)){
 assert.equal(service.depends_on,undefined,name+' must not depend on another source');
 assert.ok(service.mem_limit&&service.cpus,'bounded source resources');
 assert.ok(!JSON.stringify(service).includes('ecosync_owner'));
 for(const port of service.ports)assert.equal(port.host_ip,'127.0.0.1');
 assert.deepEqual(Object.keys(service.networks),['sources']);
}
assert.equal(config.services.supplier.user,'1000:1000');
assert.equal(config.services.supplier.read_only,true);
const secretKeys=['SALES_OWNER_PASSWORD','SALES_MIGRATION_PASSWORD','SALES_READER_PASSWORD','INVENTORY_ROOT_PASSWORD','INVENTORY_MIGRATION_PASSWORD','INVENTORY_READER_PASSWORD','SUPPLIER_API_TOKEN'];
const env=Object.fromEntries((await readFile(process.env.DEMO_ENV_FILE??'infra/docker/demo.env','utf8')).trim().split(/\r?\n/).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1)];}));
assert.equal(new Set(secretKeys.map(k=>env[k])).size,7,'Credentials are unique per owner/migrator/reader/API');
assert.ok(secretKeys.every(k=>/^[A-Za-z0-9_-]{32,}$/.test(env[k]??'')));
for(const [source,query]of [['sales',pg],['inventory',mysql]]){
 const history=query('SELECT version,sha256 FROM '+(source==='sales'?'sales.':'')+'schema_history ORDER BY version;','migrator').stdout.trim().split('\n').map(x=>x.split(source==='sales'?'|':'\t'));
 for(const [version,checksum]of history){
  const suffix=version==='V001'?'schema':'seed';
  assert.equal(checksum,sha(await readFile('demo-systems/'+(source==='sales'?'sales-postgres':'inventory-mysql')+'/migrations/'+version+'__'+suffix+'.sql')));
 }
 assert.equal(history.length,2);
}
function fingerprint(source){
 if(source==='sales')return sha(['products','customers','orders','order_lines'].map(table=>pg('SELECT json_agg(t) FROM (SELECT * FROM sales.'+table+' ORDER BY 1) t;').stdout).join(''));
 return sha(['items','warehouses','stock_levels'].map(table=>mysql('SELECT * FROM '+table+' ORDER BY 1;').stdout).join(''));
}
const baseline={sales:fingerprint('sales'),inventory:fingerprint('inventory')};
async function peers(excluded){
 if(excluded!=='sales')assert.equal(pg('SELECT count(*) FROM sales.order_lines;').stdout.trim(),'432');
 if(excluded!=='inventory')assert.equal(mysql('SELECT count(*) FROM stock_levels;').stdout.trim(),'90');
 if(excluded!=='supplier')assert.equal((await fetch(apiUrl()+'/health/ready',{signal:AbortSignal.timeout(10000)})).status,200);
}
for(const service of ['sales','inventory','supplier']){
 console.log('Verifying independent stop/restart: '+service);
 try {docker(['stop',service]);await peers(service);}
 finally {docker(['up','-d','--wait','--wait-timeout','180',service]);}
 if(service!=='supplier')assert.equal(fingerprint(service),baseline[service],service+' persistent business rows');
 else await verifyApi();
}
if(process.argv.includes('--with-platform')){
 const platformEnv=Object.fromEntries((await readFile('infra/docker/.env','utf8')).trim().split(/\r?\n/).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1)];}));
 const base='http://127.0.0.1:'+(platformEnv.API_PORT||'18080');
 try{
  docker(['stop']);
  assert.equal((await fetch(base+'/actuator/health/readiness',{signal:AbortSignal.timeout(10000)})).status,200);
  console.log('PASS: existing platform remains ready with all demo services stopped.');
 }finally{docker(['up','-d','--wait','--wait-timeout','240']);}
}
await peers();
console.log('PASS: independent health/lifecycle, persistent database rows, exact migration checksums, distinct credentials, loopback ports, bounds and non-root API.');
