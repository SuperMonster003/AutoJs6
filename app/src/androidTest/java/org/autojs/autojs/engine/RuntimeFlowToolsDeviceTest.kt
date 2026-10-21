package org.autojs.autojs.engine

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RuntimeFlowToolsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val harness = CaptureWorkerBridgeDeviceTest()
    private val controls = RuntimeWebMediaControls(harness)
    private fun shell(value: String) = ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(value)).use { it.readBytes() }
    private fun services(value: String?) {
        require(value == null || value.matches(Regex("[A-Za-z0-9_./:$]*")))
        shell(if (value.isNullOrBlank() || value == "null") "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $value")
    }
    internal fun fixture(overrides: Map<String, Boolean> = emptyMap(), action: () -> Unit) {
        val prefs = context.getSharedPreferences("bun_host_capability_grants", Context.MODE_PRIVATE)
        val keys = RuntimeFlowToolsPrograms.grants
        val old = keys.associateWith { if (prefs.contains(it)) prefs.getBoolean(it, false) else null }
        val saved = Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services")
        val enabled = Settings.Secure.getString(context.contentResolver, "accessibility_enabled")
        val component = context.packageName + "/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        val manager = context.getSystemService(NotificationManager::class.java)
        val originalChannels = manager.notificationChannels.map { it.id }.toSet()
        try {
            prefs.edit().apply { keys.forEach { putBoolean(it, overrides[it] ?: true) } }.commit()
            harness.withFixture {
                val others = saved.orEmpty().split(':').filter { it.isNotBlank() && it != "null" && it != component }
                for (attempt in 1..2) {
                    if (AccessibilityService.registry.isOperational) break
                    services(others.joinToString(":")); SystemClock.sleep(750); services((others + component).joinToString(":"))
                    if (AccessibilityService.waitForOperational(30000)) break
                }
                assertTrue("Host accessibility service is operational with the fixture in front", AccessibilityService.registry.isOperational)
                action()
            }
        } finally {
            manager.notificationChannels.filter { it.id !in originalChannels && it.id.startsWith("flow-tools-events_flow_") }.forEach { manager.deleteNotificationChannel(it.id) }
            services(saved)
            shell(if (enabled == null) "settings delete secure accessibility_enabled" else "settings put secure accessibility_enabled $enabled")
            prefs.edit().apply { old.forEach { (k,v) -> if (v == null) remove(k) else putBoolean(k,v) } }.commit()
        }
    }
    internal fun injectToast(marker: String) {
        require(marker.startsWith("EVENTS_FLOW_"))
        context.startActivity(Intent().setClassName(instrumentation.context.packageName, RuntimeEventsToastFixtureActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("ownedText", marker))
    }
    private fun program(mode: String, body: String): String {
        if (mode == "bun") return "const flow=host.flow,ui=host.ui,notifications=host.notifications;" + body
        val imports = "const flow=require('flow'),ui=require('ui'),notifications=require('notifications');"
        if (mode == "node") return imports + body
        val child = "const {parentPort}=require('node:worker_threads');$imports(async()=>{$body})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e;});"
        return "const {Worker}=require('node:worker_threads'),w=new Worker(${JSONObject.quote(child)},{eval:true});try{return await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});}finally{await w.terminate();}"
    }
    private fun exercise(mode: String) = fixture {
        val before = AccessibilityService.eventBus.subscriptionCount
        val pool = Executors.newSingleThreadExecutor()
        try {
            val future = pool.submit<JSONObject> { harness.execute(if (mode == "bun") "bun" else "node", program(mode, RuntimeFlowToolsPrograms.exercise(launch = true)), capabilities = RuntimeFlowToolsPrograms.capabilities) }
            val injected = HashSet<String>()
            controls.await("Flow tools real events $mode", 115000) {
                controls.find { it.text?.toString()?.startsWith("FLOW_TOAST ") == true }?.text?.toString()?.removePrefix("FLOW_TOAST ")?.let {
                    if (injected.add(it)) injectToast(it)
                }
                future.isDone
            }
            val result = future.get(1, TimeUnit.SECONDS)
            assertTrue(result.getInt("checks") >= 40)
            assertEquals(context.packageName, result.getString("packageName"))
            File(context.getExternalFilesDir(null), "runtime-flow-tools-$mode.json").writeText(result.toString(2))
            context.getSystemService(NotificationManager::class.java).deleteNotificationChannel(result.getString("channel"))
            harness.await("Flow event subscriptions released") { AccessibilityService.eventBus.subscriptionCount == before }
        } finally { pool.shutdownNow() }
    }
    @Test fun nodeAdvancedToolsAndEvents() = exercise("node")
    @Test fun bunAdvancedToolsAndEvents() = exercise("bun")
    @Test fun workerAdvancedToolsAndEvents() = exercise("worker")
    @Test fun workerModesReleaseNativeListenersAndPreserveParent() = fixture {
        val before = AccessibilityService.eventBus.subscriptionCount
        val result = harness.execute("node", """
            const flow=require('flow'),{Worker}=require('node:worker_threads');
            const parent=flow.waitForEvent('view_clicked',null,Infinity);await parent.ready;
            const body="const task=flow.waitForEvent('view_clicked',null,Infinity);await task.ready;const found=await flow.findAny(['Flow missing worker target'],{timeout:0});parentPort.postMessage({pending:task.state==='pending',found});";
            const cjs="const {parentPort}=require('node:worker_threads'),flow=require('flow');(async()=>{"+body+"})().catch(e=>{throw e;});";
            const esm="import {parentPort} from 'node:worker_threads';import flow from 'autojs6:flow';"+body;
            const nested="const {parentPort,Worker}=require('node:worker_threads'),w=new Worker("+JSON.stringify(cjs)+",{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});";
            try{
                for(const [entry,options] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
                    const w=new Worker(entry,options);
                    try{eq(await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);}),{pending:true,found:null},'Native Worker Flow');}
                    finally{await w.terminate();}
                    eq(parent.state,'pending','Parent listener preserved');
                }
                parent.cancel();await parent.catch(error=>eq(error.code,'CANCELLED','Parent cancellation'));
                return {modes:3,parentPreserved:true};
            }finally{await flow.dispose();}
        """.trimIndent(), capabilities = listOf("accessibility", "events"))
        harness.await("Worker Flow listeners released") { AccessibilityService.eventBus.subscriptionCount == before }
        File(context.getExternalFilesDir(null), "runtime-flow-tools-worker-modes.json").writeText(result.toString(2))
    }
    @Test fun bunFlowGrantIsSeparateFromAccessibility() = fixture(mapOf("flow.control" to false)) {
        val result = harness.execute("bun", """
            const flow=host.flow;let error;try{await flow.findAny(['missing']);}catch(e){error=e;}
            if(error?.code!=='NOT_GRANTED')throw Error('Separate flow.control grant');
            return {local:await flow.of(3),denied:error.code};
        """.trimIndent())
        assertEquals("NOT_GRANTED", result.getString("denied"))
    }
    @Test fun nodeAndBunRejectExtraCapabilitiesBeforeSideEffects() = fixture(mapOf("accessibility.gesture" to false, "events.toast" to false)) {
        for (mode in listOf("node", "bun")) {
            val result = harness.execute(mode, program(mode, """
                let gesture,event;try{await flow.smartClick('missing');}catch(e){gesture=e;}
                try{await flow.waitForToast('missing',1000);}catch(e){event=e;}
                if(!gesture?.code||!event?.code||gesture.code==='TIMEOUT'||event.code==='TIMEOUT')throw Error('Capabilities must reject before work');
                const absent=await flow.clickIfExists('missing',{timeout:0,gestureFallback:false});
                await flow.dispose();return {gesture:gesture.code,event:event.code,absent};
            """.trimIndent()), capabilities = listOf("accessibility"))
            assertFalse(result.getBoolean("absent"))
        }
    }
}
