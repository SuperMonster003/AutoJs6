import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {hostExports} from './host-exports.mjs';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'), workspace=resolve(root,'..');
const read=path=>readFileSync(path,'utf8');
const node=resolve(workspace,'AutoJs6-Plugin-NodeJs-Runtime'), bun=resolve(workspace,'AutoJs6-Plugin-Bun-Runtime');
execFileSync(process.execPath,[resolve(bun,'tools/host-client/sync-ai.mjs'),'--check'],{stdio:'inherit'});
const source=read(resolve(node,'app/src/main/cpp/node_bridge_sources.cpp'));
const begin=source.indexOf('  // Shared AI facade.'), end=source.indexOf('  // End shared AI facade.',begin);
assert(begin>=0&&end>begin);
const agent=new Function(source.slice(begin,end)+';return __autojs6_create_ai;')()(()=>{throw Error('Unexpected host call');}).agent;
const roots=['run','create','get','list','catalog','presets','status','result','context'];
// The ai.agent object is built by AiAgent.createObject, not declared in script-modules.json: exports.json has the
// getter and the AgentRun members, not the roots, which stay listed here (Q46).
const exported=hostExports();
assert.deepEqual(exported.names('ai','Getters'),['agent']);
const runMethods=exported.returnedObject('AgentRun').members.filter(member=>member.kind==='Functions').map(member=>member.name);
assert(runMethods.length>0,'AgentRun members');
const types=read(resolve(node,'docs/nodejs/types/autojs6-node/ai.d.ts'));
const docs=read(resolve(process.argv[2]||resolve(workspace,'AutoJs6-Documentation'),'api/ai.md'));
for(const name of [...roots,'hasAccess','requestAccess','close']) {
  assert.equal(typeof agent[name],'function',name+' facade');
  assert(types.includes(name+'('),name+' declaration');
  assert(docs.includes(name),name+' documentation');
}
for(const name of runMethods) assert(types.includes(name+'(')||types.includes(name+'<'),'AgentRun '+name);
assert(types.includes('join(timeoutMs?: number): Promise<AgentResult>'));
assert(types.includes('readonly agent: AgentAPI'));
assert(Object.isFrozen(agent));
console.log(JSON.stringify({registeredAgentRoots:roots.length,runMethods:runMethods.length,runtimeHelpers:3,sharedNodeBunWorkerFactory:true,
  asynchronousTerminals:true,packagedExecutionHost:'AutoJs6 with explicit application consent'}));
