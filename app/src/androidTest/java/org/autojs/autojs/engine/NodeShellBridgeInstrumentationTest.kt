package org.autojs.autojs.engine

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class NodeShellBridgeInstrumentationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun provider(caps:List<String> = listOf("shell","shell.shizuku"))=AndroidNodeBridgeCapabilityProvider(context,
        NodeBridgeEngineInfo.fromContext(context,id="shell-native-${UUID.randomUUID()}",cwd=context.cacheDir.path),
        permissionManifest=NodeBridgePermissionManifest.fromDeclaredCapabilities(caps))
    private fun call(p:NodeBridgeCapabilityProvider,method:String,args:JSONArray=JSONArray()):NodeBridgeResponse {
        val f=CompletableFuture<NodeBridgeResponse>();val text=args.toString()
        p.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(),"shell",method,text,10000,permissions=NodeBridgePermissionManifest.requiredCapabilitiesFor("shell",method,text))){f.complete(it)}
        return f.get(12,TimeUnit.SECONDS)
    }
    @Test fun registryAndDynamicPrivilegeRequirementsMatchShellOptions() {
        assertEquals(setOf("exec","spawn","open","drain","write","closeInput","kill","close","getAccess","requestShizukuPermission"),NodeBridgeModules.shellMethods)
        val options=JSONObject().put("root",true).put("shizuku",true)
        assertEquals(listOf("shell","shell.root","shell.shizuku"),NodeBridgePermissionManifest.requiredCapabilitiesFor("shell","spawn",JSONArray().put("true").put(options).toString()))
        assertEquals(listOf("shell","shell.shizuku"),NodeBridgePermissionManifest.requiredCapabilitiesFor("shell","requestShizukuPermission"))
        assertTrue(NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf("shell.shizuku")).contains("moe.shizuku.manager.permission.API_V23"))
    }
    @Test fun rawExecUsesHostPathsTypedResultsAndBoundedOutput() {
        val p=provider();try {
            val response=call(p,"exec",JSONArray().put("printf out; printf err >&2; exit 7").put(JSONObject().put("cwd","/system")));assertTrue(response.toString(),response.ok)
            val data=JSONObject(response.resultJson);assertEquals(7,data.getInt("code"));assertEquals("out",data.getString("stdout"));assertEquals("err",data.getString("stderr"))
            val limited=call(p,"exec",JSONArray().put("printf 123456789").put(JSONObject().put("maxOutputBytes",3)));assertTrue(limited.toString(),limited.ok);val capped=JSONObject(limited.resultJson);assertTrue(capped.getBoolean("truncated"));assertEquals("123",capped.getString("stdout"))
        }finally{p.destroy()}
    }
    @Test fun timeoutKillsCommandsThatIgnoreTermAndReleasesResources() {
        val p=provider();try {
            val response=call(p,"exec",JSONArray().put("trap '' TERM; while true; do sleep 1; done").put(JSONObject().put("timeoutMs",150)));assertTrue(response.toString(),response.ok)
            val data=JSONObject(response.resultJson);assertEquals(124,data.getInt("code"));assertTrue(data.getBoolean("timedOut"));assertEquals("0",p.nativeDiagnostics()["embedded_script.shell.active_handle_count"])
        }finally{p.destroy()}
    }
    @Test fun rootAndShizukuRequireExplicitProjectCapabilities() {
        val p=provider(listOf("shell"));try {
            for(key in listOf("root","shizuku","adb"))assertFalse(key,call(p,"spawn",JSONArray().put("true").put(JSONObject().put(key,true))).ok)
            assertFalse(call(p,"requestShizukuPermission").ok)
            assertEquals("0",p.nativeDiagnostics()["embedded_script.shell.active_handle_count"])
        }finally{p.destroy()}
    }
    @Test fun cancelledBunStartsAndRawExecCallsReleaseTheirProcesses() {
        for(method in listOf("spawn","exec")) {
            val p=provider();val bridge=BunAutomationBridge(context,listOf(Grants.SHELL_CONTROL)){p}
            try {
                val started=bridge.invoke(Grants.SHELL_CONTROL,JSONObject().put("method",method).put("args",JSONArray().put("sleep 20")))
                assertTrue(started.toString(),started.ok);val id=JSONObject(started.resultJson).getString("id")
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while(p.nativeDiagnostics()["embedded_script.shell.active_handle_count"]!="1"&&System.nanoTime()<deadline)Thread.sleep(10)
                assertEquals("1",p.nativeDiagnostics()["embedded_script.shell.active_handle_count"])
                assertTrue(bridge.invoke(Grants.SHELL_CONTROL,JSONObject().put("action","cancel").put("id",id)).ok)
                val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while(p.nativeDiagnostics()["embedded_script.shell.active_handle_count"]!="0"&&System.nanoTime()<end)Thread.sleep(10)
                assertEquals("0",p.nativeDiagnostics()["embedded_script.shell.active_handle_count"])
            }finally{bridge.close();p.destroy()}
        }
    }
    @Test fun ptyInputEofKeepsOutputReadable() {
        repeat(8){ index ->
            val p=provider();try {
                val opened=call(p,"open",JSONArray().put(JSONObject().put("cmd","cat").put("pty",true).put("timeoutMs",3000)))
                assertTrue(opened.toString(),opened.ok);val id=JSONObject(opened.resultJson).getString("id")
                val write=call(p,"write",JSONArray().put(id).put(android.util.Base64.encodeToString("pty line\n".toByteArray(),android.util.Base64.NO_WRAP)));assertTrue(write.toString(),write.ok)
                val rows=JSONArray();val output=StringBuilder();val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2)
                while(output.isEmpty() && System.nanoTime()<deadline){val response=call(p,"drain",JSONArray().put(id));assertTrue(response.toString(),response.ok);val events=JSONArray(response.resultJson);for(n in 0 until events.length()){val event=events.getJSONObject(n);rows.put(event);if(event.optString("type")=="stdout")output.append(String(android.util.Base64.decode(event.getString("data"),android.util.Base64.DEFAULT)))}}
                assertEquals("PTY iteration $index events=$rows","pty line\n",output.toString())
                assertTrue(call(p,"closeInput",JSONArray().put(id)).ok)
                var exited=false;val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
                while(!exited&&System.nanoTime()<end){val response=call(p,"drain",JSONArray().put(id));assertTrue(response.toString(),response.ok);val events=JSONArray(response.resultJson);for(n in 0 until events.length()){val event=events.getJSONObject(n);if(event.optString("type")=="exit"){assertEquals(event.toString(),0,event.getInt("code"));exited=true}}}
                assertTrue("PTY did not consume EOF",exited)
            }finally{p.destroy()}
        }
    }
    @Test fun bunCallQuotaTerminatesAnExistingHostProcess() {
        val execution="shell-quota-${UUID.randomUUID()}"
        val broker=BunHostCapabilityBroker(context,android.os.Process.myUid(),execution,listOf(Grants.SHELL_CONTROL),{throw IllegalStateException("Unused console")},context.cacheDir.path)
        var count=0
        fun invoke(body:JSONObject):android.os.Bundle {
            count++
            val future=CompletableFuture<android.os.Bundle>()
            val request=android.os.Bundle().apply {
                putInt(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_HOST_CAPABILITY_BRIDGE_VERSION,1)
                putString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_EXECUTION_ID,execution)
                putString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_HOST_CALL_ID,"owned-$count")
                putString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_HOST_CAPABILITY,Grants.SHELL_CONTROL)
                putString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_HOST_CALL_REQUEST_JSON,body.toString())
            }
            broker.invoke(request,object:org.autojs.plugin.bun.runtime.api.IBunHostCapabilityCallback.Stub(){override fun onResult(reply:android.os.Bundle?){future.complete(requireNotNull(reply))}})
            return future.get(5,TimeUnit.SECONDS)
        }
        fun result(reply:android.os.Bundle)=JSONObject(reply.getString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_HOST_CALL_RESULT_JSON)!!).getJSONObject("value")
        try {
            val id=result(invoke(JSONObject().put("method","spawn").put("args",JSONArray().put("sleep 20")))).getString("id")
            var record:JSONObject?=null
            while(record==null){val reply=result(invoke(JSONObject().put("action","poll").put("id",id)));if(reply.getBoolean("done"))record=reply.getJSONObject("value")}
            val pid=record.getInt("pid");assertTrue(pid>0)
            var final=android.os.Bundle()
            while(count<=org.autojs.plugin.bun.runtime.api.BunRuntimeContract.MAX_HOST_CALLS_PER_EXECUTION)final=invoke(JSONObject().put("method","unsupported"))
            assertEquals(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.HOST_CALL_ERROR_TOO_MANY_REQUESTS,final.getString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_ERROR_CODE))
            val alive=runCatching { android.system.Os.kill(pid,0);true }.getOrDefault(false);assertFalse("Quota left process $pid running",alive)
        }finally{broker.close()}
    }
    @Test fun destructionDuringCreationLeavesNoLateHandle() {
        repeat(6){
            val p=provider();val f=CompletableFuture<NodeBridgeResponse>()
            p.dispatch(NodeBridgeRequest("late","shell","spawn","[\"sleep 20\"]",5000,permissions=listOf("shell"))){f.complete(it)}
            p.destroy();f.get(6,TimeUnit.SECONDS)
            assertEquals("0",p.nativeDiagnostics()["embedded_script.shell.active_handle_count"])
        }
    }
}
