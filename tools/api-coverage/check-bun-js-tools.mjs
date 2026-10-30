// Structure check of the Bun JS tools (api-coverage P4.2a): the Bun host client carries the Node runtime's shared JS
// tools core unchanged (sync-js-tools.mjs --check), every registered host member of util (with its submodules),
// species, s13n, cvt / fmt bytes, jsox, Mathx / Arrayx / Numberx, the global data functions and the pinyin constants
// is a member of the client and declared in host-client.d.mts, the pinyin constants equal the Node runtime's, and
// docs/host-api.md describes the namespaces. The values are compared with Rhino by the Bun repository's
// tools/host-client/js-tools.test.mjs (desktop) and this repository's tools/api-coverage/device_bun_js_tools.py (device).
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const bun = resolve(workspace, 'AutoJs6-Plugin-Bun-Runtime'), node = resolve(workspace, 'AutoJs6-Plugin-NodeJs-Runtime');
const read = path => readFileSync(path, 'utf8');
execFileSync(process.execPath, [resolve(bun, 'tools/host-client/sync-js-tools.mjs'), '--check', '--node-root', node], {stdio: 'inherit'});
const {createHostClient} = await import(pathToFileURL(resolve(bun, 'app/src/main/assets/autojs6/host-client.mjs')));
// A client without any grant: the JS tools are local, so building them must not reach the host.
const host = await createHostClient({socket: 'coverage', fetch: async url => {
  assert(url.endsWith('/info'), 'Coverage inspection must not call the host');
  return Response.json({ok: true, bridgeVersion: 1, capabilities: [], limits: {}});
}});
const exported = hostExports();
// util.unwrapJavaObject stays not-portable (java-prototype). Of the library getters of the global object, axios is the
// embedded host axios (Q64 (b), P4.2b); dayjs / pangu / cheerio / i18n follow the shared core (Q61). The files and data
// family adds Color and open (P4.2b, checked by check-bun-files-data.mjs).
const GLOBALS = ['random', 'randomInt', 'randomFloat', 'isInteger', 'isPrimitive', 'isObject', 'isReference', 'isBigInt', 'isEmptyObject', 'isObjectSpecies', 'TODO'];
const LIBRARIES =['axios', 'dayjs', 'pangu', 'cheerio', 'i18n', 'Color', 'open'];
// host.global also carries the global system functions of the system information family (P4.1b), which
// check-bun-system.mjs checks.
const SYSTEM_GLOBALS = ['currentPackage', 'currentActivity', 'currentComponent', 'waitForActivity', 'waitForPackage', 'exit', 'stop', 'isRunning',
  'isStopped', 'isShuttingDown', 'notStopped', 'requiresApi', 'requiresAutojsVersion', 'setScreenMetrics', 'getClip', 'setClip', 'WIDTH', 'HEIGHT', 'isAutoJs6'];
// And the globals of the UI family (P4.3, checked by check-bun-ui-family.mjs) and of the automation family (P4.4, checked
// by check-bun-automation-family.mjs).
const UI_GLOBALS = ['isUiThread', 'toastLog', 'toastVerbose', 'toastInfo', 'toastWarn', 'toastError', 'toastlog', 'toastverbose', 'toastinfo', 'toastwarn',
  'toasterror', 'newWebSocket', 'newInjectableWebView', 'newInjectableWebClient', 'ComposeError'];
const AUTOMATION_GLOBALS = ['back', 'home', 'recents', 'notifications', 'quickSettings', 'powerDialog', 'splitScreen', 'click', 'longClick', 'press', 'swipe',
  'gesture', 'gestureAsync', 'gestures', 'gesturesAsync', 'scrollUp', 'scrollDown', 'input', 'setText', 'pickup', 'detect', 'existsAll', 'existsOne', 'wait',
  'sleep', 'keepAlive', 'loop', 'retry', 'sync'];
