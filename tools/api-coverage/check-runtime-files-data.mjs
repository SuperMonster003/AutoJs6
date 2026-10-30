// Structure check of the Node files and data family (api-coverage P3.2b): every registered host member of colors,
// files, zip, mime, recorder (timing), storages and mediainfo, the members of Color / Zip / Storage objects and
// EpubBook#toString have an entry in the Node runtime (colors / files / mime / recorder / zip in the shared JS tools
// core of node_bridge_sources.cpp, storages / mediainfo / epub in their bridge facades), a Node declaration and a
// Worker table entry, and autojs6:global has the Rhino global libraries (Q61), open and Color. colors.theme (Q80),
// gallery (Q81) and files.batchRename (Q82) are checked by check-runtime-plugin-family.mjs. The values are compared with
// Rhino by the Node repository's tools/nodejs/api/files-data (unit tests and device_files_data.py).
import assert from 'node:assert/strict';
import fs, {readFileSync} from 'node:fs';
import path, {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const node = resolve(workspace, 'AutoJs6-Plugin-NodeJs-Runtime');
const read = file => readFileSync(file, 'utf8');
const source = read(resolve(node, 'app/src/main/cpp/node_bridge_sources.cpp'));
const first = source.indexOf('  // Shared JS tools core.'), last = source.indexOf('  // End shared JS tools core.', first);
assert(first >= 0 && last > first, 'Shared JS tools core section');
const create = new Function(source.slice(first, last) + ';return __autojs6_create_js_tools;')();
const core = create({device: {sdkInt: 35, density: 2}, log() {}, warn() {}, java() { throw Error('Coverage inspection must not call the host'); },
  fs, path, cwd: () => root, sdcardPath: '/storage/emulated/0', android: () => ({})});
const exported = hostExports();
const types = name => read(resolve(node, `docs/nodejs/types/autojs6-node/${name}.d.ts`));
// A member name may be quoted in the declaration ("var"(...values)).
const declares = (text, name) => new RegExp(`(^|[^\\w$])["']?${name.replace(/\$/g, '\\$')}["']?\\??(\\(|:|<)`).test(text);
const hostNames = module => ['Functions', 'Properties', 'Getters'].flatMap(kind => { try { return exported.names(module, kind); } catch { return []; } });
let members = 0;
// [host module, Node value (core module) or null (bridge facade, declaration only), declaration files, skipped names]
const groups = [
  ['colors', core.colors, ['colors'], ['theme']],
  ['files', core.files, ['files'], ['batchRename']],
  ['zip', core.zip, ['zip'], []],
  ['storages', null, ['storage'], []],
  ['mediainfo', null, ['mediainfo'], []],
];
for (const [module, value, files, skipped] of groups) {
  const text = files.map(types).join('\n');
  for (const name of hostNames(module).filter(item => !skipped.includes(item))) {
    if (value) assert(name in value, `Missing core ${module}.${name}`);
    assert(declares(text, name), `Missing declaration of ${module}.${name}`);
    members += 1;
  }
}
// Members of the host modules' prototypes (Q18): files' Files object, mime's Mime object and the timing Recorder.
const FILES_PROTOTYPE = ['sdcardPath', 'getSdcardPath', 'nonNullPath', 'cwd', 'create', 'createIfNotExists', 'createWithDirs', 'exists', 'ensureDir',
  'read', 'readAssets', 'readBytes', 'write', 'append', 'writeBytes', 'appendBytes', 'copy', 'rename', 'renameWithoutExtension', 'move', 'getExtension',
  'getName', 'getNameWithoutExtension', 'remove', 'removeDir', 'listDir', 'isFile', 'isDir', 'isEmptyDir', 'getHumanReadableSize', 'formatSizeWithUnit',
  'getSimplifiedPath'];
const MIME_PROTOTYPE = ['getMediaType', 'parseMediaType', 'fromFile', 'fromFileOr', 'fromFileOrWildcard'];
const RECORDER_TIMING = ['save', 'load', 'isLessThan', 'isGreaterThan', 'has', 'remove', 'clear'];
for (const [module, value, file, names] of [['files', core.files, 'files', FILES_PROTOTYPE], ['mime', core.mime, 'mime', MIME_PROTOTYPE],
  ['recorder', core.recorder, 'recorder', RECORDER_TIMING]]) {
  for (const name of names) {
    assert(name in value, `Missing core ${module}.${name}`);
    assert(declares(types(file), name), `Missing declaration of ${module}.${name}`);
    members += 1;
  }
}
assert.equal(typeof core.mime, 'function', 'mime(mimeString)');
assert.equal(typeof core.recorder, 'function', 'recorder(key?, ts?)');
assert.equal(typeof core.zip, 'function', 'zip(zipPath)');
// Returned objects through their Node handle types (runtime-policy.json returnedObjects).
const policy = JSON.parse(read(resolve(root, 'tools/api-coverage/runtime-policy.json'))).returnedObjects;
const objects = [['Color', 'colors'], ['Zip', 'zip'], ['Storage', 'storage'], ['EpubBook', 'epub']];
for (const [object, file] of objects) {
  assert(policy[object]?.node, `runtime-policy.json returnedObjects ${object}`);
  const text = types(file), type = policy[object].node.type;
  // The interface body ends at the closing brace indented like its header (epub.d.ts declares Book without export).
  const header = new RegExp(`^( *)(export )?interface ${type} \\{`, 'm').exec(text);
  assert(header, `${file}.d.ts declares ${type}`);
  const body = text.slice(header.index);
  const declared = body.slice(0, body.indexOf(`\n${header[1]}}`));
  const wanted = object === 'EpubBook' ? ['toString'] : exported.returnedObject(object).members.map(item => item.name || item);
  for (const name of wanted) {
    assert(declares(declared, name), `Missing ${type}.${name} (${object}#${name})`);
    members += 1;
  }
}
const color = core.global.Color('#ff8000');
for (const name of exported.returnedObject('Color').members.map(item => item.name || item)) assert(name in color, `Missing core Color#${name}`);
// autojs6:global: the Q61 libraries (getters, not compiled here), Rhino's global open and Color.
for (const name of ['cheerio', 'dayjs', 'i18n', 'pangu']) {
  assert(Object.getOwnPropertyDescriptor(core.global, name)?.get?.__autojs6LazyExport, `autojs6:global ${name} getter`);
}
assert.equal(typeof core.global.open, 'function', 'autojs6:global open');
assert(declares(types('global'), 'open') && declares(types('global'), 'Color'), 'global.d.ts declares open and Color');
const table = source.slice(source.indexOf('hostModuleMethods: {'), source.indexOf('hostModuleConstants: {'));
for (const name of ['colors', 'files', 'zip', 'mime', 'recorder', 'storages', 'storage', 'mediainfo']) {
  assert(new RegExp(`(^|[\\s,{])${name}\\s*:`).test(table), `Worker table entry ${name}`);
}
const workerGlobals = JSON.parse(/global: (\[[^\]]*\])/.exec(table)[1]);
for (const name of ['axios', 'cheerio', 'dayjs', 'i18n', 'pangu', 'open', 'Color']) assert(workerGlobals.includes(name), `Worker global ${name}`);
const hostApi = read(resolve(node, 'docs/HOST-API.md'));
assert(hostApi.split('## 四.')[1].includes('### 文件与数据族的 Rhino 对照'), 'HOST-API section 4 describes the files and data family');
console.log(JSON.stringify({hostMembers: members, returnedObjects: objects.length, workerTable: true, globals: true, hostApi: true}));
