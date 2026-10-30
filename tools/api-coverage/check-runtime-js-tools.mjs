// Structure check of the Node JS tools core (api-coverage P3.2a): every registered host member of util (with its
// submodules), species, s13n, cvt / fmt bytes, jsox, Mathx / Arrayx / Numberx and the global data functions has a
// member in the shared core of node_bridge_sources.cpp, a Node declaration and a Worker table entry, and HOST-API
// lists the modules as local. The values are compared with Rhino by the Node repository's
// tools/nodejs/api/test-js-tools.js (desktop) and tools/nodejs/api/js-tools/device_js_tools.py (device).
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const node = resolve(workspace, 'AutoJs6-Plugin-NodeJs-Runtime');
const read = path => readFileSync(path, 'utf8');
const source = read(resolve(node, 'app/src/main/cpp/node_bridge_sources.cpp'));
const first = source.indexOf('  // Shared JS tools core.'), last = source.indexOf('  // End shared JS tools core.', first);
assert(first >= 0 && last > first, 'Shared JS tools core section');
const create = new Function(source.slice(first, last) + ';return __autojs6_create_js_tools;')();
const host = {device: {sdkInt: 33, density: 2, densityDpi: 320, scaledDensity: 2}, log() {}, warn() {}, java() { throw Error('Coverage inspection must not call the host'); }};
const main = create({...host, axios: () => ({})}), worker = create(host);
const exported = hostExports();
// util.unwrapJavaObject stays not-portable (java-prototype). The global object also has the Q61 libraries and Rhino's
// global open / Color (P3.2b, check-runtime-files-data.mjs).
const GLOBALS = ['random', 'randomInt', 'randomFloat', 'isInteger', 'isPrimitive', 'isObject', 'isReference', 'isBigInt', 'isEmptyObject', 'isObjectSpecies', 'TODO'];
const LATER =['cheerio', 'dayjs', 'i18n', 'pangu', 'open', 'Color'];
const groups = [
  ['util', main.util, ['unwrapJavaObject']], ['util.inspect', main.util.inspect], ['util.java', main.util.java], ['util.morseCode', main.util.morseCode],
  ['util.version', main.util.version], ['util.versionCodes', main.util.versionCodes], ['species', main.species], ['s13n', main.s13n],
  ['jsox', main.jsox], ['Mathx', main['jsox.mathx']], ['Arrayx', main['jsox.arrayx']], ['Numberx', main['jsox.numberx']],
  ['cvt.bytes', main.converter.bytes], ['fmt.bytes', main.formatter.bytes],
];
const types = ['util', 'species', 'global', 's13n', 'jsox', 'converter', 'formatter'].map(name => read(resolve(node, `docs/nodejs/types/autojs6-node/${name}.d.ts`))).join('\n');
// A member name may be quoted in the declaration ("var"(...values)).
const declared = name => new RegExp(`(^|[^\\w$])["']?${name.replace(/\$/g, '\\$')}["']?\\??(\\(|:|<)`).test(types);
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
assert.equal(typeof main.species, 'function', 'species(o)');
assert.equal(typeof main.util.inspect, 'function', 'util.inspect(o)');
assert.equal(typeof main.util.morseCode, 'function', 'util.morseCode(source)');
for (const name of GLOBALS) {
  assert.equal(typeof main.global[name], 'function', `Missing global ${name}`);
  assert(declared(name), `Missing declaration of global ${name}`);
}
assert.equal(typeof main.global.axios, 'object', 'Main thread axios getter');
assert.deepEqual(Object.keys(worker.global).sort(), [...GLOBALS, ...LATER].sort(), 'Worker global object (its axios is added by the Worker host modules)');
const table = source.slice(source.indexOf('hostModuleMethods: {'), source.indexOf('hostModuleConstants: {'));
for (const name of ['util', 'species', 's13n', 'formatter', 'fmt', 'converter', 'cvt', 'jsox', '"jsox.mathx"', '"jsox.arrayx"', '"jsox.numberx"']) {
  assert(new RegExp(`(^|[\\s,{])${name.replace(/\./g, '\\.')}\\s*:`).test(table), `Worker table entry ${name}`);
}
const workerGlobals = JSON.parse(/global: (\[[^\]]*\])/.exec(table)[1]);
assert.deepEqual([...workerGlobals].sort(), [...GLOBALS, 'axios', ...LATER].sort(), 'Worker table global list');
const hostApi = read(resolve(node, 'docs/HOST-API.md'));
const local = hostApi.split('## 四.')[1].split('由插件本地实现')[0];
for (const name of ['autojs6:util', 'species', 'autojs6:global', 's13n', 'jsox']) assert(local.includes('`' + name + '`'), `HOST-API section 4 lists ${name}`);
console.log(JSON.stringify({hostMembers: members, globals: GLOBALS.length, workerTable: true, hostApiLocal: true, mainAxios: true}));
