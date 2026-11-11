// Use only with the fixture produced by live-browser-smoke against a disposable DB.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const base=process.argv[2],fixture=JSON.parse(await readFile(process.argv[3],'utf8'));
for(const loginId of fixture.logins) {
 let cookie='';
 async function request(route,method='GET',body) {
  const headers={Cookie:cookie,'Content-Type':'application/json'};
  if(method!=='GET') {
   const csrf=await request('/api/csrf');headers[csrf.data.headerName]=csrf.data.token;headers.Cookie=cookie;
  }
  const r=await fetch(base+route,{method,headers,...(body===undefined?{}:{body:JSON.stringify(body)})});
  for(const value of r.headers.getSetCookie())if(value.startsWith('JSESSIONID='))cookie=value.split(';')[0];
  return {status:r.status,data:await r.json().catch(()=>null)};
 }
 assert.equal((await request('/api/auth/login','POST',{loginId,password:fixture.password})).status,200);
 const me=(await request('/api/me')).data;assert.equal(me.user.houseId,fixture.house);
 const state=(await request('/api/houses/'+fixture.house)).data;
 const fridge=state.furniture.find(f=>f.id===fixture.fridge);
 assert.ok(fridge);assert.equal(fridge.rotation,true);assert.equal(state.furniture.length,2);
 const note=(await request('/api/houses/'+fixture.house+'/notes/'+fixture.note)).data;
 assert.equal(note.body,'우리만 보는 쪽지');assert.equal(note.isPublic,false);
}
assert.equal((await fetch(base+'/api/houses/'+fixture.house+'/notes/'+fixture.note)).status,404);
console.log('PASS: both members recover saved layout, rotation and private note after server restart');
