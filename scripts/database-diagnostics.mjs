import {readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
let secrets=[];for(const file of ['infra/docker/.env','infra/docker/demo.env','infra/docker/.env.mcp','infra/docker/.env.database'])try{secrets.push(...(await readFile(file,'utf8')).split(/\r?\n/).filter(l=>/^[A-Z_]*(PASSWORD|TOKEN|SECRET)=/.test(l)).map(l=>l.slice(l.indexOf('=')+1)));}catch{}
try{secrets.push(...Object.values(JSON.parse(await readFile('.verification/mcp/keyring.json','utf8')).keys));}catch{}
try{secrets.push((await readFile('.verification/database/assertion-private.der')).toString('base64'));}catch{}
const redact=text=>secrets.filter(s=>s.length>=16).reduce((value,s)=>value.replaceAll(s,'[REDACTED]'),text);
for(const name of ['ecosync-database-database-gateway-1','ecosync-phase1-backend-1','ecosync-phase1-setup-1']){const result=spawnSync('docker',['logs','--tail','60',name],{encoding:'utf8',windowsHide:true,timeout:8000,maxBuffer:65536});console.log(name+'\n'+redact(((result.stdout??'')+(result.stderr??'')).slice(-30000)));}
