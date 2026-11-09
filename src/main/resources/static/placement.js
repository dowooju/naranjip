// Shared, pure client preview rules. The server independently validates every mutation.
export const dimensions = (type, rotation = 0) => rotation % 180 === 0
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
    if (type.kind === 'wall' && other.kind === 'wall') return item.wall === candidate.wall && item.x === x && item.y === y;
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
