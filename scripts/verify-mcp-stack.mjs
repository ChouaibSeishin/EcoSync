import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
const parse=async path=>Object.fromEntries((await readFile(path,'utf8')).split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1)];}));
const local=await parse('infra/docker/.env'),mcp=await parse('infra/docker/.env.mcp');
const origin='http://127.0.0.1:'+(local.API_PORT??18080),results=[];
const platform=['compose','--env-file','infra/docker/.env','--env-file','infra/docker/.env.mcp','-f','infra/docker/compose.yaml','-f','infra/docker/mcp-platform.override.yaml'];
const native=['compose','--env-file','infra/docker/demo.env','--env-file','infra/docker/.env.mcp','-f','infra/docker/mcp-demo.compose.yaml','-f','verification/mcp-compatibility/observe.compose.yaml'];
function docker(args,input){const r=spawnSync('docker',args,{input,encoding:'utf8',windowsHide:true,timeout:240000,maxBuffer:8*1024*1024});if(r.status!==0)throw Error('Docker verification failed: '+args[0]+' '+(args.includes('restart')?'restart':args.includes('stop')?'stop':'operation'));return (args.includes('logs')?r.stdout+r.stderr:r.stdout).trim();}
async function ready(){const end=Date.now()+120000;while(Date.now()<end){try{if((await fetch(origin+'/actuator/health/readiness',{signal:AbortSignal.timeout(4000)})).ok)return;}catch{}await new Promise(r=>setTimeout(r,1000));}throw Error('Backend readiness deadline');}
let cookie='';
async function call(path,method='GET',body,expected=200,csrf=true){
 const headers=new Headers();if(cookie)headers.set('Cookie',cookie);
 if(method!=='GET'&&csrf){const token=await call('/api/csrf');headers.set(token.headerName,token.token);}
 if(body!==undefined)headers.set('Content-Type','application/json');
 const r=await fetch(origin+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(35000),redirect:'manual'});
 const set=r.headers.getSetCookie().find(s=>s.startsWith('ECOSYNC_SESSION='));if(set)cookie=set.split(';')[0];
 const data=r.status===204?{}:await r.json();assert.equal(r.status,expected,'API '+method+' '+path+' expected '+expected+'; received '+r.status+' '+(data.code??''));return data;
}
async function login(){cookie='';const csrf=await call('/api/csrf');const r=await fetch(origin+'/api/auth/login',{method:'POST',headers:{Cookie:cookie,[csrf.headerName]:csrf.token},body:new URLSearchParams({username:local.BOOTSTRAP_USERNAME,password:local.BOOTSTRAP_PASSWORD})});assert.equal(r.status,204);cookie=r.headers.getSetCookie().find(s=>s.startsWith('ECOSYNC_SESSION=')).split(';')[0];return call('/api/session');}
function counts(){
 const code=`const values={};for(const kind of ['sales','inventory']){const r=await fetch('http://'+kind+'-mcp:'+(kind==='sales'?8091:8092)+'/internal/operations',{headers:{Authorization:'Bearer '+process.env[kind==='sales'?'SALES_MCP_TOKEN':'INVENTORY_MCP_TOKEN']}});if(!r.ok)throw Error('Counter unavailable');values[kind]=(await r.json()).sourceSqlAttempts;}console.log(JSON.stringify(values));`;
 return JSON.parse(docker(['run','--rm','-i','--network','ecosync-mcp_control','--env-file','infra/docker/.env.mcp','--entrypoint','node','ecosync-native-mcp-client:phase3','--input-type=module'],code));
}
async function noSource(label,callback){const before=counts();await callback();assert.deepEqual(counts(),before,label+' must produce zero source SQL admissions');results.push({check:label,result:'PASS',sourceSqlAdmissions:0});console.log('PASS: '+label+'; zero source SQL admissions.');}
const ids=[];let org,base;
const create=async(kind,name,extra={})=>{const item=await call(base,'POST',{displayName:name,endpoint:'http://'+kind+'-mcp:'+(kind==='sales'?8091:8092)+'/mcp',protocolPolicy:kind==='sales'?'LEGACY_2025':'MODERN_2026',credential:mcp[kind==='sales'?'SALES_MCP_TOKEN':'INVENTORY_MCP_TOKEN'],...extra});ids.push(item.id);return item;};
const invoke=(id,cap,args,status=200)=>call(base+'/'+id+'/capabilities/'+cap+'/invocations','POST',{invocationId:randomUUID(),arguments:args},status);
async function approve(id,cap,approved=true){return call(base+'/'+id+'/capabilities/'+cap+'/approval','POST',{role:'INTEGRATION_ADMIN',approved});}
const volumes=()=>JSON.parse(docker(['volume','ls','--format','json']).split(/\r?\n/).filter(Boolean).map(s=>JSON.parse(s)).filter(v=>/ecosync-(?:phase1|demo-v1)_/.test(v.Name)).map(v=>v.Name).sort().map(s=>JSON.stringify(s)).join(',').replace(/^/,'[')+']');
const beforeVolumes=volumes();
try{
 await ready();await call('/api/session','GET',undefined,401);
 const session=await login();org=session.workspaces[0].id;base='/api/organizations/'+org+'/integrations';
 await noSource('Native endpoint authentication, Origin, Host and malformed JSON',async()=>{
  const code=await readFile('verification/mcp-compatibility/endpoint-negatives.mjs','utf8');
  docker(['run','--rm','-i','--network','ecosync-mcp_control','--env-file','infra/docker/.env.mcp','--entrypoint','node','ecosync-native-mcp-client:phase3','--input-type=module'],code);
 });
 await noSource('CSRF required',()=>call(base,'POST',{},403,false));
 await noSource('Operator URL policy',()=>call(base,'POST',{displayName:'Forbidden',endpoint:'http://169.254.169.254/latest',protocolPolicy:'AUTO_DUAL',credential:'sentinel-credential-123456789'},422));
 const records=[];
 for(const kind of ['sales','inventory']){
  const name='Verification '+kind+' '+randomUUID().slice(0,8),item=await create(kind,name);
  await noSource('Duplicate case-insensitive display name '+kind,()=>call(base,'POST',{displayName:name.toUpperCase(),endpoint:'http://'+kind+'-mcp:'+(kind==='sales'?8091:8092)+'/mcp',protocolPolicy:'AUTO_DUAL',credential:mcp[kind==='sales'?'SALES_MCP_TOKEN':'INVENTORY_MCP_TOKEN']},400));
  await noSource('Cross-organization '+kind,()=>call('/api/organizations/'+randomUUID()+'/integrations/'+item.id,'GET',undefined,403));
  let detail=await call(base+'/'+item.id+'/validate','POST');assert.equal(detail.health,'HEALTHY');assert.equal(detail.tools.filter(t=>t.present).length,3);
  const tool=detail.tools.find(t=>t.tool.name===(kind==='sales'?'sales_get_revenue_summary':'inventory_get_stock_by_sku'));
  const args=kind==='sales'?{startInclusive:'2026-10-01T00:00:00Z',endExclusive:'2026-11-01T00:00:00Z',currency:'USD'}:{sku:'WH-700'};
  await noSource('Discovery does not approve '+kind,()=>invoke(item.id,tool.id,args,409));
  await approve(item.id,tool.id);await call(base+'/'+item.id+'/activate','POST');
  const other=detail.tools.find(t=>t.id!==tool.id);await noSource('Unapproved tool '+kind,()=>invoke(item.id,other.id,{},403));
  await noSource('Invalid typed arguments '+kind,()=>invoke(item.id,tool.id,{unexpected:true},422));
  const before=counts(),result=await invoke(item.id,tool.id,args);assert.equal(counts()[kind],before[kind]+1);assert.equal(result.untrusted,true);
  if(kind==='sales')assert.equal(result.result.structuredContent.rows[0].revenue,'145444.58');else assert.equal(result.result.structuredContent.rows.length,3);
  await approve(item.id,tool.id,false);await noSource('Revoked approval '+kind,()=>invoke(item.id,tool.id,args,403));await approve(item.id,tool.id);
  assert.ok(!(JSON.stringify(await call(base+'/'+item.id))).includes(mcp[kind==='sales'?'SALES_MCP_TOKEN':'INVENTORY_MCP_TOKEN']));
  records.push({kind,id:item.id,tool:tool.id,args});results.push({check:kind+' authenticated source invocation',result:'PASS',protocol:result.protocol});
 }
 const sales=records[0],inventory=records[1];
 await noSource('Capability identity cannot cross integrations',()=>invoke(inventory.id,sales.tool,inventory.args,403));
 await noSource('Oversized management body',()=>call(base+'/'+sales.id+'/capabilities/'+sales.tool+'/invocations','POST',{invocationId:randomUUID(),arguments:{payload:'x'.repeat(70000)}},413));
 for(const scenario of ['wrong credential','protocol mismatch']){
  const item=await create('inventory','Negative '+randomUUID().slice(0,8),scenario==='wrong credential'?{credential:'sentinel-wrong-credential-123456789'}:{protocolPolicy:'LEGACY_2025'});
  await noSource(scenario,async()=>{const error=await call(base+'/'+item.id+'/validate','POST',undefined,422);assert.equal(error.code,scenario==='wrong credential'?'MCP_AUTHENTICATION_FAILED':'MCP_UNSUPPORTED_PROTOCOL');});
 }
 // PostgreSQL registry survives a real backend process restart; old HTTP session does not.
 docker([...platform,'restart','backend']);await ready();await call('/api/session','GET',undefined,401);await login();
 assert.equal((await call(base+'/'+sales.id)).state,'ACTIVE');await invoke(sales.id,sales.tool,sales.args);results.push({check:'Registry and approved-schema recovery after backend restart',result:'PASS'});
 for(const record of records){
  docker([...native,'stop',record.kind+'-mcp']);await invoke(record.id,record.tool,record.args,502);
  assert.equal((await call(base+'/'+record.id)).health,'UNREACHABLE');docker([...native,'up','-d','--wait','--wait-timeout','120',record.kind+'-mcp']);
  await call(base+'/'+record.id+'/validate','POST');await call(base+'/'+record.id+'/activate','POST');await invoke(record.id,record.tool,record.args);results.push({check:record.kind+' real server disconnect and explicit recovery',result:'PASS'});
 }
 await call(base+'/'+sales.id,'PUT',{displayName:'Revised '+randomUUID().slice(0,8),endpoint:'http://sales-mcp:8091/mcp',protocolPolicy:'LEGACY_2025',credential:''});
 await noSource('Material revision invalidates approvals',()=>invoke(sales.id,sales.tool,sales.args,409));
 await call(base+'/'+inventory.id+'/disable','POST');await noSource('Disabled registration',()=>invoke(inventory.id,inventory.tool,inventory.args,409));
 docker([...native,'stop']);docker([...platform,'restart','backend']);await ready();await login();assert.equal((await call(base)).available,true);assert.equal((await call('/api/system/status')).status,'UP');
 docker([...native,'up','-d','--wait','--wait-timeout','180']);results.push({check:'Platform starts with all MCP services stopped; readiness remains DB-only',result:'PASS'});
 const backendId=docker([...platform,'ps','-q','backend']),clientId=docker([...native,'ps','-q','mcp-client']);
 const backend=JSON.parse(docker(['inspect',backendId]))[0],bridge=JSON.parse(docker(['inspect',clientId]))[0];
 assert.ok(!Object.keys(backend.NetworkSettings.Networks).includes('ecosync-demo-v1_sources'));assert.deepEqual(Object.keys(bridge.NetworkSettings.Networks),['ecosync-mcp_control']);
 for(const name of ['sales-mcp','inventory-mcp']){const inspect=JSON.parse(docker(['inspect',docker([...native,'ps','-q',name])]))[0];assert.ok(inspect.NetworkSettings.Networks['ecosync-demo-v1_sources']);assert.ok(inspect.NetworkSettings.Networks['ecosync-mcp_control']);assert.ok(!Object.values(inspect.NetworkSettings.Ports??{}).some(Boolean));}
 assert.deepEqual(volumes(),beforeVolumes);results.push({check:'Network/source isolation, no MCP host ports, existing named volumes preserved',result:'PASS'});
 const permission=docker([...platform,'exec','-T','db','psql','-U','ecosync_owner','-d','ecosync','-Atc',"SELECT has_table_privilege('ecosync_runtime','app.mcp_configuration','INSERT'),has_table_privilege('ecosync_runtime','app.mcp_configuration','UPDATE'),has_table_privilege('ecosync_runtime','app.app_user','UPDATE'),has_table_privilege('ecosync_runtime','app.flyway_schema_history','SELECT')"]);assert.equal(permission,'t|f|f|f');
 results.push({check:'Runtime registry INSERT permitted; immutable revision UPDATE, identity writes and migration history denied',result:'PASS'});
 const sources=await parse('infra/docker/demo.env'),keyring=JSON.parse(await readFile('.verification/mcp/keyring.json','utf8'));
 const secretValues=[...Object.values(local),...Object.values(mcp),...Object.values(sources),...Object.values(keyring.keys)].filter(v=>v.length>=16);
 const logs=docker([...platform,'logs','--no-color','backend'])+'\n'+docker([...native,'logs','--no-color']);assert.ok(!secretValues.some(v=>logs.includes(v)));assert.ok(!logs.includes('sentinel-wrong-credential-123456789'));
 results.push({check:'Actual service logs contain no generated credential/key values or sentinel credential',result:'PASS'});
}finally{
 try{docker([...native,'up','-d','--wait','--wait-timeout','180']);await ready();await login();for(const id of ids){try{await call(base+'/'+id,'DELETE');}catch{}}}catch{console.error('Verification cleanup requires operator attention; no volumes were deleted.');}
}
await mkdir('.verification/phase3',{recursive:true});await writeFile('.verification/phase3/stack-results.json',JSON.stringify({observedOn:new Date().toISOString(),results},null,2)+'\n');console.log('PASS: '+results.length+' actual stack/security/lifecycle checks; no skipped checks. Evidence: .verification/phase3/stack-results.json');
