import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {deriveCase} from '../../evaluation/oracle/v1.mjs';
const dir='evaluation/datasets/v1/';
const writing=process.argv.includes('--write');
const files=[
 'demo-systems/sales-postgres/fixtures/v1.json','demo-systems/inventory-mysql/fixtures/v1.json','demo-systems/supplier-api/fixtures/v1.json',
 ...['sales-postgres','inventory-mysql'].flatMap(s=>['V001__schema.sql','V002__seed.sql'].map(m=>'demo-systems/'+s+'/migrations/'+m)),
 'demo-systems/supplier-api/openapi.json','demo-systems/supplier-api/package-lock.json','evaluation/oracle/v1.mjs',
 ...['generate-sales','generate-inventory','generate-suppliers','generate-api-contract','prepare-mappings','prepare-cases'].map(n=>'scripts/demo/'+n+'.mjs'),
 ...['mappings/relationships.json','development/cases.json','held-out/cases.json','development/expected.json','held-out/expected.json','schema/case.schema.json','schema/expected.schema.json','schema/mapping.schema.json','faults/oversized-response.json','faults/invalid-money.json','faults/malformed.json.txt'].map(p=>dir+p)
].sort();
async function compareOrWrite(path,data){
 const text=JSON.stringify(data,null,2)+'\n';
 if(writing)await writeFile(path,text);
 else if(await readFile(path,'utf8')!==text)throw Error('Immutable reference mismatch: '+path+'; investigate before changing the baseline.');
}
for(const split of ['development','held-out']){
 const cases=JSON.parse(await readFile(dir+split+'/cases.json','utf8'));
 const expected=[];for(const c of cases)expected.push(await deriveCase(c));
 await compareOrWrite(dir+split+'/expected.json',expected);
}
const hashes={};
for(const path of files)hashes[path]=createHash('sha256').update(await readFile(path)).digest('hex');
await compareOrWrite(dir+'manifest.json',{
 datasetVersion:'1.0.0',mappingVersion:'1.0.0',scenario:'Meridian Office & Electronics (wholly synthetic)',
 sourceFixtures:{sales:'demo-systems/sales-postgres/fixtures/v1.json',inventory:'demo-systems/inventory-mysql/fixtures/v1.json',supplier:'demo-systems/supplier-api/fixtures/v1.json'},
 fixedSeed:20261030,asOf:'2026-10-15T12:00:00Z',
 sourceVersions:{sales:{schema:'V001',seedMigration:'V002',fixture:'sales-v1'},inventory:{schema:'V001',seedMigration:'V002',fixture:'inventory-v1'},supplier:{schema:'OpenAPI-1.0.0',fixture:'supplier-v1'}},
 counts:{salesProducts:34,salesCustomers:18,salesOrders:144,salesOrderLines:432,inventorySkus:30,warehouses:3,stockLevels:90,suppliers:10,offers:40,developmentCases:30,heldOutCases:6},
 developmentCategoryCounts:Object.fromEntries([...new Set(JSON.parse(await readFile(dir+'development/cases.json','utf8')).map(c=>c.category))].map(category=>[category,3])),
 expectedMethod:'Independent deterministic fixture oracle using BigInt minor units; no LLM and no benchmark execution.',
 heldOutPolicy:'Separate artifacts; exclude held-out cases and answers from future prompt/example development. This preparation does not execute scored experiments.',
 policyCases:'Authorization, outage and malformed-tool cases declare future host expectations; source enforcement is tested separately. A 65536-byte limit belongs only to the explicit fault case, not an implemented host policy.',
 hashAlgorithm:'SHA-256',hashes
});
console.log('PASS: '+(writing?'wrote':'independently recomputed and compared')+' 36 explicit expected results and SHA-256 manifest.');
