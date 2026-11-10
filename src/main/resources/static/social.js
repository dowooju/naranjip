const escape=value=>String(value??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const date=value=>new Date(value).toLocaleString('ko-KR');
export function rankingMarkup(data,type) {
 return `<div class="theme-announcement">${data.theme?`<strong>이번 주 · ${escape(data.theme.title)}</strong><p>${escape(data.theme.description)}</p>`:'<p>이번 주 콘셉트를 준비 중이에요.</p>'}</div><div class="rank-list">${data.houses.length?data.houses.map(({house,rank})=>`<a class="rank-card" href="#visit/${house.houseId}"><span class="rank-number">${rank??'♡'}</span><span><strong>${escape(house.residents)}</strong><small>${type==='theme'?'콘셉트 ':''}좋아요 ${house.likes} · 코멘트 ${house.comments}</small></span><span>↗</span></a>`).join(''):'<p class="muted">아직 순위에 올라온 집이 없어요.</p>'}</div><button class="button secondary full" data-random>이웃집 구경하기 ↗</button>`;
}
export function socialMarkup() {return '<section id="social-panel"><p class="muted">좋아요와 코멘트를 불러오는 중…</p></section>';}
export async function mountSocial(root,houseId,me,api,login) {
 const panel=root.querySelector('#social-panel');let stats,comments,page=0,busy=false;
 async function refresh() {
  const draft=panel.querySelector('textarea')?.value||'';
  [stats,comments]=await Promise.all([api('/houses/'+houseId+'/social'),api('/houses/'+houseId+'/guestbook?page='+page)]);
  if(!panel.isConnected)return;
  panel.innerHTML=`<div class="theme-announcement">${stats.theme?`<strong>이번 주 · ${escape(stats.theme.title)}</strong><p>${escape(stats.theme.description)}</p>`:'<p>등록된 콘셉트가 없어요.</p>'}</div>
  <div class="reaction-buttons"><button class="reaction" data-like="general" aria-pressed="${stats.generalLiked}" ${stats.owner?'disabled':''}>♡ 좋아요 <strong>${stats.generalLikes}</strong></button><button class="reaction" data-like="theme" aria-pressed="${stats.themeLiked}" ${stats.owner||!stats.theme?'disabled':''}>✧ 콘셉트 좋아요 <strong>${stats.themeLikes}</strong></button></div>
  ${stats.owner?'<p class="muted">우리집에는 좋아요를 누를 수 없어요.</p>':''}
  <h3>☏ 코멘트 <span>${stats.comments}</span></h3>${me.authenticated?'<form id="comment-form"><label for="comment-body">우리집에 남길 한마디</label><textarea id="comment-body" name="body" maxlength="200" required placeholder="따뜻한 한마디를 남겨 주세요 (200자)"></textarea><button class="button primary" type="submit">코멘트 남기기</button></form>':'<button class="button secondary" data-login>로그인하고 코멘트 남기기</button>'}
  <ol class="comment-list">${comments.items.map(({comment:c,canDelete})=>`<li><div>${c.authorHouseId?`<a href="#visit/${c.authorHouseId}">${escape(c.authorName)} ↗</a>`:`<strong>${escape(c.authorName)}</strong>`}<time>${date(c.createdAt)}</time></div><p>${escape(c.body)}</p>${canDelete?`<button class="text-button" data-delete-comment="${c.id}">삭제</button>`:''}</li>`).join('')||'<li class="muted">첫 코멘트를 남겨 주세요.</li>'}</ol>
  <div class="comment-pages"><button class="button secondary" data-page="-1" ${page===0?'disabled':''}>이전</button><span>${page+1}쪽</span><button class="button secondary" data-page="1" ${comments.hasMore?'':'disabled'}>다음</button></div><p id="social-error" class="form-error" role="alert"></p>`;
  if(panel.querySelector('textarea'))panel.querySelector('textarea').value=draft;
 }
 async function run(action) {
  if(busy)return;busy=true;panel.querySelectorAll('button').forEach(b=>b.disabled=true);
  let failure='';
  try {await action();}catch(e){failure=e.message;}
  try {await refresh();}catch {failure=failure||'연결을 확인하고 집을 다시 불러와 주세요.';}
  if(panel.isConnected)panel.querySelector('#social-error').textContent=failure;
  busy=false;
 }
 panel.addEventListener('click',event=>{
  const button=event.target.closest('button');if(!button||busy)return;
  if(button.hasAttribute('data-login'))login();
  if(button.dataset.like) {
   if(!me.authenticated){login();return;}
   const kind=button.dataset.like,liked=kind==='general'?stats.generalLiked:stats.themeLiked;
   run(()=>api('/houses/'+houseId+'/likes/'+kind,{themeId:stats.theme?.id??null},liked?'DELETE':'PUT'));
  }
  if(button.dataset.deleteComment&&confirm('이 코멘트를 삭제할까요?'))run(()=>api('/guestbook/'+button.dataset.deleteComment,{},'DELETE'));
  if(button.dataset.page)run(async()=>{page+=Number(button.dataset.page);});
 });
 panel.addEventListener('submit',event=>{
  if(event.target.id!=='comment-form')return;event.preventDefault();
  const textarea=panel.querySelector('textarea'),body=textarea.value.trim();
  if(!body){panel.querySelector('#social-error').textContent='코멘트를 입력해 주세요.';return;}
  run(async()=>{await api('/houses/'+houseId+'/guestbook',{body});textarea.value='';page=0;});
 });
 await refresh();
}
export function adminMarkup(data) {
 return `<section class="house-top"><div><a href="#home" class="back-link">← 마을로 돌아가기</a><h1>이번 주 콘셉트 관리</h1><p>함께 꾸밀 주제와 안내문을 적어 주세요.</p></div></section><section class="panel admin-panel">
 <h2>현재 콘셉트</h2><div class="theme-announcement">${data.current?`<strong>${escape(data.current.title)}</strong><p>${escape(data.current.description)}</p><small>${date(data.current.activatedAt)} 공개</small>`:'아직 등록된 콘셉트가 없어요.'}</div>
 <form id="theme-form"><label for="theme-title">새 콘셉트 제목 (80자 이내)</label><input id="theme-title" name="title" maxlength="80" required placeholder="예: 비 오는 날의 집"><label for="theme-description">컨셉 코멘트 · 꾸미기 안내 (1,000자 이내)</label><textarea id="theme-description" name="description" maxlength="1000" placeholder="예: 빗소리를 들으며 쉬고 싶은 방을 꾸며 주세요."></textarea><p>공개하면 바로 적용돼요. 이전 콘셉트의 좋아요는 기록으로 남고, 새 콘셉트 좋아요는 0부터 시작해요. 일반 좋아요와 코멘트는 유지돼요.</p><button type="submit" class="button primary">이번 주 콘셉트 공개하기</button><p id="admin-error" class="form-error" role="alert"></p></form>
 <h2>지난 콘셉트</h2><ol class="theme-history">${data.history.map(t=>`<li><strong>${escape(t.title)}</strong><time>${date(t.activatedAt)}</time><p>${escape(t.description)}</p></li>`).join('')||'<li>아직 등록 이력이 없어요.</li>'}</ol></section>`;
}
export function mountAdmin(root,data,api,refresh,toast) {
 const form=root.querySelector('#theme-form');let busy=false;
 form.addEventListener('submit',async event=>{
  event.preventDefault();if(busy)return;
  const title=form.elements.title.value.trim(),description=form.elements.description.value.trim();
  if(!title){form.querySelector('#admin-error').textContent='콘셉트 제목을 입력해 주세요.';return;}
  if(!confirm('새 콘셉트를 지금 공개할까요? 콘셉트 좋아요는 새로 집계됩니다.'))return;
  busy=true;form.querySelector('button').disabled=true;form.querySelector('#admin-error').textContent='';
  try {await api('/admin/themes',{title,description,expectedThemeId:data.current?.id??null});await refresh();toast('이번 주 콘셉트를 공개했어요.');}
  catch(e){form.querySelector('#admin-error').textContent=e.message;form.querySelector('button').disabled=false;}
  finally{busy=false;}
 });
}
