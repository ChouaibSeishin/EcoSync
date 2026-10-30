import {randomBytes} from 'node:crypto';
import {writeFile,access} from 'node:fs/promises';
const file='infra/docker/demo.env';
try { await access(file); console.log('Demo configuration exists; preserved.'); }
catch {
 const names=['SALES_OWNER_PASSWORD','SALES_MIGRATION_PASSWORD','SALES_READER_PASSWORD','INVENTORY_ROOT_PASSWORD','INVENTORY_MIGRATION_PASSWORD','INVENTORY_READER_PASSWORD','SUPPLIER_API_TOKEN'];
 await writeFile(file,names.map(n=>n+'='+randomBytes(32).toString('base64url')).join('\n')+'\n',{flag:'wx',mode:0o600});
 console.log('Created ignored, independently generated source credentials. No values printed.');
}
