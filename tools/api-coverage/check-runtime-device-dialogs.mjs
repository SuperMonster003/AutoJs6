import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),workspace=resolve(root,'..');
const bun=resolve(workspace,'AutoJs6-Plugin-Bun-Runtime');
const {HOST_API_METHODS}=await import(pathToFileURL(resolve(bun,'app/src/main/assets/autojs6/host-client.mjs')).href);
const native=readFileSync(resolve(root,'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt'),'utf8');
const registry=(source,name)=>{
  const body=source.match(new RegExp('val '+name+'(?:: Set<String>)? = setOf\\(([^)]*)\\)'))?.[1];assert(body,name+' registry missing');
  return [...body.matchAll(/"([^"]+)"/g)].map(row=>row[1]);
};
// Bun's device.control also routes Rhino's device members (api-coverage P4.1c, Q153 (a)): keepAwake / cancelVibration
// and the RuntimeDeviceBridge queries, whose Node capabilities BunAutomationBridge adds per grant.
const bridge=readFileSync(resolve(root,'app/src/main/java/org/autojs/autojs/engine/RuntimeDeviceBridge.kt'),'utf8');
const rhinoDevice=[...registry(native,'deviceRhinoControls'),...registry(bridge,'methods'),...registry(bridge,'POWER'),...registry(bridge,'IDENTIFIERS')];
for(const [name,capability,extra] of [['device','device.control',rhinoDevice],['dialogs','dialogs.control',[]]]){
  assert.deepEqual([...HOST_API_METHODS[capability]].sort(),[...registry(native,name+'Methods'),...extra].sort());
}
assert.deepEqual(HOST_API_METHODS['device.audio'],['getAudioStreamVolume','getAudioStreamMaxVolume','setAudioStreamVolume']);
// Q152 (a): Rhino's media module is media.control (playback) and media.library (scanFile).
assert.deepEqual([...HOST_API_METHODS['media.control']].sort(),registry(native,'mediaPlaybackMethods').sort());
assert.deepEqual(HOST_API_METHODS['media.library'],['scanFile']);
assert(registry(native,'mediaStoreMethods').includes('scanFile'));
execFileSync(process.execPath,[resolve(bun,'tools/host-client/sync-device-dialogs.mjs'),'--check'],{stdio:'inherit'});
console.log(`Device/dialog coverage: ${HOST_API_METHODS['device.control'].length} device routes (${rhinoDevice.length} for Rhino's members), 3 audio routes, 7 media routes, 12 dialog routes, shared main/Worker/Bun factories and types`);
