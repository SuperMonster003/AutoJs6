import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const host=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const root=path.dirname(host), node=path.join(root,'AutoJs6-Plugin-NodeJs-Runtime'), bun=path.join(root,'AutoJs6-Plugin-Bun-Runtime');
const read=file=>fs.readFileSync(file,'utf8').replace(/\r\n/g,'\n');
const portable=read(path.join(node,'tools/nodejs/api/portable-ui-compose.js'));
const cpp=read(path.join(node,'app/src/main/cpp/node_bridge_sources.cpp'));
const sdk=read(path.join(bun,'app/src/main/assets/autojs6/host-client.mjs'));
assert(cpp.includes(portable),'Embedded UI/Compose facade is stale');
assert(sdk.includes(portable),'Bun UI/Compose facade differs from Node');
const make=new Function(portable+';return {ui:__autojs6_create_ui,compose:__autojs6_create_compose};')();
const noCall=()=>{throw Error('Pure UI constructors must not call Android');};
const compose=make.compose(noCall), ui=make.ui(noCall);
const canonical=read(path.join(root,'AutoJs6-TypeScript-Declarations/declarations/autojs6/aj6-int-compose.d.ts'));
const names=canonical.match(/type Compose(?:CatalogNodeName|V2NodeName) = [^;]+/g)
  .flatMap(line=>[...line.matchAll(/"([^"\n]+)"/g)].map(match=>match[1]));
assert.equal(names.length,54);
for(const name of names) {assert.equal(typeof compose[name],'function');assert(compose[name]({}));}
assert.equal(compose.AndroidView,undefined,'Native Views require a different interop contract');
const native=read(path.join(host,'app/src/main/java/org/autojs/autojs/engine/RuntimeComposeBridge.kt'));
assert(read(path.join(host,'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt'))
  .includes('modules = listOf(NodeBridgeModules.UI, NodeBridgeModules.COMPOSE)'), 'Provider discovery must advertise Compose');
const methods=[...native.match(/val methods = setOf\(([^)]+)\)/)[1].matchAll(/"([^"]+)"/g)].map(match=>match[1]).sort();
const catalog=JSON.parse(read(path.join(node,'app/src/main/assets/nodejs/node-capability-catalog.json')));
assert.deepEqual(catalog.bridge.operations.filter(op=>op.module==='compose').map(op=>op.method).sort(),methods);
for(const op of catalog.bridge.operations.filter(op=>op.module==='compose')) assert.deepEqual(op.requiredCapabilities,['ui']);
assert(cpp.includes('module === "ui" ? { ...options, events: false }'),'Worker UI must use its own event reader');
assert(cpp.includes('for (const [id, module] of windows) closeWindow(id, module)'),'Worker windows lack exit cleanup');
const {createHostClient}=await import(pathToFileURL(path.join(bun,'app/src/main/assets/autojs6/host-client.mjs')));
const client=await createHostClient({socket:'/test',fetch:async()=>({status:200,ok:true,json:async()=>({ok:true,bridgeVersion:1,capabilities:[],limits:{}})})});
assert.deepEqual(Object.keys(client.ui),Object.keys(ui));assert.deepEqual(Object.keys(client.compose),Object.keys(compose));
await assert.rejects(client.compose.isAvailable(),error=>error.code==='NOT_GRANTED');
await assert.rejects(client.ui.showLayout({type:'Text',text:'denied'}),error=>error.code==='NOT_GRANTED');
console.log(JSON.stringify({composeFactories:names.length,composeHostOperations:methods.length,sharedUiFacades:true,workerWindowCleanup:true}));
