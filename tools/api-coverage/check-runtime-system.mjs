// Structure check of the Node system information & engines family (api-coverage P3.1b): every registered host member of
// autojs (with autojs.version and R), installer, memory, settings, sysprops, powerManager, notice / notice.channel,
// plugins.extend* / plugins.tools, engines and dialogs.build, and the global system functions (currentPackage ...
// isAutoJs6, getClip / setClip) has a member in the shared factories of node_bridge_sources.cpp and a Node declaration;
// memory.watch is not-portable (Q134) and workManager follows the tasks module. Workers build the family from the same
// factory; the host routes it through RuntimeSystemBridge with the nine capabilities of Q133 (b), the dotted ones never
// implied; HOST-API describes the family. Behaviour is compared with Rhino by the Node repository's
// tools/nodejs/api/test-system-rhino.js (desktop) and docs/dev/runtime-system-engines-2026-10-30.md (device).
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
// Members are only inspected.
const call = async () => null;
const system = factory('__autojs6_create_system')(call, {
  engine: {versionCode: 1, versionName: '6.8.0'}, device: {}, EventEmitter, jsox: () => ({}), shell: () => ({}), shizuku: () => ({}),
  lifecycle: {isStopped: () => false, exit() {}},
});
const notice = factory('__autojs6_create_notifications')(call, message => new Error(message), 'check', () => ({}));
const dialogs = factory('__autojs6_create_dialogs')(call, 'check');
// The main-thread engines object needs the bridge; its members are the frozen object literal.
const enginesBody = part('  function __autojs6_limited_engines(');
const enginesLiteral = enginesBody.slice(enginesBody.lastIndexOf('Object.freeze({'));
const engines = Object.fromEntries([...enginesLiteral.matchAll(/^\s+(\w+)(?::\s*\w+)?,?$/gm)].map(match => [match[1], () => {}]));

const types = name => read(resolve(node, `docs/nodejs/types/autojs6-node/${name}.d.ts`));
const body = (text, head) => {
  const start = text.indexOf(head);
  assert(start >= 0, head);
  return text.slice(start, text.indexOf('\n    }', start));
};
const declarations = {
  autojs: body(types('autojs'), 'interface AutojsModule {'), 'autojs.version': body(types('autojs'), 'interface Version {'),
  installer: body(types('installer'), 'interface InstallerModule {'), memory: body(types('memory'), 'interface MemoryModule {'),
  settings: body(types('settings'), 'interface SettingsModule {'), sysprops: body(types('sysprops'), 'interface SyspropsModule {'),
  powerManager: body(types('power_manager'), 'interface PowerManagerModule {'), global: body(types('system'), 'interface SystemModule {'),
  notice: body(types('notifications'), 'interface NotificationsModule {'), 'notice.channel': body(types('notifications'), 'interface ChannelModule {'),
  plugins: body(types('plugins'), 'interface PluginsModule {'), 'plugins.tools': body(types('plugins'), 'interface PluginToolsModule {'),
  engines: body(types('engines'), 'interface EnginesModule {'), dialogs: body(types('dialogs'), 'interface DialogsModule {'),
  workManager: body(types('tasks'), 'interface TasksModule {'),
};
const declared = (where, name) => new RegExp(`(^|[^\\w$])(readonly )?${name}\\??(\\(|:)`, 'm').test(declarations[where]);
const objects = {
  autojs: system.autojs, 'autojs.version': system.autojs.version, installer: system.installer, memory: system.memory, settings: system.settings,
  sysprops: system.sysprops, powerManager: system.power_manager, global: system.system, notice, 'notice.channel': notice.channel,
  plugins: system.plugins, 'plugins.tools': system.plugins.tools, engines, dialogs,
};
const exported = hostExports(), records = JSON.parse(readFileSync(exported.file, 'utf8')).records;
// memory.watch: not-portable, runtime-native (Q134); plugins.load: the main thread's local scan (D-14); dialogs: only
// build belongs to this family; workManager is checked against the tasks declarations.
const SKIPPED = {memory: ['watch'], plugins: ['load']}, ONLY = {dialogs: ['build']};
let members = 0;
const check = (where, name, kind) => {
  const value = objects[where];
  if (value) {
    if (kind === 'Functions') assert.equal(typeof value[name], 'function', `Missing ${where}.${name}`);
    else assert(name in value, `Missing ${where}.${name}`);
  }
  assert(declared(where, name), `Missing declaration of ${where}.${name}`);
  members += 1;
};
const MODULES = ['autojs', 'autojs.version', 'installer', 'memory', 'settings', 'sysprops', 'powerManager', 'notice', 'notice.channel',
  'plugins', 'plugins.tools', 'engines', 'dialogs', 'workManager'];
