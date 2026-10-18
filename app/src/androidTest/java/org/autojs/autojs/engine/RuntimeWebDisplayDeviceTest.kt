package org.autojs.autojs.engine

import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RuntimeWebDisplayDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val ui=RuntimeUiComposeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)

    @Test fun microphoneContinuesWhileAnotherAppIsVisible()=run("audio")
    @Test fun screenSharingContinuesWhileAnotherAppIsVisible()=run("display")
    @Test fun nativeCameraRecordingContinuesWhileAnotherAppIsVisible()=run("camera")

    private fun run(kind:String)=ui.grants {harness.withFixture {RuntimeWebFixtureServer().use {server->
        val modes=InstrumentationRegistry.getArguments().getString("webMode")?.let(::listOf) ?: listOf("node","node-esm","worker","worker-esm","worker-nested","bun")
        val results=JSONArray()
        assertNotEquals(context.applicationInfo.uid,instrumentation.context.applicationInfo.uid)
        (if(kind=="display")null else RecordingAudioDeviceTest.Tone(context,true,quiet=true)).use {tone->
            for(mode in modes){val pool=Executors.newSingleThreadExecutor();try {
                server.requests.clear()
                val program=when(kind){"audio"->RuntimeWebDisplayPrograms.audio();"camera"->RuntimeWebDisplayPrograms.camera();else->RuntimeWebDisplayPrograms.display(mode)}
                val body="const base="+JSONObject.quote(server.url)+";const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web);"+RuntimeWebDisplayPrograms.waiters()+
                    "await web.loadUrl(base+'/index');await wait(async()=>await web.evaluateJavascript('document.readyState===\"complete\"&&document.title===\"Web callbacks\"')===true,'Page ready');try{"+program+"}finally{await window.close();await web.dispose();}"
                val future=pool.submit<JSONObject>{ui.run(mode,body)}
                if(kind=="display")controls.click("Share $mode")
                controls.await("Background $kind ready",65000){controls.permission(true);if(kind=="display")harness.consentButton(true)?.let{harness.clickConsent(true)};if(future.isDone)fail(future.get().toString());server.requests.contains("/background-ready")}
                context.startActivity(Intent().setClassName(instrumentation.context.packageName,RuntimeWebBackgroundFixtureActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                controls.await("Background fixture visible"){controls.labelled("Close display fixture")!=null}
                android.os.SystemClock.sleep(300);tone?.pulse()
                controls.await("Background $kind continues",40000){if(future.isDone)fail(future.get().toString());server.requests.contains("/background-finished")}
                controls.click("Close display fixture")
                val result=future.get(45,TimeUnit.SECONDS);val bytes=server.uploads.single();assertEquals(result.getString("sha256"),RuntimeWebMediaProof.sha256(bytes))
                results.put(JSONObject().put("mode",mode).put("result",result).put("decoded",RuntimeWebDisplayProof.inspect(context,kind,bytes)).put("tone",tone?.pulseProof()))
                bytes.fill(0);server.uploads.clear();harness.awaitNoProjection()
            }finally{pool.shutdownNow()}}
        }
        File(context.getExternalFilesDir(null),"runtime-web-display-"+kind+RuntimeWebDisplayProof.suffix()+".json").writeText(results.toString(2))
    }}}
}

internal object RuntimeWebDisplayProof {
    fun suffix()=InstrumentationRegistry.getArguments().getString("proofSuffix")?.also{require(it.matches(Regex("[a-z0-9-]+")))}?.let{"-$it"}.orEmpty()
    fun inspect(context:android.content.Context,kind:String,bytes:ByteArray):JSONObject {
        val file=File.createTempFile("web-display-",if(kind=="camera")".mp4" else ".webm",context.cacheDir);file.writeBytes(bytes)
        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(file.path);assertEquals(if(kind=="camera")2 else 1,extractor.trackCount)
            val result=JSONObject()
            for(index in 0 until extractor.trackCount){val format=extractor.getTrackFormat(index);val mime=format.getString(MediaFormat.KEY_MIME)!!
                extractor.selectTrack(index);extractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);var last=0L;var samples=0
                do {if(extractor.sampleTime>=0){last=maxOf(last,extractor.sampleTime);samples++}}while(extractor.advance())
                assertTrue("Media must span the background interval: $mime $last",last>4_000_000);assertTrue(samples>5)
                result.put(mime,JSONObject().put("samples",samples).put("lastUs",last));extractor.unselectTrack(index);extractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }
            if(kind!="display"){
                val audio=RecordingAudioDeviceTest().decodeAudio(file,if(kind=="camera")2 else 1,if(kind=="camera")"audio/mp4a-latm" else "audio/opus")
                assertTrue("Decoded audio level: $audio",audio.rms>15)
                assertTrue("Acoustic fixture tone: $audio",audio.peakTone>10)
                assertTrue("Decoded audio duration: $audio",audio.samples>4*48000);result.put("audio",audio.json())
            }else result.put("frames",RuntimeWebDisplayFrameProof.decode(file))
            return result
        }finally{extractor.release();file.delete()}
    }
}
