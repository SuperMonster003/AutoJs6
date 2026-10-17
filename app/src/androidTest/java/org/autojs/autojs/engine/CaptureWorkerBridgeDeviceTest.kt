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
    private val automation:UiAutomation by lazy {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).apply {
            serviceInfo=serviceInfo.apply { flags=flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        }
    }
    private val global get() = AutoJs.instance.globalConsole
    private val evidence = JSONArray()

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

    private fun execute(kind:String,body:String,owned:AtomicReference<BunPluginScriptEngine>?=null):JSONObject {
        val marker="CAPTURE_WORKER_${UUID.randomUUID()}:"
        val imports=if(kind=="bun") "const {createHostClient}=await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);const host=await createHostClient();const images=host.images;"
            else "const images=require('images');"
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
            engine.setTag(ExecutionConfig.tag,ExecutionConfig().apply { setArgument("bun.timeoutMillis",120_000) })
            try { engine.init();engine.execute(StringScriptSource("capture-worker.bun.js",source)) }
            catch(error:Throwable){throw AssertionError("Bun: "+console.snapshotLogEntries().joinToString("\n"){it.content.toString()}.takeLast(4000),error)}
            finally { engine.destroy() }
            output=console.snapshotLogEntries().joinToString("\n"){it.content.toString()}
        } else {
            try {
                val manifest=NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("opencc","image","media_projection"))
                val result=NodeJsRuntimePluginBridge.runScript(context,NodePluginScriptRequest(source=source,sourceName="capture-worker.cjs",
                    workingDirectory=context.cacheDir.path,timeoutMs=90_000,runtimeModuleSources=mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson())),console=console,
                    outputListener={_,text->console.println(android.util.Log.DEBUG,text)})
                assertTrue("${result.errorCode}: ${result.errorMessage}\n${result.stderr.takeLast(4000)}",result.succeeded&&!result.timedOut);output=result.stdout
            } finally {console.onRuntimeExit()}
        }
        val line=output.lineSequence().lastOrNull{it.contains(marker)}?:throw AssertionError(output.takeLast(4000))
        return JSONObject(line.substringAfter(marker)).also { evidence.put(JSONObject().put("runtime",kind).put("result",it)) }
    }

    private fun withGrants(action:()->Unit) {
        val store=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val keys=listOf(BunHostCapabilityGrants.SCREEN_CAPTURE,BunHostCapabilityGrants.IMAGE_PROCESS,BunHostCapabilityGrants.SCREEN_METRICS)
        val previous=keys.associateWith{if(store.contains(it))store.getBoolean(it,false) else null}
        store.edit().apply{keys.forEach{putBoolean(it,true)}}.commit()
        try {action()}finally{store.edit().apply{previous.forEach{(k,v)->if(v==null)remove(k)else putBoolean(k,v)}}.commit()}
    }
    private fun withFixture(action:()->Unit) {
        val saved=listOf(Settings.System.USER_ROTATION,Settings.System.ACCELEROMETER_ROTATION).associateWith{Settings.System.getString(context.contentResolver,it)}
        val rotation=displayRotation()
        val activity=instrumentation.startActivitySync(Intent(context,RuntimeCaptureFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync {
            activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.setContentView(object:View(activity){override fun onDraw(canvas:Canvas){canvas.drawColor(COLOR)}})
        }
        try {assertTrue(automation.setRotation(0));await("portrait"){displayRotation()==0};SystemClock.sleep(400);action()}
        finally {
            automation.setRotation(rotation);automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            saved.forEach{(key,value)->shell(if(value==null)"settings --user current delete system $key" else "settings --user current put system $key $value")}
            instrumentation.runOnMainSync {activity.finish()}
        }
    }
    private fun consentButton(positive:Boolean):AccessibilityNodeInfo? {
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
    private fun clickConsent(positive:Boolean) {assertTrue("System consent button",checkNotNull(consentButton(positive)).performAction(AccessibilityNodeInfo.ACTION_CLICK))}
    private fun await(label:String,test:()->Boolean) {val deadline=SystemClock.elapsedRealtime()+20_000;while(SystemClock.elapsedRealtime()<deadline){if(test())return;SystemClock.sleep(80)};fail("Timed out: $label")}
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    private fun awaitNoProjection(){await("MediaProjection released"){!shell("dumpsys media_projection").contains(context.packageName)}}
    @Suppress("DEPRECATION") private fun displaySize()=Point().also{display().getRealSize(it)}.let{it.x to it.y}
    private fun display()=(context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
    private fun displayRotation()=display().rotation
    private fun writeEvidence(name:String){File(context.getExternalFilesDir(null),"capture-worker-$name.json").writeText(JSONObject().put("cases",evidence).toString(2))}
    companion object {private val COLOR=Color.rgb(51,102,153)}
}
