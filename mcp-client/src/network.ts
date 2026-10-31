import {lookup as systemLookup} from 'node:dns/promises';
import {isIP} from 'node:net';
import http from 'node:http';
import https from 'node:https';
import {Readable} from 'node:stream';
import {AsyncLocalStorage} from 'node:async_hooks';
import {ClientFailure} from './errors.js';
export interface OperationContext {controller:AbortController;deadline:number;bytes:number;limitExceeded:boolean;redirected:boolean;authenticationFailed:boolean;cancelled:boolean;transportCode?:string;}
export const operations=new AsyncLocalStorage<OperationContext>();
export type Lookup=(host:string)=>Promise<{address:string;family:number}[]>;
function ipv4Equivalent(address:string):string|undefined {
 if(isIP(address)===4)return address;
 if(isIP(address)!==6)return undefined;
 let ip=address.toLowerCase();
 if(ip.includes('.')){
  const at=ip.lastIndexOf(':'),v=ip.slice(at+1).split('.').map(Number);
  ip=ip.slice(0,at+1)+((v[0]!<<8)|v[1]!).toString(16)+':'+((v[2]!<<8)|v[3]!).toString(16);
 }
 const parts=ip.split('::'),left=parts[0]?parts[0].split(':'):[],right=parts[1]?parts[1].split(':'):[];
 const words=(parts.length===2?[...left,...Array(8-left.length-right.length).fill('0'),...right]:left).map(s=>parseInt(s,16));
 if(words.length===8&&words.slice(0,5).every(n=>n===0)&&words[5]===65535)return [words[6]!>>>8,words[6]!&255,words[7]!>>>8,words[7]!&255].join('.');
 return undefined;
}
export function privateAddress(address:string):boolean{
 const ip=address.toLowerCase();
 const mapped=ipv4Equivalent(ip);if(mapped&&mapped!==ip)return privateAddress(mapped);
 if(isIP(ip)===4){
  const a=ip.split('.').map(Number),x=a[0]!,y=a[1]!;
  return x===0||x===10||x===127||x===169&&y===254||x===172&&y>=16&&y<=31||x===192&&y===168||x===100&&y>=64&&y<=127||x>=224||x===198&&(y===18||y===19);
 }
 if(isIP(ip)===6)return !/^[23]/.test(ip)||ip.startsWith('2001:db8:');
 return true;
}
export class DestinationPolicy {
 readonly allowed:Map<string,URL>;
 constructor(endpoints:string[],readonly development:boolean,readonly lookup:Lookup=async host=>systemLookup(host,{all:true,verbatim:true})){
  this.allowed=new Map(endpoints.map(value=>{
   const url=this.parse(value);
   if(url.protocol!=='https:'&&(!development||url.protocol!=='http:'))throw new ClientFailure('MCP_DESTINATION_DENIED',422);
   return[url.href,url];
  }));
 }
 private parse(value:string):URL{
  let url:URL;try{url=new URL(value);}catch{throw new ClientFailure('MCP_DESTINATION_DENIED',422);}
  if(value.length>512||url.username||url.password||url.search||url.hash||!url.hostname||!['http:','https:'].includes(url.protocol)||/%|\\/.test(value)||value!==url.href)throw new ClientFailure('MCP_DESTINATION_DENIED',422);
  return url;
 }
 validate(value:string):URL{
  const url=this.parse(value);if(!this.allowed.has(url.href))throw new ClientFailure('MCP_DESTINATION_DENIED',422);return url;
 }
 async resolve(value:string):Promise<{url:URL;address:string;family:number}>{
  const url=this.validate(value),host=url.hostname.replace(/^\[|\]$/g,'');
  const addresses=isIP(host)?[{address:host,family:isIP(host)}]:await this.lookup(host);
  if(!addresses.length||addresses.length>16)throw new ClientFailure('MCP_DESTINATION_DENIED',422);
  for(const record of addresses){
   const address=record.address.toLowerCase(),normalized=ipv4Equivalent(address)??address;
   const metadata=normalized.startsWith('169.254.')||normalized==='100.100.100.200'||normalized==='0.0.0.0'||isIP(address)===6&&!ipv4Equivalent(address)&&address!=='::1'&&privateAddress(address);
   if(!isIP(address)||metadata||privateAddress(address)&&!(this.development&&url.protocol==='http:'))throw new ClientFailure('MCP_DESTINATION_DENIED',422);
  }
  return {url,...addresses[0]!};
 }
}
export function guardedFetch(policy:DestinationPolicy,endpoint:string):typeof fetch {
 return async(input:RequestInfo|URL,init?:RequestInit):Promise<Response>=>{
  const request=new Request(input,init);
  if(request.url!==endpoint||!['POST','GET','DELETE'].includes(request.method))throw new ClientFailure('MCP_DESTINATION_DENIED',422);
  const context=operations.getStore();
  let target:Awaited<ReturnType<DestinationPolicy['resolve']>>;
  try{target=await policy.resolve(request.url);}catch(error){if(context)context.transportCode=error instanceof ClientFailure?error.code:'MCP_UNAVAILABLE';throw error;}
  const {url,address,family}=target;
  const remaining=Math.max(1,Math.min(10000,(context?.deadline??Date.now()+10000)-Date.now()));
  const signal=AbortSignal.any([request.signal,AbortSignal.timeout(remaining),...(context?[context.controller.signal]:[])]);
  const body=request.method==='POST'?Buffer.from(await request.arrayBuffer()):undefined;
  if(body&&body.length>65536)throw new ClientFailure('MCP_ARGUMENTS_TOO_LARGE',413);
  return new Promise<Response>((resolve,reject)=>{
   const send=url.protocol==='https:'?https.request:http.request;
   const headers=Object.fromEntries(request.headers);
   if(body)headers['content-length']=String(body.length);
   const req=send(url,{method:request.method,headers,signal,
    // Resolve once, validate every returned address, and pin this socket to one
    // approved address. HTTPS still verifies the original URL hostname.
    lookup:(_hostname,options,callback)=>{
     if(typeof options==='object'&&options.all)callback(null,[{address,family}]);
     else callback(null,address,family);
    }
   },response=>{
    const status=response.statusCode??502;
    if(status>=500&&context)context.transportCode='MCP_UNAVAILABLE';
    if(status>=300&&status<400){if(context)context.redirected=true;response.destroy();reject(new ClientFailure('MCP_REDIRECT_DENIED'));return;}
    if(status===401||status===403){if(context)context.authenticationFailed=true;}
    const resultHeaders=new Headers();
    for(const[k,v]of Object.entries(response.headers))if(v!==undefined)for(const value of Array.isArray(v)?v:[String(v)])resultHeaders.append(k,value);
    if(status===204||status===205||status===304){response.resume();resolve(new Response(null,{status,headers:resultHeaders}));return;}
    let bytes=0;
    const bounded=(Readable.toWeb(response) as ReadableStream<Uint8Array>).pipeThrough(new TransformStream<Uint8Array,Uint8Array>({
     transform(chunk,controller){
      bytes+=chunk.byteLength;if(context)context.bytes+=chunk.byteLength;
      if(bytes>1048576||(context?.bytes??0)>2097152){if(context)context.limitExceeded=true;response.destroy();controller.error(new ClientFailure('MCP_RESPONSE_TOO_LARGE'));return;}
      controller.enqueue(chunk);
     }
    }));
    resolve(new Response(bounded,{status,headers:resultHeaders}));
   });
   req.setTimeout(5000,()=>req.destroy(new ClientFailure('MCP_TIMEOUT',504)));
   req.on('error',error=>{if(context&&!context.transportCode)context.transportCode=error instanceof ClientFailure?error.code:'MCP_UNAVAILABLE';reject(error);});if(body)req.end(body);else req.end();
  });
 };
}
