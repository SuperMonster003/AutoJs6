package org.autojs.autojs.engine

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RuntimeFlowDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val harness = CaptureWorkerBridgeDeviceTest()

    private fun fixture(enabled: Boolean = true, grant: Boolean = true, action: () -> Unit) {
        val prefs = context.getSharedPreferences("bun_host_capability_grants", Context.MODE_PRIVATE)
        val key = BunHostCapabilityGrants.ACCESSIBILITY_CONTROL
        val before = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        val service = context.packageName + "/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        val configured = Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services")
        val active = Settings.Secure.getString(context.contentResolver, "accessibility_enabled")
        fun services(value: String?) {
            require(value == null || value.matches(Regex("[A-Za-z0-9_./:$]*")))
            val command = if (value.isNullOrEmpty() || value == "null") "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $value"
            ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(command)).use { it.readBytes() }
        }
        try {
            prefs.edit().putBoolean(key, grant).commit()
            val others = configured.orEmpty().split(':').filter { it.isNotBlank() && it != "null" && it != service }
            services(others.joinToString(":")); SystemClock.sleep(500)
            if (enabled) {
                services((others + service).joinToString(":"))
                assertTrue("Flow needs a bound host accessibility service", AccessibilityService.waitForOperational(30000))
            } else harness.await("Service disconnected") { !AccessibilityService.registry.isOperational }
            harness.withFixture(setup = {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.setContentView(TextView(it).apply { text = "Flow host anchor"; textSize = 24f })
            }) { action() }
        } finally {
            services(configured)
            if (active == null) ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("settings delete secure accessibility_enabled")).use { it.readBytes() }
            else ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("settings put secure accessibility_enabled $active")).use { it.readBytes() }
            prefs.edit().apply { if (before == null) remove(key) else putBoolean(key, before) }.commit()
        }
    }
    private fun script(mode: String, body: String): String {
        if (mode == "bun") return "const flow=host.flow;" + body
        if (mode == "node") return "const flow=require('autojs6:flow');" + body
        val child = "const {parentPort}=require('node:worker_threads'),flow=require('flow');(async()=>{$body})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e;});"
        return "const {Worker}=require('node:worker_threads'),w=new Worker(${JSONObject.quote(child)},{eval:true});try{return await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});}finally{await w.terminate();}"
    }
    private fun exercise(mode: String) = fixture {
        val program = "const core=await(async()=>{${RuntimeFlowPrograms.core()}})();const query=await(async()=>{${RuntimeFlowPrograms.query("Flow host anchor", context.packageName)}})();" +
            "if(await flow.waitForActivity('org.autojs.autojs.engine.RuntimeCaptureFixtureActivity',{timeout:10000,interval:50})!==true)throw Error('Activity');return {core,query,activity:true};"
        val result = harness.execute(if (mode == "bun") "bun" else "node", script(mode, program), capabilities = listOf("accessibility"))
        assertTrue(result.getJSONObject("core").getInt("checks") >= 20)
        assertTrue(result.getJSONObject("query").getBoolean("stable"))
        File(context.getExternalFilesDir(null), "runtime-flow-$mode.json").writeText(result.toString(2))
    }
    @Test fun nodeChainsAndQueriesUseRealHost() = exercise("node")
    @Test fun bunChainsAndQueriesUseRealHost() = exercise("bun")
    @Test fun workerChainsAndQueriesUseRealHost() = exercise("worker")
    @Test fun unavailableServiceIsNotAHiddenTarget() = fixture(enabled = false) {
        for (mode in listOf("node", "bun")) {
            val result = harness.execute(mode, script(mode, """
                for(const recoverService of [false,true]){
                    let error;try{await flow.waitForHidden('absent',{recoverService,timeout:100,interval:10});}catch(e){error=e;}
                    if(error?.code!=='A11Y_UNAVAILABLE')throw Error('Unavailable is not hidden');
                }
                return {unavailable:true,local:await flow.of(4).map(x=>x+1)};
            """.trimIndent()), capabilities = listOf("accessibility"))
            assertEquals(5, result.getInt("local"))
        }
    }
    @Test fun bunLocalChainsNeedNoGrantButRawAndFacadeQueriesDo() = fixture(grant = false) {
        val result = harness.execute("bun", """
            const flow=host.flow;let error;try{await flow.wait('Flow host anchor');}catch(e){error=e;}
            if(error?.code!=='NOT_GRANTED')throw Error('Flow grant');
            const response=await fetch('http://autojs6/v1/accessibility.control',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,
                method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'start',method:'flowProbe',args:[{text:'Flow host anchor'}]})});
            return {local:await flow.of(1).map(x=>x+1),status:response.status};
        """.trimIndent())
        assertEquals(2, result.getInt("local")); assertEquals(403, result.getInt("status"))
    }
    @Test fun cjsEsmAndNestedWorkersReleaseTimersAndPreserveParentDefaults() = fixture {
        val result = harness.execute("node", """
            const flow=require('flow'),{Worker}=require('node:worker_threads');
            const body="flow.defaults({interval:20,timeout:Infinity});flow.wait('Flow absent');parentPort.postMessage(await flow.of(2).map(x=>x*3));";
            const cjs="const {parentPort}=require('node:worker_threads'),flow=require('flow');(async()=>{"+body+"})().catch(e=>{throw e;});";
            const esm="import {parentPort} from 'node:worker_threads';import flow from 'autojs6:flow';"+body;
            const nested="const {parentPort,Worker}=require('node:worker_threads'),w=new Worker("+JSON.stringify(cjs)+",{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});";
            for(const [entry,options] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
                const w=new Worker(entry,options);try{eq(await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);}),6,'Worker chain');}finally{await w.terminate();}
                eq(flow.defaults().interval,200,'Parent defaults');eq(await flow.of(7),7,'Parent alive');
            }return {modes:3,parent:true,pending:flow.pending};
        """.trimIndent(), capabilities = listOf("accessibility"))
        assertEquals(3, result.getInt("modes"))
        File(context.getExternalFilesDir(null), "runtime-flow-worker-modes.json").writeText(result.toString(2))
    }
}
