import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),bun=path.resolve(root,'../AutoJs6-Plugin-Bun-Runtime'),node=path.resolve(root,'../AutoJs6-Plugin-NodeJs-Runtime');
execFileSync(process.execPath,[path.join(bun,'tools/host-client/sync-sensors.mjs'),'--check','--node-root',node],{stdio:'inherit'});
const sdk=await import(pathToFileURL(path.join(bun,'app/src/main/assets/autojs6/host-client.mjs')));
const names=text=>[...text.matchAll(/"(\w+)"/g)].map(m=>m[1]).sort();
const bridge=fs.readFileSync(path.join(root,'app/src/main/java/org/autojs/autojs/engine/BunAutomationBridge.kt'),'utf8');
const provider=fs.readFileSync(path.join(root,'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt'),'utf8');
const expected=[...sdk.HOST_API_METHODS['sensors.control']].sort();assert.equal(expected.length,5);
assert.deepEqual(names(bridge.match(/Grants.SENSORS_CONTROL to setOf\(([^)]*)\)/)[1]),expected);
assert.deepEqual(names(provider.match(/val sensorsMethods = setOf\(([^)]*)\)/)[1]),expected);
const source=fs.readFileSync(path.join(node,'app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
function section(a,z){const first=source.indexOf(a),last=source.indexOf(z,first);assert(first>=0&&last>first);return source.slice(first,last);}
const factory=new Function('return ('+section('  function __autojs6_create_sensors(','  // Main-thread sensors facade;')+')')();
const workers=new Function('return ('+section('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(')+')')();
const {EventEmitter}=await import('node:events');
const modules=workers(async()=>[],{sensors:['policy','getAvailableSensors','once','subscribe']},{},[],console,undefined,undefined,undefined,undefined,factory,EventEmitter);
assert.deepEqual([...modules.keys()],['sensors']);
// Rhino's sensors layer over subscribe (api-coverage P3.1c / P4.1c, Q147 (a)): an event emitter with register / getSensor.
const keys=Object.keys(modules.get('sensors'));
for(const name of ['getAvailableSensors','once','policy','subscribe','delay','register','getSensor','unregister','unregisterAll','ignoresUnsupportedSensor',
  'on','addListener','emit','emitSticky','removeListener','removeAllListeners','listeners','listenerCount','eventNames','getMaxListeners','setMaxListeners','getTimer'])assert(keys.includes(name),'sensors.'+name);
console.log('Sensors coverage: 5 native routes, shared main/Worker/Bun facade and synchronized types');
