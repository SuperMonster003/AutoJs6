// Structure check of the Bun UI family (api-coverage P4.3): the Bun host client carries the Node runtime's shared UI facade
// (portable-ui-compose.js) unchanged and its declarations match Node's (sync-ui-family.mjs --check); every registered host
// member of ui, floaty, web and terminal is a member of the client and declared in host-client.d.mts, which type-checks; the
// members of Rhino's FloatyWindow / RawWindow / WebSocket / InjectableWebView / InjectableWebClient / ConsoleConfigurator and of
// compose's node, session, floating window, state and ref are declared on the handle types (and present on console.build's
// configurator), and the family's Rhino globals are members of
// host.global; the client's floaty / activity window method lists equal the host broker's, every terminal method the facade
// calls is a host terminal route; terminal.control, floaty.control, floaty.raw and floaty.touch (Q3 (a) / Q173 (a)) are host
// grants with plugin center switches; and without grants the family rejects NOT_GRANTED before the host.
// The values are compared with Rhino on a device by tools/api-coverage/device_bun_ui.py.
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
execFileSync(process.execPath, [resolve(bun, 'tools/host-client/sync-ui-family.mjs'), '--check', '--node-root', node], {stdio: 'inherit'});
const clientFile = resolve(bun, 'app/src/main/assets/autojs6/host-client.mjs'), clientText = read(clientFile);
const portable = read(resolve(node, 'tools/nodejs/api/portable-ui-compose.js'));
assert(clientText.includes(portable), 'The Bun UI facade differs from Node portable-ui-compose.js (run Node tools/nodejs/api/sync-ui-compose.js)');
const directory = mkdtempSync(join(tmpdir(), 'aj6-bun-ui-family-'));
copyFileSync(clientFile, join(directory, 'host-client.mjs'));
process.on('exit', () => rmSync(directory, {recursive: true, force: true}));
const sdk = await import(pathToFileURL(join(directory, 'host-client.mjs')));

const FAMILY = ['ui.layout', 'ui.compose', 'ui.toast', 'console.control', 'clipboard.control', 'floaty.control', 'floaty.raw', 'floaty.touch', 'terminal.control'];
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
const namespace = name => {
  const first = declarations.indexOf(`export namespace ${name} {`);
  assert(first >= 0, `host-client.d.mts declares ${name}`);
  return declarations.slice(first, declarations.indexOf('\n}\n', first));
};
const block = (text, name, indent = '    ') => {
  const first = text.search(new RegExp(`(interface|type) ${name}[ <=]`));
  assert(first >= 0, `host-client.d.mts declares ${name}`);
  return text.slice(first, text.indexOf(`\n${indent}}`, first));
};
// A member declared as a method, a property or a getter.
const declared = (name, text) => new RegExp(`(^|[\\s;{])(readonly\\s+)?(get\\s+)?${name}\\??(\\(|:|<)`, 'm').test(text);
const modules = {
  ui: [host.ui, namespace('RuntimeUi')],
  floaty: [host.floaty, block(namespace('RuntimeFloaty'), 'FloatyModule')],
  web: [host.web, block(namespace('RuntimeWeb'), 'WebModule')],
  terminal: [host.terminal, block(namespace('RuntimeTerminal'), 'TerminalModule')],
};
// The names runtime-policy.json excludes for Bun: compiler and Rhino internals (internal-exports, Q13) and Rhino's Java
// LayoutInflater (java-prototype-layout-inflater, Q169 (a)).
const policy = JSON.parse(read(resolve(root, 'tools/api-coverage/runtime-policy.json')));
const excluded = new Set(policy.exclusions.filter(rule => rule.columns.includes('bun')).flatMap(rule => rule.match.filter(name => !name.includes('*'))));
assert(excluded.has('ui.layoutInflater') && excluded.has('ui.__inflate__'), 'runtime-policy.json exclusions');
const exported = hostExports(), data = JSON.parse(readFileSync(exported.file, 'utf8'));
const rows = data.records.filter(row => row.ownerModule in modules)
  .map(row => ({id: row.api, owner: row.ownerModule, member: row.member, kind: row.kind}))
  .concat(data.prototypes.filter(item => item.module in modules).flatMap(item => (item.members || [])
    .filter(member => !member.excluded && !member.shadowed).map(member => ({id: `${item.module}.${member.name}`, owner: item.module, member: member.name, kind: 'Prototype'}))));
