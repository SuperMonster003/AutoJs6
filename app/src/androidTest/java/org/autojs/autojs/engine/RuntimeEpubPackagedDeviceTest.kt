package org.autojs.autojs.engine

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
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

class RuntimeEpubPackagedDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    private fun packageName(mode:String)="org.autojs.autojs6.interop.epub.$mode"
    @Test fun produce() {
        for(mode in listOf("bun","worker")) {
            val root=File(context.cacheDir,"epub-package-${UUID.randomUUID()}").canonicalFile.apply{mkdirs()}
            val output=File(context.getExternalFilesDir(null),"runtime-epub-$mode.apk")
            try {
                val bun=mode=="bun";val entry=if(bun)"main.bun.js" else "main.cjs"
                val config=ProjectConfig().setName("EPUB $mode Test").setPackageName(packageName(mode)).setVersionName("1.0.0").setVersionCode(1)
                    .setMainScriptFileName(entry).setProjectType(if(bun)"bun" else "node").setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList()).setPermissions(listOf(Manifest.permission.INTERNET))
                config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
                if(bun)config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("epub.control","java.interop","ui.layout")).setTimeoutMs(180000L))
                else config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(listOf("epub","java_interop","ui","network")).setTimeoutMs(180000L))
                if(bun) {
                    val sdk=File(context.getExternalFilesDir(null),"runtime-epub-sdk.mjs")
                    assertTrue(sdk.exists());val installed=context.createPackageContext("io.github.supermonster003.autojs6.plugin.bun.runtime",0).assets.open("autojs6/host-client.mjs").use{it.readBytes()}
                    assertArrayEquals(installed,sdk.readBytes());sdk.copyTo(File(root,"host-client.mjs"))
                }
                val prelude="const expectedPackage=${JSONObject.quote(packageName(mode))};const eq=(a,b,label)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label);};"
                val program=prelude+RuntimeEpubPrograms.body()
                val worker="const {parentPort,workerData}=require('node:worker_threads'),java=require('autojs6:java'),epub=require('epub');const bookBytes=workerData.bookBytes;(async()=>{$program})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e;});"
                val imports=if(bun)"const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),java=host.java,epub=host.epub,ui=host.ui;"
                    else "const java=require('java'),epub=require('autojs6:epub'),ui=require('ui'),{Worker}=require('node:worker_threads');"
                val loadBook=if(bun)"const bookBytes=(await import('node:fs')).readFileSync(new URL('./runtime-epub-input.epub',import.meta.url));"
                    else "const bookBytes=require('node:fs').readFileSync(require('node:path').join(__dirname,'runtime-epub-input.epub'));"
                val run="const main=await(async()=>{$program})();"+if(bun)"const result={main};" else
                    "const w=new Worker(${JSONObject.quote(worker)},{eval:true,workerData:{bookBytes}});let child;try{child=await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});}finally{await w.terminate();}const result={main,worker:child};"
                File(root,entry).writeText("""
                    "${if(bun)"bun" else "nodejs"}";(async()=>{
                        $imports
                        const pause=ms=>new Promise(r=>setTimeout(r,ms));const field=await ui.createView('Input',{text:'Epub ready $mode'}),window=await ui.showLayout(field);
                        try {
                            const end=Date.now()+90000;while(!String(await field.text).startsWith('RUN ')){if(Date.now()>end)throw Error('Checkpoint timeout');await pause(100);}
                            const parts=String(await field.text).split(' '),round=Number(parts[1]),url=parts[2],identity=await ui.context();
                            if(identity.packageName!==${JSONObject.quote(packageName(mode))}||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                            $loadBook
                            $run

                            await window.close();await field.dispose();await fetch(url+'/upload',{method:'POST',body:JSON.stringify({identity,result,mode:'$mode',round})});
                        }catch(error){await field.set({text:'Epub failure '+String(error.stack||error)});await pause(60000);throw error;}
                    })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
                """.trimIndent())
                assertTrue(File(root,entry).length()<NodeRuntimeCapabilities.RUNTIME_SCRIPT_SOURCE_MAX_BYTES)
                InstrumentationRegistry.getInstrumentation().context.assets.open("runtime-epub/book.epub").use { input ->
                    File(root,"runtime-epub-input.epub").outputStream().use { input.copyTo(it) }
                }
                File(root,"NOTICE.txt").writeText(InstrumentationRegistry.getInstrumentation().context.assets.open("runtime-epub/NOTICE.txt").bufferedReader().use{it.readText()})
                File(root,"project.json").writeText(config.toJson(false))
                val built=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
                assertTrue("APK Builder $mode: ${built.errors}",built.isSuccess)
                File(output.parentFile,output.name+".json").writeText(JSONObject().put("packageName",packageName(mode)).put("runtimeVersionCode",BuildConfig.VERSION_CODE).put("sha256",RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
            } finally {check(root.parentFile==context.cacheDir.canonicalFile);root.deleteRecursively()}
        }
    }
    @Test fun installedApplicationsUseOwnContextBeforeAndAfterRelaunch()=harness.withFixture(setup={it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}) {RuntimeWebFixtureServer().use {server->
        val proofs=JSONArray()
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(command)).use{it.readBytes()}
        for(mode in listOf("bun","worker"))for(round in 1..2) {
            val pkg=packageName(mode);shell("am force-stop $pkg")
            shell("am force-stop io.github.supermonster003.autojs6.plugin.three.folio.epub")
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun receipt()=server.uploads.map{JSONObject(it.toString(Charsets.UTF_8))}.firstOrNull{it.optString("mode")==mode&&it.optInt("round")==round}
            fun wait(label:String,fn:()->Boolean)=controls.await(label,160000){controls.find{it.text?.toString()?.startsWith("Epub failure ")==true}?.let{fail(it.text.toString())};fn()}
            wait("Epub entry $mode/$round"){controls.labelled("Epub ready $mode")!=null}
            assertTrue(controls.labelled("Epub ready $mode")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"RUN $round "+server.url)})==true)
            wait("Epub result"){receipt()!=null}
            val row=receipt()!!;assertEquals(pkg,row.getJSONObject("identity").getString("packageName"));assertEquals(BuildConfig.VERSION_CODE,row.getJSONObject("identity").getInt("runtimeVersionCode"))
            for(key in if(mode=="bun")listOf("main")else listOf("main","worker")) {
                val value=row.getJSONObject("result").getJSONObject(key)
                assertEquals(pkg,value.getString("packageName"))
                assertTrue(value.getBoolean("quiet"))
                assertTrue(value.getBoolean("errors"))
                assertTrue(value.getBoolean("readerClosed"))
                assertTrue(value.getLong("coverBytes")>8)
                assertEquals(10000,value.getInt("unicodeMarkers"))
                assertTrue(value.getInt("searchHits")>0)
            }
            proofs.put(row)
            File(context.getExternalFilesDir(null),"runtime-epub-packaged.json").writeText(proofs.toString(2))
            Thread.sleep(2000);shell("am force-stop $pkg")
        }
        File(context.getExternalFilesDir(null),"runtime-epub-packaged.json").writeText(proofs.toString(2))
    }}
}
