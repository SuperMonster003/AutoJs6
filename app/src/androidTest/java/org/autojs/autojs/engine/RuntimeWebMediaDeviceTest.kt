package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Rect
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class RuntimeWebMediaDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val ui=RuntimeUiComposeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    private val modes=InstrumentationRegistry.getArguments().getString("webMode")?.let(::listOf) ?: listOf("node","node-esm","worker","worker-esm","worker-nested","bun")
    private val evidence=JSONArray()

    @Test fun videosCaptureUploadAndReleaseAcrossRuntimes()=capture("video")
    @Test fun audioCaptureUploadAndReleaseAcrossRuntimes()=capture("audio")

    @Test fun audioCancellationRotationAndBackgroundRelease()=ui.grants { harness.withFixture {
        val before=File(context.cacheDir,"runtime-web-capture").list().orEmpty().toSet()
        val manager=context.getSystemService(android.media.AudioManager::class.java)
        val sessions=manager.activeRecordingConfigurations.map{it.clientAudioSessionId}.toSet()
        RecordingAudioDeviceTest.Tone(context,true).use {tone->tone.enabled=true
        for(mode in listOf("node","worker-nested","bun")){val pool=Executors.newSingleThreadExecutor();try {
            harness.automation.setRotation(0)
            val future=pool.submit<JSONObject>{ui.run(mode,"""
                const web=await ui.createView('WebView'),window=await ui.showLayout(web);
                try {
                  const first=await web.chooseFiles({captureEnabled:true,captureKind:'audio',durationLimitSeconds:15});if(first!==null)throw Error('Cancelled audio returned a file');
                  const second=await web.chooseFiles({captureEnabled:true,captureKind:'audio',durationLimitSeconds:15});if(!second)throw Error('Background-stopped audio was lost');
                  const data=await web.readDocument(second[0],0,1024);if(data.length!==1024)throw Error('Recorded file unreadable');return {cancelled:true,rotated:true,backgroundStopped:true,bytes:data.length};
                } finally {await window.close();await web.dispose();}
            """.trimIndent())}
            controls.await("First audio chooser"){controls.byId("runtime_web_audio_start")?.isEnabled==true}
            controls.byId("runtime_web_audio_start")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            controls.await("First audio starts"){controls.permission(true);controls.byId("runtime_web_audio_stop")?.isEnabled==true}
            SystemClock.sleep(1200);controls.byId("runtime_web_audio_cancel")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            controls.await("Second audio chooser"){if(future.isDone)fail(future.get().toString());controls.byId("runtime_web_audio_start")?.isEnabled==true}
            controls.byId("runtime_web_audio_start")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            controls.await("Second audio starts"){controls.byId("runtime_web_audio_stop")?.isEnabled==true}
            SystemClock.sleep(1200)
            harness.automation.setRotation(1)
            controls.await("Recorder after rotation"){controls.byId("runtime_web_audio_stop")?.isEnabled==true}
            var taskId=-1
            instrumentation.runOnMainSync {taskId=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<RuntimeWebActionActivity>().single().taskId}
            harness.automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            harness.await("Microphone released in background"){manager.activeRecordingConfigurations.map{it.clientAudioSessionId}.toSet()==sessions}
            context.getSystemService(android.app.ActivityManager::class.java).appTasks.single{it.taskInfo?.taskId==taskId}.moveToFront()
            controls.await("Stopped recording retained"){controls.byId("runtime_web_audio_use")?.isEnabled==true}
            assertFalse(controls.byId("runtime_web_audio_stop")!!.isEnabled)
            controls.byId("runtime_web_audio_use")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            evidence.put(JSONObject().put("mode",mode).put("result",future.get(30,TimeUnit.SECONDS)))
            harness.await("Cancelled and accepted capture cleanup"){File(context.cacheDir,"runtime-web-capture").list().orEmpty().toSet()==before}
        }finally{pool.shutdownNow()}}};save("audio-lifecycle")
    } }

    @Test fun audioLimitsFinishWithoutAStopCommand()=ui.grants { harness.withFixture {
        RecordingAudioDeviceTest.Tone(context,true).use {tone->tone.enabled=true
        for((name,limit) in listOf("duration" to "durationLimitSeconds:1","size" to "sizeLimitBytes:32768")){
            val pool=Executors.newSingleThreadExecutor();try {
                val future=pool.submit<JSONObject>{ui.run("node","""
                    const web=await ui.createView('WebView'),window=await ui.showLayout(web);
                    try {const result=await web.chooseFiles({captureEnabled:true,captureKind:'audio',$limit});if(!result)throw Error('Limited capture cancelled');const bytes=await web.readDocument(result[0]);return {automaticStop:true,readBytes:bytes.length};}
                    finally {await window.close();await web.dispose();}
                """.trimIndent())}
                controls.await("Limited recorder"){controls.byId("runtime_web_audio_start")?.isEnabled==true}
                controls.byId("runtime_web_audio_start")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                controls.await("Automatic $name stop",15000){controls.permission(true);if(future.isDone)fail(future.get().toString());controls.byId("runtime_web_audio_use")?.isEnabled==true}
                assertFalse(controls.byId("runtime_web_audio_stop")!!.isEnabled)
                controls.byId("runtime_web_audio_use")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                evidence.put(JSONObject().put("limit",name).put("result",future.get(20,TimeUnit.SECONDS)))
            }finally{pool.shutdownNow()}
        }};save("audio-limits")
    } }

    @Test fun workerExitCancelsActiveAudioAndVideo()=ui.grants { harness.withFixture {
        val before=File(context.cacheDir,"runtime-web-capture").list().orEmpty().toSet()
        for(kind in listOf("audio","video"))RuntimeWebFixtureServer().use {server->
            val child="""
                const ui=require('ui'),{parentPort,workerData}=require('node:worker_threads'),web=ui.view(workerData);
                web.chooseFiles({captureEnabled:true,captureKind:'$kind',videoQuality:0,durationLimitSeconds:15}).catch(()=>{});
                parentPort.postMessage('ready');setInterval(()=>{},1000);
            """.trimIndent()
            val nested="const {Worker,parentPort,workerData}=require('node:worker_threads');new Worker(${JSONObject.quote(child)},{eval:true,workerData}).on('message',v=>parentPort.postMessage(v)).on('error',e=>{throw e;});"
            val pool=Executors.newSingleThreadExecutor();try {
                val future=pool.submit<JSONObject>{ui.run("node","""
                    const {Worker}=require('node:worker_threads'),web=await ui.createView('WebView'),window=await ui.showLayout(web);let worker;
                    try {worker=new Worker(${JSONObject.quote(nested)},{eval:true,workerData:web});await new Promise((r,j)=>{worker.once('message',r);worker.once('error',j)});
                      const end=Date.now()+30000;while(!await(await fetch('${server.url}/worker-stop')).json()){if(Date.now()>end)throw Error('Capture did not start');await new Promise(r=>setTimeout(r,80));}
                      await worker.terminate();worker=null;await web.set({contentDescription:'Worker-ended-$kind'});
                      const released=Date.now()+15000;while(await(await fetch('${server.url}/worker-stop')).json()){if(Date.now()>released)throw Error('Capture not released after Worker exit');await new Promise(r=>setTimeout(r,80));}
                      return {cancelled:true,kind:'$kind'};
                    } finally {if(worker)await worker.terminate();await window.close();await web.dispose();}
                """.trimIndent())}
                if(kind=="audio"){
                    controls.await("Worker audio chooser"){controls.byId("runtime_web_audio_start")?.isEnabled==true}
                    controls.byId("runtime_web_audio_start")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    controls.await("Worker microphone active"){controls.permission(true);controls.byId("runtime_web_audio_stop")?.isEnabled==true}
                }else{
                    controls.startVideo {future.isDone}
                }
                SystemClock.sleep(1200);server.stopWorker=true
                controls.await("Worker exited while parent View stays open"){if(future.isDone)fail(future.get().toString());controls.labelled("Worker-ended-$kind")!=null}
                controls.await("Capture dismissed after Worker exit"){controls.byId("runtime_web_audio_cancel")==null&&controls.shutter()==null}
                harness.await("Worker capture files removed"){File(context.cacheDir,"runtime-web-capture").list().orEmpty().toSet()==before}
                server.stopWorker=false
                evidence.put(JSONObject().put("kind",kind).put("result",future.get(30,TimeUnit.SECONDS)))
            }finally{pool.shutdownNow()}
        };save("worker-exit")
    } }

    @Test fun chooserTimeoutClosesAudioUi()=ui.grants {harness.withFixture {
        val before=File(context.cacheDir,"runtime-web-capture").list().orEmpty().toSet()
        for(mode in listOf("node","bun")){
            val result=ui.run(mode,"""
                const web=await ui.createView('WebView'),window=await ui.showLayout(web);
                try {const started=Date.now(),file=await web.chooseFiles({captureEnabled:true,captureKind:'audio',timeoutMs:300});if(file!==null)throw Error('Expired chooser returned a file');return {cancelled:true,elapsedMs:Date.now()-started};}
                finally {await window.close();await web.dispose();}
            """.trimIndent())
            assertTrue(result.getLong("elapsedMs")>=250)
            harness.await("Expired audio UI closed"){controls.byId("runtime_web_audio_cancel")==null&&File(context.cacheDir,"runtime-web-capture").list().orEmpty().toSet()==before}
            evidence.put(JSONObject().put("mode",mode).put("result",result))
        };save("timeout")
    }}

    private fun capture(kind:String)=ui.grants { harness.withFixture { RuntimeWebFixtureServer().use { server ->
        val before=File(context.cacheDir,"runtime-web-capture").list().orEmpty().toSet()
        val tone=if(kind=="audio")RecordingAudioDeviceTest.Tone(context,true).apply{enabled=true}else null
        try {for(mode in modes){val executor=Executors.newSingleThreadExecutor();try {
            val future=executor.submit<JSONObject>{ui.run(mode,"""
                const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web);let selected,event;
                const errors=[];web.on('webCallbackError',e=>errors.push(e));
                try {
                  ${if(mode!="bun") "await web.setWebChromeClient({async onShowFileChooser(e){event=e;selected=await this.chooseFiles({...e,durationLimitSeconds:3,videoQuality:0,timeoutMs:180000});return selected;}},{callbackTimeoutMs:180000});" else ""}
                  await web.loadUrl('${server.url}/capture-$kind');
                  let end=Date.now()+15000;while(await web.evaluateJavascript('document.readyState==="complete"&&!!document.getElementById("capture")')!==true){if(Date.now()>end)throw Error('Media page');await new Promise(r=>setTimeout(r,50));}
                  await web.evaluateJavascript('document.getElementById("capture").textContent="Capture-$mode-$kind"');
                  end=Date.now()+60000;while(await web.evaluateJavascript('window.uploaded')!==true){const error=await web.evaluateJavascript('window.captureError');if(error||Date.now()>end)throw Error('$mode $kind '+JSON.stringify({error,errors,event}));await new Promise(r=>setTimeout(r,60));}
                  const info=await web.evaluateJavascript('window.mediaInfo');if(!Number.isFinite(info.duration)||info.duration<0.5||info.size<1000)throw Error('Empty captured media '+JSON.stringify(info));
                  const result={...info,defaultPicker:${mode=="bun"}};
                  if(selected){if(!event.captureEnabled||!event.acceptTypes.some(v=>v.startsWith('$kind/')))throw Error('Native capture request lost');result.head=[...await web.readDocument(selected[0],0,16)];result.tail=[...await web.readDocument(selected[0],info.size-16,16)];}
                  return result;
                } finally {await window.close();await web.dispose();}
            """.trimIndent())}
            controls.click("Capture-$mode-$kind",future)
            if(kind=="video")controls.video {future.isDone} else controls.audio {future.isDone}
            val value=future.get(80,TimeUnit.SECONDS)
            assertEquals(1,server.uploads.size)
            val bytes=RuntimeWebMediaProof.upload(server.uploads.single(),value.getInt("size"))
            assertEquals(value.getString("sha256"),RuntimeWebMediaProof.sha256(bytes))
            if(value.has("head")){assertEquals(value.getJSONArray("head").toString(),JSONArray(bytes.take(16).map{it.toInt() and 255}).toString());assertEquals(value.getJSONArray("tail").toString(),JSONArray(bytes.takeLast(16).map{it.toInt() and 255}).toString())}
            value.put("decoded",RuntimeWebMediaProof.inspect(context,kind,bytes));bytes.fill(0);server.uploads.clear()
            evidence.put(JSONObject().put("mode",mode).put("kind",kind).put("result",value));save(kind)
            harness.await("Temporary media files released"){File(context.cacheDir,"runtime-web-capture").list().orEmpty().toSet()==before}
        }finally{executor.shutdownNow()}}}finally{tone?.close()}
    } } }

    @Test fun mediaOptionsPreserveNativeCaptureAndSaveDefaults() {
        for(kind in listOf("image","video","audio")){
            assertEquals(kind,RuntimeWebFileChooser.captureKind(JSONObject().put("captureEnabled",true).put("acceptTypes",JSONArray(listOf("$kind/*")))))
            assertEquals(kind,RuntimeWebFileChooser.captureKind(JSONObject().put("captureEnabled",true).put("captureKind",kind).put("acceptTypes",JSONArray(listOf("*/*")))))
        }
        assertNull(RuntimeWebFileChooser.captureKind(JSONObject().put("captureEnabled",true).put("acceptTypes",JSONArray(listOf("image/*","video/*")))))
        assertNull(RuntimeWebFileChooser.captureKind(JSONObject().put("captureEnabled",true).put("captureKind","video").put("mode","save")))
        val options=JSONObject().put("videoQuality",0).put("durationLimitSeconds",4).put("sizeLimitBytes",1048576)
        val uri=android.net.Uri.parse("content://fixture/capture.mp4");val intent=RuntimeWebFileChooser.cameraIntent("video",options,uri)
        assertEquals(MediaStore.ACTION_VIDEO_CAPTURE,intent.action);assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
        assertEquals(uri,intent.getParcelableExtra<android.net.Uri>(MediaStore.EXTRA_OUTPUT))
        assertEquals(4,intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT,-1));assertEquals(0,intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY,-1));assertEquals(1048576L,intent.getLongExtra(MediaStore.EXTRA_SIZE_LIMIT,-1))
        assertEquals("download",RuntimeWebFileChooser.saveFileName(JSONObject().put("filenameHint",JSONObject.NULL)))
        for(value in listOf(0,-1,1.5)){try{RuntimeWebFileChooser.durationLimit(JSONObject().put("durationLimitSeconds",value));fail("Invalid duration accepted")}catch(_:IllegalArgumentException){}}
        try{RuntimeWebFileChooser.sizeLimit(JSONObject().put("sizeLimitBytes",0));fail("Invalid size accepted")}catch(_:IllegalArgumentException){}
        try{RuntimeWebFileChooser.captureKind(JSONObject().put("captureEnabled",true).put("captureKind","screen"));fail("Invalid capture kind accepted")}catch(_:IllegalArgumentException){}
    }

    private fun save(name:String){File(context.getExternalFilesDir(null),"runtime-web-media-$name.json").writeText(evidence.toString(2))}
}

