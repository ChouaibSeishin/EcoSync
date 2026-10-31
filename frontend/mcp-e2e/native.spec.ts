import {test,expect} from '@playwright/test';
import {randomUUID} from 'node:crypto';
import {mkdir} from 'node:fs/promises';
for(const kind of ['sales','inventory'])test(kind+' native MCP browser journey',async({page})=>{
 const username=process.env.E2E_USERNAME,password=process.env.E2E_PASSWORD,token=process.env[kind==='sales'?'SALES_MCP_TOKEN':'INVENTORY_MCP_TOKEN'];
 if(!username||!password||!token)throw Error('Generated local credentials required; use run-mcp-e2e.mjs.');
 await page.goto('/');await page.getByLabel('Username').fill(username);await page.getByLabel('Password').fill(password);await page.getByRole('button',{name:'Sign in to workspace'}).click();
 await expect(page.getByRole('heading',{name:'A clearer view starts here.'})).toBeVisible();await page.getByRole('link',{name:'Integrations',exact:true}).click();
 await page.getByRole('button',{name:'Add MCP server'}).click();const dialog=page.getByRole('dialog'),name=kind+' '+randomUUID().slice(0,8);
 await dialog.getByLabel('Display name').fill(name);await dialog.getByLabel('Endpoint URL').fill('http://'+kind+'-mcp:'+(kind==='sales'?8091:8092)+'/mcp');await dialog.getByLabel('Bearer credential',{exact:true}).fill(token);await dialog.getByLabel('Protocol compatibility').selectOption(kind==='sales'?'LEGACY_2025':'MODERN_2026');await dialog.getByRole('button',{name:'Register server'}).click();await expect(dialog).toBeHidden();
 await expect(page.getByRole('heading',{name,exact:true})).toBeVisible();await page.getByRole('button',{name:'Test connection & discover'}).click();
 const toolName=kind==='sales'?'sales_get_revenue_summary':'inventory_get_stock_by_sku';const tool=page.getByRole('article',{name:toolName});await expect(tool).toBeVisible();
 await expect(tool).toContainText(kind==='sales'?'2025-11-25':'2026-07-28');await tool.getByText('Input schema and required parameters').click();await expect(tool.locator('pre').first()).toContainText('required');
 await tool.getByRole('button',{name:'Approve admin',exact:true}).click();await expect(tool.getByRole('button',{name:'Revoke admin'})).toBeVisible();await page.getByRole('button',{name:'Activate integration'}).click();await expect(page.getByRole('button',{name:'Invoke tool'})).toBeEnabled();
 const args=kind==='sales'?{startInclusive:'2026-10-01T00:00:00Z',endExclusive:'2026-11-01T00:00:00Z',currency:'USD'}:{sku:'WH-700'};
 await page.getByLabel('JSON arguments').fill(JSON.stringify(args));await page.getByRole('button',{name:'Invoke tool'}).click();
 const output=page.getByLabel('Invocation result JSON');await expect(output).toBeVisible();const result=JSON.parse(await output.innerText());expect(result.untrusted).toBe(true);expect(result.result.isError).not.toBe(true);expect(result.result.structuredContent.source).toBe(kind==='sales'?'sales-postgresql':'inventory-mysql');
 if(kind==='sales')expect(result.result.structuredContent.rows[0].revenue).toBe('145444.58');else{const rows=result.result.structuredContent.rows;expect(rows).toHaveLength(3);for(const row of rows)expect(row.available_quantity).toBe(row.quantity_on_hand-row.quantity_reserved);}
 await expect(page.getByText('INVOKE · SUCCESS',{exact:true})).toBeVisible();expect(await page.locator('body').innerText()).not.toContain(token);
 await mkdir('../.verification/phase3',{recursive:true});await page.screenshot({path:'../.verification/phase3/'+kind+'-desktop.png',fullPage:true});
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:'../.verification/phase3/'+kind+'-mobile.png',fullPage:true});
 const session=await (await page.request.get('/api/session')).json(),org=session.workspaces[0].id,base='/api/organizations/'+org+'/integrations';const items=(await (await page.request.get(base)).json()).items;const integration=items.find((i:{display_name:string})=>i.display_name===name);const detail=await(await page.request.get(base+'/'+integration.id)).json();const cap=detail.tools.find((t:{tool:{name:string}})=>t.tool.name===toolName);
 await page.getByRole('button',{name:'Disable integration'}).click();await expect(page.getByText('No tools are approved for your roles.')).toBeVisible();const csrf=await(await page.request.get('/api/csrf')).json();const rejected=await page.request.post(base+'/'+integration.id+'/capabilities/'+cap.id+'/invocations',{headers:{[csrf.headerName]:csrf.token},data:{invocationId:randomUUID(),arguments:args}});expect(rejected.status()).toBe(409);
 // Preserve audit evidence in PostgreSQL while freeing the bounded registration slot.
 const currentCsrf=await(await page.request.get('/api/csrf')).json();expect((await page.request.delete(base+'/'+integration.id,{headers:{[currentCsrf.headerName]:currentCsrf.token}})).ok()).toBe(true);
});
