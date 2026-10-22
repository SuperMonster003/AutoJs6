import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'), workspace=resolve(root,'..');
const read=path=>readFileSync(path,'utf8');
const node=resolve(workspace,'AutoJs6-Plugin-NodeJs-Runtime'), bun=resolve(workspace,'AutoJs6-Plugin-Bun-Runtime');
execFileSync(process.execPath,[resolve(bun,'tools/host-client/sync-ai.mjs'),'--check'],{stdio:'inherit'});
const source=read(resolve(node,'app/src/main/cpp/node_bridge_sources.cpp'));
const begin=source.indexOf('  // Shared AI facade.'), end=source.indexOf('  // End shared AI facade.',begin);
assert(begin>=0&&end>begin);
const agent=new Function(source.slice(begin,end)+';return __autojs6_create_ai;')()(()=>{throw Error('Unexpected host call');}).agent;
const roots=['run','create','get','list','catalog','presets','status','result','context'];
const registered=read(resolve(root,'app/src/main/java/org/autojs/autojs/runtime/api/augment/ai/AiAgent.kt'))
  .split('override val selfAssignmentFunctions = listOf(')[1].split(')')[0];
assert.deepEqual([...registered.matchAll(/::(\w+)\.name/g)].map(value=>value[1]).sort(),roots.slice().sort());
const types=read(resolve(node,'docs/nodejs/types/autojs6-node/ai.d.ts'));
const docs=read(resolve(process.argv[2]||resolve(workspace,'AutoJs6-Documentation'),'api/ai.md'));
for(const name of [...roots,'hasAccess','requestAccess','close']) {
  assert.equal(typeof agent[name],'function',name+' facade');
  assert(types.includes(name+'('),name+' declaration');
  assert(docs.includes(name),name+' documentation');
}
for(const name of ['on','once','off','respond','confirm','cancel','join']) assert(types.includes(name+'(')||types.includes(name+'<'),'AgentRun '+name);
assert(types.includes('join(timeoutMs?: number): Promise<AgentResult>'));
assert(types.includes('readonly agent: AgentAPI'));
assert(Object.isFrozen(agent));
console.log(JSON.stringify({registeredAgentRoots:roots.length,runMethods:7,runtimeHelpers:3,sharedNodeBunWorkerFactory:true,
  asynchronousTerminals:true,packagedExecutionHost:'AutoJs6 with explicit application consent'}));
