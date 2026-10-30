// Structure check of the Bun automation family (api-coverage P4.4): the Bun host client carries the Node runtime's shared
// automation factory, events and system families unchanged (sync-automation-family.mjs / sync-events.mjs / sync-system.mjs
// --check); every registered host member of automator, auto, keys, ocr, barcode, qrcode, yolo, threads, continuation and
// timers (module functions and getters, the YoloDetector / VolatileDispose members) is a member of the client and declared in
// host-client.d.mts, which type-checks, and the family's Rhino globals are members of host.global; every host route the
// factory calls is on a host white list, and the ocr.control / barcode.control / yolo.control method tables equal the host's;
// accessibility.service, events.touch, events.key.intercept, ocr.control, barcode.control and yolo.control (Q181 (a) /
// Q182 (a)) are host grants with plugin center switches; and without grants the family rejects NOT_GRANTED before the host.
// The values are compared with Rhino on a device by tools/api-coverage/device_bun_automation.py.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {copyFileSync, mkdtempSync, readFileSync, rmSync} from 'node:fs';
import {createRequire} from 'node:module';
import {tmpdir} from 'node:os';
import {resolve, dirname, join} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const bun = resolve(workspace, 'AutoJs6-Plugin-Bun-Runtime'), node = resolve(workspace, 'AutoJs6-Plugin-NodeJs-Runtime');
const ace = resolve(workspace, 'AutoJs6-Plugin-Ace-Editor');
const read = path => readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
for (const script of ['sync-automation-family.mjs', 'sync-events.mjs', 'sync-system.mjs']) {
  execFileSync(process.execPath, [resolve(bun, 'tools/host-client', script), '--check', '--node-root', node], {stdio: 'inherit'});
}
const directory = mkdtempSync(join(tmpdir(), 'aj6-bun-automation-family-'));
copyFileSync(resolve(bun, 'app/src/main/assets/autojs6/host-client.mjs'), join(directory, 'host-client.mjs'));
process.on('exit', () => rmSync(directory, {recursive: true, force: true}));
const sdk = await import(pathToFileURL(join(directory, 'host-client.mjs')));

const FAMILY = ['accessibility.control', 'accessibility.gesture', 'accessibility.service', 'screen.capture', 'events.observe', 'events.key',
  'events.touch', 'events.key.intercept', 'ocr.control', 'barcode.control', 'yolo.control', 'flow.control'];
async function client(capabilities, calls = []) {
  return sdk.createHostClient({socket: 'coverage', fetch: async (url, init) => {
    if (url.endsWith('/info')) return Response.json({ok: true, bridgeVersion: 1, capabilities, limits: {}});
    calls.push([url.slice(url.lastIndexOf('/') + 1), JSON.parse(init.body)]);
    throw Error(`Coverage inspection must not call the host: ${url} ${init.body}`);
  }});
}
const hostCalls = [];
const host = await client(FAMILY, hostCalls);

// host-client.d.mts type-checks.
const ts = createRequire(import.meta.url)(resolve(ace, JSON.parse(read(resolve(ace, 'tools/ace-lsp/ace-builds-lock.json'))).assetRoot, 'autojs6/typescript/typescript.js'));
const declarationsFile = resolve(bun, 'app/src/main/assets/autojs6/host-client.d.mts');
const program = ts.createProgram([declarationsFile], {noEmit: true, strict: true, target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.NodeNext,
  moduleResolution: ts.ModuleResolutionKind.NodeNext, lib: ['lib.es2022.d.ts', 'lib.dom.d.ts'], types: []});
const diagnostics = ts.getPreEmitDiagnostics(program).filter(item => item.file?.fileName.endsWith('host-client.d.mts'))
  .map(item => `${item.file.getLineAndCharacterOfPosition(item.start).line + 1}: ${ts.flattenDiagnosticMessageText(item.messageText, ' ')}`);
assert.deepEqual(diagnostics, [], 'host-client.d.mts type-checks');

