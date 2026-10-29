// Structure check of the Bun app / shell / shizuku family (api-coverage P4.1a): the Bun host client carries the Node
// runtime's shared app and shell factories unchanged (sync-automation.mjs / sync-shell.mjs --check); every registered
// host member of app, shell and shizuku (module functions, getters and properties, their global placements such as
// Home / Tap / kill, the shizuku prototype members of Q18) and every ShellProcess member is a member of the client
// (host.app / host.shell / host.shizuku, a spawned process) and declared in host-client.d.mts; the app.control /
// shell.control method tables equal the host's BunAutomationBridge white lists and are real Node provider routes; and
// without grants the privileged entries reject NOT_GRANTED before reaching the host. The values are compared with
// Rhino on a device by tools/api-coverage/device_bun_app_shell.py.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const bun = resolve(workspace, 'AutoJs6-Plugin-Bun-Runtime'), node = resolve(workspace, 'AutoJs6-Plugin-NodeJs-Runtime');
const read = path => readFileSync(path, 'utf8');
for (const script of ['sync-automation.mjs', 'sync-shell.mjs']) {
  execFileSync(process.execPath, [resolve(bun, 'tools/host-client', script), '--check', '--node-root', node], {stdio: 'inherit'});
}
const sdk = await import(pathToFileURL(resolve(bun, 'app/src/main/assets/autojs6/host-client.mjs')));
// app.autojs follows the autojs module (P3.1b / P4.1b, api-coverage Q93 (2)).
const SKIPPED = new Set(['app.autojs']);

// A client with every grant of the family, whose host answers only the app identity: building the modules and
// reading their members must not reach the host otherwise.
const IDENTITY = {packageName: 'org.autojs.autojs6', versionName: '6.8.0', versionCode: 5935};
async function client(capabilities, calls = []) {
  return sdk.createHostClient({socket: 'coverage', fetch: async (url, init) => {
    if (url.endsWith('/info')) return Response.json({ok: true, bridgeVersion: 1, capabilities, limits: {}});
    const request = JSON.parse(init.body);
    calls.push([url.slice(url.lastIndexOf('/') + 1), request]);
    if (request.action === 'identity') return Response.json({ok: true, capability: 'app.control', result: {value: IDENTITY}});
    throw Error(`Coverage inspection must not call the host: ${url} ${init.body}`);
  }});
}
const host = await client(['app.control', 'shell.control', 'shell.root', 'shell.shizuku']);
const exported = hostExports();
const declarations = read(resolve(bun, 'app/src/main/assets/autojs6/host-client.d.mts'));
const section = (begin, end) => {
  const first = declarations.indexOf(begin), last = declarations.indexOf(end, first);
  assert(first >= 0 && last > first, `host-client.d.mts section ${begin}`);
  return declarations.slice(first, last);
};
const appTypes = section('export namespace RuntimeApp {', 'export namespace RuntimeAccessibility {');
const shellTypes = section('BEGIN GENERATED SHELL TYPES', 'END GENERATED SHELL TYPES');
const block = (text, name) => {
  const first = text.indexOf(`interface ${name} {`);
  assert(first >= 0, `host-client.d.mts declares ${name}`);
  return text.slice(first, text.indexOf('\n    }', first));
};
// A member declared as a method, a property or a getter.
const declared = (name, text) => new RegExp(`(^|[\\s;{])(readonly\\s+)?(get\\s+)?${name}\\??(\\(|:|<)`, 'm').test(text);
const modules = {
  app: [host.app, block(appTypes, 'AppModule')],
  shell: [host.shell, block(shellTypes, 'ShellModule')],
  shizuku: [host.shizuku, block(shellTypes, 'ShizukuModule')],
};
const data = JSON.parse(readFileSync(exported.file, 'utf8'));
const rows = data.records.filter(row => row.ownerModule in modules).map(row => ({id: row.api, owner: row.ownerModule, member: row.member, kind: row.kind}))
  .concat(data.prototypes.filter(item => item.module in modules).flatMap(item => (item.members || [])
    .filter(member => !member.excluded && !member.shadowed).map(member => ({id: `${item.module}.${member.name}`, owner: item.module, member: member.name, kind: member.kind}))));
