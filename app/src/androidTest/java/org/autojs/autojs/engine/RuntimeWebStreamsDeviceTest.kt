package org.autojs.autojs.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RuntimeWebStreamsDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val ui=RuntimeUiComposeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    private val modes=InstrumentationRegistry.getArguments().getString("webMode")?.let(::listOf) ?: listOf("node","node-esm","worker","worker-esm","worker-nested","bun")

    @Test fun awaitedPagePromisesAndCancellationAcrossRuntimes()=ui.grants { harness.withFixture {RuntimeWebFixtureServer().use {server->
        for(mode in modes) assertTrue(ui.run(mode,RuntimeWebStreamsFixture.setup(server.url)+"""
            try {
              if(await web.evaluateJavascriptAsync('Promise.resolve(42)')!==42)throw Error('Promise result');
              if(await web.evaluateJavascriptAsync('undefined')!==null)throw Error('Undefined result');
              for(const [source,message] of [["Promise.reject(new DOMException('denied','NotAllowedError'))",'NotAllowedError'],['(()=>{throw new TypeError("bad")})()','TypeError'],['({value:1n})','TypeError']]){
                let failed=false;try{await web.evaluateJavascriptAsync(source);}catch(e){failed=String(e).includes(message);}if(!failed)throw Error('Lost rejection '+message);
              }
              await web.setWebChromeClient({async onJsConfirm(){await this.title;return true;}});
              if(!await web.evaluateJavascriptAsync('Promise.resolve(confirm("async"))'))throw Error('Callback deadlock');
              let timeout=false;try{await web.evaluateJavascriptAsync('new Promise(()=>{})',{timeoutMs:150});}catch(e){timeout=String(e).includes('timed out');}if(!timeout)throw Error('No timeout');
              if(await web.evaluateJavascript('Object.getOwnPropertyNames(window).filter(k=>k.startsWith("__autojs6_result_")).length')!==0)throw Error('Result leak');
              const pending=web.evaluateJavascriptAsync('new Promise(()=>{})').then(()=>false,()=>true);
              await web.loadUrl('${server.url}/index?next');if(!await pending)throw Error('Navigation did not cancel');
              return {promises:true,errors:true,timeout:true,navigation:true};
            }finally{await window.close();await web.dispose();}
        """.trimIndent()).getBoolean("promises"))
    } } }

    @Test fun liveAudioVideoChunksAndRecordingAcrossRuntimes()=ui.grants {harness.withFixture {RuntimeWebFixtureServer().use {server->
        val evidence=JSONArray()
        RecordingAudioDeviceTest.Tone(context,true).use {tone->tone.enabled=true
            for(mode in modes){val pool=Executors.newSingleThreadExecutor();try{
                val future=pool.submit<JSONObject>{ui.run(mode,RuntimeWebStreamsFixture.setup(server.url)+RuntimeWebStreamsFixture.record())}
                controls.await("Streaming permission and result for $mode",90000){controls.permission(true);future.isDone}
                val result=future.get(1,TimeUnit.SECONDS)
                assertEquals(1,server.uploads.size)
                val bytes=server.uploads.single();assertEquals(result.getString("sha256"),RuntimeWebMediaProof.sha256(bytes));assertEquals(result.getInt("size"),bytes.size)
                evidence.put(JSONObject().put("mode",mode).put("result",result).put("decoded",RuntimeWebStreamsFixture.inspect(context,bytes)))
                bytes.fill(0);server.uploads.clear()
            }finally{pool.shutdownNow()}}
        }
        File(context.getExternalFilesDir(null),"runtime-web-streams.json").writeText(evidence.toString(2))
    } } }

    @Test fun webPermissionDenialAndUnsupportedConstraints()=ui.grants {harness.withFixture {RuntimeWebFixtureServer().use {server->
        for(mode in listOf("node","worker-nested","bun"))assertTrue(ui.run(mode,RuntimeWebStreamsFixture.setup(server.url)+"""
            try {
              async function denied(){let failed=false;try{await web.evaluateJavascriptAsync('navigator.mediaDevices.getUserMedia({audio:true}).then(s=>{s.getTracks().forEach(t=>t.stop());return true;})');}catch(e){failed=String(e).includes('NotAllowedError');}if(!failed)throw Error('Permission must be denied');}
              await denied();await web.setWebChromeClient({onPermissionRequest:()=>null});await denied();
              await web.setWebChromeClient({onPermissionRequest:()=>['unrequested']});await denied();
              let invalid=false;try{await web.evaluateJavascriptAsync('navigator.mediaDevices.getUserMedia({audio:false,video:false})');}catch(e){invalid=String(e).includes('TypeError');}if(!invalid)throw Error('Invalid constraints');
              if(await web.evaluateJavascriptAsync('MediaRecorder.isTypeSupported("video/autojs6-unsupported")'))throw Error('False codec support');
              return {denied:true};
            }finally{await window.close();await web.dispose();}
        """.trimIndent()).getBoolean("denied"))
    } } }

    @Test fun workerExitReleasesLiveMicrophoneWhileParentRemains()=ui.grants {harness.withFixture {RuntimeWebFixtureServer().use {server->
        val manager=context.getSystemService(android.media.AudioManager::class.java)
        val before=manager.activeRecordingConfigurations.map{it.clientAudioSessionId}.toSet()
        val child="const ui=require('ui'),{parentPort}=require('node:worker_threads');(async()=>{"+RuntimeWebStreamsFixture.setup(server.url)+"""
            await web.setWebChromeClient({onPermissionRequest:e=>({resources:e.resources,requestAndroidPermissions:true})});
            await web.evaluateJavascriptAsync('navigator.mediaDevices.getUserMedia({audio:true}).then(s=>{window.stream=s;return true;})');
            parentPort.postMessage('active');setInterval(()=>{},1000);
        """.trimIndent()+"})().catch(e=>{throw e;});"
        val nested="const {Worker,parentPort}=require('node:worker_threads');new Worker(${JSONObject.quote(child)},{eval:true}).on('message',v=>parentPort.postMessage(v)).on('error',e=>{throw e;});"
        val pool=Executors.newSingleThreadExecutor()
        try {
            val future=pool.submit<JSONObject>{ui.run("node","""
                const parent=await ui.createView('Text',{text:'Streaming parent'}),window=await ui.showLayout(parent),{Worker}=require('node:worker_threads');
                const worker=new Worker(${JSONObject.quote(nested)},{eval:true});
                await new Promise((r,j)=>{worker.once('message',r);worker.once('error',j);});
                while(!await(await fetch('${server.url}/worker-stop')).json())await new Promise(r=>setTimeout(r,60));
                await worker.terminate();await parent.set({text:'Streaming worker terminated'});
                while(await(await fetch('${server.url}/worker-stop')).json())await new Promise(r=>setTimeout(r,60));
                await window.close();await parent.dispose();return {terminated:true};
            """.trimIndent())}
            controls.await("Child microphone live",60000){controls.permission(true);if(future.isDone)fail(future.get().toString());manager.activeRecordingConfigurations.map{it.clientAudioSessionId}.toSet()!=before}
            server.stopWorker=true
            controls.await("Parent alive after nested Worker exit",30000){controls.labelled("Streaming worker terminated")!=null}
            harness.await("Nested Worker microphone released"){manager.activeRecordingConfigurations.map{it.clientAudioSessionId}.toSet()==before}
            server.stopWorker=false;assertTrue(future.get(20,TimeUnit.SECONDS).getBoolean("terminated"))
        }finally{pool.shutdownNow()}
    } } }
}

