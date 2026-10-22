package org.autojs.autojs.engine

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.apkbuilder.template.RemoteApkBuildClient
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentLinkController
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeAgentPackagedDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val harness = CaptureWorkerBridgeDeviceTest()
    private val controls = RuntimeWebMediaControls(harness)
    private fun packageName(mode: String) = "org.autojs.autojs6.interop.aiagent.$mode"

    @Test fun produce() = harness.withFixture {
        val target = RuntimeAgentDeviceTest().localTarget()
        for (mode in listOf("bun", "worker")) {
            val root = File(context.cacheDir, "ai-agent-package-${UUID.randomUUID()}").canonicalFile.apply { check(mkdir()) }
            val output = File(context.getExternalFilesDir(null), "runtime-ai-agent-$mode.apk")
            try {
                val bun = mode == "bun"; val entry = if (bun) "main.bun.js" else "main.cjs"
                val config = ProjectConfig().setName("Agent $mode Test").setPackageName(packageName(mode)).setVersionName("1.0.0").setVersionCode(1)
                    .setMainScriptFileName(entry).setProjectType(if (bun) "bun" else "node").setSourcePath(root.path).setAbis(listOf("arm64-v8a"))
                    .setLibs(emptyList()).setPermissions(listOf(Manifest.permission.INTERNET))
                config.launchConfig.apply { isSplashVisible = false; isLogsVisible = true; isRunOnBoot = false; requiresSharedStorage = false }
                if (bun) config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(RuntimeAgentPrograms.grants).setTimeoutMs(600000L))
                else config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(RuntimeAgentPrograms.capabilities).setTimeoutMs(600000L))
                if (bun) {
                    val sdk = File(context.getExternalFilesDir(null), "runtime-ai-agent-sdk.mjs")
                    val installed = context.createPackageContext("io.github.supermonster003.autojs6.plugin.bun.runtime", 0).assets.open("autojs6/host-client.mjs").use { it.readBytes() }
                    assertArrayEquals(installed, sdk.readBytes()); sdk.copyTo(File(root, "host-client.mjs"))
                }
                val program = RuntimeAgentPrograms.exercise(target, full = false)
                val child = "const {parentPort}=require('node:worker_threads'),ai=require('ai');(async()=>{$program})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e;});"
                val imports = if (bun) "const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ai=host.ai,ui=host.ui;"
                    else "const ai=require('autojs6:ai'),ui=require('ui'),{Worker}=require('node:worker_threads');"
                val run = "const main=await(async()=>{$program})();" + if (bun) "const result={main};" else
                    "const w=new Worker(${JSONObject.quote(child)},{eval:true});let worker;try{worker=await new Promise((r,j)=>{w.once('message',r);w.once('error',j);});}finally{await w.terminate();}const result={main,worker};"
                File(root, entry).writeText("""
                    "${if (bun) "bun" else "nodejs"}";(async()=>{
                        $imports
                        const agent=ai.agent,pause=ms=>new Promise(r=>setTimeout(r,ms));
                        const field=await ui.createView('Input',{text:'Agent ready $mode'}),window=await ui.showLayout(field);
                        try {
                            const deadline=Date.now()+90000;while(!String(await field.text).startsWith('RUN ')){if(Date.now()>deadline)throw Error('Checkpoint timeout');await pause(100);}
                            const parts=String(await field.text).split(' '),round=Number(parts[1]),url=parts[2],previous=parts[3],foreign=parts[4],identity=await ui.context();
                            if(identity.packageName!==${JSONObject.quote(packageName(mode))}||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                            const before=await agent.hasAccess();
                            if(round===0||round===3){
                                if(before)throw Error('Application access should be absent');
                                if(round===0&&await agent.requestAccess())throw Error('Expected access denial');
                                const error=await agent.status().then(()=>null,e=>e);
                                if(error?.code!=='PERMISSION_DENIED')throw Error('Unauthorized Agent query: '+error?.code);
                                await agent.close();await fetch(url+'/upload',{method:'POST',body:JSON.stringify({identity,mode:'$mode',round,before,denied:error.code})});
                            }else if(round===4){
                                if(!before)throw Error('Expected retained consent');
                                const run=await agent.run('Propose a one-step plan and wait for my review before saying OK.',{
                                    target:${JSONObject.quote(target)},tools:[],memory:false,plan:true,locale:'en',interaction:'script',detached:true,
                                    budget:{maxSteps:4,maxModelCalls:6,maxDurationMs:180000,maxTotalTokens:8192}});
                                await field.set({text:'Agent revoke ready $mode '+run.id});
                                const end=Date.now()+120000;while(await agent.hasAccess()){if(Date.now()>end)throw Error('Revocation timeout');await pause(100);}
                                const terminal=await run.result.then(result=>({status:result.status}),error=>({error:error.code}));
                                const after=await agent.hasAccess();if(after)throw Error('Access was not revoked');
                                await agent.close();await fetch(url+'/upload',{method:'POST',body:JSON.stringify({identity,mode:'$mode',round,before,after,id:run.id,terminal})});
                            }else{
                                if(!before&&!await agent.requestAccess())throw Error('Agent application access denied');
                                let recovered=false,isolated=false;
                                if(previous&&previous!=='-'){const earlier=await agent.get(previous);if(!earlier||(await earlier.result).status!=='completed')throw Error('Own history after relaunch');recovered=true;}
                                if(foreign&&foreign!=='-'){if(await agent.get(foreign)!==null)throw Error('Another application history leaked');isolated=true;}
                                $run
                                await fetch(url+'/upload',{method:'POST',body:JSON.stringify({identity,result,mode:'$mode',round,before,recovered,isolated})});
                            }
                            await window.close();await field.dispose();
                        }catch(error){await field.set({text:'Agent failure '+String(error.stack||error)});await pause(60000);throw error;}
                    })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
                """.trimIndent())
                File(root, "project.json").writeText(config.toJson(false))
                val built = RemoteApkBuildClient.buildBlocking(context, config, output, AtomicBoolean(false), RemoteApkBuildClient.ProgressListener {}, ApkBuilderTemplatePluginIds.PACKAGE_NAME)
                assertTrue("APK Builder $mode: ${built.errors}", built.isSuccess)
                val manifest = requireNotNull(context.packageManager.getPackageArchiveInfo(output.path, android.content.pm.PackageManager.GET_PERMISSIONS))
                assertTrue(manifest.requestedPermissions.orEmpty().contains(RuntimeAgentRemote.PERMISSION))
                assertFalse(manifest.requestedPermissions.orEmpty().contains(Manifest.permission.RECORD_AUDIO))
                File(output.parentFile, output.name + ".json").writeText(JSONObject().put("packageName", packageName(mode))
                    .put("runtimeVersionCode", BuildConfig.VERSION_CODE).put("target", target).put("sha256", RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
            } finally { check(root.parentFile == context.cacheDir.canonicalFile); root.deleteRecursively() }
        }
    }

    @Test fun installedApplicationsEnforceConsentIsolationRevocationAndRelaunch() = harness.withFixture(setup = { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }) {
        RuntimeWebFixtureServer().use { server ->
            fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(command)).use { it.readBytes() }
            fun revoke(packageName: String) {
                context.startActivity(Intent(context, RuntimeAgentAccessManagementActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                controls.await("Agent access manager", 20000) {
                    controls.find { it.packageName?.toString() == context.packageName && it.text?.toString() == context.getString(R.string.agent_runtime_access_title) } != null
                }
                var row = controls.find { it.text?.toString()?.endsWith("\n$packageName") == true }
                if (row != null) {
                    while (row != null && !row!!.isClickable) row = row!!.parent
                    assertTrue(row?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                    controls.await("Agent revoke dialog", 10000) { controls.find { it.packageName?.toString() == context.packageName && it.viewIdResourceName == "android:id/button1" } != null }
                    assertTrue(controls.find { it.packageName?.toString() == context.packageName && it.viewIdResourceName == "android:id/button1" }
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                    controls.await("Agent grant removed", 10000) { controls.find { it.text?.toString()?.endsWith("\n$packageName") == true } == null }
                }
                harness.automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
            val proofs = JSONArray(); val previous = linkedMapOf<String, String>()
            for (mode in listOf("bun", "worker")) {
                val pkg = packageName(mode)
                try {
                    for (round in listOf(0, 1, 2, 4, 3, 5)) {
                        shell("am force-stop $pkg")
                        if (round == 0 || round == 3) revoke(pkg)
                        context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        fun receipt() = server.uploads.map { JSONObject(it.toString(Charsets.UTF_8)) }.firstOrNull { it.optString("mode") == mode && it.optInt("round") == round }
                        var revoked: org.autojs.autojs.core.plugin.agent.ThreeStoveAgentRunRecord? = null
                        fun wait(label: String, condition: () -> Boolean) = controls.await(label, 900000) {
                            controls.find { it.text?.toString()?.startsWith("Agent failure ") == true }?.let { fail(it.text.toString()) }
                            if (round in listOf(0, 1, 5)) {
                                val button = if (round == 0) "android:id/button2" else "android:id/button1"
                                controls.find { it.packageName?.toString() == context.packageName && it.viewIdResourceName == button }
                                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            }
                            if (round == 4 && revoked == null) {
                                val text = controls.find { it.text?.toString()?.startsWith("Agent revoke ready $mode ") == true }?.text?.toString()
                                if (text != null) {
                                    val id = text.substringAfterLast(' ')
                                    revoked = requireNotNull(ThreeStoveAgentLinkController.get(context).scriptRuns.get(id))
                                    assertFalse("Revocation must interrupt an active detached task", revoked!!.settled)
                                    revoke(pkg)
                                }
                            }
                            condition()
                        }
                        wait("Agent entry $mode/$round") { controls.labelled("Agent ready $mode") != null }
                        val foreign = previous.entries.firstOrNull { it.key != pkg }?.value ?: "-"
                        assertTrue(controls.labelled("Agent ready $mode")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "RUN $round ${server.url} ${previous[pkg] ?: "-"} $foreign")
                        }) == true)
                        wait("Agent result $mode/$round") { receipt() != null }
                        val row = receipt()!!
                        assertEquals(pkg, row.getJSONObject("identity").getString("packageName"))
                        assertEquals(BuildConfig.VERSION_CODE, row.getJSONObject("identity").getInt("runtimeVersionCode"))
                        if (round == 0 || round == 3) assertEquals("PERMISSION_DENIED", row.getString("denied"))
                        else if (round == 4) {
                            assertTrue(row.getBoolean("before")); assertFalse(row.getBoolean("after")); assertNotNull(revoked)
                            controls.await("Detached Agent task stopped", 120000) { revoked!!.settled }
                            assertEquals("cancelled", revoked!!.result?.get("status")?.asString)
                            row.put("nativeStatusAfterRevocation", "cancelled")
                        } else {
                            assertEquals(round == 2, row.getBoolean("before"))
                            if (round in listOf(2, 5)) assertTrue(row.getBoolean("recovered"))
                            if (foreign != "-") assertTrue(row.getBoolean("isolated"))
                            for (key in if (mode == "bun") listOf("main") else listOf("main", "worker")) {
                                val result = row.getJSONObject("result").getJSONObject(key)
                                assertTrue(result.toString(), result.getInt("checks") >= 22)
                                assertEquals("completed", result.getString("status"))
                                assertTrue(result.getJSONObject("usage").getInt("modelCalls") > 0)
                            }
                            previous[pkg] = row.getJSONObject("result").getJSONObject("main").getString("id")
                        }
                        proofs.put(row); File(context.getExternalFilesDir(null), "runtime-ai-agent-packaged.json").writeText(proofs.toString(2))
                    }
                } finally { shell("am force-stop $pkg") }
            }
        }
    }
}
