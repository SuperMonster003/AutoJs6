import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const node = resolve(workspace, 'AutoJs6-Plugin-NodeJs-Runtime'), bun = resolve(workspace, 'AutoJs6-Plugin-Bun-Runtime');
const read = path => readFileSync(path, 'utf8');
execFileSync(process.execPath, [resolve(bun, 'tools/host-client/sync-flow.mjs'), '--check'], {stdio:'inherit'});
const source = read(resolve(node, 'app/src/main/cpp/node_bridge_sources.cpp'));
const first = source.indexOf('  // Shared flow core.'), last = source.indexOf('  // End shared flow core.', first);
assert(first >= 0 && last > first);
const flow = new Function(source.slice(first, last) + ';return __autojs6_create_flow;')()(() => { throw Error('Unexpected host call'); });
const names = ['wait','waitAsync','waitUntil','waitWhile','whenPresent','repeatUntil','waitForStable','waitForVisible','waitForHidden','waitForGone',
  'waitForActivity','waitForPackage','sleep','delay','run','of','all','race','any','cancelAll','defaults','trace',
  'waitThenClick','clickWait','waitThenClickBounds','clickBoundsWait','waitThenLongClick','waitForStableThenClick','clickWhenStable',
  'waitForStableThenClickBounds','clickBoundsWhenStable','clickWhenStableAfter','clickBoundsWhenStableAfter',
  'smartClick','smartClickBounds','clickIfExists','clickBoundsIfExists','clickAny','clickBoundsAny','findAny',
  'waitForIdle','waitForEvent','waitForToast','waitForNotification','scrollUntil','typeInto','dismissPopups','collectList','launchAndWait','backUntil','backToApp','toggle'];
const types = read(resolve(node, 'docs/nodejs/types/autojs6-node/flow.d.ts'));
const docs = read(resolve(workspace, 'AutoJs6-Documentation/api/runtimeApis.md')).split('## Flow 异步链与等待核心')[1];
assert(docs);
for (const name of names) {
  assert.equal(typeof flow[name], 'function', name);
  assert(types.includes(name + ':') || types.includes(name + '(') || types.includes(name + '<'), 'Missing type '+name);
  assert(docs.includes(name), 'Missing documentation '+name);
}
for (const name of ['sync','await']) {
  assert.equal(typeof flow.Flow.prototype[name], 'function', name);
  assert(types.includes(name + '('), name + ' declaration');
  assert(docs.includes(name + '('), name + ' documentation');
}
const scopedActions = ['within','scope','press','swipe','gesture','gestures'];
for (const name of scopedActions) {
  assert.equal(typeof flow.Flow.prototype[name], 'function', name);
  assert(types.includes(name + ':') || types.includes(name + '(') || types.includes(name + '<'), name + ' declaration');
  assert(docs.includes(name), name + ' documentation');
}
const actions=['click','longClick','clickBounds','setText','appendText','clear','paste','copy','cut','select','focus','clearFocus','scrollForward','scrollBackward','scrollUp','scrollDown','scrollLeft','scrollRight','expand','collapse','dismiss','show','contextClick','imeEnter','performAction'];
for(const name of actions){assert.equal(typeof flow.Flow.prototype[name],'function',name);assert(types.includes(name+':'),name+' declaration');assert(docs.includes(name),name+' docs');}
assert.equal(names.length,52);
assert.equal(flow.pending, 0); assert.equal(flow.workers, 0);
assert(flow.FlowError('TIMEOUT','wait') instanceof flow.FlowError);
for (const method of ['flowProbe','flowForeground']) {
  assert(read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt')).includes('"'+method+'"'));
  assert(read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine/BunAutomationBridge.kt')).includes('"'+method+'"'));
}
console.log(JSON.stringify({flowRegisteredRootNames:names.length,nodeActions:actions.length,scopeAndGestureMethods:scopedActions.length,mainBunWorkerFactory:true,eventReadiness:true,portableScopes:true,rhinoSyncParity:false}));
