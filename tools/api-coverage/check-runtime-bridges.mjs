#!/usr/bin/env node
// Compare actual client exports with host method sets; runtime behavior has separate tests.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
const host = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const workspace = path.dirname(host);
const nodeRoot = path.resolve(process.argv[2] || path.join(workspace, 'AutoJs6-Plugin-NodeJs-Runtime'));
const bunRoot = path.resolve(process.argv[3] || path.join(workspace, 'AutoJs6-Plugin-Bun-Runtime'));
const engine = path.join(host, 'app/src/main/java/org/autojs/autojs/engine');
const read = file => fs.readFileSync(file, 'utf8');
const source = read(path.join(nodeRoot, 'app/src/main/cpp/node_bridge_sources.cpp'));
const sorted = values => [...new Set(values)].sort();
function methods(file, name) {
  const match = read(path.join(engine, file)).match(new RegExp(`val ${name} = setOf\\(([\\s\\S]*?)\\)`));
  assert.ok(match, `Host method set changed: ${file}:${name}`);
  return sorted(Array.from(match[1].matchAll(/"([^"\r\n]+)"/g), item => item[1]));
}
function segment(start, end) {
  const a = source.indexOf(start), b = source.indexOf(end, a);
  assert.ok(a >= 0 && b > a, `Embedded function boundary changed: ${start}`);
  return source.slice(a,b);
}
const create = new Function('__autojs6_call_autojs', '__autojs6_invalid_arg_type', 'process', `
  let __autojs6_limited_pinyin_cache, __autojs6_limited_pinyin4j_cache;
  let __autojs6_limited_screen_metrics_cache, __autojs6_limited_console_cache;
  const __autojs6_has_own = (object,name) => Object.prototype.hasOwnProperty.call(object,name);
  ${segment('  const __autojs6_pinyin_styles =','  function __autojs6_jsox_mathx_flatten(')}
  ${segment('  function __autojs6_limited_console()','  let __autojs6_main_module_ref =')}
  return {pinyin:__autojs6_limited_pinyin(),pinyin4j:__autojs6_limited_pinyin4j(),
    coordinates:__autojs6_limited_screen_metrics(),console:__autojs6_limited_console()};
`);
const node = create(() => {throw Error('Unexpected Android dispatch');}, message => new TypeError(message), {stdout:{write(){}},stderr:{write(){}}});
const bun = await import(pathToFileURL(path.join(bunRoot,'app/src/main/assets/autojs6/host-client.mjs')).href);
const keys = object => Object.keys(object).filter(key => typeof object[key] === 'function');
const aliases = {showConsole:'show',openConsole:'show',launchConsole:'launch',clearConsole:'clear'};
const controls = keys(node.console).filter(key => String(node.console[key]).includes('"console"')).map(key => aliases[key] || key);
const groups = [
  ['text.pinyin',methods('NodeBridgeProtocol.kt','pinyinMethods'),keys(node.pinyin)],
  ['text.pinyin4j',methods('NodeBridgeProtocol.kt','pinyin4jMethods'),keys(node.pinyin4j)],
  ['screen.metrics',methods('RuntimeCoordinateBridge.kt','METHODS'),keys(node.coordinates)],
  ['console.control',methods('RuntimeConsoleBridge.kt','METHODS'),controls],
];
for(const [name,expected,actual] of groups) {
  assert.deepEqual(sorted(actual),expected,`${name}: Node client/host drift`);
  assert.deepEqual(sorted(bun.HOST_API_METHODS[name]),expected,`${name}: Bun client/host drift`);
}
const protocol = read(path.join(engine,'NodeBridgeProtocol.kt'));
assert.match(protocol,/SCREEN_METRICS to screenMetricsMethods/);
assert.match(protocol,/consoleMethods = setOf\("tail"\) \+ RuntimeConsoleBridge\.METHODS/);
const index = read(path.join(nodeRoot,'docs/nodejs/types/autojs6-node/index.d.ts'));
assert.match(index,/input_observer\.d\.ts/);
assert.match(index,/screen_metrics\.d\.ts/);
console.log(JSON.stringify({passed:true,groups:Object.fromEntries(groups.map(([name,methods])=>[name,methods.length])),scope:'Current text/console/coordinate clients versus host; not full Rhino equivalence'},null,2));
