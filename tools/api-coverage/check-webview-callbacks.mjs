import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),host=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),root=path.dirname(host);
const read=file=>fs.readFileSync(path.join(root,file),'utf8');
const native=read('AutoJs6/app/src/main/java/org/autojs/autojs/engine/RuntimeWebCallbacks.kt');
const source=read('AutoJs6-Plugin-NodeJs-Runtime/tools/nodejs/api/portable-ui-compose.js');
const nodeTypes=read('AutoJs6-Plugin-NodeJs-Runtime/docs/nodejs/types/autojs6-node/ui.d.ts');
const bunTypes=read('AutoJs6-Plugin-Bun-Runtime/app/src/main/assets/autojs6/host-client.d.mts');
const docs=read('AutoJs6-Documentation/api/runtimeApis.md');
const strings=value=>[...value.matchAll(/"([^"\n]+)"/g)].map(match=>match[1]).sort();
let callbacks=1;
assert(native.includes('notify("download", "onDownloadStart"'), 'Missing Android download callback');
assert(docs.includes('onDownloadStart') && nodeTypes.includes('WebDownloadEvent') && bunTypes.includes('WebDownloadEvent'));
for(const [field,nativeField] of [['webViewClientCallbacks','clientNames'],['webChromeClientCallbacks','chromeNames']]) {
  const names=strings(source.match(new RegExp('const '+field+' = \\[([\\s\\S]*?)\\];'))[1]);
  const handlers=strings(native.match(new RegExp('val '+nativeField+' = setOf\\(([\\s\\S]*?)\\)'))[1]);
  assert.deepEqual(names,handlers,field);callbacks+=names.length;
  for(const name of names) {
    assert(native.includes('override fun '+name+'('),name+' lacks an Android callback');
    assert(docs.includes(name),name+' lacks runtime documentation');
    for(const types of [nodeTypes,bunTypes])assert(types.includes(name+'?: WebCallback<'),name+' lacks typed callback data');
  }
}
const createUi=new Function(source+';return __autojs6_create_ui;')();
const view=createUi(()=>{throw Error('Unexpected RPC');}).view({__autojs6ViewHandle:'test'});
const methods=['setWebViewClient','setWebChromeClient','setDownloadListener','chooseFiles','cancelFileChooser','exitFullscreen','download','getDownload','cancelDownload','readDownload','readDocument','evaluateJavascriptAsync'];
for(const name of methods){assert.equal(typeof view[name],'function');assert(docs.includes(name+'('),name+' lacks runtime documentation');for(const types of [nodeTypes,bunTypes])assert(types.includes(name+'('),name);}
assert(!Object.keys(view).includes('setWebViewClient'));
const {createWebFixture,exercise}=require(path.join(root,'AutoJs6-Plugin-NodeJs-Runtime/tools/nodejs/api/test-webview.js'));
const nodeFixture=createWebFixture();await exercise(createUi(nodeFixture.invoke),nodeFixture);
const {createHostClient}=await import(pathToFileURL(path.join(root,'AutoJs6-Plugin-Bun-Runtime/app/src/main/assets/autojs6/host-client.mjs')));
const bunFixture=createWebFixture();
const bun=await createHostClient({socket:'/fixture',fetch:async(url,init)=>{
  const capability=url.split('/').at(-1);
  if(capability==='info')return Response.json({ok:true,bridgeVersion:1,capabilities:['ui.layout'],limits:{maxRequestBytes:65536,maxResultBytes:262144,maxConcurrentCalls:4,maxCallsPerExecution:1024,callTimeoutMillis:10000}});
  const {method,args}=JSON.parse(init.body);
  try{return Response.json({ok:true,capability,result:{value:await bunFixture.invoke('ui',method,args)}});}
  catch(error){return Response.json({ok:false,error:{code:'INVALID_REQUEST',message:error.message}},{status:400});}
}});
await exercise(bun.ui,bunFixture);
for(const field of ['captureEnabled','captureKind','videoQuality','durationLimitSeconds','sizeLimitBytes','timeoutMs','fileName','writable','permissionMode','destinationUri','directoryUri','WebDownloadId']) {
  for(const types of [nodeTypes,bunTypes])assert(types.includes(field),field+' lacks a runtime declaration');
}
for(const mode of ['open','multiple','directory','save']) {
  assert(nodeTypes.includes("'"+mode+"'"));assert(bunTypes.includes("'"+mode+"'"));assert(docs.includes(mode));
}
await import('./test-web-download-stream.mjs');
console.log(JSON.stringify({webCallbacks:callbacks,publicWebMethods:methods.length,nativeClientNamesMatch:true,callbackResultsAndTaskCancellation:true,nodeAndBun:true}));
