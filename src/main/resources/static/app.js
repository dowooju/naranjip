import { roomSvg } from './room.js';

const app = document.querySelector('#app');
let me = { authenticated: false, demoMode: false };
let tab = 'general';
let zoom = 1;
let renderVersion = 0;
let toastTimer;
const escape = value => String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));

async function api(path, body) {
  const response = await fetch(`/api${path}`, body === undefined ? {} : {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
  });
  if (!response.ok) {
    const error = new Error(response.status === 503 ? '개발용 로그인이 꺼져 있어요. DEMO_AUTH_ENABLED 설정을 확인해 주세요.' : response.status === 401 ? '다시 로그인해 주세요.' : '요청을 완료하지 못했어요. 잠시 후 다시 시도해 주세요.');
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}

function toast(message) {
  const el = document.querySelector('#toast');
  el.textContent = message; el.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { el.hidden = true; }, 3500);
}

function ranking() {
  return `<aside class="panel ranking-panel"><div class="panel-eyebrow"><span class="tiny-star">✧</span> 이웃의 공간</div>
    <div class="tabs" role="tablist" aria-label="집 순위"><button role="tab" data-tab="general" aria-selected="${tab === 'general'}">명예의 전당</button><button role="tab" data-tab="theme" aria-selected="${tab === 'theme'}">이번 주 콘셉트</button></div>
    <div class="ranking-empty"><span class="outline-medal">${tab === 'general' ? '♧' : '✧'}</span><h3>${tab === 'general' ? '멋진 집을 기다리고 있어요' : '다음 이야기를 준비 중이에요'}</h3><p>${tab === 'general' ? '서로의 취향이 담긴 집들을<br>곧 여기에서 만나보세요.' : '같은 주제, 서로 다른 취향.<br>새로운 콘셉트로 곧 만나요.'}</p><span class="soon-label">준비 중</span></div>
    <div class="aside-note"><span>♡</span><p>작은 공간에도<br>우리다운 이야기는 가득하니까.</p></div></aside>`;
}

function authPanel(register = false) {
  if (me.authenticated) return `<section class="panel account-panel"><span class="avatar">${escape(me.user.displayName.slice(0,1))}</span><p class="eyebrow">WELCOME HOME</p><h2>${escape(me.user.displayName)}님,<br>집에 잘 오셨어요.</h2><p class="muted">아직 비어 있어도 괜찮아요.<br>우리의 시작이 담긴 공간이니까.</p><a class="button primary full" href="#house">우리집 가기 <span>↗</span></a><div class="session-note">이 브라우저에서 잠시 머무는 체험용 집이에요.</div></section>`;
  return `<section class="panel account-panel"><span class="small-house">⌂</span><p class="eyebrow">${register ? 'A NEW BEGINNING' : 'MAKE YOURSELF AT HOME'}</p><h2>${register ? '우리의 첫 페이지를<br>열어볼까요?' : '우리의 공간에<br>어서 오세요.'}</h2><p class="muted">${register ? '나란집에서 함께할 이름을 알려주세요.' : '나란히 앉아, 같은 집을 꿈꾸는 곳.'}</p>
    <form id="auth-form" data-mode="${register ? 'register' : 'login'}">
      ${register ? '<label for="email">이메일 <span class="optional">선택</span></label><input id="email" name="email" type="text" inputmode="email" placeholder="hello@naranjip.com" autocomplete="email" maxlength="120">' : ''}
      <label for="login-id">아이디</label><input id="login-id" name="loginId" placeholder="어떤 이름이든 괜찮아요" autocomplete="username" required maxlength="40">
      <label for="password">비밀번호</label><input id="password" name="password" type="password" placeholder="아무 값이나 입력해 주세요" autocomplete="${register ? 'new-password' : 'current-password'}">
      <p id="form-error" class="form-error" role="alert"></p><button class="button primary full" type="submit">${register ? '가입하고 시작하기' : '로그인'} <span>→</span></button>
    </form><p class="auth-switch">${register ? '이미 함께하고 있나요? <a href="#home">로그인</a>' : '나란집이 처음인가요? <a href="#signup">회원가입</a>'}</p>
    <div class="demo-note"><span class="status-dot"></span><span>개발용 체험 · 아무 정보로 시작할 수 있어요.<br>이메일과 비밀번호는 전송·저장하지 않아요.</span></div></section>`;
}

function home(register) {
  return `<section class="intro"><div><p class="eyebrow">A LITTLE SPACE, A LOT OF US</p><h1>나란히, <span>조금씩.</span></h1><p>함께 채워갈 우리의 공간, 나란집</p></div><span class="intro-note">서로의 하루가 머무는 곳 <span>⌁</span></span></section>
    <div class="home-grid">${ranking()}<section class="panel welcome-panel"><div class="room-heading"><span class="pill"><span class="status-dot"></span> 우리의 첫 번째 집</span><span class="room-number">ROOM 001</span></div><div class="home-room">${roomSvg()}</div><div class="welcome-caption"><p class="eyebrow">EVERY STORY STARTS SOMEWHERE</p><h2>아직은 빈 집,<br>곧 우리의 집.</h2><p>좋아하는 것들로 채워갈 내일을 상상해 보세요.</p></div><span class="corner-spark">✧</span></section>${authPanel(register)}</div>`;
}

