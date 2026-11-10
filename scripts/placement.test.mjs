import test from 'node:test';
import assert from 'node:assert/strict';
import { validatePlacement, floorCell, iso } from '../src/main/resources/static/placement.js';
const types=new Map([
  ['chair',{id:'chair',kind:'floor',width:1,height:1}],['sofa',{id:'sofa',kind:'floor',width:2,height:1}],
  ['rug',{id:'rug',kind:'rug',width:3,height:2}],['door',{id:'door',kind:'wall',width:1,height:1}],
  ['frame',{id:'frame',kind:'attachment',width:1,height:1}],['desk',{id:'desk',kind:'floor',width:2,height:1}]
]);
test('isometric grid inversion respects each cell center',()=>{
  for(let x=0;x<8;x++)for(let y=0;y<8;y++)assert.deepEqual(floorCell(iso(x+.5,y+.5)),{x,y});
});
test('preview detects overlap, rotated footprint, boundary and rug layers',()=>{
  const house={furniture:[{id:'s',typeId:'sofa',x:2,y:2,rotation:true}]};
  const valid=item=>validatePlacement(item,house,types).valid;
  assert.equal(valid({typeId:'chair',x:2,y:3,rotation:false}),false);
  assert.equal(valid({typeId:'chair',x:3,y:2,rotation:false}),true);
  assert.equal(valid({typeId:'sofa',x:7,y:7,rotation:false}),false);
  assert.equal(valid({typeId:'rug',x:2,y:2,rotation:false}),true);
  assert.equal(valid({id:'s',typeId:'sofa',x:2,y:2,rotation:false}),true);
});
test('doors and frame attachment limits',()=>{
  assert.equal(validatePlacement({typeId:'door',x:2,y:2,rotation:false},{furniture:[]},types).valid,false);
  assert.equal(validatePlacement({typeId:'door',x:2,y:0,wall:'right',rotation:false},{furniture:[]},types).valid,true);
  const house={furniture:[{id:'d',typeId:'desk'}, {id:'f1',typeId:'frame',parentId:'d'}, {id:'f2',typeId:'frame',parentId:'d'}]};
  assert.equal(validatePlacement({typeId:'frame',parentId:'d'},house,types).valid,false);
  assert.equal(validatePlacement({id:'f1',typeId:'frame',parentId:'d'},house,types).valid,true);
});
