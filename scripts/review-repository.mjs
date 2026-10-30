import { readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url));
const listing=spawnSync('git',['ls-files','-z'],{cwd:root,encoding:'utf8',windowsHide:true});
if(listing.status!==0)throw new Error('Cannot inspect tracked files.');
const files=listing.stdout.split('\0').filter(Boolean);
let secrets=[];
try {
  secrets=(await readFile(path.join(root,'infra/docker/.env'),'utf8')).split(/\r?\n/).filter(l=>/^[A-Z_]*(PASSWORD|TOKEN|SECRET)=/.test(l)).map(l=>l.slice(l.indexOf('=')+1)).filter(v=>v.length>=16);
} catch { /* CI may run the source review before local initialization. */ }
const failures=[];
for(const file of files) {
  if(file.split('/').some(p=>['node_modules','target','.verification','test-results','playwright-report'].includes(p)) || (file.endsWith('/.env') || file==='.env')) failures.push('Generated/private path tracked: '+file);
  const content=await readFile(path.join(root,file));
  if(secrets.some(value=>content.includes(Buffer.from(value))))failures.push('Local generated secret found in '+file);
  if(/(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)/.test(content.toString()))failures.push('Credential pattern in '+file);
}
const v=JSON.parse(await readFile(path.join(root,'config/versions.json'),'utf8'));
const pkg=JSON.parse(await readFile(path.join(root,'frontend/package.json'),'utf8'));
for(const group of ['dependencies','devDependencies'])for(const[name,version]of Object.entries(v.frontend[group]))if(pkg[group]?.[name]!==version)failures.push('Frontend pin differs: '+name);
if(pkg.engines.node!==v.toolchain.node || pkg.engines.npm!==v.toolchain.npm)failures.push('Frontend engine differs from approved baseline.');
const images=JSON.parse(await readFile(path.join(root,'config/container-images.json'),'utf8'));
for(const [key,file]of [['postgres','infra/docker/compose.yaml'],['java','backend/Dockerfile'],['node','frontend/Dockerfile']])if(!(await readFile(path.join(root,file),'utf8')).includes(images[key]))failures.push('Container pin differs: '+key);
const workflow=await readFile(path.join(root,'.github/workflows/ci.yml'),'utf8');
for(const match of workflow.matchAll(/uses:\s*([^\s#]+)/g))if(!/@[a-f0-9]{40}$/.test(match[1]))failures.push('Unpinned Action: '+match[1]);
if(failures.length){failures.forEach(f=>console.error(f));process.exitCode=1;}else console.log('PASS: tracked-file/known-secret review, frontend version alignment, container digests and action SHA pins ('+files.length+' tracked paths). This is not an exhaustive security audit.');
