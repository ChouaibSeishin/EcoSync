import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {counter,docker,gateway,platform,ready,registration} from './database-runtime.mjs';
function hold(requests){const code=`const r=await fetch('http://database-gateway:8093/internal/verification-hold',{method:'POST',headers:{Authorization:'Bearer '+process.env.DATABASE_GATEWAY_TOKEN,'Content-Type':'application/json'},body:JSON.stringify({milliseconds:5000,requests:${requests}})});if(!r.ok)throw Error('Verification hold unavailable');console.log('configured');`;docker(['run','--rm','-i','--network','ecosync-database_control','--env-file','infra/docker/.env.database','--entrypoint','node','ecosync-native-mcp-client:phase3','--input-type=module'],code);}
async function waitHeld(minimum){for(let i=0;i<8;i++){const value=counter();if(value.held>=minimum)return value;await new Promise(r=>setTimeout(r,100));}throw Error('Actual gateway admission did not reach verification fence.');}
export async function lifecycle({api,base,records,query,check,validate,activate,gatewayReady,assertionsFor,wire}){
 // The private, bounded test-only hold delays already-authorized real requests before SQL. It never fabricates a connection/result.
 await check('Real gateway concurrency, cancellation and disable fence late admissions before protected SQL',async()=>{
  const r=records[0],first=randomUUID(),second=randomUUID(),before=counter().sourceSqlAdmissions;hold(2);const a=query(r.id,r.table.id,{columns:r.columns},null,api,first),b=query(r.id,r.table.id,{columns:r.columns},null,api,second);await waitHeld(2);const third=await query(r.id,r.table.id,{columns:r.columns},null);assert.equal(third.status,429);assert.equal(third.value.code,'DB_BUSY');await api.call(base+'/'+r.id+'/invocations/'+first+'/cancel','POST');await api.call(base+'/'+r.id+'/disable','POST');const results=await Promise.all([a,b]);assert.ok(results.every(v=>[409,429,503].includes(v.status)));assert.ok(results.every(v=>!v.value.result));assert.equal(counter().sourceSqlAdmissions,before);
 });
 await api.call(base+'/'+records[0].id,'PUT',registration(records[0].kind,records[0].name));await validate(records[0]);await activate(records[0]);
 await check('Gateway restart during a held real request releases no stale result and requires healthy recovery',async()=>{
  const r=records[1];hold(1);const pending=query(r.id,r.table.id,{columns:r.columns},null);await waitHeld(1);docker([...gateway,'restart','--timeout','1']);const result=await pending;assert.ok([409,429,503].includes(result.status));assert.ok(!result.value.result);docker([...gateway,'up','-d','--no-build','--wait','--wait-timeout','180']);await validate(r);await activate(r);await gatewayReady(r);await query(r.id,r.table.id,{columns:r.columns,limit:1});
 });
 await check('Unavailable platform authority fails closed over actual MCP without protected SQL',async()=>{
  const r=records[0],context=await assertionsFor(r),before=counter().sourceSqlAdmissions;
  try{docker([...platform,'stop','--timeout','2','backend']);const result=wire([{name:'Authority unavailable denies discovery and invocation',kind:'sdk',assertion:context.issue(),visible:false,arguments:context.args}]);assert.equal(result.result,'PASS');assert.equal(counter().sourceSqlAdmissions,before);}
  finally{docker([...platform,'up','-d','--no-build','--wait','--wait-timeout','180','backend']);await ready();await api.login();}
 });
 await check('Platform readiness and native profile start while database gateway is stopped',async()=>{
  try{docker([...gateway,'stop','--timeout','2']);docker([...platform,'restart','--timeout','2','backend']);await ready();await api.login();assert.equal((await api.call('/api/system/status')).database,'UP');const result=await query(records[1].id,records[1].table.id,{columns:records[1].columns},null);assert.equal(result.status,503);}
  finally{docker([...gateway,'up','-d','--no-build','--wait','--wait-timeout','180']);}
  await validate(records[1]);await activate(records[1]);await query(records[1].id,records[1].table.id,{columns:records[1].columns,limit:1});
 });
}
