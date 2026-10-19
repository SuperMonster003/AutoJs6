package org.autojs.autojs.engine

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.apkbuilder.template.RemoteApkBuildClient
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeShellPackagedDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    private fun packageName(mode:String)="org.autojs.autojs6.interop.shell.$mode"
    @Test fun produce() {
        for(mode in listOf("bun","worker")) {
            val root=File(context.cacheDir,"shell-package-$mode").apply{mkdirs()}
            val output=File(context.getExternalFilesDir(null),"runtime-shell-$mode.apk");val engine=if(mode=="bun")"bun" else "node"
            try {
                val entry=if(mode=="bun")"main.bun.js" else "main.cjs"
                val config=ProjectConfig().setName("Shell $mode Test").setPackageName(packageName(mode)).setVersionName("1.0.0").setVersionCode(1)
                    .setMainScriptFileName(entry).setProjectType(engine).setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList()).setPermissions(listOf(Manifest.permission.INTERNET))
                config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
                if(mode=="bun")config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("shell.control","shell.shizuku","ui.layout")).setTimeoutMs(180000L))
                else config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(listOf("shell","shell.shizuku","ui")).setTimeoutMs(180000L))
                if(mode=="bun") {
                    val sdk=File(context.getExternalFilesDir(null),"runtime-shell-sdk.mjs")
                    assertEquals(InstrumentationRegistry.getArguments().getString("sdkSha256"),RuntimeWebMediaProof.sha256(sdk.readBytes()))
                    sdk.copyTo(File(root,"host-client.mjs"),overwrite=true)
                }
                val checks="""
                    function eq(a,b,label){if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label+': '+JSON.stringify(a));}
                    async function rejects(fn){try{await fn();}catch(e){return e;}throw Error('Expected rejection');}
                    const plain=await(async()=>{${RuntimeShellPrograms.exercise(false)}})();
                    eq(await shizuku.requestPermission({timeoutMs:20000}),true,'Shizuku consent');
                    const remote=await(async()=>{${RuntimeShellPrograms.exercise(true)}})();return {plain,remote};
                """.trimIndent()
                val runner=if(mode=="bun")"const runShell=async()=>{const shell=host.shell,shizuku=host.shizuku;$checks};" else {
                    val child="const {parentPort}=require('node:worker_threads'),shell=require('autojs6:shell'),shizuku=require('autojs6:shizuku');(async()=>{$checks})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e});"
                    """const {Worker}=require('node:worker_threads');const runShell=()=>new Promise((resolve,reject)=>{let result;const w=new Worker(${JSONObject.quote(child)},{eval:true});w.on('message',v=>result=v);w.on('error',reject);w.on('exit',code=>code===0?resolve(result):reject(Error('Worker exit '+code)));});"""
                }
                val imports=if(mode=="bun")"const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ui=host.ui;" else "const ui=require('ui');"
                File(root,entry).writeText("""
                    "${if(mode=="bun")"bun" else "nodejs"}";(async()=>{
                    $imports $runner
                    const pause=ms=>new Promise(r=>setTimeout(r,ms));const field=await ui.createView('Input',{text:'Shell ready $mode'}),window=await ui.showLayout(field);
                    try {
                      const end=Date.now()+90000;while(!String(await field.text).startsWith('RUN ')){if(Date.now()>end)throw Error('Checkpoint');await pause(100);}
                      const url=String(await field.text).slice(4),identity=await ui.context();
                      if(identity.packageName!==${JSONObject.quote(packageName(mode))}||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                      const result=await runShell();await window.close();await field.dispose();
                      await fetch(url+'/upload',{method:'POST',body:JSON.stringify({identity,result,mode:'$mode'})});
                    }catch(error){await field.set({text:'Shell failure '+String(error.stack||error)});await pause(60000);throw error;}
                    })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
                """.trimIndent())
                File(root,"project.json").writeText(config.toJson(false))
                val result=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
                assertTrue("APK Builder $mode: ${result.errors}",result.isSuccess)
                File(output.parentFile,output.name+".json").writeText(JSONObject().put("packageName",packageName(mode)).put("runtimeVersionCode",BuildConfig.VERSION_CODE).put("sha256",RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
            }finally{check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
        }
    }
    @Test fun installedBunAndWorkerShellsAfterRelaunch()=RuntimeShellDeviceTest().approvePermission {harness.withFixture {RuntimeWebFixtureServer().use {server->
        val proofs=JSONArray();var count=0
        for(mode in listOf("bun","worker"))for(expected in 1..2) {
            val packageName=packageName(mode);context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun wait(label:String,fn:()->Boolean)=controls.await(label,100000){controls.find{it.text?.toString()?.startsWith("Shell failure ")==true}?.let{fail(it.text.toString())};fn()}
            wait("Packaged shell ready"){controls.labelled("Shell ready $mode")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"RUN "+server.url)})==true}
            count++;wait("Packaged shell proof"){server.uploads.size==count}
            val proof=JSONObject(server.uploads.last().toString(Charsets.UTF_8));val result=proof.getJSONObject("result")
            for(kind in listOf("plain","remote")){
                val row=result.getJSONObject(kind);assertTrue(row.getBoolean("closed"));assertTrue(row.getBoolean("abort"));assertTrue(row.getBoolean("timeout"));assertTrue(row.getBoolean("pty"));assertEquals(70000,row.getInt("binaryBytes"))
                if(kind=="remote")assertEquals(2000,row.getInt("uid"))else assertEquals(row.getInt("hostUid"),row.getInt("uid"))
            }
            assertEquals(packageName,proof.getJSONObject("identity").getString("packageName"));proofs.put(proof)
            ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("am force-stop $packageName")).use{it.readBytes()}
        }
        File(context.getExternalFilesDir(null),"runtime-shell-packaged.json").writeText(proofs.toString(2))
    }}}
}
