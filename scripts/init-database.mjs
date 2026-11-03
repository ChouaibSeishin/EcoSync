import {readFile,writeFile,mkdir,access,chmod} from 'node:fs/promises';
import {randomBytes,generateKeyPairSync} from 'node:crypto';
import {spawnSync} from 'node:child_process';
const file='infra/docker/.env.database',directory='.verification/database';
await mkdir(directory,{recursive:true});
try{await access(file);console.log('Existing database gateway configuration retained.');}catch(e){if(e.code!=='ENOENT')throw e;
 const values={DATABASE_GATEWAY_TOKEN:randomBytes(32).toString('base64url'),GATEWAY_SALES_READER_PASSWORD:randomBytes(32).toString('base64url'),GATEWAY_INVENTORY_READER_PASSWORD:randomBytes(32).toString('base64url')};
 await writeFile(file,Object.entries(values).map(([k,v])=>k+'='+v).join('\n')+'\n',{flag:'wx',mode:0o600});console.log('Generated separate ignored gateway and database reader credentials.');
}
try{await access(directory+'/assertion-private.der');await access(directory+'/assertion-public.der');console.log('Existing delegation keypair retained.');}catch(e){if(e.code!=='ENOENT')throw e;
 // Refuse partial keypairs; never replace a retained identity accidentally.
 const exists=async p=>access(p).then(()=>true,e=>{if(e.code==='ENOENT')return false;throw e;});
 if(await exists(directory+'/assertion-private.der')||await exists(directory+'/assertion-public.der'))throw Error('Partial delegation keypair; restore the matching retained files.');
 const {privateKey,publicKey}=generateKeyPairSync('ed25519');await writeFile(directory+'/assertion-private.der',privateKey.export({type:'pkcs8',format:'der'}),{flag:'wx',mode:0o640});await writeFile(directory+'/assertion-public.der',publicKey.export({type:'spki',format:'der'}),{flag:'wx',mode:0o644});console.log('Generated ignored delegated-identity keypair; private key stays with backend.');
}
// Record the operator-selected actual source subnet. Re-running preserves an existing policy.
const text=await readFile(file,'utf8');if(!/^DATABASE_ALLOWED_CIDRS=/m.test(text)){
 const r=spawnSync('docker',['network','inspect','ecosync-demo-v1_sources','--format','{{json .IPAM.Config}}'],{encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error('Start the retained demo sources first.');
 const networks=JSON.parse(r.stdout).map(n=>n.Subnet).filter(n=>typeof n==='string'&&/^[0-9./]+$/.test(n));if(!networks.length)throw Error('Source network subnet unavailable.');await writeFile(file,text+'DATABASE_ALLOWED_CIDRS='+networks.join(',')+'\n');
}
await chmod(file,0o600);console.log('Database gateway local setup ready. No existing volume, source account, encryption key or identity was changed.');
