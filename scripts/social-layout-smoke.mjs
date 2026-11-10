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


const fixture=JSON.parse(await readFile(process.argv[4],'utf8'));
try {
 await send('Page.enable');await send('Network.enable');await send('Network.setCacheDisabled',{cacheDisabled:true});
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:base+'/#visit/'+fixture.houseId});
 await wait("!!document.querySelector('.comment-list p')");
 assert.equal(await evaluate("getComputedStyle(document.querySelector('.house-layout')).alignItems"),'start');
 assert((await evaluate("document.querySelector('.house-stage').getBoundingClientRect().height"))<800);
 await screenshot('visitor-layout-final.png');
 console.log('PASS: house no longer stretches with comments.');
}finally{ws.close();}