let checked = 0;
const seen = new Set();
for (const row of rows) {
  if (SKIPPED.has(row.id) || seen.has(row.id)) continue;
  seen.add(row.id);
  const [value, types] = modules[row.owner];
  if (row.kind === 'Functions') assert.equal(typeof value[row.member], 'function', `Missing host.${row.owner}.${row.member} (${row.id})`);
  else assert(row.member in value, `Missing host.${row.owner}.${row.member} (${row.id})`);
  assert(declared(row.member, types), `Missing declaration of ${row.owner}.${row.member} (${row.id})`);
  checked += 1;
}
assert.equal(typeof host.shizuku, 'function', 'shizuku(command) is callable');

// ShellProcess: every member of the Rhino object on a process the client creates (not started: no host call yet) and
// in RuntimeShell.ShellProcess.
const processTypes = block(shellTypes, 'ShellProcess');
const spawned = host.shell.spawn('true');
spawned.ready.catch(() => {});
const shellProcess = exported.returnedObject('ShellProcess');
for (const member of shellProcess.members.filter(item => !item.excluded)) {
  if (member.kind === 'Functions') assert.equal(typeof spawned[member.name], 'function', `Missing ShellProcess#${member.name}`);
  else assert(member.name in spawned, `Missing ShellProcess#${member.name}`);
  assert(declared(member.name, processTypes), `Missing declaration of ShellProcess#${member.name}`);
  checked += 1;
}

// Method tables: client == host white list, and every route is a Node provider route.
const bridge = read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine/BunAutomationBridge.kt'));
const protocol = read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt'));
const names = text => [...text.matchAll(/"(\w+)"/g)].map(match => match[1]).sort();
for (const [capability, key, module] of [['app.control', 'APP_CONTROL', 'app'], ['shell.control', 'SHELL_CONTROL', 'shell']]) {
  const allowed = names(bridge.match(new RegExp(`Grants\\.${key} to setOf\\(([^)]*)\\)`))[1]);
  assert.deepEqual([...sdk.HOST_API_METHODS[capability]].sort(), allowed, `${capability}: client / BunAutomationBridge drift`);
  const routes = names(protocol.match(new RegExp(`val ${module}Methods = setOf\\(([\\s\\S]*?)\\)`))[1]);
  assert(allowed.every(name => routes.includes(name)), `${capability} must use real Node provider routes`);
}

// Without grants: the privileged and shell-backed entries reject NOT_GRANTED and never reach the host.
const calls = [];
const denied = await client([], calls);
for (const [name, call] of [
  ['app.kill', () => denied.app.kill('com.example')], ['app.launchDual', () => denied.app.launchDual('com.example')],
  ['shell.currentPackage', () => denied.shell.currentPackage()], ['shell.execCommand', () => denied.shell.execCommand('true')],
  ['Home', () => denied.shell.Home()], ['shizuku.execCommand', () => denied.shizuku.execCommand('true')],
]) {
  await assert.rejects(call(), {code: 'NOT_GRANTED'}, `${name} without grants`);
}
assert.deepEqual(calls, [], 'Denied entries must not call the host');

const hostApi = read(resolve(bun, 'docs/host-api.md'));
for (const name of ['currentPackage', 'execCommand', 'fromIntent', 'isRootAvailable', 'getApkInfo', 'getInstalledPackages', 'launchDual', 'killDual', 'isAlive', 'getInputStream']) {
  assert(hostApi.includes(name), `Bun docs/host-api.md describes ${name}`);
}
console.log(JSON.stringify({members: checked, skipped: [...SKIPPED], routes: {app: sdk.HOST_API_METHODS['app.control'].length, shell: sdk.HOST_API_METHODS['shell.control'].length}, hostApi: true}));
