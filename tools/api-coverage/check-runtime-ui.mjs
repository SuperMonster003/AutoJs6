// Structure check of the Node UI family (api-coverage P3.3): every registered host member of ui (the content view and the
// window members of R7, D-17 / Q164; the internal names and layoutInflater stay excluded by the runtime policy, Q169),
// floaty, web and terminal (Q167 / Q168), the members of the Rhino objects ConsoleConfigurator (Q166), FloatyWindow /
// RawWindow, WebSocket, InjectableWebClient and of the compose objects ComposeNode / ComposeState / ComposeRef (Q104 / Q106)
// has a member in the shared UI factory of node_bridge_sources.cpp (byte-identical with portable-ui-compose.js) and a Node
// declaration; the console controls and input (D-7, Q165), the leveled toasts and the compose session handles are checked in
// their facades. Workers get floaty / web / terminal by their autojs6: names and the family's globals; the host routes the
// family through RuntimeUiInterop / RuntimeUiWindowControls / RuntimeConsoleBridge / RuntimeTerminalBridge with the
// capability terminal never implied (Q167 (a)); HOST-API describes the family. Behaviour is compared with Rhino by the Node
// repository's tools/nodejs/api/test-ui-rhino.js (desktop) and docs/dev/runtime-ui-2026-10-30.md (device).
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), node = resolve(root, '../AutoJs6-Plugin-NodeJs-Runtime');
const read = path => readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
const source = read(resolve(node, 'app/src/main/cpp/node_bridge_sources.cpp'));
const part = (first, last) => {
  const a = source.indexOf(first), z = source.indexOf(last, a);
  assert(a >= 0 && z > a, first.trim());
  return source.slice(a, z);
};
const uiSource = part('  function __autojs6_create_ui(', '  function __autojs6_create_compose(');
const composeSource = part('  function __autojs6_create_compose(', '  function __autojs6_ui_options(');
assert.equal((uiSource + composeSource).trimEnd(), read(resolve(node, 'tools/nodejs/api/portable-ui-compose.js')).trimEnd(),
  'node_bridge_sources.cpp carries portable-ui-compose.js (sync-ui-compose.js)');
const createUi = new Function('return (' + uiSource + ')')(), createCompose = new Function('return (' + composeSource + ')')();

// A host answering the calls that open the handles; members are only inspected.
const view = id => ({__autojs6ViewHandle: id, id});
const ANSWERS = {
  'ui.floatyWindow': () => ({id: 'floaty-1', rootView: view('f1')}), 'ui.floatyRawWindow': () => ({id: 'floaty-2', rootView: view('f2')}),
  'terminal.open': () => ({id: 's1', state: 'running', alive: true, exitCode: null}),
};
const call = async (module, method) => {
  const answer = ANSWERS[`${module}.${method}`];
  return answer ? answer() : module === 'ui' && method === 'drainEvents' ? new Promise(() => {}) : null;
};
const connectWebSocket = async () => { const connection = {on: () => connection, send: async () => {}, close: async () => {}}; return connection; };
const ui = createUi(call), family = ui.__autojs6Family({connectWebSocket, exit() {}}), compose = createCompose(call);
const objects = {
  ui, floaty: family.floaty, web: family.web, terminal: family.terminal,
  ConsoleConfigurator: family.console.build(), FloatyWindow: await family.floaty.window('<frame/>'), RawWindow: await family.floaty.rawWindow('<frame/>'),
  WebSocket: family.web.newWebSocket('wss://check.test'), InjectableWebClient: family.web.newInjectableWebClient(),
  ComposeNode: compose.TextField({}), ComposeState: compose.state(0), ComposeRef: compose.ref(),
};
const session = await family.terminal.open();
for (const name of ['write', 'waitFor', 'transcript', 'isAlive', 'show', 'close']) {
  assert.equal(typeof session[name], 'function', `TerminalSession.${name}`);
  assert.equal(session[name + 'Async'], session[name], `TerminalSession.${name}Async`);
}
// InjectableWebView is the inflated WebView handle with inject (a Proxy over a host view).
assert(uiSource.includes('key === "inject" ? client.inject'), 'newInjectableWebView adds inject');

