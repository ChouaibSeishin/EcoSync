import {Ajv} from 'ajv';
import {Ajv2020} from 'ajv/dist/2020.js';
import {createHash} from 'node:crypto';
import {ClientFailure} from './errors.js';
export const canonical=(value:unknown):unknown=>value&&typeof value==='object'?Array.isArray(value)?value.map(canonical):Object.fromEntries(Object.entries(value).sort(([a],[b])=>a.localeCompare(b,'en')).map(([k,v])=>[k,canonical(v)])):value;
export const fingerprint=(value:unknown)=>createHash('sha256').update(JSON.stringify(canonical(value))).digest('hex');
const allowed=new Set(['$schema','type','properties','required','additionalProperties','items','minItems','maxItems','minLength','maxLength','minimum','maximum','enum','description','title','format']);
const types=new Set(['object','array','string','integer','number','boolean','null']);
function compiler(schema:Record<string,unknown>){
 const options={strict:true,allErrors:false,coerceTypes:false,removeAdditional:false,validateFormats:true};
 const ajv=schema.$schema==='http://json-schema.org/draft-07/schema#'?new Ajv(options):new Ajv2020(options);
 ajv.addFormat('date-time',(s:string)=>{
  if(!/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:[.][0-9]{1,3})?Z$/.test(s))return false;
  const d=new Date(s),normalized=s.includes('.')?s.replace(/\.([0-9]{1,3})Z$/,(_,f:string)=>'.'+f.padEnd(3,'0')+'Z'):s.replace('Z','.000Z');
  return Number.isFinite(d.valueOf())&&d.toISOString()===normalized;
 });
 return ajv.compile(schema);
}
export function schemaIssue(schema:unknown):string|null {
 if(Buffer.byteLength(JSON.stringify(schema)??'')>32768)return 'SCHEMA_TOO_LARGE';
 let nodes=0;
 function visit(value:unknown,depth:number):void{
  if(depth>8||++nodes>200)throw Error('SCHEMA_COMPLEXITY');
  if(!value||typeof value!=='object'||Array.isArray(value))throw Error('SCHEMA_UNSUPPORTED');
  const s=value as Record<string,unknown>;
  for(const k of Object.keys(s))if(!allowed.has(k))throw Error('SCHEMA_UNSUPPORTED');
  const ts=Array.isArray(s.type)?s.type:[s.type];
  if(!ts.length||ts.length>2||!ts.every(t=>typeof t==='string'&&types.has(t)))throw Error('SCHEMA_UNSUPPORTED');
  if(ts.length===2&&!ts.includes('null'))throw Error('SCHEMA_UNSUPPORTED');
  if(s.$schema!==undefined&&!['https://json-schema.org/draft/2020-12/schema','http://json-schema.org/draft-07/schema#'].includes(String(s.$schema)))throw Error('SCHEMA_DIALECT_UNSUPPORTED');
  if(s.format!==undefined&&s.format!=='date-time')throw Error('SCHEMA_FORMAT_UNSUPPORTED');
  if(s.enum!==undefined&&(!Array.isArray(s.enum)||s.enum.length>100||s.enum.some(v=>v!==null&&typeof v==='object')))throw Error('SCHEMA_UNSUPPORTED');
  if(ts.includes('object')){
   if((s.additionalProperties!==undefined&&typeof s.additionalProperties!=='boolean')||!s.properties||typeof s.properties!=='object'||Array.isArray(s.properties))throw Error('SCHEMA_UNSUPPORTED');
   const properties=s.properties as Record<string,unknown>;
   if(Object.keys(properties).length>40)throw Error('SCHEMA_COMPLEXITY');
   for(const [key,child]of Object.entries(properties)){
    if(['__proto__','prototype','constructor'].includes(key)||key.length>80)throw Error('SCHEMA_UNSUPPORTED');
    visit(child,depth+1);
   }
   if(s.required!==undefined&&(!Array.isArray(s.required)||s.required.some(k=>typeof k!=='string'||!Object.hasOwn(properties,k))))throw Error('SCHEMA_UNSUPPORTED');
  }
  if(ts.includes('array')){if(typeof s.maxItems!=='number'||s.maxItems<0||s.maxItems>100)throw Error('SCHEMA_UNBOUNDED_ARRAY');visit(s.items,depth+1);}
  if(ts.includes('string')&&typeof s.maxLength==='number'&&s.maxLength>16384)throw Error('SCHEMA_COMPLEXITY');
 }
 try{visit(schema,0);compiler(schema as Record<string,unknown>);return null;}catch(e){const message=(e as Error).message;return message.startsWith('SCHEMA_')?message:'SCHEMA_INVALID';}
}
export function validateArguments(schema:unknown,value:unknown,maxBytes=16384):void {
 if(schemaIssue(schema))throw new ClientFailure('MCP_SCHEMA_UNSUPPORTED',422);
 if(Buffer.byteLength(JSON.stringify(value)??'')>maxBytes)throw new ClientFailure('MCP_ARGUMENTS_TOO_LARGE',413);
 const object=schema as Record<string,unknown>;
 try{if(!compiler(object)(value))throw new ClientFailure('MCP_INVALID_ARGUMENTS',422);}
 catch(e){if(e instanceof ClientFailure)throw e;throw new ClientFailure('MCP_SCHEMA_UNSUPPORTED',422);}
}
export function redact(value:unknown,secrets:readonly string[]):unknown{
 if(typeof value==='string')return secrets.filter(s=>s.length>=16).reduce((text,secret)=>text.replaceAll(secret,'[REDACTED]'),value);
 if(Array.isArray(value))return value.map(v=>redact(v,secrets));
 if(value&&typeof value==='object')return Object.fromEntries(Object.entries(value).map(([k,v])=>[redact(k,secrets) as string,redact(v,secrets)]));
 return value;
}
