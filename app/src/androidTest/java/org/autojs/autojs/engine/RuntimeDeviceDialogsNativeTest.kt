package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class RuntimeDeviceDialogsNativeTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun provider(caps:List<String> = listOf("device","device.power","media","media.audio","dialogs"))=AndroidNodeBridgeCapabilityProvider(context,
        NodeBridgeEngineInfo.fromContext(context,id="device-native-${UUID.randomUUID()}",cwd=context.cacheDir.path),permissionManifest=NodeBridgePermissionManifest.fromDeclaredCapabilities(caps))
    private fun call(p:NodeBridgeCapabilityProvider,module:String,method:String,args:JSONArray=JSONArray()):NodeBridgeResponse {
        val future=CompletableFuture<NodeBridgeResponse>();val text=args.toString()
        p.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(),module,method,text,5000,permissions=NodeBridgePermissionManifest.requiredCapabilitiesFor(module,method,text))){future.complete(it)}
        return future.get(6,TimeUnit.SECONDS)
    }
    @Test fun wakeLocksAreIsolatedAndClosedScopesRejectLateCreation() {
        val p=provider();try{
            for(scope in listOf("parent","worker"))assertTrue(call(p,"device","keepScreenOn",JSONArray().put(30000).put(scope)).ok)
            assertEquals("2",p.nativeDiagnostics()["embedded_script.device.active_wake_lock_count"])
            assertTrue(call(p,"device","closeScope",JSONArray().put("worker")).ok)
            assertEquals("1",p.nativeDiagnostics()["embedded_script.device.active_wake_lock_count"])
            assertFalse(call(p,"device","keepScreenOn",JSONArray().put(30000).put("worker")).ok)
            p.destroy();assertEquals("0",p.nativeDiagnostics()["embedded_script.device.active_wake_lock_count"])
        }finally{p.destroy()}
    }
    @Test fun dialogCancellationBeforeActivityCreationAndScopeIsolation() {
        val p=provider();try{
            assertTrue(call(p,"dialogs","cancelRequest",JSONArray().put("early").put("worker")).ok)
            assertFalse(call(p,"dialogs","progressShow",JSONArray().put(JSONObject().put("__requestId","early").put("__scope","worker"))).ok)
            val shown=call(p,"dialogs","progressShow",JSONArray().put(JSONObject().put("title","Owned native").put("__scope","parent")))
            assertTrue(shown.toString(),shown.ok);val id=JSONObject(shown.resultJson!!).getString("id")
            assertFalse(call(p,"dialogs","progressDismiss",JSONArray().put(id).put("worker")).ok)
            assertTrue(call(p,"dialogs","closeScope",JSONArray().put("worker")).ok)
            assertTrue(call(p,"dialogs","progressUpdate",JSONArray().put(id).put(JSONObject().put("value",5)).put("parent")).ok)
            assertTrue(call(p,"dialogs","progressDismiss",JSONArray().put(id).put("parent")).ok)
        }finally{p.destroy();instrumentation.waitForIdleSync()}
    }
    @Test fun rawBunCallsCannotBypassIndependentPowerAndAudioGrants() {
        val p=provider(BunAutomationBridge.nodeCapabilities(listOf(Grants.DEVICE_CONTROL)))
        try{
            assertTrue(call(p,"device","info").ok)
            for(method in listOf("keepScreenOn","setBrightness","setBrightnessMode"))assertFalse(call(p,"device",method,JSONArray().put(1)).ok)
            assertFalse(call(p,"media","getAudioStreamVolume",JSONArray().put("music")).ok)
            assertFalse(call(p,"dialogs","alert",JSONArray().put("Denied").put("Denied")).ok)
        }finally{p.destroy()}
    }
    @Test fun abandonedBunProgressAndWakeLockJobsReleaseNativeResources() {
        val p=provider();val bridge=BunAutomationBridge(context,listOf(Grants.DEVICE_CONTROL,Grants.DEVICE_POWER,Grants.DIALOGS_CONTROL)){p}
        try{
            for((cap,method,args) in listOf(Triple(Grants.DEVICE_CONTROL,"keepScreenOn",JSONArray().put(30000).put("abandoned")),
                Triple(Grants.DIALOGS_CONTROL,"progressShow",JSONArray().put(JSONObject().put("title","Owned abandoned").put("__requestId","abandoned-dialog").put("__scope","abandoned"))))){
                val start=bridge.invoke(cap,JSONObject().put("method",method).put("args",args));assertTrue(start.ok)
                Thread.sleep(500)
                val id=JSONObject(start.resultJson!!).getString("id");assertTrue(bridge.invoke(cap,JSONObject().put("action","cancel").put("id",id)).ok)
            }
            val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while(System.nanoTime()<end&&(p.nativeDiagnostics()["embedded_script.device.active_wake_lock_count"]!="0"||NodeDialogActivity.sessions.isNotEmpty()))Thread.sleep(20)
            assertEquals("0",p.nativeDiagnostics()["embedded_script.device.active_wake_lock_count"]);assertTrue(NodeDialogActivity.sessions.isEmpty())
        }finally{bridge.close();p.destroy()}
    }
    @Test fun cancellingRawBunDialogDoesNotDismissAnotherTokenlessDialog() {
        val p=provider();val bridge=BunAutomationBridge(context,listOf(Grants.DIALOGS_CONTROL)){p}
        try {
            val ids=(1..2).map { index ->
                val reply=bridge.invoke(Grants.DIALOGS_CONTROL,JSONObject().put("method","progressShow")
                    .put("args",JSONArray().put(JSONObject().put("title","Owned raw $index"))))
                assertTrue(reply.ok);JSONObject(reply.resultJson!!).getString("id")
            }
            fun expect(count:Int) {
                val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while(NodeDialogActivity.sessions.size!=count&&System.nanoTime()<end)Thread.sleep(20)
                assertEquals(count,NodeDialogActivity.sessions.size)
            }
            expect(2)
            for((index,id) in ids.withIndex()) {
                assertTrue(bridge.invoke(Grants.DIALOGS_CONTROL,JSONObject().put("action","cancel").put("id",id)).ok)
                expect(1-index)
            }
        }finally{bridge.close();p.destroy();instrumentation.waitForIdleSync()}
    }
}
