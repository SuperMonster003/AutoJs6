package org.autojs.autojs.engine

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Main thread and real V8 Workers use the same projection, encoder and image provider. */
class NodeScreenRecordingDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()

    @Test fun mainPlaybackAndParallelScreenshotsAcrossRotation()=RecordingAudioDeviceTest().verifyPlayback("node")
    @Test fun mainMicrophoneMixedAndNotificationStop()=RecordingAudioDeviceTest().verifyMicrophone("node")
    @Test fun mainPlaybackRespectsSourceCapturePolicy()=RecordingAudioDeviceTest().verifyPolicy("node")

    @Test fun workerCjsEsmNestedAudioModesAndParallelScreenshots() {
        val folder=File(context.getExternalFilesDir(null),"node-worker-recording-${UUID.randomUUID()}").apply{mkdirs()}
        val rotate="NODE_RECORDING_ROTATE_${UUID.randomUUID()}"
        try {RecordingAudioDeviceTest.Tone(context,true).use {tone -> tone.enabled=true
            withConsent("""
                const {Worker}=require('node:worker_threads');
                const projection=require('autojs6:media_projection');const cap=await projection.requestScreenCapture();
                const folder=${JSONObject.quote(folder.absolutePath)}, movies=[];let mainShots=0;
                const body=async function(audio,path){
                  const images=require('autojs6:images'),screen=require('autojs6:screen_recorder');
                  const cap=await require('autojs6:media_projection').requestScreenCapture();
                  const active=await screen.start(path,{width:192,height:320,frameRate:20,audio});
                  const shots=[];
                  for(let i=0;i<3;i++) {const image=await cap.nextImage();shots.push(images.getSize(image));await images.recycle(image);await new Promise(r=>setTimeout(r,350));}
                  const movie=await screen.stop({id:active.id});return {movie,shots};
                };
                async function run(source){return await new Promise((resolve,reject)=>{let result;const w=new Worker(source,{eval:true});w.on('message',v=>result=v);w.on('error',reject);w.on('exit',n=>n?reject(Error('Worker '+n)):resolve(result));});}
                for(const mode of ['cjs','esm','nested'])for(const audio of ['none','playback','microphone','mixed']){
                  if(movies.length===6){console.log('$rotate');while((await host.coordinates.getInfo()).rotation!==1)await pause(50);await pause(400);}
                  const path=folder+'/'+mode+'-'+audio+'.mp4';
                  const code='('+body.toString()+')('+JSON.stringify(audio)+','+JSON.stringify(path)+').then(v=>require("node:worker_threads").parentPort.postMessage(v))';
                  let running=true;const shots=(async()=>{while(running){const image=await images.captureScreen();await images.recycle(image);mainShots++;await pause(100);}})();
                  try {
                    let result;
                    if(mode==='esm'){
                      const esm='import screen,{start,stop} from "autojs6:screen_recorder";import projection from "autojs6:media_projection";import {createRequire} from "node:module";const require=createRequire(process.cwd()+"/entry.cjs");if(screen.start!==start||typeof stop!=="function"||!projection.requestScreenCapture)throw Error("ESM exports");'+code;
                      result=await new Promise((resolve,reject)=>{let value;const w=new Worker(new URL('data:text/javascript,'+encodeURIComponent(esm)));w.on('message',v=>value=v);w.on('error',reject);w.on('exit',n=>n?reject(Error('ESM Worker '+n)):resolve(value));});
                    }else result=await run(mode==='nested'?'const {Worker,parentPort}=require("node:worker_threads");new Worker('+JSON.stringify(code)+',{eval:true}).on("message",v=>parentPort.postMessage(v));':code);
                    eq(result.shots.length,3,'Worker screenshot count');eq(result.movie.audioSource,audio,'recorded source');
                    eq(result.movie.audioCodec,audio==='none'?null:'aac','recorded codec');
                    const {size,durationMs,width,height}=result.movie;
                    movies.push({movie:{path:path.split('/').pop(),size,durationMs,width,height},shots:[result.shots[2]],mode,audio});
                  }finally{running=false;await shots;}
                }
                // The existing audio-only recorder also resolves in Workers; stop it from the parent.
                const audio=await new Promise((resolve,reject)=>{const w=new Worker('require("autojs6:recorder").start({path:'+JSON.stringify(folder+'/audio-only.m4a')+'}).then(x=>require("node:worker_threads").parentPort.postMessage(x));',{eval:true});w.on('message',resolve);w.on('error',reject);});
                eq(audio.recording,true,'Worker audio-only start');await pause(1200);const audioOnly=await require('recorder').stop();
                await cap.stop();return {movies,mainShots,audioOnly};
            """.trimIndent(),rotate) {result ->
                val movies=result.getJSONArray("movies");assertEquals(12,movies.length());assertTrue(result.getInt("mainShots")>12)
                for(i in 0 until movies.length()) {
                    val entry=movies.getJSONObject(i);val movie=entry.getJSONObject("movie");val file=File(folder,movie.getString("path"))
                    assertTrue(file.length()>0);assertEquals(192,movie.getInt("width"));assertEquals(320,movie.getInt("height"))
                    val shot=entry.getJSONArray("shots").getJSONObject(0)
                    assertEquals(i<6,shot.getInt("height")>shot.getInt("width"))
                    val retriever=MediaMetadataRetriever()
                    try {retriever.setDataSource(file.absolutePath);val frame=retriever.getFrameAtTime(500_000);assertNotNull(frame);frame!!.recycle()}finally{retriever.release()}
                    if(entry.getString("audio")!="none") {
                        val proof=RecordingAudioDeviceTest().decodeAudio(file);assertTrue("Missing tone: $proof",proof.rms>20&&proof.peakTone>10)
                        entry.put("decodedAudio",proof.json())
                    } else {
                        val extractor=MediaExtractor();try{extractor.setDataSource(file.path);assertEquals(1,extractor.trackCount)}finally{extractor.release()}
                    }
                }
                val audio=MediaExtractor();try{audio.setDataSource(File(folder,"audio-only.m4a").path);assertEquals(1,audio.trackCount);assertEquals("audio/mp4a-latm",audio.getTrackFormat(0).getString(MediaFormat.KEY_MIME))}finally{audio.release()}
                evidence("worker-matrix",result)
            }
        }}finally{folder.deleteRecursively()}
    }

    @Test fun workerExitCancellationAndCrossThreadStopProtectNewRecordings() {
        val folder=File(context.getExternalFilesDir(null),"node-recording-exit-${UUID.randomUUID()}").apply{mkdirs()}
        try {withConsent("""
            const {Worker}=require('node:worker_threads');const screen=require('screen_recorder');
            await images.requestScreenCapture();const folder=${JSONObject.quote(folder.absolutePath)};
            function spawn(path,nested=false){const code='require("screen_recorder").start('+JSON.stringify(path)+',{width:192,height:320,audio:"mixed"}).then(x=>require("node:worker_threads").parentPort.postMessage(x));setInterval(()=>{},1000);';
              return new Worker(nested?'const {Worker,parentPort}=require("node:worker_threads");new Worker('+JSON.stringify(code)+',{eval:true}).on("message",x=>parentPort.postMessage(x));setInterval(()=>{},1000);':code,{eval:true});}
            function ready(w){return new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});}
            for(const nested of [false,true]){const w=spawn(folder+'/cancel-'+nested+'.mp4',nested);await ready(w);await w.terminate();
              for(let i=0;i<100&&(await screen.getStatus()).state!=='idle';i++)await pause(50);eq((await screen.getStatus()).state,'idle','Worker exit');}
            const old=spawn(folder+'/saved.mp4');const active=await ready(old);await pause(1200);const saved=await screen.stop({id:active.id});
            const replacement=await screen.start(folder+'/replacement.mp4',{width:192,height:320,audio:'mixed'});await old.terminate();await pause(250);
            eq((await screen.getStatus()).id,replacement.id,'stale Worker cleanup');eq(await screen.cancel({id:active.id}),false,'stale cancel');
            eq(await screen.cancel({id:replacement.id}),true,'replacement cancel');const image=await images.captureScreen();await images.recycle(image);await images.stopScreenCapture();
            return {saved,exitCleaned:true,staleProtected:true};
        """.trimIndent()) {result ->
            assertEquals(setOf("saved.mp4"),folder.list()!!.toSet());assertTrue(File(folder,"saved.mp4").length()>0)
            harness.await("recording threads released"){Thread.getAllStackTraces().keys.none{it.isAlive&&it.name in listOf("ScreenRecording-AAC","ScreenRecording-AVC","ScreenRecording-GL")}}
            harness.await("recording notification released"){context.getSystemService(NotificationManager::class.java).activeNotifications.none{it.notification.channelId=="node_audio_recording"}}
            evidence("worker-exit",result)
        }}finally{folder.deleteRecursively()}
    }

    @Test fun rawBridgeCannotBypassAudioCapabilities() {
        val result=harness.execute("node","""
            const bridge=require('autojs6:bridge');
            const imported=await import('autojs6:screen_recorder');eq(imported.start===require('screen_recorder').start,true,'Main ESM named export');
            eq(typeof imported.default.stop,'function','Main ESM default export');
            for(const audio of ['microphone','playback','mixed']){
              const error=await rejects(()=>bridge.callAutoJs('screen_recorder','start',['/sdcard/denied.mp4',{audio}],{permissions:['screen_capture']}));
              eq(error.code,'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED','raw audio capability');
            }return {denied:true};
        """.trimIndent(),capabilities=listOf("screen_capture"))
        assertTrue(result.getBoolean("denied"))
    }

    private fun withConsent(body:String,rotate:String?=null,verify:(JSONObject)->Unit) = harness.withFixture {
        val pool=Executors.newSingleThreadExecutor()
        try {
            val future=pool.submit<JSONObject>{harness.execute("node",body)}
            harness.await("Node recording consent"){if(future.isDone)fail(future.get().toString());harness.consentButton(true)!=null};harness.clickConsent(true)
            if(rotate!=null){harness.await("Node recording rotation",60_000){if(future.isDone)fail(future.get().toString());AutoJs.instance.globalConsole.snapshotLogEntries().any{it.content.toString().contains(rotate)}};assertTrue(harness.automation.setRotation(1))}
            verify(future.get(90,TimeUnit.SECONDS));harness.awaitNoProjection()
        }finally{pool.shutdownNow()}
    }
    private fun evidence(name:String,result:JSONObject){File(context.getExternalFilesDir(null),"node-recording-$name.json").writeText(result.toString(2))}
}

