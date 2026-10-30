// Structure check of the Bun system information & engines family (api-coverage P4.1b): the Bun host client carries
// Node's shared system factory, main-thread engines, notice extras and dialogs.build unchanged (sync-system.mjs /
// sync-events.mjs / sync-device-dialogs.mjs / sync-automation.mjs --check); every registered host member of autojs (with
// autojs.version and R), installer, memory (watch is not-portable, D-22), settings, sysprops, powerManager, notice /
// notice.channel, plugins.extend* / plugins.tools, engines, dialogs.build, app.autojs, workManager (host.tasks) and the
// global system functions is a member of the client and declared in host-client.d.mts, which type-checks; the query
// grants' method tables equal the host's BunAutomationBridge white lists (RuntimeSystemBridge, engines, plugin_tools),
// the notice / powerManager routes are the host's second modules of notifications.post / device.control, and the 13 grants
// of Q136 (a) / Q137 (a) are host grants with plugin center switches; without grants the family rejects NOT_GRANTED before
// the host. The values are compared with Rhino on a device by tools/api-coverage/device_bun_system.py.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {resolve, dirname} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const bun = resolve(workspace, 'AutoJs6-Plugin-Bun-Runtime'), node = resolve(workspace, 'AutoJs6-Plugin-NodeJs-Runtime');
const ace = resolve(workspace, 'AutoJs6-Plugin-Ace-Editor');
const read = path => readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
for (const script of ['sync-system.mjs', 'sync-events.mjs', 'sync-device-dialogs.mjs', 'sync-automation.mjs']) {
  execFileSync(process.execPath, [resolve(bun, 'tools/host-client', script), '--check', '--node-root', node], {stdio: 'inherit'});
}
const sdk = await import(pathToFileURL(resolve(bun, 'app/src/main/assets/autojs6/host-client.mjs')));

// A client with every grant of the family but engines.control (which asks the host for its record first): building it
// and reading its members must not reach the host except for the app identity.
const FAMILY = ['autojs.control', 'autojs.manage', 'settings.control', 'settings.write', 'sysprops.control', 'memory.control', 'memory.dump',
  'installer.control', 'installer.mutate', 'engines.control', 'engines.exec', 'plugin_tools.control', 'plugin_tools.user_granted'];
async function client(capabilities, calls = []) {
  return sdk.createHostClient({socket: 'coverage', fetch: async (url, init) => {
    if (url.endsWith('/info')) return Response.json({ok: true, bridgeVersion: 1, capabilities, limits: {}});
    const request = JSON.parse(init.body);
    calls.push([url.slice(url.lastIndexOf('/') + 1), request]);
    if (request.action === 'identity') return Response.json({ok: true, capability: 'app.control', result: {value: {packageName: 'org.autojs.autojs6', versionName: '6.8.0', versionCode: 5935}}});
    throw Error(`Coverage inspection must not call the host: ${url} ${init.body}`);
  }});
}
const host = await client([...FAMILY.filter(name => name !== 'engines.control'), 'app.control', 'notifications.post', 'notifications.settings',
  'device.control', 'device.power', 'tasks.control', 'dialogs.control', 'accessibility.control', 'clipboard.control']);

// host-client.d.mts type-checks (the system types reference the shared Bun types only).
const ts = createRequire(import.meta.url)(resolve(ace, JSON.parse(read(resolve(ace, 'tools/ace-lsp/ace-builds-lock.json'))).assetRoot, 'autojs6/typescript/typescript.js'));
const declarationsFile = resolve(bun, 'app/src/main/assets/autojs6/host-client.d.mts');
const program = ts.createProgram([declarationsFile], {noEmit: true, strict: true, target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.NodeNext,
  moduleResolution: ts.ModuleResolutionKind.NodeNext, lib: ['lib.es2022.d.ts', 'lib.dom.d.ts'], types: []});
const diagnostics = ts.getPreEmitDiagnostics(program).filter(item => item.file?.fileName.endsWith('host-client.d.mts'))
  .map(item => `${item.file.getLineAndCharacterOfPosition(item.start).line + 1}: ${ts.flattenDiagnosticMessageText(item.messageText, ' ')}`);
assert.deepEqual(diagnostics, [], 'host-client.d.mts type-checks');

