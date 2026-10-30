import {buildApp} from './app.js';
const app=buildApp({token:process.env.SUPPLIER_API_TOKEN??''});
app.server.maxHeadersCount=32;
app.server.maxRequestsPerSocket=100;
for(const signal of ['SIGTERM','SIGINT'])process.on(signal,()=>{void app.close().then(()=>process.exit(0));});
try {await app.listen({host:'0.0.0.0',port:8081});console.log('Supplier snapshot API listening on 8081.');}
catch {console.error('Supplier API startup failed.');process.exitCode=1;}
