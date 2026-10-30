// Structure check of the Node automation family (api-coverage P3.4): every registered host member of automator (the
// Toolkit helpers aside, which follow flow by Q28), auto, threads, continuation, timers (keepAlive / setIntervalExt; the
// timed tasks follow autojs6:tasks, the Node timers are runtime-native), keys, ocr, yolo, qrcode, the events module's
// prototype (Q18), the family's globals (Q177 (a)), sleep / wait and the members of VolatileDispose / YoloDetector has a
// member in the shared factories of node_bridge_sources.cpp and a Node declaration. Workers build the family from the same
// factory and forward keys / ocr / barcode / qrcode to the main thread; the host routes it through RuntimeAutomationBridge
// and NodeAutomationObservers with accessibility.service, events.key.intercept and events.touch never implied (Q178 (a));
// HOST-API describes the family. Behaviour is compared with Rhino by the Node repository's
// tools/nodejs/api/test-automation-rhino.js (desktop) and docs/dev/runtime-automation-2026-10-30.md (device).
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {EventEmitter} from 'node:events';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), node = resolve(root, '../AutoJs6-Plugin-NodeJs-Runtime');
const read = path => readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
const source = read(resolve(node, 'app/src/main/cpp/node_bridge_sources.cpp'));
// A top-level facade function, up to its closing brace at the same indentation.
const part = head => {
  const a = source.indexOf(head), z = source.indexOf('\n  }\n', a);
  assert(a >= 0 && z > a, head.trim());
  return source.slice(a, z + 4);
};
const factory = name => new Function('return (' + part(`  function ${name}(`) + ')')();
// Members are only inspected; yolo.load answers a detector session.
const call = async (module, method) => module === 'yolo' && method === 'load' ? {id: 'check', component: 'check/check'} : null;
const kit = factory('__autojs6_create_automation')(call, {});
const events = factory('__autojs6_create_host_events')(call, EventEmitter);
const system = factory('__autojs6_create_system')(call, {
  engine: {versionCode: 1, versionName: '6.8.0'}, device: {}, EventEmitter, jsox: () => ({}), shell: () => ({}), shizuku: () => ({}),
  lifecycle: {isStopped: () => false, exit() {}}, automation: () => kit,
});
const ocr = kit.rhinoOcr({recognize: async () => [], recognizeText: async () => '', detectTextBounds: async () => []});
// The main thread's qrcode is barcode with qrOnly; its members are the frozen object literal.
const qrcodeBody = part('  function __autojs6_limited_qrcode(');
assert(qrcodeBody.includes('qrOnly: true'), 'qrcode passes qrOnly');
const qrcode = Object.fromEntries([...qrcodeBody.matchAll(/^\s+(\w+): \(image, options\) => barcode\.\1\(image, qr\(options\)\)/gm)].map(match => [match[1], () => {}]));
const keys = {...kit.keys};
for (const name of ['notifications', 'quickSettings', 'lockScreen', 'takeScreenshot', 'splitScreen', 'powerDialog']) keys[name] = () => {};
const detector = await kit.yolo.load('/check', {component: 'check/check'});

const types = name => read(resolve(node, `docs/nodejs/types/autojs6-node/${name}.d.ts`));
// An interface body, up to the closing brace at the indentation of its head.
const body = (text, head) => {
  const start = text.indexOf(head);
  assert(start >= 0, head);
  const indent = text.slice(text.lastIndexOf('\n', start) + 1, start).match(/^\s*/)[0];
  return text.slice(start, text.indexOf('\n' + indent + '}', start));
};
const automation = types('automation');
const declarations = {
  automator: body(automation, 'export interface AutomatorModule {'), auto: body(automation, 'export interface AutoModule {'),
  threads: body(automation, 'export interface ThreadsModule {'), continuation: body(automation, 'export interface ContinuationModule {'),
  timers: body(automation, 'export interface RhinoTimersModule {'), yolo: body(automation, 'export interface YoloModule {'),
  VolatileDispose: body(automation, 'export interface VolatileDispose<T = unknown> {'), YoloDetector: body(automation, 'export interface YoloDetector {'),
  keys: body(types('keys'), 'interface KeysModule {'), ocr: body(types('ocr'), 'export interface OcrModule {'),
  qrcode: body(types('barcode'), 'export interface BarcodeModule {'),
  events: body(types('host_events'), 'export interface EventsModule extends Listeners {') + body(types('host_events'), 'export interface Listeners {'),
  global: automation + types('system'),
};
const declared = (where, name) => where === 'global'
  ? new RegExp(`^declare (const|function) ${name}[(:]`, 'm').test(declarations.global)
  : new RegExp(`(^|[^\\w$])(readonly )?${name}\\??(\\(|:|<)`, 'm').test(declarations[where]);