const declarations = read(declarationsFile);
const block = (namespace, name) => {
  const first = declarations.indexOf(`export namespace ${namespace} {`), start = declarations.indexOf(`interface ${name} `, first);
  assert(first >= 0 && start > first, `host-client.d.mts declares ${namespace}.${name}`);
  return declarations.slice(start, declarations.indexOf('\n    }', start));
};
const types = {
  autojs: block('RuntimeAutojs', 'AutojsModule'), 'autojs.version': block('RuntimeAutojs', 'Version'),
  installer: block('RuntimeInstaller', 'InstallerModule'), memory: block('RuntimeMemory', 'MemoryModule'),
  settings: block('RuntimeSettings', 'SettingsModule'), sysprops: block('RuntimeSysprops', 'SyspropsModule'),
  powerManager: block('RuntimePowerManager', 'PowerManagerModule'), global: block('RuntimeSystem', 'SystemModule'),
  notice: block('RuntimeNotifications', 'NotificationsModule'), 'notice.channel': block('RuntimeNotifications', 'ChannelModule'),
  plugins: block('RuntimePlugins', 'PluginsModule'), 'plugins.tools': block('RuntimePlugins', 'PluginToolsModule'),
  engines: block('RuntimeEngines', 'EnginesModule'), dialogs: block('RuntimeDialogs', 'DialogsModule'), workManager: block('RuntimeTasks', 'TasksModule'),
  app: block('RuntimeApp', 'AppModule'),
};
// A member declared as a method, a property or a getter.
const declared = (where, name) => new RegExp(`(^|[^\\w$])(readonly\\s+)?(get\\s+)?${name}\\??(\\(|:|<)`, 'm').test(types[where]);
const objects = {
  autojs: host.autojs, 'autojs.version': host.autojs.version, installer: host.installer, memory: host.memory, settings: host.settings,
  sysprops: host.sysprops, powerManager: host.powerManager, global: host.global, notice: host.notifications, 'notice.channel': host.notifications.channel,
  plugins: host.plugins, 'plugins.tools': host.plugins.tools, engines: host.engines, dialogs: host.dialogs, workManager: host.tasks, app: host.app,
};
const exported = hostExports(), records = JSON.parse(readFileSync(exported.file, 'utf8')).records;
// memory.watch: not-portable, runtime-native (D-22, Q140 (a)); plugins.load: rhino-only (D-14); dialogs and app: only
// build / autojs belong to this family.
const SKIPPED = {memory: ['watch'], plugins: ['load']}, ONLY = {dialogs: ['build'], app: ['autojs']};
let members = 0;
const check = (where, name, kind) => {
  const value = objects[where];
  if (kind === 'Functions') assert.equal(typeof value[name], 'function', `Missing host.${where}.${name}`);
  else assert(name in value, `Missing host.${where}.${name}`);
  assert(declared(where, name), `Missing declaration of ${where}.${name}`);
  members += 1;
};
for (const module of Object.keys(objects).filter(name => name !== 'global')) {
  for (const kind of ['Functions', 'Properties', 'Getters']) {
    let names;
    try { names = exported.names(module, kind); } catch { continue; }
    for (const name of names.filter(item => !(SKIPPED[module] || []).includes(item) && (!ONLY[module] || ONLY[module].includes(item)))) check(module, name, kind);
  }
}
assert.equal(host.app.autojs, host.autojs, 'app.autojs is the autojs namespace');
// The global system functions (rows without an owner module) and autojs.R, on host.global / host.autojs.
const SYSTEM = new Set(['currentPackage', 'currentActivity', 'currentComponent', 'waitForActivity', 'waitForPackage',
  'isStopped', 'isShuttingDown', 'notStopped', 'isRunning', 'exit', 'stop', 'requiresApi', 'requiresAutojsVersion', 'setScreenMetrics', 'getClip', 'setClip',
  'isAutoJs6', 'WIDTH', 'HEIGHT']);
const globals = records.filter(row => row.module === '' && (row.ownerModule === '' && SYSTEM.has(row.member) || row.ownerModule === 'autojs'));
for (const row of globals) check(row.ownerModule || 'global', row.member, row.kind);
assert(globals.length >= 17, `global rows ${globals.length}`);

