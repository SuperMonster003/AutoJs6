import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),workspace=resolve(root,'..');
const bun=resolve(workspace,'AutoJs6-Plugin-Bun-Runtime');
const {HOST_API_METHODS}=await import(pathToFileURL(resolve(bun,'app/src/main/assets/autojs6/host-client.mjs')).href);
const native=readFileSync(resolve(root,'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt'),'utf8');
// A registry's names with those of the registries it adds (eventsMethods + automationEventsMethods, api-coverage P3.4 / P4.4).
function registry(name){
  const match=native.match(new RegExp('val '+name+'Methods(?:: Set<String>\\s*get\\(\\))? = setOf\\(([^)]*)\\)((?: \\+ \\w+Methods)*)'));
  assert(match,name+' registry missing');
  return [...match[1].matchAll(/"([^"]+)"/g)].map(row=>row[1]).concat([...match[2].matchAll(/(\w+)Methods/g)].flatMap(row=>registry(row[1])));
}
for(const [name,capability] of [['events','events.observe'],['notifications','notifications.post']]){
  assert.deepEqual([...HOST_API_METHODS[capability]].sort(),registry(name).sort(),capability+' client/host drift');
}
execFileSync(process.execPath,[resolve(bun,'tools/host-client/sync-events.mjs'),'--check'],{stdio:'inherit'});
console.log(`Event coverage: ${HOST_API_METHODS['events.observe'].length} observer routes, 5 notification routes, shared main/Worker/Bun factories and declarations`);