const objects = {
  automator: kit.automator, auto: kit.auto, threads: kit.threads, continuation: kit.continuation, timers: kit.timers, yolo: kit.yolo,
  VolatileDispose: kit.threads.disposable(), YoloDetector: detector, keys, ocr, qrcode, events, global: {...kit.globals, sleep: system.system.sleep, wait: system.system.wait},
};
let members = 0;
const check = (where, name) => {
  assert(name in objects[where], `Missing ${where}.${name}`);
  assert(declared(where, name), `Missing declaration of ${where}.${name}`);
  members += 1;
};

// Host members: the module registrations, the script prototypes (Q18) and the returned objects' members.
const exported = hostExports(), data = JSON.parse(readFileSync(exported.file, 'utf8'));
const policy = JSON.parse(read(resolve(root, 'tools/api-coverage/runtime-policy.json')));
const toolkit = new Set(Object.keys(policy.node.memberAliases).filter(name => policy.node.memberAliases[name].module === 'autojs6:flow'));
const prototype = module => (data.prototypes.find(item => item.module === module)?.members || []).map(item => typeof item === 'string' ? item : item.name);
const ONLY = {timers: ['keepAlive', 'setIntervalExt']};
for (const module of ['automator', 'auto', 'threads', 'continuation', 'timers', 'yolo', 'keys', 'ocr', 'qrcode']) {
  const names = data.records.filter(row => row.module === module).map(row => row.member);
  assert(names.length, `No host records of ${module}`);
  for (const name of names.filter(item => !toolkit.has(`${module}.${item}`) && (!ONLY[module] || ONLY[module].includes(item)))) check(module, name);
}
for (const module of ['threads', 'continuation', 'events']) for (const name of prototype(module)) check(module, name);
assert(prototype('events').length >= 36, 'events prototype rows');
for (const object of ['VolatileDispose', 'YoloDetector']) {
  const members = exported.returnedObject(object)?.members || [];
  assert(members.length, `No host members of ${object}`);
  for (const item of members) check(object, typeof item === 'string' ? item : item.name);
}
// Globals: the family's names on the global object (their owner modules), sleep / wait, and wait.async.
const OWNERS = new Set(['automator', 'selector', 'threads', 'timers']);
const globals = data.records.filter(row => row.module === '' && (OWNERS.has(row.ownerModule) && !toolkit.has(row.member) && row.member in kit.globals
  || row.ownerModule === '' && ['sleep', 'wait'].includes(row.member)));
for (const row of globals) check('global', row.member);
assert.deepEqual(new Set(globals.map(row => row.member)), new Set([...Object.keys(kit.globals), 'sleep', 'wait']), 'Every global of the family is a host global');
assert.equal(typeof system.system.wait.async, 'function', 'wait.async');
assert.equal(policy.node.memberAliases['wait.async']?.module, 'autojs6:system', 'wait.async member alias');
for (const name of ['VolatileDispose', 'YoloDetector']) assert(policy.returnedObjects[name]?.node, `returnedObjects ${name}`);

// Q177 (a): the main thread and Workers install the globals (accessors, assigning replaces, existing names kept).
const target = {click: 'kept'};
const installed = kit.installGlobals(target);
assert(!installed.includes('click') && target.click === 'kept' && target.back === kit.automator.back, 'installGlobals keeps existing names');
assert(source.includes('__autojs6_system_global_names.concat(__autojs6_limited_automation().installGlobals(globalThis))'), 'The main thread installs the globals');
assert(source.includes('if (methods.automator) automation().installGlobals(globalThis);'), 'Workers install the globals');
// Workers: the factory in the preload, the table entries and the forwarded modules.
assert(source.includes('__autojs6_create_system.toString() + ", " + __autojs6_create_automation.toString() + ", "'), 'Worker preload receives the automation factory');
const table = source.slice(source.indexOf('hostModuleMethods: {'), source.indexOf('\n          },', source.indexOf('hostModuleMethods: {')));
for (const name of ['automator', 'auto', 'threads', 'continuation', 'timers', 'yolo', 'selector', 'keys', 'ocr', 'barcode', 'qrcode']) {
  assert(new RegExp(`(^|[\\s,{])${name}\\s*:\\s*Object\\.keys\\(`).test(table), `Worker table entry ${name}`);
}
const invoke = part('  async function __autojs6_worker_invoke(');
for (const [name, target] of [['keys', '__autojs6_keys()'], ['ocr', '__autojs6_limited_ocr_base()'], ['barcode', '__autojs6_limited_barcode()'], ['qrcode', '__autojs6_limited_qrcode()']]) {
  assert(invoke.includes(`name === "${name}" ? ${target}`), `Workers forward ${name}`);
}

