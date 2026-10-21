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

class RuntimeAiPackagedDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val harness = CaptureWorkerBridgeDeviceTest()
    private val controls = RuntimeWebMediaControls(harness)
    private fun packageName(mode: String) = "org.autojs.autojs6.interop.aicore.$mode"

    @Test fun produce() {
        for (mode in listOf("bun", "worker")) {
            val root = File(context.cacheDir, "ai-core-package-${UUID.randomUUID()}").canonicalFile.apply { mkdirs() }
            val output = File(context.getExternalFilesDir(null), "runtime-ai-core-$mode.apk")
            try {
                val bun = mode == "bun"; val entry = if (bun) "main.bun.js" else "main.cjs"
                val config = ProjectConfig().setName("AI core $mode Test").setPackageName(packageName(mode)).setVersionName("1.0.0").setVersionCode(1)
                    .setMainScriptFileName(entry).setProjectType(if (bun) "bun" else "node").setSourcePath(root.path).setAbis(listOf("arm64-v8a"))
                    .setLibs(emptyList()).setPermissions(listOf(Manifest.permission.INTERNET))
                config.launchConfig.apply { isSplashVisible = false; isLogsVisible = false; isRunOnBoot = false; requiresSharedStorage = false }
                if (bun) config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(RuntimeAiPrograms.grants).setTimeoutMs(600000L))
                else config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(RuntimeAiPrograms.capabilities).setTimeoutMs(600000L))
                if (bun) {
                    val sdk = File(context.getExternalFilesDir(null), "runtime-ai-core-sdk.mjs")
                    val installed = context.createPackageContext("io.github.supermonster003.autojs6.plugin.bun.runtime", 0).assets.open("autojs6/host-client.mjs").use { it.readBytes() }
                    assertArrayEquals(installed, sdk.readBytes()); sdk.copyTo(File(root, "host-client.mjs"))
                }
                val program = RuntimeAiPrograms.exercise(full = false)
                val child = "const {parentPort}=require('node:worker_threads'),ai=require('ai');(async()=>{$program})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e;});"
                val imports = if (bun) "const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ai=host.ai,ui=host.ui;"
                    else "const ai=require('autojs6:ai'),ui=require('ui'),{Worker}=require('node:worker_threads');"
                val run = "const main=await(async()=>{$program})();" + if (bun) "const result={main};" else
                    "const w=new Worker(${JSONObject.quote(child)},{eval:true});let worker;try{worker=await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});}finally{await w.terminate();}const result={main,worker};"
                File(root, entry).writeText("""
                    "${if (bun) "bun" else "nodejs"}";(async()=>{
                        $imports
                        const pause=ms=>new Promise(r=>setTimeout(r,ms)),field=await ui.createView('Input',{text:'AI core ready $mode'}),window=await ui.showLayout(field);
                        try {
                            const end=Date.now()+90000;while(!String(await field.text).startsWith('RUN ')){if(Date.now()>end)throw Error('Checkpoint timeout');await pause(100);}
                            const parts=String(await field.text).split(' '),round=Number(parts[1]),url=parts[2],identity=await ui.context();
                            if(identity.packageName!==${JSONObject.quote(packageName(mode))}||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                            const before=await ai.hasAccess();
                            if(round===0||round===3){
                                if(before)throw Error('Permission should be absent');
                                if(round===0&&await ai.requestAccess())throw Error('Expected Android permission denial');
                                const error=await ai.catalog().then(()=>null,e=>e);
                                if(error?.code!=='PERMISSION_DENIED')throw Error('Unauthorized AI catalogue');
                                await ai.close();await fetch(url+'/upload',{method:'POST',body:JSON.stringify({identity,mode:'$mode',round,before,denied:error.code})});
                            }else{
                                if(!before&&!await ai.requestAccess())throw Error('Android AI grant denied');
                                $run
                                await fetch(url+'/upload',{method:'POST',body:JSON.stringify({identity,result,mode:'$mode',round,before})});
                            }
                            await window.close();await field.dispose();
                        }catch(error){await field.set({text:'AI core failure '+String(error.stack||error)});await pause(60000);throw error;}
                    })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
                """.trimIndent())
                File(root, "project.json").writeText(config.toJson(false))
                val built = RemoteApkBuildClient.buildBlocking(context, config, output, AtomicBoolean(false), RemoteApkBuildClient.ProgressListener {}, ApkBuilderTemplatePluginIds.PACKAGE_NAME)
                assertTrue("APK Builder $mode: ${built.errors}", built.isSuccess)
                val manifest = requireNotNull(context.packageManager.getPackageArchiveInfo(output.path, android.content.pm.PackageManager.GET_PERMISSIONS))
                assertTrue("AI capability must reach the generated Android manifest",
                    manifest.requestedPermissions.orEmpty().contains("org.autojs.permission.AI_RUNTIME"))
                File(output.parentFile, output.name + ".json").writeText(JSONObject().put("packageName", packageName(mode))
                    .put("runtimeVersionCode", BuildConfig.VERSION_CODE).put("sha256", RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
            } finally { check(root.parentFile == context.cacheDir.canonicalFile); root.deleteRecursively() }
        }
    }

    @Test fun installedApplicationsEnforcePermissionAndKeepNativeCallsAcrossRelaunch() = harness.withFixture(setup = { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }) {
        RuntimeWebFixtureServer().use { server ->
            fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(command)).use { it.readBytes() }
            val proofs = JSONArray()
            for (mode in listOf("bun", "worker")) {
                val pkg = packageName(mode)
                try {
                    for (round in 0..3) {
                        shell("am force-stop $pkg")
                        if (round == 0 || round == 3) shell("pm revoke $pkg org.autojs.permission.AI_RUNTIME")
                        context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        fun receipt() = server.uploads.map { JSONObject(it.toString(Charsets.UTF_8)) }.firstOrNull { it.optString("mode") == mode && it.optInt("round") == round }
                        fun wait(label: String, condition: () -> Boolean) = controls.await(label, 900000) {
                            controls.find { it.text?.toString()?.startsWith("AI core failure ") == true }?.let { fail(it.text.toString()) }
                            if (round <= 1) {
                                val token = if (round == 0) "permission_deny_button" else "permission_allow_button"
                                controls.find { it.viewIdResourceName?.endsWith(":id/$token") == true }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            }
                            condition()
                        }
                        wait("AI core entry $mode/$round") { controls.labelled("AI core ready $mode") != null }
                        assertTrue(controls.labelled("AI core ready $mode")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "RUN $round " + server.url)
                        }) == true)
                        wait("AI core result $mode/$round") { receipt() != null }
                        val row = receipt()!!
                        assertEquals(pkg, row.getJSONObject("identity").getString("packageName"))
                        assertEquals(BuildConfig.VERSION_CODE, row.getJSONObject("identity").getInt("runtimeVersionCode"))
                        if (round == 0 || round == 3) assertEquals("PERMISSION_DENIED", row.getString("denied"))
                        else {
                            assertEquals(round == 2, row.getBoolean("before"))
                            for (key in if (mode == "bun") listOf("main") else listOf("main", "worker")) {
                                val result = row.getJSONObject("result").getJSONObject(key)
                                assertTrue(result.toString(), result.getInt("checks") >= 18)
                                assertTrue(result.getJSONObject("stream").getInt("outputTokens") > 0)
                            }
                        }
                        proofs.put(row); File(context.getExternalFilesDir(null), "runtime-ai-core-packaged.json").writeText(proofs.toString(2))
                    }
                } finally { shell("am force-stop $pkg") }
            }
        }
    }
}
