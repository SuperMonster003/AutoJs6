package org.autojs.autojs.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RuntimeJavaInteropNativeTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun call(provider:AndroidNodeBridgeCapabilityProvider,method:String,payload:JSONObject=JSONObject()):NodeBridgeResponse {
        val result=CompletableFuture<NodeBridgeResponse>()
        provider.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(),"java",method,JSONArray().put(payload).toString(),5000,
            permissions=listOf("java_interop"))){result.complete(it)}
        return result.get(6,TimeUnit.SECONDS)
    }
    private fun ok(response:NodeBridgeResponse):String {assertTrue(response.toString(),response.ok);return response.resultJson!!}
    @Test fun explicitDisableExecutionOwnershipAndDestruction() {
        val disabled=AndroidNodeBridgeCapabilityProvider(context,javaInteropEnabled=false)
        try {assertEquals(NodeRuntimeErrorCodes.JAVA_INTEROP_DISABLED,call(disabled,"getContext").error?.code)} finally {disabled.destroy()}
        val first=AndroidNodeBridgeCapabilityProvider(context);val other=AndroidNodeBridgeCapabilityProvider(context)
        try {
            val value=JSONObject(ok(call(first,"getContext")));val handle=JSONObject().put("handle",value.getString("__autojs6JavaHandle")).put("method","getPackageName")
            assertEquals(JSONObject.quote(context.packageName),ok(call(first,"call",handle)))
            assertFalse(call(other,"call",handle).ok)
            first.closeJavaObjects();assertEquals("0",first.nativeDiagnostics()["embedded_script.java_interop_handle_count"])
            assertFalse(call(first,"getContext").ok);assertFalse(call(first,"call",handle).ok)
        } finally {first.destroy();other.destroy()}
    }
    @Test fun bunCancelledMainThreadCreationReleasesLateHandle() {
        val provider=AndroidNodeBridgeCapabilityProvider(context)
        val automation=BunAutomationBridge(context,listOf(Grants.JAVA_INTEROP)){provider}
        val entered=CountDownLatch(1);val resume=CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {entered.countDown();resume.await(10,TimeUnit.SECONDS)}
        assertTrue(entered.await(2,TimeUnit.SECONDS))
        try {
            val start=JSONObject(ok(automation.invoke(Grants.JAVA_INTEROP,JSONObject().put("action","start").put("method","getContext")
                .put("args",JSONArray().put(JSONObject().put("thread","main"))))))
            assertEquals("true",ok(automation.invoke(Grants.JAVA_INTEROP,JSONObject().put("action","cancel").put("id",start.getString("id")))))
            resume.countDown()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val end=System.currentTimeMillis()+3000
            while(System.currentTimeMillis()<end && provider.nativeDiagnostics()["embedded_script.java_interop_handle_count"]!="0")Thread.sleep(20)
            assertEquals("0",provider.nativeDiagnostics()["embedded_script.java_interop_handle_count"])
        } finally {resume.countDown();automation.close();provider.destroy()}
    }
    @Test fun publicClassMetadataAndBunDefaultGrant() {
        val policy=ControlledJavaInteropPolicy.policyJson();assertEquals("public",policy.getString("access"));assertEquals(0,policy.getJSONArray("allowedClasses").length())
        assertTrue(ControlledJavaInteropPolicy.describeClass("android.content.Intent").getBoolean("constructable"))
        assertTrue(ControlledJavaInteropPolicy.describeClass("java.lang.StringBuilder").getJSONArray("methods").length()>0)
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val old=if(prefs.contains(Grants.JAVA_INTEROP))prefs.getBoolean(Grants.JAVA_INTEROP,false)else null
        try {prefs.edit().remove(Grants.JAVA_INTEROP).commit();assertFalse(Grants.isJavaInteropGranted(context))}
        finally {prefs.edit().apply{if(old==null)remove(Grants.JAVA_INTEROP)else putBoolean(Grants.JAVA_INTEROP,old)}.commit()}
        val provider=AndroidNodeBridgeCapabilityProvider(context);val denied=BunAutomationBridge(context,emptyList()){provider}
        try {try {denied.invoke(Grants.JAVA_INTEROP,JSONObject().put("action","start").put("method","getContext"));fail("Missing grant")}
            catch (_:SecurityException) {}} finally {denied.close();provider.destroy()}
    }
}
