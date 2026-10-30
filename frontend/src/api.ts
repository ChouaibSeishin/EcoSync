export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.method && options.method !== 'GET') {
    const csrf = await fetch('/api/csrf', { credentials: 'same-origin', cache: 'no-store' });
    if (!csrf.ok) throw new ApiError(csrf.status, 'Unable to verify this session. Refresh and try again.');
    const token: { token: string; headerName: string } = await csrf.json();
    headers.set(token.headerName, token.token);
  }
  let response: Response;
  try { response = await fetch(path, { ...options, headers, credentials: 'same-origin', cache: 'no-store' }); }
  catch { throw new ApiError(0, 'The service could not be reached. Check your connection and retry.'); }
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new ApiError(response.status, body.message ?? 'The request could not be completed.');
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export type Workspace = { id: string; name: string; roles: string[]; createdAt: string };
export type Session = { id: string; username: string; displayName: string; platformAdministrator: boolean; workspaces: Workspace[] };
export type Status = { application: string; database: string; status: string; uptimeSeconds: number; aiAvailable: boolean; integrationManagementAvailable: boolean };
export type Overview = { workspace: Workspace; registeredIntegrations: number; aiAvailable: boolean; integrationManagementAvailable: boolean };
