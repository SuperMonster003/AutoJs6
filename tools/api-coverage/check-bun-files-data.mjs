// Structure check of the Bun files and data family (api-coverage P4.2b): the generated sections of the Bun host
// client are current (sync-mediainfo.mjs: the Node runtime's mediainfo declarations; sync-axios.mjs: the axios 1.1.2
// of Q64 (b), from the Bundled Libraries plugin since host 67ef4c1a5; the shared JS tools core with colors / zip / mime
// / recorder is checked by check-bun-js-tools.mjs), every member of the Rhino files prototype and every registered
// files / mediainfo / colors / zip / mime / recorder / storages export (but colors.theme, files.batchRename and gallery,
// appendix C Q80 - Q82) and the global open / Color / libraries are members of the client, the mediainfo.control methods equal the host's Node bridge mediainfo methods, the grant
// is registered in the host (BunHostCapabilityGrants, Plugin Center switch in 11 languages) and declared in
// host-client.d.mts, and docs/host-api.md describes the members. The values are compared with Rhino by
// tools/api-coverage/device_bun_js_tools.py --cases tools/api-coverage/device_bun_files_data_cases.js (device).
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {existsSync, readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const bun = resolve(workspace, 'AutoJs6-Plugin-Bun-Runtime');
const read = path => readFileSync(path, 'utf8');
const sync = (script, ...args) => execFileSync(process.execPath, [resolve(bun, 'tools/host-client', script), '--check', ...args], {stdio: 'inherit'});
sync('sync-mediainfo.mjs');
sync('sync-axios.mjs', '--libraries-root', resolve(workspace, 'AutoJs6-Plugin-Bundled-Libraries'));
const {createHostClient, HOST_API_METHODS} = await import(pathToFileURL(resolve(bun, 'app/src/main/assets/autojs6/host-client.mjs')));
// A client without any grant: building the members must not reach the host.
const host = await createHostClient({socket: 'coverage', fetch: async url => {
  assert(url.endsWith('/info'), 'Coverage inspection must not call the host');
  return Response.json({ok: true, bridgeVersion: 1, capabilities: [], limits: {}});
}});
const exported = hostExports();
const prototypes = JSON.parse(read(exported.file)).prototypes;
const prototypeMembers = module => {
  const found = prototypes.find(item => item.module === module);
  assert(found?.complete, `${exported.file}: no complete prototype of ${module}`);
  return found.members.map(member => member.name);
};
const sorted = values => [...new Set(values)].sort();

// Left for the decisions of appendix C Q80 (colors.theme), Q81 (gallery) and Q82 (files.batchRename).
const PENDING = {colors: ['theme'], files: ['batchRename']};
assert(!('gallery' in host), 'gallery waits for Q81');
// files: the Rhino prototype members and the registered exports (open / toFile follow the shared core).
const files = sorted([...prototypeMembers('files'), ...exported.names('files')]).filter(name => !PENDING.files.includes(name));
assert.deepEqual(files.filter(name => !(name in host.files)), [], 'Rhino files members missing from host.files');
assert.equal(typeof host.files.sdcardPath === 'string' || host.files.sdcardPath === null, true, 'files.sdcardPath is a string (null without device-info.json)');

// colors, zip, mime, recorder (the shared JS tools core), storages and the global members of the family.
const members = {colors: host.colors, zip: host.zip, mime: host.mime, recorder: host.recorder, storages: host.storages};
const counts = {};
// mime and recorder are Rhino prototype modules (Q18), the others registered modules.
const PROTOTYPE_MODULES = ['mime', 'recorder'];
for (const [module, value] of Object.entries(members)) {
  const names = (PROTOTYPE_MODULES.includes(module) ? prototypeMembers(module) : exported.names(module)).filter(name => !(PENDING[module] || []).includes(name));
  assert.deepEqual(names.filter(name => !(name in value)), [], `Rhino ${module} members missing from host.${module}`);
  counts[module] = names.length;
}
for (const name of PENDING.colors) assert(!(name in host.colors), `colors.${name} waits for its decision`);
assert.equal(typeof host.zip, 'function', 'host.zip is callable like Rhino zip');
assert.equal(typeof host.recorder, 'function', 'host.recorder is callable like Rhino recorder');
assert.equal(host.global.open, host.files.open, 'the global open is files.open');
for (const name of ['Color', 'open', 'axios', 'dayjs', 'pangu', 'cheerio', 'i18n']) assert(name in host.global, `host.global lacks ${name}`);

// mediainfo: the registered exports, the host's Node bridge methods and the grant.
const mediainfo = exported.names('mediainfo');
assert.deepEqual(sorted(Object.keys(host.mediainfo)), mediainfo, 'host.mediainfo differs from the Rhino mediainfo exports');
const protocol = read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt'));
const methods = sorted([...protocol.match(/val mediainfoMethods = setOf\(([^)]*)\)/)[1].matchAll(/"(\w+)"/g)].map(match => match[1]));
assert.deepEqual(sorted(HOST_API_METHODS['mediainfo.control']), methods, 'mediainfo.control: Bun client / host drift');
const grants = read(resolve(root, 'app/src/main/java/org/autojs/autojs/core/plugin/bun/BunHostCapabilityGrants.kt'));
assert(grants.includes('const val MEDIAINFO_CONTROL = "mediainfo.control"'), 'BunHostCapabilityGrants lacks mediainfo.control');
assert((grants.match(/MEDIAINFO_CONTROL/g) || []).length >= 5, 'mediainfo.control is not granted, listed and validated like the other grants');
const settings = read(resolve(root, 'app/src/main/res/xml/fragment_plugin_settings_preferences_official.xml'));
assert(settings.includes('plugin_bun_mediainfo_control_grant'), 'Plugin Center has no mediainfo.control switch');
const languages = ['', '-ar', '-en', '-es', '-fr', '-ja', '-ko', '-ru', '-zh', '-zh-rHK', '-zh-rTW'];
for (const language of languages) {
  const file = resolve(root, `app/src/main/res/values${language}/strings_bun_mediainfo.xml`);
  assert(existsSync(file) && read(file).includes('text_bun_mediainfo_control_grant'), `values${language}: no mediainfo.control strings`);
}

