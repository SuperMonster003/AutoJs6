package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.autojs.autojs.timing.TimedTaskManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class RuntimeAndroidTasksNativeTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    @Before fun recoverExplicitlyNamedOwnedFixtures() {
        val values=InstrumentationRegistry.getArguments().getString("recoverNativeFolders")?:return
        for(value in values.split(',').filter{it.isNotBlank()}) {
            require(UUID.fromString(value).toString()==value)
            val root=File(context.cacheDir,"android-tasks-native-$value").canonicalFile
            require(root.parentFile==context.cacheDir.canonicalFile)
            TimedTaskManager.allTasksAsList.filter{it.scriptPath?.startsWith(root.path+File.separator)==true}.forEach(TimedTaskManager::removeTaskSync)
            TimedTaskManager.allIntentTasksAsList.filter{it.scriptPath?.startsWith(root.path+File.separator)==true}.forEach(TimedTaskManager::removeTaskSync)
            check(TimedTaskManager.allTasksAsList.none{it.scriptPath?.startsWith(root.path+File.separator)==true})
            check(TimedTaskManager.allIntentTasksAsList.none{it.scriptPath?.startsWith(root.path+File.separator)==true})
            root.deleteRecursively()
        }
    }
    private fun provider(root:File,caps:List<String> = listOf("android.objects","tasks"))=AndroidNodeBridgeCapabilityProvider(context,
        NodeBridgeEngineInfo.fromContext(context,id="android-tasks-${UUID.randomUUID()}",cwd=root.path),permissionManifest=NodeBridgePermissionManifest.fromDeclaredCapabilities(caps))
    private fun call(p:NodeBridgeCapabilityProvider,module:String,method:String,args:JSONArray=JSONArray()):NodeBridgeResponse {
        val future=CompletableFuture<NodeBridgeResponse>();val text=args.toString()
        p.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(),module,method,text,5000,permissions=NodeBridgePermissionManifest.requiredCapabilitiesFor(module,method,text))){future.complete(it)}
        return future.get(6,TimeUnit.SECONDS)
    }
    private fun ok(response:NodeBridgeResponse):String {assertTrue(response.toString(),response.ok);return response.resultJson!!}
    private fun fixture(action:(File)->Unit) {
        val root=File(context.cacheDir,"android-tasks-native-${UUID.randomUUID()}").canonicalFile.apply{mkdirs()}
        val beforeTimed=TimedTaskManager.allTasksAsList.map{it.id to it.scriptPath}.toSet()
        val beforeIntent=TimedTaskManager.allIntentTasksAsList.map{it.id to it.scriptPath}.toSet()
        try{action(root)}finally{
            TimedTaskManager.allTasksAsList.filter{it.scriptPath?.startsWith(root.path+File.separator)==true}.forEach(TimedTaskManager::removeTaskSync)
            TimedTaskManager.allIntentTasksAsList.filter{it.scriptPath?.startsWith(root.path+File.separator)==true}.forEach(TimedTaskManager::removeTaskSync)
            assertEquals(beforeTimed,TimedTaskManager.allTasksAsList.map{it.id to it.scriptPath}.toSet())
            assertEquals(beforeIntent,TimedTaskManager.allIntentTasksAsList.map{it.id to it.scriptPath}.toSet())
            check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()
        }
    }
    @Test fun androidPaintColorStateListAndFileUriUseRealFrameworkObjects()=fixture {root->harness.withFixture {
        val p=provider(root)
        try{
            val paint=JSONObject(ok(call(p,"android","createPaint",JSONArray().put(JSONObject().put("color",-65536).put("textSize",24)))))
            ok(call(p,"android","setPaintColor",JSONArray().put(paint.getString("id")).put(-16711936)))
            assertEquals(-16711936,JSONObject(ok(call(p,"android","getPaintInfo",JSONArray().put(paint.getString("id"))))).getInt("color"))
            assertTrue(ok(call(p,"android","measureText",JSONArray().put(paint.getString("id")).put("AutoJs6"))).toDouble()>0)
            val descriptor=JSONObject().put("states",JSONArray().put(JSONArray().put("pressed")).put(JSONArray())).put("colors",JSONArray().put(-65536).put(-16776961))
            val list=JSONObject(ok(call(p,"android","createColorStateList",JSONArray().put(descriptor))))
            assertEquals("-65536",ok(call(p,"android","getColorForState",JSONArray().put(list.getString("id")).put(JSONArray().put("pressed")))))
            instrumentation.runOnMainSync {
                val view=TextView(context);RuntimeViewAttributes.apply(view,JSONObject().put("textColor",list),true)
                assertEquals(-16776961,view.currentTextColor);view.isPressed=true;assertEquals(-65536,view.currentTextColor)
            }
            val owned=File(root,"owned.txt").apply{writeText("owned-uri")}
            val uri=JSONObject(ok(call(p,"android","getUriForFile",JSONArray().put(owned.path))))
            assertEquals(context.packageName,context.packageManager.resolveContentProvider(uri.getString("authority"),0)?.packageName)
            assertEquals("content",uri.getString("scheme"));context.contentResolver.openInputStream(android.net.Uri.parse(uri.getString("value")))!!.use{assertEquals("owned-uri",it.readBytes().toString(Charsets.UTF_8))}
            assertTrue(JSONObject(ok(call(p,"android","getThemeColor"))).has("colorPrimary"))
            p.destroy();assertEquals("0",p.nativeDiagnostics()["embedded_script.android.active_object_count"])
        }finally{p.destroy()}
    }}
    @Test fun persistentCalendarCrudCoversEveryRepeatModeAndSurvivesProviderExit()=fixture {root->
        File(root,"child.cjs").writeText("'nodejs';")
        val p=provider(root)
        try{
            val modes=listOf("disposable","daily","weekly","monthly","yearly","countdown")
            val ids=modes.map {mode->
                val options=JSONObject().put("path","child.cjs").put("repeatMode",mode).put("time","23:59:59").put("date",System.currentTimeMillis()+86_400_000)
                    .put("delayMs",86_400_000).put("daysOfWeek",JSONArray().put(1).put(7)).put("dayOfMonth",29).put("monthOfYear",2)
                val value=JSONObject(ok(call(p,"tasks","addTimedTask",JSONArray().put(options))));assertEquals(mode,value.getString("repeatMode"));value.getLong("id")
            }
            assertEquals(6,JSONArray(ok(call(p,"tasks","queryTimedTasks",JSONArray().put(JSONObject().put("path","child.cjs"))))).length())
            val daily=JSONObject(ok(call(p,"tasks","updateTask",JSONArray().put(JSONObject().put("id",ids[1]).put("kind","timed").put("time","23:58:59").put("endMode","count").put("times",2)))))
            assertEquals(2,daily.getJSONObject("options").getInt("times"));assertEquals("23:58:59.000",daily.getJSONObject("options").getString("time"))
            val deadline=TimedTaskManager.getTimedTask(ids[5])!!.millis
            Thread.sleep(20)
            val countdown=JSONObject(ok(call(p,"tasks","updateTask",JSONArray().put(JSONObject().put("id",ids[5]).put("kind","timed").put("path","child.cjs")))))
            assertEquals("Unchanged countdown deadline",deadline,countdown.getLong("millis"))
            p.destroy();ids.forEach{assertNotNull(TimedTaskManager.getTimedTask(it))}
        }finally{p.destroy()}
    }
    @Test fun cancelledBunTaskAndObjectRegistrationsAreRemovedBeforeExecutionEnds()=fixture {root->harness.withFixture {
        File(root,"child.cjs").writeText("'nodejs';")
        val p=provider(root);val bridge=BunAutomationBridge(context,listOf(Grants.ANDROID_OBJECTS,Grants.TASKS_CONTROL)){p}
        try{
            for((cap,method,args) in listOf(Triple(Grants.ANDROID_OBJECTS,"createPaint",JSONArray().put(JSONObject())),Triple(Grants.TASKS_CONTROL,"addTimedTask",JSONArray().put(JSONObject().put("path","child.cjs").put("repeatMode","daily").put("time","23:59:59"))))){
                val id=JSONObject(ok(bridge.invoke(cap,JSONObject().put("method",method).put("args",args)))).getString("id")
                Thread.sleep(150);ok(bridge.invoke(cap,JSONObject().put("action","cancel").put("id",id)))
            }
            harness.await("Cancelled native registrations"){p.nativeDiagnostics()["embedded_script.android.active_object_count"]=="0"&&TimedTaskManager.allTasksAsList.none{it.scriptPath?.startsWith(root.path+File.separator)==true}}
        }finally{bridge.close();p.destroy()}
    }}
    @Test fun rawAndroidAndTaskRoutesEnforceSeparateGrants()=fixture {root->
        val android=provider(root,listOf("android.objects"));val tasks=provider(root,listOf("tasks"))
        try{
            assertTrue(call(android,"android","getThemeColor").ok);assertFalse(call(android,"tasks","queryTimedTasks").ok)
            assertTrue(call(tasks,"tasks","queryTimedTasks").ok);assertFalse(call(tasks,"android","createPaint",JSONArray().put(JSONObject())).ok)
        }finally{android.destroy();tasks.destroy()}
    }
    @Test fun localAndExternalIntentTasksRespectFiltersPayloadsAndSharedActionRemoval()=fixture {root->harness.withFixture {
        val p=provider(root);val action=context.packageName+".OWNED_"+UUID.randomUUID().toString().replace('-','_')
        val output=File(root,"events.txt")
        fun create(name:String,local:Boolean):Long {
            val file=File(root,"$name.js");file.writeText("files.append(${JSONObject.quote(output.path)},${JSONObject.quote(name+"\n")});")
            val options=JSONObject().put("path",file.path).put("action",action).put("local",local)
            if(!local)options.put("category","owned.category").put("dataType","text/plain")
            return JSONObject(ok(call(p,"tasks","addIntentTask",JSONArray().put(options)))).getLong("id")
        }
        fun rows()=if(output.exists())output.readLines().filter{it.isNotBlank()}else emptyList()
        fun sendExternal(type:String)=context.sendBroadcast(Intent(action).setPackage(context.packageName).addCategory("owned.category").setType(type).putExtra("marker","external-payload"))
        try{
            val localOne=create("local-one",true);create("local-two",true);val external=create("external",false)
            sendExternal("image/png");Thread.sleep(350);assertTrue(rows().isEmpty())
            sendExternal("text/plain");harness.await("MIME-filtered external task"){rows()==listOf("external")}
            val last=JSONObject(ok(call(p,"tasks","getIntentTask",JSONArray().put(external)))).getJSONObject("lastIntent")
            assertEquals("external-payload",last.getJSONObject("extras").getString("marker"))
            val localPayload=JSONObject().put("action",action).put("extras",JSONObject().put("marker","local-payload"))
            ok(call(p,"tasks","sendLocalBroadcastSync",JSONArray().put(localPayload)))
            harness.await("Local tasks"){rows().size==3};assertEquals(setOf("external","local-one","local-two"),rows().toSet())
            assertEquals("true",ok(call(p,"tasks","removeIntentTask",JSONArray().put(localOne))))
            Thread.sleep(350);assertEquals("No broadcast replay after receiver refresh",3,rows().size)
            ok(call(p,"tasks","sendLocalBroadcastSync",JSONArray().put(localPayload)));harness.await("Remaining local task"){rows().size==4};assertEquals("local-two",rows().last())
            val changed=action+"_CHANGED";ok(call(p,"tasks","updateTask",JSONArray().put(JSONObject().put("id",external).put("kind","intent").put("action",changed))))
            val cleared=JSONObject(ok(call(p,"tasks","updateTask",JSONArray().put(JSONObject().put("id",external).put("kind","intent").put("category",JSONObject.NULL).put("dataType",JSONObject.NULL)))))
            assertTrue(cleared.isNull("category"));assertTrue(cleared.isNull("dataType"))
            ok(call(p,"tasks","updateTask",JSONArray().put(JSONObject().put("id",external).put("kind","intent").put("category","owned.category").put("dataType","text/plain"))))
            sendExternal("text/plain");Thread.sleep(350);assertEquals(4,rows().size)
            context.sendBroadcast(Intent(changed).setPackage(context.packageName).addCategory("owned.category").setType("text/plain"));harness.await("Updated action task"){rows().size==5}
        }finally{p.destroy()}
    }}
}
