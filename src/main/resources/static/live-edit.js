import {apiFetch} from './api-client.js';
export function connectLive(houseId,{snapshot,presence,preview,clearPreview,connection,lostLock}) {
 let socket=null,clientId=null,closed=false,ready=false,retry=null,lastPong=0,held=null,locks=[],people=[],sequence=0,epoch=0;
 const requests=new Map();
 const send=value=>{if(socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify(value));};
 function fail() {
  ready=false;held=null;clientId=null;clearPreview();connection(false);
  for(const r of requests.values()){clearTimeout(r.timer);r.reject(Error('실시간 연결이 끊겼어요. 다시 연결한 뒤 시도해 주세요.'));}requests.clear();
  lostLock();
 }
 async function start() {
  if(closed)return;
  try {
   const response=await apiFetch('/api/live/ticket?houseId='+encodeURIComponent(houseId),{method:'POST'});
   if(!response.ok)throw Error('실시간 접속 권한을 확인하지 못했어요.');
   const {ticket}=await response.json();if(closed)return;
   socket=new WebSocket((location.protocol==='https:'?'wss:':'ws:')+'//'+location.host+'/ws/edit?ticket='+encodeURIComponent(ticket));
   const current=socket;
   current.onmessage=event=>{
    if(closed||socket!==current)return;
    const m=JSON.parse(event.data);
    if(m.type==='hello'){clientId=m.clientId;ready=true;lastPong=Date.now();snapshot(m.house);connection(true);}
    if(m.type==='pong')lastPong=Date.now();
    if(m.type==='snapshot')snapshot(m.house);
    if(m.type==='presence'){
     locks=m.locks;people=m.people;presence(people.filter(p=>p.clientId!==clientId),locks.filter(l=>l.clientId!==clientId));
     if(held&&!locks.some(l=>l.clientId===clientId)){held=null;lostLock();}
     clearPreview(locks.map(l=>l.clientId));
    }
    if(m.type==='preview'&&m.clientId!==clientId)preview(m.clientId,m.item);
    if(m.type==='previewEnd')clearPreview(null,m.clientId);
    if(m.type==='locked'||m.type==='rejected'){
     const r=requests.get(m.requestId);if(!r)return;clearTimeout(r.timer);requests.delete(m.requestId);
     if(r.epoch!==epoch){r.reject(Error('선택이 바뀌었어요. 다시 선택해 주세요.'));return;}
     if(m.type==='locked'){held=m.id;r.resolve();}else r.reject(Error(m.message));
    }
   };
   current.onclose=()=>{if(socket!==current)return;fail();if(!closed)retry=setTimeout(start,1500);};
   current.onerror=()=>current.close();
  }catch {fail();if(!closed)retry=setTimeout(start,2000);}
 }
 const heartbeat=setInterval(()=>{
  if(ready&&Date.now()-lastPong>18000){socket.close();return;}
  if(ready)send({type:'ping'});
 },4000);
 start();
 return {
  get ready(){return ready;},get clientId(){return clientId;},
  headers(){return clientId?{'X-Live-Client':clientId}:{};},
  lock(id){
   if(!ready)return Promise.reject(Error('실시간 연결 중이에요. 연결된 뒤 다시 시도해 주세요.'));
   if(held===id)return Promise.resolve();
   const requestId=String(++sequence),current=++epoch;
   return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{requests.delete(requestId);socket.close();reject(Error('편집 권한 확인 시간이 초과됐어요.'));},5000);
    requests.set(requestId,{resolve,reject,timer,epoch:current});send({type:'lock',id,requestId});
   });
  },
  release(){epoch++;held=null;send({type:'release'});},
  preview(item){if(ready&&held===item.id)send({type:'preview',id:item.id,x:item.x,y:item.y,rotation:item.rotation,wall:item.wall,parentId:item.parentId,slot:item.slot});},
  destroy(){closed=true;clearTimeout(retry);clearInterval(heartbeat);socket?.close();fail();}
 };
}
