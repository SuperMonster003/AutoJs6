// colors.theme, gallery and files.batchRename in the JS runtimes (api-coverage Q80 - Q82 (a)): the shared facades against Rhino's
// exports and the declarations, the host modules behind them, the Worker tables and the Bun grants.
import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const node = resolve(workspace, 'AutoJs6-Plugin-NodeJs-Runtime'), bun = resolve(workspace, 'AutoJs6-Plugin-Bun-Runtime');
const read = path => readFileSync(path, 'utf8');
execFileSync(process.execPath, [resolve(bun, 'tools/host-client/sync-plugin-family.mjs'), '--check'], {stdio: 'inherit'});
const source = read(resolve(node, 'app/src/main/cpp/node_bridge_sources.cpp'));
const a = source.indexOf('  // Shared plugin family facades:'), z = source.indexOf('  // End shared plugin family facades.', a);
assert(a >= 0 && z > a);
const {gallery, batchRename} = new Function(source.slice(a, z) + ';return __autojs6_create_plugin_family;')()(() => {
  throw Error('Coverage inspection must not call the host');
}, EventEmitter);
const exported = hostExports();
const types = name => read(resolve(node, `docs/nodejs/types/autojs6-node/${name}.d.ts`));
let count = 0;
for (const [name, api, declarations] of [['gallery', gallery, types('gallery')], ['files.batchRename', batchRename, types('files')]]) {
  for (const method of exported.functions(name)) {
    assert.equal(typeof api[method], 'function', `Missing ${name}.${method}`);
    assert(declarations.includes(method + '('), `Missing ${name} declaration ${method}`); count++;
  }
}
assert(gallery.GalleryError('x', 'IO') instanceof gallery.GalleryError);
assert(batchRename.BatchRenameError('x', 'IO') instanceof batchRename.BatchRenameError);
const themeSource = source.slice(source.indexOf("// Rhino's colors.theme"), source.indexOf('define(colors, "theme", Object.freeze(theme));'));
for (const method of exported.functions('colors.theme')) {
  assert(themeSource.includes(`["${method}", `), `Missing colors.theme.${method}`);
  assert(types('colors').includes(method + '('), `Missing colors.theme declaration ${method}`); count++;
}
for (const name of ['gallery', 'batch_rename']) {
  assert(source.includes(`const creates${name === 'gallery' ? 'Gallery' : 'BatchRename'}=message.module==="${name}" && message.method==="open";`), `Worker tracking of ${name}`);
  assert(source.includes(`invoke("${name}","close",[id],{permissions:["${name}"]})`), `Worker close of ${name}`);
}
assert(source.includes('gallery:Object.keys(__autojs6_limited_gallery())'), 'Worker gallery table');
const manifest = read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt'));
const plugin = read(resolve(node, 'app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeBridgePermissionManifest.java'));
const catalog = JSON.parse(read(resolve(node, 'app/src/main/assets/nodejs/node-capability-catalog.json')));
for (const [constant, name] of [['THEME', 'theme'], ['GALLERY', 'gallery'], ['BATCH_RENAME', 'batch_rename']]) {
  assert(manifest.includes(`const val ${constant} = "${name}"`), `Host capability ${name}`);
  assert(plugin.includes(`String ${constant} = "${name}"`), `Plugin capability ${name}`);
  assert(catalog.bridge.modules.some(item => item.id === name), `Catalog module ${name}`);
  assert(catalog.bridge.permissionCapabilities.some(item => item.id === name), `Catalog capability ${name}`);
  assert(catalog.bridge.operations.every(item => item.module !== name || item.requiredCapabilities.join() === name), `Catalog operations of ${name}`);
}
const grants = read(resolve(root, 'app/src/main/java/org/autojs/autojs/core/plugin/bun/BunHostCapabilityGrants.kt'));
const hostClient = read(resolve(bun, 'app/src/main/assets/autojs6/host-client.mjs'));
const dispatcher = read(resolve(bun, 'app/src/main/java/io/github/supermonster003/autojs6/plugin/bun/runtime/BunHostBridgeDispatcher.kt'));
for (const grant of ['theme.control', 'gallery.control', 'batchrename.control']) {
  assert(grants.includes(`_CONTROL = "${grant}"`), `Host grant ${grant}`);
  assert(hostClient.includes(`"${grant}": Object.freeze([`), `Bun method table ${grant}`);
}
for (const grant of ['gallery.control', 'batchrename.control']) assert(dispatcher.includes(`"${grant}"`), `Bun call budget of ${grant}`);
assert(hostClient.includes("themeCall: (method, args) => automationCall('theme', method, args)"), 'Bun colors.theme');
assert(hostClient.includes('gallery: pluginFamily.gallery'), 'Bun gallery');
assert(hostClient.includes('result.batchRename = shared.batchRename;'), 'Bun files.batchRename');
const docs = read(resolve(workspace, 'AutoJs6-Documentation/api/runtimeApis.md'));
for (const text of ['colors.theme', 'gallery', 'files.batchRename', 'theme.control', 'gallery.control', 'batchrename.control']) assert(docs.includes(text), `Runtime documentation ${text}`);
console.log(JSON.stringify({rhinoMembers: count, nodeBunWorker: true, grants: 3}));