/** Run with both host media permissions revoked. Each method requires a fresh process. */
class RuntimeWebStreamsPermissionDeviceTest {
    @Test fun androidGrantOrDenialIsDeliveredToPage() {
        val args=InstrumentationRegistry.getArguments();val allow=args.getString("allowMedia")=="true";val mode=args.getString("webMode") ?: "node"
        val ui=RuntimeUiComposeDeviceTest();val harness=CaptureWorkerBridgeDeviceTest();val controls=RuntimeWebMediaControls(harness)
        ui.grants {harness.withFixture {RuntimeWebFixtureServer().use {server->
            val pool=Executors.newSingleThreadExecutor()
            try {
                val future=pool.submit<JSONObject>{ui.run(mode,RuntimeWebStreamsFixture.setup(server.url)+"""
                    try {
                      await web.setWebChromeClient({onPermissionRequest:e=>({resources:e.resources,requestAndroidPermissions:true})},{callbackTimeoutMs:60000});
                      let result;try{result=await web.evaluateJavascriptAsync('navigator.mediaDevices.getUserMedia({audio:true,video:true}).then(s=>{s.getTracks().forEach(t=>t.stop());return {allowed:true};})',{timeoutMs:60000});}
                      catch(e){if(!String(e).includes('NotAllowedError'))throw e;result={allowed:false};}return result;
                    }finally{await window.close();await web.dispose();}
                """.trimIndent())}
                controls.await("Android streaming permission decision",80000){controls.permission(allow);future.isDone}
                assertEquals(allow,future.get(1,TimeUnit.SECONDS).getBoolean("allowed"))
            }finally{pool.shutdownNow()}
        }}}
    }
}

