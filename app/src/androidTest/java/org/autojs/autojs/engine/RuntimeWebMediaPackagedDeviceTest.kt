package org.autojs.autojs.engine

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.pm.PackageInfoCompat
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

/** Real APK Builder output, including the external runtime and rewritten private providers. */
class RuntimeWebMediaPackagedDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val args=InstrumentationRegistry.getArguments()
    private val kind get()=requireNotNull(args.getString("runtimeKind")).also{require(it in listOf("node","worker","bun"))}
    private val engine get()=if(kind=="bun")"bun" else "node"
    private val packageName get()="org.autojs.autojs6.interop.$engine"
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)

    @Test fun produce() {
        val root=File(context.cacheDir,"runtime-web-media-package-$kind").apply{mkdirs()}
        val output=File(context.getExternalFilesDir(null),"runtime-web-media-$kind.apk")
        try {
            val entry=if(kind=="bun")"main.bun.js" else "main.cjs"
            val config=ProjectConfig().setName("Web media $kind").setPackageName(packageName)
                .setVersionName("1.0.0").setVersionCode(1).setMainScriptFileName(entry).setProjectType(engine)
                .setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList())
                .setPermissions(listOf(android.Manifest.permission.INTERNET,android.Manifest.permission.RECORD_AUDIO))
            config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
            if(engine=="node")config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(listOf("ui")).setTimeoutMs(180000L))
            else {
                config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("ui.layout","ui.compose")).setTimeoutMs(180000L))
                val sdk=File(context.getExternalFilesDir(null),"interop-host-client.mjs")
                assertEquals(args.getString("sdkSha256"),RuntimeWebMediaProof.sha256(sdk.readBytes()))
                sdk.copyTo(File(root,"host-client.mjs"),overwrite=true)
            }
            val imports=if(engine=="node")"const ui=require('ui'),compose=require('compose');" else "const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ui=host.ui,compose=host.compose;"
            val body=imports+"try {"+"""
                const identity=await ui.context();if(identity.packageName!=='$packageName'||identity.applicationVersionCode!==1||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                const root=await ui.inflate('<vertical><input id="fixture" singleLine="true" text="Fixture-$kind"/><webview id="browser" h="300" javaScriptEnabled="true"/></vertical>');
                const window=await ui.showLayout(root),web=await root.findView('browser'),field=await root.findView('fixture'),jobs=[],captures=[];let selected;
                async function wait(fn,label){const end=Date.now()+60000;while(!await fn()){if(Date.now()>end)throw Error(label);await new Promise(r=>setTimeout(r,60));}}
                function same(a,b,label){if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label);}
                try {
                  await wait(async()=>String(await field.text).startsWith('http://127.0.0.1:'),'Fixture URL');const base=await field.text;
                  await web.setWebChromeClient({async onShowFileChooser(event){selected=await this.chooseFiles({...event,durationLimitSeconds:4,videoQuality:0,timeoutMs:180000});return selected;}},{callbackTimeoutMs:180000});
                  for(const media of ['video','audio']){
                    selected=null;await web.loadUrl(base+'/capture-'+media);
                    await wait(async()=>await web.evaluateJavascript('document.readyState==="complete"&&document.title==="Media '+media+'"&&!!document.getElementById("capture")')===true,'Media page');
                    await web.evaluateJavascript('document.getElementById("capture").textContent="Capture-pack-$kind-'+media+'"');
                    await wait(async()=>{const error=await web.evaluateJavascript('window.captureError');if(error)throw Error(error);return await web.evaluateJavascript('window.uploaded')===true;},'Media upload');
                    const info=await web.evaluateJavascript('window.mediaInfo');if(!selected||selected.length!==1||!Number.isFinite(info.duration)||info.duration<0.5)throw Error('Media result');captures.push(selected[0]);
                    same([...await web.readDocument(selected[0],0,16)],info.head,'Native header');same([...await web.readDocument(selected[0],info.size-16,16)],info.tail,'Native tail');
                    const job=await web.download({url:selected[0],mimeType:info.mime,fileName:'autojs6-webmedia-$kind-'+media+'-'+Date.now()+(media==='audio'?'.m4a':'.mp4')});jobs.push(job.id);
                    await wait(async()=>{const state=await web.getDownload(job.id);if(state.status==='failed')throw Error(state.error||state.reason);if(state.status==='successful'){if(state.bytesDownloaded!==info.size)throw Error('Copied size');return true;}return false;},'Media copy');
                    same([...await web.readDownload(job.id,0,16)],info.head,'Downloaded header');await web.cancelDownload(job.id);
                    await field.set({text:'Media-proof-$kind-'+media+'-'+info.size+'-'+info.sha256});await wait(async()=>await field.text==='Media-checked-'+media,'Media hash acknowledgement');
                  }
                } finally {for(const id of jobs)await web.cancelDownload(id).catch(()=>{});await window.close();await root.dispose();}
                const reader=await ui.createView('WebView');try{for(const uri of captures){let failed=false;try{await reader.readDocument(uri);}catch(_){failed=true;}if(!failed)throw Error('Expired capture still readable');}}finally{await reader.dispose();}
                const count=compose.state(0);const session=await compose.mount(()=>compose.Column({},compose.Text({text:'Media-$kind-count:'+count.value}),compose.Button({onClick(){count.value++;}},compose.Text({text:'Media-$kind-next'}))));
                await wait(()=>count.value===2,'Compose interaction');await session.close();
            """.trimIndent()+"} catch(error){const view=await ui.createView('Text',{text:'Media failure: '+String(error.stack||error)});await ui.showLayout(view);await new Promise(r=>setTimeout(r,40000));throw error;}"
            val source=if(kind=="worker"){
                val inner="const {parentPort}=require('node:worker_threads');(async()=>{$body})().then(()=>parentPort.postMessage('done')).catch(e=>{console.error(e.stack);throw e;});"
                val outer="const {Worker}=require('node:worker_threads');new Worker(${JSONObject.quote(inner)},{eval:true}).on('error',e=>{throw e;});"
                "\"node\";const {Worker}=require('node:worker_threads');new Worker(${JSONObject.quote(outer)},{eval:true}).on('error',e=>{console.error(e.stack);throw e;});"
            }else "\"$engine\"; (async()=>{$body})().catch(e=>{console.error(e.stack);throw e;});"
            File(root,entry).writeText(source);File(root,"project.json").writeText(config.toJson(false))
            val result=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
            assertTrue("APK Builder: ${result.errors}",result.isSuccess)
            val archive=requireNotNull(context.packageManager.getPackageArchiveInfo(output.path,android.content.pm.PackageManager.GET_PERMISSIONS))
            assertEquals(packageName,archive.packageName);assertEquals(1L,PackageInfoCompat.getLongVersionCode(archive))
            assertTrue(archive.requestedPermissions.orEmpty().contains(android.Manifest.permission.RECORD_AUDIO))
            assertFalse(archive.requestedPermissions.orEmpty().contains("org.autojs.permission.PLUGIN"))
            File(output.parentFile,output.name+".json").writeText(JSONObject().put("kind",kind).put("packageName",packageName).put("runtimeVersionCode",BuildConfig.VERSION_CODE)
                .put("applicationVersionCode",1).put("sha256",RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
        }finally{check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
    }

    @Test fun runInstalledOutput()=harness.withFixture {RuntimeWebFixtureServer().use {server->
        val results=JSONArray()
        RecordingAudioDeviceTest.Tone(context,true).use {tone->
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun failed(){controls.find{it.text?.toString()?.startsWith("Media failure:")==true}?.let{fail(it.text.toString())}}
            fun wait(label:String,predicate:()->Boolean){controls.await(label,60000){failed();predicate()}}
            wait("Packaged media fixture"){controls.labelled("Fixture-$kind")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,server.url)})==true}
            for(media in listOf("video","audio")){
                tone.enabled=media=="audio"
                controls.click("Capture-pack-$kind-$media")
                fun done():Boolean{failed();return controls.find{it.text?.toString()?.startsWith("Media-proof-$kind-$media-")==true}!=null}
                if(media=="video")controls.video(::done)else controls.audio(::done)
                val field=requireNotNull(controls.find{it.text?.toString()?.startsWith("Media-proof-$kind-$media-")==true})
                val match=requireNotNull(Regex("Media-proof-$kind-$media-(\\d+)-([a-f0-9]{64})").matchEntire(field.text.toString()))
                val bytes=RuntimeWebMediaProof.upload(server.uploads.last(),match.groupValues[1].toInt())
                assertEquals(match.groupValues[2],RuntimeWebMediaProof.sha256(bytes))
                results.put(JSONObject().put("media",media).put("bytes",bytes.size).put("sha256",match.groupValues[2]).put("decoded",RuntimeWebMediaProof.inspect(context,media,bytes)))
                bytes.fill(0);server.uploads.clear()
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Media-checked-$media")}))
            }
            tone.enabled=false
            wait("Packaged Compose"){controls.labelled("Media-$kind-count:0")!=null}
            controls.click("Media-$kind-next");wait("Packaged state"){controls.labelled("Media-$kind-count:1")!=null}
            harness.automation.setRotation(1);wait("State after media capture and rotation"){controls.labelled("Media-$kind-count:1")!=null}
            controls.click("Media-$kind-next");wait("Packaged media UI closed"){controls.labelled("Media-$kind-next")==null}
            File(context.getExternalFilesDir(null),"runtime-web-media-packaged-$kind.json").writeText(results.toString(2))
        }
    }}
}
