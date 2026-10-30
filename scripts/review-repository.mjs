import {readFile,readdir} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url));
const listing=spawnSync('git',['ls-files','-z'],{cwd:root,encoding:'utf8',windowsHide:true});
if(listing.status!==0)throw new Error('Cannot inspect tracked files.');
const files=listing.stdout.split('\0').filter(Boolean),failures=[];
let secrets=[];
for(const name of await readdir(path.join(root,'infra/docker'))){
 if(!['.env','demo.env'].includes(name)&&!name.startsWith('.env.demo-repro-'))continue;
 const source=await readFile(path.join(root,'infra/docker',name),'utf8');
 secrets.push(...source.split(/\r?\n/).filter(l=>/^[A-Z_]*(PASSWORD|TOKEN|SECRET)=/.test(l)).map(l=>l.slice(l.indexOf('=')+1)).filter(v=>v.length>=16));
}
secrets=[...new Set(secrets)];
for(const file of files){
 const base=path.basename(file);
 if(file.split('/').some(p=>['node_modules','target','.verification','test-results','playwright-report'].includes(p))||base==='demo.env'||base==='.env'||base.startsWith('.env.')&&base!=='.env.example')failures.push('Generated/private path tracked: '+file);
 const content=await readFile(path.join(root,file));
 if(secrets.some(value=>content.includes(Buffer.from(value))))failures.push('Local generated secret found in '+file);
 if(/(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)/.test(content.toString()))failures.push('Credential pattern in '+file);
}
const v=JSON.parse(await readFile(path.join(root,'config/versions.json'),'utf8'));
const pkg=JSON.parse(await readFile(path.join(root,'frontend/package.json'),'utf8'));
for(const group of ['dependencies','devDependencies'])for(const[name,version]of Object.entries(v.frontend[group]))if(pkg[group]?.[name]!==version)failures.push('Frontend pin differs: '+name);
if(pkg.engines.node!==v.toolchain.node||pkg.engines.npm!==v.toolchain.npm)failures.push('Frontend engine differs from approved baseline.');
const demo=JSON.parse(await readFile(path.join(root,'config/demo-versions.json'),'utf8'));
const supplier=JSON.parse(await readFile(path.join(root,'demo-systems/supplier-api/package.json'),'utf8'));
const lock=JSON.parse(await readFile(path.join(root,'demo-systems/supplier-api/package-lock.json'),'utf8'));
for(const group of ['dependencies','devDependencies']){
 if(JSON.stringify(supplier[group])!==JSON.stringify(demo.supplier[group]))failures.push('Supplier pins differ: '+group);
 for(const [name,version]of Object.entries(supplier[group])){
  if(!/^[0-9]+[.][0-9]+[.][0-9]+$/.test(version)||lock.packages[''][group]?.[name]!==version)failures.push('Supplier dependency/lock pin differs: '+name);
 }
}
if(supplier.engines.node!==v.toolchain.node||supplier.engines.npm!==v.toolchain.npm)failures.push('Supplier engine differs from verified Node baseline.');
const images=JSON.parse(await readFile(path.join(root,'config/container-images.json'),'utf8'));
for(const [key,file]of [['postgres','infra/docker/compose.yaml'],['java','backend/Dockerfile'],['node','frontend/Dockerfile'],['postgres','infra/docker/demo-systems.compose.yaml'],['mysql','infra/docker/demo-systems.compose.yaml'],['node','demo-systems/supplier-api/Dockerfile']])if(!(await readFile(path.join(root,file),'utf8')).includes(images[key]))failures.push('Container pin differs: '+file+'/'+key);
const workflow=await readFile(path.join(root,'.github/workflows/ci.yml'),'utf8');
for(const match of workflow.matchAll(/uses:\s*([^\s#]+)/g))if(!/@[a-f0-9]{40}$/.test(match[1]))failures.push('Unpinned Action: '+match[1]);
const prior=spawnSync('git',['show','603cfb993566f465d0c3328f175d71b27f6a4942:.github/workflows/ci.yml'],{cwd:root,encoding:'utf8',windowsHide:true});
if(prior.status===0){
 const body=prior.stdout.replaceAll('\r\n','\n').split('jobs:\n')[1].trimEnd();
 if(!workflow.replaceAll('\r\n','\n').includes(body))failures.push('Accepted Phase 1 CI job was changed or removed.');
}else {
 // Shallow CI checkouts cannot read the ancestor; compare the committed snapshot.
 const snapshot=await readFile(path.join(root,'config/phase1-ci-job.txt'),'utf8');
 if(!workflow.replaceAll('\r\n','\n').includes(snapshot.trimEnd()))failures.push('Accepted Phase 1 CI job differs from the preserved snapshot.');
}
if(failures.length){failures.forEach(f=>console.error(f));process.exitCode=1;}
else console.log('PASS: '+files.length+' tracked paths; all local known-secret values reviewed, platform/supplier exact pins, digest images, SHA-pinned Actions and preserved Phase 1 CI job. Not an exhaustive security audit.');
