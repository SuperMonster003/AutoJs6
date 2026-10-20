import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const node = resolve(workspace, 'AutoJs6-Plugin-NodeJs-Runtime'), bun = resolve(workspace, 'AutoJs6-Plugin-Bun-Runtime');
const read = path => readFileSync(path, 'utf8');
execFileSync(process.execPath, [resolve(bun, 'tools/host-client/sync-speech.mjs'), '--check'], {stdio: 'inherit'});
const source = read(resolve(node, 'app/src/main/cpp/node_bridge_sources.cpp'));
const a = source.indexOf('  // Shared speech facade.'), z = source.indexOf('  // End shared speech facade.', a);
assert(a >= 0 && z > a);
const create = new Function(source.slice(a, z) + ';return __autojs6_create_speech;')();
const speech = create(() => { throw Error('Coverage inspection must not call the host'); });
const types = read(resolve(node, 'docs/nodejs/types/autojs6-node/speech.d.ts'));
const docs = read(resolve(workspace, 'AutoJs6-Documentation/api/runtimeApis.md')).split('## Node.js / Bun / Worker 的语音识别与模型管理')[1];
assert(docs, 'Speech runtime documentation');
let count = 0;
for (const [name, api] of [['Speech', speech], ['SpeechModels', speech.models]]) {
  const rhino = read(resolve(root, 'app/src/main/java/org/autojs/autojs/runtime/api/augment/speech', name + '.kt'));
  const declarations = rhino.split('override val selfAssignmentFunctions = listOf(')[1].split('\n    )')[0];
  for (const [, method] of declarations.matchAll(/::(\w+)\.name/g)) {
    assert.equal(typeof api[method], 'function', `Missing ${name}.${method}`);
    assert(types.includes(method + '('), `Missing speech declaration ${method}`);
    assert(docs.includes(method), `Missing speech documentation ${method}`); count++;
  }
}
assert(speech.SpeechError('test', 'MODEL_MISSING') instanceof speech.SpeechError);
assert(source.includes('for(const id of speechClients)closeSpeech(id)'));
const host = read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine/RuntimeSpeechBridge.kt'));
for (const route of ['recognizeFile', 'detectVoiceFile', 'openSession', 'openWakeSession', 'installModel', 'importModel', 'refreshCatalog', 'synthesizeToFile'])
  assert(host.includes('SpeechPluginHost.' + route), `Native speech provider ${route}`);
assert(host.includes('scope.cancel()') && host.includes('SpeechAudio.writeWavHeader'));
console.log(JSON.stringify({rhinoSpeechMethods: count, nodeBunWorker: true, sharedPlugin: true}));
