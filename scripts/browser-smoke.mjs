// Optional local browser integration check. Start Chrome with --remote-debugging-port=9223
// and run: node scripts/browser-smoke.mjs http://localhost:8082 <screenshot-directory>
import assert from 'node:assert/strict';
import { writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
const base=process.argv[2]||'http://localhost:8082';
const output=process.argv[3]||'target/browser-check';
await mkdir(output,{recursive:true});
const tabs=await (await fetch('http://127.0.0.1:9223/json')).json();
const tab=tabs.find(t=>t.type==='page');
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
let sequence=0;const awaiting=new Map(),errors=[];
ws.onmessage=event=>{const msg=JSON.parse(event.data);if(msg.id){const item=awaiting.get(msg.id);if(item){awaiting.delete(msg.id);msg.error?item.reject(msg.error):item.resolve(msg.result);}}else if(msg.method==='Runtime.exceptionThrown')errors.push(msg.params.exceptionDetails.text);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;awaiting.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
const evaluate=async expression=>{const result=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(result.exceptionDetails)throw new Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);return result.result.value;};
const wait=async expression=>{for(let i=0;i<100;i++){if(await evaluate(expression))return;await new Promise(r=>setTimeout(r,100));}throw new Error('Timed out: '+expression);};
const mouse=(type,x,y)=>send('Input.dispatchMouseEvent',{type,x,y,button:type==='mouseMoved'?'none':'left',buttons:type==='mouseReleased'?0:1,clickCount:1});
const click=async selector=>{const p=await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.scrollIntoView({block:'nearest'});const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);await mouse('mousePressed',p.x,p.y);await mouse('mouseReleased',p.x,p.y);};
const getHouse=()=>evaluate(`(async()=>{const me=await (await fetch('/api/me')).json();return (await fetch('/api/houses/'+me.user.houseId)).json();})()`);
const screenPoint=async(x,y)=>evaluate(`(()=>{const r=document.querySelector('.editor-room canvas').getBoundingClientRect();const s=Math.min(r.width/650,r.height/550);return {x:r.x+(r.width-640*s)/2+${x}*s,y:r.y+(r.height-540*s)/2+${y}*s};})()`);
const screenshot=async name=>{const result=await send('Page.captureScreenshot',{format:'png'});await writeFile(path.join(output,name),Buffer.from(result.data,'base64'));};
async function dragAt(type,worldX,worldY,expectedCount){
  const start=await evaluate(`(()=>{const e=document.querySelector('[data-type="${type}"]');e.scrollIntoView({block:'nearest'});const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);
  const end=await screenPoint(worldX,worldY);
  await mouse('mousePressed',start.x,start.y);
  for(let i=1;i<=8;i++)await mouse('mouseMoved',start.x+(end.x-start.x)*i/8,start.y+(end.y-start.y)*i/8);
  await mouse('mouseReleased',end.x,end.y);
  await wait(`document.querySelector('.editor-shell').dataset.saving==='false'`);
  assert.equal((await getHouse()).furniture.length,expectedCount,`drag ${type}`);
}
const drag=(type,x,y,count)=>dragAt(type,320+(x-y)*32,230+(x+y)*16,count);
try {
  await send('Runtime.enable');await send('Page.enable');await send('Network.enable');await send('Network.setCacheDisabled',{cacheDisabled:true});
  await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
  await send('Page.navigate',{url:base});await wait(`!!document.querySelector('#app')`);
  await evaluate(`fetch('/api/auth/logout',{method:'POST'})`);
  await send('Page.reload',{ignoreCache:true});await wait(`!!document.querySelector('#auth-form')`);
  await evaluate(`document.querySelector('#login-id').value='꾸미기테스트';document.querySelector('#auth-form').requestSubmit();`);
  await wait(`!!document.querySelector('.viewer-canvas')`);
  await click('a[href="#edit"]');await wait(`document.querySelectorAll('.furniture-card').length===11`);
  assert.equal(await evaluate(`getComputedStyle(document.querySelector('body>.header')).display`),'none');
  assert.equal(await evaluate(`document.querySelector('.editor-shell').getBoundingClientRect().height`),1000);
  await screenshot('editor-empty.png');
  await drag('chair',1.5,4.5,1);
  await drag('sofa',3.5,4.5,2);
  await drag('desk',1.5,1.5,3);
  await drag('fridge',5.5,1.5,4);
  await drag('rug',3.5,4.5,5);
  // Invalid overlap must preserve the server state.
  await drag('chair',1.5,4.5,5);
  await evaluate(`document.querySelector('[data-object="cancel"]').click()`);
  const house=await getHouse();
  const sofa=house.furniture.find(i=>i.typeId==='sofa');assert.equal(sofa.x,3);
  // Select the sofa by its visible top and use the UI rotation button.
  const sofaPoint=await screenPoint(320+(4-4.5)*32,230+(4+4.5)*16-40);
  await mouse('mousePressed',sofaPoint.x,sofaPoint.y);await mouse('mouseReleased',sofaPoint.x,sofaPoint.y);
  await click('[data-object="rotate"]');await wait(`document.querySelector('.editor-shell').dataset.saving==='false'`);
  assert.equal((await getHouse()).furniture.find(i=>i.id===sofa.id).rotation,90);
  // Door must be dropped on the wall surface, not in the middle of the floor.
  await drag('door',4.5,4.5,5);
  await dragAt('door',320-6.5*32,230+6.5*16-80,6);
  assert.equal((await getHouse()).furniture.find(i=>i.typeId==='door').wall,'left');
  // Raster attachment hit testing uses the parent furniture, never arbitrary floor cells.
  await dragAt('frame',320+(2-1.5)*32,230+(2+1.5)*16-32,7);
  await dragAt('frame',320+(2-1.5)*32,230+(2+1.5)*16-32,8);
  await dragAt('frame',320+(2-1.5)*32,230+(2+1.5)*16-32,8);
  await dragAt('note',320+(5.5-1.5)*32,230+(5.5+1.5)*16-40,9);
  await evaluate(`document.querySelector('[data-object="cancel"]').click()`);
  // Existing furniture drag and deletion controls.
  const chairStart=await screenPoint(320+(1.5-4.5)*32,230+(1.5+4.5)*16-60);
  const chairEnd=await screenPoint(320+(1.5-6.5)*32,230+(1.5+6.5)*16);
  await mouse('mousePressed',chairStart.x,chairStart.y);await mouse('mouseMoved',chairEnd.x,chairEnd.y);await mouse('mouseReleased',chairEnd.x,chairEnd.y);
  await wait(`document.querySelector('.editor-shell').dataset.saving==='false'`);
  assert.equal((await getHouse()).furniture.find(i=>i.typeId==='chair').y,6);
  await drag('plant-small',6.5,6.5,10);
  const plantPoint=await screenPoint(320,230+13*16-25);
  await mouse('mousePressed',plantPoint.x,plantPoint.y);await mouse('mouseReleased',plantPoint.x,plantPoint.y);
  await click('[data-object="delete"]');await wait(`document.querySelector('.editor-shell').dataset.saving==='false'`);
  assert.equal((await getHouse()).furniture.length,9);
  await screenshot('editor-decorated.png');
  await click('#finish-edit');await wait(`!!document.querySelector('.viewer-canvas')`);
  assert.equal((await getHouse()).furniture.length,9);
  await screenshot('house-decorated.png');
  await send('Page.reload',{ignoreCache:true});await wait(`!!document.querySelector('.viewer-canvas')`);
  assert.equal((await getHouse()).furniture.length,9);
  await click('a[href="#edit"]');await wait(`document.querySelectorAll('.furniture-card').length===11`);
  await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await new Promise(r=>setTimeout(r,300));
  assert.equal(await evaluate(`document.documentElement.scrollWidth<=window.innerWidth`),true);
  assert.equal(await evaluate(`document.querySelector('.editor-room canvas').getBoundingClientRect().right<=window.innerWidth`),true);
  assert.equal(await evaluate(`document.querySelector('#finish-edit').getBoundingClientRect().right<=window.innerWidth`),true);
  await screenshot('editor-mobile.png');
  assert.deepEqual(errors,[]);
  console.log('PASS: fullscreen, 11 raster assets, real pointer drag, collision rollback, rug layering, rotation, view/edit transition, reload persistence, mobile width, no browser exceptions');
} catch(error) {
  await screenshot('editor-failure.png');
  console.log(await evaluate(`({hint:document.querySelector('.editor-hint')?.textContent,status:document.querySelector('.save-status')?.textContent})`));
  console.log(await getHouse());
  throw error;
} finally {ws.close();}
