// Structure check of the Bun device / sensors / media family (api-coverage P4.1c): the Bun host client carries the Node
// runtime's shared device and sensors factories unchanged (sync-device-dialogs.mjs / sync-sensors.mjs --check); every
// registered host member of device, sensors and media (module functions and getters, the Device / Sensors / Media
// prototype members) is a member of the client (host.device / host.sensors / host.media) and declared in
// host-client.d.mts, which type-checks; the device.control / media.control / media.library method tables equal the host's
// BunAutomationBridge white lists, device.identifiers / media.control / media.library (Q152 (a) / Q153 (a)) are host
// grants with plugin center switches; the snapshot members come with device.info (Q151 (a) / Q154 (a)); and without
// grants the family rejects NOT_GRANTED before the host. The values are compared with Rhino on a device by
// tools/api-coverage/device_bun_device_media.py.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {copyFileSync, mkdtempSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {tmpdir} from 'node:os';
import {resolve, dirname, join} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), workspace = resolve(root, '..');
const bun = resolve(workspace, 'AutoJs6-Plugin-Bun-Runtime'), node = resolve(workspace, 'AutoJs6-Plugin-NodeJs-Runtime');
const ace = resolve(workspace, 'AutoJs6-Plugin-Ace-Editor');
const read = path => readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
for (const script of ['sync-device-dialogs.mjs', 'sync-sensors.mjs']) {
  execFileSync(process.execPath, [resolve(bun, 'tools/host-client', script), '--check', '--node-root', node], {stdio: 'inherit'});
}
// The client next to a device-info.json as the plugin writes it (BunHostClientModule.deviceInfo with BunDeviceProfile), so
// that the snapshot members exist.
const DEVICE_INFO = {sdkInt: 35, density: 2.75, densityDpi: 440, scaledDensity: 2.75, width: 1080, height: 2400,
  build: Object.fromEntries(['buildId', 'buildDisplay', 'product', 'board', 'brand', 'manufacturer', 'device', 'model', 'bootloader', 'hardware',
    'fingerprint', 'incremental', 'release', 'baseOS', 'securityPatch', 'codename'].map(name => [name, `${name}-value`])),
  pageSize: 4096, marketName: 'market', manufacturers: {isXiaomi: true}, brands: {isRedmi: true}, roms: {isMiui: true},
  sensors: {light: {name: 'light', type: 5}}};
const directory = mkdtempSync(join(tmpdir(), 'aj6-bun-device-media-'));
copyFileSync(resolve(bun, 'app/src/main/assets/autojs6/host-client.mjs'), join(directory, 'host-client.mjs'));
writeFileSync(join(directory, 'device-info.json'), JSON.stringify(DEVICE_INFO));
// createHostClient reads the file, so it stays until the end.
process.on('exit', () => rmSync(directory, {recursive: true, force: true}));
const sdk = await import(pathToFileURL(join(directory, 'host-client.mjs')));

// A client with every grant of the family: building the modules and reading their members must not reach the host
// (the Promise getters rotation / orientation / imei / serial are checked with `in`, which does not read them).
const FAMILY = ['device.info', 'device.control', 'device.power', 'device.audio', 'device.identifiers', 'sensors.control', 'media.control', 'media.library'];
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
const block = (text, name, indent = '    ') => {
  const first = text.indexOf(`interface ${name} `);
  assert(first >= 0, `host-client.d.mts declares ${name}`);
  return text.slice(first, text.indexOf(`\n${indent}}`, first));
};
const namespace = name => declarations.slice(declarations.indexOf(`export namespace ${name} {`));
// A member declared as a method, a property or a getter.
const declared = (name, text) => new RegExp(`(^|[\\s;{])(readonly\\s+)?(get\\s+)?${name}\\??(\\(|:|<)`, 'm').test(text);
const modules = {
  device: [host.device, block(namespace('RuntimeDevice'), 'DeviceModule')],
  sensors: [host.sensors, block(namespace('RuntimeSensors'), 'SensorsModule') + block(namespace('RuntimeSensors'), 'RhinoEventEmitter')],
  media: [host.media, block(declarations, 'MediaModule', '')],
};
const exported = hostExports(), data = JSON.parse(readFileSync(exported.file, 'utf8'));
const rows = data.records.filter(row => row.ownerModule in modules).map(row => ({id: row.api, owner: row.ownerModule, member: row.member, kind: row.kind}))
  .concat(data.prototypes.filter(item => item.module in modules).flatMap(item => (item.members || [])
    .filter(member => !member.excluded && !member.shadowed).map(member => ({id: `${item.module}.${member.name}`, owner: item.module, member: member.name, kind: member.kind}))));
const checked = Object.fromEntries(Object.keys(modules).map(name => [name, 0]));
const seen = new Set();
for (const row of rows) {
  if (seen.has(row.id)) continue;
  seen.add(row.id);
  const [value, types] = modules[row.owner];
  if (row.kind === 'Functions') assert.equal(typeof value[row.member], 'function', `Missing host.${row.owner}.${row.member} (${row.id})`);
  else assert(row.member in value, `Missing host.${row.owner}.${row.member} (${row.id})`);
  assert(declared(row.member, types), `Missing declaration of ${row.owner}.${row.member} (${row.id})`);
  checked[row.owner] += 1;
}
assert(checked.device >= 80 && checked.sensors >= 20 && checked.media >= 9, `family rows ${JSON.stringify(checked)}`);
assert.deepEqual(hostCalls, [], 'Reading the family must not call the host');
// The Promise getters stay getters on the client (a host call per read, Q148 (a)).
for (const name of ['rotation', 'orientation', 'imei', 'serial']) assert.equal(typeof Object.getOwnPropertyDescriptor(host.device, name)?.get, 'function', `device.${name} getter`);