const types = name => read(resolve(node, `docs/nodejs/types/autojs6-node/${name}.d.ts`));
// An interface body, up to the closing brace at the indentation of its head.
const body = (text, head) => {
  const start = text.indexOf(head);
  assert(start >= 0, head);
  const indent = text.slice(text.lastIndexOf('\n', start) + 1, start).match(/^\s*/)[0];
  return text.slice(start, text.indexOf('\n' + indent + '}', start));
};
const [uiTypes, floatyTypes, webTypes, composeTypes] = [types('ui'), types('floaty'), types('web'), types('compose')];
const windowBase = body(floatyTypes, 'interface WindowBase {');
const declarations = {
  ui: body(uiTypes, 'export interface UiModule {'), floaty: body(floatyTypes, 'export interface FloatyModule {'),
  web: body(webTypes, 'export interface WebModule {'), terminal: body(types('terminal'), 'export interface TerminalModule {'),
  ConsoleConfigurator: body(types('console'), 'export interface ConsoleConfigurator {'),
  FloatyWindow: windowBase + body(floatyTypes, 'export interface FloatyWindow extends WindowBase {'),
  RawWindow: windowBase + body(floatyTypes, 'export interface RawWindow extends WindowBase {'),
  WebSocket: body(webTypes, 'export interface WebSocket {'), InjectableWebClient: body(webTypes, 'export interface InjectableWebClient {'),
  ComposeNode: body(composeTypes, 'interface Node<N extends ComponentName = ComponentName> {'),
  ComposeState: body(composeTypes, 'interface State<T> {'), ComposeRef: body(composeTypes, 'interface Ref {'),
};
const declared = (where, name) => new RegExp(`(^|[^\\w$])(readonly )?${name}\\??(\\(|:|<)`, 'm').test(declarations[where]);
let members = 0;
const check = (where, name) => {
  assert(name in objects[where], `Missing ${where}.${name}`);
  assert(declared(where, name), `Missing declaration of ${where}.${name}`);
  members += 1;
};

// Host members: the module registrations, ui's script prototype (Q18) and the returned objects' members, less the policy's
// Node exclusions; Kotlin getters (getX) answer as their property (x), as the matrix reads them.
const exported = hostExports(), data = JSON.parse(readFileSync(exported.file, 'utf8'));
const policy = JSON.parse(read(resolve(root, 'tools/api-coverage/runtime-policy.json')));
const excluded = new Set(policy.exclusions.filter(rule => rule.columns.includes('node_main')).flatMap(rule => rule.match));
const property = name => /^(get|is)[A-Z]/.test(name) && !(name in Object.prototype) ? name.replace(/^(get|is)(.)/, (_, prefix, first) => first.toLowerCase()) : name;
const named = (where, name) => name in objects[where] ? name : property(name);
for (const module of ['ui', 'floaty', 'web', 'terminal']) {
  const names = [...data.records.filter(row => row.module === module).map(row => row.member),
    ...(data.prototypes.find(item => item.module === module)?.members || []).map(item => typeof item === 'string' ? item : item.name)];
  assert(names.length, `No host records of ${module}`);
  for (const name of new Set(names)) if (!excluded.has(`${module}.${name}`)) check(module, name);
}
for (const object of ['ConsoleConfigurator', 'FloatyWindow', 'RawWindow', 'WebSocket', 'InjectableWebClient', 'ComposeNode', 'ComposeState', 'ComposeRef']) {
  const names = (exported.returnedObject(object)?.members || []).map(item => typeof item === 'string' ? item : item.name);
  assert(names.length, `No host members of ${object}`);
  for (const name of names) check(object, object === 'ComposeNode' || object === 'ConsoleConfigurator' ? name : named(object, name));
}
assert.equal(exported.returnedObject('ConsoleConfigurator').members.length, 52, 'ConsoleConfigurator has Rhino\'s 52 members (Q166 (a))');
for (const name of ['ComposeSession', 'ComposeFloatyWindow', 'ComposeState', 'ComposeRef', 'ConsoleConfigurator', 'FloatyWindow', 'RawWindow',
  'WebSocket', 'InjectableWebView', 'InjectableWebClient']) assert(policy.returnedObjects[name]?.node, `returnedObjects ${name}`);
