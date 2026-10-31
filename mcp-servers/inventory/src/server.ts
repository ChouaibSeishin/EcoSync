import {createServer} from 'node:http';
import {timingSafeEqual} from 'node:crypto';
import {createPool} from 'mysql2/promise';
import {createMcpHandler} from '@modelcontextprotocol/server';
import {toNodeHandler} from '@modelcontextprotocol/node';
import {inventoryServer} from './queries.js';

const token=process.env.INVENTORY_MCP_TOKEN??'';
if(!/^[A-Za-z0-9_-]{32,128}$/.test(token))throw Error('MCP credential required');
const expected=Buffer.from('Bearer '+token);
const pool=createPool({host:process.env.INVENTORY_DB_HOST??'inventory',port:Number(process.env.INVENTORY_DB_PORT??3306),user:'inventory_reader',password:process.env.INVENTORY_READER_PASSWORD,database:'inventory',connectionLimit:4,waitForConnections:false,connectTimeout:5000,dateStrings:true,timezone:'Z',multipleStatements:false});
const handler=createMcpHandler(()=>inventoryServer(pool),{legacy:'reject',responseMode:'auto',keepAliveMs:0,maxRequestBodySize:65536});
const adapter=toNodeHandler({fetch:async request=>{
 const url=new URL(request.url);
 if(url.pathname==='/health/ready'){
  try{await pool.execute({sql:'SELECT 1 FROM inventory.items LIMIT 1',timeout:4000});return Response.json({ready:true});}catch{return Response.json({ready:false},{status:503});}
 }
 if(url.pathname!=='/mcp')return new Response(null,{status:404});
 if(request.headers.has('origin')||!['inventory-mcp','localhost','127.0.0.1'].includes(url.hostname))return new Response(null,{status:403});
 const auth=Buffer.from(request.headers.get('authorization')??'');
 if(auth.length!==expected.length||!timingSafeEqual(expected,auth))return new Response(null,{status:401,headers:{'WWW-Authenticate':'Bearer'}});
 return handler.fetch(request);
}});
const server=createServer({maxHeaderSize:8192},(req,res)=>{void adapter(req,res).catch(()=>{if(!res.headersSent)res.writeHead(500);res.end();});});
server.maxConnections=64;server.requestTimeout=30000;server.headersTimeout=5000;
server.listen(8092,'0.0.0.0');
for(const signal of ['SIGTERM','SIGINT'])process.on(signal,()=>{server.close();void handler.close().then(()=>pool.end()).finally(()=>process.exit(0));setTimeout(()=>process.exit(1),5000).unref();});
