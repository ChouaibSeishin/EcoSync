import {readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
const secrets=[];
for(const file of ['infra/docker/.env','infra/docker/demo.env','infra/docker/.env.mcp'])try{
 secrets.push(...(await readFile(file,'utf8')).split(/\r?\n/).filter(l=>l.includes('=')).map(l=>l.slice(l.indexOf('=')+1)).filter(v=>v.length>=16));
}catch{}
try{secrets.push(...Object.values(JSON.parse(await readFile('.verification/mcp/keyring.json','utf8')).keys));}catch{}
const groups=[['--env-file','infra/docker/.env','--env-file','infra/docker/.env.mcp','-f','infra/docker/compose.yaml','-f','infra/docker/mcp-platform.override.yaml'],['--env-file','infra/docker/demo.env','--env-file','infra/docker/.env.mcp','-f','infra/docker/mcp-demo.compose.yaml']];
for(const group of groups){
 const r=spawnSync('docker',['compose',...group,'logs','--no-color','--tail','80'],{encoding:'utf8',windowsHide:true,maxBuffer:4*1024*1024});
 let output=(r.stdout??'')+(r.stderr??'');for(const secret of secrets)output=output.split(secret).join('[REDACTED]');console.log(output.slice(-32768));
}
