// Shared, pure client preview rules. The server independently validates every mutation.
export const dimensions = (type, rotation = false) => !rotation
  ? [type.width, type.height] : [type.height, type.width];

export function validatePlacement(candidate, house, types) {
  const type = types.get(candidate.typeId);
  if (!type) return { valid: false, reason: '가구를 선택해 주세요.' };
  if (type.kind === 'attachment') {
    const parent = house.furniture.find(item => item.id === candidate.parentId);
    const expected = type.id === 'frame' ? 'desk' : 'fridge';
    if (!parent || parent.typeId !== expected) return { valid: false, reason: type.id === 'frame' ? '책상 위로 끌어 주세요.' : '냉장고 위로 끌어 주세요.' };
    const limit = type.id === 'frame' ? 2 : 3;
    const count = house.furniture.filter(item => item.parentId === parent.id && item.id !== candidate.id).length;
    return { valid: count < limit, reason: count < limit ? '여기에 붙일 수 있어요.' : '붙일 자리가 없어요.' };
  }
  const [w, h] = dimensions(type, candidate.rotation);
  const { x, y } = candidate;
  if (x < 0 || y < 0 || x + w > 8 || y + h > 8) return { valid: false, reason: '방 안으로 끌어 주세요.' };
  if (type.kind === 'wall' && !((candidate.wall === 'left' && x === 0) || (candidate.wall === 'right' && y === 0))) {
    return { valid: false, reason: '문은 벽에만 붙일 수 있어요.' };
  }
  const collision = house.furniture.some(item => {
    if (item.id === candidate.id) return false;
    const other = types.get(item.typeId);
    if (other.kind === 'attachment') return false;
    if (type.kind === 'wall' && other.kind === 'wall') {
      const start=candidate.wall==='left'?y:x,otherStart=candidate.wall==='left'?item.y:item.x;
      return item.wall===candidate.wall&&start<otherStart+other.width&&start+type.width>otherStart;
    }
    if (type.kind === 'wall' || other.kind === 'wall') return false;
    if ((type.kind === 'rug') !== (other.kind === 'rug')) return false;
    const [ow, oh] = dimensions(other, item.rotation);
    return x < item.x + ow && x + w > item.x && y < item.y + oh && y + h > item.y;
  });
  return { valid: !collision, reason: collision ? '다른 가구와 겹쳐요.' : '놓으면 자동으로 저장돼요.' };
}

export const iso = (x, y) => ({ x: 320 + (x - y) * 32, y: 230 + (x + y) * 16 });
export function floorCell(point) {
  const dx = (point.x - 320) / 32, dy = (point.y - 230) / 16;
  return { x: Math.floor((dx + dy) / 2), y: Math.floor((dy - dx) / 2) };
}

export function automaticPlacement(typeId, house, types) {
  const type=types.get(typeId);
  if(!type)return null;
  if(type.kind==='attachment') {
    const parentType=typeId==='frame'?'desk':'fridge';
    const parents=house.furniture.filter(item=>item.typeId===parentType).sort((a,b)=>{
      const [aw,ah]=dimensions(types.get(a.typeId),a.rotation),[bw,bh]=dimensions(types.get(b.typeId),b.rotation);
      return (a.x-a.y+(aw-ah)/2)-(b.x-b.y+(bw-bh)/2)||a.y-b.y||a.x-b.x;
    });
    for(const parent of parents) {
      const used=house.furniture.filter(item=>item.parentId===parent.id).map(item=>item.slot);
      let slot=0;while(used.includes(slot))slot++;
      const candidate={typeId,x:0,y:0,rotation:false,parentId:parent.id,slot};
      if(validatePlacement(candidate,house,types).valid)return candidate;
    }
    return null;
  }
  const candidates=[];
  for(let y=0;y<8;y++)for(let x=0;x<8;x++) {
    if(type.kind==='wall') {
      if(x===0)candidates.push({typeId,x,y,rotation:true,wall:'left'});
      if(y===0)candidates.push({typeId,x,y,rotation:false,wall:'right'});
    } else candidates.push({typeId,x,y,rotation:false});
  }
  const distance=item=>{
    const [w,h]=dimensions(type,item.rotation);
    return (item.x+w/2-4)**2+(item.y+h/2-4)**2;
  };
  return candidates.sort((a,b)=>distance(a)-distance(b)||a.y-b.y||a.x-b.x).find(item=>validatePlacement(item,house,types).valid)||null;
}
