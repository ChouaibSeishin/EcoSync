// Dependency-free documentation/pin validation. No application runtime claims.
import { readFile, readdir, stat, access } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const required = [
  'README.md', 'PROJECT_STATUS.md', 'ROADMAP.md', 'BACKLOG.md', 'config/versions.json',
  'docs/architecture/ARCHITECTURE.md', 'docs/architecture/COMPATIBILITY.md',
  'docs/architecture/SECURITY.md', 'docs/architecture/DATA_MODEL.md',
  'docs/development/LOCAL_SETUP.md', 'docs/development/PHASE_0_VERIFICATION.md',
  'docs/deployment/DEPLOYMENT.md', 'docs/research/METHODOLOGY.md',
  ...['0001-modular-platform', '0002-supported-dependencies', '0003-mcp-and-gateway',
    '0004-security-and-mappings', '0005-delivery-and-evaluation'].map(n => `docs/architecture/decisions/${n}.md`)
];
const failures = [];
for (const file of required) {
  try { await access(path.join(root, file)); }
  catch { failures.push(`Missing required deliverable: ${file}`); }
}
let pins = 0;
try {
  const v = JSON.parse(await readFile(path.join(root, 'config/versions.json'), 'utf8'));
  if (!/^\d{4}-\d{2}-\d{2}$/.test(v.verifiedOn)) failures.push('Missing dated version evidence.');
  for (const group of ['dependencies', 'devDependencies']) {
    for (const [name, value] of Object.entries(v.frontend[group])) {
      pins++;
      if (!/^\d+\.\d+\.\d+$/.test(value)) failures.push(`Unpinned frontend dependency: ${name}`);
    }
  }
  for (const [name, value] of Object.entries({ ...v.backend, ...v.managedObservations })) {
    pins++;
    if (!/^\d+\.\d+\.\d+$/.test(value)) failures.push(`Unpinned backend dependency: ${name}`);
  }
  if (v.toolchain.javaRelease !== '21') failures.push('Java baseline must match the Phase 0 decision.');
} catch (error) { failures.push(`Version manifest: ${error.message}`); }

async function markdownFiles(dir) {
  const result = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const target = path.join(dir, entry.name);
    if (entry.isDirectory()) result.push(...await markdownFiles(target));
    else if (entry.name.endsWith('.md')) result.push(target);
  }
  return result;
}
const docs = (await markdownFiles(path.join(root, 'docs'))).concat(
  ['README.md', 'PROJECT_STATUS.md', 'ROADMAP.md', 'BACKLOG.md'].map(f => path.join(root, f))
);
let links = 0;
for (const file of docs) {
  let source;
  try { source = await readFile(file, 'utf8'); } catch { continue; }
  if (!source.endsWith('\n')) failures.push(`Missing final newline: ${path.relative(root, file)}`);
  if ((source.match(/^```/gm) || []).length % 2 !== 0) failures.push(`Unclosed code fence: ${file}`);
  for (const match of source.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)) {
    const href = match[1];
    if (/^(?:https?:|mailto:|#)/.test(href)) continue;
    links++;
    const target = path.resolve(path.dirname(file), decodeURIComponent(href.split('#')[0]));
    const relative = path.relative(root, target);
    if (relative.startsWith('..') || path.isAbsolute(relative)) { failures.push(`Link leaves repository: ${href}`); continue; }
    try { if (!(await stat(target)).isFile()) failures.push(`Link is not a file: ${href}`); }
    catch { failures.push(`Broken link in ${path.relative(root, file)}: ${href}`); }
  }
}
if (failures.length) {
  failures.forEach(f => console.error(`FAIL: ${f}`));
  process.exitCode = 1;
} else {
  console.log(`PASS: ${required.length} required deliverables; ${docs.length} Markdown files; ${links} local file links; ${pins} exact dependency pins.`);
  console.log('Scope: documentation structure and declared pins only; no application tests or external-link availability asserted.');
}
