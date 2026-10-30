// Structure check of the Node device, sensors and media family (api-coverage P3.1c): every registered host member of
// device (the module and Rhino's Device prototype), sensors (Rhino's Sensors) and media (Rhino's Media) has a member in
// the shared factories of node_bridge_sources.cpp and a Node declaration; device.manufacturers / brands / roms follow the
// host's checks. Workers build device and sensors from the same factories and forward media to the main thread; the host
// routes Rhino's device queries through RuntimeDeviceBridge with device.identifiers (Q146 (a), never implied) and
// accepts Rhino's sensor names (Q147 (a)); the plugin's startup snapshot carries the fields the factories read (Q148 (a)).
// Behaviour is compared with Rhino by the Node repository's tools/nodejs/api/test-device-media-rhino.js (desktop) and
// docs/dev/runtime-device-media-2026-10-30.md (device).
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {EventEmitter} from 'node:events';
import {hostExports} from './host-exports.mjs';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), node = resolve(root, '../AutoJs6-Plugin-NodeJs-Runtime');
const read = path => readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
const source = read(resolve(node, 'app/src/main/cpp/node_bridge_sources.cpp'));
// A top-level facade function, up to its closing brace at the same indentation.
const part = head => {
  const a = source.indexOf(head), z = source.indexOf('\n  }\n', a);
  assert(a >= 0 && z > a, head.trim());
  return source.slice(a, z + 4);
};
const factory = name => new Function('return (' + part(`  function ${name}(`) + ')')();
// Members are only inspected.
const call = async () => null;
// The device factory reads the startup snapshot that __autojs6_host_device_info parses from the plugin's device-info module
// (NodeRuntimeModuleInjector and NodeDeviceProfile): every key the plugin writes, with a value of the expected kind.
const plugin = name => read(resolve(node, 'app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs', name));
const injector = plugin('NodeRuntimeModuleInjector.java'), profile = plugin('NodeDeviceProfile.java');
const injected = injector.slice(injector.indexOf('private String deviceInfoRuntimeModuleSource('), injector.indexOf('return info.toString();'));
const keys = new Set([...injected.matchAll(/\.put\("(\w+)"/g), ...profile.matchAll(/\.put\("(\w+)"/g)].map(match => match[1]));
const NUMBERS = ['sdkInt', 'width', 'height', 'density', 'densityDpi', 'scaledDensity', 'pageSize'], OBJECTS = ['manufacturers', 'brands', 'roms', 'sensors'];
const record = Object.fromEntries([...keys].map(key => [key, NUMBERS.includes(key) ? 1 : OBJECTS.includes(key) ? {} : 'x']));
const snapshot = new Function('__autojs6_runtime_module_embedded_sources', '__autojs6_device_number_property', 'return (' + part('  function __autojs6_host_device_info(') + ')')(
  {'autojs6:device-info': {source: JSON.stringify(record)}}, (value, fallback) => Number.isFinite(value) ? value : fallback)();
for (const key of [...NUMBERS, ...OBJECTS, 'buildDisplay', 'manufacturer', 'incremental', 'baseOS', 'securityPatch', 'codename', 'marketName']) {
  assert(key in snapshot, `Snapshot field ${key} (plugin and facade)`);
}
const device = factory('__autojs6_create_device')(call, snapshot, 'check', {morsePattern: () => []});
const sensors = factory('__autojs6_create_sensors')(call, EventEmitter, undefined, undefined, {sensors: {}, missing: () => []});
// The main-thread media object needs the bridge; its members are the frozen object literal.
const mediaBody = part('  function __autojs6_limited_media(');
const mediaLiteral = mediaBody.slice(mediaBody.lastIndexOf('Object.freeze({'));
const media = Object.fromEntries([...mediaLiteral.matchAll(/^\s+(\w+)(?::[^\n]*|,)?$/gm)].map(match => [match[1], () => {}]));

const types = name => read(resolve(node, `docs/nodejs/types/autojs6-node/${name}.d.ts`));
const body = (text, head) => {
  const start = text.indexOf(head);
  assert(start >= 0, head);
  return text.slice(start, text.indexOf('\n    }', start));
};
const declarations = {
  device: body(types('device'), 'interface DeviceModule {'),
  sensors: body(types('sensors'), 'interface SensorsModule extends RhinoEventEmitter {') + body(types('sensors'), 'interface RhinoEventEmitter {'),
  media: body(types('media'), 'interface MediaModule {'),
  manufacturers: body(types('device'), 'interface DeviceManufacturers {'), brands: body(types('device'), 'interface DeviceBrands {'),
  roms: body(types('device'), 'interface DeviceRoms {'),
};
const declared = (where, name) => new RegExp(`(^|[^\\w$])(readonly )?${name}\\??(\\(|:)`, 'm').test(declarations[where]);
const objects = {device, sensors, media, manufacturers: device.manufacturers, brands: device.brands, roms: device.roms};
let members = 0;
const check = (where, name) => {
  assert(name in objects[where], `Missing ${where}.${name}`);
  assert(declared(where, name), `Missing declaration of ${where}.${name}`);
  members += 1;
};

// Host members: the device module's registrations and the script prototypes (Rhino's Device, Sensors and Media).
const exported = hostExports(), data = JSON.parse(readFileSync(exported.file, 'utf8'));
const prototype = module => {
  const entry = data.prototypes.find(item => item.module === module);
  assert(entry, `No script prototype of ${module} in ${exported.file}`);
  return entry.members.map(item => typeof item === 'string' ? item : item.name);
};
const names = {
  device: new Set([...exported.names('device', 'Functions'), ...exported.names('device', 'Getters'), ...prototype('device')]),
  sensors: new Set(prototype('sensors')), media: new Set(prototype('media')),
};
for (const [module, list] of Object.entries(names)) for (const name of list) check(module, name);
assert(names.device.size >= 80 && names.sensors.size >= 21 && names.media.size >= 9, 'Host member counts');

// device.manufacturers / brands / roms: the checks of the host's Device.Manufacturers, RomUtils.Brand and RomUtils.
const host = path => read(resolve(root, 'app/src/main/java/org/autojs/autojs', path));
const deviceJava = host('runtime/api/Device.java'), romUtils = host('util/RomUtils.kt');
const manufacturers = deviceJava.slice(deviceJava.indexOf('public class Manufacturers'));
const brandStart = romUtils.indexOf('object Brand');
const checks = {
  manufacturers: [...manufacturers.matchAll(/public boolean (is\w+)\(\)/g)].map(match => match[1]),
  brands: [...romUtils.slice(brandStart).matchAll(/^\s+fun (is\w+)\(\)/gm)].map(match => match[1]),
  roms: [...romUtils.slice(0, brandStart).matchAll(/^\s+fun (is\w+)\(/gm)].map(match => match[1]),
};
for (const [where, list] of Object.entries(checks)) {
  assert(list.length >= 3, `Host checks of ${where}`);
  for (const name of list) check(where, name);
}
for (const name of [...checks.manufacturers, ...checks.brands, ...checks.roms.filter(name => name !== 'isBackgroundStartGranted')]) {
  assert(profile.includes(`"${name}"`), `Plugin snapshot check ${name}`);
}

// Workers receive the snapshot and list every member.
const table = source.slice(source.indexOf('hostModuleMethods: {'), source.indexOf('\n          },', source.indexOf('hostModuleMethods: {')));
for (const name of ['device', 'sensors', 'media']) assert(new RegExp(`(^|[\\s,{])${name}\\s*:\\s*Object\\.keys\\(`).test(table), `Worker table entry ${name}`);
assert(source.includes('createDevice(bridgeCall,constants.device,undefined,'), 'Workers pass the morse pattern to device');
assert(source.includes('createSensors(bridgeCall, SensorEmitter, undefined, undefined,'), 'Workers pass the sensors snapshot');
assert(part('  async function __autojs6_worker_invoke(').includes(': name === "media" ? __autojs6_limited_media()'), 'Workers forward media');
assert.equal(typeof sensors.register, 'function');
assert.deepEqual({...sensors.delay}, {normal: 3, ui: 2, game: 1, fastest: 0});

// Sensor names: the host bridge and the plugin snapshot accept Rhino's table (Sensors.SENSORS).
const sensorTable = host('runtime/api/Sensors.java');
const rhinoNames = [...sensorTable.matchAll(/\.put\("(\w+)", Sensor\.TYPE_\w+\)/g)].map(match => match[1].toLowerCase());
assert.equal(rhinoNames.length, 12, 'Rhino sensor table');
const engine = name => read(resolve(root, 'app/src/main/java/org/autojs/autojs/engine', name));
const protocol = engine('NodeBridgeProtocol.kt');
for (const name of rhinoNames) {
  assert(protocol.includes(`"${name}" to Sensor.TYPE_`), `Host sensor name ${name}`);
  assert(profile.includes(`types.put("${name}"`), `Plugin sensor name ${name}`);
}

// Host routes and capabilities (Q146 (a)) in the host, the plugin and the catalog.
const bridge = engine('RuntimeDeviceBridge.kt'), manifest = engine('NodeBridgePermissionManifest.kt');
const routes = [...bridge.match(/val methods: Set<String> = setOf\(([\s\S]*?)\) \+/)[1].matchAll(/"(\w+)"/g)].map(match => match[1]);
const identifiers = [...bridge.match(/IDENTIFIERS = setOf\(([^)]*)\)/)[1].matchAll(/"(\w+)"/g)].map(match => match[1]);
const power = [...bridge.match(/POWER = setOf\(([^)]*)\)/)[1].matchAll(/"(\w+)"/g)].map(match => match[1]);
const catalog = JSON.parse(read(resolve(node, 'app/src/main/assets/nodejs/node-capability-catalog.json')));
const operations = new Map(catalog.bridge.operations.filter(item => item.module === 'device').map(item => [item.method, item.requiredCapabilities]));
for (const method of [...routes, ...identifiers, ...power, 'keepAwake', 'cancelVibration']) {
  assert(operations.has(method), `Catalog device route ${method}`);
  const expected = identifiers.includes(method) ? ['device', 'device.identifiers'] : power.includes(method) || method === 'keepAwake' ? ['device', 'device.power'] : ['device'];
  assert.deepEqual(operations.get(method), expected, `Catalog capabilities of device.${method}`);
}
assert(/required == DEVICE_IDENTIFIERS \|\|/.test(manifest), 'device.identifiers never implied (host)');
assert(part('  function __autojs6_bridge_declares_capability(').includes('"device.identifiers"'), 'device.identifiers never implied (facade)');
const pluginManifest = read(resolve(node, 'app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeBridgePermissionManifest.java'));
assert(pluginManifest.includes('"device.identifiers"'), 'Plugin capability device.identifiers');
assert(catalog.bridge.permissionCapabilities.some(item => item.id === 'device.identifiers'), 'Catalog capability device.identifiers');

const hostApi = read(resolve(node, 'docs/HOST-API.md'));
assert(hostApi.includes('## 设备, 传感器与媒体族的 Rhino 对照'), 'HOST-API section of the device, sensors and media family');
console.log(JSON.stringify({hostMembers: members, device: names.device.size, sensors: names.sensors.size, media: names.media.size,
  sensorNames: rhinoNames.length, deviceRoutes: routes.length + identifiers.length + power.length, hostApi: true}));
