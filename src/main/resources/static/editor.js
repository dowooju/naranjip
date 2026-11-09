import { createScene } from './furniture-scene.js';
import { floorCell, validatePlacement, automaticPlacement } from './placement.js';

export function editorMarkup() {
  return `<section class="editor-shell" aria-label="전체 화면 집 꾸미기" data-saving="false">
    <header class="editor-toolbar"><div class="editor-title">♡ 우리집 꾸미기 <small>MY LITTLE ROOM STUDIO</small></div><span class="save-status" role="status">가구 불러오는 중…</span><button class="button primary" id="finish-edit">꾸미기 완료 ✓</button></header>
    <div class="editor-body"><div class="editor-room"><div class="editor-hint" role="status">가구를 누르면 중앙의 빈자리에 놓여요.</div><canvas class="room-canvas" tabindex="0" aria-label="집 꾸미기. 가구 선택 후 방향키로 위치 이동, R로 회전, Enter로 배치, Escape로 취소"></canvas>
      <div class="editor-camera"><button data-camera="out" aria-label="축소">−</button><span id="editor-zoom">100%</span><button data-camera="in" aria-label="확대">+</button></div>
      <div class="object-tools" hidden><button data-object="rotate" aria-label="가구 90도 회전" title="90도 회전 (R)">↻</button><button data-object="delete" aria-label="가구 삭제" title="삭제 (Delete)">×</button></div>
    </div><aside class="catalog-panel"><h2>✧ 나의 가구 상자</h2><p>누르면 중앙부터 빈자리에 놓여요.<br>액자·쪽지는 왼쪽 가구에 자동으로 붙어요.</p><div class="catalog-filters" aria-label="가구 분류"><button data-filter="all" aria-pressed="true">전체</button><button data-filter="floor" aria-pressed="false">가구</button><button data-filter="decor" aria-pressed="false">소품</button></div><div class="furniture-list"></div></aside></div>
    <footer class="editor-footer"><span>놓을 때마다 자동 저장 · 서버 재시작 시 초기화</span><span>우클릭: 회전·삭제 / R: 회전 / Esc: 취소</span></footer>
  </section>`;
}

