package org.autojs.autojs.engine

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.apkbuilder.template.RemoteApkBuildClient
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeWebStreamsPackagedDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val args=InstrumentationRegistry.getArguments()
    private val kind get()=requireNotNull(args.getString("runtimeKind")).also{require(it in listOf("node","worker","bun"))}
    private val engine get()=if(kind=="bun")"bun" else "node"
    private val packageName get()="org.autojs.autojs6.interop.$engine"
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)

    @Test fun produce() {
        val root=File(context.cacheDir,"runtime-web-streams-package-$kind").apply{mkdirs()}
        val output=File(context.getExternalFilesDir(null),"runtime-web-streams-$kind.apk")
        try {
            val entry=if(kind=="bun")"main.bun.js" else "main.cjs"
            val config=ProjectConfig().setName("Web streams $kind").setPackageName(packageName)
                .setVersionName("1.0.0").setVersionCode(1).setMainScriptFileName(entry).setProjectType(engine)
                .setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList())
                .setPermissions(listOf(android.Manifest.permission.INTERNET,android.Manifest.permission.RECORD_AUDIO,android.Manifest.permission.CAMERA))
            config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
            if(engine=="node")config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(listOf("ui")).setTimeoutMs(180000L))
            else {
                config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("ui.layout","ui.compose")).setTimeoutMs(180000L))
                val sdk=File(context.getExternalFilesDir(null),"interop-host-client.mjs")
                assertEquals(args.getString("sdkSha256"),RuntimeWebMediaProof.sha256(sdk.readBytes()));sdk.copyTo(File(root,"host-client.mjs"),overwrite=true)
            }
            val imports=if(engine=="node")"const ui=require('ui'),compose=require('compose');" else "const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ui=host.ui,compose=host.compose;"
            val body=imports+"try {"+"""
                const identity=await ui.context();if(identity.packageName!=='$packageName'||identity.applicationVersionCode!==1||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                const field=await ui.createView('Input',{text:'Streams fixture $kind'}),fieldWindow=await ui.showLayout(field);
                async function wait(fn,label){const end=Date.now()+60000;while(!await fn()){if(Date.now()>end)throw Error(label);await new Promise(r=>setTimeout(r,60));}}
                await wait(async()=>String(await field.text).startsWith('http://127.0.0.1:'),'Fixture URL');const base=await field.text;
                const result=await (async()=>{
            """.trimIndent()+RuntimeWebStreamsFixture.setup("'+base+'")+RuntimeWebStreamsFixture.record()+"""
                })();await field.set({text:'Streams proof $kind '+JSON.stringify(result)});
                await wait(async()=>await field.text==='Streams checked','Stream validation');await fieldWindow.close();await field.dispose();
                const count=compose.state(0);const session=await compose.mount(()=>compose.Column({},compose.Text({text:'Streams count:'+count.value}),compose.Button({onClick(){count.value++;}},compose.Text({text:'Streams next'}))));
                await wait(()=>count.value===2,'Compose interaction');await session.close();
            """.trimIndent()+"}catch(error){const view=await ui.createView('Text',{text:'Streams failure: '+String(error.stack||error)});await ui.showLayout(view);await new Promise(r=>setTimeout(r,40000));throw error;}"
            val source=if(kind=="worker"){
                val inner="(async()=>{$body})().catch(e=>{throw e;});"
                val outer="const {Worker}=require('node:worker_threads');new Worker(${JSONObject.quote(inner)},{eval:true}).on('error',e=>{throw e;});"
                "\"node\";const {Worker}=require('node:worker_threads');new Worker(${JSONObject.quote(outer)},{eval:true}).on('error',e=>{throw e;});"
            }else "\"$engine\";(async()=>{$body})().catch(e=>{throw e;});"
            File(root,entry).writeText(source);File(root,"project.json").writeText(config.toJson(false))
            val result=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
            assertTrue("APK Builder: ${result.errors}",result.isSuccess)
            val archive=requireNotNull(context.packageManager.getPackageArchiveInfo(output.path,android.content.pm.PackageManager.GET_PERMISSIONS))
            assertEquals(packageName,archive.packageName)
            assertTrue(archive.requestedPermissions.orEmpty().toList().containsAll(listOf(android.Manifest.permission.CAMERA,android.Manifest.permission.RECORD_AUDIO)))
            assertTrue("Chromium audio dependency",archive.requestedPermissions.orEmpty().contains(android.Manifest.permission.MODIFY_AUDIO_SETTINGS))
            File(output.parentFile,output.name+".json").writeText(JSONObject().put("kind",kind).put("packageName",packageName).put("runtimeVersionCode",BuildConfig.VERSION_CODE)
                .put("sha256",RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
        }finally{check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
    }

    @Test fun runInstalledOutput()=harness.withFixture {RuntimeWebFixtureServer().use {server->
        RecordingAudioDeviceTest.Tone(context,true).use {tone->tone.enabled=true
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun wait(label:String,predicate:()->Boolean){controls.await(label,80000){controls.permission(true);controls.find{it.text?.toString()?.startsWith("Streams failure:")==true}?.let{fail(it.text.toString())};predicate()}}
            wait("Packaged stream fixture"){controls.labelled("Streams fixture $kind")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,server.url)})==true}
            wait("Packaged stream result"){controls.find{it.text?.toString()?.startsWith("Streams proof $kind ")==true}!=null}
            val field=requireNotNull(controls.find{it.text?.toString()?.startsWith("Streams proof $kind ")==true})
            val result=JSONObject(field.text.toString().removePrefix("Streams proof $kind "));val bytes=server.uploads.single()
            assertEquals(result.getString("sha256"),RuntimeWebMediaProof.sha256(bytes));result.put("decoded",RuntimeWebStreamsFixture.inspect(context,bytes))
            bytes.fill(0);server.uploads.clear();tone.enabled=false
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Streams checked")}))
            wait("Compose after streaming"){controls.labelled("Streams count:0")!=null};controls.click("Streams next");wait("Compose state"){controls.labelled("Streams count:1")!=null}
            harness.automation.setRotation(1);wait("Compose state after rotation"){controls.labelled("Streams count:1")!=null};controls.click("Streams next");wait("Packaged UI cleanup"){controls.labelled("Streams next")==null}
            File(context.getExternalFilesDir(null),"runtime-web-streams-packaged-$kind.json").writeText(result.toString(2))
        }
    }}
}
