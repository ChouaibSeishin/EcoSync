import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
export const composeArgs=['compose','--env-file','infra/docker/demo.env','-f','infra/docker/demo-systems.compose.yaml',...(process.env.DEMO_PROJECT?['-p',process.env.DEMO_PROJECT]:[])];
export function docker(args,input,allowFailure=false){
 const r=spawnSync('docker',[...composeArgs,...args],{input,encoding:'utf8',windowsHide:true,maxBuffer:16*1024*1024,env:process.env});
 if(!allowFailure&&r.status!==0)throw Error('Docker operation failed: '+args[0]+' '+(r.stderr||r.error?.message||'').slice(0,1200));
 return r;
}
export function pg(query,role='reader',fail=false){
 return docker(['exec','-T','sales','sh','-c','PGPASSWORD="$SALES_'+(role==='reader'?'READER':'MIGRATION')+'_PASSWORD" psql -X -h 127.0.0.1 -U sales_'+(role==='reader'?'reader':'migrator')+' -d sales -At -v ON_ERROR_STOP=1'],query,fail);
}
export function mysql(query,role='reader',fail=false){
 return docker(['exec','-T','inventory','sh','-c','MYSQL_PWD="$INVENTORY_'+(role==='reader'?'READER':'MIGRATION')+'_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -u inventory_'+(role==='reader'?'reader':'migrator')+' --database=inventory --batch --raw --skip-column-names'],query,fail);
}
export function denied(result,pattern=/permission denied|must be|not permitted|access denied|command denied|denied to user/i){
 assert.notEqual(result.status,0,'Operation unexpectedly succeeded');
 assert.match(result.stderr,pattern,'Failure must be an actual authorization rejection');
}