const declarations = read(declarationsFile);
const block = (text, name, indent = '    ') => {
  const first = text.search(new RegExp(`interface ${name}[ <]`));
  assert(first >= 0, `host-client.d.mts declares ${name}`);
  return text.slice(first, text.indexOf(`\n${indent}}`, first));
};
const namespace = name => declarations.slice(declarations.indexOf(`export namespace ${name} {`));
// A member declared as a method, a property or a getter.
const declared = (name, text) => new RegExp(`(^|[\\s;{])(readonly\\s+)?(get\\s+)?${name}\\??(\\(|:|<)`, 'm').test(text);
const ocrTypes = block(namespace('RuntimeOcr'), 'OcrModule') + block(namespace('RuntimeOcr'), 'FixedEngineOcr');
const modules = {
  automator: [host.automator, block(namespace('RuntimeAutomator'), 'AutomatorModule')],
  auto: [host.auto, block(namespace('RuntimeAuto'), 'AutoModule')],
  keys: [host.keys, block(declarations, 'KeysApi', '')],
  ocr: [host.ocr, ocrTypes],
  barcode: [host.barcode, block(namespace('RuntimeBarcode'), 'BarcodeModule')],
  qrcode: [host.qrcode, block(namespace('RuntimeBarcode'), 'BarcodeModule')],
  yolo: [host.yolo, block(namespace('RuntimeYolo'), 'YoloModule')],
  threads: [host.threads, block(namespace('RuntimeThreads'), 'ThreadsModule')],
  continuation: [host.continuation, block(namespace('RuntimeContinuation'), 'ContinuationModule')],
  timers: [host.timers, block(namespace('RuntimeTimers'), 'RhinoTimersModule')],
};
// Rhino's timers.setTimeout and the other five are Bun's global timers (D-9, runtime-native); the automator Toolkit helpers
// are flow's (runtime-policy.json bun.memberAliases, Q28 (a)); timers.addDailyTask and the other timed task members are the
// tasks module's (bun.namespaceAliases), checked by its own family.
const NATIVE_TIMERS = new Set(['setTimeout', 'setInterval', 'setImmediate', 'clearTimeout', 'clearInterval', 'clearImmediate']);
const policy = JSON.parse(read(resolve(root, 'tools/api-coverage/runtime-policy.json'))), aliases = policy.bun.memberAliases;
const aliased = (owner, member) => !(member in (modules[owner][0] ?? {}))
  && (policy.bun.namespaceAliases[owner] ?? []).some(name => typeof host[name]?.[member] === 'function');
const exported = hostExports(), data = JSON.parse(readFileSync(exported.file, 'utf8'));
const rows = data.records.filter(row => row.ownerModule in modules).map(row => ({id: row.api, owner: row.ownerModule, member: row.member, kind: row.kind}))
  .concat(data.prototypes.filter(item => item.module in modules).flatMap(item => (item.members || [])
    .filter(member => !member.excluded && !member.shadowed).map(member => ({id: `${item.module}.${member.name}`, owner: item.module, member: member.name, kind: member.kind}))));
const checked = Object.fromEntries(Object.keys(modules).map(name => [name, 0]));
const seen = new Set();
for (const row of rows) {
  if (seen.has(row.id) || row.id in aliases || row.owner === 'timers' && NATIVE_TIMERS.has(row.member) || aliased(row.owner, row.member)) continue;
  seen.add(row.id);
  const [value, types] = modules[row.owner];
  // ocr.mlkit.detect and the other fixed-engine members: the engine object, then its member.
  const path = row.member.split('.'), owner = path.slice(0, -1).reduce((object, key) => object?.[key], value), member = path.at(-1);
  if (row.kind === 'Functions') assert.equal(typeof owner?.[member], 'function', `Missing host.${row.owner}.${row.member} (${row.id})`);
  else assert(owner && member in owner, `Missing host.${row.owner}.${row.member} (${row.id})`);
  assert(declared(member, types), `Missing declaration of ${row.owner}.${row.member} (${row.id})`);
  checked[row.owner] += 1;
}
assert(checked.automator >= 55 && checked.auto >= 38 && checked.keys === 12 && checked.ocr >= 6 && checked.threads >= 9 && checked.timers >= 4, `family rows ${JSON.stringify(checked)}`);
// Returned objects: YoloDetector members on the handle type, VolatileDispose on threads.disposable().
const handles = {YoloDetector: block(namespace('RuntimeYolo'), 'YoloDetector'), VolatileDispose: block(namespace('RuntimeThreads'), 'VolatileDispose')};
const disposable = host.threads.disposable();
for (const item of data.prototypes.filter(item => item.module in handles)) {
  for (const member of (item.members || []).filter(member => !member.excluded && !member.shadowed)) {
    assert(declared(member.name, handles[item.module]), `Missing declaration of ${item.module}#${member.name}`);
    if (item.module === 'VolatileDispose') assert.equal(typeof disposable[member.name], 'function', `Missing VolatileDispose#${member.name}`);
  }
}
// The family's Rhino globals: members of host.global, declared by AutomationGlobals or the system family.
const globals = block(declarations, 'AutomationGlobals', '') + block(namespace('RuntimeSystem'), 'SystemModule');
for (const name of ['click', 'longClick', 'press', 'swipe', 'gesture', 'gestureAsync', 'gestures', 'gesturesAsync', 'scrollUp', 'scrollDown', 'input',
  'setText', 'back', 'home', 'recents', 'notifications', 'quickSettings', 'powerDialog', 'splitScreen', 'retry', 'sync', 'keepAlive', 'loop', 'pickup',
  'detect', 'existsOne', 'existsAll', 'sleep', 'wait']) {
  assert.equal(typeof host.global[name], 'function', `Missing host.global.${name}`);
  assert(declared(name, globals), `Missing declaration of global ${name}`);
}
assert.equal(typeof host.global.wait.async, 'function', 'host.global.wait.async');
assert.deepEqual(hostCalls, [], 'Reading the family must not call the host');

