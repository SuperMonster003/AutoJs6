package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.autojs.autojs.timing.TimedTaskManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RuntimeAndroidTasksDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val caps=listOf("android.objects","tasks","ui","network")
    private fun grants(action:()->Unit) {
        val keys=listOf(Grants.ANDROID_OBJECTS,Grants.TASKS_CONTROL,Grants.UI_LAYOUT)
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val old=keys.associateWith{if(prefs.contains(it))prefs.getBoolean(it,false)else null}
        prefs.edit().apply{keys.forEach{putBoolean(it,true)}}.commit()
        try{action()}finally{prefs.edit().apply{old.forEach{(k,v)->if(v==null)remove(k)else putBoolean(k,v)}}.commit()}
    }
    private fun fixture(action:(File)->Unit)=grants {harness.withFixture(setup={it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}) {
        val root=File(context.cacheDir,"android-tasks-runtime-${UUID.randomUUID()}").canonicalFile.apply{mkdirs()}
        try{action(root)}finally{
            TimedTaskManager.allTasksAsList.filter{it.scriptPath?.startsWith(root.path+File.separator)==true}.forEach(TimedTaskManager::removeTaskSync)
            TimedTaskManager.allIntentTasksAsList.filter{it.scriptPath?.startsWith(root.path+File.separator)==true}.forEach(TimedTaskManager::removeTaskSync)
            check(TimedTaskManager.allTasksAsList.none{it.scriptPath?.startsWith(root.path+File.separator)==true})
            check(TimedTaskManager.allIntentTasksAsList.none{it.scriptPath?.startsWith(root.path+File.separator)==true})
            check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()
        }
    }}
    private fun writeProject(root:File,kind:String) {
        File(root,if(kind=="bun")"main.bun.js" else "main.cjs").writeText("\"${if(kind=="bun")"bun" else "nodejs"}\";")
        File(root,"project.json").writeText(JSONObject().put("name","Owned Android Tasks").put("main",if(kind=="bun")"main.bun.js" else "main.cjs")
            .put("projectType",if(kind=="bun")"bun" else "node").put("node",JSONObject().put("permissions",JSONArray(caps))).toString())
    }
    private fun script(mode:String,program:String):String {
        if(mode=="bun")return "const android=host.android,colors=host.colors,tasks=host.tasks,ui=host.ui;$program"
        if(mode=="node")return "const android=require('autojs6:android'),colors=require('colors'),tasks=require('tasks'),ui=require('ui');$program"
        val child="const {parentPort}=require('node:worker_threads'),android=require('android'),colors=require('autojs6:colors'),tasks=require('autojs6:tasks'),ui=require('ui');const eq=(a,b,l)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(l);};(async()=>{$program})().then(result=>parentPort.postMessage(result)).catch(e=>{throw e;});"
        return "const {Worker}=require('node:worker_threads');const worker=new Worker(${JSONObject.quote(child)},{eval:true});try{return await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});}finally{await worker.terminate();}"
    }
    private fun exercise(mode:String)=fixture {root->
        val owned=File(root,"owned.txt").apply{writeText("owned")};val child=File(root,"child.cjs").apply{writeText("'nodejs';")}
        val program="const ownedFile=${JSONObject.quote(owned.path)},child=${JSONObject.quote(child.path)};const helper=await(async()=>{${RuntimeAndroidTasksPrograms.helpers()}})();const calendar=await(async()=>{${RuntimeAndroidTasksPrograms.crud()}})();return {helper,calendar};"
        val result=harness.execute(if(mode=="bun")"bun" else "node",script(mode,program),capabilities=caps,workingDirectory=root.path)
        assertEquals(6,result.getJSONObject("calendar").getInt("calendarModes"));File(context.getExternalFilesDir(null),"runtime-android-tasks-$mode.json").writeText(result.toString(2))
    }
    @Test fun bunAndroidObjectsAndCalendarCrud()=exercise("bun")
    @Test fun nodeAndroidObjectsAndCalendarCrud()=exercise("node")
    @Test fun workerAndroidObjectsAndCalendarCrud()=exercise("worker")
    @Test fun cjsEsmNestedWorkersReleaseObjectsAndKeepConfirmedTasks()=fixture {root->
        val child=File(root,"child.cjs").apply{writeText("'nodejs';")}
        val result=harness.execute("node","""
            const {Worker}=require('node:worker_threads'),android=require('android'),tasks=require('tasks'),bridge=require('autojs6:bridge');
            const parent=await android.createPaint({color:-65536});const ids=[];
            const body="const paint=await android.createPaint();const task=await tasks.addDailyTask({path:"+JSON.stringify(${JSONObject.quote(child.path)})+",time:'23:59:59'});parentPort.postMessage({paint:paint.id,task:task.id});setInterval(()=>{},1000);";
            const cjs="const {parentPort}=require('node:worker_threads'),android=require('android'),tasks=require('tasks');(async()=>{"+body+"})().catch(e=>{throw e;});";
            const esm="import {parentPort} from 'node:worker_threads';import android from 'autojs6:android';import tasks from 'autojs6:tasks';"+body;
            const nested="const {Worker,parentPort}=require('node:worker_threads');const w=new Worker("+JSON.stringify(cjs)+",{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});";
            try{for(const [entry,opts]of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
                const w=new Worker(entry,opts);const value=await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});ids.push(value.task);await w.terminate();await pause(300);
                await rejects(()=>bridge.callAutoJs('android','getPaintInfo',[value.paint],{permissions:['android.objects']}));eq((await parent.getInfo()).color,-65536,'parent paint');
                if(!await tasks.getTimedTask(value.task))throw Error('Confirmed calendar task must persist');
            }}finally{await parent.close();for(const id of ids)await tasks.removeTimedTask(id);}
            return {workerModes:3,confirmedTasksPersisted:true};
        """.trimIndent(),capabilities=caps,workingDirectory=root.path)
        assertEquals(3,result.getInt("workerModes"))
    }
    private fun fires(kind:String)=fixture {root->RuntimeWebFixtureServer().use {server->
        writeProject(root,kind)
        val completed=java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
        val service=org.autojs.autojs.AutoJs.instance.scriptEngineService
        val listener=object:org.autojs.autojs.execution.ScriptExecutionListener {
            private fun record(execution:org.autojs.autojs.execution.ScriptExecution,error:Throwable?) {
                val source=execution.source as? org.autojs.autojs.script.JavaScriptFileSource?:return
                if(source.file.canonicalPath.startsWith(root.path+File.separator))completed.add(JSONObject().put("file",source.file.name).put("success",error==null).put("error",error?.toString()?:JSONObject.NULL))
            }
            override fun onStart(execution:org.autojs.autojs.execution.ScriptExecution) {}
            override fun onSuccess(execution:org.autojs.autojs.execution.ScriptExecution,result:Any?)=record(execution,null)
            override fun onException(execution:org.autojs.autojs.execution.ScriptExecution,error:Throwable)=record(execution,error)
        }
        service.registerGlobalScriptExecutionListener(listener)
        try {
        val action=context.packageName+".SCHEDULE_"+UUID.randomUUID().toString().replace('-','_')
        val suffix=if(kind=="bun")"bun.js" else "cjs"
        for(trigger in listOf("daily","weekly","disposable","countdown","zero","intent")) {
            val intentCode=if(trigger=="intent")if(kind=="bun")"const {createHostClient}=await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);const tasks=(await createHostClient()).tasks;const received=(await tasks.queryIntentTasks({action:${JSONObject.quote(action)}}))[0].lastIntent;" else "const tasks=require('tasks');const received=(await tasks.queryIntentTasks({action:${JSONObject.quote(action)}}))[0].lastIntent;" else "const received=null;"
            File(root,"$trigger.$suffix").writeText("\"${if(kind=="bun")"bun" else "nodejs"}\";(async()=>{$intentCode await fetch(${JSONObject.quote(server.url+"/upload")},{method:'POST',body:JSON.stringify({trigger:'$trigger',runtime:process.versions.bun?'bun':'node',received})});})().catch(e=>{console.error(e.stack);process.exitCode=1;});")
        }
        val setup="""
            const tasks=${if(kind=="bun")"host.tasks" else "require('tasks')"},base=Date.now(),path=name=>${JSONObject.quote(root.path+"/")}+name+'.$suffix';
            const ids=[];ids.push((await tasks.addDailyTask({path:path('daily'),time:base+6000,endMode:'count',times:1})).id);
            ids.push((await tasks.addWeeklyTask({path:path('weekly'),time:base+7000,daysOfWeek:[0,1,2,3,4,5,6],endMode:'count',times:1})).id);
            ids.push((await tasks.addDisposableTask({path:path('disposable'),date:base+8000})).id);
            ids.push((await tasks.addCountdownTask({path:path('countdown'),delayMs:5000})).id);
            const intent=await tasks.addIntentTask({path:path('intent'),action:${JSONObject.quote(action)},category:'owned.category',dataType:'text/plain'});
            ids.push((await tasks.addCountdownTask({path:path('zero'),delayMs:1})).id);await pause(250);
            return {ids,intent:intent.id};
        """.trimIndent()
        val created=harness.execute(kind,setup,capabilities=caps,workingDirectory=root.path)
        harness.await("Actual $kind calendar scripts",80000){
            completed.firstOrNull{!it.getBoolean("success")}?.let{fail(it.toString())}
            server.uploads.size>=5&&completed.size>=5
        }
        assertEquals("Exactly five calendar launches: "+JSONArray(completed),5,server.uploads.size)
        context.sendBroadcast(Intent(action).setPackage(context.packageName).addCategory("owned.category").setType("text/plain").putExtra("marker","owned-schedule"))
        harness.await("Actual $kind IntentTask script",45000){
            completed.firstOrNull{!it.getBoolean("success")}?.let{fail(it.toString())}
            server.uploads.size>=6&&completed.size>=6
        }
        assertEquals("Exactly one IntentTask launch: "+JSONArray(completed),6,server.uploads.size)
        val results=server.uploads.map { JSONObject(it.toString(Charsets.UTF_8)) };assertEquals(setOf("daily","weekly","disposable","countdown","zero","intent"),results.map{it.getString("trigger")}.toSet())
        results.forEach{assertEquals(kind,it.getString("runtime"))};assertEquals("owned-schedule",results.first{it.getString("trigger")=="intent"}.getJSONObject("received").getJSONObject("extras").getString("marker"))
        File(context.getExternalFilesDir(null),"runtime-android-tasks-firing-$kind.json").writeText(JSONObject().put("created",created).put("results",JSONArray(results)).put("completed",JSONArray(completed)).toString(2))
        }finally{service.unregisterGlobalScriptExecutionListener(listener)}
    }}
    @Test fun nodeCalendarAndIntentTasksLaunchActualScriptsAfterCreatorExit()=fires("node")
    @Test fun bunCalendarAndIntentTasksLaunchActualScriptsAfterCreatorExit()=fires("bun")
}