// The compose session handles (mount / floaty) are one frozen literal per session: its keys and the Session declarations.
const handleStart = composeSource.indexOf('const handle = Object.freeze({');
const handle = composeSource.slice(handleStart, composeSource.indexOf('\n      });', handleStart));
const sessionTypes = body(composeTypes, 'interface Session {') + body(composeTypes, 'interface FloatySession extends Session {');
for (const object of ['ComposeSession', 'ComposeFloatyWindow']) {
  for (const item of exported.returnedObject(object)?.members || []) {
    const name = typeof item === 'string' ? item : item.name;
    assert(new RegExp(`(get |[\\s{,])${name}(\\(|:)`).test(handle), `Missing ${object}.${name}`);
    assert(new RegExp(`(^|[^\\w$])(readonly )?${name}\\??(\\(|:|<)`, 'm').test(sessionTypes), `Missing declaration of ${object}.${name}`);
    members += 1;
  }
}

// Facades of the main thread: the console controls and input (Q165 (a)), the leveled toasts, ComposeError.
const consoleFacade = part('  function __autojs6_limited_console(', '\n  }\n');
const consoleTypes = body(types('console'), 'export interface ConsoleModule extends ConsoleInstance {');
const CONSOLE = ['isShowing', 'resetBackgroundAlpha', 'resetContentBackgroundAlpha', 'resetTitleBackgroundAlpha', 'setAvoidStatusBar', 'setColorful',
  'setGravity', 'setInputVisible', 'setTimeFormat', 'setTimeVisible', 'setTouchThrough', 'rawInput', 'input', 'build'];
for (const name of CONSOLE) {
  assert(consoleFacade.includes(`      ${name}: {`), `console.${name}`);
  assert(new RegExp(`(^|[^\\w$])${name}\\(`, 'm').test(consoleTypes), `Missing declaration of console.${name}`);
}
const toastFacade = part('  function __autojs6_limited_toast(', '\n  }\n'), toastTypes = types('toast');
const LEVELS = ['Log', 'Info', 'Warn', 'Error', 'Verbose'];
for (const name of ['dismissAll', ...LEVELS.map(level => 'toast' + level)]) {
  assert(toastFacade.includes(name), `toast.${name}`);
  assert(new RegExp(`(^|[^\\w$])${name}\\(`, 'm').test(body(toastTypes, 'export interface ToastModule {')), `Missing declaration of toast.${name}`);
}
assert.equal(typeof compose.ComposeError, 'function', 'compose.ComposeError');
assert.equal((() => { try { compose.createElement('Nope'); } catch (error) { return error; } })()?.code, 'INVALID_ARGUMENT', 'Argument errors are ComposeError');

// Globals (main threads and Workers): the family's names on the global object, as accessors that keep existing names.
const GLOBALS = ['isUiThread', 'newWebSocket', 'newInjectableWebView', 'newInjectableWebClient', 'ComposeError',
  ...LEVELS.flatMap(level => ['toast' + level, 'toast' + level.toLowerCase()])];
const target = {toastLog: 'kept'}, installed = family.installGlobals(target, {toast: () => ({}), compose: () => compose});
assert.deepEqual(new Set(installed), new Set(GLOBALS.filter(name => name !== 'toastLog')), 'installGlobals installs the family\'s globals');
assert(target.toastLog === 'kept' && target.isUiThread() === false && target.ComposeError === compose.ComposeError, 'installGlobals keeps existing names');
const globalTypes = uiTypes + toastTypes + webTypes + composeTypes;
for (const name of GLOBALS) {
  assert(new RegExp(`^declare (const|function) ${name}[(:]`, 'm').test(globalTypes), `Missing global declaration of ${name}`);
  assert.equal(policy.node.memberAliases[name]?.member, name.replace(/^toast(\w)/, (_, first) => 'toast' + first.toUpperCase()), `memberAliases ${name}`);
}
const globalRows = data.records.filter(row => row.module === '' && GLOBALS.includes(row.member)).map(row => row.member);
assert.deepEqual(new Set(globalRows), new Set(GLOBALS), 'Every global of the family is a host global');
assert(source.includes('__autojs6_system_global_names.concat(__autojs6_ui_family().installGlobals(globalThis'), 'The main thread installs the globals');
assert(source.includes('uiFamily.installGlobals(globalThis'), 'Workers install the globals');
// Workers: the modules by their autojs6: names, the table entries and the toast forwarded to the main thread.
assert(source.includes('"system", "floaty", "web", "terminal",'), 'Worker modules answer only to autojs6:');
const table = source.slice(source.indexOf('hostModuleMethods: {'), source.indexOf('\n          },', source.indexOf('hostModuleMethods: {')));
for (const name of ['floaty', 'web', 'terminal', 'toast']) {
  assert(new RegExp(`(^|[\\s,{])${name}\\s*:\\s*Object\\.keys\\(`).test(table), `Worker table entry ${name}`);
}
for (const name of ['floaty', 'web', 'terminal']) assert(source.includes(`"autojs6:${name}": function() { return __autojs6_ui_family().${name}; }`), `factory autojs6:${name}`);

