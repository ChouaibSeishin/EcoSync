import { readFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../', import.meta.url));
let local = {};
try { local = Object.fromEntries((await readFile(path.join(root, 'infra/docker/.env'), 'utf8')).split(/\r?\n/).filter(l => l && !l.startsWith('#')).map(l => { const i = l.indexOf('='); return [l.slice(0,i), l.slice(i+1)]; })); }
catch { /* CI/operators can supply E2E credentials instead. */ }
const username = process.env.E2E_USERNAME ?? local.BOOTSTRAP_USERNAME;
const password = process.env.E2E_PASSWORD ?? local.BOOTSTRAP_PASSWORD;
if (!username || !password) throw new Error('Initialize the local environment or supply E2E_USERNAME/E2E_PASSWORD.');
const base = process.env.E2E_BASE_URL ?? ('http://127.0.0.1:' + (local.WEB_PORT ?? '5173'));
const deadline = Date.now() + 120_000;
let ready = false;
while (Date.now() < deadline) {
  try { if ((await fetch(base + '/actuator/health/readiness', { signal: AbortSignal.timeout(4000) })).ok) { ready = true; break; } } catch { }
  await new Promise(resolve => setTimeout(resolve, 1000));
}
if (!ready) throw new Error('Local stack did not become ready before browser verification.');
const child = spawn(process.execPath, [path.join(root, 'frontend/node_modules/@playwright/test/cli.js'), 'test'], {
  cwd: path.join(root, 'frontend'), stdio: 'inherit',
  env: { ...process.env, E2E_USERNAME: username, E2E_PASSWORD: password, E2E_BASE_URL: base }
});
child.on('error', error => { console.error(error.message); process.exitCode = 1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
