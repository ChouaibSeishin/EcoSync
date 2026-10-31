import {randomBytes} from 'node:crypto';
import {mkdir,writeFile,access} from 'node:fs/promises';
const env='infra/docker/.env.mcp',keys='.verification/mcp/keyring.json';
await mkdir('.verification/mcp',{recursive:true});
try{await access(env);}catch{
 const names=['MCP_INTERNAL_TOKEN','SALES_MCP_TOKEN','INVENTORY_MCP_TOKEN'];
 await writeFile(env,names.map(n=>n+'='+randomBytes(32).toString('base64url')).join('\n')+'\n',{flag:'wx',mode:0o600});
}
try{await access(keys);}catch{
 await writeFile(keys,JSON.stringify({activeKeyId:'local-v1',keys:{'local-v1':randomBytes(32).toString('base64')}})+'\n',{flag:'wx',mode:0o600});
}
console.log('Ignored MCP credentials and external versioned keyring ready; existing files preserved. No values printed.');
