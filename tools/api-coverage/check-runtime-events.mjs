import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),workspace=resolve(root,'..');
const bun=resolve(workspace,'AutoJs6-Plugin-Bun-Runtime');
const {HOST_API_METHODS}=await import(pathToFileURL(resolve(bun,'app/src/main/assets/autojs6/host-client.mjs')).href);
const native=readFileSync(resolve(root,'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt'),'utf8');
for(const [name,capability] of [['events','events.observe'],['notifications','notifications.post']]){
  const body=native.match(new RegExp('val '+name+'Methods = setOf\\(([^)]*)\\)'))?.[1];assert(body,name+' registry missing');
  const names=[...body.matchAll(/"([^"]+)"/g)].map(row=>row[1]).sort();assert.deepEqual([...HOST_API_METHODS[capability]].sort(),names);
}
execFileSync(process.execPath,[resolve(bun,'tools/host-client/sync-events.mjs'),'--check'],{stdio:'inherit'});
console.log('Event coverage: 6 observer routes, 5 notification routes, shared main/Worker/Bun factories and declarations');