class NodeScreenRecordingPermissionDeviceTest {
    @Test fun androidAudioDenialKeepsSilentRecordingAndScreenshotsUsable() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        val harness=CaptureWorkerBridgeDeviceTest();val folder=File(context.getExternalFilesDir(null),"node-recording-denied-${UUID.randomUUID()}").apply{mkdirs()}
        try {harness.withFixture {
            val pool=Executors.newSingleThreadExecutor()
            try {
                val future=pool.submit<JSONObject>{harness.execute("node","""
                    const screen=require('screen_recorder');await images.requestScreenCapture();
                    for(const audio of ['microphone','playback','mixed']){const error=await rejects(()=>screen.start(${JSONObject.quote(File(folder,"denied.mp4").path)},{width:192,height:320,audio}));if(!error.message.includes('RECORD_AUDIO'))throw error;}
                    await screen.start(${JSONObject.quote(File(folder,"silent.mp4").path)},{width:192,height:320});
                    const image=await images.captureScreen();await images.recycle(image);await pause(1200);const movie=await screen.stop();await images.stopScreenCapture();return {movie};
                """.trimIndent())}
                harness.await("Node denied recording consent"){if(future.isDone)fail(future.get().toString());harness.consentButton(true)!=null};harness.clickConsent(true)
                assertFalse(future.get(20,TimeUnit.SECONDS).getJSONObject("movie").getBoolean("audio"));assertEquals(setOf("silent.mp4"),folder.list()!!.toSet());harness.awaitNoProjection()
            }finally{pool.shutdownNow()}
        }}finally{folder.deleteRecursively()}
    }
}
