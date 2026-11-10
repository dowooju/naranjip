// Local opt-in integration test. Use a dedicated fixture path outside Git.
// prepare BASE FIXTURE -> restart app/DB -> verify BASE FIXTURE
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
const [mode, base='http://localhost:8082', fixturePath] = process.argv.slice(2);
if(!['prepare','verify'].includes(mode)||!fixturePath)throw Error('Usage: node scripts/house-persistence-smoke.mjs prepare|verify BASE FIXTURE');
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
const password='HousePersistence123!';
let fixture;
if(mode==='prepare') {
  const loginId='hp'+Date.now();
  const {user}=await request('/api/auth/register','POST',{loginId,email:loginId+'@example.com',password});
  const route='/api/houses/'+user.houseId;
  let house=await request(route);
  async function place(typeId,x,y,rotation=false,wall=null,parentId=null,id=null) {
    house=await request(route+'/furniture'+(id?'/'+id:''),id?'PUT':'POST',{typeId,x,y,rotation,wall,parentId,revision:house.revision});
    return house.furniture.at(-1).id;
  }
  const desk=await place('desk',1,2);
  await place('frame',0,0,false,null,desk);
  await place('frame',0,0,false,null,desk);
  const fridge=await place('fridge',6,6,true);
  await place('note',0,0,false,null,fridge);
  await place('door',0,3,true,'left');
  await place('rug',2,2);
  await place('sofa',5,2,true);
  await place('chair',1,6);
  await place('desk',3,2,true,null,null,desk);
  fixture={loginId,house};
  await writeFile(fixturePath,JSON.stringify(fixture,null,2));
  await request('/api/auth/logout','POST');
} else fixture=JSON.parse(await readFile(fixturePath,'utf8'));
cookies.clear();
const {user}=await request('/api/auth/login','POST',{loginId:fixture.loginId,password});
assert.equal(user.houseId,fixture.house.id);
const restored=await request('/api/houses/'+user.houseId);
assert.deepEqual(restored,fixture.house);
assert(restored.furniture.every(item=>typeof item.rotation==='boolean'));
await request('/api/auth/logout','POST');
console.log(JSON.stringify({mode,passed:true,furniture:restored.furniture.length,revision:restored.revision}));
