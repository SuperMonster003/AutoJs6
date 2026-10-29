// Structure check of the Node app / shell / shizuku family (api-coverage P3.1a): every registered host member of app,
// shell and shizuku, the global functions they own (launchDual, Home, KeyCode, ...), the Q18 shizuku prototype rows and
// the ShellProcess members (Q31) have a member in the shared factories of node_bridge_sources.cpp and a Node
// declaration; Workers build app from the same factory; the host routes the read-only app methods of D-4 (Q89); HOST-API
// describes the family. Behaviour is compared with Rhino by the Node repository's tools/nodejs/api/test-app-shell-rhino.js
// (desktop) and docs/dev/runtime-app-shell-2026-10-29.md (device).
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {EventEmitter} from 'node:events';
import * as streams from 'node:stream';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), node = resolve(root, '../AutoJs6-Plugin-NodeJs-Runtime');
const read = path => readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
const source = read(resolve(node, 'app/src/main/cpp/node_bridge_sources.cpp'));
const part = (first, last) => {
  const a = source.indexOf(first), z = source.indexOf(last, a);
  assert(a >= 0 && z > a, first.trim());
  return source.slice(a, z);
};
const createShell = new Function('return (' + part('  function __autojs6_create_shell(', '  // Main-thread shell facade;') + ')')();
const createApp = new Function('return (' + part('  function __autojs6_create_app(', '\n  function __autojs6_limited_app() {') + ')')();
// Members are only inspected; a spawned process exits at once.
const call = async (module, method) => method === 'spawn' ? {id: 'p1', pid: 1, mode: 'sh'} : method === 'drain' ? [{type: 'exit', code: 0}] : null;
const {shell, shizuku} = createShell(call, EventEmitter, streams, Buffer, () => [], undefined, () => '', () => ({width: 0, height: 0}));
const app = createApp(call, () => ({packageName: 'org.autojs.autojs6', versionName: '6.8.0', versionCode: 1}), message => new Error(message),
  () => undefined, () => ({}), () => shell, () => []);
const process = shell.spawn('true');
await process.waitFor();

const types = name => read(resolve(node, `docs/nodejs/types/autojs6-node/${name}.d.ts`));
const body = (text, head) => {
  const start = text.indexOf(head);
  assert(start >= 0, head);
  return text.slice(start, text.indexOf('\n    }', start));
};
const declarations = {
  app: body(types('app'), 'interface AppModule {'), shell: body(types('shell'), 'interface ShellModule {'),
  shizuku: body(types('shell'), 'interface ShizukuModule {'), ShellProcess: body(types('shell'), 'interface ShellProcess {'),
};
const declared = (where, name) => new RegExp(`(^|[^\\w$])(readonly )?${name}\\??(\\(|:)`, 'm').test(declarations[where]);
const exported = hostExports(), records = JSON.parse(readFileSync(exported.file, 'utf8')).records;
// app.autojs follows the autojs module of P3.1b (Q93); the shizuku status functions are Q18 prototype rows.
const SKIPPED = {app: ['autojs']}, PROTOTYPE = {shizuku: ['isInstalled', 'hasService', 'isRunning', 'hasPermission', 'isOperational']};
const objects = {app, shell, shizuku};
let members = 0;
const check = (where, name, kind) => {
  const value = objects[where] ?? process;
  if (kind === 'Functions') assert.equal(typeof value[name], 'function', `Missing ${where}.${name}`);
  else assert(name in value, `Missing ${where}.${name}`);
  assert(declared(where, name), `Missing declaration of ${where}.${name}`);
  members += 1;
};
for (const module of ['app', 'shell', 'shizuku']) {
  for (const kind of ['Functions', 'Properties', 'Getters']) {
    let names;
    try { names = exported.names(module, kind); } catch { continue; }
    for (const name of names.filter(item => !(SKIPPED[module] || []).includes(item))) check(module, name, kind);
  }
  for (const name of PROTOTYPE[module] || []) check(module, name, 'Functions');
}
const globals = records.filter(row => row.module === '' && ['app', 'shell', 'shizuku'].includes(row.ownerModule));
for (const row of globals) check(row.ownerModule, row.member, row.kind);
for (const member of exported.returnedObject('ShellProcess').members) {
  // ShellProcess getters are read-only fields of the Node process.
  check('ShellProcess', member.name, member.kind === 'Functions' ? 'Functions' : 'Getters');
}

// Workers build app from the shared factory and list its members.
assert(/function __autojs6_worker_host_modules\([^)]*, createApp[,)]/.test(source), 'Worker host modules take createApp');
assert(source.includes('__autojs6_create_app.toString()'), 'Worker preload receives the app factory');
assert(source.includes('app:Object.keys(__autojs6_limited_app())'), 'Worker table entry app');
assert(source.includes('appMissing:__autojs6_bridge_missing_capabilities('), 'Worker app capability constants');

// Host routes of the read-only app methods and their capabilities (D-4, Q89).
const engine = name => read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine', name));
const routes = engine('NodeBridgeProtocol.kt').match(/val appMethods = setOf\(([^)]*)\)/)[1];
const manifest = engine('NodeBridgePermissionManifest.kt');
const QUERY = ['resolvePackage', 'getUserProfiles', 'getInstalledPackages', 'getApkInfo', 'getAppByAlias'];
for (const name of [...QUERY, 'sendLocalBroadcastSync']) assert(routes.includes(`"${name}"`), `Host app route ${name}`);
assert(manifest.includes(QUERY.map(name => `"${name}"`).join(', ') + ' -> listOf(APP_QUERY)'), 'app.query for the read-only app methods');
assert(manifest.includes('"sendLocalBroadcastSync" -> listOf(APP_ACTIVITY)'), 'app.activity for sendLocalBroadcastSync');
for (const field of ['shizukuInstalled', 'shizukuHasService', 'shizukuOperational']) {
  assert(engine('RuntimeShellBridge.kt').includes(`"${field}"`), `getAccess field ${field}`);
}

const hostApi = read(resolve(node, 'docs/HOST-API.md'));
assert(hostApi.includes('## 应用与 shell 族的 Rhino 对照'), 'HOST-API section of the app and shell family');
console.log(JSON.stringify({hostMembers: members, globals: globals.length, shellProcess: true, workerApp: true, hostRoutes: QUERY.length + 1, hostApi: true}));