/** Invoked separately with RECORD_AUDIO revoked; Android revocation kills the target process. */
class RuntimeWebMediaPermissionDeviceTest {
    @Test fun microphoneDenialCancelsCaptureForNodeAndBun() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
        val harness=CaptureWorkerBridgeDeviceTest();val ui=RuntimeUiComposeDeviceTest();val controls=RuntimeWebMediaControls(harness)
        val before=File(context.cacheDir,"runtime-web-capture").list().orEmpty().toSet()
        ui.grants {harness.withFixture {for(mode in listOf("node","bun")){
            val pool=Executors.newSingleThreadExecutor();try {
                val future=pool.submit<JSONObject>{ui.run(mode,"""
                    const web=await ui.createView('WebView'),window=await ui.showLayout(web);
                    try {const result=await web.chooseFiles({captureEnabled:true,captureKind:'audio'});if(result!==null)throw Error('Microphone denial returned media');return {denied:true};}
                    finally {await window.close();await web.dispose();}
                """.trimIndent())}
                controls.await("Denied audio chooser"){controls.byId("runtime_web_audio_start")?.isEnabled==true}
                controls.byId("runtime_web_audio_start")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                controls.await("Deny microphone"){future.isDone||controls.permission(false)}
                assertTrue(future.get(30,TimeUnit.SECONDS).getBoolean("denied"))
                harness.await("No denied capture file"){File(context.cacheDir,"runtime-web-capture").list().orEmpty().toSet()==before}
            }finally{pool.shutdownNow()}
        }}}
    }
}