const checked = Object.fromEntries(Object.keys(modules).map(name => [name, 0]));
const seen = new Set();
for (const row of rows) {
  if (seen.has(row.id) || excluded.has(row.id)) continue;
  seen.add(row.id);
  const [value, types] = modules[row.owner];
  // terminal.npm.run and the other nested members: the object, then its member. Prototype members (ui.bindingContext is an
  // accessor in the facade) only need to be present.
  const path = row.member.split('.'), owner = path.slice(0, -1).reduce((object, key) => object?.[key], value), member = path.at(-1);
  if (row.kind === 'Functions') assert.equal(typeof owner?.[member], 'function', `Missing host.${row.owner}.${row.member} (${row.id})`);
  else assert(owner && member in owner, `Missing host.${row.owner}.${row.member} (${row.id})`);
  assert(declared(member, types), `Missing declaration of ${row.owner}.${row.member} (${row.id})`);
  checked[row.owner] += 1;
}
assert(checked.ui >= 30 && checked.floaty === 8 && checked.web >= 4 && checked.terminal >= 20, `family rows ${JSON.stringify(checked)}`);
assert.equal(typeof host.console.build, 'function', 'host.console.build');
assert(declared('build', block(declarations, 'ConsoleBuildApi', '')), 'Missing declaration of console.build');

// Returned objects: Rhino's handle members on the facade's handle types; console.build's configurator carries them all.
const floatyTypes = namespace('RuntimeFloaty'), windowBase = block(floatyTypes, 'WindowBase');
const webTypes = namespace('RuntimeWeb'), configuratorTypes = block(namespace('RuntimeConsole'), 'ConsoleConfigurator', '  ');
const handles = {FloatyWindow: windowBase + block(floatyTypes, 'FloatyWindow'), RawWindow: windowBase + block(floatyTypes, 'RawWindow'),
  WebSocket: block(webTypes, 'WebSocket'), InjectableWebView: block(webTypes, 'InjectableWebView'),
  InjectableWebClient: block(webTypes, 'InjectableWebClient'), ConsoleConfigurator: configuratorTypes};
// Compose's handles (Q106 (a)): the node with its own members and the catalogue accessors (Q104), the session, the floating
// window (a session too), state and ref.
const composeTypes = namespace('RuntimeCompose'), session = block(composeTypes, 'Session');
Object.assign(handles, {ComposeNode: block(composeTypes, 'Node'), ComposeSession: session, ComposeFloatyWindow: session + block(composeTypes, 'FloatySession'),
  ComposeState: block(composeTypes, 'State'), ComposeRef: block(composeTypes, 'Ref')});
const configurator = host.console.build({});
const handleMembers = Object.fromEntries(Object.keys(handles).map(name => [name, 0]));
for (const item of data.returnedObjects.filter(item => item.object in handles)) {
  for (const member of (item.members || []).map(member => member.name ?? member)) {
    assert(declared(member, handles[item.object]), `Missing declaration of ${item.object}#${member}`);
    if (item.object === 'ConsoleConfigurator') assert(member in configurator, `Missing ConsoleConfigurator#${member}`);
    handleMembers[item.object] += 1;
  }
}
assert(handleMembers.ConsoleConfigurator >= 50 && handleMembers.FloatyWindow >= 10 && handleMembers.WebSocket >= 20 && handleMembers.ComposeNode >= 28
  && handleMembers.ComposeSession >= 7, `handle members ${JSON.stringify(handleMembers)}`);

// The family's Rhino globals: members of host.global (UiFamilyGlobals), installed with the others.
const globals = block(declarations, 'UiFamilyGlobals', '');
const installed = {};
const names = host.global.install(installed);
for (const name of ['newWebSocket', 'newInjectableWebView', 'newInjectableWebClient', 'ComposeError']) {
  assert.equal(typeof host.global[name], 'function', `Missing host.global.${name}`);
  assert(declared(name, globals), `Missing declaration of global ${name}`);
  assert(names.includes(name) && installed[name] === host.global[name], `host.global.install() installs ${name}`);
}
assert.deepEqual(hostCalls, [], 'Reading the family must not call the host');

// Host routes: the client's window method lists equal the broker's; the facade's terminal methods are host terminal routes.
const engine = name => read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine', name));
const broker = engine('BunHostCapabilityBroker.kt'), bridge = engine('BunAutomationBridge.kt');
const strings = body => [...body.matchAll(/["'](\w+)["']/g)].map(match => match[1]).sort();
const list = (text, pattern) => { const body = text.match(pattern)?.[1]; assert(body, `${pattern}`); return strings(body); };
assert.deepEqual(list(clientText, /const FLOATY_METHODS = \[([^\]]*)\]/), list(broker, /val FLOATY_WINDOW_METHODS = setOf\(([^)]*)\)/), 'floaty window methods: client / host drift');
assert.deepEqual(list(clientText, /ACTIVITY_METHODS = \[([^\]]*)\]/), list(broker, /val ACTIVITY_WINDOW_METHODS = setOf\(([^)]*)\)/), 'activity window methods: client / host drift');
const terminalRoutes = [...engine('RuntimeTerminalBridge.kt').match(/val ARITY: Map<String, IntRange> = linkedMapOf\(([\s\S]*?)\n\s*\)/)[1]
  .matchAll(/"(\w+)" to/g)].map(match => match[1]);