function houseScreen(house, editing) {
  return `<section class="house-top"><div><a class="back-link" href="#home">← 마을로 돌아가기</a><h1>${editing ? '우리집 수정하기' : '우리집에 오신 걸 환영해요.'}</h1><p>${escape(house.ownerName)}님의 작은 공간 <span class="dot-divider">·</span> ${house.width} × ${house.height} 빈 집</p></div>${editing ? '<a class="button primary" href="#house">꾸미기 완료 ✓</a>' : '<a class="button primary" href="#edit">수정하기 ↗</a>'}</section>
    <div class="house-layout"><section class="panel house-stage"><div class="room-heading"><span class="pill"><span class="status-dot"></span> ${editing ? '꾸미기 모드' : '우리의 첫 번째 집'}</span><span class="room-number">${editing ? 'EDIT ROOM' : 'MY HOME'}</span></div><div class="large-room" style="--zoom:${zoom}">${roomSvg(editing)}</div><div class="stage-bottom"><span>${editing ? '가구 배치 기능은 준비 중이에요.' : '지금은 비어 있는, 가능성 가득한 집.'}</span><div class="zoom-controls"><button data-zoom="-" aria-label="집 축소">−</button><span id="zoom-label">${Math.round(zoom*100)}%</span><button data-zoom="+" aria-label="집 확대">+</button></div></div></section>
    <aside class="panel house-sidebar">${editing ? '<p class="eyebrow">MAKE IT OURS</p><h2>함께 채워갈 것들</h2><p class="muted">우리의 취향이 하나씩<br>쌓일 자리를 준비하고 있어요.</p><div class="catalog-empty"><span>⌑</span><h3>가구가 곧 찾아와요</h3><p>이번 버전은 빈 집과<br>수정 화면만 제공해요.</p><span class="soon-label">준비 중</span></div><a class="button secondary full" href="#house">집으로 돌아가기</a>' : '<p class="eyebrow">OUR LITTLE PLACE</p><h2>작은 집, 큰 마음.</h2><p class="muted">좋아하는 마음도, 짧은 안부도.<br>이곳에 차곡차곡 남겨볼까요?</p><div class="reaction-buttons"><button class="reaction" data-placeholder="좋아요"><span>♡</span><span>좋아요</span><small>준비 중</small></button><button class="reaction" data-placeholder="콘셉트 좋아요"><span>✧</span><span>콘셉트 좋아요</span><small>준비 중</small></button><button class="reaction" data-placeholder="코멘트"><span>☏</span><span>코멘트</span><small>준비 중</small></button></div><div class="aside-note"><span>⌂</span><p>오늘은 빈 공간을 둘러보고,<br>다음엔 우리답게 채워봐요.</p></div>'}</aside></div>`;
}

async function render() {
  const version = ++renderVersion;
  const route = location.hash || '#home';
  document.querySelector('#header-user').textContent = me.authenticated ? `${me.user.displayName}님의 공간` : '함께라서 더 좋은 집';
  document.querySelector('#logout').hidden = !me.authenticated;
  if (route === '#house' || route === '#edit') {
    if (!me.authenticated) { location.hash = '#home'; toast('먼저 로그인하고 우리집에 들어가세요.'); return; }
    app.innerHTML = '<p class="loading">우리집을 불러오는 중이에요…</p>';
    try {
      const house = await api(`/houses/${encodeURIComponent(me.user.houseId)}`);
      if (version !== renderVersion) return;
      app.innerHTML = houseScreen(house, route === '#edit');
    } catch (error) {
      if (version !== renderVersion) return;
      if (error.status === 401) { me = {authenticated:false}; location.hash = '#home'; toast(error.message); }
      else app.innerHTML = `<div class="error-panel"><h2>집을 불러오지 못했어요.</h2><p>${escape(error.message)}</p><a class="button secondary" href="#home">홈으로 돌아가기</a></div>`;
    }
  } else app.innerHTML = home(route === '#signup');
}

app.addEventListener('click', event => {
  const tabButton = event.target.closest('[data-tab]');
  if (tabButton) { tab = tabButton.dataset.tab; render().then(() => app.querySelector(`[data-tab="${tab}"]`)?.focus()); }
  const placeholder = event.target.closest('[data-placeholder]');
  if (placeholder) toast(`${placeholder.dataset.placeholder} 기능은 준비 중이에요. 아직 저장되지 않아요.`);
  const zoomButton = event.target.closest('[data-zoom]');
  if (zoomButton) {
    zoom = Math.min(1.3, Math.max(.7, Math.round((zoom + (zoomButton.dataset.zoom === '+' ? .1 : -.1))*10)/10));
    app.querySelector('.large-room').style.setProperty('--zoom', zoom);
    app.querySelector('#zoom-label').textContent = `${Math.round(zoom*100)}%`;
  }
});

app.addEventListener('submit', async event => {
  if (event.target.id !== 'auth-form') return;
  event.preventDefault();
  const form = event.target;
  const button = form.querySelector('[type=submit]');
  const loginId = new FormData(form).get('loginId').trim();
  if (!loginId) { form.querySelector('#form-error').textContent = '사용할 이름을 입력해 주세요.'; return; }
  button.disabled = true;
  try {
    // Deliberately omit password and email, even from the network request.
    me = await api(`/auth/${form.dataset.mode}`, {loginId});
    location.hash = '#house';
    toast('환영해요! 우리의 첫 번째 집이 준비됐어요.');
  } catch (error) { form.querySelector('#form-error').textContent = error.message; button.disabled = false; }
});

document.querySelector('#logout').addEventListener('click', async () => {
  try { await api('/auth/logout', {}); me = {authenticated:false}; if (location.hash === '#home') await render(); else location.hash = '#home'; toast('다음에 또 만나요.'); }
  catch (error) { toast(error.message); }
});
window.addEventListener('hashchange', render);
try { me = await api('/me'); await render(); }
catch { app.innerHTML = '<div class="error-panel"><h2>잠시 연결이 끊겼어요.</h2><p>서버가 실행 중인지 확인하고 다시 시도해 주세요.</p><button class="button primary" onclick="location.reload()">다시 연결하기</button></div>'; }
