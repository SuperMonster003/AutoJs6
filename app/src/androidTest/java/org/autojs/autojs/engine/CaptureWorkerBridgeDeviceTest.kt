package org.autojs.autojs.engine

import android.app.Activity
import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.Paint
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.hardware.display.DisplayManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.view.Display
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.ui.log.LogActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Captures only an owned solid-color fixture; uses the real Android consent dialog. */
class CaptureWorkerBridgeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    internal val automation:UiAutomation by lazy {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).apply {
            serviceInfo=serviceInfo.apply { flags=flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        }
    }
    private val global get() = AutoJs.instance.globalConsole
    private val evidence = JSONArray()

    @Test fun projectionReadinessDistinguishesClearedBuffersFromBlackContent() {
        val bitmap=Bitmap.createBitmap(8,6,Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.TRANSPARENT)
            assertTrue(NodeScreenCaptureSession.isTransparentProjectionBuffer(bitmap))
            bitmap.eraseColor(Color.BLACK)
            assertFalse("A black display is a valid frame",NodeScreenCaptureSession.isTransparentProjectionBuffer(bitmap))
            bitmap.eraseColor(Color.TRANSPARENT);bitmap.setPixel(0,0,Color.RED)
            assertFalse("Opaque content away from the center is valid",NodeScreenCaptureSession.isTransparentProjectionBuffer(bitmap))
        } finally {bitmap.recycle()}
    }

    @Test fun bunRecordsPlayableMp4AcrossRotationAndRestoresCapture() = withGrants {
        val folder=File(context.getExternalFilesDir(null),"recording-${UUID.randomUUID()}").apply{mkdirs()}
        try { withFixture {
            val pool=Executors.newSingleThreadExecutor()
            val marker="RECORD_ROTATE_${UUID.randomUUID()}"
            try {
                val future=pool.submit<JSONObject>{execute("bun","""
                    const recorder=host.screenRecorder,dir=${JSONObject.quote(folder.absolutePath)};
                    await rejects(()=>recorder.start(dir+'/no-consent.mp4'));
                    await images.requestScreenCapture({timeoutMs:30000});await pause(600);
                    eq((await recorder.getStatus()).state,'idle','initial status');
                    await rejects(()=>recorder.start(dir+'/invalid.mp4',{width:319,height:192}));
                    await rejects(()=>recorder.start(dir+'/invalid.mp4',{audio:true}));
                    const started=await recorder.start(dir+'/portrait.mp4',{width:192,height:320,frameRate:15,bitRate:1000000});
                    eq(started.width,192,'width');eq(started.state,'recording','state');
                    await rejects(()=>recorder.start(dir+'/duplicate.mp4'));
                    const concurrent=await images.captureScreen();
                    eq(await images.pixel(concurrent,Math.floor(concurrent.width/2),Math.floor(concurrent.height/2)),$COLOR,'parallel screenshot');
                    const concurrentSize=images.getSize(concurrent);await images.recycle(concurrent);
                    await pause(1200);console.log('$marker');
                    while((await host.coordinates.getInfo()).rotation!==1)await pause(100);
                    await pause(1000);
                    const rotated=await images.captureScreen();
                    const screen=await host.coordinates.getInfo();eq(images.getSize(rotated),{width:screen.width,height:screen.height},'rotated parallel dimensions');
                    eq(await images.pixel(rotated,Math.floor(rotated.width/2),Math.floor(rotated.height/2)),$COLOR,'rotated parallel pixel');
                    await images.recycle(rotated);await pause(600);const portrait=await recorder.stop();
                    eq((await recorder.getStatus()).state,'idle','stopped status');eq(await recorder.stop(),null,'second stop');
                    const shot=await images.captureScreen();eq(await images.pixel(shot,Math.floor(shot.width/2),Math.floor(shot.height/2)),$COLOR,'restored screenshot');await images.recycle(shot);
                    await rejects(()=>recorder.start(dir+'/portrait.mp4',{width:192,height:320}));
                    await recorder.start(dir+'/landscape.mp4',{width:320,height:192,frameRate:15,bitRate:1000000});
                    await pause(1600);const landscape=await recorder.stop();
                    await recorder.start(dir+'/cancelled.mp4',{width:320,height:192});await pause(400);
                    eq(await recorder.cancel(),true,'cancel');eq(await recorder.cancel(),false,'second cancel');
                    await images.stopScreenCapture();return {portrait,landscape};
                """.trimIndent())}
                await("video consent"){if(future.isDone)fail("No consent: ${future.get()}");consentButton(true)!=null};clickConsent(true)
                await("video rotation checkpoint"){if(future.isDone)fail("No rotation checkpoint: ${future.get()}");global.snapshotLogEntries().any{it.content.toString().contains(marker)}}
                assertTrue(automation.setRotation(1));await("video landscape"){displayRotation()==1}
                val result=future.get(35,TimeUnit.SECONDS)
                for((name,width,height) in listOf(Triple("portrait",192,320),Triple("landscape",320,192))) {
                    val data=result.getJSONObject(name);val file=File(data.getString("path"))
                    assertEquals(file.length(),data.getLong("size"));assertTrue(data.toString(),data.getLong("durationMs")>=1000)
                    val extractor=MediaExtractor()
                    try {
                        extractor.setDataSource(file.absolutePath);assertEquals(1,extractor.trackCount)
                        val format=extractor.getTrackFormat(0);assertEquals("video/avc",format.getString(MediaFormat.KEY_MIME))
                        assertEquals(width,format.getInteger(MediaFormat.KEY_WIDTH));assertEquals(height,format.getInteger(MediaFormat.KEY_HEIGHT))
                        extractor.selectTrack(0);var samples=0;val timestamps=mutableSetOf<Long>()
                        while(extractor.sampleTime>=0){timestamps.add(extractor.sampleTime);samples++;extractor.advance()}
                        assertTrue("Encoded samples=$samples",samples>=10)
                        assertTrue("Video timestamps",timestamps.size>=10&&timestamps.max()-timestamps.min()>=900_000L)
                        data.put("samples",samples)
                    } finally {extractor.release()}
                    val retriever=MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        for(time in listOf(500_000L,1_200_000L)) {
                            val frame=checkNotNull(retriever.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST))
                            try {val pixel=frame.getPixel(frame.width/2,frame.height/2)
                                for(channel in listOf(Color::red,Color::green,Color::blue))assertTrue("Decoded pixel $pixel",kotlin.math.abs(channel(pixel)-channel(COLOR))<20)
                            } finally {frame.recycle()}
                        }
                        data.put("decodedFixture",true)
                    } finally {retriever.release()}
                }
                assertEquals(setOf("portrait.mp4","landscape.mp4"),folder.list()!!.toSet())
                evidence.put(result);awaitNoProjection()
            } finally {pool.shutdownNow()}
        };writeEvidence("video-output") } finally {folder.deleteRecursively()}
    }

    @Test fun bunVideoCancellationAndSeparateGrantCleanUnfinishedOutput() = withGrants {
        val folder=File(context.getExternalFilesDir(null),"recording-cleanup-${UUID.randomUUID()}").apply{mkdirs()}
        try {withFixture {
            val pool=Executors.newSingleThreadExecutor();val owned=AtomicReference<BunPluginScriptEngine>()
            val marker="VIDEO_ACTIVE_${UUID.randomUUID()}"
            try {
                val future=pool.submit<JSONObject>{execute("bun","""
                    await images.requestScreenCapture({timeoutMs:30000});
                    await host.screenRecorder.start(${JSONObject.quote(File(folder,"exit.mp4").absolutePath)},{width:320,height:192});
                    console.log('$marker');await pause(60000);return {unexpected:true};
                """.trimIndent(),owned)}
                await("cancel video consent"){if(future.isDone)fail("No consent: ${future.get()}");consentButton(true)!=null};clickConsent(true)
                await("active recording"){if(future.isDone)fail("No active recording: ${future.get()}");global.snapshotLogEntries().any{it.content.toString().contains(marker)}}
                owned.get().forceStop();assertNotNull(runCatching{future.get(15,TimeUnit.SECONDS)}.exceptionOrNull())
                awaitNoProjection();await("partial files removed"){folder.list()!!.isEmpty()}
                BunHostCapabilityGrants.setScreenRecordGranted(context,false)
                execute("bun","""
                    eq(host.hasCapability('screen.record'),false,'record grant');
                    eq((await rejects(()=>host.screenRecorder.start('/sdcard/denied.mp4'))).code,'NOT_GRANTED','denied');
                    const response=await fetch('http://autojs6/v1/screen.record',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',
                      headers:{'Content-Type':'application/json'},body:JSON.stringify({method:'start',args:['/sdcard/denied.mp4']})});
                    eq(response.status,403,'raw recording bypass');
                    await rejects(()=>host.call('screen.capture',{method:'start',args:['/sdcard/denied.mp4']}));
                    return {recordingGrantRejected:true};
                """.trimIndent())
            } finally {pool.shutdownNow()}
        };writeEvidence("video-cleanup")}finally{folder.deleteRecursively()}
    }

    @Test fun workerPinyinConsoleAndCoordinatesShareTheParentExecution() = withFixture {
        for(rotation in listOf(0,1)) {
            assertTrue(automation.setRotation(rotation));await("worker rotation"){displayRotation()==rotation};SystemClock.sleep(400)
            val(width,height)=displaySize()
            for((baseX,baseY) in listOf(720 to 1280,1080 to 1920,1920 to 1080)) {
                val body="""
                    (async()=>{
                      function eq(a,b){if(JSON.stringify(a)!==JSON.stringify(b))throw Error(JSON.stringify([a,b]));}
                      eq(pinyin.STYLE_NORMAL,0);eq(pinyin.MODE_PLACE_NAME,2);
                      const rows=await pinyin(Buffer.from('北京'),{style:pinyin.STYLE_NORMAL,mode:pinyin.MODE_NORMAL});eq(rows,[['bei'],['jing']]);eq(await rows.compact(),[['bei','jing']]);
                      eq(await pinyin.simple({toString(){return '北京';}}),'beijing');eq(await four('北京',{separator:' '}),'bei jing');
                      eq(await four.readings('北京',{tone:'WITH_TONE_NUMBER'}),[['bei3'],['jing1']]);
                      eq(await screen.getScaleBases(),{x:$baseX,y:$baseY});
                      const values=[];for(const [m,a] of [['cX',[]],['cY',[]],['cX',[360]],['cY',[640]],['cXy',[0.5,'16:9']],['cYx',[0.5,'16:9']]]) values.push(await screen[m](...a));
                      eq(values,[$width,$height,${kotlin.math.round(360.0*width/baseX).toInt()},${kotlin.math.round(640.0*height/baseY).toInt()},${kotlin.math.floor(height*0.5*16/9+0.5).toInt()},${kotlin.math.floor(width*0.5*9/16+0.5).toInt()}]);
                      let rejected=0;for(const action of [()=>screen.cX(undefined),()=>screen.cY(NaN),()=>screen.setScaleBaseX(720)]){try{await action()}catch(e){rejected++}}eq(rejected,3);
                      await controls.setTitle('Worker API validation');await console.setSize(0.6,0.3);await controls.show();await new Promise(r=>setTimeout(r,150));await controls.hide();
                      eq(console.log('WORKER_STDOUT_${rotation}_${baseX}'),undefined);
                      parentPort.postMessage({values,compact:true});
                    })().catch(e=>{throw e});
                """.trimIndent()
                val cjs="const {parentPort}=require('node:worker_threads');const pinyin=require('autojs6:pinyin'),four=require('pinyin4j'),screen=require('screen_metrics'),controls=require('node:console');$body"
                val esm="import {parentPort} from 'node:worker_threads';import pinyin from 'pinyin';import four from 'autojs6:pinyin4j';import screen from 'autojs6:screen_metrics';import controls from 'autojs6:console';$body"
                val nested="const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSONObject.quote(cjs)},{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e});"
                execute("node","""
                    await require('screen_metrics').setScaleBases($baseX,$baseY);
                    const {Worker}=require('node:worker_threads');const results=[];
                    for(const [source,esm] of [[${JSONObject.quote(cjs)},false],[${JSONObject.quote(esm)},true],[${JSONObject.quote(nested)},false]]) {
                      let result;await new Promise((resolve,reject)=>{const w=new Worker(esm?new URL('data:text/javascript,'+encodeURIComponent(source)):source,{eval:!esm});
                        w.on('message',v=>result=v);w.on('error',reject);w.on('exit',c=>c===0?resolve():reject(Error('Worker exit '+c)));});results.push(result);
                    }return {rotation:$rotation,baseX:$baseX,baseY:$baseY,results};
                """.trimIndent())
            }
        };writeEvidence("worker-text-console-coordinates")
    }

    @Test fun terminatingWorkerCancelsItsPendingConsentWhileParentStaysAlive() {
        withFixture {
            val marker="WORKER_STOPPED_${UUID.randomUUID()}"
            val pool=Executors.newSingleThreadExecutor()
            try {
                val future=pool.submit<JSONObject>{execute("node", """
                    const {Worker}=require('node:worker_threads');
                    const worker=new Worker("require('images').requestScreenCapture({timeoutMs:30000}).catch(()=>{});",{eval:true});
                    await pause(2500);await worker.terminate();console.log('$marker');
                    await pause(6000);await rejects(()=>require('media_projection').requestScreenCapture({requireExistingPermission:true}));return {parentStillAlive:true};
                """.trimIndent())}
                await("worker consent"){if(future.isDone)fail("No worker consent: ${future.get()}");consentButton(false)!=null}
                await("worker terminated"){global.snapshotLogEntries().any{it.content.toString().contains(marker)}}
                await("worker dialog closed"){consentButton(false)==null}
                assertTrue(future.get(15,TimeUnit.SECONDS).getBoolean("parentStillAlive"))
                awaitNoProjection()
            } finally {pool.shutdownNow()}
        }
        writeEvidence("worker-cancellation")
    }

    @Test fun bunConsentOutlivesOneBridgeCallCapturesBothRotationsAndRequestsAgain() = withGrants {
        withFixture {
            val portrait = displaySize()
            val marker = "ROTATE_${UUID.randomUUID()}"
            val pool = Executors.newSingleThreadExecutor()
            try {
                val future = pool.submit<JSONObject> { execute("bun", """
                    await rejects(() => images.captureScreen());
                    const start = Date.now();
                    eq(await images.requestScreenCapture({timeoutMs:45000}),true,'grant');
                    const consentMillis = Date.now()-start;
                    await pause(700);
                    async function shot() {
                      const image = await images.captureScreen();
                      const color = await images.pixel(image,Math.floor(image.width/2),Math.floor(image.height/2));
                      eq(color,${COLOR},'actual captured fixture');
                      const dimensions = images.getSize(image);
                      await images.recycle(image);return dimensions;
                    }
                    const portrait = await shot();
                    console.log('$marker');
                    while ((await host.coordinates.getInfo()).rotation!==1) await pause(100);
                    await pause(900);
                    const landscape = await shot();
                    eq(await images.stopScreenCapture(),true,'stop');
                    eq(await images.stopScreenCapture(),false,'second stop');
                    await rejects(() => images.captureScreen());
                    eq(await images.requestScreenCapture({width:320,height:180,orientation:'landscape',timeoutMs:30000}),true,'request again');
                    await pause(700);
                    const resized = await shot();eq(resized,{width:320,height:180},'requested dimensions');
                    // Leave the final capturer active to verify execution cleanup.
                    return {portrait,landscape,resized,consentMillis};
                """.trimIndent()) }
                await("first consent") { if (future.isDone) fail("No consent: ${future.get()}"); consentButton(true) != null }
                SystemClock.sleep(11_000)
                clickConsent(true)
                await("rotation checkpoint") { if (future.isDone) fail("No rotation checkpoint: ${future.get()}"); global.snapshotLogEntries().any { it.content.toString().contains(marker) } }
                assertTrue(automation.setRotation(1))
                await("landscape") { displayRotation() == 1 }
                val landscape = displaySize()
                await("second consent") { if (future.isDone) fail("No second consent: ${future.get()}"); consentButton(true) != null }
                clickConsent(true)
                val result = future.get(45,TimeUnit.SECONDS)
                assertTrue(result.toString(),result.getLong("consentMillis") >= 10_000)
                assertEquals(portrait.first,result.getJSONObject("portrait").getInt("width"))
                assertEquals(portrait.second,result.getJSONObject("portrait").getInt("height"))
                assertEquals(landscape.first,result.getJSONObject("landscape").getInt("width"))
                assertEquals(landscape.second,result.getJSONObject("landscape").getInt("height"))
                awaitNoProjection()
                execute("bun","await rejects(() => images.captureScreen()); return {freshExecution:true};")
            } finally { pool.shutdownNow() }
        }
        writeEvidence("consent-rotation")
    }

    @Test fun bunDenialTimeoutAndStoppingPendingConsentRemainBounded() = withGrants {
        withFixture {
            val pool = Executors.newSingleThreadExecutor()
            try {
                val denied = pool.submit<JSONObject> { execute("bun","let code,message; try { await images.requestScreenCapture({timeoutMs:15000}); } catch(e) {code=e.code;message=e.message;} return {code,message};") }
                await("deniable consent") { if (denied.isDone) fail("No consent: ${denied.get()}"); consentButton(false) != null }; clickConsent(false)
                assertEquals("NOT_GRANTED",denied.get(20,TimeUnit.SECONDS).getString("code"))
                val expired = execute("bun","let code; try { await images.requestScreenCapture({timeoutMs:1500}); } catch(e) {code=e.code;} return {code};")
                assertEquals("TIMEOUT",expired.getString("code"))
                await("expired dialog closed") { consentButton(false) == null }
                val stopped = pool.submit<JSONObject> { execute("bun", """
                    const pending = images.requestScreenCapture({timeoutMs:15000}).then(()=> 'unexpected',e=>e.code);
                    await pause(2500);const stopped=await images.stopScreenCapture();
                    const code=await pending;if(code==='unexpected')throw Error('Pending consent was granted');
                    return {stopped,code};
                """.trimIndent()) }
                await("cancelable consent") { consentButton(false) != null }
                assertTrue(stopped.get(20,TimeUnit.SECONDS).getBoolean("stopped"))
                await("cancelled dialog closed") { consentButton(false) == null }
                awaitNoProjection()
            } finally { pool.shutdownNow() }
        }
        writeEvidence("denial-timeout-stop")
    }

    @Test fun bunExecutionCancellationClosesConsentAndCaptureGrantCannotBeBypassed() = withGrants {
        withFixture {
            val pool=Executors.newSingleThreadExecutor()
            val owned=AtomicReference<BunPluginScriptEngine>()
            try {
                val future=pool.submit<JSONObject> { execute("bun","await images.requestScreenCapture({timeoutMs:45000}); return {unexpected:true};",owned) }
                await("execution consent") { if (future.isDone) fail("No consent: ${future.get()}"); consentButton(false)!=null }
                owned.get().forceStop()
                assertNotNull(runCatching { future.get(15,TimeUnit.SECONDS) }.exceptionOrNull())
                await("execution dialog closed") { consentButton(false)==null }
                awaitNoProjection()
            } finally { pool.shutdownNow() }
            BunHostCapabilityGrants.setScreenCaptureGranted(context,false)
            execute("bun", """
                eq(host.hasCapability('screen.capture'),false,'grant');
                await rejects(()=>images.requestScreenCapture()); await rejects(()=>images.captureScreen());
                const response=await fetch('http://autojs6/v1/screen.capture',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',
                  headers:{'Content-Type':'application/json'},body:JSON.stringify({method:'requestScreenCapture',args:[]})});
                eq(response.status,403,'raw bypass');
                await rejects(()=>host.call('image.process',{method:'captureScreen',args:[]}));
                return {grantRejected:true};
            """.trimIndent())
        }
        writeEvidence("cancellation-grant")
    }

    @Test fun workerCjsEsmAndNestedFacadesUseRealProvidersAndBinaryTransport() {
        val random=java.util.Random(42)
        val pixels=IntArray(192*192){0xff000000.toInt() or random.nextInt(0x1000000)}
        val bitmap=Bitmap.createBitmap(pixels,192,192,Bitmap.Config.ARGB_8888)
        val encoded=try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);Base64.encodeToString(it.toByteArray(),Base64.NO_WRAP) } } finally { bitmap.recycle() }
        val body="""
            (async()=>{
              if(await cc.s2t('汉语')!=='漢語')throw Error('OpenCC');
              if(await cc.s2t(Buffer.from('汉语'))!=='漢語')throw Error('OpenCC worker coercion');
              const bytes=Buffer.from(workerData.encoded,'base64');
              const padded=Buffer.concat([Buffer.from([0]),bytes,Buffer.from([0])]);
              const img=await images.fromBytes(padded.subarray(1,1+bytes.length));
              const out=await images.toBytes(img,'png');
              if(!Buffer.isBuffer(out)||out.length<65536)throw Error('Worker binary path');
              const roundtrip=await images.fromBytes(out);
              const plane=await images.readPixels(roundtrip);
              if(plane.data.length!==36864||plane.data[36863]!==${pixels.last()})throw Error('Worker pixels');
              await images.recycle(roundtrip);
              if(!images.isRecycled(roundtrip))throw Error('Worker recycle bookkeeping');
              parentPort.postMessage({image:img,text:await cc.convert('漢語','t2s')});
            })().catch(error=>{throw error});
        """.trimIndent()
        val cjs="const {parentPort,workerData}=require('node:worker_threads');const cc=require('autojs6:opencc'),images=require('images');\n$body"
        val esm="import {parentPort,workerData} from 'node:worker_threads';import cc from 'autojs6:opencc';import images from 'autojs6:images';\n$body"
        val nested="const {Worker,parentPort,workerData}=require('node:worker_threads');const child=new Worker(${JSONObject.quote(cjs)},{eval:true,workerData});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e});"
        execute("node", """
            const {Worker}=require('node:worker_threads');const results=[];
            for(const [source,esm] of [[${JSONObject.quote(cjs)},false],[${JSONObject.quote(esm)},true],[${JSONObject.quote(nested)},false]]) {
              let result;await new Promise((resolve,reject)=>{
                const worker=new Worker(esm?new URL('data:text/javascript,'+encodeURIComponent(source)):source,{eval:!esm,workerData:{encoded:'$encoded'}});
                worker.on('message',value=>result=value);worker.on('error',reject);worker.on('exit',code=>code===0?resolve():reject(Error('exit '+code)));
              });
              eq(result.text,'汉语','worker text');
              // An intentionally transferred handle belongs to the execution and survives worker exit.
              eq(await images.pixel(result.image,0,0),${pixels.first()},'shared execution handle');
              await images.recycle(result.image);results.push(true);
            }
            return {cjs:results[0],esm:results[1],nested:results[2]};
        """.trimIndent())
        writeEvidence("worker-modules")
    }

    internal fun execute(kind:String,body:String,owned:AtomicReference<BunPluginScriptEngine>?=null, capabilities:List<String>?=null, workingDirectory:String?=null, timeoutMs:Long=90_000):JSONObject {
        val marker="CAPTURE_WORKER_${UUID.randomUUID()}:"
        val imports=if(kind=="bun") "const {createHostClient}=await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);const host=await createHostClient();const images=host.images;"
            else "const images=require('images');const host={screenRecorder:require('screen_recorder'),coordinates:require('screen_metrics')};"
        val source="""
            ${if(kind=="bun") "\"bun\";" else "\"nodejs\";"}
            (async()=>{ $imports
              const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
              function eq(a,b,label){if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label+': '+JSON.stringify(a)+' != '+JSON.stringify(b));}
              async function rejects(action){try{await action();}catch(e){return e;}throw Error('Expected rejection');}
              const result=await(async()=>{$body})();console.log(${JSONObject.quote(marker)}+JSON.stringify(result));
            })().catch(error=>{console.error(error.stack||String(error));process.exitCode=1;});
        """.trimIndent()
        val console=ConsoleImpl(global.uiHandler,global)
        val output:String
        if(kind=="bun") {
            val engine=BunPluginScriptEngine(context,global){console};owned?.set(engine)
            if(workingDirectory!=null)engine.setTag(ScriptEngine.TAG_WORKING_DIRECTORY,workingDirectory)
            engine.setTag(ExecutionConfig.tag,ExecutionConfig().apply { setArgument("bun.timeoutMillis",maxOf(120_000L,timeoutMs)) })
            try { engine.init();engine.execute(StringScriptSource("capture-worker.bun.js",source)) }
            catch(error:Throwable){throw AssertionError("Bun: "+console.snapshotLogEntries().joinToString("\n"){it.content.toString()}.takeLast(4000),error)}
            finally { engine.destroy() }
            output=console.snapshotLogEntries().joinToString("\n"){it.content.toString()}
        } else {
            try {
                val manifest=NodeBridgePermissionManifest.fromDeclaredCapabilities(capabilities?:listOf("opencc","image","media_projection","pinyin","pinyin4j","console","device","media","media.recording"))
                val result=NodeJsRuntimePluginBridge.runScript(context,NodePluginScriptRequest(source=source,sourceName="capture-worker.cjs",
                    workingDirectory=workingDirectory?:context.cacheDir.path,timeoutMs=timeoutMs,runtimeModuleSources=mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson())),console=console,
                    outputListener={_,text->console.println(android.util.Log.DEBUG,text)})
                assertTrue("${result.errorCode}: ${result.errorMessage}\n${result.stderr.takeLast(4000)}",result.succeeded&&!result.timedOut);output=result.stdout
            } finally {console.onRuntimeExit()}
        }
        val line=output.lineSequence().lastOrNull{it.contains(marker)}?:throw AssertionError(output.takeLast(4000))
        return JSONObject(line.substringAfter(marker)).also { evidence.put(JSONObject().put("runtime",kind).put("result",it)) }
    }

    internal fun withGrants(action:()->Unit) {
        val store=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val keys=listOf(BunHostCapabilityGrants.SCREEN_CAPTURE,BunHostCapabilityGrants.SCREEN_RECORD,
            BunHostCapabilityGrants.SCREEN_RECORD_MICROPHONE,BunHostCapabilityGrants.SCREEN_RECORD_PLAYBACK,
            BunHostCapabilityGrants.IMAGE_PROCESS,BunHostCapabilityGrants.SCREEN_METRICS)
        val previous=keys.associateWith{if(store.contains(it))store.getBoolean(it,false) else null}
        store.edit().apply{keys.forEach{putBoolean(it,true)}}.commit()
        try {action()}finally{store.edit().apply{previous.forEach{(k,v)->if(v==null)remove(k)else putBoolean(k,v)}}.commit()}
    }
    internal fun withFixture(setup:(Activity)->Unit = {}, action:()->Unit) {
        val saved=listOf(Settings.System.USER_ROTATION,Settings.System.ACCELEROMETER_ROTATION).associateWith{Settings.System.getString(context.contentResolver,it)}
        val rotation=displayRotation()
        val activity=instrumentation.startActivitySync(Intent(context,RuntimeCaptureFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync {
            activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.setContentView(object:View(activity){override fun onDraw(canvas:Canvas){
                canvas.drawColor(COLOR)
                canvas.drawRect(0f,0f,40f,40f,Paint().apply{color=if(SystemClock.uptimeMillis()/100%2==0L)Color.RED else Color.GREEN})
                postInvalidateDelayed(80)
            }})
            setup(activity)
        }
        try {assertTrue(automation.setRotation(0));await("portrait"){displayRotation()==0};SystemClock.sleep(400);action()}
        finally {
            automation.setRotation(rotation);automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            saved.forEach{(key,value)->shell(if(value==null)"settings --user current delete system $key" else "settings --user current put system $key $value")}
            instrumentation.runOnMainSync {activity.finish()}
        }
    }
    internal fun consentButton(positive:Boolean):AccessibilityNodeInfo? {
        val root=automation.rootInActiveWindow?:return null
        if(root.packageName?.toString()==context.packageName)return null
        val nodes=root.findAccessibilityNodeInfosByViewId(if(positive)"android:id/button1" else "android:id/button2")
        nodes.firstOrNull{it.isEnabled&&it.isClickable}?.let{return it}
        // The Android 13 Redmi uses Material buttons without the platform button ids.
        for(label in if(positive)listOf("START NOW","Start now")else listOf("CANCEL","Cancel")) {
            for(node in root.findAccessibilityNodeInfosByText(label)) {
                if(!node.text?.toString().equals(label,true))continue
                var clickable:AccessibilityNodeInfo?=node
                repeat(3){if(clickable?.isEnabled==true&&clickable?.isClickable==true)return clickable;clickable=clickable?.parent}
            }
        }
        return null
    }
    internal fun clickConsent(positive:Boolean) {assertTrue("System consent button",checkNotNull(consentButton(positive)).performAction(AccessibilityNodeInfo.ACTION_CLICK))}
    internal fun await(label:String,timeoutMs:Long=20_000,test:()->Boolean) {val deadline=SystemClock.elapsedRealtime()+timeoutMs;while(SystemClock.elapsedRealtime()<deadline){if(test())return;SystemClock.sleep(80)};fail("Timed out: $label")}
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    internal fun awaitNoProjection(){await("MediaProjection released"){!shell("dumpsys media_projection").contains(context.packageName)}}
    @Suppress("DEPRECATION") private fun displaySize()=Point().also{display().getRealSize(it)}.let{it.x to it.y}
    private fun display()=(context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
    private fun displayRotation()=display().rotation
    private fun writeEvidence(name:String){File(context.getExternalFilesDir(null),"capture-worker-$name.json").writeText(JSONObject().put("cases",evidence).toString(2))}
    companion object {private val COLOR=Color.rgb(51,102,153)}
}
