package org.autojs.autojs.engine

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class RuntimeEventsNativeTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun provider(caps:List<String> = listOf("events","notifications","notifications.settings"))=AndroidNodeBridgeCapabilityProvider(context,
        NodeBridgeEngineInfo.fromContext(context,id="events-native-${UUID.randomUUID()}",cwd=context.cacheDir.path),
        permissionManifest=NodeBridgePermissionManifest.fromDeclaredCapabilities(caps))
    private fun call(p:NodeBridgeCapabilityProvider,module:String,method:String,args:JSONArray=JSONArray()):NodeBridgeResponse {
        val future=CompletableFuture<NodeBridgeResponse>();val text=args.toString()
        p.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(),module,method,text,5000,permissions=NodeBridgePermissionManifest.requiredCapabilitiesFor(module,method,text))){future.complete(it)}
        return future.get(6,TimeUnit.SECONDS)
    }
    private fun ok(value:NodeBridgeResponse):String {assertTrue(value.toString(),value.ok);return value.resultJson!!}
    @Test fun multipleBroadcastSubscribersHaveIndependentQueuesAndCleanup() {
        val p=provider();try{
            val a=JSONObject(ok(call(p,"events","observeBroadcasts"))).getString("id")
            val b=JSONObject(ok(call(p,"events","observeBroadcasts"))).getString("id");assertNotEquals(a,b)
            for(id in listOf(a,b)){
                var battery=false;val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4)
                while(!battery&&System.nanoTime()<end){val rows=JSONArray(ok(call(p,"events","drainEvents",JSONArray().put(id))));battery=(0 until rows.length()).any{rows.getJSONObject(it).optString("type")=="battery_changed"};if(!battery)Thread.sleep(50)}
                assertTrue("Actual sticky battery event for $id",battery)
            }
            assertEquals("true",ok(call(p,"events","close",JSONArray().put(a))));assertEquals("1",p.nativeDiagnostics()["embedded_script.events.active_handle_count"])
            assertFalse(call(p,"events","drainEvents",JSONArray().put(a)).ok)
            p.destroy();assertEquals("0",p.nativeDiagnostics()["embedded_script.events.active_handle_count"])
        }finally{p.destroy()}
    }
    @Test fun eventQueueReportsOverflowAndClosesExactlyOnce() {
        val session=NodeEventObservers.Session("owned","key",null);var detached=0;session.detach={detached++}
        repeat(135){session.publish(JSONObject().put("type","key").put("index",it))}
        val first=session.drain();assertEquals(32,first.length());assertEquals("overflow",first.getJSONObject(0).getString("type"));assertEquals(7,first.getJSONObject(0).getInt("dropped"));assertEquals(7,first.getJSONObject(1).getInt("index"))
        session.close("owned-end");session.close("again");assertEquals(1,detached)
        val closed=session.drain();assertEquals(1,closed.length());assertEquals("close",closed.getJSONObject(0).getString("type"))
    }
    @Test fun notificationIdsAreIsolatedByExecutionAndClientScope() {
        val one=provider();val two=provider();val manager=context.getSystemService(NotificationManager::class.java)
        val channel="owned-events-${UUID.randomUUID()}"
        fun post(p:NodeBridgeCapabilityProvider,scope:String,title:String)=ok(call(p,"notifications","notify",JSONArray().put(JSONObject().put("id",77).put("title",title).put("channel",channel).put("priority","low")).put(scope)))
        fun count()=manager.activeNotifications.count{it.notification.channelId==channel && it.tag?.startsWith("autojs6-runtime-")==true}
        fun expectCount(expected:Int){val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(count()!=expected&&System.nanoTime()<deadline)Thread.sleep(20);assertEquals(expected,count())}
        try {
            post(one,"parent","one");post(one,"worker","two");post(two,"parent","three");expectCount(3)
            ok(call(one,"notifications","cancelAll",JSONArray().put("worker")));expectCount(2)
            ok(call(one,"notifications","cancel",JSONArray().put(77).put("parent")));expectCount(1)
            two.destroy();expectCount(0)
        }finally{one.destroy();two.destroy();manager.deleteNotificationChannel(channel)}
    }
    @Test fun eventSubGrantsAndNotificationSettingsAreIndependent() {
        val p=provider(listOf("events","notifications"));try {
            for(method in listOf("observeNotification","observeToast","observeKey"))assertFalse(call(p,"events",method).ok)
            assertFalse(call(p,"notifications","openSettings",JSONArray().put(JSONObject().put("dryRun",true))).ok)
            assertTrue(call(p,"notifications","getPermissionStatus").ok)
        }finally{p.destroy()}
        val caps=BunAutomationBridge.nodeCapabilities(listOf(Grants.EVENTS_OBSERVE,Grants.EVENTS_KEY,Grants.NOTIFICATIONS_POST))
        assertEquals(listOf("events","events.key","notifications"),caps)
    }
    @Test fun abandonedBunObserversAndNotificationsReleaseLateResources() {
        for(kind in listOf("events","notifications")){
            val p=provider();val capability=if(kind=="events")Grants.EVENTS_OBSERVE else Grants.NOTIFICATIONS_POST
            val bridge=BunAutomationBridge(context,listOf(capability)){p};val channel="owned-abandon-${UUID.randomUUID()}";val manager=context.getSystemService(NotificationManager::class.java)
            try{
                val args=if(kind=="events")JSONArray() else JSONArray().put(JSONObject().put("title","owned").put("channel",channel).put("priority","low")).put("late")
                val job=JSONObject(ok(bridge.invoke(capability,JSONObject().put("method",if(kind=="events")"observeBroadcasts" else "notify").put("args",args)))).getString("id")
                val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4)
                fun live()=if(kind=="events")p.nativeDiagnostics()["embedded_script.events.active_handle_count"]=="1" else manager.activeNotifications.any{it.notification.channelId==channel && it.tag?.startsWith("autojs6-runtime-")==true}
                while(!live()&&System.nanoTime()<end)Thread.sleep(10);assertTrue(live())
                ok(bridge.invoke(capability,JSONObject().put("action","cancel").put("id",job)))
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(live()&&System.nanoTime()<deadline)Thread.sleep(10);assertFalse(live())
            }finally{bridge.close();p.destroy();manager.deleteNotificationChannel(channel)}
        }
    }
    @Test fun destructionBeforeQueuedRegistrationDoesNotLeakObserversOrNotifications() {
        repeat(4){
            val p=provider();val future=CompletableFuture<NodeBridgeResponse>()
            p.dispatch(NodeBridgeRequest("late","events","observeBroadcasts","[]",5000,permissions=listOf("events"))){future.complete(it)}
            p.destroy();future.get(6,TimeUnit.SECONDS);assertEquals("0",p.nativeDiagnostics()["embedded_script.events.active_handle_count"])
            assertFalse(call(p,"notifications","notify",JSONArray().put(JSONObject().put("title","must not post"))).ok)
        }
    }
    @Test fun bunQuotaExhaustionReleasesObserversAndNotificationsBeforeExecutionEnds() {
        val execution="events-quota-${UUID.randomUUID()}";val channel="owned-quota-${UUID.randomUUID()}"
        val broker=BunHostCapabilityBroker(context,android.os.Process.myUid(),execution,listOf(Grants.EVENTS_OBSERVE,Grants.NOTIFICATIONS_POST),{throw IllegalStateException("Unused console")},context.cacheDir.path)
        var count=0
        fun invoke(capability:String,body:JSONObject):android.os.Bundle {
            count++;val future=CompletableFuture<android.os.Bundle>()
            val request=android.os.Bundle().apply {
                putInt(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_HOST_CAPABILITY_BRIDGE_VERSION,1)
                putString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_EXECUTION_ID,execution)
                putString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_HOST_CALL_ID,"owned-$count")
                putString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_HOST_CAPABILITY,capability)
                putString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_HOST_CALL_REQUEST_JSON,body.toString())
            }
            broker.invoke(request,object:org.autojs.plugin.bun.runtime.api.IBunHostCapabilityCallback.Stub(){override fun onResult(reply:android.os.Bundle?){future.complete(requireNotNull(reply))}})
            return future.get(5,TimeUnit.SECONDS)
        }
        fun result(reply:android.os.Bundle)=JSONObject(reply.getString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_HOST_CALL_RESULT_JSON)!!).getJSONObject("value")
        fun start(capability:String,method:String,args:JSONArray=JSONArray()){
            val id=result(invoke(capability,JSONObject().put("method",method).put("args",args))).getString("id")
            while(!result(invoke(capability,JSONObject().put("action","poll").put("id",id))).getBoolean("done"))Unit
        }
        val manager=context.getSystemService(NotificationManager::class.java)
        try{
            start(Grants.EVENTS_OBSERVE,"observeBroadcasts")
            start(Grants.NOTIFICATIONS_POST,"notify",JSONArray().put(JSONObject().put("title","owned quota").put("channel",channel).put("priority","low")).put("quota"))
            val provider=broker.javaClass.getDeclaredField("imageProvider").apply{isAccessible=true}.get(broker) as AndroidNodeBridgeCapabilityProvider
            assertEquals("1",provider.nativeDiagnostics()["embedded_script.events.active_handle_count"])
            var reply=android.os.Bundle()
            while(count<=org.autojs.plugin.bun.runtime.api.BunRuntimeContract.MAX_HOST_CALLS_PER_EXECUTION)reply=invoke(Grants.EVENTS_OBSERVE,JSONObject().put("method","unsupported"))
            assertEquals(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.HOST_CALL_ERROR_TOO_MANY_REQUESTS,reply.getString(org.autojs.plugin.bun.runtime.api.BunRuntimeContract.KEY_ERROR_CODE))
            assertEquals("0",provider.nativeDiagnostics()["embedded_script.events.active_handle_count"])
            val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
            while(manager.activeNotifications.any{it.notification.channelId==channel}&&System.nanoTime()<end)Thread.sleep(20)
            assertFalse(manager.activeNotifications.any{it.notification.channelId==channel})
        }finally{broker.close();manager.deleteNotificationChannel(channel)}
    }
}
