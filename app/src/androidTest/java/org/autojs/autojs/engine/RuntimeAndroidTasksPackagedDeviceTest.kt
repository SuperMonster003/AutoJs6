package org.autojs.autojs.engine

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.apkbuilder.template.RemoteApkBuildClient
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeAndroidTasksPackagedDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    private fun packageName(mode:String)="org.autojs.autojs6.interop.androidtasks.$mode"
    @Test fun produce() {
        for(mode in listOf("bun","worker")){
            val root=File(context.cacheDir,"android-tasks-package-${UUID.randomUUID()}").canonicalFile.apply{mkdirs()}
            val output=File(context.getExternalFilesDir(null),"runtime-android-tasks-$mode.apk")
            try{
                val bun=mode=="bun";val entry=if(bun)"main.bun.js" else "main.cjs";val suffix=if(bun)"bun.js" else "cjs"
                val config=ProjectConfig().setName("Android tasks $mode Test").setPackageName(packageName(mode)).setVersionName("1.0.0").setVersionCode(1)
                    .setMainScriptFileName(entry).setProjectType(if(bun)"bun" else "node").setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList()).setPermissions(listOf(Manifest.permission.INTERNET))
                config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
                if(bun)config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("android.objects","tasks.control","ui.layout","files.control")).setTimeoutMs(180000L))
                else config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(listOf("android.objects","tasks","ui","network")).setTimeoutMs(180000L))
                if(bun){
                    val sdk=File(context.getExternalFilesDir(null),"runtime-android-tasks-sdk.mjs")
                    assertEquals(InstrumentationRegistry.getArguments().getString("sdkSha256"),RuntimeWebMediaProof.sha256(sdk.readBytes()));sdk.copyTo(File(root,"host-client.mjs"),overwrite=true)
                }
                val imports=if(bun)"const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ui=host.ui,files=host.files;" else "const ui=require('ui'),files=require('files');"
                val scenario="""
                    const ownedFile='$entry',child='crud.$suffix',action=${JSONObject.quote(packageName(mode)+".OWNED")};
                    const before=await tasks.queryTimedTasks({path:'trigger.$suffix',repeatMode:'daily'}),intents=await tasks.queryIntentTasks({action});
                    eq(before.length,round===1?0:1,'persistent calendar');eq(intents.length,round===1?0:1,'persistent IntentTask');
                    if(round===2)eq(intents[0].lastIntent.extras.marker,'round-1','persisted Intent payload');
                    const helper=await(async()=>{${RuntimeAndroidTasksPrograms.helpers()}})();
                    const calendar=await(async()=>{${RuntimeAndroidTasksPrograms.crud()}})();
                    if(!before.length)await tasks.addDailyTask({path:'trigger.$suffix',time:Date.now()+12*3600000});
                    else await tasks.updateTask(before[0],{time:Date.now()+12*3600000});
                    if(!intents.length)await tasks.addIntentTask({path:'trigger.$suffix',action,category:'owned.category',dataType:'text/plain'});
                    await tasks.addCountdownTask({path:'trigger.$suffix',delayMs:8000});
                    return {helper,calendar,resumed:round===2,persistentTaskIds:(await tasks.queryTasks()).map(t=>({id:t.id,kind:t.kind}))};
                """.trimIndent()
                val prelude="const eq=(a,b,l)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(l+': '+JSON.stringify(a)+' != '+JSON.stringify(b));};"
                val worker="const {parentPort,workerData}=require('node:worker_threads'),android=require('autojs6:android'),colors=require('autojs6:colors'),tasks=require('autojs6:tasks'),ui=require('ui'),round=workerData.round;$prelude (async()=>{$scenario})().then(result=>parentPort.postMessage(result)).catch(e=>{throw e;});"
                val run=if(bun)"const run=async round=>{const android=host.android,colors=host.colors,tasks=host.tasks;$prelude $scenario};" else
                    "const {Worker}=require('node:worker_threads');const run=round=>new Promise((resolve,reject)=>{const w=new Worker(${JSONObject.quote(worker)},{eval:true,workerData:{round}});w.on('message',async result=>{await w.terminate();resolve(result);});w.on('error',reject);});"
                File(root,entry).writeText("""
                    "${if(bun)"bun" else "nodejs"}";(async()=>{
                        $imports $run
                        const pause=ms=>new Promise(r=>setTimeout(r,ms));const field=await ui.createView('Input',{text:'Android tasks ready $mode'}),window=await ui.showLayout(field);
                        try{
                            const deadline=Date.now()+90000;while(!String(await field.text).startsWith('RUN ')){if(Date.now()>deadline)throw Error('Checkpoint');await pause(100);}
                            const parts=String(await field.text).split(' '),round=Number(parts[1]),url=parts[2],identity=await ui.context();
                            if(identity.packageName!==${JSONObject.quote(packageName(mode))}||identity.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Packaged identity');
                            await files.write('endpoint.json',JSON.stringify({url,round,mode:'$mode',startedAt:Date.now()}));
                            const result=await run(round);
                            await window.close();await field.dispose();await fetch(url+'/upload',{method:'POST',body:JSON.stringify({type:'main',identity,result,mode:'$mode',round})});
                        }catch(error){await field.set({text:'Android tasks failure '+String(error.stack||error)});await pause(60000);throw error;}
                    })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
                """.trimIndent())
                File(root,"crud.$suffix").writeText("\"${if(bun)"bun" else "nodejs"}\";")
                val childImports=if(bun)"const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),tasks=host.tasks,files=host.files,ui=host.ui;" else "const tasks=require('tasks'),files=require('files'),ui=require('ui');"
                File(root,"trigger.$suffix").writeText("""
                    "${if(bun)"bun" else "nodejs"}";(async()=>{
                        $childImports
                        const saved=JSON.parse(await files.read('endpoint.json')),identity=await ui.context();
                        const task=(await tasks.queryIntentTasks({action:${JSONObject.quote(packageName(mode)+".OWNED")}}))[0];
                        const received=task.lastIntent,trigger=received&&received.receivedAt>=saved.startedAt?'intent':'countdown';
                        await fetch(saved.url+'/upload',{method:'POST',body:JSON.stringify({type:'trigger',trigger,received,identity,round:saved.round,mode:saved.mode,runtime:process.versions.bun?'bun':'node'})});
                    })().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
                """.trimIndent())
                File(root,"project.json").writeText(config.toJson(false))
                val result=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
                assertTrue("APK Builder $mode: ${result.errors}",result.isSuccess)
                File(output.parentFile,output.name+".json").writeText(JSONObject().put("packageName",packageName(mode)).put("runtimeVersionCode",BuildConfig.VERSION_CODE).put("sha256",RuntimeWebMediaProof.sha256(output.readBytes())).toString(2))
            }finally{check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
        }
    }
    @Test fun installedBunAndWorkerTasksPersistAndExecuteAfterRelaunch()=harness.withFixture(setup={it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}) {RuntimeWebFixtureServer().use {server->
        val proofs=JSONArray()
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(command)).use{it.readBytes()}
        for(mode in listOf("bun","worker"))for(round in 1..2){
            val packageName=packageName(mode)
            @Suppress("DEPRECATION") val permissions=context.packageManager.getPackageInfo(packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
            assertTrue(permissions.toString(),permissions.containsAll(setOf(Manifest.permission.WAKE_LOCK,Manifest.permission.RECEIVE_BOOT_COMPLETED,Manifest.permission.SCHEDULE_EXACT_ALARM)))
            shell("cmd appops set $packageName SCHEDULE_EXACT_ALARM allow")
            shell("am force-stop $packageName")
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun rows()=server.uploads.map{JSONObject(it.toString(Charsets.UTF_8))}.filter{it.optString("mode")==mode&&it.optInt("round")==round}
            fun wait(label:String,fn:()->Boolean)=controls.await(label,100000){controls.find{it.text?.toString()?.startsWith("Android tasks failure ")==true}?.let{fail(it.text.toString())};fn()}
            wait("Packaged task entry"){controls.labelled("Android tasks ready $mode")!=null}
            assertTrue(controls.labelled("Android tasks ready $mode")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"RUN $round "+server.url)})==true)
            wait("Packaged main and countdown"){rows().any{it.optString("type")=="main"}&&rows().any{it.optString("trigger")=="countdown"}}
            // The HTTP receipt precedes plugin teardown; do not overlap the next Bun invocation with it.
            Thread.sleep(2000)
            context.sendBroadcast(Intent(packageName+".OWNED").setPackage(packageName).addCategory("owned.category").setType("text/plain").putExtra("marker","round-$round"))
            wait("Packaged IntentTask"){rows().any{it.optString("trigger")=="intent"}}
            Thread.sleep(2000)
            val result=rows();assertEquals(3,result.size)
            for(row in result){assertEquals(packageName,row.getJSONObject("identity").getString("packageName"));assertEquals(BuildConfig.VERSION_CODE,row.getJSONObject("identity").getInt("runtimeVersionCode"));proofs.put(row)}
            val main=result.first{it.getString("type")=="main"}.getJSONObject("result")
            assertEquals(round==2,main.getBoolean("resumed"));assertEquals(6,main.getJSONObject("calendar").getInt("calendarModes"))
            assertTrue(main.getJSONObject("helper").getString("uri").startsWith("content://$packageName."))
            val received=result.first{it.optString("trigger")=="intent"}.getJSONObject("received")
            assertEquals("round-$round",received.getJSONObject("extras").getString("marker"))
            shell("am force-stop $packageName")
        }
        File(context.getExternalFilesDir(null),"runtime-android-tasks-packaged.json").writeText(proofs.toString(2))
    }}
}