// Host routes and capabilities (Q167 (a)) in the host, the plugin and the catalog.
const engine = name => read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine', name));
const quoted = text => [...text.matchAll(/"(\w+)"/g)].map(match => match[1]);
// The argument list of the call after `head` (setOf(...), linkedMapOf(...)), up to its balanced closing parenthesis.
const block = (text, head) => {
  const start = text.indexOf('(', text.indexOf(head));
  assert(text.indexOf(head) >= 0 && start > 0, head);
  let depth = 0, end = start;
  for (; end < text.length; end += 1) if (text[end] === '(') depth += 1; else if (text[end] === ')' && --depth === 0) break;
  return text.slice(start + 1, end);
};
const routes = {
  ui: [...quoted(block(engine('RuntimeUiWindowControls.kt'), 'val METHODS = setOf'))],
  console: [...quoted(block(engine('RuntimeConsoleBridge.kt'), 'val METHODS = setOf')), 'rawInput'],
  terminal: [...block(engine('RuntimeTerminalBridge.kt'), 'val ARITY').matchAll(/"(\w+)" to/g)].map(match => match[1]),
  toast: ['dismissAll'],
};
assert(engine('RuntimeUiInterop.kt').includes('"floatyCloseAll" ->'), 'Host route ui.floatyCloseAll');
const catalog = JSON.parse(read(resolve(node, 'app/src/main/assets/nodejs/node-capability-catalog.json')));
const operations = new Map(catalog.bridge.operations.map(item => [`${item.module}.${item.method}`, item.requiredCapabilities]));
const EXPECTED = {
  'ui.floatyWindow': ['ui', 'ui.overlay'], 'ui.floatyRawWindow': ['ui', 'ui.overlay', 'floaty.raw'], 'ui.floatyControl': ['ui', 'ui.overlay'],
  'ui.floatyCloseAll': ['ui'], 'toast.dismissAll': ['toast'],
};
let routeCount = 0;
for (const [module, methods] of Object.entries(routes)) {
  assert(methods.length >= 1, `Host routes of ${module}`);
  for (const method of methods) {
    const key = `${module}.${method}`;
    assert(operations.has(key), `Catalog route ${key}`);
    assert.deepEqual(operations.get(key), EXPECTED[key] || [module], `Catalog capabilities of ${key}`);
    routeCount += 1;
  }
}
assert(routes.terminal.length >= 15 && routes.console.length >= 13, 'terminal and console routes');
const manifest = engine('NodeBridgePermissionManifest.kt');
const pluginManifest = read(resolve(node, 'app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeBridgePermissionManifest.java'));
assert(manifest.includes('const val TERMINAL = "terminal"') && manifest.includes('NodeBridgeModules.TERMINAL -> listOf(TERMINAL)'), 'Host capability terminal');
assert(pluginManifest.includes('TERMINAL = "terminal"'), 'Plugin capability terminal');
assert(catalog.bridge.permissionCapabilities.some(item => (item.id ?? item) === 'terminal'), 'Catalog capability terminal');
// terminal has no parent capability: shell / shell.root never imply it.
const declares = new Function('return (' + part('  function __autojs6_bridge_declares_capability(', '\n  }\n') + '\n  })')();
assert(declares(['terminal'], 'terminal') && !declares(['shell', 'shell.root', 'shell.shizuku'], 'terminal'), 'terminal never implied');
assert(part('  function __autojs6_bridge_default_permissions(', '\n  }\n').includes('if (moduleName === "terminal") return ["terminal"];'), 'terminal calls request terminal');

const hostApi = read(resolve(node, 'docs/HOST-API.md'));
assert(hostApi.includes('## UI 族的 Rhino 对照'), 'HOST-API section of the UI family');
console.log(JSON.stringify({hostMembers: members, consoleFacade: CONSOLE.length, globals: GLOBALS.length, hostRoutes: routeCount, capabilities: ['terminal'], hostApi: true}));
process.exit(0);
