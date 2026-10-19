import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),workspace=resolve(root,'..');
const bun=resolve(workspace,'AutoJs6-Plugin-Bun-Runtime');
const {HOST_API_METHODS}=await import(pathToFileURL(resolve(bun,'app/src/main/assets/autojs6/host-client.mjs')).href);
for(const [name,capability] of [['RuntimeAndroidObjects','android.objects'],['RuntimeTasksBridge','tasks.control']]){
  const native=readFileSync(resolve(root,`app/src/main/java/org/autojs/autojs/engine/${name}.kt`),'utf8');
  const body=native.match(/val methods\s*=\s*setOf\(([^)]*)\)/)?.[1];assert(body,name+' registry missing');
  assert.deepEqual([...HOST_API_METHODS[capability]].sort(),[...body.matchAll(/"([^"]+)"/g)].map(row=>row[1]).sort());
}
execFileSync(process.execPath,[resolve(bun,'tools/host-client/sync-android-tasks.mjs'),'--check'],{stdio:'inherit'});
console.log('Android/task coverage: 10 native object routes, 10 persistent task routes, shared Node/Bun/Worker factories and types');
