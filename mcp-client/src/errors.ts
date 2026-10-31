export class ClientFailure extends Error {
 constructor(public readonly code:string,public readonly status=502){super(code);}
}
export function failureCode(error:unknown):string {
 if(error instanceof ClientFailure)return error.code;
 const e=error as {code?:string|number;status?:number;statusCode?:number;data?:{status?:number};name?:string};
 const status=e?.status??e?.statusCode??e?.data?.status;
 if(status===401||status===403)return 'MCP_AUTHENTICATION_FAILED';
 if(e?.code==='REQUEST_TIMEOUT'||e?.name==='TimeoutError'||e?.name==='AbortError')return 'MCP_TIMEOUT';
 if(e?.code==='INVALID_RESULT'||e?.code==='UNSUPPORTED_RESULT_TYPE')return 'MCP_INVALID_RESPONSE';
 if(e?.code===-32022||e?.code==='ERA_NEGOTIATION_FAILED'||e?.code==='VERSION_NEGOTIATION_FAILED'||e?.code==='METHOD_NOT_SUPPORTED_BY_PROTOCOL_VERSION')return 'MCP_UNSUPPORTED_PROTOCOL';
 if(e?.code==='LIST_PAGINATION_EXCEEDED')return 'MCP_DISCOVERY_LIMIT';
 return 'MCP_UNAVAILABLE';
}
