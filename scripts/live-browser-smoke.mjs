// Run only against a disposable database. Chrome needs --remote-debugging-port=9223.
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
const base=process.argv[2]||'http://localhost:8087',output=process.argv[3]||'target/live-browser';
await mkdir(output,{recursive:true});
const version=await(await fetch('http://127.0.0.1:9223/json/version')).json();
const ws=new WebSocket(version.webSocketDebuggerUrl);await new Promise((ok,no)=>{ws.onopen=ok;ws.onerror=no;});
let sequence=0;const pending=new Map(),contexts=[],errors=[];
const send=(method,params={},sessionId)=>new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params,...(sessionId?{sessionId}:{})}));});
ws.onmessage=e=>{
 const m=JSON.parse(e.data);
 if(m.method==='Page.javascriptDialogOpening')send('Page.handleJavaScriptDialog',{accept:true},m.sessionId);
 if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.exception?.description||m.params.exceptionDetails.text);
 if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(Error(JSON.stringify(m.error))):p.resolve(m.result);}
};
async function page() {
 const {browserContextId}=await send('Target.createBrowserContext');contexts.push(browserContextId);
 const {targetId}=await send('Target.createTarget',{url:'about:blank',browserContextId});
 const {sessionId}=await send('Target.attachToTarget',{targetId,flatten:true});
 await send('Page.enable',{},sessionId);await send('Runtime.enable',{},sessionId);await send('Network.enable',{},sessionId);
 await send('Network.setCacheDisabled',{cacheDisabled:true},sessionId);
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false},sessionId);
 await send('Page.addScriptToEvaluateOnNewDocument',{source:"window.__liveMessages=[];window.__sockets=[];window.WebSocket=class extends WebSocket{constructor(...args){super(...args);window.__sockets.push(this);this.addEventListener('message',e=>window.__liveMessages.push(JSON.parse(e.data)));}};"},sessionId);
 await send('Page.navigate',{url:base+'/#home'},sessionId);
 await wait(sessionId,"!!document.querySelector('#auth-form')");
 return sessionId;
}
async function evaluate(s,expression) {
 const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true},s);
 if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);
 return r.result.value;
}
async function wait(s,expression) {
 for(let n=0;n<160;n++){if(await evaluate(s,expression))return;await new Promise(r=>setTimeout(r,100));}
 throw Error('Timeout: '+expression);
}
async function api(s,url,method='GET',body,headers={}) {
 return evaluate(s,`(async()=>{const {apiFetch}=await import('/api-client.js');const r=await apiFetch(${JSON.stringify(url)},{method:${JSON.stringify(method)},headers:{'Content-Type':'application/json',...${JSON.stringify(headers)}},...(${body===undefined} ? {}:{body:JSON.stringify(${JSON.stringify(body??null)})})});return {status:r.status,data:await r.json().catch(()=>null)};})()`);
}
const click=(s,selector)=>evaluate(s,`document.querySelector(${JSON.stringify(selector)}).click()`);
const shot=async(s,name)=>{const r=await send('Page.captureScreenshot',{format:'png'},s);await writeFile(path.join(output,name),Buffer.from(r.data,'base64'));};
async function editor(s){await send('Page.navigate',{url:base+'/#edit'},s);await wait(s,"document.querySelector('.live-status')?.dataset.online==='true'");}
async function raw(s,message) {
 const requestId='qa'+Math.random();await evaluate(s,`window.__sockets.at(-1).send(JSON.stringify(${JSON.stringify({...message,requestId})}))`);
 await wait(s,`window.__liveMessages.some(m=>m.requestId===${JSON.stringify(requestId)})`);
 return evaluate(s,`window.__liveMessages.find(m=>m.requestId===${JSON.stringify(requestId)})`);
}
async function coordinate(s,id) {
 return evaluate(s,`(async()=>{
 const house=await(await fetch('/api/houses/'+window.__house)).json(),catalog=await(await fetch('/api/catalog')).json();
 const actual=document.querySelector('.editor-room canvas').getBoundingClientRect(),canvas=document.createElement('canvas');
 canvas.style.cssText='position:fixed;left:-10000px;top:-10000px;width:'+actual.width+'px;height:'+actual.height+'px';document.body.append(canvas);
 const {createScene}=await import('/furniture-scene.js');const scene=await createScene(canvas,house,catalog,true);
 const item=house.furniture.find(f=>f.id===${JSON.stringify(id)}),bounds=scene.screenBounds(item),rect=canvas.getBoundingClientRect();let result=null;
 for(let y=bounds.y+3;y<bounds.y+bounds.height&&!result;y+=4)for(let x=bounds.x+3;x<bounds.x+bounds.width;x+=4){const px=actual.left+x-rect.left,py=actual.top+y-rect.top;if(document.elementFromPoint(px,py)===document.querySelector('.editor-room canvas')&&scene.hit(scene.point({clientX:x,clientY:y}))?.id===item.id){result={x:px,y:py};break;}}
 scene.destroy();canvas.remove();return result;
 })()`);
}
async function pointer(s,type,point,extra={}){await send('Input.dispatchMouseEvent',{type,...point,...extra},s);}
try {
 const a=await page(),b=await page(),visitor=await page(),stamp=Date.now(),password='LiveBrowser123!';
 const logins=['la'+stamp,'lb'+stamp];
 for(const [i,s] of [a,b].entries())assert.equal((await api(s,'/api/auth/register','POST',{loginId:logins[i],email:logins[i]+'@example.com',password})).status,201);
 const house=(await api(a,'/api/houses','POST',{})).data.houseId;
 const code=(await api(a,'/api/houses/'+house+'/invite','POST',{})).data.code;
 assert.equal((await api(b,'/api/invites/accept','POST',{code,agreeDelete:false})).status,200);
 assert.equal((await api(visitor,'/api/live/ticket?houseId='+house,'POST',{})).status,401);
 await editor(a);await editor(b);
 await evaluate(a,'window.__house='+JSON.stringify(house));await evaluate(b,'window.__house='+JSON.stringify(house));
 await wait(a,"document.querySelector('.live-status').textContent.includes('함께')");
 await wait(b,"document.querySelector('.live-status').textContent.includes('함께')");
 await click(a,'[data-type=fridge]');
 await wait(b,"Number(document.querySelector('.room-canvas').dataset.revision)>=1");
 await wait(a,"document.querySelector('.editor-shell').dataset.saving==='false'");
 await click(a,'[data-type=note]');
 await wait(b,"!!document.querySelector('[data-note-id]')");
 let state=(await api(a,'/api/houses/'+house)).data;
 const fridge=state.furniture.find(f=>f.typeId==='fridge').id,note=state.furniture.find(f=>f.typeId==='note').id;
 await click(a,'[data-note-id]');await wait(a,"!!document.querySelector('#note-body')");
 assert.equal(await evaluate(a,"document.querySelector('#note-public').checked"),false);
 await wait(b,"!!document.querySelector('.remote-label')");
 assert.equal((await raw(b,{type:'lock',id:fridge})).type,'rejected');
 assert.equal((await api(b,'/api/houses/'+house+'/furniture/'+fridge+'?revision='+state.revision+'&cascade=true','DELETE')).status,409);
 await evaluate(a,"document.querySelector('#note-body').value='둘이 꾸미는 집 <img src=x onerror=alert(1)>';document.querySelector('#note-public').checked=true");
 await shot(a,'note-editing.png');await shot(b,'partner-locked.png');
 await click(a,'#note-form button');await wait(a,"!document.querySelector('.note-dialog')");
 await wait(b,"document.querySelector('.note-card span')?.textContent.includes('둘이 꾸미는 집')");
 assert.equal(await evaluate(b,"document.querySelectorAll('.note-card img').length"),0);
 assert.equal((await api(visitor,'/api/houses/'+house+'/notes/'+note)).status,200);
 await click(b,'[data-note-id]');await wait(b,"!!document.querySelector('#note-body')");
 await evaluate(b,"document.querySelector('#note-public').checked=false;document.querySelector('#note-body').value='우리만 보는 쪽지'");
 await click(b,'#note-form button');await wait(b,"!document.querySelector('.note-dialog')");
 await wait(a,"document.querySelector('.note-card span')?.textContent==='우리만 보는 쪽지'");
 assert.equal((await api(visitor,'/api/houses/'+house+'/notes/'+note)).status,404);
 const point=await coordinate(a,fridge);assert.ok(point);
 await pointer(a,'mousePressed',point,{button:'left',buttons:1,clickCount:1});
 await wait(b,"!!document.querySelector('.remote-label')");
 const destination=await evaluate(a,"(()=>{const r=document.querySelector('.editor-room canvas').getBoundingClientRect(),scale=Math.min(r.width/650,r.height/550);return {x:r.left+(r.width-640*scale)/2+320*scale,y:r.top+(r.height-540*scale)/2+(230+5.5*32)*scale};})()");
 await pointer(a,'mouseMoved',destination,{button:'left',buttons:1});
 await wait(b,"window.__liveMessages.some(m=>m.type==='preview')");
 const before=(await api(a,'/api/houses/'+house)).data.revision;
 await shot(b,'partner-drag-preview.png');
 assert.equal((await api(a,'/api/houses/'+house)).data.revision,before);
 await pointer(a,'mouseReleased',destination,{button:'left',buttons:0,clickCount:1});
 await wait(b,`Number(document.querySelector('.room-canvas').dataset.revision)>${before}`);
 await wait(a,"document.querySelector('.editor-shell').dataset.saving==='false'");
 assert.equal((await api(a,'/api/houses/'+house+'/notes/'+note)).data.body,'우리만 보는 쪽지');
 await click(a,'[data-object=rotate]');
 await wait(b,`Number(document.querySelector('.room-canvas').dataset.revision)>${before+1}`);
 await wait(a,"document.querySelector('.editor-shell').dataset.saving==='false'");
 // Closing a socket must release leases and reconnect with a fresh snapshot.
 await raw(a,{type:'lock',id:fridge});
 await evaluate(a,"window.__sockets.at(-1).close()");
 await wait(a,"document.querySelector('.live-status')?.dataset.online==='true' && window.__sockets.length>1");
 assert.equal((await raw(b,{type:'lock',id:fridge})).type,'locked');
 await evaluate(b,"window.__sockets.at(-1).send(JSON.stringify({type:'release'}))");
 // A silent client must lose its lease even without a clean close frame.
 await raw(a,{type:'lock',id:fridge});
 await evaluate(a,"(()=>{const s=window.__sockets.at(-1),original=s.send.bind(s);s.send=data=>{if(JSON.parse(data).type!=='ping')original(data);};})()");
 await new Promise(r=>setTimeout(r,16000));
 assert.equal((await raw(b,{type:'lock',id:fridge})).type,'locked');
 await evaluate(b,"window.__sockets.at(-1).send(JSON.stringify({type:'release'}))");
 await wait(a,"document.querySelector('.live-status')?.dataset.online==='true' && window.__sockets.length>2");
 await click(a,'[data-type=chair]');
 await wait(a,"document.querySelector('.editor-shell').dataset.saving==='false' && !document.querySelector('.object-tools').hidden");
 state=(await api(a,'/api/houses/'+house)).data;
 const chair=state.furniture.find(f=>f.typeId==='chair');assert.ok(chair);
 await wait(b,`Number(document.querySelector('.room-canvas').dataset.revision)>=${state.revision}`);
 await click(a,'[data-object=delete]');
 await wait(b,`Number(document.querySelector('.room-canvas').dataset.revision)>${state.revision}`);
 assert.equal((await api(a,'/api/houses/'+house)).data.furniture.some(f=>f.id===chair.id),false);
 await shot(a,'owner-final.png');await shot(b,'partner-final.png');
 await writeFile(path.join(output,'fixture.json'),JSON.stringify({house,fridge,note,logins,password}));
 assert.deepEqual(errors,[]);
 console.log('PASS: two isolated accounts, real WebSocket handshake, presence, UI add/drag/rotate/delete, lease conflict, note privacy, reconnect, no browser exceptions');
} finally {
 for(const browserContextId of contexts)await send('Target.disposeBrowserContext',{browserContextId});
 ws.close();
}
