import { createNotes } from './notes.js';
import { rankingMarkup, socialMarkup, mountSocial, adminMarkup, mountAdmin } from './social.js';
import { coupleMarkup, mountCouple } from './couple.js';
import { roomSvg } from './room.js';
import { editorMarkup, mountEditor } from './editor.js';
import { createScene } from './furniture-scene.js';
import { apiFetch } from './api-client.js';
import { validateAuth } from './auth-validation.js';

const app = document.querySelector('#app');
let me = { authenticated: false };
let tab = 'general';
let rankingData={theme:null,houses:[]};
let zoom = 1;
let renderVersion = 0;
let toastTimer;
let currentEditor = null;
let currentScene = null;
let currentNotes = null;
const escape = value => String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));

async function api(path, body, method='POST') {
  const response = await apiFetch(`/api${path}`, body === undefined ? {} : {
    method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
  });
  if (!response.ok) {
    let details = {};
    try { details = await response.json(); } catch {}
    const error = new Error(details.message || (response.status === 401 ? '다시 로그인해 주세요.' : '요청을 완료하지 못했어요. 잠시 후 다시 시도해 주세요.'));
    error.fields = details.fields || {};
    error.status = response.status;
    throw error;
  }
  const text=await response.text();
  return text ? JSON.parse(text) : null;
}

function toast(message) {
  const el = document.querySelector('#toast');
  el.textContent = message; el.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { el.hidden = true; }, 3500);
}

function ranking() {
  return `<aside class="panel ranking-panel"><div class="panel-eyebrow"><span class="tiny-star">✧</span> 이웃의 공간</div>
    <div class="tabs" role="tablist" aria-label="집 순위"><button role="tab" data-tab="general" aria-selected="${tab === 'general'}">명예의 전당</button><button role="tab" data-tab="theme" aria-selected="${tab === 'theme'}">이번 주 콘셉트</button></div>
    ${rankingMarkup(rankingData,tab)}
    <div class="aside-note"><span>♡</span><p>작은 공간에도<br>우리다운 이야기는 가득하니까.</p></div></aside>`;
}

function authPanel(register = false) {
  if (me.authenticated) return `<section class="panel account-panel"><span class="avatar">${escape(me.user.displayName.slice(0,1))}</span><p class="eyebrow">WELCOME HOME</p><h2>${escape(me.user.displayName)}님,<br>집에 잘 오셨어요.</h2><p class="muted">${me.user.partnerName ? escape(me.user.displayName)+' ♡ '+escape(me.user.partnerName) : '혼자 시작해도, 함께 채워도 좋아요.'}</p>${me.user.houseId ? '<a class="button primary full" href="#house">우리의 집 가기 ↗</a>' : '<a class="button primary full" href="#couple">새 집 만들기 / 초대 코드 입력</a>'}<a class="button secondary full" href="#couple">♡ ${me.user.partnerName ? escape(me.user.partnerName)+'님 프로필' : '커플 연결하기'}</a>${me.user.admin ? '<a class="admin-link" href="#admin">♛ 이번 주 콘셉트 관리</a>' : ''}<div class="session-note">꾸민 집은 자동 저장돼요. 다음에도 그대로 만나요.</div></section>`;
  return `<section class="panel account-panel"><span class="small-house">⌂</span><p class="eyebrow">${register ? 'A NEW BEGINNING' : 'MAKE YOURSELF AT HOME'}</p><h2>${register ? '우리의 첫 페이지를<br>열어볼까요?' : '우리의 공간에<br>어서 오세요.'}</h2><p class="muted">${register ? '나란집에서 함께할 이름을 알려주세요.' : '나란히 앉아, 같은 집을 꿈꾸는 곳.'}</p>
    <form id="auth-form" data-mode="${register ? 'register' : 'login'}" novalidate>
      ${register ? '<label for="email">이메일</label><input id="email" name="email" type="email" placeholder="hello@naranjip.com" autocomplete="email" required maxlength="254" aria-describedby="email-error"><p id="email-error" class="field-error" data-error="email" aria-live="polite"></p>' : ''}
      <label for="login-id">아이디</label><input id="login-id" name="loginId" placeholder="${register ? '한글·영문·숫자·밑줄 3~20자' : '아이디를 입력해 주세요'}" autocomplete="username" required maxlength="20" aria-describedby="loginId-error"><p id="loginId-error" class="field-error" data-error="loginId" aria-live="polite"></p>
      <label for="password">비밀번호</label><input id="password" name="password" type="password" placeholder="${register ? '8자 이상 입력해 주세요' : '비밀번호를 입력해 주세요'}" autocomplete="${register ? 'new-password' : 'current-password'}" required maxlength="72" aria-describedby="password-error"><p id="password-error" class="field-error" data-error="password" aria-live="polite"></p>
      <p id="form-error" class="form-error" role="alert"></p><button class="button primary full" type="submit">${register ? '가입하고 시작하기' : '로그인'} <span>→</span></button>
    </form><p class="auth-switch">${register ? '이미 함께하고 있나요? <a href="#home">로그인</a>' : '나란집이 처음인가요? <a href="#signup">회원가입</a>'}</p>
    </section>`;
}

