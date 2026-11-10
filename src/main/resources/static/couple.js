const escape=value=>String(value).replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const date=value=>new Date(value).toLocaleDateString('ko-KR');
export function coupleMarkup(data) {
 const profile=(person,label)=>`<section class="panel couple-card"><p class="eyebrow">${label}</p><span class="avatar">${escape(person.displayName.slice(0,1))}</span><h2>${escape(person.displayName)}</h2><p class="muted">가입일 · ${date(person.joinedAt)}</p></section>`;
 return `<section class="house-top"><div><a class="back-link" href="#home">← 마을로 돌아가기</a><h1>우리 둘의 연결 ♡</h1><p>초대 코드로 같은 집에서 만나요.</p></div>${data.houseId?'<a class="button secondary" href="#house">우리집 가기</a>':''}</section>
 <div class="couple-profiles">${profile(data.self,'MY PROFILE')}${data.partner?profile(data.partner,'MY PARTNER'):'<section class="panel couple-card"><span class="avatar">♡</span><h2>함께할 사람을 기다려요</h2><p class="muted">초대 코드를 전해 같은 집에서 만나요.</p></section>'}</div>
 <section class="panel couple-manage">
 ${data.partner?`<h2>${escape(data.self.displayName)} ♡ ${escape(data.partner.displayName)}</h2><p>함께 살기 시작한 날 · ${date(data.connectedAt)}</p><p>같은 집을 보고 각자 꾸밀 수 있어요.<br>상대의 변경은 집을 다시 불러오면 보여요.</p><a class="button primary" href="#house">우리의 집으로 ↗</a>`:`
 ${data.houseId?`<h2>내 집으로 초대하기</h2><p class="muted">7자리 코드 · 발급 후 24시간 · 한 번만 사용 가능<br>재발급하면 이전 코드는 사용할 수 없어요.</p>
 ${data.invite?`<label for="invite-code">내 초대 코드</label><div class="couple-input"><input id="invite-code" readonly value="${escape(data.invite.code)}"><button class="button primary" data-copy>코드 복사</button></div><p>만료 · ${new Date(data.invite.expiresAt).toLocaleString('ko-KR')}</p><button class="button secondary" data-revoke>초대 취소</button>`:''}
 <button class="button secondary" data-issue>${data.invite?'새 코드 발급':'초대 코드 발급'}</button>`:`<h2>우리의 첫 집</h2><p class="muted">새 집을 만들거나, 상대방의 초대 코드를 입력해 주세요.</p><button class="button primary" data-create-house>새 집 만들기</button>`}
 <h3>받은 초대 코드</h3><form id="couple-form" novalidate><label for="received-code">상대방의 코드 7자리</label><div class="couple-input"><input id="received-code" name="code" maxlength="7" autocomplete="off" placeholder="예: X8CQP91"><button class="button primary" type="submit">초대 확인</button></div></form>
 <div id="invite-preview" hidden></div>`}
 <p id="couple-error" class="form-error" role="alert"></p><button class="button secondary" data-couple-refresh>상태 새로고침 ↻</button></section>`;
}
export function mountCouple(root,api,refresh,toast,data) {
 const panel=root.querySelector('.couple-manage');let busy=false,preview=null,code='';
 async function run(action) {
  if(busy)return;busy=true;panel.querySelector('#couple-error').textContent='';
  panel.querySelectorAll('button').forEach(b=>b.disabled=true);
  try { await action(); } catch(e) { panel.querySelector('#couple-error').textContent=e.message; }
  finally { busy=false;panel.querySelectorAll('button').forEach(b=>b.disabled=false); }
 }
 panel.querySelector('#couple-form')?.addEventListener('submit',event=>{
  event.preventDefault();run(async()=>{
   preview=null;panel.querySelector('#invite-preview').hidden=true;
   code=panel.querySelector('#received-code').value.trim().toUpperCase();
   if(!/^[A-Z0-9]{7}$/.test(code))throw Error('영문·숫자 7자리 초대 코드를 입력해 주세요.');
   preview=await api('/invites/preview',{code});
   const target=panel.querySelector('#invite-preview');target.hidden=false;
   target.innerHTML=`<h3>${escape(preview.inviterName)}님의 초대</h3><p>${preview.deletesExistingHouse?'기존의 방과 배치된 가구가 삭제됩니다.':'이 집에서 함께 살게 돼요.'}</p><button class="button primary" data-accept>초대 수락</button> <button class="button secondary" data-cancel-preview>취소</button>`;
  });
 });
 panel.addEventListener('click',event=>{
  const button=event.target.closest('button');if(!button||busy)return;
  if(button.hasAttribute('data-copy'))run(async()=>{
   try { await navigator.clipboard.writeText(data.invite.code);toast('초대 코드를 복사했어요.'); }
   catch { panel.querySelector('#invite-code').select();throw Error('코드를 선택했어요. Ctrl+C로 복사해 주세요.'); }
  });
  if(button.hasAttribute('data-issue'))run(async()=>{await api('/houses/'+data.houseId+'/invite',{});await refresh();});
  if(button.hasAttribute('data-revoke'))run(async()=>{await api('/houses/'+data.houseId+'/invite',{},'DELETE');await refresh();});
  if(button.hasAttribute('data-create-house')) {
   if(confirm('나중에 다른 사람의 초대를 수락하면 지금 만든 집은 삭제됩니다. 새 집을 만들까요?'))
    run(async()=>{await api('/houses',{});await refresh();toast('새 집을 만들었어요.');});
  }
  if(button.hasAttribute('data-accept')&&preview) {
   if(!confirm(preview.deletesExistingHouse?'기존의 방은 삭제됩니다. 초대를 수락하시겠습니까?':'초대를 수락하고 함께 살까요?'))return;
   run(async()=>{await api('/invites/accept',{code,agreeDelete:preview.deletesExistingHouse,existingHouseId:preview.existingHouseId});await refresh();toast('이제 같은 집에서 함께해요!');});
  }
  if(button.hasAttribute('data-cancel-preview')) { preview=null;panel.querySelector('#invite-preview').hidden=true; }
  if(button.hasAttribute('data-couple-refresh'))run(refresh);
 });
}
