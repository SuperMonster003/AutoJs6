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
  'waitForActivity','waitForPackage','sleep','delay','run','of','all','race','any','cancelAll','defaults','trace'];
const types = read(resolve(node, 'docs/nodejs/types/autojs6-node/flow.d.ts'));
const docs = read(resolve(workspace, 'AutoJs6-Documentation/api/runtimeApis.md')).split('## Flow 异步链与等待核心')[1];
assert(docs);
for (const name of names) {
  assert.equal(typeof flow[name], 'function', name);
  assert(types.includes(name + ':') || types.includes(name + '(') || types.includes(name + '<'), 'Missing type '+name);
  assert(docs.includes(name), 'Missing documentation '+name);
}
const pending = ['smartClick','waitThenClick','waitForIdle','waitForEvent','waitForToast','waitForNotification'];
for (const name of pending) assert.equal(flow[name], undefined, 'Unreviewed scope: '+name);
for (const name of ['sync','await','within','scope','click']) assert.equal(flow.Flow.prototype[name], undefined, name);
assert.equal(flow.pending, 0); assert.equal(flow.workers, 0);
assert(flow.FlowError('TIMEOUT','wait') instanceof flow.FlowError);
for (const method of ['flowProbe','flowForeground']) {
  assert(read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt')).includes('"'+method+'"'));
  assert(read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine/BunAutomationBridge.kt')).includes('"'+method+'"'));
}
console.log(JSON.stringify({flowRegisteredRootNames:names.length,mainBunWorkerFactory:true,remainingToolkitAndEventWaits:true}));
