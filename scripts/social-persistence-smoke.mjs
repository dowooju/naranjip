import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
const [mode,base,fixturePath]=process.argv.slice(2);
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

const password='SocialBrowser123!';
if(mode==='prepare') {
 const suffix=Date.now(),fixture={admin:'sa'+suffix,owner:'so'+suffix,visitor:'sv'+suffix};
 for(const role of ['admin','owner','visitor']) {
  cookies.clear();const loginId=fixture[role];
  await request('/api/auth/register','POST',{loginId,email:loginId+'@example.com',password});
  if(role==='owner') {
   const house=await request('/api/houses','POST',{});fixture.houseId=house.houseId;
   await request('/api/houses/'+house.houseId+'/furniture','POST',{typeId:'desk',x:2,y:2,rotation:false,revision:0});
  }
  await request('/api/auth/logout','POST');
 }
 await writeFile(fixturePath,JSON.stringify(fixture,null,2));console.log('Prepared isolated social test accounts.');
} else {
 const fixture=JSON.parse(await readFile(fixturePath,'utf8'));
 await request('/api/auth/login','POST',{loginId:fixture.visitor,password});
 const stats=await request('/api/houses/'+fixture.houseId+'/social');
 assert.equal(stats.generalLikes,1);assert.equal(stats.themeLikes,0);assert.equal(stats.comments,1);
 assert.equal(stats.theme.title,'따뜻한 겨울집');assert.equal(stats.generalLiked,true);
 assert.equal((await request('/api/houses/'+fixture.houseId+'/guestbook')).items[0].comment.body,'포근한 방이에요 ♡');
 console.log('PASS: theme, votes and comments persist.');
}
