import { mkdir, writeFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
const dir = fileURLToPath(new URL('../infra/docker/', import.meta.url));
const secret = () => randomBytes(30).toString('base64url');
await mkdir(dir, { recursive: true });
try {
  await writeFile(dir + '.env', [
    'DB_OWNER_PASSWORD=' + secret(), 'DB_MIGRATION_PASSWORD=' + secret(),
    'DB_RUNTIME_PASSWORD=' + secret(), 'BOOTSTRAP_USERNAME=admin',
    'BOOTSTRAP_PASSWORD=' + secret(), 'BOOTSTRAP_ORGANIZATION=Ecosync Workspace',
    'DB_PORT=55432', 'WEB_PORT=5173', 'API_PORT=8080', ''
  ].join('\n'), { flag: 'wx', mode: 0o600 });
  console.log('Created infra/docker/.env with unique local credentials. Open it locally to read the initial password.');
} catch (error) {
  if (error.code !== 'EEXIST') throw error;
  console.log('Existing infra/docker/.env preserved. No credentials changed.');
}