// Declarations and documentation.
const declarations = read(resolve(bun, 'app/src/main/assets/autojs6/host-client.d.mts'));
assert(/export type HostCapability = [^;]*"mediainfo\.control"/.test(declarations), 'HostCapability lacks mediainfo.control');
assert(declarations.includes('readonly mediainfo: RuntimeMediainfo.MediainfoModule;'), 'HostClient does not declare mediainfo');
for (const declared of ['readonly colors: RuntimeColors.ColorsModule;', 'readonly zip: RuntimeZip.ZipModule;', 'readonly mime: RuntimeMime.MimeModule;', 'readonly recorder:'])
  assert(declarations.includes(declared), `HostClient does not declare ${declared}`);
for (const name of mediainfo) assert(new RegExp(`\\b${name}\\b`).test(declarations.slice(declarations.indexOf('export namespace RuntimeMediainfo'))), `RuntimeMediainfo lacks ${name}`);
const documentation = read(resolve(bun, 'docs/host-api.md'));
assert(documentation.includes('## host.mediainfo'), 'docs/host-api.md does not describe host.mediainfo');
for (const text of ['### colors / Color, zip, mime 与计时 recorder', 'names(options?) / all(options?)', 'files.batchRename (api-coverage Q82)', 'gallery (Q81)', 'colors.theme 尚无入口 (api-coverage Q80)'])
  assert(documentation.includes(text), `docs/host-api.md lacks: ${text}`);
const undocumented = files.filter(name => !new RegExp(`\\b${name}\\b`).test(documentation));
assert.deepEqual(undocumented, [], 'docs/host-api.md does not describe these files members');
console.log(`Bun files and data: files ${files.length} members, mediainfo ${mediainfo.length} members, mediainfo.control ${methods.length} host methods, ` +
  Object.entries(counts).map(([module, count]) => `${module} ${count}`).join(', ') + `, axios ${host.global.axios.VERSION}`);
