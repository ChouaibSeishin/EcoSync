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
 if(!['.env','demo.env','.env.mcp','.env.database','.env.rest'].includes(name)&&!name.startsWith('.env.demo-repro-'))continue;
 const source=await readFile(path.join(root,'infra/docker',name),'utf8');
 secrets.push(...source.split(/\r?\n/).filter(l=>/^[A-Z_]*(PASSWORD|TOKEN|SECRET|API_KEY)=/.test(l)).map(l=>l.slice(l.indexOf('=')+1)).filter(v=>v.length>=16));
}
try{const ring=JSON.parse(await readFile(path.join(root,'.verification/mcp/keyring.json'),'utf8'));secrets.push(...Object.values(ring.keys).filter(v=>typeof v==='string'&&v.length>=16));}catch(error){if(error.code!=='ENOENT')failures.push('Cannot inspect local MCP keyring for known-secret review.');}
secrets=[...new Set(secrets)];
try{secrets.push((await readFile(path.join(root,'.verification/database/assertion-private.der'))).toString('base64'));}catch(error){if(error.code!=='ENOENT')failures.push('Cannot inspect delegated signing key for known-secret review.');}
try{secrets.push((await readFile(path.join(root,'.verification/rest/assertion-private.der'))).toString('base64'));}catch(error){if(error.code!=='ENOENT')failures.push('Cannot inspect REST signing key.');}
try{const values=await readFile(path.join(root,'.verification/movies/reader.env'),'utf8');secrets.push(...values.split(/\r?\n/).filter(l=>l.startsWith('MOVIES_DB_PASSWORD=')).map(l=>l.slice(l.indexOf('=')+1)).filter(v=>v.length>=16));}catch(error){if(error.code!=='ENOENT')failures.push('Cannot inspect optional local movies credential.');}
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
if(JSON.stringify(pkg.overrides)!==JSON.stringify(v.frontend.overrides))failures.push('Frontend security override differs from declared pins.');
const webLock=JSON.parse(await readFile(path.join(root,'frontend/package-lock.json'),'utf8'));
for(const [key,entry]of Object.entries(webLock.packages))if(key.endsWith('/minimatch')&&entry.version!==v.frontend.overrides.minimatch)failures.push('Unpatched minimatch entry in frontend lock.');
for(const file of ['mcp-client/package.json','mcp-servers/inventory/package.json','verification/mcp-compatibility/typescript/package.json']){
 const p=JSON.parse(await readFile(path.join(root,file),'utf8')),l=JSON.parse(await readFile(path.join(root,path.dirname(file),'package-lock.json'),'utf8'));
 for(const group of ['dependencies','devDependencies'])for(const[name,version]of Object.entries(p[group]??{})){
  if(!/^[0-9]+[.][0-9]+[.][0-9]+$/.test(version)||l.packages[''][group]?.[name]!==version)failures.push('MCP exact dependency/lock mismatch: '+file+'/'+name);
  const expected=name==='mysql2'?v.mcp.mysql2:name==='ajv'?v.mcp.ajv:name==='zod'?v.mcp.zod:name==='@modelcontextprotocol/client'?v.mcp.typescriptClient:name==='@modelcontextprotocol/core'?v.mcp.typescriptCore:name==='@modelcontextprotocol/server'?v.mcp.typescriptServer:name==='@modelcontextprotocol/node'?v.mcp.nodeAdapter:null;
  if(expected&&version!==expected)failures.push('MCP manifest mismatch: '+file+'/'+name);
 }
}
for(const file of ['mcp-servers/sales/pom.xml','verification/mcp-compatibility/java/pom.xml','managed-gateway/pom.xml','rest-gateway/pom.xml','verification/gateway-compatibility/java/pom.xml']){
 const source=await readFile(path.join(root,file),'utf8');
 for(const version of [v.backend.springBoot,v.backend.springAi,v.managedObservations.mcpJavaSdk])if(!source.includes('<version>'+version+'</version>'))failures.push('MCP Java baseline mismatch: '+file);
}
if(!(await readFile(path.join(root,'backend/pom.xml'),'utf8')).includes('<artifactId>json-schema-validator</artifactId><version>'+v.backend.jsonSchemaValidator+'</version>'))failures.push('Java schema validator pin differs.');
const gatewayPom=await readFile(path.join(root,'managed-gateway/pom.xml'),'utf8');
for(const version of [v.databaseConnectors.postgresqlJdbc,v.databaseConnectors.mysqlConnectorJ])if(!gatewayPom.includes('<version>'+version+'</version>'))failures.push('Gateway JDBC pin differs.');
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
const phase2=await readFile(path.join(root,'config/phase2-ci-job.txt'),'utf8');
if(!workflow.replaceAll('\r\n','\n').includes(phase2.trimEnd()))failures.push('Accepted Phase 2 CI job was changed or removed.');
const phase3=await readFile(path.join(root,'config/phase3-ci-job.txt'),'utf8');
if(!workflow.replaceAll('\r\n','\n').includes(phase3.trimEnd()))failures.push('Accepted Phase 3 CI job was changed or removed.');
for(const [key,file]of [['java','rest-gateway/Dockerfile'],['node','demo-systems/partner-api/Dockerfile'],['java','managed-gateway/Dockerfile'],['java','verification/gateway-compatibility/Dockerfile'],['node','verification/gateway-compatibility/Dockerfile'],['java','mcp-servers/sales/Dockerfile'],['node','mcp-servers/inventory/Dockerfile'],['node','mcp-client/Dockerfile'],['java','verification/mcp-compatibility/Dockerfile'],['node','verification/mcp-compatibility/Dockerfile']])if(!(await readFile(path.join(root,file),'utf8')).includes(images[key]))failures.push('MCP image digest mismatch: '+file);
for(const file of ['infra/docker/compose.yaml','infra/docker/demo-systems.compose.yaml']){
 const previous=spawnSync('git',['show','a38682428457cca417cbac8f20bc14e7c0792f21:'+file],{cwd:root,encoding:'utf8',windowsHide:true});
 if(previous.status===0&&(await readFile(path.join(root,file),'utf8')).replaceAll('\r\n','\n')!==previous.stdout.replaceAll('\r\n','\n'))failures.push('Accepted platform/source lifecycle file changed: '+file);
}
const phase4Workflow=await readFile(path.join(root,'config/phase4-ci-workflow.txt'),'utf8');
for(const name of ['verify','managed_database','demo','native_mcp']){const prior=phase4Workflow.split('\n  '+name+':\n')[1]?.split(/\n  [a-z_]+:\n/)[0];const current=workflow.replaceAll('\r\n','\n').split('\n  '+name+':\n')[1]?.split(/\n  [a-z_]+:\n/)[0];if(!prior||prior.trimEnd()!==current?.trimEnd())failures.push('Accepted Phase 4 CI job changed: '+name);}
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
else console.log('PASS: '+files.length+' tracked paths; local platform/source/MCP credentials and key values reviewed, exact dependency/override pins, digest images, SHA-pinned Actions and preserved Phase 1/2 jobs/lifecycle files. Not an exhaustive security audit.');
