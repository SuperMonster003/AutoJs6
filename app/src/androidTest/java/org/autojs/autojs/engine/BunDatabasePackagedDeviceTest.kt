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

class BunDatabasePackagedDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    private fun packageName(mode:String)="org.autojs.autojs6.interop.database.$mode"
    @Test fun produce() {
        for(mode in listOf("bun","worker")) {
            val root=File(context.cacheDir,"database-package-$mode").apply{mkdirs()}
            val output=File(context.getExternalFilesDir(null),"bun-database-$mode.apk");val engine=if(mode=="bun")"bun" else "node"
            try {
                val entry=if(mode=="bun")"main.bun.js" else "main.cjs"
                val config=ProjectConfig().setName("Database $mode Test").setPackageName(packageName(mode)).setVersionName("1.0.0").setVersionCode(1)
                    .setMainScriptFileName(entry).setProjectType(engine).setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList()).setPermissions(listOf(Manifest.permission.INTERNET))
                config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
                if(mode=="bun")config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("database.control","ui.layout")).setTimeoutMs(180000L))
                else config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(listOf("database","ui")).setTimeoutMs(180000L))
                if(mode=="bun") {
                    val sdk=File(context.getExternalFilesDir(null),"bun-database-sdk.mjs")
                    assertEquals(InstrumentationRegistry.getArguments().getString("sdkSha256"),RuntimeWebMediaProof.sha256(sdk.readBytes()))
                    sdk.copyTo(File(root,"host-client.mjs"),overwrite=true)
                }
                val checks="""
                    function eq(a,b,label){if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label+': '+JSON.stringify(a));}
                    async function rejects(fn){try{await fn();}catch(e){return e;}throw Error('Expected rejection');}
                    const checks=await(async()=>{${RuntimeDatabasePrograms.exercise("owned-api")}})();
                    const db=await sqlite('owned-counter');await db.exec('CREATE TABLE IF NOT EXISTS state(id INTEGER PRIMARY KEY, value INTEGER)');
                    const previous=await db.get('SELECT value FROM state WHERE id=?',[1]),counter=(previous?.value||0)+1;
                    if(previous)await db.run('UPDATE state SET value=? WHERE id=?',[counter,1]);else await db.run('INSERT INTO state VALUES(?,?)',[1,counter]);
                    eq(await db.get('SELECT value FROM state WHERE id=?',[1]),{value:counter},'Persistent counter');await db.close();return {...checks,counter};
                """.trimIndent()
                val runner=if(mode=="bun")"const runDatabase=async()=>{const database=host.database,sqlite=host.sqlite;$checks};" else {
                    val child="const {parentPort}=require('node:worker_threads'),database=require('autojs6:database'),sqlite=require('autojs6:sqlite');(async()=>{$checks})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e});"
                    """const {Worker}=require('node:worker_threads');const runDatabase=()=>new Promise((resolve,reject)=>{let result;const w=new Worker(${JSONObject.quote(child)},{eval:true});w.on('message',v=>result=v);w.on('error',reject);w.on('exit',code=>code===0?resolve(result):reject(Error('Worker exit '+code)));});"""
                }
                val imports=if(mode=="bun")"const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ui=host.ui;" else "const ui=require('ui');"
                File(root,entry).writeText("""
                    "${if(mode=="bun")"bun" else "nodejs"}";(async()=>{
                    $imports $runner
                    const pause=ms=>new Promise(r=>setTimeout(r,ms));const field=await ui.createView('Input',{text:'Database ready $mode'}),window=await ui.showLayout(field);
                    try {
                      const end=Date.now()+90000;while(!String(await field.text).startsWith('RUN ')){if(Date.now()>end)throw Error('Checkpoint');await pause(100);}
                      const url=String(await field.text).slice(4),identity=await ui.context();
                      if(identity.packageName!==${JSONObject.quote(packageName(mode))}||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                      const result=await runDatabase();await window.close();await field.dispose();
                      await fetch(url+'/upload',{method:'POST',body:JSON.stringify({identity,result,mode:'$mode'})});
                    }catch(error){await field.set({text:'Database failure '+String(error.stack||error)});await pause(60000);throw error;}
                    })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
                """.trimIndent())
                File(root,"project.json").writeText(config.toJson(false))
                val result=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
                assertTrue("APK Builder $mode: ${result.errors}",result.isSuccess)
                File(output.parentFile,output.name+".json").writeText(JSONObject().put("packageName",packageName(mode)).put("runtimeVersionCode",BuildConfig.VERSION_CODE).put("sha256",RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
            }finally{check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
        }
    }
    @Test fun installedBunAndWorkerOutputsPersistAfterForceStop()=harness.withFixture {RuntimeWebFixtureServer().use {server->
        val proofs=JSONArray();var count=0
        for(mode in listOf("bun","worker"))for(expected in 1..2) {
            val packageName=packageName(mode);context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun wait(label:String,fn:()->Boolean)=controls.await(label,100000){controls.find{it.text?.toString()?.startsWith("Database failure ")==true}?.let{fail(it.text.toString())};fn()}
            wait("Packaged database ready"){controls.labelled("Database ready $mode")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"RUN "+server.url)})==true}
            count++;wait("Packaged database proof"){server.uploads.size==count}
            val proof=JSONObject(server.uploads.last().toString(Charsets.UTF_8));val result=proof.getJSONObject("result")
            assertEquals(expected,result.getInt("counter"));assertTrue(result.getBoolean("typedBindings"));assertTrue(result.getBoolean("rollback"));assertEquals(2,result.getInt("multirowAffected"))
            assertEquals(packageName,proof.getJSONObject("identity").getString("packageName"));proofs.put(proof)
            ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("am force-stop $packageName")).use{it.readBytes()}
        }
        File(context.getExternalFilesDir(null),"bun-database-packaged.json").writeText(proofs.toString(2))
    }}
}
