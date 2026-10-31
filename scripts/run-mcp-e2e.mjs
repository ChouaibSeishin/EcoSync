import {readFile} from 'node:fs/promises';
import {spawn} from 'node:child_process';
const read=async p=>Object.fromEntries((await readFile(p,'utf8')).split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1)];}));
const local=await read('infra/docker/.env'),mcp=await read('infra/docker/.env.mcp');
const child=spawn(process.execPath,['node_modules/@playwright/test/cli.js','test','--config','playwright.mcp.config.ts'],{cwd:'frontend',stdio:'inherit',env:{...process.env,E2E_BASE_URL:process.env.E2E_BASE_URL??'http://127.0.0.1:'+(local.WEB_PORT??5173),E2E_USERNAME:local.BOOTSTRAP_USERNAME,E2E_PASSWORD:local.BOOTSTRAP_PASSWORD,SALES_MCP_TOKEN:mcp.SALES_MCP_TOKEN,INVENTORY_MCP_TOKEN:mcp.INVENTORY_MCP_TOKEN}});
child.on('error',()=>{console.error('MCP browser runner failed to start.');process.exitCode=1;});child.on('exit',code=>{process.exitCode=code??1;});
