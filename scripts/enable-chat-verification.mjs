import {spawnSync} from 'node:child_process';
import {access} from 'node:fs/promises';
function run(command,args){const r=spawnSync(command,args,{stdio:'inherit',windowsHide:true});if(r.status!==0)throw Error('Explicit chat verification startup failed; existing data retained.');}
run('docker',['compose','--env-file','infra/docker/demo.env','--env-file','infra/docker/.env.mcp','-f','infra/docker/mcp-demo.compose.yaml','-f','verification/mcp-compatibility/observe.compose.yaml','up','-d','--no-build','--wait','--wait-timeout','180']);
const gateway=['compose','--env-file','infra/docker/.env.database','-f','infra/docker/database-gateway.compose.yaml','-f','verification/gateway-compatibility/observe.compose.yaml'];try{await access('.verification/movies/gateway.override.yaml');gateway.push('-f','.verification/movies/gateway.override.yaml');}catch(e){if(e.code!=='ENOENT')throw e;}
run('docker',[...gateway,'up','-d','--no-build','--wait','--wait-timeout','180']);
run(process.execPath,['scripts/start-rest.mjs','--verification']);
run(process.execPath,['scripts/init-chat-demo.mjs']);
run(process.execPath,['scripts/start-chat.mjs','--verification']);
