import { roomSvg } from './room.js';
import { dimensions, iso } from './placement.js';

function loadImage(url) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('가구 이미지를 불러오지 못했어요. 다시 연결해 주세요.'));
    image.src = url;
  });
}

export async function createScene(canvas, house, catalog, editing = false) {
  const types = new Map(catalog.items.map(type => [type.id, type]));
  const [atlas, room] = await Promise.all([
    loadImage(catalog.atlasUrl),
    loadImage('data:image/svg+xml;charset=utf-8,' + encodeURIComponent(roomSvg(editing).replace('<svg ', '<svg xmlns="http://www.w3.org/2000/svg" ')))
  ]);
  const source = document.createElement('canvas');
  source.width = atlas.width; source.height = atlas.height;
  const sourceContext = source.getContext('2d', { willReadFrequently: true });
  sourceContext.drawImage(atlas, 0, 0);
  const cellW = atlas.width / catalog.columns, cellH = atlas.height / catalog.rows;
  const sprites = new Map();
  for (const type of catalog.items) {
    const sx = Math.round(type.column * cellW), sy = Math.round(type.row * cellH);
    const width = Math.floor(cellW), height = Math.floor(cellH);
    const pixels = sourceContext.getImageData(sx, sy, width, height).data;
    let left = width, top = height, right = 0, bottom = 0;
    for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
      if (pixels[(y * width + x) * 4 + 3] > 35) {
        left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
      }
    }
    if (left > right) throw new Error('가구 이미지의 영역을 확인할 수 없어요.');
    sprites.set(type.id, { sx: sx + left, sy: sy + top, sw: right - left + 1, sh: bottom - top + 1 });
  }
  const context = canvas.getContext('2d');
  let state = house, selected = null, preview = null, zoom = 1, transform = { scale: 1, x: 0, y: 0 }, hits = [], disposed = false;

  function anchor(item) {
    const type = types.get(item.typeId);
    if (type.kind === 'attachment') {
      const parent = state.furniture.find(p => p.id === item.parentId);
      if (!parent) return {x:0,y:0};
      const base = anchor(parent);
      if (type.id === 'frame') return { x: base.x + (item.slot === 0 ? -16 : 16), y: base.y - 50 };
      return { x: base.x + (item.slot === 1 ? -9 : item.slot === 2 ? 9 : 0), y: base.y - (item.slot === 0 ? 65 : 42) };
    }
    if (type.kind === 'wall') return item.wall === 'left' ? iso(0, item.y + .5) : iso(item.x + .5, 0);
    const [w, h] = dimensions(type, item.rotation);
    return iso(item.x + w / 2, item.y + h / 2);
  }
  function bounds(item) {
    const type = types.get(item.typeId), sprite = sprites.get(type.id), base = anchor(item);
    const scale = Math.min(type.drawWidth / sprite.sw, type.drawHeight / sprite.sh);
    const width = sprite.sw * scale, height = sprite.sh * scale;
    return {x: base.x - width / 2, y: base.y - height + (type.kind === 'rug' ? height / 2 : 7), width, height};
  }
  function drawItem(item, alpha = 1, ghost = false) {
    const sprite = sprites.get(item.typeId), rect = bounds(item);
    const flip = item.rotation % 180 === 90;
    context.save();
    context.globalAlpha = alpha;
    if (!ghost && selected === item.id) { context.shadowColor = '#dc4c98'; context.shadowBlur = 8; }
    if (flip) { context.translate(rect.x + rect.width, rect.y); context.scale(-1, 1); }
    context.drawImage(atlas, sprite.sx, sprite.sy, sprite.sw, sprite.sh, flip ? 0 : rect.x, flip ? 0 : rect.y, rect.width, rect.height);
    context.restore();
    if (!ghost) hits.push({item, rect});
  }
  function polygon(points, color, stroke) {
    context.beginPath(); points.forEach((p, index) => index ? context.lineTo(p.x,p.y) : context.moveTo(p.x,p.y)); context.closePath();
    context.fillStyle = color; context.fill(); context.strokeStyle = stroke; context.lineWidth = 1.2; context.stroke();
  }
  function footprint(item, valid) {
    const type = types.get(item.typeId), [w,h] = dimensions(type,item.rotation);
    if (type.kind === 'attachment') {
      const parent = state.furniture.find(p => p.id === item.parentId);
      if (parent) footprint(parent,valid);
    } else if (type.kind === 'wall') {
      const a = item.wall === 'left' ? iso(0,item.y) : iso(item.x,0);
      const b = item.wall === 'left' ? iso(0,item.y+1) : iso(item.x+1,0);
      polygon([a,b,{x:b.x,y:b.y-142},{x:a.x,y:a.y-142}], valid ? '#f67ca955' : '#6e587b66', valid ? '#f04890' : '#6e587b');
    } else {
      for (let y=0;y<h;y++) for(let x=0;x<w;x++) polygon([
        iso(item.x+x,item.y+y),iso(item.x+x+1,item.y+y),iso(item.x+x+1,item.y+y+1),iso(item.x+x,item.y+y+1)
      ],valid ? '#f85b8c88' : '#65577977',valid ? '#d32663' : '#594d6d');
    }
  }
  function draw() {
    if (disposed) return;
    const rect = canvas.getBoundingClientRect(), dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(rect.width*dpr); canvas.height = Math.round(rect.height*dpr);
    if (!rect.width || !rect.height) return;
    const scale = Math.min(rect.width/650, rect.height/550)*zoom;
    transform = {scale, x:(rect.width-640*scale)/2, y:(rect.height-540*scale)/2};
    context.setTransform(dpr,0,0,dpr,0,0); context.clearRect(0,0,rect.width,rect.height);
    context.translate(transform.x,transform.y); context.scale(scale,scale);
    context.drawImage(room,0,0,640,540); hits = [];
    const order = item => {
      const type=types.get(item.typeId);
      if (type.kind==='wall') return -200;
      if (type.kind==='rug') return -100;
      return item.x+item.y+dimensions(type,item.rotation).reduce((a,b)=>a+b,0)/2;
    };
    const parents = state.furniture.filter(item => !item.parentId).sort((a,b)=>order(a)-order(b));
    for (const item of parents.filter(item => types.get(item.typeId).kind === 'rug')) drawItem(item,preview?.item.id===item.id ? .3 : 1);
    if (preview) footprint(preview.item,preview.valid);
    for (const item of parents) {
      if (types.get(item.typeId).kind === 'rug') continue;
      drawItem(item,preview?.item.id===item.id ? .3 : 1);
      for(const child of state.furniture.filter(child=>child.parentId===item.id).sort((a,b)=>a.slot-b.slot)) drawItem(child);
    }
    if (preview && (types.get(preview.item.typeId).kind !== 'attachment' || preview.item.parentId)) {
      drawItem(preview.item,preview.valid ? .7 : .35,true);
    }
  }
  const observer = new ResizeObserver(draw); observer.observe(canvas);
  draw();
  return {
    types,
    update(house) { state=house; draw(); },
    select(id) { selected=id; draw(); },
    preview(value) { preview=value; draw(); },
    zoom(value) { zoom=value; draw(); },
    point(event) { const rect=canvas.getBoundingClientRect(); return {x:(event.clientX-rect.left-transform.x)/transform.scale,y:(event.clientY-rect.top-transform.y)/transform.scale}; },
    hit(point, predicate = () => true) {
      for (const hit of [...hits].reverse()) {
        const {rect,item}=hit;
        if (predicate(item) && point.x>=rect.x && point.x<rect.x+rect.width && point.y>=rect.y && point.y<rect.y+rect.height) {
          const sprite=sprites.get(item.typeId);
          let u=(point.x-rect.x)/rect.width;
          if(item.rotation%180===90)u=1-u;
          const sx=sprite.sx+Math.min(sprite.sw-1,Math.floor(u*sprite.sw));
          const sy=sprite.sy+Math.min(sprite.sh-1,Math.floor((point.y-rect.y)/rect.height*sprite.sh));
          if(sourceContext.getImageData(sx,sy,1,1).data[3]>35)return item;
        }
      }
      return null;
    },
    thumbnail(type, target) {
      const sprite=sprites.get(type.id), ctx=target.getContext('2d');
      const scale=Math.min((target.width-12)/sprite.sw,(target.height-12)/sprite.sh);
      ctx.drawImage(atlas,sprite.sx,sprite.sy,sprite.sw,sprite.sh,(target.width-sprite.sw*scale)/2,(target.height-sprite.sh*scale)/2,sprite.sw*scale,sprite.sh*scale);
    },
    destroy() { disposed=true; observer.disconnect(); }
  };
}
