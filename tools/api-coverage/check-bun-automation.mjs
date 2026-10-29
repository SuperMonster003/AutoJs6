import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {execFileSync} from 'node:child_process';
const host=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),bun=path.resolve(host,'../AutoJs6-Plugin-Bun-Runtime'),node=path.resolve(host,'../AutoJs6-Plugin-NodeJs-Runtime');
execFileSync(process.execPath,[path.join(bun,'tools/host-client/sync-automation.mjs'),'--check','--node-root',node],{stdio:'inherit'});
const sdk=await import(pathToFileURL(path.join(bun,'app/src/main/assets/autojs6/host-client.mjs')));
const source=fs.readFileSync(path.join(host,'app/src/main/java/org/autojs/autojs/engine/BunAutomationBridge.kt'),'utf8');
const protocol=fs.readFileSync(path.join(host,'app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt'),'utf8');
for(const [cap,key,module] of [['app.control','APP_CONTROL','app'],['accessibility.control','ACCESSIBILITY_CONTROL','accessibility'],['clipboard.control','CLIPBOARD_CONTROL','clipboard']]){
 const names=text=>[...text.matchAll(/"([A-Za-z]+)"/g)].map(x=>x[1]).sort();
 const actual=names(source.match(new RegExp(`Grants\\.${key} to setOf\\(([^)]*)\\)`))[1]);
 assert.deepEqual([...sdk.HOST_API_METHODS[cap]].sort(),actual,cap+' client/host drift');
 const native=names(protocol.match(new RegExp(`val ${module}Methods = setOf\\(([\\s\\S]*?)\\)`))[1]);
 assert(actual.every(name=>native.includes(name)),cap+' must use real Node provider routes');
}
const routes=['app.control','accessibility.control','clipboard.control'].reduce((sum,cap)=>sum+sdk.HOST_API_METHODS[cap].length,0);
console.log(`Bun automation: ${routes} native routes and generated public adapters/declarations match Node`);
