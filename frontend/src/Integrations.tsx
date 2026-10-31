import {useState,type FormEvent} from 'react';
import {useQuery,useQueryClient} from '@tanstack/react-query';
import * as Dialog from '@radix-ui/react-dialog';
import {request,ApiError,type Workspace} from './api';

type Approval={role:string;status:string;actor:string;created_at:string};
type Capability={id:string;alias:string;present:boolean;protocol:string;server:Record<string,unknown>;tool:{name:string;description:string;inputSchema:Record<string,unknown>;outputSchema?:Record<string,unknown>;annotations?:unknown;metadata?:unknown;fingerprint:string;supported:boolean;unsupportedReason?:string};approvals:Approval[]};
type Event={id:string;actor:string;version:number;capability_id?:string;protocol?:string;kind:string;status:string;authorization_outcome:string;error_code?:string;started_at:string;duration_ms?:number};
export type Integration={id:string;display_name:string;endpoint:string;state:string;health:string;configuration_version:number;protocolPolicy?:string;protocol_policy?:string;last_connected_at?:string;last_discovered_at?:string;tools?:Capability[];events?:Event[]};
type Listing={available:boolean;canManage?:boolean;items:Integration[]};
const formatted=(value:unknown)=>JSON.stringify(value,null,2);
const approved=(cap:Capability,role:string)=>cap.approvals.some(a=>a.role===role&&a.status==='APPROVED');
const jsonOptions=(method:string,body?:unknown):RequestInit=>({method,headers:{'Content-Type':'application/json'},...(body===undefined?{}:{body:JSON.stringify(body)})});

function Registration({initial,save}:{initial?:Integration;save:(input:unknown)=>Promise<void>}){
 const [name,setName]=useState(initial?.display_name??'');const [endpoint,setEndpoint]=useState(initial?.endpoint??'');
 const [policy,setPolicy]=useState(initial?.protocolPolicy??initial?.protocol_policy??'AUTO_DUAL');const [credential,setCredential]=useState('');
 const [pending,setPending]=useState(false),[error,setError]=useState('');
 async function submit(event:FormEvent){event.preventDefault();setPending(true);setError('');try{await save({displayName:name,endpoint,protocolPolicy:policy,credential});setCredential('');}catch(e){setCredential('');setError(e instanceof Error?e.message:'Registration failed.');}finally{setPending(false);}}
 return <form className="mcp-form" aria-label={initial?'Edit MCP server':'Register MCP server'} onSubmit={e=>void submit(e)}>
  <p className="muted">1. Register → 2. Test and discover → 3. Review tools → 4. Activate</p>
  <label>Display name<input required maxLength={100} value={name} onChange={e=>setName(e.target.value)}/></label>
  <label>Endpoint URL<input required type="url" maxLength={512} value={endpoint} onChange={e=>setEndpoint(e.target.value)} placeholder="https://approved-server.example/mcp"/></label>
  <p className="muted">Use an endpoint permitted by your deployment operator.</p>
  <label>Transport<select value="STREAMABLE_HTTP" disabled><option value="STREAMABLE_HTTP">Streamable HTTP</option></select></label>
  <label>Authentication<select value="BEARER" disabled><option value="BEARER">Bearer credential</option></select></label>
  <label>{initial?'Replacement bearer credential (optional)':'Bearer credential'}<input required={!initial} type="password" autoComplete="new-password" minLength={16} maxLength={2048} value={credential} onChange={e=>setCredential(e.target.value)}/></label>
  <p className="muted">Credentials are encrypted and never shown again. Editing creates a new revision and requires review again.</p>
  <label>Protocol compatibility<select value={policy} onChange={e=>setPolicy(e.target.value)}><option value="AUTO_DUAL">Automatic: tested legacy and modern</option><option value="LEGACY_2025">Legacy 2025-11-25</option><option value="MODERN_2026">Modern 2026-07-28</option></select></label>
  {error&&<p role="alert" className="form-error">{error}</p>}
  <button className="button primary" disabled={pending} type="submit">{pending?'Saving…':initial?'Save new revision':'Register server'}</button>
 </form>;
}
function RegistrationDialog({initial,save}:{initial?:Integration;save:(input:unknown)=>Promise<void>}){
 const [open,setOpen]=useState(false);
 return <Dialog.Root open={open} onOpenChange={setOpen}><Dialog.Trigger asChild><button className="button secondary">{initial?'Edit configuration':'Add MCP server'}</button></Dialog.Trigger><Dialog.Portal><Dialog.Overlay className="dialog-overlay"/><Dialog.Content className="dialog mcp-dialog"><Dialog.Title>{initial?'Edit MCP server':'Add MCP server'}</Dialog.Title><Dialog.Description>Register a native remote MCP server for this organization.</Dialog.Description><Registration initial={initial} save={async data=>{await save(data);setOpen(false);}}/><Dialog.Close asChild><button className="icon-button dialog-close" aria-label="Close registration">×</button></Dialog.Close></Dialog.Content></Dialog.Portal></Dialog.Root>;
}

