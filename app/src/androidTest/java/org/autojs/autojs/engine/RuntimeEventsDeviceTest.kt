package org.autojs.autojs.engine

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RuntimeEventsDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    private val manager=context.getSystemService(NotificationManager::class.java)
    private val caps=listOf("events","events.notification","events.toast","events.key","notifications","notifications.settings")
    internal fun rebindNotificationListener(packageName:String=context.packageName) {
        val component="$packageName/org.autojs.autojs.core.notification.NotificationListenerService"
        val enabled=android.provider.Settings.Secure.getString(context.contentResolver,"enabled_notification_listeners").orEmpty()
        assertTrue("Test setup must authorize $component",component in enabled)
        for(command in listOf("disallow_listener","allow_listener")){
            ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("cmd notification $command $component")).use{it.readBytes()}
            Thread.sleep(500)
        }
        if(packageName==context.packageName)controls.await("Authorized notification listener reconnected",30000){org.autojs.autojs.core.notification.NotificationListenerService.instance?.connected==true}
        else Thread.sleep(1500)
    }
    internal fun rebindAccessibility(packageName:String=context.packageName) {
        // Instrumentation/force-stop restarts the target. Rebind only the already-authorized test component.
        val component="$packageName/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        val previous=android.provider.Settings.Secure.getString(context.contentResolver,"enabled_accessibility_services").orEmpty().split(':').filter{it.contains('/')}
        if(component !in previous)assertTrue("Only owned packaged fixtures may be authorized after launch",packageName in listOf("${context.packageName}.interop.events.bun","${context.packageName}.interop.events.worker"))
        val configured=(previous+component).distinct()
        fun set(services:List<String>){
            val value=services.joinToString(":");require(value.matches(Regex("[A-Za-z0-9_./:$]*")))
            val command=if(value.isEmpty())"settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $value"
            ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(command)).use{it.readBytes()}
        }
        if(component !in previous)set(configured)
        fun connected()=if(packageName==context.packageName)org.autojs.autojs.core.accessibility.AccessibilityService.registry.isOperational else
            context.getSystemService(android.view.accessibility.AccessibilityManager::class.java).getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any{it.resolveInfo.serviceInfo.packageName==packageName}
        for(attempt in 1..2){
            if(connected())break
            set(configured-component);Thread.sleep(750);set(configured)
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
            while(!connected()&&System.nanoTime()<deadline)Thread.sleep(100)
            android.util.Log.i("RuntimeEventsTest","Accessibility setup attempt $attempt, connected=${connected()}")
        }
        assertTrue("Accessibility is connected for $packageName",connected())
        if(packageName!=context.packageName)Thread.sleep(500)
    }
    private fun grants(enabled:Boolean=true,action:()->Unit){
        val keys=listOf(Grants.EVENTS_OBSERVE,Grants.EVENTS_NOTIFICATION,Grants.EVENTS_TOAST,Grants.EVENTS_KEY,Grants.NOTIFICATIONS_POST,Grants.NOTIFICATIONS_SETTINGS)
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE);val old=keys.associateWith{if(prefs.contains(it))prefs.getBoolean(it,false)else null}
        prefs.edit().apply{keys.forEach{putBoolean(it,enabled)}}.commit()
        try{action()}finally{prefs.edit().apply{old.forEach{(k,v)->if(v==null)remove(k)else putBoolean(k,v)}}.commit()}
    }
    internal fun inject(marker:String) {
        context.startActivity(Intent().setClassName(instrumentation.context.packageName,RuntimeEventsToastFixtureActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("ownedText",marker))
        controls.await("Owned external Toast Activity",5000){controls.labelled("Owned event fixture")!=null}
        Thread.sleep(500)
        // ADB's direct event injection did not enter accessibility's key filter on these devices.
        // Use the platform uinput fixture path so F8 traverses InputReader and the real key filter.
        val name="AutoJs6-events-${UUID.randomUUID()}"
        val file=File(context.getExternalFilesDir(null),"events-keys-${UUID.randomUUID()}.json")
        file.writeText("""
            {"id":1,"command":"register","name":"$name","vid":6354,"pid":11330,"bus":"usb","configuration":[{"type":100,"data":[1]},{"type":101,"data":[66]}]}
            {"id":1,"command":"delay","duration":750}
            {"id":1,"command":"inject","events":[1,66,1,0,0,0]}
            {"id":1,"command":"delay","duration":50}
            {"id":1,"command":"inject","events":[1,66,0,0,0,0]}
            {"id":1,"command":"delay","duration":250}
        """.trimIndent())
        try {ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("uinput ${file.absolutePath}")).use{it.readBytes()}}
        finally{check(file.canonicalPath.startsWith(context.getExternalFilesDir(null)!!.canonicalPath+File.separator));check(file.delete())}
        controls.await("Owned virtual keyboard released",3000){android.view.InputDevice.getDeviceIds().none{android.view.InputDevice.getDevice(it)?.name==name}}
    }
    private fun hasLog(text:String)=AutoJs.instance.globalConsole.snapshotLogEntries().any{it.content.toString().contains(text)}
    private fun execute(mode:String,body:String,marker:String,parentCheck:Boolean=false):JSONObject {
        val pool=Executors.newSingleThreadExecutor()
        try {
            val future=pool.submit<JSONObject>{harness.execute(mode,body,capabilities=caps)}
            controls.await("Event listeners ready",25000){if(future.isDone)fail("No ready marker: ${future.get()}");hasLog("READY_$marker")}
            inject(marker)
            if(parentCheck){
                controls.await("Parent notification checkpoint",20000){if(future.isDone)fail("No parent checkpoint: ${future.get()}");hasLog("PARENT_$marker")}
                assertTrue("Parent notification must survive Worker termination",manager.activeNotifications.any{it.notification.extras.getCharSequence("android.title")?.toString()=="PARENT_$marker"})
                assertFalse("Worker notifications must be released",manager.activeNotifications.any{it.notification.extras.getCharSequence("android.title")?.toString()=="ORPHAN_$marker"})
            }
            return future.get(25,TimeUnit.SECONDS)
        }finally{pool.shutdownNow()}
    }
    @Test fun mainNodeAndBunObserveRealSourcesAndPostSilentNotifications()=grants {harness.withFixture {
        rebindAccessibility()
        rebindNotificationListener()
        val rows=JSONArray()
        for(mode in listOf("node","bun")){
            val marker="EVENTS_${UUID.randomUUID()}";val channel="owned-${marker.lowercase()}"
            try {
                val imports=if(mode=="bun")"const events=host.events,notifications=host.notifications;" else "const events=require('autojs6:events'),notifications=require('autojs6:notifications');"
                val result=execute(mode,imports+"const marker=${JSONObject.quote(marker)},channel=${JSONObject.quote(channel)},ready=async()=>console.log('READY_'+marker);"+RuntimeEventsPrograms.exercise(),marker)
                assertEquals(4,result.getInt("sourceCount"));assertEquals(context.packageName,result.getJSONObject("notification").getString("packageName"))
                assertEquals(instrumentation.context.packageName,result.getJSONObject("toast").getString("packageName"));rows.put(JSONObject().put("runtime",mode).put("result",result))
            }finally{manager.deleteNotificationChannel(channel)}
        }
        File(context.getExternalFilesDir(null),"runtime-events-host.json").writeText(rows.toString(2))
    }}
    @Test fun cjsEsmAndNestedWorkersReleaseOwnedObserversAndNotifications()=harness.withFixture {
        rebindAccessibility()
        rebindNotificationListener()
        val rows=JSONArray()
        for(kind in listOf("cjs","esm","nested")){
            val marker="EVENTS_${UUID.randomUUID()}";val channel="owned-${marker.lowercase()}"
            val checks="""
                const pause=ms=>new Promise(r=>setTimeout(r,ms)),eq=(a,b,l)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(l);};
                const marker=${JSONObject.quote(marker)},channel=${JSONObject.quote(channel)},ready=async()=>console.log('READY_'+marker);
                if(typeof NativeEvents.EventEmitter!=='function')throw Error('Native events was replaced');
                const result=await(async()=>{${RuntimeEventsPrograms.exercise()}})();
                await events.observeBroadcasts();await notifications.notify({id:7,title:'ORPHAN_'+marker,channel,priority:'low'});
                parentPort.postMessage(result);setInterval(()=>{},1000);
            """.trimIndent()
            val cjs="const {parentPort}=require('node:worker_threads'),NativeEvents=require('events'),events=require('autojs6:events'),notifications=require('notifications');(async()=>{$checks})().catch(e=>{throw e});"
            val esm="import {parentPort} from 'node:worker_threads';import NativeEvents from 'node:events';import events from 'autojs6:events';import notifications from 'autojs6:notifications';$checks"
            val child=if(kind=="nested")"const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSONObject.quote(cjs)},{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e});" else if(kind=="esm")esm else cjs
            try {
                val result=execute("node","""
                    const events=require('autojs6:events'),notifications=require('autojs6:notifications'),{Worker}=require('node:worker_threads');
                    const marker=${JSONObject.quote(marker)},channel=${JSONObject.quote(channel)};
                    const parent=await events.observeBroadcasts();await notifications.notify({id:7,title:'PARENT_'+marker,channel,priority:'low'});
                    const result=await new Promise((resolve,reject)=>{const w=new Worker(${if(kind=="esm")"new URL('data:text/javascript,'+encodeURIComponent(${JSONObject.quote(child)}))" else JSONObject.quote(child)},{eval:${kind!="esm"}});w.on('message',async v=>{await w.terminate();resolve(v);});w.on('error',reject);});
                    await pause(250);console.log('PARENT_'+marker);await pause(1500);
                    eq(parent.closed,false,'Parent subscription survives');await parent.drainEvents();await parent.close();await notifications.cancelAll();return result;
                """.trimIndent(),marker,true)
                rows.put(JSONObject().put("kind",kind).put("result",result))
            }finally{manager.deleteNotificationChannel(channel)}
        }
        File(context.getExternalFilesDir(null),"runtime-events-worker.json").writeText(rows.toString(2))
    }
    @Test fun missingBunGrantsAndNodeSubGrantsRejectWithoutChangingAndroidSettings() {
        grants(false){harness.execute("bun","""
            eq((await rejects(()=>host.events.observeBroadcasts())).code,'NOT_GRANTED','Bun event grant');
            eq((await rejects(()=>host.notifications.notify({title:'denied'}))).code,'NOT_GRANTED','Bun notification grant');return {denied:true};
        """.trimIndent())}
        harness.execute("node","""
            const events=require('autojs6:events'),notifications=require('notifications');
            for(const method of ['observeNotification','observeToast','observeKey'])await rejects(()=>events[method]());
            await rejects(()=>notifications.openSettings({dryRun:true}));await events.close();return {denied:true};
        """.trimIndent(),capabilities=listOf("events","notifications"))
    }
}
