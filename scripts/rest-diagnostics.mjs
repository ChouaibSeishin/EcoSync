import {spawnSync} from 'node:child_process';
// Never emit source bodies, environment, credentials, request arguments or raw exception chains.
const result=spawnSync('docker',['ps','-a','--format','{{.Names}}\t{{.Status}}'],{encoding:'utf8',windowsHide:true,timeout:10000});
for(const line of (result.stdout??'').split(/\r?\n/))if(line.startsWith('ecosync-'))console.log(line);
console.log('REST diagnostics contain container status only. Inspect bounded sanitized verification failures; raw request/response logs are intentionally excluded.');
