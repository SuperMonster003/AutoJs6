package org.autojs.autojs.engine

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.Settings
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

class RuntimeFlowToolsPackagedDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val harness = CaptureWorkerBridgeDeviceTest()
    private val controls = RuntimeWebMediaControls(harness)
    private fun packageName(mode: String) = "org.autojs.autojs6.interop.flowtools.$mode"
    @Test fun produce() {
        for (mode in listOf("bun", "worker")) {
            val root = File(context.cacheDir, "flow-tools-package-${UUID.randomUUID()}").canonicalFile.apply { mkdirs() }
            val output = File(context.getExternalFilesDir(null), "runtime-flow-tools-$mode.apk")
            try {
                val bun = mode == "bun"; val entry = if (bun) "main.bun.js" else "main.cjs"
                val config = ProjectConfig().setName("Flow tools $mode Test").setPackageName(packageName(mode)).setVersionName("1.0.0").setVersionCode(1)
                    .setMainScriptFileName(entry).setProjectType(if (bun) "bun" else "node").setSourcePath(root.path).setAbis(listOf("arm64-v8a"))
                    .setLibs(emptyList()).setPermissions(listOf(Manifest.permission.INTERNET, Manifest.permission.POST_NOTIFICATIONS))
                config.launchConfig.apply { isSplashVisible = false; isLogsVisible = false; isRunOnBoot = false; requiresSharedStorage = false }
                if (bun) config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(RuntimeFlowToolsPrograms.grants).setTimeoutMs(180000L))
                else config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(RuntimeFlowToolsPrograms.capabilities).setTimeoutMs(180000L))
                if (bun) {
                    val sdk = File(context.getExternalFilesDir(null), "runtime-flow-tools-sdk.mjs")
                    val installed = context.createPackageContext("io.github.supermonster003.autojs6.plugin.bun.runtime", 0).assets.open("autojs6/host-client.mjs").use { it.readBytes() }
                    assertArrayEquals(installed, sdk.readBytes()); sdk.copyTo(File(root, "host-client.mjs"))
                }
                val label = "Flow tools package anchor $mode"
                val program = "await flow.waitForPackage(${JSONObject.quote(packageName(mode))},{timeout:15000,interval:100,recoverService:true});" + RuntimeFlowToolsPrograms.exercise()
                val child = "const {parentPort}=require('node:worker_threads'),flow=require('flow'),ui=require('ui'),notifications=require('notifications');(async()=>{$program})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e;});"
                val imports = if (bun) "const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),flow=host.flow,ui=host.ui,notifications=host.notifications;"
                    else "const flow=require('autojs6:flow'),ui=require('ui'),notifications=require('notifications'),{Worker}=require('node:worker_threads');"
                val run = "const main=await(async()=>{$program})();" + if (bun) "const result={main};" else
                    "const w=new Worker(${JSONObject.quote(child)},{eval:true});let child;try{child=await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});}finally{await w.terminate();}const result={main,worker:child};"
                File(root, entry).writeText("""
                    "${if (bun) "bun" else "nodejs"}";(async()=>{
                        $imports
                        const pause=ms=>new Promise(r=>setTimeout(r,ms)),field=await ui.createView('Input',{text:'Flow tools ready $mode'}),window=await ui.showLayout(field);
                        try {
                            const end=Date.now()+90000;while(!String(await field.text).startsWith('RUN ')){if(Date.now()>end)throw Error('Checkpoint timeout');await pause(100);}
                            const parts=String(await field.text).split(' '),round=Number(parts[1]),url=parts[2],identity=await ui.context();
                            if(identity.packageName!==${JSONObject.quote(packageName(mode))}||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                            await field.set({text:${JSONObject.quote(label)}});
                            $run
                            await window.close();await field.dispose();await fetch(url+'/upload',{method:'POST',body:JSON.stringify({identity,result,mode:'$mode',round})});
                        }catch(error){await field.set({text:'Flow tools failure '+String(error.stack||error)});await pause(60000);throw error;}
                    })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
                """.trimIndent())
                File(root, "project.json").writeText(config.toJson(false))
                val built = RemoteApkBuildClient.buildBlocking(context, config, output, AtomicBoolean(false), RemoteApkBuildClient.ProgressListener {}, ApkBuilderTemplatePluginIds.PACKAGE_NAME)
                assertTrue("APK Builder $mode: ${built.errors}", built.isSuccess)
                File(output.parentFile, output.name + ".json").writeText(JSONObject().put("packageName", packageName(mode)).put("runtimeVersionCode", BuildConfig.VERSION_CODE).put("sha256", RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
            } finally { check(root.parentFile == context.cacheDir.canonicalFile); root.deleteRecursively() }
        }
    }
    @Test fun installedApplicationsKeepOwnContextAndServiceAcrossRelaunch() = harness.withFixture(setup = { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }) {
        RuntimeWebFixtureServer().use { server ->
            fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(command)).use { it.readBytes() }
            val saved = Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services")
            val enabled = Settings.Secure.getString(context.contentResolver, "accessibility_enabled")
            fun services(value: String?) {
                require(value == null || value.matches(Regex("[A-Za-z0-9_./:$]*")))
                shell(if (value.isNullOrBlank() || value == "null") "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $value")
            }
            val proofs = JSONArray()
            val injected = HashSet<String>()
            try {
                for (mode in listOf("bun", "worker")) for (round in 1..2) {
                    val pkg = packageName(mode)
                    services(saved); shell("am force-stop $pkg")
                    if (android.os.Build.VERSION.SDK_INT >= 33) shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")
                    context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    fun receipt() = server.uploads.map { JSONObject(it.toString(Charsets.UTF_8)) }.firstOrNull { it.optString("mode") == mode && it.optInt("round") == round }
                    fun wait(label: String, fn: () -> Boolean) = controls.await(label, 120000) {
                        controls.find { it.text?.toString()?.startsWith("Flow tools failure ") == true }?.let { fail(it.text.toString()) }
                        controls.find { it.text?.toString()?.startsWith("FLOW_TOAST ") == true }?.text?.toString()?.removePrefix("FLOW_TOAST ")?.let {
                            if (injected.add(it)) RuntimeFlowToolsDeviceTest().injectToast(it)
                        }
                        fn()
                    }
                    wait("Flow tools entry $mode/$round") { controls.labelled("Flow tools ready $mode") != null }
                    services((saved.orEmpty().split(':').filter { it.isNotBlank() && it != "null" } + "$pkg/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher").distinct().joinToString(":"))
                    assertTrue(controls.labelled("Flow tools ready $mode")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "RUN $round " + server.url)
                    }) == true)
                    wait("Flow tools result") { receipt() != null }
                    val row = receipt()!!
                    assertEquals(pkg, row.getJSONObject("identity").getString("packageName"))
                    assertEquals(BuildConfig.VERSION_CODE, row.getJSONObject("identity").getInt("runtimeVersionCode"))
                    for (key in if (mode == "bun") listOf("main") else listOf("main", "worker")) {
                        val result = row.getJSONObject("result").getJSONObject(key)
                        assertTrue(result.getInt("checks") >= 40)
                        assertEquals(pkg, result.getString("packageName"))
                        assertEquals(pkg, result.getJSONObject("notification").getString("packageName"))
                        assertTrue(result.getJSONObject("toast").getString("packageName") != pkg)
                    }
                    proofs.put(row); File(context.getExternalFilesDir(null), "runtime-flow-tools-packaged.json").writeText(proofs.toString(2))
                    services(saved); shell("am force-stop $pkg")
                }
            } finally {
                services(saved)
                shell(if (enabled == null) "settings delete secure accessibility_enabled" else "settings put secure accessibility_enabled $enabled")
            }
        }
    }
}
