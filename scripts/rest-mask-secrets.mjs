import {readFile} from 'node:fs/promises';
if(process.env.GITHUB_ACTIONS==='true'){for(const line of (await readFile('infra/docker/.env.rest','utf8')).split(/\r?\n/)){if(/^[A-Z_]*(TOKEN|KEY|PASSWORD)=/.test(line)){const value=line.slice(line.indexOf('=')+1);if(value.length>=16)console.log('::add-mask::'+value);}}}
