package org.autojs.autojs.engine

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class BunSensorsDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private fun grant(enabled:Boolean=true,action:()->Unit) {
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE);val key=Grants.SENSORS_CONTROL
        val before=if(prefs.contains(key))prefs.getBoolean(key,false)else null;prefs.edit().putBoolean(key,enabled).commit()
        try{action()}finally{prefs.edit().apply{if(before==null)remove(key)else putBoolean(key,before)}.commit()}
    }
    private fun run(mode:String,body:String)=harness.execute(mode,
        (if(mode=="bun")"const sensors=host.sensors;" else "const sensors=require('sensors');")+body,capabilities=listOf("sensors"))
    @Test fun bunAndNodeSamplePhysicalSensorsAndReleaseListeners()=grant {
        val rows=JSONArray();for(mode in listOf("node","bun"))rows.put(JSONObject().put("mode",mode).put("result",run(mode,RuntimeSensorPrograms.exercise())))
        File(context.getExternalFilesDir(null),"bun-sensors-host.json").writeText(rows.toString(2))
    }
    @Test fun workersCjsEsmAndNestedUseSensorsAndTerminateSubscriptions() {
        val checks="""
            const eq=(a,b,label)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label+': '+JSON.stringify(a));};
            const rejects=async fn=>{try{await fn();}catch(e){return e;}throw Error('Expected rejection');};
            const result=await(async()=>{${RuntimeSensorPrograms.exercise()}})();
            const orphan=sensors.subscribe('accelerometer');await orphan.ready;parentPort.postMessage(result);
        """.trimIndent()
        val cjs="const {parentPort}=require('node:worker_threads'),sensors=require('autojs6:sensors');(async()=>{$checks})().catch(e=>{throw e});"
        val esm="import {parentPort} from 'node:worker_threads';import sensors,{once,policy} from 'sensors';if(once!==sensors.once||policy.maxSubscriptions!==4)throw Error('ESM exports');$checks"
        val nested="const {Worker,parentPort}=require('node:worker_threads');const child=new Worker(${JSONObject.quote(cjs)},{eval:true});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e});"
        val result=run("node","""
            const {Worker}=require('node:worker_threads'),results=[];
            for(const [source,esm] of [[${JSONObject.quote(cjs)},false],[${JSONObject.quote(esm)},true],[${JSONObject.quote(nested)},false]]){
              results.push(await new Promise((resolve,reject)=>{const w=new Worker(esm?new URL('data:text/javascript,'+encodeURIComponent(source)):source,{eval:!esm});w.on('message',async value=>{await w.terminate();resolve(value);});w.on('error',reject);}));
              await pause(150);const subs=[];for(let i=0;i<4;i++){const s=sensors.subscribe('accelerometer');await s.ready;subs.push(s);}for(const s of subs)await s.close();
            }return {cjs:results[0],esm:results[1],nested:results[2],terminationReleased:true};
        """.trimIndent())
        File(context.getExternalFilesDir(null),"bun-sensors-worker.json").writeText(result.toString(2))
    }
    @Test fun missingGrantAndUnknownRawMethodAreRejected() {
        grant(false){run("bun","""
            eq((await rejects(()=>sensors.getAvailableSensors())).code,'NOT_GRANTED','Missing grant');
            const response=await fetch('http://autojs6/v1/sensors.control',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',body:JSON.stringify({method:'subscribe',args:[{type:'accelerometer'}]})});eq(response.status,403,'Raw denial');return {denied:true};
        """.trimIndent())}
        grant{run("bun","""
            const response=await fetch('http://autojs6/v1/sensors.control',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',body:JSON.stringify({method:'registerRawListener',args:[]})});eq(response.status,400,'Raw method');return {bounded:true};
        """.trimIndent())}
        assertTrue(Grants.validatePackagedCapabilities(listOf(Grants.SENSORS_CONTROL)).contains(Grants.SENSORS_CONTROL))
    }
    private fun provider()=AndroidNodeBridgeCapabilityProvider(context,NodeBridgeEngineInfo.fromContext(context,id="sensor-${UUID.randomUUID()}",cwd=context.cacheDir.path),permissionManifest=NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("sensors","image")))
    private fun call(provider:AndroidNodeBridgeCapabilityProvider,module:String,method:String,args:JSONArray):NodeBridgeResponse {
        val future=CompletableFuture<NodeBridgeResponse>()
        provider.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(),module,method,args.toString(),5000,permissions=NodeBridgePermissionManifest.requiredCapabilitiesFor(module,method))){future.complete(it)}
        return future.get(6,TimeUnit.SECONDS)
    }
    @Test fun cancelledBunRegistrationsReleaseUnclaimedSubscriptions() {
        val provider=provider();val bridge=BunAutomationBridge(context,listOf(Grants.SENSORS_CONTROL)){provider}
        fun awaitCount(expected:String){val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"]!=expected&&System.nanoTime()<deadline)Thread.sleep(10);assertEquals(expected,provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])}
        try{repeat(6){
            val response=bridge.invoke(Grants.SENSORS_CONTROL,JSONObject().put("method","subscribe").put("args",JSONArray().put(JSONObject().put("type","accelerometer"))))
            assertTrue(response.toString(),response.ok);val id=JSONObject(response.resultJson).getString("id");awaitCount("1")
            assertTrue(bridge.invoke(Grants.SENSORS_CONTROL,JSONObject().put("action","cancel").put("id",id)).ok);awaitCount("0")
        }}finally{bridge.close();provider.destroy()}
    }
    @Test fun destructionBeforeQueuedRegistrationCannotLeakALateSensor() {
        val provider=provider();val blocked=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1);val future=CompletableFuture<NodeBridgeResponse>()
        android.os.Handler(android.os.Looper.getMainLooper()).post{blocked.countDown();release.await(10,TimeUnit.SECONDS)}
        assertTrue(blocked.await(5,TimeUnit.SECONDS))
        try{provider.dispatch(NodeBridgeRequest("queued-sensor","sensors","subscribe","[{\"type\":\"accelerometer\"}]",5000,permissions=listOf("sensors"))){future.complete(it)};provider.destroy()}finally{release.countDown()}
        assertFalse(future.get(5,TimeUnit.SECONDS).ok);assertEquals("0",provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
    }
    @Test fun sensorUnsubscribeCannotCloseAnImageResource() {
        val provider=provider();val bitmap=android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.RED)}
        try{
            val bytes=java.io.ByteArrayOutputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}
            val response=call(provider,"image","fromBase64",JSONArray().put(android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)));assertTrue(response.toString(),response.ok)
            val image=JSONObject(response.resultJson);assertTrue(call(provider,"sensors","unsubscribe",JSONArray().put(JSONObject().put("id",image.getString("id")))).ok)
            val pixel=call(provider,"image","pixel",JSONArray().put(image).put(0).put(0));assertTrue(pixel.toString(),pixel.ok);assertEquals(android.graphics.Color.RED,pixel.resultJson!!.toInt())
        }finally{bitmap.recycle();provider.destroy()}
    }
}
