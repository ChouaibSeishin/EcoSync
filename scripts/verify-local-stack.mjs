import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
const root = fileURLToPath(new URL('../', import.meta.url));
process.chdir(root);
await mkdir('.verification/phase1', { recursive: true });
const env = Object.fromEntries((await readFile('infra/docker/.env', 'utf8')).split(/\r?\n/).filter(l => l && !l.startsWith('#')).map(l => { const i=l.indexOf('='); return [l.slice(0,i),l.slice(i+1)]; }));
const origin = 'http://127.0.0.1:' + (env.API_PORT ?? '18080');
const web = 'http://127.0.0.1:' + (env.WEB_PORT ?? '5173');
function compose(...args) {
  const r = spawnSync('docker', ['compose','-f','infra/docker/compose.yaml',...args], { encoding:'utf8', windowsHide:true });
  if (r.status !== 0) throw new Error('Compose operation failed: '+args[0]);
  return r.stdout.trim();
}
async function waitReady() {
  const deadline=Date.now()+120_000;
  while(Date.now()<deadline) {
    try { if ((await fetch(origin+'/actuator/health/readiness',{signal:AbortSignal.timeout(4000)})).ok) return; } catch { }
    await new Promise(r=>setTimeout(r,1000));
  }
  throw new Error('Application readiness deadline exceeded.');
}
let cookie='';
async function call(url, options={}) {
  const headers=new Headers(options.headers); if(cookie)headers.set('Cookie',cookie);
  const response=await fetch(origin+url,{...options,headers,redirect:'manual',signal:AbortSignal.timeout(15000)});
  const next=response.headers.getSetCookie().find(c=>c.startsWith('ECOSYNC_SESSION='));
  if(next)cookie=next.split(';')[0];
  return response;
}
async function login() {
  const csrf=await (await call('/api/csrf')).json();
  const response=await call('/api/auth/login',{method:'POST',headers:{[csrf.headerName]:csrf.token},body:new URLSearchParams({username:env.BOOTSTRAP_USERNAME,password:env.BOOTSTRAP_PASSWORD})});
  assert.equal(response.status,204,'Actual login must succeed');
}
await waitReady();
const anonymousResponse = await call('/api/session');
assert.equal(anonymousResponse.status,401);
assert.match((await anonymousResponse.json()).requestId,/^[0-9a-f-]{36}$/);
await call('/api/csrf'); const anonymous=cookie; await login(); assert.notEqual(cookie,anonymous);
const before=await (await call('/api/session')).json();
const require=createRequire(path.join(root,'frontend/package.json'));
const {chromium}=require('@playwright/test');
const browser=await chromium.launch();
let dbStopped=false;
const results={checkedOn:new Date().toISOString(),cookieRotation:true,runtimeRoleRestricted:false,persistenceAcrossBackendRestart:false,oldSessionInvalidated:false,livenessDuringDbFailure:false,readinessDuringDbFailure:false,statusApiDuringDbFailure:false,statusUiDuringDbFailure:false,recovery:false,responsiveLayout:false};
try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  await page.goto(web);
  await page.getByLabel('Username').fill(env.BOOTSTRAP_USERNAME);
  await page.getByLabel('Password').fill(env.BOOTSTRAP_PASSWORD);
  await page.getByRole('button',{name:'Sign in to workspace'}).click();
  await page.getByRole('heading',{name:'A clearer view starts here.'}).waitFor();
  await page.screenshot({path:'.verification/phase1/dashboard-desktop.png',fullPage:true,animations:'disabled'});
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);
  results.responsiveLayout=true;
  await page.screenshot({path:'.verification/phase1/dashboard-mobile.png',fullPage:true,animations:'disabled'});
  await page.close();
  const role=compose('exec','-T','db','sh','-c','PGPASSWORD="$DB_RUNTIME_PASSWORD" psql -h 127.0.0.1 -U ecosync_runtime -d ecosync -Atc "SELECT current_user,has_schema_privilege(current_user,\'app\',\'CREATE\'),rolsuper,rolcreatedb FROM pg_roles WHERE rolname=current_user"');
  assert.equal(role,'ecosync_runtime|f|f|f'); results.runtimeRoleRestricted=true;
  const control=compose('exec','-T','db','psql','-U','ecosync_owner','-d','ecosync','-Atc',"SELECT has_table_privilege('ecosync_runtime','app.flyway_schema_history','UPDATE'),has_table_privilege('ecosync_runtime','app.bootstrap_marker','DELETE'),has_table_privilege('ecosync_runtime','app.app_user','UPDATE')");
  assert.equal(control,'f|f|f');
  const migration=compose('exec','-T','db','psql','-U','ecosync_owner','-d','ecosync','-Atc','SELECT count(*) FROM app.flyway_schema_history WHERE version=\'1\' AND checksum IS NOT NULL AND success');
  assert.equal(migration,'1');
  compose('restart','backend'); await waitReady();
  assert.equal((await call('/api/session')).status,401); results.oldSessionInvalidated=true;
  await login(); const after=await (await call('/api/session')).json();
  assert.equal(after.id,before.id); assert.equal(after.workspaces[0].id,before.workspaces[0].id);
  results.persistenceAcrossBackendRestart=true;
  const statusPage=await browser.newPage({viewport:{width:1440,height:1000}});
  await statusPage.goto(web);
  await statusPage.getByLabel('Username').fill(env.BOOTSTRAP_USERNAME);
  await statusPage.getByLabel('Password').fill(env.BOOTSTRAP_PASSWORD);
  await statusPage.getByRole('button',{name:'Sign in to workspace'}).click();
  await statusPage.getByRole('heading',{name:'A clearer view starts here.'}).waitFor();
  await statusPage.getByRole('link',{name:'System status',exact:true}).click();
  await statusPage.getByText('Core services are available').waitFor();
  compose('stop','db'); dbStopped=true;
  assert.equal((await fetch(origin+'/actuator/health/liveness')).status,200); results.livenessDuringDbFailure=true;
  assert.equal((await fetch(origin+'/actuator/health/readiness')).status,503); results.readinessDuringDbFailure=true;
  const status=await (await call('/api/system/status')).json(); assert.equal(status.database,'DOWN'); results.statusApiDuringDbFailure=true;
  await statusPage.getByRole('button',{name:'Refresh status'}).click();
  await statusPage.getByText('Database unavailable',{exact:true}).waitFor({timeout:20000}); results.statusUiDuringDbFailure=true;
  compose('start','db'); dbStopped=false; await waitReady();
  await statusPage.getByRole('button',{name:'Refresh status'}).click();
  await statusPage.getByText('Core services are available').waitFor({timeout:20000}); results.recovery=true;
  await writeFile('.verification/phase1/runtime-evidence.json',JSON.stringify(results,null,2)+'\n');
  console.log('PASS: real browser layout, runtime DB privileges, migration checksum, backend restart persistence, stale-session rejection, DB outage health/API/UI and recovery.');
} finally {
  if(dbStopped){compose('start','db');await waitReady();}
  await browser.close();
}
