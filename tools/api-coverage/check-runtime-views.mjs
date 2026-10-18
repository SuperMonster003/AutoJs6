import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const host=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),root=path.dirname(host);
const read=file=>fs.readFileSync(file,'utf8');
const native=read(path.join(host,'app/src/main/java/org/autojs/autojs/engine/RuntimeViewAttributes.kt'));
const widgets=read(path.join(host,'app/src/main/java/org/autojs/autojs/engine/RuntimeViewWidgets.kt'));
const components=read(path.join(host,'app/src/main/java/org/autojs/autojs/engine/RuntimeViewComponents.kt'));
const source=read(path.join(root,'AutoJs6-Plugin-NodeJs-Runtime/tools/nodejs/api/portable-ui-compose.js'));
const nodeTypes=read(path.join(root,'AutoJs6-Plugin-NodeJs-Runtime/docs/nodejs/types/autojs6-node/ui.d.ts'));
const bunTypes=read(path.join(root,'AutoJs6-Plugin-Bun-Runtime/app/src/main/assets/autojs6/host-client.d.mts'));
const strings=text=>[...text.matchAll(/"([^"\n]+)"/g)].map(match=>match[1]);
const writable=strings(native.match(/val writable = setOf\(([\s\S]*?)\)\n/)[1]).sort();
const declared=strings(source.match(/const writableViewProperties = \[([\s\S]*?)\];/)[1]).sort();
assert.deepEqual(declared,writable,'Native attributes and JS property proxies differ');
const handled=new Set([...native.matchAll(/prop\("(\w+)"/g)].map(match=>match[1]));
for(const key of ['width','height','min','max','value','displayedValues'])handled.add(key);
for(const key of writable)assert(handled.has(key),'Attribute lacks native mutation handler: '+key);
const snapshot=native.slice(native.indexOf('fun snapshot('));
for(const key of writable)assert(snapshot.includes('put("'+key+'"'),'Attribute lacks a native getter: '+key);
const additionalWidgets=strings(widgets.match(/val types = setOf\(([\s\S]*?)\)\n/)[1]);
for(const type of additionalWidgets) {
  assert((widgets+components).includes('"'+type+'" ->'),type+' lacks a native factory');
  assert(nodeTypes.includes('"'+type+'"'),type+' lacks a declaration');
}
const createUi=new Function(source+';return __autojs6_create_ui;')();
let calls=0;const ui=createUi(()=>{calls++;throw Error('Unexpected bridge call');});
const token={__autojs6ViewHandle:'coverage',id:'coverage',type:'TextView'};
const view=ui.view(token);
for(const key of writable) {
  const descriptor=Object.getOwnPropertyDescriptor(view,key);
  assert.equal(typeof descriptor.get,'function');assert.equal(typeof descriptor.set,'function');assert.equal(descriptor.enumerable,false);
  for(const types of [nodeTypes,bunTypes]) {
    assert(types.includes('get '+key+'(): Promise<'),key+' lacks an asynchronous declaration');
    assert(types.includes('set '+key+'(value:'),key+' lacks an assignment declaration');
  }
}
assert.deepEqual(JSON.parse(JSON.stringify(view)),token,'Native proxies leaked into serializable identity');
assert.equal(calls,0);
assert.equal(typeof view.flush,'function');
console.log(JSON.stringify({nativePropertyProxies:writable.length,additionalXmlWidgetFactories:additionalWidgets.length,
  declarationsAndHandlersAgree:true,plainSerializableHandles:true}));

const commands=['loadUrl','loadHtml','evaluateJavascript','goBack','goForward','reload','stopLoading','openDrawer','closeDrawer','setCurrentItem','showOverflowMenu','hideOverflowMenu'];
for(const name of commands){assert.equal(typeof view[name],'function');assert(components.includes('"'+name+'"'),name+' lacks a native command');for(const types of [nodeTypes,bunTypes])assert(types.includes(name+'('),name+' lacks a declaration');}
console.log(JSON.stringify({nativeCommands:commands.length,webViewDisposal:components.includes('it.release()'),offscreenPages:components.includes('view.pages.toList()')}));
