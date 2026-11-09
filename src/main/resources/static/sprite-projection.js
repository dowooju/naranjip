import { iso } from './placement.js';

// Normalized contact points in the raster: left, front, right.
// Map these three points to tile vertices instead of fitting an arbitrary image box.
const contacts = {
  chair: [[.06,.77],[.58,.99],[.94,.71]],
  desk: [[.07,.77],[.80,.98],[.98,.63]],
  sofa: [[.05,.72],[.66,.98],[.96,.72]],
  dining: [[.05,.75],[.65,.99],[.97,.69]],
  fridge: [[.06,.88],[.59,.99],[.96,.77]],
  'plant-small': [[.12,.85],[.50,.99],[.88,.85]],
  'plant-tall': [[.20,.88],[.50,.99],[.80,.88]],
  rug: [[.03,.55],[.55,.96],[.97,.45]],
};

export function affine(source, target) {
  const [p,q,r]=source, [a,b,c]=target;
  const ux=q[0]-p[0], uy=q[1]-p[1], vx=r[0]-p[0], vy=r[1]-p[1];
  const det=ux*vy-uy*vx;
  const xx=((b.x-a.x)*vy-(c.x-a.x)*uy)/det;
  const xy=((c.x-a.x)*ux-(b.x-a.x)*vx)/det;
  const yx=((b.y-a.y)*vy-(c.y-a.y)*uy)/det;
  const yy=((c.y-a.y)*ux-(b.y-a.y)*vx)/det;
  return new DOMMatrix([xx,yx,xy,yy,a.x-xx*p[0]-xy*p[1],a.y-yx*p[0]-yy*p[1]]);
}

export function furnitureMatrix(item,type,sprite) {
  if(type.kind==='wall') {
    const left=item.wall==='left';
    const a=left?iso(0,item.y+1):iso(item.x,0);
    const b=left?iso(0,item.y):iso(item.x+1,0);
    return affine([[0,sprite.sh],[sprite.sw,sprite.sh],[0,0]], [a,b,{x:a.x,y:a.y-145}]);
  }
  const points=contacts[type.id]||contacts.chair;
  const w=type.width,h=type.height;
  const targets=item.rotation%180===90
    ? [iso(item.x+h,item.y),iso(item.x+h,item.y+w),iso(item.x,item.y+w)]
    : [iso(item.x,item.y+h),iso(item.x+w,item.y+h),iso(item.x+w,item.y)];
  return affine(points.map(([x,y])=>[x*sprite.sw,y*sprite.sh]), targets);
}
