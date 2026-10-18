package org.autojs.autojs.engine

import android.Manifest
import android.content.ClipboardManager
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

class BunAutomationPackagedDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val packageName="org.autojs.autojs6.interop.bun"
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    @Test fun produce() {
        val root=File(context.cacheDir,"bun-automation-package").apply{mkdirs()}
        val output=File(context.getExternalFilesDir(null),"bun-automation.apk")
        try {
            val config=ProjectConfig().setName("Bun Automation Test").setPackageName(packageName).setVersionName("1.0.0").setVersionCode(1)
                .setMainScriptFileName("main.bun.js").setProjectType("bun").setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList())
                .setPermissions(listOf(Manifest.permission.INTERNET,Manifest.permission.QUERY_ALL_PACKAGES))
            config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
            config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("app.control","accessibility.control","accessibility.gesture","clipboard.control","ui.layout")).setTimeoutMs(180000L))
            val sdk=File(context.getExternalFilesDir(null),"interop-host-client.mjs")
            assertEquals(InstrumentationRegistry.getArguments().getString("sdkSha256"),RuntimeWebMediaProof.sha256(sdk.readBytes()))
            sdk.copyTo(File(root,"host-client.mjs"),overwrite=true)
            val code="""
                "bun";(async()=>{
                const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ui=host.ui,app=host.app,a=host.accessibility,clipboard=host.clipboard;
                const pause=ms=>new Promise(r=>setTimeout(r,ms));function eq(x,y,label){if(JSON.stringify(x)!==JSON.stringify(y))throw Error(label+': '+JSON.stringify(x));}
                const field=await ui.createView('Input',{text:'Bun automation ready'}),window=await ui.showLayout(field);
                async function wait(fn){const end=Date.now()+90000;while(!await fn()){if(Date.now()>end)throw Error('Packaged checkpoint');await pause(100);}}
                try {
                  const identity=await ui.context();eq(identity.packageName,'$packageName','Packaged identity');eq(identity.runtimeVersionCode,${BuildConfig.VERSION_CODE},'Runtime kit identity');
                  await wait(async()=>String(await field.text).startsWith('RUN '));const base=String(await field.text).slice(4);
                  const content=await(async()=>{${BunAutomationPrograms.appAndClipboard(packageName)}})();
                  const automation=await(async()=>{${BunAutomationPrograms.accessibility(instrumentation.context.packageName,false)}})();
                  await window.close();await field.dispose();
                  await fetch(base+'/upload',{method:'POST',body:JSON.stringify({automation,content,identity})});
                }catch(error){await field.set({text:'Bun automation failure '+String(error.stack||error)});await pause(60000);throw error;}
                })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
            """.trimIndent()
            File(root,"main.bun.js").writeText(code);File(root,"project.json").writeText(config.toJson(false))
            val result=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
            assertTrue("APK Builder: ${result.errors}",result.isSuccess)
            File(output.parentFile,output.name+".json").writeText(JSONObject().put("packageName",packageName).put("runtimeVersionCode",BuildConfig.VERSION_CODE).put("sha256",RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
        }finally{check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
    }
    @Test fun runInstalledOutput()=harness.withFixture {RuntimeWebFixtureServer().use {server->
        val clipboard=context.getSystemService(ClipboardManager::class.java)
        var previous:android.content.ClipData?=null
        instrumentation.runOnMainSync{previous=clipboard.primaryClip}
        try {
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun wait(label:String,action:()->Boolean)=controls.await(label,100000) {
                controls.find{it.text?.toString()?.startsWith("Bun automation failure ")==true}?.let{fail(it.text.toString())};action()
            }
            fun input(node:AccessibilityNodeInfo,value:String)=node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)})
            wait("Packaged Bun ready"){controls.labelled("Bun automation ready")?.let{input(it,"RUN "+server.url)}==true}
            wait("Packaged automation proof"){server.uploads.isNotEmpty()}
            val proof=JSONObject(server.uploads.single().toString(Charsets.UTF_8))
            assertTrue(proof.getJSONObject("automation").getBoolean("accessibility"));assertTrue(proof.getJSONObject("content").getBoolean("clipboard"))
            assertEquals(packageName,proof.getJSONObject("content").getString("packageName"))
            File(context.getExternalFilesDir(null),"bun-automation-packaged.json").writeText(proof.toString(2))
        }finally{instrumentation.runOnMainSync{previous?.let(clipboard::setPrimaryClip)?:clipboard.clearPrimaryClip()}}
    }}
}
