package org.autojs.autojs.engine

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class RuntimeJavaCallbacksNativeTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun request(provider:AndroidNodeBridgeCapabilityProvider,method:String,payload:JSONObject=JSONObject()):CompletableFuture<NodeBridgeResponse> {
        val result=CompletableFuture<NodeBridgeResponse>()
        provider.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(),"java",method,JSONArray().put(payload).toString(),120000,permissions=listOf("java_interop"))){result.complete(it)}
        return result
    }
    private fun ok(result:CompletableFuture<NodeBridgeResponse>):String {
        val response=result.get(125,TimeUnit.SECONDS);assertTrue(response.toString(),response.ok);return response.resultJson!!
    }
    private fun proxy(provider:AndroidNodeBridgeCapabilityProvider,client:String,timeout:Long=5000):JSONObject = JSONObject(ok(request(provider,"proxy",JSONObject()
        .put("client",client).put("callback","$client-owner").put("callbackTimeoutMs",timeout).put("interfaces",JSONArray().put("java.lang.Runnable")).put("methods",JSONArray().put("*")))))
    private fun event(provider:AndroidNodeBridgeCapabilityProvider,client:String):JSONObject {
        val end=System.currentTimeMillis()+5000
        while(System.currentTimeMillis()<end) {
            val values=JSONArray(ok(request(provider,"drainCallbacks",JSONObject().put("client",client).put("waitMs",500))))
            if(values.length()>0)return values.getJSONObject(0)
        }
        error("No callback event")
    }
    @Test fun mainThreadReentryReturnsAndBorrowedHandlesAreReleased() {
        val provider=AndroidNodeBridgeCapabilityProvider(context);val client="native-${UUID.randomUUID()}"
        try {
            val value=proxy(provider,client);val handle=value.getString("__autojs6JavaHandle")
            val invocation=request(provider,"call",JSONObject().put("handle",handle).put("method","run").put("thread","main"))
            val event=event(provider,client);val callbackId=event.getString("id");val borrowed=event.getJSONObject("receiver").getString("__autojs6JavaHandle")
            val looper=JSONObject(ok(request(provider,"callStatic",JSONObject().put("className","android.os.Looper").put("method","myLooper").put("callbackInvocationId",callbackId))))
            assertEquals("true",ok(request(provider,"call",JSONObject().put("handle",looper.getString("__autojs6JavaHandle")).put("method","isCurrentThread").put("callbackInvocationId",callbackId))))
            assertEquals("true",ok(request(provider,"completeCallback",JSONObject().put("client",client).put("id",callbackId).put("value",JSONObject.NULL))))
            assertEquals("null",ok(invocation));assertFalse(request(provider,"getClassName",JSONObject().put("handle",borrowed)).get(10,TimeUnit.SECONDS).ok)
            assertEquals("0",provider.nativeDiagnostics()["embedded_script.java.pending_callbacks"])
            ok(request(provider,"release",JSONObject().put("handle",handle)));assertEquals("0",provider.nativeDiagnostics()["embedded_script.java.callback_registrations"])
        } finally {provider.destroy()}
    }
    @Test fun callbackTimeoutAndOwnerReleaseUnblockNativeCallers() {
        val provider=AndroidNodeBridgeCapabilityProvider(context);val client="timeout-${UUID.randomUUID()}"
        try {
            val value=proxy(provider,client,1000);val handle=value.getString("__autojs6JavaHandle")
            val first=request(provider,"call",JSONObject().put("handle",handle).put("method","run"));val firstEvent=event(provider,client)
            val timed=first.get(3,TimeUnit.SECONDS);assertFalse(timed.ok);assertTrue(timed.error.toString(),timed.error!!.message.contains("timed out"))
            assertEquals("false",ok(request(provider,"completeCallback",JSONObject().put("client",client).put("id",firstEvent.getString("id")).put("value",JSONObject.NULL))))
            val next=request(provider,"call",JSONObject().put("handle",handle).put("method","run"));event(provider,client)
            ok(request(provider,"release",JSONObject().put("handle",handle)));assertFalse(next.get(3,TimeUnit.SECONDS).ok)
            assertEquals("0",provider.nativeDiagnostics()["embedded_script.java.pending_callbacks"])
        } finally {provider.destroy()}
    }
    /** Class.getTypeName only exists from API 26; the names below reach scripts as signatures and come back in callSuper. */
    @Test fun typeNamesMatchThePlatformFromApi26() {
        val names=mapOf<Class<*>,String>(Int::class.javaPrimitiveType!! to "int",String::class.java to "java.lang.String",ByteArray::class.java to "byte[]",
            Array<IntArray>::class.java to "int[][]",Map.Entry::class.java to "java.util.Map\$Entry",emptyArray<Map.Entry<*,*>>().javaClass to "java.util.Map\$Entry[]",
            Array<Array<String>>::class.java to "java.lang.String[][]")
        for((type,name) in names) {
            assertEquals(name,RuntimeJavaSubclassGenerator.typeName(type))
            if(Build.VERSION.SDK_INT>=26)assertEquals(name,type.typeName)
        }
    }
    @Test fun superCallsResolveArrayAndNestedParameterTypes() {
        val provider=AndroidNodeBridgeCapabilityProvider(context);val client="super-${UUID.randomUUID()}"
        fun instance(base:String,key:String):String {
            val name=JSONObject(ok(request(provider,"defineClass",JSONObject().put("client",client).put("callback","$client-$base").put("extends",base)
                .put("methods",JSONArray().put(key))))).getString("__autojs6JavaClass")
            return JSONObject(ok(request(provider,"new",JSONObject().put("className",name)))).getString("__autojs6JavaHandle")
        }
        fun callSuper(event:JSONObject)=ok(request(provider,"callSuper",JSONObject().put("handle",event.getJSONObject("receiver").getString("__autojs6JavaHandle"))
            .put("callbackInvocationId",event.getString("id")).put("args",event.getJSONArray("args"))))
        fun complete(event:JSONObject,value:Any)=ok(request(provider,"completeCallback",JSONObject().put("client",client).put("id",event.getString("id")).put("value",value)))
        try {
            val stream=instance("java.io.ByteArrayOutputStream","write(byte[],int,int)")
            val written=request(provider,"call",JSONObject().put("handle",stream).put("method","write").put("args",JSONArray().put(JSONArray().put(1).put(2).put(3)).put(0).put(2)))
            val write=event(provider,client)
            assertEquals("write(byte[],int,int)",write.getString("signature"));assertEquals("write(byte[],int,int)",write.getString("key"))
            assertEquals("null",callSuper(write));assertEquals("true",complete(write,JSONObject.NULL));assertEquals("null",ok(written))
            assertEquals("[1,2]",ok(request(provider,"call",JSONObject().put("handle",stream).put("method","toByteArray"))))
            val thread=instance("java.lang.Thread","setUncaughtExceptionHandler(java.lang.Thread\$UncaughtExceptionHandler)")
            val set=request(provider,"call",JSONObject().put("handle",thread).put("method","setUncaughtExceptionHandler").put("args",JSONArray().put(JSONObject.NULL)))
            val handler=event(provider,client)
            assertEquals("setUncaughtExceptionHandler(java.lang.Thread\$UncaughtExceptionHandler)",handler.getString("signature"))
            assertEquals("null",callSuper(handler));assertEquals("true",complete(handler,JSONObject.NULL));assertEquals("null",ok(set))
        } finally {provider.destroy()}
    }
    @Test fun loadersAreExecutionLocalAndOwnedReadOnlyCacheIsRemoved() {
        val provider=AndroidNodeBridgeCapabilityProvider(context);val other=AndroidNodeBridgeCapabilityProvider(context)
        val source=File(context.getExternalFilesDir(null),"java-callback-fixtures/callback.jar")
        val before=RuntimeWebMediaProof.sha256(source.readBytes());var owned:File?=null
        try {
            val loader=JSONObject(ok(request(provider,"loadJar",JSONObject().put("paths",JSONArray().put(source.path)))))
            val code=File(loader.getJSONArray("paths").getString(0));assertFalse(code.canWrite());owned=code.parentFile!!.parentFile!!
            require(owned.parentFile==context.codeCacheDir.canonicalFile)
            val type=JSONObject().put("className","org.autojs.fixture.runtime.CallbackFixture")
            ok(request(provider,"type",type));assertFalse(request(other,"type",type).get(10,TimeUnit.SECONDS).ok)
            assertEquals(before,RuntimeWebMediaProof.sha256(source.readBytes()))
        } finally {provider.destroy();other.destroy()}
        val end=System.currentTimeMillis()+3000;while(owned!!.exists() && System.currentTimeMillis()<end)Thread.sleep(20)
        assertFalse("Owned class cache remains",owned.exists())
    }
    @Test fun externalCallbackReentryKeepsCodeUntilTheOperationFinishes() {
        val objects=java.util.concurrent.ConcurrentHashMap<String,Any>()
        val handles=object:ControlledJavaInteropPolicy.Handles {
            override fun resolve(handleId:String)=objects[handleId]
            override fun register(value:Any,spec:JavaInteropClassSpec):JSONObject {
                val id=UUID.randomUUID().toString();objects[id]=value;return ControlledJavaInteropPolicy.handleJson(id,value,spec)
            }
        }
        val scope=RuntimeJavaSession(context,context.cacheDir.path,handles){objects.remove(it)}
        val client="external-${UUID.randomUUID()}";val entered=java.util.concurrent.CountDownLatch(1);val resume=java.util.concurrent.CountDownLatch(1)
        var thread:Thread?=null
        try {
            val loaded=scope.invoke("loadJar",JSONObject().put("paths",JSONArray().put(File(context.getExternalFilesDir(null),"java-callback-fixtures/callback.jar").path)))
            assertNull(loaded.error);val code=File((loaded.value as JSONObject).getJSONArray("paths").getString(0));val root=code.parentFile!!.parentFile!!
            val created=scope.invoke("proxy",JSONObject().put("client",client).put("callback",client).put("interfaces",JSONArray().put("java.lang.Runnable")).put("methods",JSONArray().put("*")))
            assertNull(created.error);val runnable=objects[(created.value as JSONObject).getString("__autojs6JavaHandle")] as Runnable
            thread=Thread{runnable.run()}.also {it.start()}
            var event:JSONObject?=null;val deadline=System.currentTimeMillis()+5000
            while(event==null && System.currentTimeMillis()<deadline) {
                val result=scope.invoke("drainCallbacks",JSONObject().put("client",client).put("waitMs",500));assertNull(result.error)
                event=(result.value as JSONArray).optJSONObject(0)
            }
            assertNotNull(event)
            val response=CompletableFuture<NodeBridgeResponse>()
            scope.submit(NodeBridgeRequest("reentry","java","call",JSONArray().put(JSONObject().put("callbackInvocationId",event!!.getString("id"))).toString(),5000),{response.complete(it)}) {
                entered.countDown();check(resume.await(3,TimeUnit.SECONDS));NodeBridgeResponse.success("reentry","null")
            }
            assertTrue(entered.await(2,TimeUnit.SECONDS));scope.close();assertTrue("Code was removed during reentry",root.exists())
            resume.countDown();assertTrue(response.get(3,TimeUnit.SECONDS).ok);thread.join(3000);assertFalse(thread.isAlive)
            assertFalse("Code remains after the in-flight operation",root.exists())
        } finally {resume.countDown();thread?.join(3000);scope.close()}
    }
}
