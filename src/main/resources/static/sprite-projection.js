import { iso, dimensions } from './placement.js';

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
    // Reserve two wall cells, but center the door in the inner 1.4 cells.
    const a=left?iso(0,item.y+1.7):iso(item.x+.3,0);
    const b=left?iso(0,item.y+.3):iso(item.x+1.7,0);
    return affine([[0,sprite.sh],[sprite.sw,sprite.sh],[0,0]], [a,b,{x:a.x,y:a.y-145}]);
  }
  const points=contacts[type.id]||contacts.chair;
  const w=type.width,h=type.height;
  const targets=item.rotation%180===90
    ? [iso(item.x+h,item.y),iso(item.x+h,item.y+w),iso(item.x,item.y+w)]
    : [iso(item.x,item.y+h),iso(item.x+w,item.y+h),iso(item.x+w,item.y)];
  return affine(points.map(([x,y])=>[x*sprite.sw,y*sprite.sh]), targets);
}

export function furniturePieces(item,type,sprite) {
  const rectangle=[[0,0],[sprite.sw,0],[sprite.sw,sprite.sh],[0,sprite.sh]];
  if(type.id.startsWith('plant-')) {
    // Restore the original unwarped plant artwork and its original catalog size.
    const [w,h]=dimensions(type,item.rotation),base=iso(item.x+w/2,item.y+h/2);
    const scale=Math.min(type.drawWidth/sprite.sw,type.drawHeight/sprite.sh);
    const flipped=item.rotation%180===90;
    return [{matrix:new DOMMatrix([flipped?-scale:scale,0,0,scale,
      base.x+(flipped?1:-1)*sprite.sw*scale/2,base.y-sprite.sh*scale+7]),clip:rectangle}];
  }
  if(type.id==='sofa'||type.kind==='wall')return [{matrix:furnitureMatrix(item,type,sprite),clip:rectangle}];
  const w=type.width,h=type.height,turned=item.rotation%180===90;
  const targets=turned
    ? [iso(item.x+h,item.y),iso(item.x+h,item.y+w),iso(item.x,item.y+w)]
    : [iso(item.x,item.y+h),iso(item.x+w,item.y+h),iso(item.x+w,item.y)];
  if(type.kind==='rug') {
    // Four actual rug corners -> four exact floor corners, with no upright component.
    const source=[[.03,.55],[.55,.96],[.97,.45],[.44,.03]].map(([x,y])=>[x*sprite.sw,y*sprite.sh]);
    const target=[...targets,iso(item.x,item.y)];
    return [[0,1,2],[0,2,3]].map(indices=>({
      matrix:affine(indices.map(i=>source[i]),indices.map(i=>target[i])),clip:indices.map(i=>source[i])
    }));
  }
  const points=(contacts[type.id]||contacts.chair).map(([x,y])=>[x*sprite.sw,y*sprite.sh]);
  // Two upright faces: c=0 guarantees every vertical line remains vertical.
  // Only the ground-line slope changes. Never tilt the entire object as a flat plane.
  const verticalScale=Math.abs((targets[2].x-targets[0].x)/(points[2][0]-points[0][0]));
  return [0,1].map(index=>{
    const p=points[index],q=points[index+1],a=targets[index],b=targets[index+1];
    const xScale=(b.x-a.x)/(q[0]-p[0]);
    const shear=((b.y-a.y)-verticalScale*(q[1]-p[1]))/(q[0]-p[0]);
    const left=index===0?0:points[1][0],right=index===0?points[1][0]:sprite.sw;
    return {matrix:new DOMMatrix([xScale,shear,0,verticalScale,
      a.x-xScale*p[0],a.y-shear*p[0]-verticalScale*p[1]]),
      clip:[[left,0],[right,0],[right,sprite.sh],[left,sprite.sh]]};
  });
}

export function containsPoint(polygon, point) {
  let inside=false;
  for(let i=0,j=polygon.length-1;i<polygon.length;j=i++) {
    const [x,y]=polygon[i],[px,py]=polygon[j];
    if((y>point.y)!==(py>point.y)&&point.x<(px-x)*(point.y-y)/(py-y)+x)inside=!inside;
  }
  return inside;
}