export function Integrations({workspace}:{workspace:Workspace}){
 const cache=useQueryClient(),base='/api/organizations/'+workspace.id+'/integrations';
 const list=useQuery({queryKey:['integrations',workspace.id],queryFn:()=>request<Listing>(base),refetchInterval:10000});
 const [selected,setSelected]=useState(''),[busy,setBusy]=useState(''),[error,setError]=useState('');
 const [toolId,setToolId]=useState(''),[argumentsText,setArgumentsText]=useState('{}'),[running,setRunning]=useState(''),[result,setResult]=useState<unknown>();
 const selectedId=list.data?.items.some(i=>i.id===selected)?selected:list.data?.items[0]?.id??'';
 const detail=useQuery({queryKey:['integration',workspace.id,selectedId],queryFn:()=>request<Integration>(base+'/'+selectedId),enabled:!!selectedId,refetchInterval:5000});
 const integration=detail.data,canManage=list.data?.canManage===true&&workspace.roles.includes('INTEGRATION_ADMIN');
 async function refresh(){await Promise.all([cache.invalidateQueries({queryKey:['integrations',workspace.id]}),cache.invalidateQueries({queryKey:['integration',workspace.id]}),cache.invalidateQueries({queryKey:['overview',workspace.id]})]);}
 async function action(label:string,path:string,method='POST',body?:unknown){setBusy(label);setError('');try{await request(base+path,jsonOptions(method,body));await refresh();}catch(e){setError(e instanceof Error?e.message:'Operation failed.');await refresh();}finally{setBusy('');}}
 const tools=integration?.tools??[];
 const usable=tools.filter(cap=>cap.present&&cap.tool.supported&&workspace.roles.some(role=>approved(cap,role)));
 const chosen=usable.find(cap=>cap.id===toolId)??usable[0];
 async function invoke(event:FormEvent){
  event.preventDefault();setError('');setResult(undefined);
  if(!chosen||!integration)return;
  let args:unknown;try{args=JSON.parse(argumentsText);if(!args||Array.isArray(args)||typeof args!=='object')throw Error();}catch{setError('Enter one valid JSON object for the tool arguments.');return;}
  const invocationId=crypto.randomUUID();setRunning(invocationId);
  try{setResult(await request(base+'/'+integration.id+'/capabilities/'+chosen.id+'/invocations',jsonOptions('POST',{invocationId,arguments:args})));}
  catch(e){setError(e instanceof ApiError?e.message+(e.requestId?' · Request '+e.requestId:''):e instanceof Error?e.message:'Invocation failed.');}
  finally{setRunning('');await refresh();}
 }
 return <><div className="heading-row"><div className="page-heading"><span className="eyebrow">YOUR CONNECTED ECOSYSTEM</span><h1>Integrations</h1><p>Review native MCP capabilities before your team uses them.</p></div>{list.data?.available&&canManage&&<RegistrationDialog save={async input=>{const created=await request<Integration>(base,jsonOptions('POST',input));setSelected(created.id);await refresh();}}/>}</div>
  {list.isPending?<p role="status">Loading integrations…</p>:list.error?<div role="alert" className="error-panel">{list.error.message}<button className="button secondary" onClick={()=>void list.refetch()}>Try again</button></div>:!list.data.available?<section className="empty-card"><h2>No integrations yet</h2><p>Connection registration is not available in this release.</p></section>:<>
   <p className="muted">Organization: {workspace.name} · Native MCP only · Tool text and results are untrusted source data.</p>
   {error&&<p role="alert" className="form-error">{error}</p>}{busy&&<p role="status">{busy}…</p>}
   {!list.data.items.length?<section className="empty-card"><h2>No integrations yet</h2><p>{canManage?'Add an approved MCP endpoint to get started.':'An integration administrator can register an MCP server.'}</p></section>:<div className="mcp-layout">
    <nav className="mcp-list panel" aria-label="Registered MCP servers">{list.data.items.map(item=><button key={item.id} type="button" aria-pressed={selectedId===item.id} disabled={!!running} onClick={()=>{setSelected(item.id);setToolId('');setResult(undefined);setError('');}}><strong>{item.display_name}</strong><span>{item.state} · {item.health}</span><small>{item.endpoint}</small></button>)}</nav>
    <div className="mcp-detail">{detail.isPending?<p role="status">Loading server…</p>:detail.error?<p role="alert">{detail.error.message}</p>:integration&&<>
     <section className="panel mcp-panel"><h2>{integration.display_name}</h2><p className="mcp-state" aria-live="polite">{integration.state} · {integration.health} · Revision {integration.configuration_version}</p><p className="mcp-endpoint">{integration.endpoint}</p><p className="muted">Streamable HTTP · Policy {integration.protocolPolicy} · {tools.filter(t=>t.present).length} discovered · {tools.filter(t=>t.present&&t.approvals.some(a=>a.status==='APPROVED')).length} approved</p><p className="muted">Last discovery: {integration.last_discovered_at??'Not yet'} · Last connection: {integration.last_connected_at??'Not yet'}</p>
      <div className="mcp-actions"><button className="button secondary" onClick={()=>void refresh()} disabled={!!busy}>Refresh status</button>{canManage&&<><button className="button primary" disabled={!!busy||integration.state==='DISABLED'} onClick={()=>void action('Testing connection and discovering tools','/'+integration.id+'/validate')}>Test connection & discover</button><button className="button secondary" disabled={!!busy||integration.state!=='DRAFT'} onClick={()=>void action('Activating','/'+integration.id+'/activate')}>Activate integration</button><button className="button secondary" disabled={!!busy||integration.state==='DISABLED'} onClick={()=>void action('Disabling','/'+integration.id+'/disable')}>Disable integration</button><RegistrationDialog initial={integration} save={async input=>{await request(base+'/'+integration.id,jsonOptions('PUT',input));await refresh();}}/><Dialog.Root><Dialog.Trigger asChild><button className="button secondary" disabled={!!busy}>Remove integration</button></Dialog.Trigger><Dialog.Portal><Dialog.Overlay className="dialog-overlay"/><Dialog.Content className="dialog"><Dialog.Title>Remove this integration?</Dialog.Title><Dialog.Description>New calls will be blocked. Revision and audit history remain available to operators.</Dialog.Description><Dialog.Close asChild><button className="button secondary">Keep integration</button></Dialog.Close><Dialog.Close asChild><button className="button primary" onClick={()=>void action('Removing','/'+integration.id,'DELETE')}>Confirm removal</button></Dialog.Close></Dialog.Content></Dialog.Portal></Dialog.Root></>}</div>
     </section>
     <section className="panel mcp-panel"><h2>Tool explorer</h2>{!tools.length&&<p>Test the connection to discover its actual tools.</p>}{tools.map(cap=><article className="mcp-tool" key={cap.id} aria-label={cap.tool.name}><h3>{cap.tool.name}</h3><p>{cap.tool.description}</p><p className="muted">{cap.protocol} · {String(cap.server.name)} · {cap.present?'Current schema':'Previous schema — stale'}</p><p className="muted">Alias: {cap.alias}</p><p className="mcp-fingerprint">Fingerprint: {cap.tool.fingerprint}</p>{!cap.tool.supported&&<p className="form-error">Unsupported: {cap.tool.unsupportedReason}</p>}
      <details><summary>Input schema and required parameters</summary><pre>{formatted(cap.tool.inputSchema)}</pre></details><details><summary>Output schema and metadata</summary><pre>{formatted({outputSchema:cap.tool.outputSchema??'Not advertised',annotations:cap.tool.annotations,metadata:cap.tool.metadata})}</pre></details>
      <p>{['INTEGRATION_ADMIN','USER'].map(role=><span key={role} className="pill muted-pill">{role}: {approved(cap,role)?'APPROVED':'Not approved'}</span>)}</p>
      {canManage&&cap.present&&<div className="mcp-actions">{['INTEGRATION_ADMIN','USER'].map(role=><button className="button secondary" key={role} disabled={!!busy||!cap.tool.supported||integration.state==='DISABLED'} onClick={()=>void action(approved(cap,role)?'Revoking approval':'Approving capability','/'+integration.id+'/capabilities/'+cap.id+'/approval','POST',{role,approved:!approved(cap,role)})}>{approved(cap,role)?'Revoke':'Approve'} {role==='USER'?'users':'admin'}</button>)}</div>}
      {!!cap.approvals.length&&<details><summary>Approval history</summary><pre>{formatted(cap.approvals)}</pre></details>}
     </article>)}</section>
     <section className="panel mcp-panel"><h2>Manual invocation</h2><p className="muted">Run one approved tool with JSON arguments. The server validates them again.</p>{!usable.length?<p>No tools are approved for your roles.</p>:<form className="mcp-form" aria-label="Invoke approved tool" onSubmit={e=>void invoke(e)}><label>Approved tool<select value={chosen?.id??''} onChange={e=>{setToolId(e.target.value);setResult(undefined);}} disabled={!!running}>{usable.map(t=><option key={t.id} value={t.id}>{t.tool.name}</option>)}</select></label><label>JSON arguments<textarea rows={6} value={argumentsText} onChange={e=>setArgumentsText(e.target.value)} maxLength={16384} spellCheck={false} disabled={!!running}/></label><button className="button primary" disabled={!!running||integration.state!=='ACTIVE'} type="submit">{running?'Invoking…':'Invoke tool'}</button></form>}
      {running&&<div role="status">Running invocation {running}<button className="button secondary" onClick={()=>void action('Cancelling','/'+integration.id+'/invocations/'+running+'/cancel')}>Cancel invocation</button></div>}
      {result!==undefined&&<div role="status"><h3>Invocation result</h3><p>Untrusted source data · {integration.display_name} · {chosen?.tool.name}</p><pre aria-label="Invocation result JSON">{formatted(result)}</pre></div>}
     </section>
     <section className="panel mcp-panel"><h2>Execution history</h2><p className="muted">Recent connection and invocation metadata. Arguments and result bodies are not stored.</p>{!integration.events?.length?<p>No events yet.</p>:<div className="mcp-events">{integration.events.map(event=><div className="mcp-event" key={event.id}><strong>{event.kind} · {event.status}</strong><span>{event.protocol??'—'} · Revision {event.version} · {event.duration_ms??0} ms · {event.authorization_outcome}</span><small>Correlation: {event.id} · Actor: {event.actor}</small>{event.error_code&&<span>{event.error_code}</span>}</div>)}</div>}</section>
    </>}</div>
   </div>}
  </>}
 </>;
}
