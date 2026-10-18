package org.autojs.autojs.engine

import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RuntimeWebDisplayLifecycleDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val ui=RuntimeUiComposeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)

    @Test fun freshScreenConsentDenialClonesAndPendingCancellation()=ui.grants {harness.withFixture {RuntimeWebFixtureServer().use {server->
        for(mode in listOf("node","worker-nested","bun")){val pool=Executors.newSingleThreadExecutor();try {
            val future=pool.submit<JSONObject>{ui.run(mode,RuntimeWebStreamsFixture.setup(server.url)+"""
                try {
                  const reg=await web.enableDisplayCapture({origin:'${server.url}',maxWidth:240,maxHeight:240,frameRate:5});
                  await web.evaluateJavascript(`window.phase=0;document.body.innerHTML='<button id="share">Deny $mode</button>';document.getElementById('share').onclick=async()=>{try{window.stream=await navigator.mediaDevices.getDisplayMedia({video:true});window.phase=2;}catch(e){window.failure=e.name;window.phase=1;}};true`);
                  await wait(async()=>await web.evaluateJavascript('phase===1')===true,'Denied display');if(await web.evaluateJavascript('failure')!=='NotAllowedError')throw Error('Denial mapping');
                  await web.evaluateJavascript('document.getElementById("share").textContent="Allow $mode";true');await wait(async()=>await web.evaluateJavascript('phase===2')===true,'Fresh display');
                  await web.evaluateJavascript(`window.pulses=0;window.pulse=setInterval(()=>{document.body.style.backgroundColor=++pulses%2?'red':'green';},150);window.clone=stream.getVideoTracks()[0].clone();stream.getTracks().forEach(t=>t.stop());window.parts=[];window.recorder=new MediaRecorder(new MediaStream([clone]));recorder.ondataavailable=e=>{if(e.data.size)parts.push(e.data);};recorder.start(250);true`);
                  await wait(async()=>await web.evaluateJavascript('clone.readyState!=="live"||parts.length>=2')===true,'Clone frames');if(await web.evaluateJavascript('clone.readyState')!=='live')throw Error('Clone source ended early');
                  await web.evaluateJavascript('clearInterval(pulse);recorder.stop();clone.stop();phase=0;document.getElementById("share").textContent="Cancel $mode";true');
                  await new Promise(r=>setTimeout(r,800));
                  await fetch('${server.url}/ready-third');
                  while(!await(await fetch('${server.url}/worker-stop')).json())await new Promise(r=>setTimeout(r,60));
                  await web.disableDisplayCapture(reg.id);if(await web.evaluateJavascriptAsync('new Promise(r=>setTimeout(()=>r(window.failure),200))')!=='AbortError')throw Error('Pending share not cancelled');
                  return {denied:true,clone:true,cancelled:true};
                }finally{await window.close();await web.dispose();}
            """.trimIndent())}
            controls.click("Deny $mode");controls.await("Display denial dialog"){harness.consentButton(false)!=null};harness.clickConsent(false)
            controls.click("Allow $mode");controls.await("Fresh display consent"){harness.consentButton(true)!=null};harness.clickConsent(true)
            controls.await("Clone recording finished"){if(future.isDone)fail(future.get().toString());server.requests.contains("/ready-third")}
            harness.awaitNoProjection();controls.click("Cancel $mode");controls.await("Third consent"){harness.consentButton(true)!=null};server.stopWorker=true
            assertTrue(future.get(30,TimeUnit.SECONDS).getBoolean("cancelled"));harness.awaitNoProjection();controls.await("Pending consent closed"){harness.consentButton(true)==null}
            server.stopWorker=false;server.requests.clear()
        }finally{pool.shutdownNow()}}
    }}}

    @Test fun nativeLimitsCancellationAndNotificationStop()=ui.grants {harness.withFixture {RuntimeWebFixtureServer().use {server->
        for(mode in listOf("node","worker-nested","bun")){val pool=Executors.newSingleThreadExecutor();try {
            val future=pool.submit<JSONObject>{ui.run(mode,RuntimeWebStreamsFixture.setup(server.url)+"""
                try {
                  await web.startBackgroundRecording({durationLimitSeconds:2,width:640,height:480});
                  await wait(async()=>(await web.getBackgroundRecording()).state==='stopped','Native duration limit');const limited=await web.stopBackgroundRecording();if(limited.reason!=='limit'||limited.size<1000)throw Error('Automatic recording output');
                  const cancelled=await web.startBackgroundRecording({width:640,height:480});if(!await web.cancelBackgroundRecording(cancelled.id))throw Error('Cancel returned false');
                  if((await web.getBackgroundRecording()).state!=='cancelled')throw Error('Cancellation not complete');
                  const notified=await web.startBackgroundRecording({width:640,height:480});await new Promise(r=>setTimeout(r,1500));await fetch('${server.url}/notification-ready');
                  await wait(async()=>(await web.getBackgroundRecording()).state==='stopped','Notification stop');const result=await web.stopBackgroundRecording();
                  if(result.id!==notified.id||result.reason!=='notification-stop'||result.size<1000)throw Error('Notification did not preserve output');
                  const head=await web.readDocument(result.uri,0,12);if(String.fromCharCode(...head.slice(4,8))!=='ftyp')throw Error('Invalid saved MP4');return {limit:true,cancelled:true,notification:true};
                }finally{await web.cancelBackgroundRecording().catch(()=>{});await window.close();await web.dispose();}
            """.trimIndent())}
            controls.await("Native notification ready",60000){controls.permission(true);if(future.isDone)fail(future.get().toString());server.requests.contains("/notification-ready")}
            val notification=context.getSystemService(NotificationManager::class.java).activeNotifications.single{it.id==0xD3}.notification
            requireNotNull(notification.actions).last().actionIntent.send()
            assertTrue(future.get(30,TimeUnit.SECONDS).getBoolean("notification"));server.requests.clear()
            harness.await("Media foreground notification released"){context.getSystemService(NotificationManager::class.java).activeNotifications.none{it.id==0xD3}}
        }finally{pool.shutdownNow()}}
    }}}

    @Test fun workerExitCancelsBorrowedViewRecordingAndPreservesReplacement()=ui.grants {harness.withFixture {RuntimeWebFixtureServer().use {server->
        val child="const ui=require('ui'),{parentPort,workerData}=require('node:worker_threads');(async()=>{const web=ui.view(workerData);parentPort.postMessage(await web.startBackgroundRecording({width:640,height:480}));setInterval(()=>{},1000);})().catch(e=>{throw e;});"
        for(replace in listOf(false,true)) {
            val result=ui.run("node",RuntimeWebStreamsFixture.setup(server.url)+"""
                const {Worker}=require('node:worker_threads');let child;
                try {
                  web.on('titleChanged',()=>{});
                  child=new Worker(${JSONObject.quote(child)},{eval:true,workerData:web});const owned=await new Promise((r,j)=>{child.once('message',r);child.once('error',j);});
                  let replacement;if($replace){await new Promise(r=>setTimeout(r,1300));await web.stopBackgroundRecording();replacement=await web.startBackgroundRecording({width:640,height:480});}
                  await child.terminate();child=null;
                  if($replace){await new Promise(r=>setTimeout(r,500));const current=await web.getBackgroundRecording();if(current.state!=='recording'||current.id!==replacement.id)throw Error('Replacement was stopped');await web.cancelBackgroundRecording(replacement.id);}
                  else await wait(async()=>(await web.getBackgroundRecording()).state==='cancelled','Worker native recording cleanup');
                  if(await web.title!=='Web callbacks')throw Error('Borrowed parent View was closed');return {replacement:$replace,cleaned:true};
                }finally{if(child)await child.terminate();await web.cancelBackgroundRecording().catch(()=>{});await window.close();await web.dispose();}
            """.trimIndent())
            assertTrue(result.getBoolean("cleaned"))
        }
    }}}
}
