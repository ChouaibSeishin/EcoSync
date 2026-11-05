import {spawnSync} from 'node:child_process';
import {access} from 'node:fs/promises';
function run(args){const r=spawnSync('docker',args,{stdio:'inherit',windowsHide:true});if(r.status!==0)throw Error('REST startup failed; existing volumes and credentials retained.');}
run(['compose','--env-file','infra/docker/.env.rest','-f','infra/docker/rest-gateway.compose.yaml',...(process.argv.includes('--verification')?['-f','verification/rest/observe.compose.yaml']:[]),'up','-d','--build','--wait','--wait-timeout','180']);
run(['compose','--env-file','infra/docker/demo.env','-f','infra/docker/demo-systems.compose.yaml','-f','infra/docker/rest-source.override.yaml','up','-d','--no-build','--no-deps','--wait','--wait-timeout','120','supplier']);
const platform=['compose','--env-file','infra/docker/.env','--env-file','infra/docker/.env.mcp','--env-file','infra/docker/.env.database','--env-file','infra/docker/.env.rest','-f','infra/docker/compose.yaml','-f','infra/docker/mcp-platform.override.yaml','-f','infra/docker/database-platform.override.yaml','-f','infra/docker/rest-platform.override.yaml'];
// Retain this workstation's optional movies allowlist; never import it into CI.
try{await access('.verification/movies/platform.override.yaml');platform.push('-f','.verification/movies/platform.override.yaml');}catch(e){if(e.code!=='ENOENT')throw e;}
run([...platform,'up','-d','--build','--wait','--wait-timeout','180']);
