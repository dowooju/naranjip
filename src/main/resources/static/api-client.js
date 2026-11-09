// Fetch a fresh CSRF token before each write: login/logout rotate the session.
export async function apiFetch(url, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const response = await fetch('/api/csrf', {cache:'no-store'});
    if (!response.ok) throw new Error('서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.');
    const csrf = await response.json();
    const headers = new Headers(options.headers);
    headers.set(csrf.headerName, csrf.token);
    options = {...options, headers};
  }
  return fetch(url, options);
}
