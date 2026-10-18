#!/usr/bin/env node
// Compare actual client exports with host method sets; runtime behavior has separate tests.
import './check-ui-compose.mjs';
import './check-runtime-views.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
const host = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const workspace = path.dirname(host);
const nodeRoot = path.resolve(process.argv[2] || path.join(workspace, 'AutoJs6-Plugin-NodeJs-Runtime'));
const bunRoot = path.resolve(process.argv[3] || path.join(workspace, 'AutoJs6-Plugin-Bun-Runtime'));
const engine = path.join(host, 'app/src/main/java/org/autojs/autojs/engine');
const read = file => fs.readFileSync(file, 'utf8');
const source = read(path.join(nodeRoot, 'app/src/main/cpp/node_bridge_sources.cpp'));
const sorted = values => [...new Set(values)].sort();
function methods(file, name) {
  const match = read(path.join(engine, file)).match(new RegExp(`val ${name}(?:\\s*:[^=\\n]+)?\\s*= setOf\\(([\\s\\S]*?)\\)`));
  assert.ok(match, `Host method set changed: ${file}:${name}`);
  return sorted(Array.from(match[1].matchAll(/"([^"\r\n]+)"/g), item => item[1]));
}
function segment(start, end) {
  const a = source.indexOf(start), b = source.indexOf(end, a);
  assert.ok(a >= 0 && b > a, `Embedded function boundary changed: ${start}`);
  return source.slice(a,b);
}
const create = new Function('__autojs6_call_autojs', '__autojs6_invalid_arg_type', 'process', `
  let __autojs6_limited_pinyin_cache, __autojs6_limited_pinyin4j_cache;
  let __autojs6_limited_screen_metrics_cache, __autojs6_limited_console_cache;
  let __autojs6_limited_opencc_cache, __autojs6_limited_image_cache, __autojs6_limited_images_cache;
  let __autojs6_limited_screen_recorder_cache, __autojs6_limited_media_projection_cache, __autojs6_limited_recorder_cache;
  const __autojs6_recycled_image_handles = new Set();
  const __autojs6_rhino_compat_request_screen_capture = () => { throw Error('Screen capture is not exercised by this structural check'); };
  const __autojs6_rhino_compat_stop_screen_capture = __autojs6_rhino_compat_request_screen_capture;
  const __autojs6_has_own = (object,name) => Object.prototype.hasOwnProperty.call(object,name);
  ${segment('  const __autojs6_opencc_methods =','  const __autojs6_pinyin_styles =')}
  ${segment('  const __autojs6_pinyin_styles =','  function __autojs6_jsox_mathx_flatten(')}
  ${segment('  function __autojs6_limited_image(moduleName)','  function __autojs6_ocr_bridge_options(')}
  ${segment('  function __autojs6_limited_console()','  let __autojs6_main_module_ref =')}
  ${segment('  function __autojs6_limited_screen_recorder()','  function __autojs6_limited_image(')}
  ${segment('  function __autojs6_limited_recorder()','  function __autojs6_media_file_path(')}
  return {pinyin:__autojs6_limited_pinyin(),pinyin4j:__autojs6_limited_pinyin4j(),
    coordinates:__autojs6_limited_screen_metrics(),console:__autojs6_limited_console(),screenRecorder:__autojs6_limited_screen_recorder(),
    media_projection:__autojs6_limited_media_projection(),recorder:__autojs6_limited_recorder(),
    opencc:__autojs6_limited_opencc(),image:__autojs6_limited_image('image'),images:__autojs6_limited_image('images')};
`);
const nodeCalls = [];
const node = create((module,method,args) => {
  assert.equal(module,'opencc','Unexpected Android dispatch in client construction');
  nodeCalls.push({module,method,args});
  return Promise.resolve('converted');
}, message => new TypeError(message), {stdout:{write(){}},stderr:{write(){}}});
const bun = await import(pathToFileURL(path.join(bunRoot,'app/src/main/assets/autojs6/host-client.mjs')).href);
const keys = object => Object.keys(object).filter(key => typeof object[key] === 'function');
const aliases = {showConsole:'show',openConsole:'show',launchConsole:'launch',clearConsole:'clear'};
const controls = keys(node.console).filter(key => String(node.console[key]).includes('"console"')).map(key => aliases[key] || key);
const groups = [
  ['text.pinyin',methods('NodeBridgeProtocol.kt','pinyinMethods'),keys(node.pinyin)],
  ['text.pinyin4j',methods('NodeBridgeProtocol.kt','pinyin4jMethods'),keys(node.pinyin4j)],
  ['screen.metrics',methods('RuntimeCoordinateBridge.kt','METHODS'),keys(node.coordinates)],
  ['console.control',methods('RuntimeConsoleBridge.kt','METHODS'),controls],
];
for(const [name,expected,actual] of groups) {
  assert.deepEqual(sorted(actual),expected,`${name}: Node client/host drift`);
  assert.deepEqual(sorted(bun.HOST_API_METHODS[name]),expected,`${name}: Bun client/host drift`);
}
const openccSource = read(path.join(host,'app/src/main/java/org/autojs/autojs/runtime/api/augment/opencc/OpenCC.kt'));
const openccRegistrations = openccSource.match(/selfAssignmentFunctions = listOf\(([\s\S]*?)\)/);
assert(openccRegistrations,'Rhino OpenCC registration changed');
const openccMethods = sorted([...openccRegistrations[1].matchAll(/::(\w+)\.name/g)].map(item => item[1]));
const conversionTypes = openccMethods.filter(name => name !== 'convert');
const nativeTypes = read(path.join(host,'plugin-api/opencc-api/src/main/java/org/autojs/plugin/opencc/api/OpenccConversionTypes.kt'));
const nativeConstants = Object.fromEntries([...nativeTypes.matchAll(/const val (\w+) = "([^"]+)"/g)].map(item => [item[1],item[2].toLowerCase()]));
const nativeAll = nativeTypes.match(/val ALL = listOf\(([\s\S]*?)\)/);
assert(nativeAll,'Native OpenCC type registry changed');
const nativeNames = [...nativeAll[1].matchAll(/\b[A-Z][A-Z0-9_]*\b/g)].map(item => {
  assert(nativeConstants[item[0]],`Unresolved OpenCC type: ${item[0]}`);
  return nativeConstants[item[0]];
});
const plans = read(path.join(host,'app/src/main/java/org/autojs/autojs/runtime/api/augment/opencc/OpenccConversionPlan.kt'));
const planNames = [...plans.matchAll(/"([a-z0-9]+)" to listOf\(/g)].map(item => item[1]);
assert.deepEqual(sorted([...nativeNames,...planNames]),conversionTypes,'Host conversion plans differ from Rhino exports');
assert.match(read(path.join(engine,'NodeOpenccBridge.kt')),/val METHODS[^=]*= setOf\("convert"\) \+ OpenccConversionPlan\.supportedNames/);
assert.deepEqual(sorted(keys(node.opencc)),openccMethods,'Node OpenCC aliases differ from Rhino');
assert.deepEqual(sorted(bun.OPENCC_CONVERSION_TYPES),conversionTypes,'Bun OpenCC aliases differ from Rhino');
assert.deepEqual(sorted(bun.HOST_API_METHODS['text.opencc']),['convert']);
const bunCalls = [];
const bunClient = await bun.createHostClient({socket:'coverage-socket',fetch:async (url,options) => {
  const capability = url.split('/v1/')[1];
  const reply = capability === 'info' ? {ok:true,bridgeVersion:1,capabilities:Object.keys(bun.HOST_API_METHODS),
    limits:{maxRequestBytes:65536,maxResultBytes:262144,maxCallsPerExecution:128,maxConcurrentCalls:4,callTimeoutMillis:10000}}
    : {ok:true,capability,result:{value:'converted'}};
  if (capability !== 'info') bunCalls.push({capability,...JSON.parse(options.body)});
  return {ok:true,status:200,json:async()=>reply};
}});
assert.deepEqual(sorted(keys(bunClient.opencc)),openccMethods);
for (const method of ['()',...openccMethods]) {
  const conversion = method === '()' || method === 'convert' ? 's2t' : method;
  for (const [client,calls,kind] of [[node.opencc,nodeCalls,'Node'],[bunClient.opencc,bunCalls,'Bun']]) {
    const before = calls.length;
    const returned = method === '()' ? client('汉字',conversion)
      : method === 'convert' ? client.convert('汉字',conversion) : client[method]('汉字');
    assert(returned && typeof returned.then === 'function',`${kind}.${method} is a stub or synchronous return`);
    assert.equal(await returned,'converted');
    assert.equal(calls.length,before+1,`${kind}.${method} did not dispatch`);
    assert.equal(calls.at(-1).method,'convert');
    assert.deepEqual(calls.at(-1).args,['汉字',conversion]);
  }
}
await assert.rejects(node.opencc.s2t(null));
await assert.rejects(bunClient.opencc.s2t(null));
const imageMethods = methods('NodeBridgeProtocol.kt','imageMethods');
const catalog = JSON.parse(read(path.join(nodeRoot,'app/src/main/assets/nodejs/node-capability-catalog.json')));
const recordingMethods=methods('NodeBridgeProtocol.kt','screenRecorderMethods');
assert.deepEqual(sorted(keys(node.screenRecorder)),recordingMethods,'Node recording exports differ from host');
assert.deepEqual(sorted(bun.HOST_API_METHODS['screen.record']),recordingMethods,'Node/Bun recording exports drift');
for(const method of recordingMethods) assert(catalog.bridge.operations.some(item=>item.module==='screen_recorder'&&item.method===method
  &&item.requiredCapabilities.includes('screen_capture')),`Catalog missing screen_recorder.${method}`);
for(const module of ['screen_recorder','media_projection','recorder']) {
  assert(source.includes(`${module}: Object.keys(__autojs6_limited_${module}()),`),`Worker missing ${module}`);
  const declaration=read(path.join(nodeRoot,`docs/nodejs/types/autojs6-node/${module}.d.ts`));
  assert(declaration.includes(`declare module "autojs6:${module}"`),`Canonical declaration missing ${module}`);
}
for (const [module,names,capability] of [['opencc',openccMethods,'opencc'],['image',imageMethods,'image']]) {
  for (const method of names) assert(catalog.bridge.operations.some(item => item.module === module && item.method === method
    && item.requiredCapabilities.includes(capability)),`Development catalog missing ${module}.${method}`);
}
const nodeImageAdaptors = ['getSize','getWidth','getHeight','isRecycled','requestScreenCapture','stopScreenCapture',
  'detectColor','findPointsByColor','findColorEquals','findPointByColor','findPointByColors','read','save',
  'loadAsync','requestScreenCaptureAsync','detectsMultiColors','findPointByColorExactly','findColorInRegion',
  'findImageInRegion','findPointByImage','buildRegion','__buildRegion','ObjectFrame'];
assert.deepEqual(sorted(keys(node.image)),sorted([...imageMethods,...nodeImageAdaptors]),'Node image export/host drift');
assert.deepEqual(sorted(keys(node.images)),sorted(keys(node.image)),'image/images export drift');
for (const [capability,field] of [['image.process','IMAGE_PROCESS_METHODS'],['image.files','IMAGE_FILE_METHODS'],['screen.capture','SCREEN_CAPTURE_METHODS'],['screen.record','SCREEN_RECORD_METHODS']]) {
  assert.deepEqual(sorted(bun.HOST_API_METHODS[capability]),methods('BunHostCapabilityBroker.kt',field),`${capability}: Bun host/client drift`);
}
assert.deepEqual(sorted(keys(bunClient.images)),sorted([
  ...bun.HOST_API_METHODS['image.process'],...bun.HOST_API_METHODS['image.files'],
  ...nodeImageAdaptors,'fromBytes','toBytes','captureScreen','getScreenCaptureOptions',
]),'Bun public image wrapper drift');
const imageRegistration=read(path.join(host,'app/src/main/java/org/autojs/autojs/runtime/api/augment/images/Images.kt'))
  .split('override val selfAssignmentFunctions = listOf(')[1].split('override val selfAssignmentJavaClasses')[0];
const imageExports=sorted([...imageRegistration.matchAll(/::(\w+)\.name/g)].map(match=>match[1]));
for(const name of imageExports) for(const [runtime,api] of [['Node',node.images],['Bun',bunClient.images]])
  assert.equal(typeof api[name],'function',`${runtime}: missing registered images.${name}`);
const imageExtensions = new Function('return ('+segment('  function __autojs6_image_extensions(', '  function __autojs6_limited_image(')+')')();
const bunSource=read(path.join(bunRoot,'app/src/main/assets/autojs6/host-client.mjs'));
const bunAdapter=bunSource.slice(bunSource.indexOf('  function __autojs6_image_extensions('),bunSource.indexOf('const isObject ='));
assert.equal(imageExtensions.toString(),new Function('return ('+bunAdapter+')')().toString(),'Portable Node/Bun image adapters drifted');
const createWorkerModules = new Function('return (' + segment('  function __autojs6_worker_host_modules(', '  async function __autojs6_worker_invoke(') + ')')();
const workerCalls = [];
const uiFactories = new Function(read(path.join(nodeRoot,'tools/nodejs/api/portable-ui-compose.js')) +
  ';return {ui:__autojs6_create_ui,compose:__autojs6_create_compose};')();
node.ui=uiFactories.ui(()=>Promise.resolve(null));node.compose=uiFactories.compose(()=>Promise.resolve(null));
const workerModules = createWorkerModules(async (...call) => { workerCalls.push(call); return 'converted'; },
  {ui:keys(node.ui),compose:keys(node.compose),opencc:keys(node.opencc),image:keys(node.image),images:keys(node.images),pinyin:Object.keys(node.pinyin),pinyin4j:keys(node.pinyin4j),
    screen_metrics:keys(node.coordinates),console:keys(node.console),screen_recorder:keys(node.screenRecorder),
    media_projection:keys(node.media_projection),recorder:keys(node.recorder)},
  {pinyin:Object.fromEntries(Object.entries(node.pinyin).filter(([,v])=>typeof v!=='function'))},
  keys(node.console).filter(name=>node.console[name].__autojs6ConsoleControl),console,imageExtensions,uiFactories.ui,uiFactories.compose);
for (const name of ['ui','compose','opencc','image','images','pinyin','pinyin4j','console','screen_metrics','screen_recorder','media_projection','recorder']) {
  const target=name==='screen_metrics'?node.coordinates:name==='screen_recorder'?node.screenRecorder:node[name];
  assert.deepEqual(sorted(keys(workerModules.get(name))),sorted(keys(target)));
}
for (const [name,value] of Object.entries(node.pinyin).filter(([,v])=>typeof v!=='function')) assert.equal(workerModules.get('pinyin')[name],value);
assert.deepEqual(sorted(keys(bunClient.screenRecorder)),sorted(bun.HOST_API_METHODS['screen.record']));
for (const name of conversionTypes) {
  assert.equal(await workerModules.get('opencc')[name]('汉字'),'converted');
  assert.deepEqual(workerCalls.at(-1).slice(0,3),['autojs6:worker-module:opencc',name,['汉字']]);
}
const protocol = read(path.join(engine,'NodeBridgeProtocol.kt'));
assert.match(protocol,/SCREEN_METRICS to screenMetricsMethods/);
assert.match(protocol,/consoleMethods = setOf\("tail"\) \+ RuntimeConsoleBridge\.METHODS/);
const index = read(path.join(nodeRoot,'docs/nodejs/types/autojs6-node/index.d.ts'));
assert.match(index,/input_observer\.d\.ts/);
assert.match(index,/screen_metrics\.d\.ts/);
console.log(JSON.stringify({passed:true,groups:Object.fromEntries(groups.map(([name,methods])=>[name,methods.length])),
  opencc:{conversions:conversionTypes.length,methods:openccMethods.length,dispatchChecks:nodeCalls.length+bunCalls.length},
  images:{rhinoFunctions:imageExports.length,node:keys(node.images).length,bun:keys(bunClient.images).length},
  workers:{modules:workerModules.size,openccDispatches:workerCalls.length},
  scope:'Actual Node main/Worker and Bun clients versus host; not full Rhino equivalence or Android execution'},null,2));
