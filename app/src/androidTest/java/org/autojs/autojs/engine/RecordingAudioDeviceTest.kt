package org.autojs.autojs.engine

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioTimestamp
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Actual AAC decoding and owned acoustic/playback fixtures; recorded files are deleted after checks. */
class RecordingAudioDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val global get()=AutoJs.instance.globalConsole
    @Volatile private var pulse=false

    @Test fun playbackAudioAndScreenshotsShareOneLiveRecordingAcrossRotation() = verifyPlayback("bun")

    internal fun verifyPlayback(kind:String)=harness.withGrants {
        val folder=File(context.getExternalFilesDir(null),"audio-recording-${UUID.randomUUID()}").apply{mkdirs()}
        try {Tone(context,true).use {tone ->
            harness.withFixture(setup={activity -> activity.setContentView(object:View(activity) {
                val paint=Paint()
                override fun onDraw(canvas:Canvas) {
                    val w=width.toFloat();val h=height.toFloat()
                    for((x,y,color) in listOf(Triple(0f,0f,Color.RED),Triple(w/2,0f,Color.GREEN),Triple(0f,h/2,Color.BLUE),Triple(w/2,h/2,Color.YELLOW))) {
                        paint.color=color;canvas.drawRect(x,y,x+w/2,y+h/2,paint)
                    }
                    paint.color=if(pulse)Color.MAGENTA else Color.GRAY
                    canvas.drawRect(w*.4f,h*.4f,w*.6f,h*.6f,paint);postInvalidateDelayed(30)
                }
            })}) {
                val pool=Executors.newSingleThreadExecutor();val ready="AUDIO_READY_${UUID.randomUUID()}";val rotate="AUDIO_ROTATE_${UUID.randomUUID()}"
                try {
                    val future=pool.submit<JSONObject>{harness.execute(kind,"""
                        await images.requestScreenCapture({timeoutMs:30000});await pause(500);
                        const active=await host.screenRecorder.start(${JSONObject.quote(File(folder,"playback.mp4").absolutePath)},
                          {width:192,height:320,frameRate:20,bitRate:1000000,audio:'playback'});
                        eq(active.audio,true,'active audio');eq(active.audioSource,'playback','source');console.log('$ready');
                        const shots=[];
                        async function shot(){const image=await images.captureScreen();const size=images.getSize(image);
                          const colors=[];for(const [x,y] of [[.25,.25],[.75,.25],[.25,.75],[.75,.75]])colors.push(await images.pixel(image,Math.floor(size.width*x),Math.floor(size.height*y)));
                          eq(colors,[${Color.RED},${Color.GREEN},${Color.BLUE},${Color.YELLOW}],'snapshot orientation/channels');
                          const center=await images.pixel(image,Math.floor(size.width/2),Math.floor(size.height/2));await images.recycle(image);shots.push({...size,center});}
                        for(let i=0;i<6;i++){await shot();await pause(250)}console.log('$rotate');
                        while((await host.coordinates.getInfo()).rotation!==1)await pause(100);await pause(500);
                        for(let i=0;i<4;i++){await shot();await pause(180)}
                        const movie=await host.screenRecorder.stop();await images.stopScreenCapture();return {movie,shots};
                    """.trimIndent())}
                    harness.await("audio consent"){if(future.isDone)fail(future.get().toString());harness.consentButton(true)!=null};harness.clickConsent(true)
                    harness.await("audio ready"){if(future.isDone)fail(future.get().toString());logged(ready)}
                    SystemClock.sleep(600)
                    val commandNs=System.nanoTime();tone.enabled=true
                    val presentationNs=tone.awaitPresentation(true);pulse=true
                    SystemClock.sleep(600);tone.enabled=false;tone.awaitPresentation(false);pulse=false
                    harness.await("audio rotation"){if(future.isDone)fail(future.get().toString());logged(rotate)}
                    assertTrue(harness.automation.setRotation(1))
                    val result=future.get(30,TimeUnit.SECONDS);val movie=result.getJSONObject("movie")
                    assertTrue(movie.getBoolean("audio"));assertEquals("aac",movie.getString("audioCodec"));assertEquals(48000,movie.getInt("audioSampleRate"))
                    val shots=result.getJSONArray("shots");assertEquals(10,shots.length())
                    assertTrue(shots.getJSONObject(0).getInt("height")>shots.getJSONObject(0).getInt("width"))
                    assertTrue(shots.getJSONObject(9).getInt("width")>shots.getJSONObject(9).getInt("height"))
                    assertTrue((0 until shots.length()).any{shots.getJSONObject(it).getInt("center")==Color.MAGENTA})
                    val file=File(movie.getString("path"));val decoded=decodeAudio(file)
                    assertTrue("Playback tone missing: $decoded",decoded.peakTone>150)
                    val retriever=MediaMetadataRetriever();var videoOnset=-1L
                    try {
                        retriever.setDataSource(file.absolutePath)
                        for(time in 0L..2_500_000L step 40_000L) {
                            val frame=retriever.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST)?:continue
                            val pixel=frame.getPixel(frame.width/2,frame.height/2);frame.recycle()
                            if(Color.red(pixel)>200&&Color.blue(pixel)>200&&Color.green(pixel)<70){videoOnset=time;break}
                        }
                    } finally {retriever.release()}
                    assertTrue("Video pulse missing",videoOnset>=0)
                    assertTrue("Calibrated A/V onset video=$videoOnset audio=${decoded.onsetUs}",abs(videoOnset-decoded.onsetUs)<150_000)
                    result.put("decodedAudio",decoded.json()).put("videoOnsetUs",videoOnset)
                        .put("fixturePlaybackBufferMs",(presentationNs-commandNs)/1_000_000.0)
                    evidence("$kind-playback-parallel",result);harness.awaitNoProjection()
                } finally {pool.shutdownNow();pulse=false}
            }
        }}finally{folder.deleteRecursively()}
    }

    @Test fun microphoneMixedNotificationStopAndIndependentAudioGrants() = verifyMicrophone("bun")

    internal fun verifyMicrophone(kind:String)=harness.withGrants {
        val folder=File(context.getExternalFilesDir(null),"audio-microphone-${UUID.randomUUID()}").apply{mkdirs()}
        try {Tone(context,true).use {tone -> tone.enabled=true
            harness.withFixture {
                val pool=Executors.newSingleThreadExecutor();val notify="AUDIO_NOTIFICATION_${UUID.randomUUID()}"
                try {
                    val future=pool.submit<JSONObject>{harness.execute(kind,"""
                        await images.requestScreenCapture({timeoutMs:30000});const movies=[];
                        for(const audio of ['microphone','mixed']){
                          await host.screenRecorder.start(${JSONObject.quote(folder.absolutePath)}+'/'+audio+'.mp4',{width:320,height:192,audio});
                          for(let i=0;i<4;i++){const shot=await images.captureScreen();await images.recycle(shot);await pause(450)}
                          movies.push(await host.screenRecorder.stop());
                        }
                        await host.screenRecorder.start(${JSONObject.quote(File(folder,"notification.mp4").absolutePath)},{width:320,height:192,audio:'mixed'});
                        console.log('$notify');while((await host.screenRecorder.getStatus()).state!=='idle')await pause(100);
                        const shot=await images.captureScreen();await images.recycle(shot);await images.stopScreenCapture();return {movies,notificationStopped:true};
                    """.trimIndent())}
                    harness.await("microphone consent"){if(future.isDone)fail(future.get().toString());harness.consentButton(true)!=null};harness.clickConsent(true)
                    harness.await("notification stop checkpoint"){if(future.isDone)fail(future.get().toString());logged(notify)}
                    val manager=context.getSystemService(NotificationManager::class.java)
                    val notification=manager.activeNotifications.first{it.notification.channelId=="node_audio_recording"}
                    notification.notification.actions.first().actionIntent.send()
                    val result=future.get(15,TimeUnit.SECONDS);val movies=result.getJSONArray("movies")
                    for(i in 0 until movies.length()) {
                        val movie=movies.getJSONObject(i);val decoded=decodeAudio(File(movie.getString("path")))
                        assertTrue("Real microphone/mixed signal missing: $decoded",decoded.rms>20&&decoded.peakTone>10)
                        movie.put("decodedAudio",decoded.json())
                    }
                    assertFalse(File(folder,"notification.mp4").exists())
                    assertEquals(setOf("microphone.mp4","mixed.mp4"),folder.list()!!.toSet())
                    harness.await("microphone disclosure released"){manager.activeNotifications.none{it.notification.channelId=="node_audio_recording"}}
                    if(kind=="bun") {
                    BunHostCapabilityGrants.setScreenRecordMicrophoneGranted(context,false)
                    harness.execute(kind,"""
                        eq((await rejects(()=>host.screenRecorder.start('/sdcard/denied.mp4',{audio:'microphone'}))).code,'NOT_GRANTED','microphone grant');
                        eq((await rejects(()=>host.screenRecorder.start('/sdcard/denied.mp4',{audio:'mixed'}))).code,'NOT_GRANTED','mixed microphone grant');
                        const response=await fetch('http://autojs6/v1/screen.record',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({method:'start',args:['/sdcard/denied.mp4',{audio:'microphone'}]})});
                        eq(response.status,403,'raw microphone bypass');return {denied:true};
                    """.trimIndent())
                    BunHostCapabilityGrants.setScreenRecordPlaybackGranted(context,false)
                    harness.execute(kind,"eq((await rejects(()=>host.screenRecorder.start('/sdcard/denied.mp4',{audio:'playback'}))).code,'NOT_GRANTED','playback grant');return {denied:true};")
                    }
                    evidence("$kind-microphone-mixed",result);harness.awaitNoProjection()
                }finally{pool.shutdownNow()}
            }
        }}finally{folder.deleteRecursively()}
    }

    @Test fun playbackCaptureHonorsTheSourceOptOutPolicy() = verifyPolicy("bun")

    internal fun verifyPolicy(kind:String)=harness.withGrants {
        val folder=File(context.getExternalFilesDir(null),"audio-policy-${UUID.randomUUID()}").apply{mkdirs()}
        try {Tone(context,false).use {tone -> tone.enabled=true
            harness.withFixture {
                val pool=Executors.newSingleThreadExecutor()
                try {
                    val future=pool.submit<JSONObject>{harness.execute(kind,"""
                        await images.requestScreenCapture({timeoutMs:30000});
                        await host.screenRecorder.start(${JSONObject.quote(File(folder,"policy.mp4").absolutePath)},{width:320,height:192,audio:'playback'});
                        await pause(1800);const movie=await host.screenRecorder.stop();await images.stopScreenCapture();return {movie};
                    """.trimIndent())}
                    harness.await("policy consent"){if(future.isDone)fail(future.get().toString());harness.consentButton(true)!=null};harness.clickConsent(true)
                    val result=future.get(20,TimeUnit.SECONDS);val decoded=decodeAudio(File(result.getJSONObject("movie").getString("path")))
                    assertTrue("Disallowed playback leaked: $decoded",decoded.peakTone<10)
                    evidence("$kind-playback-policy",result.put("decodedAudio",decoded.json()));harness.awaitNoProjection()
                }finally{pool.shutdownNow()}
            }
        }}finally{folder.deleteRecursively()}
    }

    @Test fun mixedExecutionCancellationReleasesAudioVideoAndPartialFiles()=harness.withGrants {
        val folder=File(context.getExternalFilesDir(null),"audio-cancel-${UUID.randomUUID()}").apply{mkdirs()}
        try {harness.withFixture {
            val pool=Executors.newSingleThreadExecutor();val owned=AtomicReference<BunPluginScriptEngine>();val marker="AUDIO_CANCEL_${UUID.randomUUID()}"
            try {
                val future=pool.submit<JSONObject>{harness.execute("bun","""
                    await images.requestScreenCapture({timeoutMs:30000});
                    await host.screenRecorder.start(${JSONObject.quote(File(folder,"cancelled.mp4").absolutePath)},{width:320,height:192,audio:'mixed'});
                    console.log('$marker');await pause(60000);return {unexpected:true};
                """.trimIndent(),owned)}
                harness.await("mixed cancellation consent"){if(future.isDone)fail(future.get().toString());harness.consentButton(true)!=null};harness.clickConsent(true)
                harness.await("mixed cancellation checkpoint"){if(future.isDone)fail(future.get().toString());logged(marker)}
                owned.get().forceStop();assertNotNull(runCatching{future.get(15,TimeUnit.SECONDS)}.exceptionOrNull())
                harness.awaitNoProjection()
                harness.await("audio/video threads and files released"){
                    folder.list()!!.isEmpty()&&Thread.getAllStackTraces().keys.none{it.isAlive&&it.name in listOf("ScreenRecording-AAC","ScreenRecording-AVC","ScreenRecording-GL")}
                }
                harness.await("audio cancellation notification released"){
                    context.getSystemService(NotificationManager::class.java).activeNotifications.none{it.notification.channelId=="node_audio_recording"}
                }
                evidence("execution-cancel",JSONObject().put("threadsReleased",true).put("filesRemoved",true))
            }finally{pool.shutdownNow()}
        }}finally{folder.deleteRecursively()}
    }

    private fun logged(marker:String)=global.snapshotLogEntries().any{it.content.toString().contains(marker)}
    private fun evidence(name:String,value:JSONObject){File(context.getExternalFilesDir(null),"recording-audio-$name.json").writeText(value.toString(2))}

    internal data class AudioProof(val samples:Int,val rms:Double,val peakTone:Double,val onsetUs:Long) {
        fun json()=JSONObject().put("samples",samples).put("rms",rms).put("peak660Hz",peakTone).put("onsetUs",onsetUs)
    }
    internal fun decodeAudio(file:File):AudioProof {
        val extractor=MediaExtractor();var decoder:MediaCodec?=null
        try {
            extractor.setDataSource(file.absolutePath)
            assertEquals("One AVC and one AAC track",2,extractor.trackCount)
            val audio=(0 until extractor.trackCount).single{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)==MediaFormat.MIMETYPE_AUDIO_AAC}
            val format=extractor.getTrackFormat(audio);assertEquals(48000,format.getInteger(MediaFormat.KEY_SAMPLE_RATE));assertEquals(1,format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT)
            extractor.selectTrack(audio)
            val codec=MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also{decoder=it;it.configure(format,null,null,0);it.start()}
            val info=MediaCodec.BufferInfo();var inputDone=false;var outputDone=false;var count=0;var squares=0.0;var peak=0.0;var onset=-1L
            val deadline=SystemClock.elapsedRealtime()+15000
            while(!outputDone) {
                check(SystemClock.elapsedRealtime()<deadline){"AAC decode deadline"}
                if(!inputDone) {
                    val index=codec.dequeueInputBuffer(10000)
                    if(index>=0){val buffer=checkNotNull(codec.getInputBuffer(index));val size=extractor.readSampleData(buffer,0)
                        if(size<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
                        else{codec.queueInputBuffer(index,0,size,extractor.sampleTime,0);extractor.advance()}}
                }
                val index=codec.dequeueOutputBuffer(info,10000)
                if(index>=0) {
                    val data=checkNotNull(codec.getOutputBuffer(index)).duplicate().order(ByteOrder.LITTLE_ENDIAN)
                    data.position(info.offset);data.limit(info.offset+info.size)
                    var re=0.0;var im=0.0;val n=info.size/2
                    for(i in 0 until n){val value=data.short.toDouble();squares+=value*value;count++;val angle=2*PI*660*i/48000;re+=value*cos(angle);im+=value*sin(angle)}
                    val amplitude=if(n==0)0.0 else 2*sqrt(re*re+im*im)/n
                    peak=maxOf(peak,amplitude);if(amplitude>150&&onset<0)onset=info.presentationTimeUs
                    outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;codec.releaseOutputBuffer(index,false)
                }
            }
            assertTrue("Decoded duration",count>48000)
            return AudioProof(count,sqrt(squares/count),peak,onset)
        }finally{runCatching{decoder?.stop()};decoder?.release();extractor.release()}
    }

    internal class Tone(context:Context,allowed:Boolean):AutoCloseable {
        @Volatile var enabled=false
        @Volatile private var writtenEnabled=false
        @Volatile private var transitionFrame=-1L
        @Volatile private var running=true
        private val manager=context.getSystemService(AudioManager::class.java)
        private val previous=manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        private val track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(if(allowed)AudioAttributes.ALLOW_CAPTURE_BY_ALL else AudioAttributes.ALLOW_CAPTURE_BY_NONE).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(3840).build()
        private val worker:Thread
        init {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC,maxOf(1,manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/3),0)
            track.play()
            worker=Thread({val samples=ShortArray(480);var position=0L
                while(running){val audible=enabled
                    if(audible!=writtenEnabled){transitionFrame=position;writtenEnabled=audible}
                    for(i in samples.indices)samples[i]=(if(audible)sin(2*PI*660*(position+i)/48000)*12000 else 0.0).toInt().toShort()
                    if(track.write(samples,0,samples.size,AudioTrack.WRITE_BLOCKING)<0)break;position+=samples.size}
            },"RecordingAudio-test-tone").apply{start()}
        }
        fun awaitPresentation(value:Boolean):Long {
            val timestamp=AudioTimestamp();val deadline=System.nanoTime()+2_000_000_000L
            while(System.nanoTime()<deadline){
                if(writtenEnabled==value&&transitionFrame>=0&&track.getTimestamp(timestamp)){
                    val time=timestamp.nanoTime+(transitionFrame-timestamp.framePosition)*1_000_000_000L/48000
                    if(System.nanoTime()>=time)return time
                }
                SystemClock.sleep(1)
            }
            throw AssertionError("No AudioTrack presentation timestamp for the fixture pulse")
        }
        override fun close(){running=false;track.pause();track.flush();worker.join(1500);track.stop();track.release();manager.setStreamVolume(AudioManager.STREAM_MUSIC,previous,0)}
    }
}

