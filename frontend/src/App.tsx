import { useEffect, useState, type FormEvent } from 'react';
import { NavLink, Route, Routes, useLocation } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as Dialog from '@radix-ui/react-dialog';
import { ApiError, request, type Session, type Status, type Overview, type Workspace } from './api';
import { Brand, Icon } from './icons';

export function Loading({ label = 'Loading your workspace…' }: { label?: string }) {
  return <div className="loading" role="status"><span className="spinner"/>{label}</div>;
}
export function ErrorPanel({ message, retry }: { message: string; retry: () => void }) {
  return <div className="error-panel" role="alert"><strong>Something needs attention</strong><p>{message}</p><button className="button secondary" onClick={retry}>Try again</button></div>;
}
export function Login() {
  const client = useQueryClient();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('');
    if (!username.trim() || !password) { setError('Enter your username and password.'); return; }
    setPending(true);
    try {
      await request<void>('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: username.trim(), password }) });
      setPassword('');
      await client.invalidateQueries({ queryKey: ['session'] });
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to sign in.'); }
    finally { setPending(false); }
  }
  return <div className="login-page">
    <aside className="login-story"><Brand/><div className="story-copy"><span className="eyebrow">CONNECTED SYSTEMS. CLEARER ANSWERS.</span><h1>A common language<br/>for every system.</h1><p>Your people, data, and tools.<br/>One trusted workspace to bring them together.</p><div className="network-art" aria-hidden="true"><span className="network-node n1"><Icon name="database" size={27}/></span><span className="network-node center-node"><Icon name="connections" size={40}/></span><span className="network-node n2"><Icon name="shield" size={27}/></span><span className="network-node n3"><Icon name="spark" size={27}/></span></div></div><small>Interoperability, thoughtfully built.</small></aside>
    <main className="login-form-area"><div className="login-card"><span className="tag">YOUR ECOSYNC WORKSPACE</span><h2>Welcome back.</h2><p className="muted">Sign in to your organization’s workspace.</p>
      <form onSubmit={submit} noValidate aria-label="Sign in">
        <label htmlFor="username">Username</label><input id="username" autoComplete="username" maxLength={64} value={username} onChange={e => setUsername(e.target.value)} aria-invalid={!!error} autoFocus/>
        <label htmlFor="password">Password</label><input id="password" type="password" autoComplete="current-password" maxLength={128} value={password} onChange={e => setPassword(e.target.value)} aria-invalid={!!error}/>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button primary full" disabled={pending} type="submit">{pending ? 'Signing in…' : 'Sign in to workspace'}<Icon name="arrow" size={18}/></button>
      </form><div className="login-note"><Icon name="shield" size={18}/><span>Your session stays protected on this device.</span></div><p className="help-note">Need access? Contact your workspace administrator.</p>
    </div><small className="login-footer">Ecosync AI · A foundation for connected work</small></main>
  </div>;
}
export function App() {
  const session = useQuery({ queryKey: ['session'], queryFn: () => request<Session>('/api/session') });
  if (session.isPending) return <Loading/>;
  if (session.error) return session.error instanceof ApiError && session.error.status === 401
    ? <Login/> : <main className="standalone"><Brand/><ErrorPanel message={session.error.message} retry={() => void session.refetch()}/></main>;
  return <WorkspaceApp session={session.data}/>;
}
function NextSteps() {
  return <Dialog.Root><Dialog.Trigger asChild><button className="button secondary">Explore what’s next<Icon name="arrow" size={16}/></button></Dialog.Trigger>
    <Dialog.Portal><Dialog.Overlay className="dialog-overlay"/><Dialog.Content className="dialog"><Dialog.Title>A connected workspace, step by step</Dialog.Title><Dialog.Description>Connection registration and AI answers are not available in this release.</Dialog.Description>
      <ol className="next-list"><li>Register an approved MCP server, database, or supported API.</li><li>Review the data and tools your team can access.</li><li>Ask questions with clear, traceable source evidence.</li></ol>
      <p className="muted">For now, you can manage your session and check workspace availability.</p><Dialog.Close asChild><button className="icon-button dialog-close" aria-label="Close"><Icon name="close"/></button></Dialog.Close></Dialog.Content></Dialog.Portal>
  </Dialog.Root>;
}
function WorkspaceApp({ session }: { session: Session }) {
  const client = useQueryClient();
  const location = useLocation();
  const [activeId, setActiveId] = useState(session.workspaces[0]?.id ?? '');
  const [mobileOpen, setMobileOpen] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  useEffect(() => {
    if (!mobileOpen) return;
    document.querySelector<HTMLAnchorElement>('#workspace-navigation nav a')?.focus();
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setMobileOpen(false); document.getElementById('mobile-navigation-toggle')?.focus(); }
    };
    document.addEventListener('keydown', close);
    return () => document.removeEventListener('keydown', close);
  }, [mobileOpen]);
  const workspace = session.workspaces.find(w => w.id === activeId);
  const status = useQuery({ queryKey: ['status'], queryFn: () => request<Status>('/api/system/status'), refetchInterval: 30_000 });
  const titles: Record<string, string> = { '/': 'Overview', '/integrations': 'Integrations', '/workspace': 'AI workspace', '/status': 'System status' };
  async function logout() {
    try { await request('/api/auth/logout', { method: 'POST' }); client.clear(); window.location.assign('/'); }
    catch (error) { setLogoutError(error instanceof Error ? error.message : 'Unable to sign out.'); }
  }
  return <div className="app-shell"><a className="skip-link" href="#main-content">Skip to content</a>
    {mobileOpen && <button className="mobile-scrim" aria-label="Close navigation" onClick={() => { setMobileOpen(false); document.getElementById('main-content')?.focus(); }}/>}
    <aside id="workspace-navigation" className={'sidebar ' + (mobileOpen ? 'mobile-open' : '')}><Brand/><div className="sidebar-workspace"><span className="workspace-avatar">{workspace?.name.slice(0,1) ?? 'W'}</span><div><small>WORKSPACE</small><select aria-label="Workspace" value={activeId} onChange={e => setActiveId(e.target.value)}>{session.workspaces.map(w => <option key={w.id} value={w.id}>{w.name}</option>)}</select></div></div>
      <span className="nav-label">WORKSPACE</span><nav aria-label="Main navigation">
        {([['/', 'grid', 'Overview'], ['/integrations', 'connections', 'Integrations'], ['/workspace', 'spark', 'AI workspace'], ['/status', 'pulse', 'System status']] as const).map(([to,icon,label]) =>
          <NavLink key={to} to={to} end={to === '/'} onClick={() => { setMobileOpen(false); document.getElementById('main-content')?.focus(); }}><Icon name={icon}/><span>{label}</span>{to === '/workspace' && <span className="nav-soon">SOON</span>}</NavLink>)}
      </nav><div className="sidebar-bottom"><div className="trust-note"><Icon name="shield"/><div><strong>Built on trust</strong><p>Controlled access.<br/>Traceable information.</p></div></div><div className="sidebar-version"><span className="tiny-dot"/>Ecosync AI<span>v0.1</span></div></div>
    </aside>
    <div className="main-shell"><header className="topbar"><div className="breadcrumb"><button className="icon-button mobile-menu" id="mobile-navigation-toggle" aria-label="Open navigation" aria-controls="workspace-navigation" aria-expanded={mobileOpen} onClick={() => setMobileOpen(true)}><Icon name="menu"/></button><span>Workspace</span><span className="breadcrumb-divider">/</span><strong>{titles[location.pathname] ?? 'Page'}</strong></div>
      <div className="topbar-right"><span className={'availability ' + (status.data?.status === 'UP' ? 'ok' : 'neutral')}><span className="tiny-dot"/>{status.data?.status === 'UP' ? 'System available' : 'Check system status'}</span><span className="topbar-separator"/><span className="user-avatar" title={session.displayName}>{session.displayName.slice(0,1)}</span><button className="icon-button" aria-label="Sign out" onClick={() => void logout()}><Icon name="logout" size={18}/></button></div>
    </header><main id="main-content" tabIndex={-1} className="main-content">{logoutError && <p role="alert" className="form-error">{logoutError}</p>}
      {!workspace ? <ErrorPanel message="You have no workspace access. Contact your administrator." retry={() => void client.invalidateQueries({ queryKey: ['session'] })}/> :
      <Routes><Route path="/" element={<Dashboard workspace={workspace} session={session} status={status.data}/>}/><Route path="/integrations" element={<Integrations workspace={workspace}/>}/><Route path="/workspace" element={<AIUnavailable/>}/><Route path="/status" element={<SystemStatus/>}/><Route path="*" element={<section className="empty-card"><h1>Page not found</h1><NavLink to="/">Return to overview</NavLink></section>}/></Routes>}
    </main><footer className="page-footer"><span>Thoughtfully connected. Secure by design.</span><span>Ecosync AI · Workspace foundation</span></footer></div>
  </div>;
}
function PageHeading({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) {
  return <div className="page-heading"><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{description}</p></div>;
}
function Dashboard({ workspace, session, status }: { workspace: Workspace; session: Session; status?: Status }) {
  const overview = useQuery({ queryKey: ['overview', workspace.id], queryFn: () => request<Overview>('/api/organizations/' + workspace.id + '/overview') });
  if (overview.isPending) return <Loading label="Loading workspace overview…"/>;
  if (overview.error) return <ErrorPanel message={overview.error.message} retry={() => void overview.refetch()}/>;
  return <><div className="heading-row"><PageHeading eyebrow="YOUR WORKSPACE, AT A GLANCE" title="A clearer view starts here." description="A secure foundation for your systems, your data, and your team."/><span className="workspace-badge"><Icon name="shield" size={16}/>Workspace established</span></div>
    <div className="stat-grid"><div className="stat-card"><div className="stat-top"><span>Registered integrations</span><Icon name="connections"/></div><strong className="stat-value">{overview.data.registeredIntegrations}<span>connections</span></strong><p>No sources registered yet</p></div>
      <div className="stat-card"><div className="stat-top"><span>Metadata database</span><Icon name="database"/></div><strong className="stat-word">{status ? status.database === 'UP' ? 'Connected' : 'Unavailable' : 'Checking…'}</strong><p><span className={'tiny-dot ' + (status?.database === 'UP' ? 'green' : 'gray')}/>Live connection status</p></div>
      <div className="stat-card"><div className="stat-top"><span>Your access</span><Icon name="shield"/></div><strong className="stat-word">{session.platformAdministrator ? 'Administrator' : workspace.roles.includes('AUDITOR') ? 'Auditor' : 'Member'}</strong><p>Scoped to your workspace</p></div></div>
    <section className="welcome-panel"><div><span className="tag">START WITH A STRONG FOUNDATION</span><h2>Bring your systems<br/>into the same conversation.</h2><p>Your workspace is ready. Connection registration and AI-assisted answers will arrive in future releases.</p><NextSteps/></div><div className="connection-illustration" aria-hidden="true"><span className="orbit"/><span className="orbit inner"/><span className="hub"><Icon name="connections" size={34}/></span><span className="satellite one"><Icon name="database" size={22}/></span><span className="satellite two"><Icon name="shield" size={22}/></span><span className="satellite three"><Icon name="spark" size={22}/></span></div></section>
    <div className="lower-grid"><section className="panel"><div className="panel-heading"><h2>Workspace readiness</h2><span className="subtle">A thoughtful start</span></div>
      <div className="readiness-row"><span className="step-icon complete"><Icon name="check" size={17}/></span><div><strong>Workspace established</strong><p>{workspace.name}</p></div><span className="pill success">Ready</span></div>
      <div className="readiness-row"><span className="step-icon"><Icon name="connections" size={17}/></span><div><strong>Connect your first source</strong><p>Review and approve every connection.</p></div><span className="pill muted-pill">Upcoming</span></div>
      <div className="readiness-row"><span className="step-icon"><Icon name="spark" size={17}/></span><div><strong>Ask with context</strong><p>Answers grounded in authorized data.</p></div><span className="pill muted-pill">Upcoming</span></div>
    </section><section className="panel details-panel"><div className="panel-heading"><h2>Workspace details</h2><Icon name="shield" size={18}/></div><dl><dt>Organization</dt><dd>{workspace.name}</dd><dt>Created</dt><dd>{new Date(workspace.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}</dd><dt>Signed in as</dt><dd>{session.username}</dd><dt>AI workspace</dt><dd><span className="pill muted-pill">Not enabled</span></dd></dl><NavLink className="text-link" to="/status">View system status<Icon name="arrow" size={16}/></NavLink></section></div>
  </>;
}
function Integrations({ workspace }: { workspace: Workspace }) {
  const query = useQuery({ queryKey: ['integrations', workspace.id], queryFn: () => request<{ items: unknown[]; available: boolean }>('/api/organizations/' + workspace.id + '/integrations') });
  return <><PageHeading eyebrow="YOUR CONNECTED ECOSYSTEM" title="Integrations" description="A place for the systems your team relies on."/>
    {query.isPending ? <Loading label="Loading integrations…"/> : query.error ? <ErrorPanel message={query.error.message} retry={() => void query.refetch()}/> :
    <section className="empty-card"><span className="empty-icon"><Icon name="connections" size={32}/></span><span className="tag">A CLEAN START</span><h2>No integrations yet</h2><p>Connection registration is not available in this release.<br/>When it arrives, you’ll review each source and its permitted tools here.</p><NextSteps/><div className="source-tags"><span>MCP servers</span><span>Databases</span><span>Supported APIs</span></div></section>}</>;
}
function AIUnavailable() {
  return <><PageHeading eyebrow="ANSWERS WITH CONTEXT" title="AI workspace" description="A future home for questions that connect your systems."/><section className="empty-card"><span className="empty-icon"><Icon name="spark" size={32}/></span><span className="pill muted-pill">Not enabled</span><h2>Good answers start with trusted sources.</h2><p>AI conversations are not available in this release.<br/>Future answers will use approved tools and show where the information came from.</p><NavLink className="button secondary" to="/integrations">View integrations<Icon name="arrow" size={16}/></NavLink></section></>;
}
function SystemStatus() {
  const query = useQuery({ queryKey: ['status'], queryFn: () => request<Status>('/api/system/status'), refetchInterval: 30_000 });
  return <><div className="heading-row"><PageHeading eyebrow="LIVE WORKSPACE AVAILABILITY" title="System status" description="Current availability, without exposing sensitive configuration."/><button className="button secondary" onClick={() => void query.refetch()} disabled={query.isFetching}>Refresh status</button></div>
    {query.isPending ? <Loading label="Checking availability…"/> : query.error ? <ErrorPanel message={query.error.message} retry={() => void query.refetch()}/> :
    <><div className={'status-banner ' + (query.data.status === 'UP' ? '' : 'degraded')}><Icon name="pulse" size={26}/><div><strong>{query.data.status === 'UP' ? 'Core services are available' : 'Database unavailable'}</strong><p>{query.data.status === 'UP' ? 'Your workspace can reach its metadata database.' : 'The application is running, but database-dependent features may be unavailable.'}</p></div></div>
      <section className="panel service-table"><div className="panel-heading"><h2>Service availability</h2><span className="subtle">Checked {new Date(query.dataUpdatedAt).toLocaleTimeString()}</span></div>{[['Platform API', query.data.application === 'UP' ? 'Available' : 'Unavailable'], ['Metadata database', query.data.database === 'UP' ? 'Connected' : 'Unavailable'], ['AI workspace', 'Not enabled'], ['Connection registration', 'Not available in this release']].map(([label,value]) => <div className="service-row" key={label}><span>{label}</span><strong>{value}</strong></div>)}<div className="service-row"><span>Application uptime</span><strong>{Math.floor(query.data.uptimeSeconds / 60)} min</strong></div></section></>}
  </>;
}