// Host routes: what the factory calls is on the host's white lists; the recognition tables equal the host's.
const engine = name => read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine', name));
const protocol = engine('NodeBridgeProtocol.kt'), automation = engine('RuntimeAutomationBridge.kt'), bridge = engine('BunAutomationBridge.kt');
const names = body => [...body.matchAll(/"(\w+)"/g)].map(match => match[1]);
const registry = (text, pattern) => { const body = text.match(pattern)?.[1]; assert(body, `${pattern}`); return names(body); };
const byModule = {automator: registry(automation, /AUTOMATOR to setOf\(([^)]*)\)/), auto: registry(automation, /AUTO to setOf\(([^)]*)\)/),
  yolo: registry(automation, /YOLO to setOf\(([^)]*)\)/), accessibility: [...sdk.HOST_API_METHODS['accessibility.control']]};
const client_ = read(resolve(bun, 'app/src/main/assets/autojs6/host-client.mjs'));
const factory = client_.slice(client_.indexOf('// BEGIN GENERATED AUTOMATION FAMILY'), client_.indexOf('// END GENERATED AUTOMATION FAMILY'));
const invoked = [...factory.matchAll(/invoke\("(\w+)", "(\w+)"/g)].map(match => [match[1], match[2]]);
assert(invoked.length >= 30, 'factory routes');
for (const [module, method] of invoked) assert(byModule[module]?.includes(method), `The factory calls ${module}.${method}, which no Bun grant routes`);
const sorted = list => [...list].sort();
assert.deepEqual(sorted(sdk.HOST_API_METHODS['ocr.control']), sorted(registry(protocol, /val ocrMethods = setOf\(([^)]*)\)/)), 'ocr.control: client / host drift');
assert.deepEqual(sorted(sdk.HOST_API_METHODS['barcode.control']), sorted(registry(protocol, /val barcodeMethods = setOf\(([^)]*)\)/)), 'barcode.control: client / host drift');
assert.deepEqual(sorted(sdk.HOST_API_METHODS['yolo.control']), sorted(byModule.yolo), 'yolo.control: client / host drift');
for (const pattern of [/Grants\.OCR_CONTROL to NodeBridgeModules\.ocrMethods/, /Grants\.BARCODE_CONTROL to NodeBridgeModules\.barcodeMethods/,
  /Grants\.YOLO_CONTROL to RuntimeAutomationBridge\.methodsByModule\.getValue\(RuntimeAutomationBridge\.YOLO\)/,
  /Grants\.EVENTS_OBSERVE to setOf\([^)]*\) \+ NodeBridgeModules\.automationEventsMethods/,
  /Grants\.ACCESSIBILITY_CONTROL to mapOf\(RuntimeAutomationBridge\.AUTOMATOR to [^\n]*\n\s*RuntimeAutomationBridge\.AUTO to /,
  /Grants\.ACCESSIBILITY_SERVICE in grants\) add\(NodeBridgePermissionManifest\.ACCESSIBILITY_SERVICE\)/,
  /Grants\.SCREEN_CAPTURE in grants\) add\(NodeBridgePermissionManifest\.SCREEN_CAPTURE\)/]) assert(pattern.test(bridge), `BunAutomationBridge ${pattern}`);
