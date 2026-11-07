import {spawnSync} from 'node:child_process';
const result=spawnSync('docker',['ps','-a','--format','{{.Names}} {{.Status}}'],{encoding:'utf8',windowsHide:true,timeout:15000});for(const line of (result.stdout??'').split(/\r?\n/))if(line.startsWith('ecosync-'))console.log(line);console.log('Raw conversations, model prompts, source payloads, environment and credentials are intentionally omitted.');
