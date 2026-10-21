import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),workspace=resolve(root,'..');
const node=resolve(workspace,'AutoJs6-Plugin-NodeJs-Runtime'),bun=resolve(workspace,'AutoJs6-Plugin-Bun-Runtime');
const read=path=>readFileSync(path,'utf8');
execFileSync(process.execPath,[resolve(bun,'tools/host-client/sync-mail.mjs'),'--check'],{stdio:'inherit'});
const source=read(resolve(node,'app/src/main/cpp/node_bridge_sources.cpp'));
const a=source.indexOf('  // Shared mail facade.'),z=source.indexOf('  // End shared mail facade.',a);
assert(a>=0&&z>a);
const factory=new Function(source.slice(a,z)+';return __autojs6_create_mail;')();
const mail=factory(()=>{throw Error('Export inspection must not call the host');});
const rhino=read(resolve(root,'app/src/main/java/org/autojs/autojs/runtime/api/augment/mail/Mail.kt'));
const registrations=rhino.split('override val selfAssignmentFunctions = listOf(')[1].split('\n    )')[0];
const names=[...registrations.matchAll(/::(\w+)\.name/g)].map(item=>item[1]);
const forwarded=[...rhino.match(/val FORWARDED: List<String> = listOf\(([\s\S]*?)\n        \)/)[1].matchAll(/"(\w+)"/g)].map(item=>item[1]);
for(const name of forwarded){names.push(name);if(!['folder','watch'].includes(name))names.push(name+'Async');}
const types=read(resolve(node,'docs/nodejs/types/autojs6-node/mail.d.ts'));
const docs=read(resolve(workspace,'AutoJs6-Documentation/api/runtimeApis.md')).split('## Node.js / Bun / Worker 的 Mail')[1];
assert(docs,'Mail runtime reference missing');
for(const name of names){
  assert.equal(typeof mail[name],'function','Missing mail export '+name);
  assert(types.includes(name+'('),'Missing mail declaration '+name);
  assert(docs.includes(name),'Missing mail documentation '+name);
}
for(const [object,nested] of [['providers',['list','get','resolve']],['accounts',['list','has']]])
  for(const name of nested){assert.equal(typeof mail[object][name],'function');assert(docs.includes(object+'.'+name));}
assert.equal(names.length,52);
assert(mail.MailError('test','AUTH_FAILED') instanceof mail.MailError);
assert(source.includes('for(const id of mailClients)closeMail(id)'));
const host=read(resolve(root,'app/src/main/java/org/autojs/autojs/engine/RuntimeMailBridge.kt'));
for(const shared of ['MailPluginHost.newClient','MailScriptArguments','MailDownloads.download','MailWatchRunner'])
  assert(host.includes(shared),'Portable mail stopped sharing '+shared);
const plugin=resolve(workspace,'AutoJs6-Plugin-Three-Stamp-Mail');
const manifest=read(resolve(plugin,'app/src/main/AndroidManifest.xml'));
const service=name=>manifest.match(new RegExp('<service\\s+android:name="\\.'+name+'"[\\s\\S]*?</service>'))?.[0];
assert(service('ThreeStampMailPluginService')?.includes('android:permission="org.autojs.permission.PLUGIN"'));
assert(service('ThreeStampMailPackagedService')?.includes('android:permission="org.autojs.plugin.mail.permission.RUNTIME"'));
assert(service('ThreeStampMailPackagedService').includes('org.autojs.plugin.MAIL_PACKAGED'));
const directory=resolve(plugin,'app/src/main/java/io/github/supermonster003/autojs6/plugin/three/stamp/mail');
assert(read(resolve(directory,'ThreeStampMailPackagedService.kt')).includes('allowStoredAccounts = false'));
const binder=read(resolve(directory,'binder/MailPluginBinder.kt'));
assert.equal(binder.split('requireStoredAccounts()').length-1,5);
assert(read(resolve(directory,'binder/PackagedCallerGuard.kt')).includes('enforceHost() != ownerUid'));
const preparer=read(resolve(root,'app/src/main/java/org/autojs/autojs/apkbuilder/template/RemoteApkBuildRequestPreparer.kt'));
assert.equal(preparer.split('MailActions.PACKAGED_PERMISSION').length-1,2);
console.log(JSON.stringify({mailRootMethods:names.length,nestedMethods:5,totalMethodNames:57,
  nodeBunWorker:true,sharedPlugin:true,packagedInlineCredentialsOnly:true}));
