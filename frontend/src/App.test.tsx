import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import { App, Login, ErrorPanel, Loading } from './App';
function wrap(element: React.ReactNode, route = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[route]}>{element}</MemoryRouter></QueryClientProvider>);
}
const workspace = { id: 'org-1', name: 'Test workspace', roles: ['INTEGRATION_ADMIN'], createdAt: '2026-10-30T00:00:00Z' };
function mockSession(database = 'UP') {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async input => {
    const url = String(input);
    if (url === '/api/session') return Response.json({ id: 'u-1', username: 'fixture', displayName: 'Fixture', platformAdministrator: true, workspaces: [workspace] });
    if (url === '/api/system/status') return Response.json({ application: 'UP', database, status: database === 'UP' ? 'UP' : 'DEGRADED', uptimeSeconds: 120 });
    if (url.endsWith('/overview')) return Response.json({ workspace, registeredIntegrations: 0, aiAvailable: false, integrationManagementAvailable: false });
    if (url.endsWith('/integrations')) return Response.json({ items: [], available: false });
    throw new Error('Unexpected test URL');
  });
}
describe('sign in', () => {
  it('validates empty fields without sending credentials', async () => {
    const fetcher = vi.spyOn(globalThis, 'fetch');
    wrap(<Login/>); fireEvent.submit(screen.getByRole('form', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter your username and password');
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('displays a safe authentication error', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(Response.json({ token: 'fixture', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(Response.json({ message: 'The username or password is incorrect.' }, { status: 401 }));
    wrap(<Login/>);
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'fixture' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'incorrect' } });
    fireEvent.submit(screen.getByRole('form', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('username or password is incorrect');
    expect(screen.getByRole('button', { name: /Sign in to workspace/ })).toBeEnabled();
  });
  it('shows login for an expired session', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({ message: 'Sign in to continue.' }, { status: 401 }));
    wrap(<App/>); expect(await screen.findByLabelText('Username')).toBeVisible();
  });
});
describe('workspace states', () => {
  it('announces loading', () => { render(<Loading/>); expect(screen.getByRole('status')).toHaveTextContent('Loading your workspace'); });
  it('offers a real retry action on error', () => { const retry = vi.fn(); render(<ErrorPanel message="Unavailable" retry={retry}/>); fireEvent.click(screen.getByRole('button', { name: 'Try again' })); expect(retry).toHaveBeenCalledOnce(); });
  it('shows an honest empty integrations page', async () => {
    mockSession(); wrap(<App/>, '/integrations');
    expect(await screen.findByRole('heading', { name: 'No integrations yet' })).toBeVisible();
    expect(screen.getByText(/Connection registration is not available/)).toBeVisible();
  });
  it('does not expose a fabricated chat form', async () => {
    mockSession(); wrap(<App/>, '/workspace');
    expect(await screen.findByRole('heading', { name: 'Good answers start with trusted sources.' })).toBeVisible();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });
  it('reports database failure while the API is available', async () => {
    mockSession('DOWN'); wrap(<App/>, '/status');
    expect(await screen.findByText('Database unavailable')).toBeVisible();
    expect(screen.getByText('Available')).toBeVisible();
  });
  it('shows overview from actual response and opens accessible roadmap dialog', async () => {
    mockSession(); wrap(<App/>);
    expect(await screen.findByRole('heading', { name: 'A clearer view starts here.' })).toBeVisible();
    const trigger = screen.getByRole('button', { name: /Explore what/ });
    fireEvent.click(trigger);
    expect(await screen.findByRole('dialog')).toHaveAccessibleName('A connected workspace, step by step');
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
  it('displays connection errors instead of invented workspace values', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'));
    wrap(<App/>); expect(await screen.findByRole('alert')).toHaveTextContent('service could not be reached');
    expect(screen.queryByText('Registered integrations')).not.toBeInTheDocument();
  });
});
