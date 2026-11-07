import {spawnSync} from 'node:child_process';
import {access,mkdir,writeFile} from 'node:fs/promises';
const verification=process.argv.includes('--verification'),model=process.argv.includes('--model');if(verification&&model)throw Error('Choose real local model or explicit verification adapter, not both.');
await mkdir('.verification/chat',{recursive:true});try{await access('.verification/chat/bindings.json');}catch(e){if(e.code!=='ENOENT')throw e;await writeFile('.verification/chat/bindings.json',JSON.stringify({version:'1.0.0',bindings:[]})+'\n');}
const args=['compose','--env-file','infra/docker/.env','--env-file','infra/docker/.env.mcp','--env-file','infra/docker/.env.database','--env-file','infra/docker/.env.rest','-f','infra/docker/compose.yaml','-f','infra/docker/mcp-platform.override.yaml','-f','infra/docker/database-platform.override.yaml','-f','infra/docker/rest-platform.override.yaml','-f','infra/docker/chat-platform.override.yaml'];
try{await access('.verification/movies/platform.override.yaml');args.push('-f','.verification/movies/platform.override.yaml');}catch(e){if(e.code!=='ENOENT')throw e;}
if(verification)args.push('-f','verification/chat-platform.override.yaml');if(model)args.push('-f','infra/docker/chat-model.override.yaml');
const r=spawnSync('docker',[...args,'up','-d','--build','--wait','--wait-timeout','180'],{stdio:'inherit',windowsHide:true,env:{...process.env,AI_ENABLED:model?'true':'false'}});if(r.status!==0)throw Error('Chat startup failed; all volumes, keys and existing registrations retained.');
console.log(verification?'Explicit verification test double enabled; this is not an LLM.':model?'Optional local model enabled; every turn remains deadline bounded.':'AI disabled; persistent conversations and existing integrations available.');
