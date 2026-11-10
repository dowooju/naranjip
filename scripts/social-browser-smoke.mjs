// Run against a disposable test environment with Chrome --remote-debugging-port=9223.
// Creates a qa* account for persistence checks; remove only that test account afterward.
import assert from 'node:assert/strict';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import path from 'node:path';
const base=process.argv[2]||'http://localhost:8082';
const output=process.argv[3]||'target/auth-browser';
await mkdir(output,{recursive:true});
const tabs=await (await fetch('http://127.0.0.1:9223/json')).json();
const ws=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);
await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
let seq=0;const pending=new Map();
ws.onmessage=event=>{const m=JSON.parse(event.data);if(m.method==='Page.javascriptDialogOpening')send('Page.handleJavaScriptDialog',{accept:true});if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}};
const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
const evaluate=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description);return r.result.value;};
const wait=async expression=>{for(let n=0;n<150;n++){if(await evaluate(expression))return;await new Promise(r=>setTimeout(r,100));}throw Error('Timeout: '+expression);};
const click=selector=>evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);
const screenshot=async name=>{const r=await send('Page.captureScreenshot',{format:'png'});await writeFile(path.join(output,name),Buffer.from(r.data,'base64'));};

const fixture=JSON.parse(await readFile(process.argv[4],'utf8')),password='SocialBrowser123!';
async function login(id) {
 await wait("!!document.querySelector('#auth-form')");
 await evaluate(`document.querySelector('#login-id').value=${JSON.stringify(id)};document.querySelector('#password').value=${JSON.stringify(password)};`);
 await click('#auth-form button[type=submit]');await wait("!document.querySelector('#auth-form') && !document.querySelector('.loading')");
}
async function navigate(hash) {await evaluate(`location.hash=${JSON.stringify(hash)}`);}
async function logout(){await click('#logout');await wait("!!document.querySelector('#auth-form')");}
async function publish(title) {
 await navigate('#admin');await wait("!!document.querySelector('#theme-form')");
 await evaluate(`document.querySelector('#theme-title').value=${JSON.stringify(title)};document.querySelector('#theme-description').value='포근한 소품으로 이번 주의 방을 꾸며 주세요.';`);
 await click('#theme-form button[type=submit]');
 await wait(`document.querySelector('.admin-panel .theme-announcement strong')?.textContent===${JSON.stringify(title)}`);
}
try {
 await send('Page.enable');await send('Network.enable');await send('Network.setCacheDisabled',{cacheDisabled:true});
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:base+'/#home'});await login(fixture.admin);
 await publish('비 오는 날의 집');await screenshot('admin-concept.png');await logout();
 await navigate('#visit/'+fixture.houseId);await wait("!!document.querySelector('[data-like=general]')");
 assert.equal(await evaluate("!!document.querySelector('a[href=\"#edit\"]')"),false);
 await click('[data-like=general]');await login(fixture.visitor);
 await wait("!!document.querySelector('[data-like=general]')");
 assert.equal(await evaluate("location.hash"),'#visit/'+fixture.houseId);
 assert.equal(await evaluate("document.querySelector('[data-like=general]').getAttribute('aria-pressed')"),'false');
 for(const expected of ['true','false','true']) {
  await click('[data-like=general]');await wait(`document.querySelector('[data-like=general]')?.getAttribute('aria-pressed')==='${expected}' && !document.querySelector('[data-like=general]').disabled`);
 }
 await click('[data-like=theme]');await wait("document.querySelector('[data-like=theme]')?.getAttribute('aria-pressed')==='true' && !document.querySelector('[data-like=theme]').disabled");
 await evaluate("document.querySelector('#comment-body').value='<img src=x onerror=alert(1)>'");
 await click('#comment-form button');await wait("!!document.querySelector('[data-delete-comment]')");
 assert.equal(await evaluate("document.querySelectorAll('.comment-list img').length"),0);
 await click('[data-delete-comment]');await wait("!document.querySelector('[data-delete-comment]')");
 await evaluate("document.querySelector('#comment-body').value='포근한 방이에요 ♡'");
 await click('#comment-form button');await wait("!!document.querySelector('[data-delete-comment]')");
 await screenshot('visitor-reactions.png');
 await navigate('#admin');await wait("document.querySelector('.error-panel')?.textContent.includes('관리자')");
 await logout();await login(fixture.admin);await publish('따뜻한 겨울집');await logout();await login(fixture.owner);
 await navigate('#house');await wait("!!document.querySelector('[data-like=general]')");
 assert.equal(await evaluate("document.querySelector('[data-like=general]').disabled"),true);
 assert.equal(await evaluate("document.querySelector('[data-like=general] strong').textContent"),'1');
 assert.equal(await evaluate("document.querySelector('[data-like=theme] strong').textContent"),'0');
 assert.equal(await evaluate("document.querySelector('.comment-list p').textContent"),'포근한 방이에요 ♡');
 assert.equal(await evaluate("!!document.querySelector('[data-delete-comment]')"),true);
 await screenshot('owner-comments.png');
 await navigate('#home');await wait("document.querySelectorAll('.rank-card').length>0");
 await click('[data-tab=theme]');await wait("document.querySelector('[data-tab=theme]').getAttribute('aria-selected')==='true'");
 await screenshot('weekly-ranking.png');
 await logout();
 console.log('PASS: admin publication/access, anonymous visit/login return, independent like toggles, escaped comments/delete, owner rules, theme reset, rankings.');
}finally{ws.close();}
