import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const [base,fixturePath]=process.argv.slice(2);
const cookies=new Map();
async function request(path,method='GET',body) {
  let token;
  if(method!=='GET')token=await request('/api/csrf');
  const response=await fetch(base+path,{method,headers:{
    Cookie:[...cookies].map(([k,v])=>k+'='+v).join('; '),
    ...(token?{[token.headerName]:token.token}:{}),
    ...(body?{'Content-Type':'application/json'}:{})
  },...(body?{body:JSON.stringify(body)}:{})});
  for(const value of response.headers.getSetCookie()) {
    const [pair]=value.split(';'), split=pair.indexOf('=');
    cookies.set(pair.slice(0,split),pair.slice(split+1));
  }
  const text=await response.text();
  if(!response.ok)throw Error(method+' '+path+' returned '+response.status+': '+text);
  return text?JSON.parse(text):null;
}

const fixture=JSON.parse(await readFile(fixturePath,'utf8'));
for(const loginId of [fixture.host,fixture.guest]) {
 cookies.clear();
 const {user}=await request('/api/auth/login','POST',{loginId,password:'InviteBrowser123!'});
 assert.equal(user.houseId,fixture.hostHouse);
 assert.equal(user.canEdit,true);
 const status=await request('/api/couple');
 assert.equal(status.partner.displayName,loginId===fixture.host?fixture.guest:fixture.host);
 assert.equal(status.invite,null);
 assert.deepEqual(await request('/api/houses/'+user.houseId),fixture.saved);
 await request('/api/auth/logout','POST');
}
console.log('PASS: both accounts restore shared membership, partner profile, and furniture after restart.');
