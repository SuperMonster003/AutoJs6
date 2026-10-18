import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {fileURLToPath} from 'node:url';
const source=readFileSync(fileURLToPath(new URL('../../app/src/main/assets/runtime-web/display-capture.js',import.meta.url)),'utf8');
class Track extends EventTarget {
  constructor(){super();this.readyState='live';this.frames=0;this.kind='video';}
  stop(){this.readyState='ended';} clone(){return new Track();} requestFrame(){this.frames++;}
}
class Stream {constructor(tracks){this.track=tracks ? tracks[0] : new Track();}getVideoTracks(){return [this.track];}}
function fixture(overrides={}) {
  let driver;
  const context={DOMException,Event,URL,Map,Promise,Number,Math,Object,TypeError,MediaStream:Stream,isSecureContext:true,location:{origin:'https://fixture.invalid'},
    navigator:{userActivation:{isActive:true},mediaDevices:{}},document:{hidden:false,createElement:()=>({width:0,height:0,captureStream:()=>{const stream=new Stream();driver=stream.track;return stream;},getContext:()=>({fillRect(){},drawImage(){}})})},
    Image:class {constructor(){this.width=400;this.height=300;}set src(_){queueMicrotask(()=>this.onload());}},...overrides};
  context.window=context;vm.createContext(context);
  const install=vm.runInContext('('+source+')',context);
  const result=install('bridge',{origin:'https://fixture.invalid',maxWidth:1280,maxHeight:720,frameRate:5});
  return {context,bridge:context.bridge,media:context.navigator.mediaDevices,result,get driver(){return driver;}};
}
const f=fixture();assert.equal(f.result.installed,true);
f.context.navigator.userActivation.isActive=false;await assert.rejects(f.media.getDisplayMedia(),e=>e.name==='InvalidStateError');
f.context.navigator.userActivation.isActive=true;f.context.document.hidden=true;await assert.rejects(f.media.getDisplayMedia(),e=>e.name==='InvalidStateError');f.context.document.hidden=false;
await assert.rejects(f.media.getDisplayMedia({video:false}),e=>e.name==='TypeError');
await assert.rejects(f.media.getDisplayMedia({video:{width:{min:32}}}),e=>e.name==='TypeError');
await assert.rejects(f.media.getDisplayMedia({video:{height:{exact:42}}}),e=>e.name==='TypeError');
await assert.rejects(f.media.getDisplayMedia({video:{frameRate:0}}),e=>e.name==='TypeError');
const pending=f.media.getDisplayMedia({video:{width:{max:320}},audio:true});const request=f.bridge.poll();
assert.equal(request.maxWidth,320);assert.equal(request.frameRate,5);
await assert.rejects(f.media.getDisplayMedia(),e=>e.name==='InvalidStateError');
assert.equal(f.bridge.accept(request.request,320,240),true);assert.equal(f.bridge.frame(request.request,'JPEG'),true);
assert.equal(f.bridge.frame(request.request,'DROP'),false,'Only one decoded image may be in flight');
const stream=await pending,track=stream.getVideoTracks()[0];assert.equal(f.driver.frames,1);
const clone=track.clone();track.stop();assert.equal(f.bridge.poll().frame,request.request,'A clone keeps its native source alive');
assert.equal(f.bridge.frame(request.request,'CLONE'),true);await new Promise(queueMicrotask);assert.equal(f.driver.frames,2);assert.equal(f.driver.readyState,'live');
clone.stop();assert.equal(f.bridge.poll().stop,request.request);
assert.equal(f.driver.readyState,'ended');
const denied=f.media.getDisplayMedia();const rejection=assert.rejects(denied,e=>e.name==='NotAllowedError');const next=f.bridge.poll();
assert.notEqual(next.request,request.request,'Every capture requires a new native request');f.bridge.reject(next.request,'User denied','NotAllowedError');await rejection;
const cancelled=f.media.getDisplayMedia();const cancellation=assert.rejects(cancelled,e=>e.name==='AbortError');f.bridge.close();await cancellation;
assert.equal(f.media.getDisplayMedia,undefined);assert.equal(f.context.bridge,undefined);
assert.throws(()=>fixture({isSecureContext:false}),e=>e.name==='NotAllowedError');
assert.throws(()=>fixture({location:{origin:'https://other.invalid'}}),e=>e.name==='NotAllowedError');
assert.throws(()=>fixture({navigator:{mediaDevices:{getDisplayMedia(){}},userActivation:{isActive:true}}}),e=>e.name==='NotSupportedError');
console.log('Display stream: activation/origin, constraints, real track shape, frame backpressure, clone lifetime, fresh consent and cancellation passed');