/** The page owns DOM streams; runtime facades exchange JSON and existing bounded download reads. */
internal object RuntimeWebStreamsFixture {
    fun setup(base:String)="""
        const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web);
        async function wait(fn,label){const end=Date.now()+20000;while(!await fn()){if(Date.now()>end)throw Error(label);await new Promise(r=>setTimeout(r,40));}}
        await web.loadUrl('$base/index');await wait(async()=>await web.evaluateJavascript('document.readyState==="complete"&&document.title==="Web callbacks"')===true,'Page loaded');
    """.trimIndent()+"\n"

    fun record()="""
        const jobs=[];
        try {
          const origin=await web.evaluateJavascript('location.origin');
          await web.setWebChromeClient({onPermissionRequest(event){if(new URL(event.origin).origin!==origin)throw Error('Unexpected origin '+event.origin);return {resources:event.resources.filter(r=>r==='android.webkit.resource.AUDIO_CAPTURE'||r==='android.webkit.resource.VIDEO_CAPTURE'),requestAndroidPermissions:true};}},{callbackTimeoutMs:60000});
          const tracks=await web.evaluateJavascriptAsync(`(async()=>{
            if(!isSecureContext||!navigator.mediaDevices||!window.MediaRecorder)throw Error('Web media unavailable');
            window.stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:false},video:{width:{ideal:320},height:{ideal:240},frameRate:{ideal:10}}});
            const video=document.createElement('video');video.muted=true;video.autoplay=true;video.playsInline=true;video.srcObject=stream;document.body.append(video);await video.play();
            await stream.getVideoTracks()[0].applyConstraints({frameRate:{ideal:10}});
            const audio=stream.getAudioTracks()[0];audio.enabled=false;audio.enabled=true;
            window.chunks=[];window.recorder=new MediaRecorder(stream,{mimeType:'video/webm;codecs=vp8,opus',videoBitsPerSecond:200000,audioBitsPerSecond:64000});
            recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data);};
            window.stopped=new Promise((r,j)=>{recorder.onstop=r;recorder.onerror=e=>j(e.error);});recorder.start(250);
            return stream.getTracks().map(t=>({kind:t.kind,readyState:t.readyState,enabled:t.enabled}));
          })()`,{timeoutMs:60000});
          if(tracks.length!==2||tracks.some(t=>t.readyState!=='live'||!t.enabled))throw Error('Live tracks');
          await wait(async()=>await web.evaluateJavascript('chunks.length>=2')===true,'Streaming chunks');
          let streamed=0;
          for(let index=0;index<2;index++){
            const chunk=await web.evaluateJavascriptAsync(`(async()=>{const blob=chunks[${'$'}{index}],bytes=new Uint8Array(await blob.arrayBuffer());return {url:URL.createObjectURL(blob),size:blob.size,head:Array.from(bytes.slice(0,16))};})()`);
            const job=await web.download({url:chunk.url,fileName:'autojs6-webstreams-chunk-'+Date.now()+'.webm'});jobs.push(job.id);
            await wait(async()=>{const state=await web.getDownload(job.id);if(state.status==='failed')throw Error(state.error);return state.status==='successful';},'Chunk download during recording');
            if(JSON.stringify([...await web.readDownload(job.id,0,16)])!==JSON.stringify(chunk.head))throw Error('Streamed chunk bytes');
            streamed+=chunk.size;await web.cancelDownload(job.id);await web.evaluateJavascript('URL.revokeObjectURL('+JSON.stringify(chunk.url)+')');
          }
          if(await web.evaluateJavascript('recorder.state')!=='recording')throw Error('Not streamed while recording');
          if(await web.evaluateJavascriptAsync('new Promise(r=>{recorder.onpause=()=>r(recorder.state);recorder.pause();})')!=='paused')throw Error('Pause');
          if(await web.evaluateJavascriptAsync('new Promise(r=>{recorder.onresume=()=>r(recorder.state);recorder.resume();})')!=='recording')throw Error('Resume');
          await web.evaluateJavascript('recorder.requestData();true');await new Promise(r=>setTimeout(r,1800));
          const result=await web.evaluateJavascriptAsync(`(async()=>{
            recorder.stop();await stopped;stream.getTracks().forEach(t=>t.stop());
            const blob=new Blob(chunks,{type:recorder.mimeType}),bytes=new Uint8Array(await blob.arrayBuffer());
            const sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('');
            if(!(await fetch('/upload',{method:'POST',body:blob})).ok)throw Error('Upload failed');
            return {url:URL.createObjectURL(blob),size:blob.size,sha256,mimeType:recorder.mimeType,chunks:chunks.length,ended:stream.getTracks().every(t=>t.readyState==='ended'),head:Array.from(bytes.slice(0,16))};
          })()`);
          const final=await web.download({url:result.url,fileName:'autojs6-webstreams-final-'+Date.now()+'.webm'});jobs.push(final.id);
          await wait(async()=>{const s=await web.getDownload(final.id);if(s.status==='failed')throw Error(s.error);return s.status==='successful';},'Final download');
          if(JSON.stringify([...await web.readDownload(final.id,0,16)])!==JSON.stringify(result.head))throw Error('Final bytes');
          if(!result.ended||!streamed||result.chunks<3)throw Error('Recording lifecycle');
          await web.evaluateJavascript('URL.revokeObjectURL('+JSON.stringify(result.url)+')');delete result.url;result.streamedBytes=streamed;return result;
        }finally{
          await web.evaluateJavascript('if(window.stream)stream.getTracks().forEach(t=>t.stop());if(window.recorder&&recorder.state!=="inactive")recorder.stop();true').catch(()=>{});
          for(const id of jobs)await web.cancelDownload(id).catch(()=>{});await window.close();await web.dispose();
        }
    """.trimIndent()

