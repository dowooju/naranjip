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
let dialogAccept=true; let seq=0;const pending=new Map();
ws.onmessage=event=>{const m=JSON.parse(event.data);if(m.method==='Page.javascriptDialogOpening')send('Page.handleJavaScriptDialog',{accept:dialogAccept});if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}};
const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
const evaluate=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description);return r.result.value;};
const wait=async expression=>{for(let n=0;n<150;n++){if(await evaluate(expression))return;await new Promise(r=>setTimeout(r,100));}throw Error('Timeout: '+expression);};
const click=selector=>evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);
const screenshot=async name=>{const r=await send('Page.captureScreenshot',{format:'png'});await writeFile(path.join(output,name),Buffer.from(r.data,'base64'));};

const suffix=Date.now(),host='ih'+suffix,guest='ig'+suffix,password='InviteBrowser123!';
let hostHouse,guestHouse,code;
const me=()=>evaluate("(async()=> (await fetch('/api/me')).json())()");
const house=id=>evaluate(`(async()=> (await fetch('/api/houses/${id}')).json())()`);
async function register(id) {
 await send('Page.navigate',{url:base+'/#signup'});
 await wait("document.querySelector('#auth-form')?.dataset.mode==='register'");
 await evaluate(`document.querySelector('#email').value=${JSON.stringify(id+'@example.com')};document.querySelector('#login-id').value=${JSON.stringify(id)};document.querySelector('#password').value=${JSON.stringify(password)};`);
 await click('#auth-form button[type=submit]');await wait("!!document.querySelector('[data-create-house]')");
 assert.equal((await me()).user.houseId,null);
}
async function createAndDecorate(type) {
 await click('[data-create-house]');await wait("!!document.querySelector('[data-issue]')");
 const id=(await me()).user.houseId;
 await click('a[href="#house"]');await wait("!!document.querySelector('.viewer-canvas')");
 await click('a[href="#edit"]');await wait("document.querySelectorAll('.furniture-card').length===11");
 await click('[data-type="'+type+'"]');await wait("document.querySelector('.editor-shell').dataset.saving==='false' && !!document.querySelector('[data-object=delete]:not(:disabled)')");
 await click('#finish-edit');await wait("!!document.querySelector('.viewer-canvas')");return id;
}
async function logout(){await click('#logout');await wait("!!document.querySelector('#auth-form')");}
try {
 await send('Page.enable');await send('Network.enable');await send('Network.setCacheDisabled',{cacheDisabled:true});
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await register(host);hostHouse=await createAndDecorate('desk');
 await click('a[href="#couple"]');await wait("!!document.querySelector('[data-issue]')");
 await click('[data-issue]');await wait("!!document.querySelector('#invite-code')");
 code=await evaluate("document.querySelector('#invite-code').value");assert.match(code,/^[A-Z0-9]{7}$/);
 await screenshot('host-invite-code.png');await logout();
 await register(guest);guestHouse=await createAndDecorate('chair');
 await click('a[href="#couple"]');await wait("!!document.querySelector('#received-code')");
 await evaluate(`document.querySelector('#received-code').value=${JSON.stringify(code)};`);
 await click('#couple-form button[type=submit]');await wait("!!document.querySelector('[data-accept]')");
 dialogAccept=false;await click('[data-accept]');
 assert.equal((await me()).user.houseId,guestHouse);assert.equal((await house(guestHouse)).furniture.length,1);
 await screenshot('invite-delete-confirmation.png');
 dialogAccept=true;await click('[data-accept]');await wait("!document.querySelector('#received-code') && document.querySelector('.couple-manage')?.textContent.includes('함께 살기')");
 assert.equal((await me()).user.houseId,hostHouse);
 assert.equal(await evaluate(`(async()=> (await fetch('/api/houses/${guestHouse}')).status)()`),404);
 const profiles=await evaluate("document.querySelector('.couple-profiles').textContent");
 assert(profiles.includes(host)&&profiles.includes(guest));assert(!profiles.includes('@example.com'));
 assert.equal((await house(hostHouse)).furniture[0].typeId,'desk');
 await screenshot('couple-profiles.png');
 await click('a[href="#house"]');await wait("!!document.querySelector('.viewer-canvas')");
 await click('a[href="#edit"]');await wait("document.querySelectorAll('.furniture-card').length===11");
 await click('[data-type="plant-small"]');await wait("document.querySelector('.editor-shell').dataset.saving==='false' && !!document.querySelector('[data-object=delete]:not(:disabled)')");
 await click('#finish-edit');await wait("!!document.querySelector('.viewer-canvas')");await logout();
 await evaluate(`document.querySelector('#login-id').value=${JSON.stringify(host)};document.querySelector('#password').value=${JSON.stringify(password)};`);
 await click('#auth-form button[type=submit]');await wait("!!document.querySelector('.viewer-canvas')");
 const saved=await house(hostHouse);assert.equal(saved.furniture.length,2);
 await screenshot('shared-house.png');
 await click('a[href="#couple"]');await wait("document.querySelector('.couple-manage')?.textContent.includes('함께 살기')");
 assert.equal(await evaluate("!!document.querySelector('#invite-code') || !!document.querySelector('#received-code')"),false);
 await writeFile(path.join(output,'test-accounts.json'),JSON.stringify({host,guest,hostHouse,guestHouse,saved},null,2));
 console.log('PASS: create house, code issue, deletion cancellation preserves old furniture, acceptance moves house, profiles, both members edit, relogin.');
}finally{ws.close();}