/** Run separately before granting RECORD_AUDIO; revoking a runtime permission kills the target process. */
class RecordingAudioPermissionDeviceTest {
    @Test fun missingAndroidRecordingPermissionRejectsBothAudioSourcesAndKeepsScreenshots() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        val harness=CaptureWorkerBridgeDeviceTest();val folder=File(context.getExternalFilesDir(null),"audio-denied-${UUID.randomUUID()}").apply{mkdirs()}
        try {harness.withGrants{harness.withFixture {
            val pool=Executors.newSingleThreadExecutor()
            try {
                val future=pool.submit<JSONObject>{harness.execute("bun","""
                    await images.requestScreenCapture({timeoutMs:30000});
                    for(const audio of ['microphone','playback','mixed']){
                      const error=await rejects(()=>host.screenRecorder.start(${JSONObject.quote(File(folder,"denied.mp4").absolutePath)},{width:320,height:192,audio}));
                      eq(error.code,'NOT_GRANTED','Android permission');if(!error.message.includes('RECORD_AUDIO'))throw error;
                    }
                    const frame=await images.captureScreen();await images.recycle(frame);await images.stopScreenCapture();return {denied:true};
                """.trimIndent())}
                harness.await("denied audio consent"){if(future.isDone)fail(future.get().toString());harness.consentButton(true)!=null};harness.clickConsent(true)
                assertTrue(future.get(20,TimeUnit.SECONDS).getBoolean("denied"));assertTrue(folder.list()!!.isEmpty());harness.awaitNoProjection()
            }finally{pool.shutdownNow()}
        }}}finally{folder.deleteRecursively()}
    }
}