/** Uses real native controls and records no camera frames or microphone samples on the host PC. */
internal class RuntimeWebMediaControls(private val harness:CaptureWorkerBridgeDeviceTest) {
    fun find(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
        fun walk(node:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(node==null)return null;if(node.isVisibleToUser&&predicate(node))return node;for(i in 0 until node.childCount)walk(node.getChild(i))?.let{return it};return null}
        return walk(harness.automation.rootInActiveWindow)?:harness.automation.windows.firstNotNullOfOrNull{walk(it.root)}
    }
    fun byId(name:String)=find{it.viewIdResourceName?.endsWith(":id/$name")==true}
    fun labelled(text:String)=find{it.text?.toString()==text||it.contentDescription?.toString()==text}
    fun tap(node:AccessibilityNodeInfo){val box=Rect();node.getBoundsInScreen(box);android.os.ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("input touchscreen tap ${box.centerX()} ${box.centerY()}")).use{it.readBytes()}}
    fun click(text:String,future:Future<*>?=null) {
        await("Media action $text"){if(future?.isDone==true)fail(future.get().toString());var node=labelled(text)?:return@await false;while(!node.isClickable&&node.parent!=null)node=node.parent;node.performAction(AccessibilityNodeInfo.ACTION_CLICK)}
    }
    fun permission(allow:Boolean):Boolean {
        if(find{val text=it.text?.toString().orEmpty();listOf("AutoJs6","Interop","Web media","Web streams","Web display","Camera","相机","相機").any(text::contains)}==null)return false
        val node=find{val id=it.viewIdResourceName.orEmpty();val text=it.text?.toString();if(allow)id.endsWith(":id/permission_allow_foreground_only_button")||text in listOf("仅在使用中允许","While using the app","WHILE USING THE APP","Allow only while using the app") else id.endsWith(":id/permission_deny_button")||id.endsWith(":id/permission_deny_and_dont_ask_again_button")||text in listOf("拒绝","拒绝且不再询问","Don't allow","DENY","Deny","Deny and don't ask again")}?:return false
        var target=node;while(!target.isClickable&&target.parent!=null)target=target.parent
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    fun audio(done:()->Boolean) {
        await("Audio capture controls"){byId("runtime_web_audio_start")?.isEnabled==true}
        byId("runtime_web_audio_start")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        await("Microphone recording"){permission(true);done()||byId("runtime_web_audio_stop")?.isEnabled==true}
        SystemClock.sleep(1800)
        byId("runtime_web_audio_stop")?.takeIf{it.isEnabled}?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        await("Use audio recording"){if(done())return@await true;byId("runtime_web_audio_use")?.takeIf{it.isEnabled}?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true}
        await("Audio capture returned",60000,done)
    }
    fun shutter()=find{val id=it.viewIdResourceName.orEmpty();it.isEnabled&&(id.endsWith(":id/shutter_button")||id.endsWith(":id/v9_shutter_button_internal")||id.endsWith(":id/video_shutter_button"))}
    fun startVideo(done:()->Boolean):JSONArray {
        await("System video camera"){permission(true);shutter()!=null}
        val initial=surface()
        harness.automation.waitForIdle(500,5000);SystemClock.sleep(800)
        var lastStart=0L
        await("Start video recording",15000){
            if(done()||accept()!=null)return@await true
            val recording=find{val id=it.viewIdResourceName.orEmpty();val text=(it.contentDescription?:it.text)?.toString();id.contains("recording_time")||id.endsWith(":id/v9_video_pause")||text in listOf("Stop recording","停止录像","停止录制")}
            if(lastStart>0&&recording!=null)return@await true
            if(SystemClock.uptimeMillis()-lastStart>1500){shutter()?.let(::tap);lastStart=SystemClock.uptimeMillis()};false
        }
        return initial
    }
    fun video(done:()->Boolean) {
        val initial=startVideo(done)
        SystemClock.sleep(1800)
        if(accept()==null)shutter()?.let(::tap)
        var last=0L
        try{await("Accept captured video",60000){if(done())return@await true;val node=accept();if(node?.isEnabled==true&&SystemClock.uptimeMillis()-last>800){tap(node);last=SystemClock.uptimeMillis()};false}}
        catch(error:Throwable){throw AssertionError("Video initial UI: $initial",error)}
    }
    private fun accept()=find{val id=it.viewIdResourceName.orEmpty();id.endsWith(":id/intent_done_apply")||id.endsWith(":id/done_button")||id.endsWith(":id/v9_btn_done")}
    fun await(label:String,timeout:Long=45000,test:()->Boolean){try{harness.await(label,timeout,test)}catch(error:Throwable){throw AssertionError("$label; UI: ${surface()}",error)}}
    private fun surface():JSONArray {
        val result=JSONArray()
        fun walk(node:AccessibilityNodeInfo?){if(node==null||result.length()>70)return;if(node.isVisibleToUser&&(node.text!=null||node.contentDescription!=null))result.put(JSONObject().put("id",node.viewIdResourceName).put("text",node.text?.toString()).put("description",node.contentDescription?.toString()));for(i in 0 until node.childCount)walk(node.getChild(i))}
        for(window in harness.automation.windows){val root=window.root?:continue;if(root.packageName?.toString()!="com.android.systemui")walk(root)};return result
    }
}