function home(register) {
  return `<section class="intro"><div><p class="eyebrow">A LITTLE SPACE, A LOT OF US</p><h1>나란히, <span>조금씩.</span></h1><p>함께 채워갈 우리의 공간, 나란집</p></div><span class="intro-note">서로의 하루가 머무는 곳 <span>⌁</span></span></section>
    <div class="home-grid">${ranking()}<section class="panel welcome-panel"><div class="room-heading"><span class="pill"><span class="status-dot"></span> 우리의 첫 번째 집</span><span class="room-number">ROOM 001</span></div><div class="home-room">${roomSvg()}</div><div class="welcome-caption"><p class="eyebrow">EVERY STORY STARTS SOMEWHERE</p><h2>아직은 빈 집,<br>곧 우리의 집.</h2><p>좋아하는 것들로 채워갈 내일을 상상해 보세요.</p></div><span class="corner-spark">✧</span></section>${authPanel(register)}</div>`;
}

function houseScreen(house) {
  const owner=me.authenticated&&me.user.houseId===house.id;
  return `<section class="house-top"><div><a class="back-link" href="#home">← 마을로 돌아가기</a><h1>우리집에 오신 걸 환영해요.</h1><p>${escape(house.ownerName)}님의 작은 공간</p></div>${owner ? '<a class="button primary" href="#edit">수정하기 ↗</a>' : '<span class="pill">이웃집 구경 중 ♡</span>'}</section>
    <div class="house-layout"><section class="panel house-stage"><div class="room-heading"><span class="pill"><span class="status-dot"></span> 우리의 첫 번째 집</span><span class="room-number">MY HOME</span></div><div class="large-room"></div><div class="stage-bottom"><span>우리의 취향이 하나씩 쌓이는 집.</span><div class="zoom-controls"><button data-zoom="-" aria-label="집 축소">−</button><span id="zoom-label">${Math.round(zoom*100)}%</span><button data-zoom="+" aria-label="집 확대">+</button></div></div></section>
    <aside class="panel house-sidebar"><p class="eyebrow">OUR LITTLE PLACE</p><h2>작은 집, 큰 마음.</h2>${owner?'<a class="button secondary full" href="#couple">♡ 커플 프로필</a>':''}<button class="button secondary full" data-random>다른 집 구경하기 ↗</button><button class="button secondary full" data-house-refresh>최신 집 불러오기 ↻</button><p class="muted">좋아하는 마음도, 짧은 안부도.<br>이곳에 차곡차곡 남겨볼까요?</p><section class="house-notes"></section>${socialMarkup()}<div class="aside-note"><span>⌂</span><p>좋아하는 것 하나, 추억 하나.<br>조금씩 우리다운 공간으로.</p></div></aside></div>`;
}
async function render() {
  if (currentEditor?.pending) {
    history.replaceState(null, '', '#edit');
    toast('마지막 변경을 저장하는 중이에요. 잠시 기다려 주세요.');
    return;
  }
  currentEditor?.destroy(); currentEditor = null;
  currentScene?.destroy(); currentScene = null;
  currentNotes?.destroy(); currentNotes = null;
  const version = ++renderVersion;
  const route = location.hash || '#home';
  try { me = await api('/me'); } catch(error) { toast(error.message); return; }
  if(version!==renderVersion)return;
  if((route==='#house'||route==='#edit') && me.authenticated && !me.user.houseId) { location.hash='#couple'; return; }
  if(route==='#edit' && me.authenticated && !me.user.canEdit) { location.hash='#house'; toast('꾸미기는 집 주인만 할 수 있어요.'); return; }
  document.body.classList.toggle('editor-active', route === '#edit' && me.authenticated);
  document.querySelector('#header-user').textContent = me.authenticated ? `${me.user.displayName}님의 공간` : '함께라서 더 좋은 집';
  document.querySelector('#logout').hidden = !me.authenticated;
  if(route === '#admin') {
    if(!me.authenticated){location.hash='#home';toast('관리자 계정으로 로그인해 주세요.');return;}
    if(!me.user.admin){app.innerHTML='<div class="error-panel">관리자만 사용할 수 있어요.</div>';return;}
    app.innerHTML='<p class="loading">콘셉트를 불러오는 중이에요…</p>';
    try { const data=await api('/admin/themes');if(version!==renderVersion)return;app.innerHTML=adminMarkup(data);mountAdmin(app,data,api,render,toast); }
    catch(error){if(version===renderVersion)app.innerHTML=`<div class="error-panel">${escape(error.message)}</div>`;}
  } else if(route === '#couple') {
    if(!me.authenticated){location.hash='#home';toast('먼저 로그인해 주세요.');return;}
    app.innerHTML='<p class="loading">우리의 연결을 불러오는 중이에요…</p>';
    try {
      const data=await api('/couple'); if(version!==renderVersion)return;
      app.innerHTML=coupleMarkup(data); mountCouple(app,api,render,toast,data);
    } catch(error) { if(version===renderVersion)app.innerHTML=`<div class="error-panel"><p>${escape(error.message)}</p><a class="button secondary" href="#home">홈으로</a></div>`; }
  } else if (route === '#house' || route === '#edit' || route.startsWith('#visit/')) {
    if (!me.authenticated && !route.startsWith('#visit/')) { location.hash = '#home'; toast('먼저 로그인하고 우리집에 들어가세요.'); return; }
    app.innerHTML = '<p class="loading">우리집을 불러오는 중이에요…</p>';
    try {
      const house = await api(route.startsWith('#visit/') ? '/visits/'+encodeURIComponent(route.slice(7)) : `/houses/${encodeURIComponent(me.user.houseId)}`);
      if (version !== renderVersion) return;
      if (route === '#edit') {
        app.innerHTML = editorMarkup();
        const editor = await mountEditor(app, house, toast);
        if (version !== renderVersion) { editor.destroy(); return; }
        currentEditor = editor;
      } else {
        app.innerHTML = houseScreen(house);
        const target = app.querySelector('.large-room');
        target.style.removeProperty('--zoom');
        target.innerHTML = '<canvas class="room-canvas viewer-canvas" aria-label="내가 꾸민 집"></canvas>';
        const catalog = await api('/catalog');
        if (version !== renderVersion) return;
        const scene = await createScene(target.querySelector('canvas'), house, catalog);
        if (version !== renderVersion) { scene.destroy(); return; }
        currentScene = scene;
        currentScene.zoom(zoom);
        currentNotes=createNotes(app.querySelector('.house-notes'),house.id);
        const notes=currentNotes;
        await notes.refresh();
        if(version!==renderVersion)return;
        target.querySelector('canvas').addEventListener('click',event=>{
          const item=scene.hit(scene.point(event),item=>item.typeId==='note');
          if(item&&notes.canRead(item.id))notes.open(item.id);
        });
        await mountSocial(app,house.id,me,api,()=>{
          sessionStorage.setItem('after-login','#visit/'+house.id);location.hash='#home';toast('로그인 후 같은 집으로 돌아와요.');
        }).catch(error=>{if(version===renderVersion)app.querySelector('#social-panel').textContent=error.message;});
        app.querySelector('.stage-bottom>span').textContent = house.furniture.length ? '우리의 취향이 하나씩 쌓이는 집.' : '수정하기에서 첫 가구를 놓아 보세요.';
        app.querySelector('.house-top p').textContent = `${house.ownerName+'님의 공간'} · 8 × 8 · 가구 ${house.furniture.length}개`;
      }
    } catch (error) {
      if (version !== renderVersion) return;
      document.body.classList.remove('editor-active');
      if (error.status === 401) { me = {authenticated:false}; location.hash = '#home'; toast(error.message); }
      else app.innerHTML = `<div class="error-panel"><h2>집을 불러오지 못했어요.</h2><p>${escape(error.message)}</p><a class="button secondary" href="#home">홈으로 돌아가기</a></div>`;
    }
  } else {
    try {rankingData=await api('/rankings?type='+tab);if(version!==renderVersion)return;app.innerHTML=home(route==='#signup');}
    catch(error){if(version===renderVersion)app.innerHTML=`<div class="error-panel">${escape(error.message)}</div>`;}
  }
}

