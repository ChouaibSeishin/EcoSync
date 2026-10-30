import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {randomBytes,createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
import {docker,pg,mysql} from './runtime.mjs';
import {verifySales} from './verify-sales.mjs';
import {verifyInventory} from './verify-inventory.mjs';
import {verifyApi} from './verify-api.mjs';
import {verifyLiveGroundTruth} from './verify-ground-truth-live.mjs';
const keys=['DEMO_PROJECT','DEMO_ENV_FILE','SALES_PORT','INVENTORY_PORT','SUPPLIER_PORT'];
const saved=Object.fromEntries(keys.map(k=>[k,process.env[k]]));
const names=['SALES_OWNER_PASSWORD','SALES_MIGRATION_PASSWORD','SALES_READER_PASSWORD','INVENTORY_ROOT_PASSWORD','INVENTORY_MIGRATION_PASSWORD','INVENTORY_READER_PASSWORD','SUPPLIER_API_TOKEN'];
const canonical=value=>value&&typeof value==='object'?Array.isArray(value)?value.map(canonical):Object.fromEntries(Object.keys(value).sort().map(k=>[k,canonical(value[k])])):value;
const results=[],prefix='ecosync-demo-repro-'+randomBytes(6).toString('hex');
const hash=value=>createHash('sha256').update(value).digest('hex');
try{
 for(const suffix of ['a','b']){
  const project=prefix+'-'+suffix;
  assert.match(project,/^ecosync-demo-repro-[a-f0-9]{12}-[ab]$/);
  const config='infra/docker/.env.demo-repro-'+project;
  await writeFile(config,names.map(n=>n+'='+randomBytes(32).toString('base64url')).join('\n')+'\n',{flag:'wx',mode:0o600});
  process.env.DEMO_PROJECT=project;process.env.DEMO_ENV_FILE=config;
  for(const key of ['SALES_PORT','INVENTORY_PORT','SUPPLIER_PORT'])process.env[key]='0';
  assert.equal(docker(['ps','--all','--quiet']).stdout.trim(),'','New project must have no containers');
  const volumes=[project+'_sales-data',project+'_inventory-data'];
  for(const volume of volumes){
   const r=spawnSync('docker',['volume','inspect',volume],{encoding:'utf8',windowsHide:true,timeout:30000});
   assert.equal(r.status,1,'Fresh proof must not reuse an existing volume');
   assert.match(r.stderr,/no such volume/i);
  }
  console.log('Initializing fresh isolated project '+project+' with new credentials (values withheld).');
  try{
   docker(['up','-d','--no-build','--wait','--wait-timeout','240']);
   const business={sales:await verifySales(),inventory:await verifyInventory(),supplier:await verifyApi()};
   assert.ok(business.sales&&business.inventory&&business.supplier);
   verifyLiveGroundTruth();
   for(const [source,query]of [['sales',pg],['inventory',mysql]]){
    for(const version of ['V001','V002']){
     const file='demo-systems/'+(source==='sales'?'sales-postgres':'inventory-mysql')+'/migrations/'+version+'__'+(version==='V001'?'schema':'seed')+'.sql';
     const actual=query("SELECT sha256 FROM "+(source==='sales'?'sales.':'')+"schema_history WHERE version='"+version+"';",'migrator').stdout.trim();
     assert.equal(actual,hash(await readFile(file)));
    }
   }
   results.push({project,volumes,businessFingerprint:hash(JSON.stringify(canonical(business))),allRowsAndReaderDenialsPassed:true,independentSqlGroundTruthPassed:true});
  }finally{
   // Preserve volumes and ignored credentials for inspection; never use down -v.
   docker(['down']);
  }
 }
 assert.equal(results[0].businessFingerprint,results[1].businessFingerprint,'Two fresh initializations must produce identical actual business snapshots');
 await mkdir('.verification/phase2',{recursive:true});
 await writeFile('.verification/phase2/reproducibility.json',JSON.stringify({verifiedAt:new Date().toISOString(),results},null,2)+'\n');
 console.log('PASS: two fresh projects, independently randomized credentials, identical actual business fingerprint '+results[0].businessFingerprint+'. Test volumes preserved.');
}finally{
 for(const k of keys)if(saved[k]===undefined)delete process.env[k];else process.env[k]=saved[k];
}
