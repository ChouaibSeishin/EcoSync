import {createServer} from 'node:http';
import {McpServer,createMcpHandler,legacyStatelessFallback} from '@modelcontextprotocol/server';
import {toNodeHandler} from '@modelcontextprotocol/node';
import * as z from 'zod/v4';
export async function peer({era='modern',token,mode='auto'}){
 const state={calls:0,changed:false,description:'Read-only peer tool',methods:[],aborted:0,pagination:null,listCalls:0};
 const factory=()=>{
  const server=new McpServer({name:'official-'+era+'-peer',version:'1.0.0'});
  const input=state.changed?z.object({value:z.string().max(40),extra:z.boolean()}):z.object({value:z.string().max(40)});
  server.registerTool('lookup',{description:state.description,inputSchema:input,outputSchema:z.object({value:z.string().max(40)})},async({value})=>{state.calls++;return{content:[{type:'text',text:value}],structuredContent:{value}};});
  server.registerTool('delay',{description:'Cancellable peer read',inputSchema:z.object({value:z.string().max(40)})},async()=>{state.calls++;await new Promise(r=>setTimeout(r,1500));return{content:[{type:'text',text:'done'}]};});
  server.registerTool('large',{description:'Oversized peer response',inputSchema:z.object({})},async()=>{state.calls++;return{content:[{type:'text',text:'x'.repeat(1100000)}]};});
  if(state.pagination)server.server.setRequestHandler('tools/list',async request=>{
   const page=Number(request.params?.cursor??0);state.listCalls++;
   return {tools:[{name:'paged_'+page,description:'Page '+page,inputSchema:{type:'object',properties:{},additionalProperties:false}}],
    ...(state.pagination==='normal'&&page===1?{}:{nextCursor:state.pagination==='cycle'?'1':String(page+1)})};
  });
  return server;
 };
 const modern=createMcpHandler(factory,{legacy:'reject',responseMode:mode,keepAliveMs:0,maxRequestBodySize:65536});
 const legacy=legacyStatelessFallback(factory,()=>{},{maxRequestBodySize:65536});
 const adapter=toNodeHandler({fetch:async request=>{
  if(request.headers.get('authorization')!=='Bearer '+token)return new Response(null,{status:401});
  if(request.headers.get('origin')==='https://bad.invalid')return new Response(null,{status:403});
  const data=await request.clone().json().catch(()=>null);
  state.methods.push({method:data?.method,version:request.headers.get('mcp-protocol-version'),meta:data?.params?._meta?.['io.modelcontextprotocol/protocolVersion']});
  return era==='modern'?modern.fetch(request):legacy(request);
 }});
 const server=createServer((req,res)=>{void adapter(req,res).catch(()=>{if(!res.headersSent)res.writeHead(500);res.end();});});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const endpoint='http://127.0.0.1:'+server.address().port+'/mcp';
 return {state,endpoint,close:async()=>{await modern.close();server.closeAllConnections();await new Promise(r=>server.close(r));}};
}