const groups = [
  ['util', host.util, ['unwrapJavaObject']], ['util.inspect', host.util.inspect], ['util.java', host.util.java], ['util.morseCode', host.util.morseCode],
  ['util.version', host.util.version], ['util.versionCodes', host.util.versionCodes], ['species', host.species], ['s13n', host.s13n],
  ['jsox', host.jsox], ['Mathx', host.jsox.mathx], ['Arrayx', host.jsox.arrayx], ['Numberx', host.jsox.numberx],
  ['cvt.bytes', host.cvt.bytes], ['fmt.bytes', host.fmt.bytes],
];
const declarations = read(resolve(bun, 'app/src/main/assets/autojs6/host-client.d.mts'));
const section = (begin, end) => {
  const first = declarations.indexOf(begin), last = declarations.indexOf(end, first);
  assert(first >= 0 && last > first, `host-client.d.mts section ${begin}`);
  return declarations.slice(first, last);
};
const types = section('BEGIN GENERATED JS TOOLS TYPES', 'END GENERATED JS TOOLS TYPES');
// A member declared with quotes ("var") counts as well.
const declared = (name, text = types) => new RegExp(`\\b${name.replace(/\$/g, '\\$')}"?\\??(\\(|:|<)`).test(text);
let members = 0;
for (const [module, value, skipped = []] of groups) {
  for (const kind of ['Functions', 'Properties', 'Getters']) {
    let names;
    try { names = exported.names(module, kind); } catch { continue; }
    for (const name of names.filter(item => !skipped.includes(item))) {
      if (kind === 'Functions') assert.equal(typeof value[name], 'function', `Missing ${module}.${name}`);
      else assert.notEqual(value[name], undefined, `Missing ${module}.${name}`);
      assert(declared(name), `Missing declaration of ${module}.${name}`);
      members += 1;
    }
  }
}
assert.equal(typeof host.species, 'function', 'species(o)');
assert.equal(typeof host.util.inspect, 'function', 'util.inspect(o)');
assert.equal(typeof host.util.morseCode, 'function', 'util.morseCode(source)');
for (const name of GLOBALS) {
  assert.equal(typeof host.global[name], 'function', `Missing global ${name}`);
  assert(declared(name), `Missing declaration of global ${name}`);
}
assert.deepEqual(Object.keys(host.global).sort(), [...GLOBALS, ...LIBRARIES, ...SYSTEM_GLOBALS, ...UI_GLOBALS, ...AUTOMATION_GLOBALS].sort(), 'Bun global data functions and library getters (Q61 / Q64)');
for (const name of LIBRARIES) assert(declared(name), `Missing declaration of global ${name}`);
assert.equal(host.global.axios.VERSION, '1.1.2', 'global.axios is the host axios library');
for (const [client, name] of [['util', 'RuntimeUtil'], ['species', 'RuntimeSpecies'], ['global', 'RuntimeGlobal'], ['s13n', 'RuntimeS13n'], ['cvt', 'RuntimeConverter'], ['fmt', 'RuntimeFormatter'], ['jsox', 'RuntimeJsox']]) {
  assert(new RegExp(`readonly ${client}: ${name}\\.`).test(declarations), `HostClient.${client} is declared as ${name}`);
}
// The pinyin constants: every registered one, with the numbers of the Node runtime's pinyin module (both runtimes
// convert through the same host pinyin bridge).
const pinyinTypes = section('interface PinyinApi', '\n}'), nodePinyin = read(resolve(node, 'docs/nodejs/types/autojs6-node/pinyin.d.ts'));
const constants = exported.names('pinyin', 'Properties');
for (const name of constants) {
  const expected = new RegExp(`readonly ${name}: (\\d+);`).exec(nodePinyin);
  assert(expected, `Node pinyin declares ${name}`);
  assert.equal(host.pinyin[name], Number(expected[1]), `pinyin.${name}`);
  assert(new RegExp(`readonly ${name}: ${expected[1]};`).test(pinyinTypes), `Bun PinyinApi declares ${name}: ${expected[1]}`);
}
const hostApi = read(resolve(bun, 'docs/host-api.md'));
for (const name of ['host.util', 'host.species', 'host.global', 'host.s13n', 'host.cvt', 'host.fmt', 'host.jsox', 'device-info.json', 'STYLE_TONE']) {
  assert(hostApi.includes(name), `Bun docs/host-api.md describes ${name}`);
}
console.log(JSON.stringify({hostMembers: members, globals: GLOBALS.length, libraries: LIBRARIES.length, pinyinConstants: constants.length, coreInSync: true, hostApi: true}));