// Host routes and capabilities (Q178 (a)) in the host, the plugin and the catalog.
const engine = name => read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine', name));
const bridge = engine('RuntimeAutomationBridge.kt'), manifest = engine('NodeBridgePermissionManifest.kt'), protocol = engine('NodeBridgeProtocol.kt');
const routes = Object.fromEntries([...bridge.match(/val methodsByModule: Map<String, Set<String>> = mapOf\(([\s\S]*?)\n {8}\)/)[1]
  .matchAll(/(AUTOMATOR|AUTO|YOLO) to setOf\(([^)]*)\)/g)].map(match => [match[1].toLowerCase(), [...match[2].matchAll(/"(\w+)"/g)].map(item => item[1])]));
const eventRoutes = [...protocol.match(/val automationEventsMethods: Set<String>\s*get\(\) = setOf\(([^)]*)\)/)[1].matchAll(/"(\w+)"/g)].map(match => match[1]);
const catalog = JSON.parse(read(resolve(node, 'app/src/main/assets/nodejs/node-capability-catalog.json')));
const operations = new Map(catalog.bridge.operations.map(item => [`${item.module}.${item.method}`, item.requiredCapabilities]));
const EXPECTED = {
  'automator.globalAction': ['keys'], 'automator.press': ['accessibility', 'accessibility.gesture'], 'automator.gestures': ['accessibility', 'accessibility.gesture'],
  'auto.start': ['accessibility', 'accessibility.service'], 'auto.stop': ['accessibility', 'accessibility.service'],
  'auto.launchSettings': ['accessibility', 'accessibility.service'], 'auto.clearCache': ['accessibility', 'accessibility.service'],
  'yolo.load': ['yolo', 'image'], 'yolo.detect': ['yolo', 'image'], 'yolo.close': ['yolo'],
  'events.observeAccessibility': ['accessibility'], 'events.observeAccessibilityState': ['accessibility'], 'events.observeTouch': ['events', 'events.touch'],
  'events.interceptKeys': ['events', 'events.key', 'events.key.intercept'],
};
let routeCount = 0;
for (const [module, methods] of [...Object.entries(routes), ['events', eventRoutes]]) {
  assert(methods.length >= 3, `Host routes of ${module}`);
  for (const method of methods) {
    const key = `${module}.${method}`;
    assert(operations.has(key), `Catalog route ${key}`);
    if (EXPECTED[key]) assert.deepEqual(operations.get(key), EXPECTED[key], `Catalog capabilities of ${key}`);
    else if (module !== 'events') assert.deepEqual(operations.get(key), ['accessibility'], `Catalog capabilities of ${key}`);
    routeCount += 1;
  }
}
const CAPABILITIES = ['accessibility.service', 'events.key.intercept', 'events.touch', 'yolo'];
const pluginManifest = read(resolve(node, 'app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeBridgePermissionManifest.java'));
const catalogCapabilities = new Set(catalog.bridge.permissionCapabilities.map(item => item.id ?? item.name ?? item));
for (const name of CAPABILITIES) {
  assert(manifest.includes(`"${name}"`), `Host capability ${name}`);
  assert(pluginManifest.includes(`"${name}"`), `Plugin capability ${name}`);
  assert(catalogCapabilities.has(name), `Catalog capability ${name}`);
}
for (const [constant, name] of [['ACCESSIBILITY_SERVICE', 'accessibility.service'], ['EVENTS_KEY_INTERCEPT', 'events.key.intercept'], ['EVENTS_TOUCH', 'events.touch']]) {
  assert(manifest.includes(`required == ${constant}`), `${name} never implied (host)`);
  assert(part('  function __autojs6_bridge_declares_capability(').includes(`"${name}"`), `${name} never implied (facade)`);
}

const hostApi = read(resolve(node, 'docs/HOST-API.md'));
assert(hostApi.includes('## 自动化族的 Rhino 对照'), 'HOST-API section of the automation family');
console.log(JSON.stringify({hostMembers: members, globals: globals.length, eventsPrototype: prototype('events').length, hostRoutes: routeCount,
  capabilities: CAPABILITIES.length, hostApi: true}));
