// Run against a disposable test environment with Chrome --remote-debugging-port=9223.
// Creates a qa* account for persistence checks; remove only that test account afterward.
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
const base=process.argv[2]||'http://localhost:8082';
const output=process.argv[3]||'target/auth-browser';
await mkdir(output,{recursive:true});
const tabs=await (await fetch('http://127.0.0.1:9223/json')).json();
const ws=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);
await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
let seq=0;const pending=new Map();
ws.onmessage=event=>{const m=JSON.parse(event.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}};
const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
const evaluate=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description);return r.result.value;};
const wait=async expression=>{for(let n=0;n<150;n++){if(await evaluate(expression))return;await new Promise(r=>setTimeout(r,100));}throw Error('Timeout: '+expression);};
const click=selector=>evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);
const screenshot=async name=>{const r=await send('Page.captureScreenshot',{format:'png'});await writeFile(path.join(output,name),Buffer.from(r.data,'base64'));};

const id='hb'+Date.now(),password='BrowserTest123!';
try {
 await send('Page.enable');await send('Network.enable');await send('Network.setCacheDisabled',{cacheDisabled:true});
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:base+'/#signup'});await wait("document.querySelector('#auth-form')?.dataset.mode==='register'");
 await evaluate(`document.querySelector('#email').value=${JSON.stringify(id+'@example.com')};document.querySelector('#login-id').value=${JSON.stringify(id)};document.querySelector('#password').value=${JSON.stringify(password)};`);
 await click('#auth-form button[type=submit]');await wait("!!document.querySelector('.viewer-canvas')");
 const me=await evaluate("(async()=> (await fetch('/api/me')).json())()");
 const readHouse=()=>evaluate(`(async()=> (await fetch('/api/houses/${me.user.houseId}')).json())()`);
 await writeFile(path.join(output,'test-account.json'),JSON.stringify({id,houseId:me.user.houseId},null,2));
 await click('a[href="#edit"]');await wait("document.querySelectorAll('.furniture-card').length===11");
 async function saved() { await wait("document.querySelector('.editor-shell').dataset.saving==='false' && !!document.querySelector('[data-object=delete]:not(:disabled)')"); }
 await click('[data-type="desk"]');await saved();
 assert.equal((await readHouse()).furniture[0].rotation,false);
 await click('[data-object="rotate"]');await saved();
 assert.equal((await readHouse()).furniture[0].rotation,true);
 await click('[data-object="rotate"]');await saved();
 assert.equal((await readHouse()).furniture[0].rotation,false);
 await click('[data-type="frame"]');await saved();
 await click('[data-type="fridge"]');await saved();
 await click('[data-type="note"]');await saved();
 await click('[data-type="door"]');await saved();
 await click('[data-type="rug"]');await saved();
 const before=await readHouse();assert.equal(before.furniture.length,6);
 await screenshot('editor-saved.png');
 await click('#finish-edit');await wait("!!document.querySelector('.viewer-canvas')");
 await wait("document.querySelector('.viewer-canvas').width>0");
 const beforePixels=await evaluate("document.querySelector('.viewer-canvas').toDataURL()");
 await screenshot('house-before-login.png');
 await click('#logout');await wait("!!document.querySelector('#auth-form')");
 await evaluate(`document.querySelector('#login-id').value=${JSON.stringify(id)};document.querySelector('#password').value=${JSON.stringify(password)};`);
 await click('#auth-form button[type=submit]');await wait("!!document.querySelector('.viewer-canvas')");
 await wait("document.querySelector('.viewer-canvas').width>0");
 assert.deepEqual(await readHouse(),before);
 assert.equal(await evaluate("document.querySelector('.viewer-canvas').toDataURL()"),beforePixels);
 await screenshot('house-after-login.png');
 await click('#logout');await wait("!!document.querySelector('#auth-form')");
 console.log('PASS: UI boolean direction toggle, six-object layout, attachments, logout/login snapshot and identical canvas pixels.');
} finally { ws.close(); }
