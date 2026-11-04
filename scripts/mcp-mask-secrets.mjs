// GitHub runner control commands register masks; never run this as a local logger.
import {readFile} from 'node:fs/promises';
if(process.env.GITHUB_ACTIONS!=='true')throw Error('Secret-mask registration is restricted to the GitHub runner.');
const values=[];
for(const file of ['infra/docker/.env','infra/docker/demo.env','infra/docker/.env.mcp']){
 const lines=(await readFile(file,'utf8')).split(/\r?\n/);values.push(...lines.filter(l=>/^[A-Z_]*(PASSWORD|TOKEN|SECRET)=/.test(l)).map(l=>l.slice(l.indexOf('=')+1)));
}
values.push(...Object.values(JSON.parse(await readFile('.verification/mcp/keyring.json','utf8')).keys));
try{const lines=(await readFile('infra/docker/.env.database','utf8')).split(/\r?\n/);values.push(...lines.filter(l=>/^[A-Z_]*(PASSWORD|TOKEN|SECRET)=/.test(l)).map(l=>l.slice(l.indexOf('=')+1)));values.push((await readFile('.verification/database/assertion-private.der')).toString('base64'));}catch(e){if(e.code!=='ENOENT')throw e;}
for(const value of new Set(values))if(typeof value==='string'&&value.length>=16&&!/[\r\n]/.test(value))process.stdout.write('::add-mask::'+value+'\n');