// Method tables: client == host white list, every route a real host route; notice / powerManager are the host's second
// modules of their grants; the family's grants are host grants with plugin center switches.
const engineDir = name => read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine', name));
const systemBridge = engineDir('RuntimeSystemBridge.kt'), protocol = engineDir('NodeBridgeProtocol.kt'), bridge = engineDir('BunAutomationBridge.kt');
const names = text => [...text.matchAll(/"(\w+)"/g)].map(match => match[1]).sort();
const routes = key => names(systemBridge.match(new RegExp(`\\n\\s+${key} to setOf\\(([\\s\\S]*?)\\),?\\n`))[1]);
for (const [capability, key] of [['autojs.control', 'AUTOJS'], ['settings.control', 'SETTINGS'], ['sysprops.control', 'SYSPROPS'], ['memory.control', 'MEMORY'], ['installer.control', 'INSTALLER']]) {
  assert.deepEqual([...sdk.HOST_API_METHODS[capability]].sort(), routes(key), `${capability}: client / RuntimeSystemBridge drift`);
  assert(new RegExp(`Grants\\.${capability.split('.')[0].toUpperCase()}_CONTROL to RuntimeSystemBridge\\.methodsByModule\\.getValue\\(RuntimeSystemBridge\\.${key}\\)`).test(bridge), `${capability} host white list`);
}
for (const [capability, key] of [['engines.control', 'engines'], ['plugin_tools.control', 'pluginTools']]) {
  assert.deepEqual([...sdk.HOST_API_METHODS[capability]].sort(), names(protocol.match(new RegExp(`val ${key}Methods = setOf\\(([^)]*)\\)`))[1]), `${capability}: client / host drift`);
}
assert(/Grants\.ENGINES_CONTROL to NodeBridgeModules\.enginesMethods/.test(bridge) && /Grants\.PLUGIN_TOOLS_CONTROL to NodeBridgeModules\.pluginToolsMethods/.test(bridge), 'engines / plugin tools white lists');
assert(/NOTIFICATIONS_POST to mapOf\(RuntimeSystemBridge\.NOTICE/.test(bridge) && /DEVICE_CONTROL to mapOf\(RuntimeSystemBridge\.POWER_MANAGER/.test(bridge), 'notice / powerManager second modules');
const grantsFile = read(resolve(root, 'app/src/main/java/org/autojs/autojs/core/plugin/bun/BunHostCapabilityGrants.kt'));
const preferences = read(resolve(root, 'app/src/main/res/xml/fragment_plugin_settings_preferences_official.xml'));
const strings = read(resolve(root, 'app/src/main/res/values/strings_bun_system.xml'));
const hostCapability = declarations.match(/export type HostCapability = ([^;]*);/)[1];
for (const grant of FAMILY) {
  assert(grantsFile.includes(`"${grant}"`), `Host grant ${grant}`);
  const key = grant.replace(/\./g, '_');
  assert(preferences.includes(`plugin_bun_${key}_grant`), `Plugin center switch ${grant}`);
  assert(strings.includes(`text_bun_${key}_grant`) && strings.includes(`summary_bun_${key}_grant`), `Strings of ${grant}`);
  assert(hostCapability.includes(`"${grant}"`), `HostCapability ${grant}`);
}

// Without grants: the family rejects NOT_GRANTED with the missing Bun grants and never reaches the host.
const calls = [];
const denied = await client([], calls);
for (const [name, call, missing] of [
  ['autojs.isRootAvailable', () => denied.autojs.isRootAvailable(), 'autojs.control'],
  ['autojs.setRootMode', () => denied.autojs.setRootMode('root'), 'autojs.manage'],
  ['settings.setEnabled', () => denied.settings.setEnabled('k', true), 'settings.write'],
  ['sysprops.getAll', () => denied.sysprops.getAll(), 'sysprops.control'],
  ['memory.dumpHeap', () => denied.memory.dumpHeap(), 'memory.dump'],
  ['installer.install', () => denied.installer.install('a.apk'), 'installer.mutate'],
  ['engines.all', () => denied.engines.all(), 'engines.control'],
  ['engines.execScriptFile', () => denied.engines.execScriptFile('a.js'), 'engines.exec'],
  ['powerManager.requestIgnoreBatteryOptimizations', () => denied.powerManager.requestIgnoreBatteryOptimizations(), 'device.power'],
  ['notice.launchSettings', () => denied.notifications.launchSettings(), 'notifications.settings'],
  ['plugins.tools.call', () => denied.plugins.tools.call('t'), 'plugin_tools.control'],
]) {
  await assert.rejects(call(), error => error.code === 'NOT_GRANTED' && error.missingCapabilities.includes(missing), `${name} without grants`);
}
assert.deepEqual(calls, [], 'Denied entries must not call the host');

const policy = JSON.parse(read(resolve(root, 'tools/api-coverage/runtime-policy.json')));
assert(policy.bun.namespaceAliases.workManager.includes('tasks'), 'Bun workManager alias tasks');
assert(policy.exclusions.find(rule => rule.id === 'native-memory-watch').columns.includes('bun'), 'memory.watch not-portable for Bun (Q140 (a))');
const hostApi = read(resolve(bun, 'docs/host-api.md'));
for (const name of ['autojs.manage', 'settings.write', 'memory.dump', 'installer.mutate', 'engines.exec', 'plugin_tools.user_granted', 'AUTOJS6_HOST_INFO_FILE', 'myEngine']) {
  assert(hostApi.includes(name), `Bun docs/host-api.md describes ${name}`);
}
console.log(JSON.stringify({members, globals: globals.length, grants: FAMILY.length, routes: Object.fromEntries(['autojs.control', 'installer.control', 'engines.control', 'plugin_tools.control']
  .map(name => [name, sdk.HOST_API_METHODS[name].length])), declarations: 'type-checked', hostApi: true}));
