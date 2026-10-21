import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'), workspace=resolve(root,'..');
const node=resolve(workspace,'AutoJs6-Plugin-NodeJs-Runtime'), bun=resolve(workspace,'AutoJs6-Plugin-Bun-Runtime');
execFileSync(process.execPath,[resolve(bun,'tools/host-client/sync-ai.mjs'),'--check'],{stdio:'inherit'});
const read=file=>readFileSync(file,'utf8');
const source=read(resolve(node,'app/src/main/cpp/node_bridge_sources.cpp'));
const first=source.indexOf('  // Shared AI facade.'),last=source.indexOf('  // End shared AI facade.',first);
assert(first>=0&&last>first);
const ai=new Function(source.slice(first,last)+';return __autojs6_create_ai;')()(()=>{throw Error('Unexpected host call');});
const names=['ask','chat','stream','session','catalog'];
const registered=read(resolve(root,'app/src/main/java/org/autojs/autojs/runtime/api/augment/ai/Ai.kt'))
  .split('override val selfAssignmentFunctions = listOf(')[1].split(')')[0];
assert.deepEqual([...registered.matchAll(/::(\w+)\.name/g)].map(x=>x[1]).sort(),names.slice().sort());
const types=read(resolve(node,'docs/nodejs/types/autojs6-node/ai.d.ts'));
const docs=read(resolve(workspace,'AutoJs6-Documentation/api/ai.md')).split('## Node.js, Bun 和 Worker')[1];
assert(docs);
for(const name of [...names,'hasAccess','requestAccess','close']) {
  assert.equal(typeof ai[name],'function');assert(types.includes(name+'('),name+' declaration');assert(docs.includes(name),name+' documentation');
}
assert.equal(typeof ai,'function');assert.equal(ai.ask,ai);assert.equal(ai.agent,undefined);
assert(types.includes('readonly result: Promise<Response>')&&types.includes('close(): Promise<void>'));
console.log(JSON.stringify({registeredAiCoreNames:5,sessionMethods:4,runtimeHelpers:3,sharedMainBunWorkerFactory:true,agentParity:false}));
