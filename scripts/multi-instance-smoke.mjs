// nginx 뒤 앱 컨테이너 2개가 세션과 실시간 편집을 Redis로 공유하는지 확인한다.
// 실행: docker compose up -d --build 후  node scripts/multi-instance-smoke.mjs http://localhost:8080
// 테스트 계정과 집을 하나 만든다. 운영 중인 서버에서는 실행하지 않는다.
// Node 22 이상(내장 fetch·WebSocket) 필요.
const base = (process.argv[2] || 'http://localhost:8080').replace(/\/$/, '');
let cookie = '';

function fail(message) {
  console.error('FAIL: ' + message);
  if (process.env.GITHUB_ACTIONS) console.log('::error title=Multi-instance smoke::' + message);
  process.exit(1);
}
process.on('unhandledRejection', e => fail(String(e?.message || e)));
process.on('uncaughtException', e => fail(String(e?.message || e)));
function check(ok, message) { if (!ok) fail(message); console.log('ok - ' + message); }

async function http(path, { method = 'GET', body, headers = {} } = {}) {
  const h = { ...headers };
  if (method !== 'GET') {
    // 토큰을 받으면서 세션 쿠키가 새로 생길 수 있으므로 쿠키는 그 뒤에 붙인다.
    const csrf = await (await http('/api/csrf')).json();
    h[csrf.headerName] = csrf.token;
  }
  if (cookie) h.cookie = cookie;
  if (body !== undefined) h['content-type'] = 'application/json';
  const res = await fetch(base + path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
  for (const c of res.headers.getSetCookie()) {
    const pair = c.split(';')[0];
    if (pair.startsWith('SESSION=')) cookie = pair;
  }
  return res;
}
async function json(path, options) {
  const res = await http(path, options);
  if (!res.ok) fail(`${options?.method || 'GET'} ${path} -> ${res.status}`);
  return res.json();
}

function connect(houseId) {
  return new Promise(async (resolve, reject) => {
    const { ticket } = await json('/api/live/ticket?houseId=' + houseId, { method: 'POST' });
    const ws = new WebSocket(base.replace(/^http/, 'ws') + '/ws/edit?ticket=' + encodeURIComponent(ticket),
      { headers: { cookie, origin: base } });
    const client = { ws, messages: [], waiters: [], closed: false };
    client.waitFor = (type, test = () => true, ms = 5000) => new Promise((ok, no) => {
      const found = client.messages.find(m => m.type === type && test(m));
      if (found) return ok(found);
      const timer = setTimeout(() => no(Error(`no ${type} message within ${ms}ms`)), ms);
      client.waiters.push(m => { if (m.type === type && test(m)) { clearTimeout(timer); ok(m); return true; } return false; });
    });
    client.send = m => ws.send(JSON.stringify(m));
    ws.onmessage = e => {
      const m = JSON.parse(e.data); client.messages.push(m);
      client.waiters = client.waiters.filter(w => !w(m));
      if (m.type === 'hello') { client.id = m.clientId; client.server = m.server; resolve(client); }
    };
    ws.onclose = () => { client.closed = true; };
    ws.onerror = () => reject(Error('websocket error'));
    setTimeout(() => reject(Error('no hello')), 5000);
  });
}

const name = 'm' + Math.random().toString(36).slice(2, 12);
await json('/api/auth/register', { method: 'POST', body: { loginId: name, email: name + '@example.com', password: 'MultiSmoke123!' } });
await json('/api/houses', { method: 'POST' });
const houseId = (await json('/api/me')).user.houseId;

// 1. 같은 로그인으로 여러 번 요청하면 nginx가 앱 2개에 나눠 보내고, 어느 쪽이든 로그인 상태가 유지된다.
const upstreams = new Set();
for (let i = 0; i < 8; i++) {
  const res = await http('/api/me');
  const me = await res.json();
  if (!me.authenticated) fail('session lost on ' + res.headers.get('x-upstream'));
  upstreams.add(res.headers.get('x-upstream'));
}
check(upstreams.size >= 2, `session is shared across app containers (${[...upstreams].join(', ')})`);

let house = await json('/api/houses/' + houseId);
house = await json(`/api/houses/${houseId}/furniture`, { method: 'POST', body: { typeId: 'chair', x: 2, y: 2, rotation: false, revision: house.revision } });
const chair = house.furniture.at(-1).id;

// 2. 실시간 연결 두 개가 서로 다른 앱 컨테이너에 붙을 때까지 다시 연결한다.
const one = await connect(houseId);
let two;
for (let i = 0; i < 10 && !two; i++) {
  const c = await connect(houseId);
  if (c.server !== one.server) two = c; else c.ws.close();
}
if (!two) fail('could not reach a second app container over WebSocket');
check(true, `two live connections on different servers (${one.server}, ${two.server})`);
await two.waitFor('presence', m => m.people.some(p => p.clientId === one.id) && m.people.some(p => p.clientId === two.id));
check(true, 'presence lists people connected to both servers');

// 3. 잠금은 한 명만, 미리보기는 상대 서버까지 전달된다.
one.send({ type: 'lock', id: chair, requestId: 'a' });
await one.waitFor('locked', m => m.requestId === 'a');
two.send({ type: 'lock', id: chair, requestId: 'b' });
await two.waitFor('rejected', m => m.requestId === 'b');
check(true, 'the same furniture can be locked by only one person across servers');
one.send({ type: 'preview', id: chair, x: 5, y: 5, rotation: false });
await two.waitFor('preview', m => m.clientId === one.id && m.item.x === 5);
check(true, 'drag preview reaches the other server');

// 4. 저장은 잠금을 가진 쪽만 되고, 저장 결과는 양쪽 모두 받는다.
const put = (client, x) => http(`/api/houses/${houseId}/furniture/${chair}`, {
  method: 'PUT', headers: { 'X-Live-Client': client.id }, body: { typeId: 'chair', x, y: 5, rotation: false, revision: 0 } });
check((await put(two, 6)).status === 409, 'save without the lock is rejected');
check((await put(one, 5)).ok, 'save with the lock succeeds');
await one.waitFor('snapshot', m => m.house.furniture.some(f => f.id === chair && f.x === 5));
await two.waitFor('snapshot', m => m.house.furniture.some(f => f.id === chair && f.x === 5));
check(true, 'saved layout is broadcast to both servers');

// 5. 로그아웃하면 두 서버의 연결이 모두 끊긴다(공유 세션 삭제).
await http('/api/auth/logout', { method: 'POST' });
for (let i = 0; i < 50 && !(one.closed && two.closed); i++) await new Promise(r => setTimeout(r, 100));
check(one.closed && two.closed, 'logout closes live connections on both servers');
console.log('multi-instance smoke passed');
process.exit(0);
