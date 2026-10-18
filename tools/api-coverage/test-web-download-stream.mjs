import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source=fs.readFileSync(new URL('../../app/src/main/assets/runtime-web/download-stream.js',import.meta.url),'utf8');
const payload=Uint8Array.from({length:2*1024*1024+137},(_,i)=>(i*19+137)%256);
let cancelled=0,reads=0;
const context=vm.createContext({btoa:s=>Buffer.from(s,'binary').toString('base64'),fetch:async()=>({ok:true,headers:new Headers({'Content-Type':'application/octet-stream','Content-Length':String(payload.length)}),body:{getReader(){let done=false;return {
  async read(){reads++;await new Promise(r=>setTimeout(r,1));if(done)return{done:true};done=true;return{value:payload,done:false};},
  async cancel(){cancelled++;}
};}}})});
const call=(action,key='fixture',url='blob:https://fixture.invalid/file')=>vm.runInContext(`(${source})(${JSON.stringify(action)},${JSON.stringify(key)},${JSON.stringify(url)})`,context);
assert.equal(call('start'),true);
const chunks=[];
for(let n=0;n<1000;n++){
 const state=call('pull');assert(!state.error,state.error);
 if(state.base64!==undefined){const bytes=Buffer.from(state.base64,'base64');assert(bytes.length<=32768);chunks.push(bytes);}
 if(state.done)break;
 if(state.pending)await new Promise(r=>setTimeout(r,1));
}
assert.deepEqual(Buffer.concat(chunks),Buffer.from(payload));assert.equal(reads,2,'Reading is demand-driven, including one EOF');
call('cancel');assert.equal(cancelled,1);assert.match(call('pull').error,/page changed/);
call('start','cancel-before-ready');call('cancel','cancel-before-ready');await new Promise(r=>setTimeout(r,5));assert.equal(cancelled,2);
context.fetch=async()=>{throw Error('revoked blob');};call('start','revoked');await new Promise(r=>setTimeout(r,1));assert.match(call('pull','revoked').error,/revoked blob/);call('cancel','revoked');
context.fetch=async()=>({ok:true,headers:new Headers(),body:null});call('start','old-webview');await new Promise(r=>setTimeout(r,1));assert.match(call('pull','old-webview').error,/streamed page downloads/);call('cancel','old-webview');
console.log('Page stream: binary integrity, 32 KiB backpressure, cancellation, revoked URL and unsupported-provider errors passed');