const terminalMethods = [...new Set([...portable.matchAll(/terminalCall\("(\w+)"/g)].map(match => match[1]))];
assert(terminalMethods.length >= 14, 'facade terminal methods');
for (const method of terminalMethods) assert(terminalRoutes.includes(method), `The facade calls terminal.${method}, which the host does not route`);
for (const pattern of [/Grants\.TERMINAL_CONTROL to NodeBridgeModules\.terminalMethods/, /Grants\.TERMINAL_CONTROL to NodeBridgeModules\.TERMINAL/,
  /Grants\.FLOATY_RAW in grants\)/, /Grants\.FLOATY_TOUCH in grants\)/]) assert(pattern.test(bridge), `BunAutomationBridge ${pattern}`);
for (const pattern of [/BunHostCapabilityGrants\.FLOATY_CONTROL ->/, /if \(method in FLOATY_PERMISSION_METHODS\) awaitImageCall\(NodeBridgeModules\.UI_OVERLAY/, /NodeBridgeModules\.uiMethods - FLOATY_WINDOW_METHODS/,
  /NodeBridgeModules\.uiMethods - ACTIVITY_WINDOW_METHODS/]) assert(pattern.test(broker), `BunHostCapabilityBroker ${pattern}`);
const grantsFile = read(resolve(root, 'app/src/main/java/org/autojs/autojs/core/plugin/bun/BunHostCapabilityGrants.kt'));
const preferences = read(resolve(root, 'app/src/main/res/xml/fragment_plugin_settings_preferences_official.xml'));
const resources = read(resolve(root, 'app/src/main/res/values/strings_bun_ui_family.xml'));
const hostCapability = declarations.match(/export type HostCapability = ([^;]*);/)[1];
for (const grant of ['terminal.control', 'floaty.control', 'floaty.raw', 'floaty.touch']) {
  const key = grant.replace(/\./g, '_');
  assert(grantsFile.includes(`"${grant}"`), `Host grant ${grant}`);
  assert(preferences.includes(`plugin_bun_${key}_grant`), `Plugin center switch ${grant}`);
  assert(resources.includes(`text_bun_${key}_grant`) && resources.includes(`summary_bun_${key}_grant`), `Strings of ${grant}`);
  assert(hostCapability.includes(`"${grant}"`), `HostCapability ${grant}`);
}

// Without grants: the family rejects NOT_GRANTED with the missing Bun grants and never reaches the host.
const calls = [];
const missing = error => error.missingCapabilities ?? error.cause?.missingCapabilities ?? [];
const denied = await client([], calls), floatyOnly = await client(['floaty.control'], calls);
for (const [name, call, grant] of [
  ['ui.layout', () => denied.ui.layout('<frame/>'), 'ui.layout'],
  ['ui.getStatusBarHeight', () => denied.ui.getStatusBarHeight(), 'ui.layout'],
  ['floaty.window', () => denied.floaty.window('<frame/>'), 'floaty.control'],
  ['floaty.closeAll', () => denied.floaty.closeAll(), 'floaty.control'],
  ['floaty.hasPermission', () => denied.floaty.hasPermission(), 'floaty.control'],
  ['floaty.rawWindow', () => floatyOnly.floaty.rawWindow('<frame/>'), 'floaty.raw'],
  ['floaty.getClip', () => floatyOnly.floaty.getClip(), 'clipboard.control'],
  ['web.newInjectableWebView', () => denied.web.newInjectableWebView(), 'ui.layout'],
  ['terminal.state', () => denied.terminal.state(), 'terminal.control'],
  ['terminal.open', () => denied.terminal.open(), 'terminal.control'],
  ['console.build().show', () => denied.console.build({title: 'denied'}).show(), 'console.control'],
]) {
  await assert.rejects(async () => call(), error => (error.code === 'NOT_GRANTED' || error.cause?.code === 'NOT_GRANTED') && missing(error).includes(grant),
    `${name} without grants`);
}
assert.deepEqual(calls, [], 'Denied entries must not call the host');

const hostApi = read(resolve(bun, 'docs/host-api.md'));
for (const name of ['terminal.control', 'floaty.control', 'floaty.raw', 'floaty.touch', 'Rhino 的 UI 族', 'UiFamilyGlobals', 'sync-ui-family.mjs']) {
  assert(hostApi.includes(name), `Bun docs/host-api.md describes ${name}`);
}
console.log(JSON.stringify({members: checked, handles: handleMembers, routes: {terminal: terminalMethods.length, floaty: list(clientText, /const FLOATY_METHODS = \[([^\]]*)\]/).length},
  declarations: 'type-checked', hostApi: true}));
