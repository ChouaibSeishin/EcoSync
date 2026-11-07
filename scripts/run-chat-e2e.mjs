import {local} from './database-runtime.mjs';
import {spawn} from 'node:child_process';
const child=spawn(process.execPath,['node_modules/@playwright/test/cli.js','test','--config','playwright.chat.config.ts'],{cwd:'frontend',stdio:'inherit',env:{...process.env,E2E_BASE_URL:process.env.E2E_BASE_URL??'http://127.0.0.1:'+(local.WEB_PORT??5173),E2E_USERNAME:local.BOOTSTRAP_USERNAME,E2E_PASSWORD:local.BOOTSTRAP_PASSWORD}});child.on('error',()=>{console.error('Chat browser runner failed to start.');process.exitCode=1;});child.on('exit',code=>{process.exitCode=code??1;});