app.addEventListener('click', async event => {
  if(event.target.closest('[data-house-refresh]'))render();
  if(event.target.closest('[data-random]')) {
    try {const result=await api('/houses/random');if(result.houseId)location.hash='#visit/'+result.houseId;else toast('지금 구경할 집이 없어요.');}
    catch(error){toast(error.message);}
  }
  const tabButton = event.target.closest('[data-tab]');
  if (tabButton) { tab = tabButton.dataset.tab; render().then(() => app.querySelector(`[data-tab="${tab}"]`)?.focus()); }
  const zoomButton = event.target.closest('[data-zoom]');
  if (zoomButton) {
    zoom = Math.min(1.3, Math.max(.7, Math.round((zoom + (zoomButton.dataset.zoom === '+' ? .1 : -.1))*10)/10));
    currentScene?.zoom(zoom);
    app.querySelector('#zoom-label').textContent = `${Math.round(zoom*100)}%`;
  }
});

app.addEventListener('submit', async event => {
  if (event.target.id !== 'auth-form') return;
  event.preventDefault();
  const form = event.target;
  const button = form.querySelector('[type=submit]');
  const data = new FormData(form);
  const input = {loginId:data.get('loginId').trim(), password:data.get('password'), email:(data.get('email') || '').trim()};
  const errors = validateAuth(input, form.dataset.mode === 'register');
  showAuthErrors(form, errors);
  form.querySelector('#form-error').textContent = '';
  if (Object.keys(errors).length) { form.querySelector('[aria-invalid="true"]')?.focus(); return; }
  button.disabled = true;
  try {
    me = await api(`/auth/${form.dataset.mode}`, input);
    form.reset();
    const back=sessionStorage.getItem('after-login');sessionStorage.removeItem('after-login');
    location.hash = back?.startsWith('#visit/') ? back : '#house';
    toast('환영해요! 나란집에 잘 오셨어요.');
  } catch (error) { showAuthErrors(form, error.fields || {}); form.querySelector('#form-error').textContent = error.message; button.disabled = false; }
});

function showAuthErrors(form, errors) {
  form.querySelectorAll('[data-error]').forEach(el => {
    const field = el.dataset.error;
    el.textContent = errors[field] || '';
    form.elements.namedItem(field)?.setAttribute('aria-invalid', errors[field] ? 'true' : 'false');
  });
}
app.addEventListener('input', event => {
  const field = event.target.name;
  const form = event.target.closest('#auth-form');
  if (!form || !field) return;
  const label = form.querySelector(`[data-error="${field}"]`);
  if (label) label.textContent = '';
  event.target.setAttribute('aria-invalid','false');
  form.querySelector('#form-error').textContent = '';
});

document.querySelector('#logout').addEventListener('click', async () => {
  try { await api('/auth/logout', {}); me = {authenticated:false}; if (location.hash === '#home') await render(); else location.hash = '#home'; toast('다음에 또 만나요.'); }
  catch (error) { toast(error.message); }
});
window.addEventListener('hashchange', render);
try { me = await api('/me'); await render(); }
catch { app.innerHTML = '<div class="error-panel"><h2>잠시 연결이 끊겼어요.</h2><p>서버가 실행 중인지 확인하고 다시 시도해 주세요.</p><button class="button primary" onclick="location.reload()">다시 연결하기</button></div>'; }
