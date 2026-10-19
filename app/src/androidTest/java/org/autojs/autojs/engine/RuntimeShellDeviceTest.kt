package org.autojs.autojs.engine

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeShellDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    private fun grants(enabled:Boolean=true,action:()->Unit){
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE);val keys=listOf(Grants.SHELL_CONTROL,Grants.SHELL_SHIZUKU)
        val before=keys.associateWith{if(prefs.contains(it))prefs.getBoolean(it,false)else null};prefs.edit().apply{keys.forEach{putBoolean(it,enabled)}}.commit()
        try{action()}finally{prefs.edit().apply{before.forEach{(k,v)->if(v==null)remove(k)else putBoolean(k,v)}}.commit()}
    }
    internal fun run(mode:String,body:String,privileged:Boolean=true)=harness.execute(mode,
        (if(mode=="bun")"const shell=host.shell,shizuku=host.shizuku;" else "const shell=require('autojs6:shell'),shizuku=require('autojs6:shizuku');")+body,
        capabilities=if(privileged)listOf("shell","shell.shizuku")else listOf("shell"))
    internal fun <T> approvePermission(action:()->T):T {
        val stop=AtomicBoolean(false);val watcher=kotlin.concurrent.thread(isDaemon=true,name="OwnedShellPermission"){
            while(!stop.get()){
                runCatching{
                    val owned=controls.find { n->n.text?.toString()?.let{it.contains("AutoJs6")||it.contains("Shell ")}==true }!=null
                    if(owned)controls.find { node->node.packageName?.toString()=="moe.shizuku.privileged.api" && node.text?.toString() in setOf("允许","始终允许","Allow","Allow all the time") }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)}
                Thread.sleep(100)
            }
        }
        try{return action()}finally{stop.set(true);watcher.join(2000)}
    }
    @Test fun mainNodeAndBunStreamOrdinaryAndShizukuProcesses()=grants {harness.withFixture {
        val results=JSONArray()
        for(mode in listOf("node","bun"))for(remote in listOf(false,true)) {
            val body=(if(remote)"const allowed=await shizuku.requestPermission({timeoutMs:20000});eq(allowed,true,'Shizuku consent');eq((await shell.getAccess()).shizuku,true,'Actual Shizuku state');"else "")+RuntimeShellPrograms.exercise(remote)
            results.put(JSONObject().put("mode",mode).put("result",approvePermission{run(mode,body)}))
        }
        File(context.getExternalFilesDir(null),"runtime-shell-host.json").writeText(results.toString(2))
    }}
    @Test fun workersCjsEsmAndNestedReleaseShellsWithoutTouchingParents()=harness.withFixture {
        val checks="""
            const eq=(a,b,l)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(l+': '+JSON.stringify(a));};
            const rejects=async fn=>{try{await fn();}catch(e){return e;}throw Error('Expected failure');};
            const plain=await(async()=>{${RuntimeShellPrograms.exercise(false)}})();
            const remote=await(async()=>{${RuntimeShellPrograms.exercise(true)}})();
            const orphan=shell.spawn('sleep 20');await orphan.ready;parentPort.postMessage({plain,remote});
        """.trimIndent()
        val cjs="const {parentPort}=require('node:worker_threads'),shell=require('shell'),shizuku=require('autojs6:shizuku');(async()=>{$checks})().catch(e=>{throw e});"
        val esm="import {parentPort} from 'node:worker_threads';import shell from 'autojs6:shell';import shizuku from 'shizuku';$checks"
        val nested="const {Worker,parentPort}=require('node:worker_threads');const child=new Worker(${JSONObject.quote(cjs)},{eval:true});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e});"
        val result=approvePermission {run("node","""
            eq(await shizuku.requestPermission({timeoutMs:20000}),true,'Permission');
            const {Worker}=require('node:worker_threads'),results=[];
            for(const [source,esm] of [[${JSONObject.quote(cjs)},false],[${JSONObject.quote(esm)},true],[${JSONObject.quote(nested)},false]]) {
                results.push(await new Promise((resolve,reject)=>{const w=new Worker(esm?new URL('data:text/javascript,'+encodeURIComponent(source)):source,{eval:!esm});w.on('message',async v=>{await w.terminate();resolve(v);});w.on('error',reject);}));
                await pause(150);const a=shell.open(),b=shell.open();await Promise.all([a.ready,b.ready]);await Promise.all([a.close(),b.close()]);
            }
            const parent=shell.open({cmd:'cat'});await parent.ready;
            await new Promise((resolve,reject)=>{const w=new Worker("const {parentPort}=require('node:worker_threads');const p=require('shell').open();p.ready.then(()=>parentPort.postMessage(true));",{eval:true});w.on('message',async()=>{await w.terminate();resolve();});w.on('error',reject);});
            await parent.writeLine('parent survives');eq(await parent.readLine(),'parent survives','Parent ownership');await parent.close();
            return {cjs:results[0],esm:results[1],nested:results[2],parentSurvived:true,workersReleased:true};
        """.trimIndent())}
        File(context.getExternalFilesDir(null),"runtime-shell-worker.json").writeText(result.toString(2))
    }
    @Test fun bunGrantAndNodePrivilegeDenialsDoNotStartProcesses() {
        grants(false){run("bun","eq((await rejects(()=>shell.exec('true'))).code,'NOT_GRANTED','No grant');return {denied:true};",false)}
        run("node","await rejects(()=>shizuku.exec('true'));await rejects(()=>shell.execRoot('true'));return {privilegesDenied:true};",false)
    }
}