const grantsFile = read(resolve(root, 'app/src/main/java/org/autojs/autojs/core/plugin/bun/BunHostCapabilityGrants.kt'));
const preferences = read(resolve(root, 'app/src/main/res/xml/fragment_plugin_settings_preferences_official.xml'));
const strings = read(resolve(root, 'app/src/main/res/values/strings_bun_automation_family.xml'));
const hostCapability = declarations.match(/export type HostCapability = ([^;]*);/)[1];
for (const grant of ['accessibility.service', 'events.touch', 'events.key.intercept', 'ocr.control', 'barcode.control', 'yolo.control']) {
  const key = grant.replace(/\./g, '_');
  assert(grantsFile.includes(`"${grant}"`), `Host grant ${grant}`);
  assert(preferences.includes(`plugin_bun_${key}_grant`), `Plugin center switch ${grant}`);
  assert(strings.includes(`text_bun_${key}_grant`) && strings.includes(`summary_bun_${key}_grant`), `Strings of ${grant}`);
  assert(hostCapability.includes(`"${grant}"`), `HostCapability ${grant}`);
}

// Without grants: the family rejects NOT_GRANTED with the missing Bun grants and never reaches the host.
const calls = [];
const denied = await client(['accessibility.control', 'events.observe', 'events.key'], calls);
const image = {id: 'image', __autojs6ImageHandle: 'image'};
for (const [name, call, missing] of [
  ['automator.press', () => denied.automator.press(1, 2), 'accessibility.gesture'],
  ['global.swipe', () => denied.global.swipe(0, 0, 9, 9, 100), 'accessibility.gesture'],
  ['auto.start', () => denied.auto.start(), 'accessibility.service'],
  ['auto.enable', () => denied.auto.enable(), 'accessibility.service'],
  ['automator.captureScreen', () => denied.automator.captureScreen(), 'screen.capture'],
  ['events.observeTouch', () => denied.events.observeTouch(), 'events.touch'],
  ['events.setKeyInterceptionEnabled', () => denied.events.setKeyInterceptionEnabled(true), 'events.key.intercept'],
  ['ocr.recognizeText', () => denied.ocr.recognizeText(image), 'ocr.control'],
  ['ocr.paddle.detect', () => denied.ocr.paddle.detect(image), 'ocr.control'],
  ['barcode.detect', () => denied.barcode.detect(image), 'barcode.control'],
  ['qrcode.recognizeText', () => denied.qrcode.recognizeText(image), 'barcode.control'],
  ['yolo.load', () => denied.yolo.load('/sdcard/model', {component: 'p/c'}), 'yolo.control'],
]) {
  await assert.rejects(call(), error => error.code === 'NOT_GRANTED' && error.missingCapabilities.includes(missing), `${name} without grants`);
}
const bare = await client([], calls);
for (const [name, call] of [['automator.click', () => bare.automator.click(1, 2)], ['auto.isRunning', () => bare.auto.isRunning()],
  ['global.back', () => bare.global.back()]]) {
  await assert.rejects(call(), error => error.code === 'NOT_GRANTED' && error.missingCapabilities.includes('accessibility.control'), `${name} without grants`);
}
assert.deepEqual(calls, [], 'Denied entries must not call the host');

const hostApi = read(resolve(bun, 'docs/host-api.md'));
for (const name of ['accessibility.service', 'events.touch', 'events.key.intercept', 'ocr.control', 'barcode.control', 'yolo.control', 'auto.waitFor',
  'threads.start', 'AutomationGlobals', 'sync-automation-family.mjs']) {
  assert(hostApi.includes(name), `Bun docs/host-api.md describes ${name}`);
}
console.log(JSON.stringify({members: checked, routes: {factory: invoked.length, ...Object.fromEntries(['ocr.control', 'barcode.control', 'yolo.control']
  .map(name => [name, sdk.HOST_API_METHODS[name].length]))}, declarations: 'type-checked', hostApi: true}));
