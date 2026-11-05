import http from 'node:http';
import {timingSafeEqual} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {gzipSync} from 'node:zlib';
import {fileURLToPath} from 'node:url';
export const partners=Object.freeze(Array.from({length:125},(_,i)=>Object.freeze({code:'PT-'+String(i+1).padStart(3,'0'),name:i===0?'River & Pine':'Partner '+String(i+1).padStart(3,'0'),tier:i%2?'GOLD':'SILVER',enabled:i%3!==0})));
export function createPartnerServer(key,{verification=false}={}){
 if(!/^[A-Za-z0-9_-]{32,128}$/.test(key))throw Error('Partner fixture requires an independent credential.');let requests=0,authenticated=0;
 const send=(res,status,body,headers={})=>{const bytes=Buffer.from(JSON.stringify(body));res.writeHead(status,{'Content-Type':'application/json','Content-Length':bytes.length,'Cache-Control':'no-store',...headers});res.end(bytes);};
 return http.createServer({requestTimeout:5000,headersTimeout:4000,maxHeaderSize:8192},async(req,res)=>{
  try{
   if(req.url==='/health/ready'&&req.method==='GET')return send(res,200,{ready:true});
   if(req.url==='/openapi.json'&&req.method==='GET'){const bytes=await readFile(new URL('./openapi.json',import.meta.url));res.writeHead(200,{'Content-Type':'application/json','Content-Length':bytes.length});return res.end(bytes);}
   const supplied=Buffer.from(String(req.headers['x-partner-key']??'')),expected=Buffer.from(key);const authorized=supplied.length===expected.length&&timingSafeEqual(supplied,expected);
   if(verification&&req.url==='/__verification/counts'){if(!authorized)return send(res,401,{code:'UNAUTHORIZED'});return send(res,200,{requests,authenticated});}
   requests++;if(!authorized)return send(res,401,{code:'UNAUTHORIZED'});authenticated++;
   if(req.method!=='GET')return send(res,405,{code:'GET_ONLY'});if(req.url.length>4096||req.headers['transfer-encoding']||Number(req.headers['content-length']??0)>0)return send(res,400,{code:'INVALID_REQUEST'});
   const url=new URL(req.url,'http://partner');for(const name of url.searchParams.keys())if(url.searchParams.getAll(name).length!==1)return send(res,400,{code:'DUPLICATE_PARAMETER'});
   if(verification&&url.pathname.startsWith('/directory/v2/probes/')){
    const kind=url.pathname.split('/').at(-1);
    if(kind==='redirect'){res.writeHead(302,{Location:'http://169.254.169.254/latest/meta-data/'});return res.end();}
    if(kind==='loopback'){res.writeHead(307,{Location:'http://127.0.0.1:8080/'});return res.end();}
    if(kind==='cross-origin'){res.writeHead(301,{Location:'https://unapproved.invalid/'});return res.end();}
    if(kind==='downgrade'){res.writeHead(302,{Location:'http://unapproved.invalid/'});return res.end();}
    if(kind==='unauthorized')return send(res,401,{code:'REJECTED'});
    if(kind==='forbidden')return send(res,403,{code:'FORBIDDEN'});
    if(kind==='missing')return send(res,404,{code:'MISSING'});
    if(kind==='failure')return send(res,503,{code:'UNAVAILABLE'});
    if(kind==='html'){res.writeHead(200,{'Content-Type':'text/html'});return res.end('<html>untrusted</html>');}
    if(kind==='invalid')return send(res,200,{records:'wrong-schema'});
    if(kind==='large')return send(res,200,{blob:'x'.repeat(300000)});
    if(kind==='deep'){res.writeHead(200,{'Content-Type':'application/json'});return res.end('{"x":'.repeat(45)+'0'+'}'.repeat(45));}
    if(kind==='gzip-bomb'){const bytes=gzipSync(JSON.stringify({blob:'x'.repeat(300000)}));res.writeHead(200,{'Content-Type':'application/json','Content-Encoding':'gzip','Content-Length':bytes.length});return res.end(bytes);}
    if(kind==='slow'){const timer=setTimeout(()=>send(res,200,{ok:true}),6000);timer.unref();req.on('close',()=>clearTimeout(timer));return;}
    if(kind==='delay'){res.writeHead(200,{'Content-Type':'application/json','Cache-Control':'no-store'});res.write('{');let ticks=0;const timer=setInterval(()=>{if(++ticks<10)res.write(' ');else{clearInterval(timer);res.end('"ok":true}');}},500);timer.unref();res.on('close',()=>clearInterval(timer));return;}
    return send(res,200,{ok:true});
   }
   if(url.pathname==='/directory/v2/partners'){
    for(const name of url.searchParams.keys())if(!['after','limit','tier','name'].includes(name))return send(res,400,{code:'UNKNOWN_PARAMETER'});
    const after=url.searchParams.get('after')??'0',limit=url.searchParams.get('limit')??'5',tier=url.searchParams.get('tier'),name=url.searchParams.get('name');
    if(!/^(0|[1-9][0-9]{0,3})$/.test(after)||Number(after)>125||!/^[1-9][0-9]{0,2}$/.test(limit)||Number(limit)>125||tier&&!['GOLD','SILVER'].includes(tier)||name!==null&&(name.length<1||name.length>80))return send(res,400,{code:'INVALID_PARAMETER'});
    const matching=partners.filter(p=>(!tier||p.tier===tier)&&(!name||p.name===name)),start=Number(after),records=matching.slice(start,start+Number(limit));return send(res,200,{records,page:{after:start,next:Math.min(start+records.length,matching.length),hasMore:start+records.length<matching.length},total:matching.length});
   }
   const match=/^\/directory\/v2\/partners\/([^/]+)$/.exec(url.pathname);if(match){if(url.search)return send(res,400,{code:'UNKNOWN_PARAMETER'});const code=decodeURIComponent(match[1]);if(!/^PT-[0-9]{3}$/.test(code))return send(res,400,{code:'INVALID_CODE'});const partner=partners.find(p=>p.code===code);return partner?send(res,200,{partner}):send(res,404,{code:'NOT_FOUND'});}
   return send(res,404,{code:'NOT_FOUND'});
  }catch{if(!res.headersSent)send(res,400,{code:'INVALID_REQUEST'});else res.destroy();}
 });
}
if(process.argv[1]===fileURLToPath(import.meta.url)){const server=createPartnerServer(process.env.PARTNER_API_KEY??'',{verification:process.env.PARTNER_VERIFICATION==='true'});server.listen(8095,'0.0.0.0');for(const signal of ['SIGTERM','SIGINT'])process.on(signal,()=>server.close(()=>process.exit(0)));}
