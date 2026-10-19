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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeDeviceDialogsPackagedDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    private fun packageName(mode:String)="org.autojs.autojs6.interop.device.$mode"
    @Test fun produce() {
        for(mode in listOf("bun","worker")){
            val root=File(context.cacheDir,"device-package-${UUID.randomUUID()}").apply{mkdirs()}
            val output=File(context.getExternalFilesDir(null),"runtime-device-dialogs-$mode.apk")
            try {
                val entry=if(mode=="bun")"main.bun.js" else "main.cjs"
                val config=ProjectConfig().setName("Device dialogs $mode Test").setPackageName(packageName(mode)).setVersionName("1.0.0").setVersionCode(1)
                    .setMainScriptFileName(entry).setProjectType(if(mode=="bun")"bun" else "node").setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList()).setPermissions(listOf(Manifest.permission.INTERNET))
                config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
                if(mode=="bun")config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("device.control","device.power","device.audio","dialogs.control","ui.layout")).setTimeoutMs(180000L))
                else config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(listOf("device","device.power","media","media.audio","dialogs","ui")).setTimeoutMs(180000L))
                if(mode=="bun"){
                    val sdk=File(context.getExternalFilesDir(null),"runtime-device-dialogs-sdk.mjs")
                    assertEquals(InstrumentationRegistry.getArguments().getString("sdkSha256"),RuntimeWebMediaProof.sha256(sdk.readBytes()));sdk.copyTo(File(root,"host-client.mjs"),overwrite=true)
                }
                val prelude="const pause=ms=>new Promise(r=>setTimeout(r,ms)),eq=(a,b,l)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(l);};"
                val child="const {parentPort}=require('node:worker_threads'),device=require('autojs6:device'),dialogs=require('autojs6:dialogs');(async()=>{$prelude ${RuntimeDeviceDialogsPrograms.exercise()}})().then(result=>parentPort.postMessage(result)).catch(e=>{throw e;});"
                val runner=if(mode=="bun")"const run=async()=>{const device=host.device,dialogs=host.dialogs;$prelude ${RuntimeDeviceDialogsPrograms.exercise()}};" else
                    "const {Worker}=require('node:worker_threads');const run=()=>new Promise((resolve,reject)=>{const w=new Worker(${JSONObject.quote(child)},{eval:true});w.on('message',async result=>{await w.terminate();resolve(result);});w.on('error',reject);});"
                val imports=if(mode=="bun")"const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ui=host.ui;" else "const ui=require('ui');"
                File(root,entry).writeText("""
                    "${if(mode=="bun")"bun" else "nodejs"}";(async()=>{
                        $imports $runner
                        const pause=ms=>new Promise(r=>setTimeout(r,ms));const field=await ui.createView('Input',{text:'Device dialogs ready $mode'}),window=await ui.showLayout(field);
                        try{
                            const deadline=Date.now()+90000;while(!String(await field.text).startsWith('RUN ')){if(Date.now()>deadline)throw Error('Checkpoint');await pause(100);}
                            const url=String(await field.text).slice(4),identity=await ui.context();
                            if(identity.packageName!==${JSONObject.quote(packageName(mode))}||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                            const result=await run();
                            await window.close();await field.dispose();await fetch(url+'/upload',{method:'POST',body:JSON.stringify({identity,result,mode:'$mode'})});
                        }catch(error){await field.set({text:'Device dialogs failure '+String(error.stack||error)});await pause(60000);throw error;}
                    })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
                """.trimIndent())
                File(root,"project.json").writeText(config.toJson(false))
                val result=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
                assertTrue("APK Builder $mode: ${result.errors}",result.isSuccess)
                File(output.parentFile,output.name+".json").writeText(JSONObject().put("packageName",packageName(mode)).put("runtimeVersionCode",BuildConfig.VERSION_CODE).put("sha256",RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
            }finally{check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
        }
    }
    @Test fun installedBunAndWorkerDeviceDialogsAfterRelaunch()=harness.withFixture {RuntimeWebFixtureServer().use {server->
        val rows=JSONArray();var count=0
        for(mode in listOf("bun","worker"))for(round in 1..2){
            val packageName=packageName(mode)
            @Suppress("DEPRECATION")
            val declared=context.packageManager.getPackageInfo(packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
            assertTrue(declared.toString(),declared.containsAll(setOf(Manifest.permission.WAKE_LOCK,Manifest.permission.VIBRATE,Manifest.permission.WRITE_SETTINGS,Manifest.permission.MODIFY_AUDIO_SETTINGS)))
            ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("am force-stop $packageName")).use{it.readBytes()}
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun wait(label:String,fn:()->Boolean)=controls.await(label,100000){controls.find{it.text?.toString()?.startsWith("Device dialogs failure ")==true}?.let{fail(it.text.toString())};fn()}
            wait("Packaged device entry"){controls.labelled("Device dialogs ready $mode")!=null}
            ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("cmd appops set $packageName WRITE_SETTINGS ${if(round==1)"ignore" else "allow"}")).use{it.readBytes()}
            assertTrue(controls.labelled("Device dialogs ready $mode")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"RUN "+server.url)})==true)
            RuntimeDeviceDialogsDeviceTest().interact()
            count++;wait("Packaged device proof"){server.uploads.size==count}
            val proof=JSONObject(server.uploads.last().toString(Charsets.UTF_8));val result=proof.getJSONObject("result")
            assertEquals(12,result.getInt("dialogs"));assertTrue(result.getBoolean("quiet"))
            assertEquals(if(round==1)"permission-denied" else "written",result.getString("brightness"))
            assertEquals(packageName,proof.getJSONObject("identity").getString("packageName"));proof.put("round",round);rows.put(proof)
            ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("am force-stop $packageName")).use{it.readBytes()}
        }
        File(context.getExternalFilesDir(null),"runtime-device-dialogs-packaged.json").writeText(rows.toString(2))
    }}
}