internal object RuntimeWebMediaProof {
    fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    fun upload(body:ByteArray,size:Int):ByteArray {
        val start=(0..body.size-4).first{body[it]==13.toByte()&&body[it+1]==10.toByte()&&body[it+2]==13.toByte()&&body[it+3]==10.toByte()}+4
        require(start+size<body.size);return body.copyOfRange(start,start+size)
    }
    fun inspect(context:Context,kind:String,bytes:ByteArray):JSONObject {
        val file=File.createTempFile("web-media-proof-",".mp4",context.cacheDir)
        try {
            file.writeBytes(bytes)
            val extractor=MediaExtractor();val tracks=JSONArray()
            try{extractor.setDataSource(file.path);for(i in 0 until extractor.trackCount){val format=extractor.getTrackFormat(i);tracks.put(format.getString(MediaFormat.KEY_MIME))}}finally{extractor.release()}
            val proof=JSONObject().put("tracks",tracks)
            if(kind=="audio"){
                val audio=RecordingAudioDeviceTest().decodeAudio(file,1)
                assertTrue("Real microphone test tone missing: $audio",audio.rms>20&&audio.peakTone>10)
                proof.put("audio",audio.json())
            }else{
                val retriever=MediaMetadataRetriever()
                try{retriever.setDataSource(file.path);val frame=requireNotNull(retriever.getFrameAtTime(500000));try{assertTrue(frame.width>0&&frame.height>0);proof.put("decodedWidth",frame.width).put("decodedHeight",frame.height)}finally{frame.recycle()}}finally{retriever.release()}
            }
            return proof
        }finally{file.delete()}
    }
}
