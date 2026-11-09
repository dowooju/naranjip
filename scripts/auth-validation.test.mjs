import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validateAuth} from '../src/main/resources/static/auth-validation.js';
test('login shows both missing fields and does not require signup email',()=>{
  assert.deepEqual(Object.keys(validateAuth({loginId:' ',password:'',email:''},false)),['loginId','password']);
  assert.deepEqual(validateAuth({loginId:'friend',password:'anything',email:''},false),{});
});
test('registration requires email, valid identifier and safe password length',()=>{
  const valid={loginId:'friend_123',password:'secret123',email:'friend@example.com'};
  assert.deepEqual(validateAuth(valid,true),{});
  assert.ok(validateAuth({...valid,email:''},true).email);
  assert.ok(validateAuth({...valid,email:'broken'},true).email);
  assert.ok(validateAuth({...valid,password:'short'},true).password);
  assert.ok(validateAuth({...valid,password:'한'.repeat(25)},true).password);
  assert.ok(validateAuth({...valid,loginId:'a!'},true).loginId);
});
