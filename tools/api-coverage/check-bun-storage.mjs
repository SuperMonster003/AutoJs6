import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const bun=path.resolve(root,'../AutoJs6-Plugin-Bun-Runtime'),node=path.resolve(root,'../AutoJs6-Plugin-NodeJs-Runtime');
execFileSync(process.execPath,[path.join(bun,'tools/host-client/sync-storage.mjs'),'--check','--node-root',node],{stdio:'inherit'});
const {HOST_API_METHODS}=await import(pathToFileURL(path.join(bun,'app/src/main/assets/autojs6/host-client.mjs')));
const source=fs.readFileSync(path.join(root,'app/src/main/java/org/autojs/autojs/engine/BunFilesBridge.kt'),'utf8');
const names=text=>[...text.matchAll(/"(\w+)"/g)].map(m=>m[1]).sort();
assert.deepEqual(names(source.match(/val methods = setOf\(([^)]*)\)/)[1]),[...HOST_API_METHODS['files.control']].sort());
const nodeFiles=fs.readFileSync(path.join(node,'docs/nodejs/types/autojs6-node/files.d.ts'),'utf8');
// The members of the Rhino files prototype that Bun answers through files.control (api-coverage P4.2b); the Node
// runtime computes them in its own process, so they are not compared with the Node host functions.
const prototypeFiles=['cwd','create','createIfNotExists','createWithDirs','readBytes','writeBytes','appendBytes','rename','renameWithoutExtension','removeDir','isEmptyDir','readAssets','getSimplifiedPath'];
assert(prototypeFiles.every(name=>HOST_API_METHODS['files.control'].includes(name)),'Bun files.control lacks a Rhino files prototype member');
// The path helpers and open / toFile of the shared JS tools core run in the client (P4.2b).
const coreFiles=['join','getName','getExtension','getNameWithoutExtension','getHumanReadableSize','formatSizeWithUnit','nonNullPath','open','toFile'];
// files.batchRename has its own interfaces ahead of FilesModule and its own bridge (check-runtime-plugin-family.mjs).
const publicFiles=[...nodeFiles.slice(nodeFiles.indexOf('export interface FilesModule')).matchAll(/^\s+(\w+)\([^\n]+\): (?!never;)/gm)].map(m=>m[1]).filter(name=>!prototypeFiles.includes(name)).sort();
assert.deepEqual(publicFiles,[...HOST_API_METHODS['files.control'].filter(name=>!prototypeFiles.includes(name)),...coreFiles].sort());
const automation=fs.readFileSync(path.join(root,'app/src/main/java/org/autojs/autojs/engine/BunAutomationBridge.kt'),'utf8');
assert.deepEqual(names(automation.match(/Grants.STORAGE_CONTROL to setOf\(([^)]*)\)/)[1]),[...HOST_API_METHODS['storage.control']].sort());
const provider=fs.readFileSync(path.join(root,'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt'),'utf8');
assert.deepEqual(names(provider.match(/val storageMethods = setOf\(([^)]*)\)/)[1]),[...HOST_API_METHODS['storage.control']].sort());
console.log(`Bun file/storage coverage: ${publicFiles.length} public file functions, ${prototypeFiles.length} Rhino files prototype members and 6 shared native storage routes`);
