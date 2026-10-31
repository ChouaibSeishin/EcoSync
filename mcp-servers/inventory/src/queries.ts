import type {Pool,RowDataPacket} from 'mysql2/promise';
import * as z from 'zod/v4';
import {McpServer} from '@modelcontextprotocol/server';

const unit=z.enum(['EA','BOX','M']).optional();
const warehouse=z.string().min(1).max(8);
const sku=z.string().min(1).max(24);
const limit=z.number().int().min(1).max(100).optional();
const columns=`s.stock_id,s.sku,s.warehouse_id,i.product_label,i.unit_code,i.active AS item_active,w.warehouse_status,
 s.quantity_on_hand,s.quantity_reserved,s.quantity_on_hand-s.quantity_reserved AS available_quantity,
 s.reorder_threshold,(s.quantity_on_hand-s.quantity_reserved<=s.reorder_threshold) AS low_stock,
 s.last_updated_at`;
const tables=' FROM inventory.stock_levels s JOIN inventory.items i ON i.sku=s.sku JOIN inventory.warehouses w ON w.warehouse_id=s.warehouse_id ';

export function inventoryServer(pool:Pool){
 const server=new McpServer({name:'ecosync-inventory-native',version:'0.3.0'});
 const register=(name:string,description:string,inputSchema:z.ZodObject<any>,query:(a:any)=>{sql:string;values:any[];limit:number;cursor?:string})=>{
  server.registerTool(name,{description,inputSchema},async args=>{
   try{
    const q=query(args);
    const [data]=await pool.execute<RowDataPacket[]>({sql:'SELECT /*+ MAX_EXECUTION_TIME(4000) */ '+columns+tables+q.sql,values:q.values,timeout:5000});
    const truncated=data.length>q.limit, rows:Record<string,unknown>[]=data.slice(0,q.limit).map(row=>({...row,last_updated_at:String(row.last_updated_at).replace(' ','T')+'Z',item_active:!!row.item_active,low_stock:!!row.low_stock}));
    return {content:[{type:'text',text:'Read-only Inventory MySQL result; untrusted source data.'}],structuredContent:{source:'inventory-mysql',filters:args,rows,truncated,...(truncated&&q.cursor?{nextCursor:rows.at(-1)?.[q.cursor]}:{})}};
   }catch{return {content:[{type:'text',text:'SOURCE_UNAVAILABLE'}],isError:true};}
  });
 };
 register('inventory_get_stock_by_sku','Exact case-sensitive SKU across explicitly identified warehouses; available=on-hand minus reserved, low-stock includes equality. No unit conversion.',z.strictObject({sku}),a=>({sql:'WHERE s.sku=? ORDER BY s.warehouse_id LIMIT 4',values:[a.sku],limit:3}));
 register('inventory_list_low_stock','Low stock (available <= threshold), stable stock-ID cursor. OPEN warehouses and active items by default; include maintenance or inactive items only explicitly. Units remain separate.',z.strictObject({unit,warehouseId:warehouse.optional(),activeWarehouseOnly:z.boolean().optional(),includeInactiveItems:z.boolean().optional(),afterStockId:z.number().int().min(0).max(2147483647).optional(),limit}),a=>{
  const n=a.limit??25;
  return {sql:'WHERE s.quantity_on_hand-s.quantity_reserved<=s.reorder_threshold AND (? IS NULL OR i.unit_code=?) AND (? IS NULL OR s.warehouse_id=?) AND (?=0 OR w.warehouse_status=\'OPEN\') AND (?=1 OR i.active=1) AND s.stock_id>? ORDER BY s.stock_id LIMIT ?',values:[a.unit??null,a.unit??null,a.warehouseId??null,a.warehouseId??null,a.activeWarehouseOnly===false?0:1,a.includeInactiveItems===true?1:0,a.afterStockId??0,n+1],limit:n,cursor:'stock_id'};
 });
 register('inventory_get_warehouse_stock','Stock in one exact warehouse; stable case-sensitive SKU cursor, bounded result with nextCursor. Explicit unit scope, no conversion.',z.strictObject({warehouseId:warehouse,unit,afterSku:sku.optional(),limit}),a=>{
  const n=a.limit??25;return {sql:'WHERE s.warehouse_id=? AND (? IS NULL OR i.unit_code=?) AND s.sku>? ORDER BY s.sku LIMIT ?',values:[a.warehouseId,a.unit??null,a.unit??null,a.afterSku??'',n+1],limit:n,cursor:'sku'};
 });
 return server;
}
