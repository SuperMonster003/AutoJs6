import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {hostExports} from './host-exports.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'), workspace=resolve(root,'..');
const node=resolve(workspace,'AutoJs6-Plugin-NodeJs-Runtime'), bun=resolve(workspace,'AutoJs6-Plugin-Bun-Runtime');
execFileSync(process.execPath,[resolve(bun,'tools/host-client/sync-ai.mjs'),'--check'],{stdio:'inherit'});
const read=file=>readFileSync(file,'utf8');
const source=read(resolve(node,'app/src/main/cpp/node_bridge_sources.cpp'));
const first=source.indexOf('  // Shared AI facade.'),last=source.indexOf('  // End shared AI facade.',first);
assert(first>=0&&last>first);
const ai=new Function(source.slice(first,last)+';return __autojs6_create_ai;')()(()=>{throw Error('Unexpected host call');});
const names=['ask','chat','stream','session','catalog'];
const exported=hostExports();
assert.deepEqual(exported.functions('ai'),names.slice().sort());
assert.deepEqual(exported.names('ai','Getters'),['agent']);assert(exported.module('ai').callable);
const types=read(resolve(node,'docs/nodejs/types/autojs6-node/ai.d.ts'));
const docs=read(resolve(workspace,'AutoJs6-Documentation/api/ai.md')).split('## Node.js, Bun 和 Worker')[1];
assert(docs);
for(const name of [...names,'hasAccess','requestAccess','close']) {
  assert.equal(typeof ai[name],'function');assert(types.includes(name+'('),name+' declaration');assert(docs.includes(name),name+' documentation');
}
assert.equal(typeof ai,'function');assert.equal(ai.ask,ai);assert.equal(typeof ai.agent.run,'function');
assert(types.includes('readonly result: Promise<Response>')&&types.includes('close(): Promise<void>'));
console.log(JSON.stringify({registeredAiCoreNames:5,sessionMethods:4,runtimeHelpers:3,sharedMainBunWorkerFactory:true,agentPortableFacade:true,rhinoPrototypeParity:false}));