// Method tables: client == host white list; the grants are host grants with plugin center switches and strings.
const engine = name => read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine', name));
const protocol = engine('NodeBridgeProtocol.kt'), deviceBridge = engine('RuntimeDeviceBridge.kt'), bridge = engine('BunAutomationBridge.kt');
const registry = (text, name) => {
  const body = text.match(new RegExp(`val ${name}(?:: Set<String>)? = setOf\\(([^)]*)\\)`))?.[1];
  assert(body, `${name} registry`);
  return [...body.matchAll(/"(\w+)"/g)].map(match => match[1]);
};
const sorted = list => [...list].sort();
assert.deepEqual(sorted(sdk.HOST_API_METHODS['device.control']), sorted([...registry(protocol, 'deviceMethods'), ...registry(protocol, 'deviceRhinoControls'),
  ...registry(deviceBridge, 'methods'), ...registry(deviceBridge, 'POWER'), ...registry(deviceBridge, 'IDENTIFIERS')]), 'device.control: client / host drift');
assert.deepEqual(sorted(sdk.HOST_API_METHODS['media.control']), sorted(registry(protocol, 'mediaPlaybackMethods')), 'media.control: client / host drift');
assert.deepEqual(sdk.HOST_API_METHODS['media.library'], ['scanFile'], 'media.library: scanFile only');
for (const pattern of [/Grants\.DEVICE_CONTROL to NodeBridgeModules\.deviceMethods \+ NodeBridgeModules\.deviceRhinoControls \+ RuntimeDeviceBridge\.methods/,
  /Grants\.MEDIA_CONTROL to NodeBridgeModules\.mediaPlaybackMethods/, /Grants\.MEDIA_LIBRARY to setOf\("scanFile"\)/,
  /Grants\.DEVICE_IDENTIFIERS in grants\) add\(NodeBridgePermissionManifest\.DEVICE_IDENTIFIERS\)/]) assert(pattern.test(bridge), `BunAutomationBridge ${pattern}`);
const grantsFile = read(resolve(root, 'app/src/main/java/org/autojs/autojs/core/plugin/bun/BunHostCapabilityGrants.kt'));
const preferences = read(resolve(root, 'app/src/main/res/xml/fragment_plugin_settings_preferences_official.xml'));
const strings = read(resolve(root, 'app/src/main/res/values/strings_bun_device.xml')) + read(resolve(root, 'app/src/main/res/values/strings_bun_media.xml'));
const hostCapability = declarations.match(/export type HostCapability = ([^;]*);/)[1];
for (const grant of ['device.identifiers', 'media.control', 'media.library']) {
  const key = grant.replace(/\./g, '_');
  assert(grantsFile.includes(`"${grant}"`), `Host grant ${grant}`);
  assert(preferences.includes(`plugin_bun_${key}_grant`), `Plugin center switch ${grant}`);
  assert(strings.includes(`text_bun_${key}_grant`) && strings.includes(`summary_bun_${key}_grant`), `Strings of ${grant}`);
  assert(hostCapability.includes(`"${grant}"`), `HostCapability ${grant}`);
}

// Without device.info the snapshot members are absent (Q154 (a)); roms keeps the isBackgroundStartGranted route.
const blind = await client(FAMILY.filter(name => name !== 'device.info'));
for (const name of ['sdkInt', 'brand', 'buildId', 'width', 'height', 'density', 'pageSize', 'digest', 'getMarketName', 'isManufacturer', 'manufacturers', 'brands']) {
  assert(!(name in blind.device), `device.${name} without device.info`);
}
assert.deepEqual(Object.keys(blind.device.roms), ['isBackgroundStartGranted'], 'roms without device.info');

// Without grants: the family rejects NOT_GRANTED with the missing Bun grants and never reaches the host.
const calls = [];
const denied = await client(['device.info'], calls);
for (const [name, call, missing] of [
  ['device.isScreenOff', () => denied.device.isScreenOff(), 'device.control'],
  ['device.keepAwake', () => denied.device.keepAwake(1, 1000), 'device.power'],
  ['device.getMusicVolume', () => denied.device.getMusicVolume(), 'device.audio'],
  ['device.getIMEI', () => denied.device.getIMEI(), 'device.identifiers'],
  ['device.serial', () => denied.device.serial, 'device.identifiers'],
  ['sensors.getAvailableSensors', () => denied.sensors.getAvailableSensors(), 'sensors.control'],
  ['media.playMusic', () => denied.media.playMusic('/sdcard/a.mp3'), 'media.control'],
  ['media.isMusicPlaying', () => denied.media.isMusicPlaying(), 'media.control'],
  ['media.scanFile', () => denied.media.scanFile('/sdcard/a.mp3'), 'media.library'],
]) {
  await assert.rejects(call(), error => error.code === 'NOT_GRANTED' && error.missingCapabilities.includes(missing), `${name} without grants`);
}
assert.deepEqual(calls, [], 'Denied entries must not call the host');

const hostApi = read(resolve(bun, 'docs/host-api.md'));
for (const name of ['device.identifiers', 'media.control', 'media.library', 'playMusic', 'scanFile', 'sensors.register', 'getMarketName', 'device-info.json']) {
  assert(hostApi.includes(name), `Bun docs/host-api.md describes ${name}`);
}
console.log(JSON.stringify({members: checked, routes: Object.fromEntries(['device.control', 'media.control', 'media.library']
  .map(name => [name, sdk.HOST_API_METHODS[name].length])), declarations: 'type-checked', hostApi: true}));
