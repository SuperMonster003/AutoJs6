import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {hostExports} from './host-exports.mjs';
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
const exported = hostExports();
let count = 0;
for (const [name, api] of [['speech', speech], ['speech.models', speech.models]]) {
  for (const method of exported.functions(name)) {
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
const contract=read(resolve(root,'plugin-api/speech-api/src/main/java/org/autojs/plugin/speech/api/SpeechPluginActions.kt'));
const literal=name=>contract.match(new RegExp('const val '+name+' = "([^"]+)"'))?.[1];
const plugin=resolve(workspace,'AutoJs6-Plugin-Three-Macaw-Speech');
const manifest=read(resolve(plugin,'app/src/main/AndroidManifest.xml'));
const service=name=>manifest.match(new RegExp('<service\\s+android:name="\\.service\\.'+name+'"[\\s\\S]*?</service>'))?.[0];
assert(service('ThreeMacawSpeechService')?.includes('android:permission="'+literal('PLUGIN_PERMISSION')+'"'));
const packaged=service('ThreeMacawSpeechPackagedService');
assert(packaged?.includes('android:permission="'+literal('PACKAGED_PERMISSION')+'"'));
assert(packaged.includes('android:name="'+literal('PACKAGED_SPEECH')+'"'));
const appManifest=read(resolve(root,'app/src/main/AndroidManifest.xml'));
assert(appManifest.includes('<uses-permission android:name="'+literal('PACKAGED_PERMISSION')+'"'));
assert(appManifest.includes('<action android:name="'+literal('PACKAGED_SPEECH')+'"'));
const prepare=read(resolve(root,'app/src/main/java/org/autojs/autojs/apkbuilder/template/RemoteApkBuildRequestPreparer.kt'));
assert.equal(prepare.split('SpeechPluginActions.PACKAGED_PERMISSION').length-1,2,'Both Node and Bun package preparation must retain speech permission');
console.log(JSON.stringify({rhinoSpeechMethods: count, nodeBunWorker: true, sharedPlugin: true, packagedEntry: true}));