    fun inspect(context:android.content.Context,bytes:ByteArray):JSONObject {
        val file=File.createTempFile("web-streams-",".webm",context.cacheDir);file.writeBytes(bytes)
        val extractor=MediaExtractor();var decoder:android.media.MediaCodec?=null
        try {
            extractor.setDataSource(file.path);assertEquals(2,extractor.trackCount)
            val formats=(0 until extractor.trackCount).map(extractor::getTrackFormat)
            assertTrue(formats.any{it.getString(MediaFormat.KEY_MIME)=="audio/opus"});assertTrue(formats.any{it.getString(MediaFormat.KEY_MIME)=="video/x-vnd.on2.vp8"})
            val video=(0 until extractor.trackCount).single{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true}
            val format=extractor.getTrackFormat(video);extractor.selectTrack(video)
            val codec=android.media.MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).also{decoder=it;it.configure(format,null,null,0);it.start()}
            val info=android.media.MediaCodec.BufferInfo();var inputDone=false;var frameBytes=0;val deadline=SystemClock.elapsedRealtime()+15000
            while(frameBytes==0){check(SystemClock.elapsedRealtime()<deadline){"Video decode deadline"}
                if(!inputDone){val i=codec.dequeueInputBuffer(10000);if(i>=0){val size=extractor.readSampleData(requireNotNull(codec.getInputBuffer(i)),0)
                    if(size<0){inputDone=true;codec.queueInputBuffer(i,0,0,0,android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)}else{codec.queueInputBuffer(i,0,size,extractor.sampleTime,0);extractor.advance()}}}
                val i=codec.dequeueOutputBuffer(info,10000);if(i>=0){frameBytes=info.size;codec.releaseOutputBuffer(i,false);check(frameBytes>0||info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM==0){"Empty video output"}}
            }
            val result=JSONObject().put("width",format.getInteger(MediaFormat.KEY_WIDTH)).put("height",format.getInteger(MediaFormat.KEY_HEIGHT)).put("decodedFrameBytes",frameBytes)
            val audio=RecordingAudioDeviceTest().decodeAudio(file,2,"audio/opus")
            assertTrue("Recorded microphone samples",audio.rms>15);assertTrue("Recorded fixture tone",audio.peakTone>10)
            return result.put("audio",audio.json())
        }finally{runCatching{decoder?.stop()};decoder?.release();extractor.release();file.delete()}
    }
}
