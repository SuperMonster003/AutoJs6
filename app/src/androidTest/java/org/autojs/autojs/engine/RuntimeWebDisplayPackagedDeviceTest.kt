package org.autojs.autojs.engine

import android.content.Intent
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

class RuntimeWebDisplayPackagedDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val args=InstrumentationRegistry.getArguments()
    private val kind get()=requireNotNull(args.getString("runtimeKind")).also{require(it in listOf("node","worker","bun"))}
    private val engine get()=if(kind=="bun")"bun" else "node"
    private val packageName get()="org.autojs.autojs6.interop.$engine"
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)

    @Test fun produce() {
        val root=File(context.cacheDir,"runtime-web-display-package-$kind").apply{mkdirs()}
        val output=File(context.getExternalFilesDir(null),"runtime-web-display-$kind.apk")
        try {
            val entry=if(kind=="bun")"main.bun.js" else "main.cjs"
            val config=ProjectConfig().setName("Web display $kind").setPackageName(packageName).setVersionName("1.0.0").setVersionCode(1)
                .setMainScriptFileName(entry).setProjectType(engine).setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList())
                .setPermissions(listOf(android.Manifest.permission.INTERNET,android.Manifest.permission.CAMERA,android.Manifest.permission.RECORD_AUDIO,android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION))
            config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
            if(engine=="node")config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(listOf("ui")).setTimeoutMs(300000L)) else {
                config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("ui.layout","ui.compose")).setTimeoutMs(300000L))
                val sdk=File(context.getExternalFilesDir(null),"interop-host-client.mjs");assertEquals(args.getString("sdkSha256"),RuntimeWebMediaProof.sha256(sdk.readBytes()));sdk.copyTo(File(root,"host-client.mjs"),overwrite=true)
            }
            val imports=if(engine=="node")"const ui=require('ui'),compose=require('compose');" else "const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ui=host.ui,compose=host.compose;"
            val stages=listOf("display","audio","camera").joinToString("\n"){media->
                val program=when(media){"display"->RuntimeWebDisplayPrograms.display("pack-$kind");"audio"->RuntimeWebDisplayPrograms.audio();else->RuntimeWebDisplayPrograms.camera()}
                "{const result=await(async()=>{const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web);"+RuntimeWebDisplayPrograms.waiters()+
                    "await web.loadUrl(base+'/index');await wait(async()=>await web.evaluateJavascript('document.readyState===\"complete\"&&document.title===\"Web callbacks\"')===true,'Page loaded');try{"+program+"}finally{await window.close();await web.dispose();}})();"+
                    "await field.set({text:'Display proof $kind $media '+JSON.stringify(result)});await wait(async()=>await field.text==='Display checked $media','Media validation');}"
            }
            val body=imports+"try{"+"""
                const identity=await ui.context();if(identity.packageName!=='$packageName'||identity.applicationVersionCode!==1||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                const field=await ui.createView('Input',{text:'Display fixture $kind'}),fieldWindow=await ui.showLayout(field);
                async function wait(fn,label){const end=Date.now()+90000;while(!await fn()){if(Date.now()>end)throw Error(label);await new Promise(r=>setTimeout(r,60));}}
                await wait(async()=>String(await field.text).startsWith('http://127.0.0.1:'),'Fixture URL');const base=await field.text;
            """.trimIndent()+stages+"""
                await fieldWindow.close();await field.dispose();const count=compose.state(0);
                const session=await compose.mount(()=>compose.Column({},compose.Text({text:'Display count:'+count.value}),compose.Button({onClick(){count.value++;}},compose.Text({text:'Display next'}))));
                await wait(()=>count.value===2,'Compose state');await session.close();
            """.trimIndent()+"}catch(error){const view=await ui.createView('Text',{text:'Display failure: '+String(error.stack||error)});await ui.showLayout(view);await new Promise(r=>setTimeout(r,40000));throw error;}"
            val source=if(kind=="worker"){
                val inner="(async()=>{$body})().catch(e=>{throw e;});"
                val outer="const {Worker}=require('node:worker_threads');new Worker(${JSONObject.quote(inner)},{eval:true}).on('error',e=>{throw e;});"
                "\"node\";const {Worker}=require('node:worker_threads');new Worker(${JSONObject.quote(outer)},{eval:true}).on('error',e=>{throw e;});"
            }else "\"$engine\";(async()=>{$body})().catch(e=>{throw e;});"
            File(root,entry).writeText(source);File(root,"project.json").writeText(config.toJson(false))
            val result=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
            assertTrue("APK Builder: ${result.errors}",result.isSuccess)
            val archive=requireNotNull(context.packageManager.getPackageArchiveInfo(output.path,android.content.pm.PackageManager.GET_PERMISSIONS));assertEquals(packageName,archive.packageName)
            assertTrue(archive.requestedPermissions.orEmpty().toSet().containsAll(setOf(android.Manifest.permission.FOREGROUND_SERVICE,android.Manifest.permission.FOREGROUND_SERVICE_CAMERA,
                android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,android.Manifest.permission.MODIFY_AUDIO_SETTINGS)))
            File(output.parentFile,output.name+".json").writeText(JSONObject().put("kind",kind).put("packageName",packageName).put("runtimeVersionCode",BuildConfig.VERSION_CODE).put("sha256",RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
        }finally{check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
    }

    @Test fun runInstalledOutput()=harness.withFixture {RuntimeWebFixtureServer().use {server->
        assertNotEquals(context.packageManager.getApplicationInfo(packageName,0).uid,instrumentation.context.applicationInfo.uid)
        RecordingAudioDeviceTest.Tone(context,true,quiet=true).use {tone->
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun failed(){controls.find{it.text?.toString()?.startsWith("Display failure:")==true}?.let{fail(it.text.toString())}}
            fun wait(label:String,predicate:()->Boolean){controls.await(label,90000){failed();controls.permission(true);harness.consentButton(true)?.let{harness.clickConsent(true)};predicate()}}
            wait("Packaged display fixture"){controls.labelled("Display fixture $kind")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,server.url)})==true}
            val results=JSONArray()
            for(media in listOf("display","audio","camera")) {
                if(media=="display")controls.click("Share pack-$kind")
                wait("Packaged $media started"){server.requests.contains("/background-ready")}
                context.startActivity(Intent().setClassName(instrumentation.context.packageName,RuntimeWebBackgroundFixtureActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                controls.await("Packaged background fixture visible"){controls.labelled("Close display fixture")!=null}
                android.os.SystemClock.sleep(300);if(media!="display")tone.pulse()
                wait("Packaged $media background operation"){server.requests.contains("/background-finished")};controls.click("Close display fixture")
                wait("Packaged $media output"){controls.find{it.text?.toString()?.startsWith("Display proof $kind $media ")==true}!=null}
                val field=requireNotNull(controls.find{it.text?.toString()?.startsWith("Display proof $kind $media ")==true});val result=JSONObject(field.text.toString().removePrefix("Display proof $kind $media "))
                val bytes=server.uploads.single();assertEquals(result.getString("sha256"),RuntimeWebMediaProof.sha256(bytes));result.put("decoded",RuntimeWebDisplayProof.inspect(context,media,bytes))
                if(media!="display")result.put("tone",tone.pulseProof())
                bytes.fill(0);server.uploads.clear();results.put(JSONObject().put("media",media).put("result",result));server.requests.clear()
                field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Display checked $media")})
            }
            tone.enabled=false;wait("Compose after background capture"){controls.labelled("Display count:0")!=null};controls.click("Display next");wait("Compose state"){controls.labelled("Display count:1")!=null}
            harness.automation.setRotation(1);wait("Compose rotation"){controls.labelled("Display count:1")!=null};controls.click("Display next");wait("Packaged cleanup"){controls.labelled("Display next")==null}
            harness.awaitNoProjection();File(context.getExternalFilesDir(null),"runtime-web-display-packaged-$kind"+RuntimeWebDisplayProof.suffix()+".json").writeText(results.toString(2))
        }
    }}
}