export async function mountEditor(root, initialHouse, toast) {
  let house = initialHouse, scene, selected = null, armed = null, drag = null, candidate = null;
  let pending = null, disposed = false, zoom = 1, filter = 'all', uncertain = false;
  const shell = root.querySelector('.editor-shell'), canvas = root.querySelector('.room-canvas');
  const status = root.querySelector('.save-status'), hint = root.querySelector('.editor-hint');
  const tools = root.querySelector('.object-tools');
  const abort = new AbortController();
  const on = (target, name, handler, options = {}) => target.addEventListener(name, handler, {...options,signal:abort.signal});
  async function request(path, options) {
    const response = await fetch(path, options);
    if (!response.ok) {
      let message = response.status === 401 ? '로그인이 만료됐어요. 홈에서 다시 로그인해 주세요.' : '변경을 저장하지 못했어요.';
      try { message = (await response.json()).message || message; } catch {}
      throw new Error(message);
    }
    return response.json();
  }
  const catalog = await request('/api/catalog');
  scene = await createScene(canvas, house, catalog, true);
  if (!canvas.isConnected) { scene.destroy(); return {pending:false,destroy(){}}; }
  const types = scene.types;
  function setStatus(text, state='saved') { status.textContent=text; status.dataset.state=state; }
  function renderCatalog() {
    const items=catalog.items.filter(type=>filter==='all'||(filter==='floor' ? type.kind==='floor' : type.kind!=='floor'));
    const list=root.querySelector('.furniture-list');
    list.innerHTML=items.map(type=>`<button class="furniture-card" data-type="${type.id}" aria-pressed="${armed===type.id}" title="${type.description}"><canvas width="180" height="150" aria-hidden="true"></canvas><strong>${type.name}</strong><small>${type.kind==='wall'?'벽 2칸':type.kind==='attachment'?'가구에 부착':`${type.width} × ${type.height}`}</small></button>`).join('');
    list.querySelectorAll('[data-type]').forEach(button=>scene.thumbnail(types.get(button.dataset.type),button.querySelector('canvas')));
  }
  function showTools() {
    tools.hidden=!selected&&!armed;
    const item=selected ? house.furniture.find(item=>item.id===selected) : {typeId:armed};
    const type=types.get(item?.typeId);
    tools.querySelector('[data-object=rotate]').disabled=!type||type.kind==='wall'||type.kind==='attachment';
    tools.querySelector('[data-object=delete]').disabled=!selected;
    positionTools();
  }
  function positionTools() {
    if(tools.hidden)return;
    const item=candidate||house.furniture.find(item=>item.id===selected);
    if(!item){tools.hidden=true;return;}
    const b=scene.screenBounds(item), area=canvas.parentElement.getBoundingClientRect();
    const half=tools.offsetWidth/2+5;
    tools.style.left=Math.max(half,Math.min(area.width-half,b.x+b.width/2-area.left))+'px';
    tools.style.top=Math.max(5,b.y-area.top-tools.offsetHeight-8)+'px';
  }
  function cancel() {
    selected=null; armed=null; drag=null; candidate=null; scene.select(null); scene.preview(null); showTools(); renderCatalog();
    hint.textContent='가구를 클릭해서 추가하세요. 놓을 수 있는 자리는 파란색이에요.';
  }
  async function resync() {
    house=await request(`/api/houses/${house.id}`); scene.update(house); uncertain=false;
  }
  async function mutate(method, path, body) {
    if (pending||disposed) return;
    shell.dataset.saving='true'; setStatus('저장 중…','saving'); scene.preview(null);
    pending=(async()=>{
      try {
        const oldIds=new Set(house.furniture.map(item=>item.id));
        const retainId=body?.id||selected;
        house=await request(path,{method,headers:{'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
        if(disposed)return;
        scene.update(house); uncertain=false; setStatus('✓ 저장했어요'); cancel();
        if(method!=='DELETE') {
          selected=house.furniture.find(item=>!oldIds.has(item.id))?.id||retainId;
          scene.select(selected); showTools();
        }
      } catch(error) {
        if(disposed)return;
        toast(error.message); uncertain=true;
        setStatus('저장 여부 확인 중…','error');
        try { await resync(); setStatus('최신 배치를 다시 불러왔어요'); cancel(); }
        catch { setStatus('연결 끊김 · 저장 여부 확인 필요','error'); hint.textContent='마지막 변경의 저장 여부를 확인할 수 없어요. 연결 복구 후 다시 불러옵니다.'; }
      } finally { pending=null; if(!disposed)shell.dataset.saving='false'; }
    })();
    await pending;
  }
  function toCandidate(event, source) {
    const point=scene.point(event), type=types.get(source.typeId);
    const item={...source,...floorCell(point)};
    if(type.kind==='wall') {
      // Only accept points on the actual wall surface or immediately at its base.
      if(point.x<320) {
        const pos=(320-point.x)/32, base=230+pos*16;
        item.x=0; item.y=Math.floor(pos); item.wall='left'; item.rotation=90;
        if(point.y<base-180||point.y>base+22) item.x=-1;
      } else {
        const pos=(point.x-320)/32, base=230+pos*16;
        item.x=Math.floor(pos); item.y=0; item.wall='right'; item.rotation=0;
        if(point.y<base-180||point.y>base+22) item.y=-1;
      }
    }
    if(type.kind==='attachment') {
      const expected=type.id==='frame'?'desk':'fridge';
      const parent=scene.hit(point,p=>p.typeId===expected);
      item.parentId=parent?.id||null; item.slot=0;
      if(parent) {
        const used=house.furniture.filter(p=>p.parentId===parent.id&&p.id!==item.id).map(p=>p.slot);
        while(used.includes(item.slot))item.slot++;
      }
    }
    return item;
  }
  function preview(item) {
    candidate=item;
    const result=validatePlacement(item,house,types);
    scene.preview({item,valid:result.valid}); hint.textContent=result.reason;
    showTools();
    return result;
  }
  async function place(item) {
    if(pending||uncertain)return;
    if(!validatePlacement(item,house,types).valid) { scene.preview(null); candidate=null; hint.textContent='그 자리에는 놓을 수 없어요. 원래 위치를 유지했어요.'; return; }
    await mutate(item.id?'PUT':'POST',`/api/houses/${house.id}/furniture${item.id?'/'+item.id:''}`,{...item,revision:house.revision});
  }
  async function addAutomatically(typeId) {
    if(pending||uncertain)return;
    const item=automaticPlacement(typeId,house,types);
    if(!item) {
      cancel();toast(types.get(typeId).kind==='attachment'?'붙일 수 있는 책상·냉장고의 빈자리가 없어요.':'가구를 놓을 빈자리가 없어요.');return;
    }
    await place(item);
  }
  function arm(typeId) {
    if(pending||uncertain)return;
    selected=null; armed=typeId; candidate=null; scene.select(null); scene.preview(null); showTools(); renderCatalog();
    hint.textContent=types.get(typeId).description+' · 원하는 곳에 놓아 주세요.';
  }
  async function rotate() {
    if(pending||uncertain)return;
    const item=candidate||house.furniture.find(item=>item.id===selected);
    if(!item) { if(armed) preview({typeId:armed,x:3,y:3,rotation:90}); return; }
    const type=types.get(item.typeId);
    if(type.kind==='wall'||type.kind==='attachment')return;
    const rotated={...item,rotation:(item.rotation+90)%360};
    if(armed||drag) { preview(rotated); if(drag)drag.source.rotation=rotated.rotation; }
    else if(validatePlacement(rotated,house,types).valid)await place(rotated);
    else toast('회전하면 경계를 넘거나 다른 가구와 겹쳐요.');
  }
  async function remove() {
    if(!selected||pending||uncertain)return;
    const children=house.furniture.some(item=>item.parentId===selected);
    if(children&&!window.confirm('이 가구에 붙어 있는 액자·쪽지도 함께 삭제됩니다. 치울까요?'))return;
    await mutate('DELETE',`/api/houses/${house.id}/furniture/${selected}?revision=${house.revision}&cascade=${children}`);
  }
  on(root,'pointerdown',event=>{
    const button=event.target.closest('[data-type]');
    if(!button||event.button!==0||pending||uncertain)return;
    event.preventDefault(); arm(button.dataset.type);
    drag={source:{typeId:armed,rotation:0},startX:event.clientX,startY:event.clientY,moved:false,fromCatalog:true};
  });
  on(root,'click',event=>{
    const filterButton=event.target.closest('[data-filter]');
    if(filterButton) {
      filter=filterButton.dataset.filter; root.querySelectorAll('[data-filter]').forEach(button=>button.setAttribute('aria-pressed',String(button===filterButton))); renderCatalog();
    }
    const typeButton=event.target.closest('[data-type]');
    if(typeButton&&event.detail===0) { addAutomatically(typeButton.dataset.type); canvas.focus(); }
    const action=event.target.closest('[data-object]')?.dataset.object;
    if(action==='rotate')rotate(); if(action==='delete')remove(); if(action==='cancel')cancel();
    const camera=event.target.closest('[data-camera]')?.dataset.camera;
    if(camera) { zoom=Math.min(1.5,Math.max(.7,zoom+(camera==='in'?.1:-.1))); scene.zoom(zoom); root.querySelector('#editor-zoom').textContent=Math.round(zoom*100)+'%'; }
  });
  on(canvas,'pointerdown',event=>{
    if(event.button!==0||pending||uncertain)return;
    event.preventDefault(); canvas.focus();
    if(armed) { const source=candidate||{typeId:armed,rotation:0}; place(toCandidate(event,source)); return; }
    const hit=scene.hit(scene.point(event));
    if(!hit) { cancel(); return; }
    selected=hit.id; scene.select(selected); showTools();
    drag={source:{...hit},startX:event.clientX,startY:event.clientY,moved:false,fromCatalog:false};
    canvas.setPointerCapture(event.pointerId);
  });
  on(window,'pointermove',event=>{
    if(pending||uncertain)return;
    const rect=canvas.getBoundingClientRect();
    const inside=event.clientX>=rect.left&&event.clientX<=rect.right&&event.clientY>=rect.top&&event.clientY<=rect.bottom;
    if(drag) {
      if(Math.hypot(event.clientX-drag.startX,event.clientY-drag.startY)>4)drag.moved=true;
      if(drag.moved&&inside)preview(toCandidate(event,drag.source)); else if(!inside)scene.preview(null);
    } else if(armed&&inside)preview(toCandidate(event,candidate||{typeId:armed,rotation:0}));
  });
  on(window,'pointerup',event=>{
    if(!drag)return;
    const current=drag; drag=null;
    const rect=canvas.getBoundingClientRect(), inside=event.clientX>=rect.left&&event.clientX<=rect.right&&event.clientY>=rect.top&&event.clientY<=rect.bottom;
    if(current.fromCatalog&&!current.moved)addAutomatically(current.source.typeId);
    else if(current.moved&&inside)place(toCandidate(event,current.source));
    else { scene.preview(null); candidate=null; showTools(); }
  });
  on(window,'pointercancel',()=>{ drag=null; candidate=null; scene.preview(null); });
  on(canvas,'scenechange',positionTools);
  on(canvas,'contextmenu',event=>{
    const item=scene.hit(scene.point(event));
    if(item) { event.preventDefault(); if(!pending){ selected=item.id;armed=null;scene.select(selected);scene.preview(null);candidate=null;showTools();renderCatalog(); } }
  });
  on(window,'keydown',event=>{
    if(event.target instanceof HTMLInputElement||pending)return;
    if(event.key==='Escape') { event.preventDefault(); cancel(); }
    if(event.key.toLowerCase()==='r') { event.preventDefault(); rotate(); }
    if(event.key==='Delete'||event.key==='Backspace') { if(selected){event.preventDefault();remove();} }
    if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(event.key)) {
      if(!armed&&!selected)return;
      event.preventDefault();
      const item={...(candidate||house.furniture.find(item=>item.id===selected)||{typeId:armed,x:3,y:3,rotation:0})};
      if(event.key==='ArrowUp')item.y--; if(event.key==='ArrowDown')item.y++; if(event.key==='ArrowLeft')item.x--; if(event.key==='ArrowRight')item.x++;
      if(types.get(item.typeId).kind==='wall') { item.wall=item.rotation===90?'left':'right'; if(item.wall==='left')item.x=0;else item.y=0; }
      preview(item);
    }
    if(event.key==='Enter'&&candidate) {event.preventDefault();place(candidate);}
  });
  on(window,'online',async()=>{
    if(!uncertain||pending)return;
    try {await resync();setStatus('다시 연결했어요');cancel();}catch{setStatus('연결을 확인해 주세요','error');}
  });
  on(window,'beforeunload',event=>{if(pending){event.preventDefault();event.returnValue='';}});
  on(root.querySelector('#finish-edit'),'click',async()=>{
    if(pending){hint.textContent='마지막 변경을 저장하는 중이에요.';await pending;}
    if(uncertain&&!window.confirm('마지막 변경의 저장 여부를 확인할 수 없어요. 편집을 닫을까요?'))return;
    location.hash='#house';
  });
  // Also reconciles another tab's edits when this editor becomes active again.
  on(window,'focus',async()=>{if(!pending&&!drag&&!armed){try{await resync();setStatus('✓ 최신 배치');}catch{uncertain=true;setStatus('다시 연결해 주세요','error');}}});
  setStatus('✓ 모든 변경 저장됨'); renderCatalog();
  return {
    get pending(){return !!pending;},
    destroy(){disposed=true;abort.abort();scene.destroy();},
  };
}
