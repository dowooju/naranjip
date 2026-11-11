import {apiFetch} from './api-client.js';
const escape=value=>String(value??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
export function createNotes(root,houseId,{editing=false,onSaved=async()=>{},onBusy=()=>{}}={}) {
 let previews=[],disposed=false,saving=false,dialog=null;
 const abort=new AbortController(),signal=abort.signal;
 async function request(path,options={}) {
  const response=await apiFetch('/api/houses/'+houseId+'/notes'+path,{cache:'no-store',...options});
  if(!response.ok) {
   let message=response.status===404?'이 쪽지는 공개되지 않았거나 삭제됐어요.':'쪽지를 불러오지 못했어요.';
   try {message=(await response.json()).message||message;}catch{}
   throw Error(message);
  }
  return response.json();
 }
 async function refresh() {
  previews=await request('');if(disposed)return;
  root.innerHTML=`<h3>✉ ${editing?'쪽지 내용 편집':'냉장고의 쪽지'}</h3><p class="muted">${editing?'쪽지를 골라 내용을 적고 공개 여부를 정해 주세요.':'읽을 수 있는 쪽지만 보여요. 눌러서 펼쳐 보세요.'}</p><div class="note-list">${previews.map((n,i)=>`<button class="note-card" data-note-id="${n.id}"><strong>쪽지 ${i+1} · ${n.isPublic?'공개':'우리만'}</strong><span>${escape(n.preview)||'아직 적힌 내용이 없어요.'}</span></button>`).join('')||'<p class="muted">읽을 수 있는 쪽지가 없어요.</p>'}</div>`;
 }
 function close() {if(saving)return;dialog?.close();dialog?.remove();dialog=null;}
 async function open(id) {
  if(disposed||saving||dialog)return;
  const box=document.createElement('dialog');dialog=box;box.className='note-dialog';box.setAttribute('aria-label',editing?'쪽지 편집':'쪽지 읽기');
  box.innerHTML='<p>쪽지를 불러오는 중이에요…</p><button class="button secondary" data-close>닫기</button>';
  document.body.append(box);box.showModal();let note=null,dirty=false;
  box.addEventListener('cancel',event=>{event.preventDefault();if(!saving&&(!dirty||confirm('저장하지 않은 내용을 버릴까요?')))close();});
  const load=async()=>{
   note=await request('/'+id);if(disposed||dialog!==box)return;
   box.innerHTML=`<h2>✉ ${editing?'마음을 담은 쪽지':'냉장고의 쪽지'}</h2>
   ${editing?`<form id="note-form"><label for="note-body">쪽지 내용 (200자 이내)</label><textarea id="note-body" maxlength="200" rows="6"></textarea><label class="note-visibility"><input type="checkbox" id="note-public" ${note.isPublic?'checked':''}> 방문객에게 공개하기</label><p class="muted">체크하지 않으면 우리집 멤버만 읽을 수 있어요.<br>저장을 눌러야 내용과 공개 설정이 반영돼요.</p><button class="button primary" type="submit">저장하기</button></form>`:`<p class="note-full-body"></p><p class="muted">${note.isPublic?'방문객에게 공개된 쪽지':'우리집 멤버만 읽는 쪽지'}</p>`}
   <p class="form-error" data-note-error role="alert"></p><button class="button secondary" data-reload>최신 내용 불러오기</button> <button class="button secondary" data-close>${editing?'취소 / 닫기':'닫기'}</button>`;
   if(editing)box.querySelector('textarea').value=note.body;
   else box.querySelector('.note-full-body').textContent=note.body||'아직 적힌 내용이 없어요.';
   dirty=false;
  };
  box.addEventListener('input',()=>{dirty=true;});
  box.addEventListener('click',event=>{
   if(event.target.closest('[data-close]')&&!saving&&(!dirty||confirm('저장하지 않은 내용을 버릴까요?')))close();
   if(event.target.closest('[data-reload]')&&!saving&&(!dirty||confirm('작성 중인 내용을 버리고 최신 내용을 불러올까요?')))load().catch(showError);
  });
  function showError(error) {
   if(disposed||dialog!==box)return;
   const target=box.querySelector('[data-note-error]');
   if(target)target.textContent=error.message;
   else {box.innerHTML='<p data-note-error role="alert"></p><button class="button secondary" data-close>닫기</button>';box.querySelector('p').textContent=error.message;}
  }
  box.addEventListener('submit',async event=>{
   if(event.target.id!=='note-form')return;event.preventDefault();if(saving||!note)return;
   saving=true;onBusy(true);box.querySelectorAll('button,input,textarea').forEach(el=>el.disabled=true);
   try {
    note=await request('/'+id,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({body:box.querySelector('textarea').value,isPublic:box.querySelector('#note-public').checked,revision:note.revision})});
    dirty=false;await onSaved();await refresh();saving=false;close();
   } catch(error){showError(error);}
   finally {saving=false;onBusy(false);box.querySelectorAll('button,input,textarea').forEach(el=>el.disabled=false);}
  });
  await load().catch(showError);
 }
 root.addEventListener('click',event=>{const id=event.target.closest('[data-note-id]')?.dataset.noteId;if(id)open(id);},{signal});
 return {refresh,open,canRead:id=>previews.some(n=>n.id===id),get busy(){return saving;},get opened(){return !!dialog;},destroy(){disposed=true;abort.abort();dialog?.remove();dialog=null;root.replaceChildren();}};
}
