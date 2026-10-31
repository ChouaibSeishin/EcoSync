import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./mcp-e2e',timeout:120000,expect:{timeout:20000},fullyParallel:false,workers:1,retries:0,reporter:[['list']],use:{baseURL:process.env.E2E_BASE_URL??'http://127.0.0.1:5173',trace:'off',screenshot:'only-on-failure'}});
