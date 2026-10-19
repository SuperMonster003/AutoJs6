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

class BunStoragePackagedDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val packageName="org.autojs.autojs6.interop.bun.storage"
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    @Test fun produce() {
        val root=File(context.cacheDir,"bun-storage-package").apply{mkdirs()}
        val output=File(context.getExternalFilesDir(null),"bun-storage.apk")
        try {
            val config=ProjectConfig().setName("Bun Storage Test").setPackageName(packageName).setVersionName("1.0.0").setVersionCode(1)
                .setMainScriptFileName("main.bun.js").setProjectType("bun").setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList())
                .setPermissions(listOf(Manifest.permission.INTERNET))
            config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
            config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("files.control","storage.control","ui.layout")).setTimeoutMs(180000L))
            val sdk=File(context.getExternalFilesDir(null),"bun-storage-sdk.mjs")
            assertEquals(InstrumentationRegistry.getArguments().getString("sdkSha256"),RuntimeWebMediaProof.sha256(sdk.readBytes()))
            sdk.copyTo(File(root,"host-client.mjs"),overwrite=true)
            File(root,"main.bun.js").writeText("""
                "bun";(async()=>{
                const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),files=host.files,storage=host.storage,storages=host.storages,ui=host.ui;
                const pause=ms=>new Promise(r=>setTimeout(r,ms));function eq(x,y,label){if(JSON.stringify(x)!==JSON.stringify(y))throw Error(label+': '+JSON.stringify(x));}
                async function rejects(action){try{await action();}catch(e){return e;}throw Error('Expected rejection');}
                const store=storage.open('owned-packaged-storage'),previous=await store.get('counter',0);
                const field=await ui.createView('Input',{text:'Bun storage ready '+previous}),window=await ui.showLayout(field);
                try {
                  const end=Date.now()+90000;while(!String(await field.text).startsWith('RUN ')){if(Date.now()>end)throw Error('Checkpoint');await pause(100);}
                  const base=String(await field.text).slice(4),identity=await ui.context();
                  eq(identity.packageName,'$packageName','Packaged identity');eq(identity.runtimeVersionCode,${BuildConfig.VERSION_CODE},'Runtime kit');
                  if(previous>0)eq(await files.read('owned-session.txt'),String(previous),'Persistent file after restart');
                  await files.write('owned-session.txt',String(previous+1));await store.putSync('counter',previous+1);
                  const content=previous===0?await(async()=>{${BunStoragePrograms.files}})():{files:true,persistence:true};
                  await window.close();await field.dispose();await fetch(base+'/upload',{method:'POST',body:JSON.stringify({identity,content,counter:previous+1,durableFile:await files.path('owned-session.txt')})});
                }catch(error){await field.set({text:'Bun storage failure '+String(error.stack||error)});await pause(60000);throw error;}
                })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
            """.trimIndent())
            File(root,"project.json").writeText(config.toJson(false))
            val result=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
            assertTrue("APK Builder: ${result.errors}",result.isSuccess)
            File(output.parentFile,output.name+".json").writeText(JSONObject().put("packageName",packageName).put("runtimeVersionCode",BuildConfig.VERSION_CODE).put("sha256",RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
        }finally{check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
    }
    @Test fun installedOutputPersistsFilesAndStorageAfterProcessRestart()=harness.withFixture {RuntimeWebFixtureServer().use {server->
        val proofs=JSONArray()
        for(expected in 1..2) {
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun wait(label:String,action:()->Boolean)=controls.await(label,100000) {
                controls.find{it.text?.toString()?.startsWith("Bun storage failure ")==true}?.let{fail(it.text.toString())};action()
            }
            wait("Packaged Bun storage ready") { controls.labelled("Bun storage ready ${expected-1}")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"RUN "+server.url)})==true }
            wait("Packaged storage proof"){server.uploads.size==expected}
            val proof=JSONObject(server.uploads.last().toString(Charsets.UTF_8))
            assertEquals(expected,proof.getInt("counter"));assertTrue(proof.getJSONObject("content").getBoolean("files"))
            assertEquals(packageName,proof.getJSONObject("identity").getString("packageName"))
            assertTrue(proof.getString("durableFile"),proof.getString("durableFile").contains("/$packageName/"))
            proofs.put(proof)
            ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("am force-stop $packageName")).use{it.readBytes()}
        }
        File(context.getExternalFilesDir(null),"bun-storage-packaged.json").writeText(proofs.toString(2))
    }}
}