for (const module of MODULES) {
  for (const kind of ['Functions', 'Properties', 'Getters']) {
    let names;
    try { names = exported.names(module, kind); } catch { continue; }
    for (const name of names.filter(item => !(SKIPPED[module] || []).includes(item) && (!ONLY[module] || ONLY[module].includes(item)))) {
      check(module, name, kind);
    }
  }
}
// Global functions of the family: the system functions of the global object (no owner module), and R of autojs.
const GLOBALS = new Set(Object.keys(system.system));
const globals = records.filter(row => row.module === '' && (row.ownerModule === '' && GLOBALS.has(row.member) || row.ownerModule === 'autojs'));
for (const row of globals) check(row.ownerModule || 'global', row.member, row.kind);
assert(globals.length >= GLOBALS.size, `global rows ${globals.length} < ${GLOBALS.size}`);

// Workers build the family from the shared factory and list its members; the dotted capabilities are never implied.
assert(/function __autojs6_worker_host_modules\([^)]*, createApp, createSystem\)/.test(source), 'Worker host modules take createSystem');
assert(source.includes('__autojs6_create_system.toString()'), 'Worker preload receives the system factory');
const table = source.slice(source.indexOf('hostModuleMethods: {'), source.indexOf('\n          },', source.indexOf('hostModuleMethods: {')));
for (const name of ['autojs', 'installer', 'memory', 'settings', 'sysprops', 'power_manager', 'system', 'engines']) {
  assert(new RegExp(`(^|[\\s,{])${name}\\s*:\\s*Object\\.keys\\(`).test(table), `Worker table entry ${name}`);
}
assert(/plugins\s*:\s*\[[^\]]*"extend"[^\]]*"tools"/.test(table), 'Worker table entry plugins with extend* and tools');
// Q135 (b): the global system functions and R are also globals, installed by the main thread and by Workers.
assert.deepEqual(system.installGlobals({}), [...Object.keys(system.system), 'R'], 'installGlobals installs the system members and R');
assert(source.includes('if (methods.system) system.installGlobals(globalThis);'), 'Workers install the system globals');
assert(/__autojs6_install_autojs_global\(\);\s*__autojs6_install_system_globals\(\);/.test(source), 'The main thread installs the system globals');
const CAPABILITIES = ['autojs', 'autojs.control', 'settings', 'settings.write', 'sysprops', 'memory', 'memory.dump', 'installer', 'installer.mutate'];
const implied = part('  function __autojs6_bridge_declares_capability(');
for (const name of ['autojs.control', 'settings.write', 'memory.dump', 'installer.mutate']) assert(implied.includes(`"${name}"`), `${name} never implied`);

// Host routes and capabilities (Q133 (b)) in the host and the plugin.
const engine = name => read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine', name));
const bridge = engine('RuntimeSystemBridge.kt'), manifest = engine('NodeBridgePermissionManifest.kt');
for (const module of ['autojs', 'settings', 'sysprops', 'memory', 'installer', 'notice', 'power_manager']) assert(bridge.includes(`"${module}"`), `Host system route ${module}`);
const pluginManifest = read(resolve(node, 'app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeBridgePermissionManifest.java'));
const catalog = JSON.parse(read(resolve(node, 'app/src/main/assets/nodejs/node-capability-catalog.json')));
const catalogCapabilities = new Set(catalog.bridge.permissionCapabilities.map(item => item.id ?? item.name ?? item));
for (const name of CAPABILITIES) {
  assert(manifest.includes(`"${name}"`), `Host capability ${name}`);
  assert(pluginManifest.includes(`"${name}"`), `Plugin capability ${name}`);
  assert(catalogCapabilities.has(name), `Catalog capability ${name}`);
}
const enginesRoutes = engine('NodeBridgeProtocol.kt').match(/val enginesMethods = setOf\(([^)]*)\)/)[1];
for (const method of ['all', 'stopAllAndToast', 'execAutoFile']) assert(enginesRoutes.includes(`"${method}"`), `Host engines route ${method}`);

// workManager follows tasks (Q135); memory.watch is excluded.
const policy = JSON.parse(read(resolve(root, 'tools/api-coverage/runtime-policy.json')));
assert(policy.node.moduleAliases.workManager.includes('tasks'), 'workManager alias tasks');
assert(policy.node.globalModules.includes('autojs6:system'), 'autojs6:system is a global module');
assert(policy.exclusions.some(rule => rule.match.includes('memory.watch')), 'memory.watch exclusion');

const hostApi = read(resolve(node, 'docs/HOST-API.md'));
assert(hostApi.includes('## 系统信息与引擎族的 Rhino 对照'), 'HOST-API section of the system information family');
console.log(JSON.stringify({hostMembers: members, globals: globals.length, workerSystem: true, globalThis: true, capabilities: CAPABILITIES.length, hostApi: true}));
