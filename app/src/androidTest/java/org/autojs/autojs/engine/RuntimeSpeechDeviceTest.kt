package org.autojs.autojs.engine

import android.content.Context
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.autojs.autojs.core.plugin.speech.SpeechPluginHost
import org.autojs.autojs.core.plugin.speech.SpeechModelQuery
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RuntimeSpeechDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val harness = CaptureWorkerBridgeDeviceTest()
    private fun fixture(action: () -> Unit) {
        val prefs = context.getSharedPreferences("bun_host_capability_grants", Context.MODE_PRIVATE)
        val keys = listOf(Grants.SPEECH_CONTROL, Grants.JAVA_INTEROP)
        val saved = keys.associateWith { if (prefs.contains(it)) prefs.getBoolean(it, false) else null }
        val inventory = File(context.getExternalFilesDir(null), "runtime-speech-inventory-before.json")
        if (!inventory.exists()) runBlocking {
            inventory.writeText(JSONObject().put("models", org.json.JSONArray(SpeechPluginHost.listModels(context, SpeechModelQuery())))
                .put("plugin", SpeechPluginHost.capabilities(context).packageName).toString(2))
        }
        prefs.edit().apply { keys.forEach { putBoolean(it, true) } }.commit()
        try { harness.withFixture(setup = { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }) { action() } }
        finally { prefs.edit().apply { saved.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) } }.commit() }
    }
    private fun exercise(mode: String) = fixture {
        val program = RuntimeSpeechPrograms.body()
        val source = when (mode) {
            "bun" -> "const speech=host.speech,java=host.java;$program"
            "node" -> "const speech=require('autojs6:speech'),java=require('java');$program"
            else -> {
                val child = "const {parentPort}=require('node:worker_threads'),speech=require('speech'),java=require('java');(async()=>{$program})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e;});"
                "const {Worker}=require('node:worker_threads'),w=new Worker(${JSONObject.quote(child)},{eval:true});try{return await new Promise((r,j)=>{w.once('message',r);w.once('error',j);});}finally{await w.terminate();}"
            }
        }
        val result = harness.execute(if (mode == "bun") "bun" else "node", source, capabilities = listOf("speech", "java_interop"))
        assertTrue(result.getBoolean("modelImport"))
        assertTrue(result.getBoolean("mutedSpeech"))
        File(context.getExternalFilesDir(null), "runtime-speech-$mode.json").writeText(result.toString(2))
    }
    @Test fun nodeRecognitionSessionsAndModels() = exercise("node")
    @Test fun bunRecognitionSessionsAndModels() = exercise("bun")
    @Test fun workerRecognitionSessionsAndModels() = exercise("worker")

    @Test fun bunDeniedGrantAndMoreThan1024StreamingCalls() = fixture {
        val prefs = context.getSharedPreferences("bun_host_capability_grants", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Grants.SPEECH_CONTROL, false).commit()
        try {
            assertTrue(harness.execute("bun", "try{await host.speech.models.list();return {denied:false};}catch(e){return {denied:e.code==='NOT_GRANTED'};}").getBoolean("denied"))
        } finally { prefs.edit().putBoolean(Grants.SPEECH_CONTROL, true).commit() }
        val result = harness.execute("bun", """
            async function raw(method,args){
                const job=(await host.call('speech.control',{action:'start',method,args})).value.id;
                while(true){const result=(await host.call('speech.control',{action:'poll',id:job})).value;if(result.done)return result.value;}
            }
            const id=(await raw('open',[])).id;
            try{for(let i=0;i<520;i++)eq(await raw('poll',[id,[]]),[],'empty event queue');}
            finally{await raw('close',[id]);}
            return {calls:1044,available:await host.speech.isAvailable()};
        """.trimIndent())
        assertTrue(result.getBoolean("available"))
        File(context.getExternalFilesDir(null), "runtime-speech-streaming-budget.json").writeText(result.toString(2))
    }

    @Test fun microphoneWakeAndEsmNestedWorkerCleanup() = fixture {
        val result = harness.execute("node", """
            const speech=require('speech'),{Worker}=require('node:worker_threads');
            const caps=await speech.capabilities();if(!caps.microphoneGranted)throw Error('Speech plugin microphone permission is required');
            const installed=await speech.models.list({installed:true}),asr=installed.find(m=>m.id.includes('zipformer-streaming')),kws=installed.find(m=>m.kind==='kws');
            if(!asr||!kws)throw Error('Streaming ASR/KWS models are required');
            const event=(s,name)=>new Promise((r,j)=>{s.once(name,r);s.once('error',j);});
            const mic=await speech.listen({model:asr.id,timeoutMillis:0,maxDurationMillis:10000});
            const micReady=event(mic,'ready'),micEnd=event(mic,'end');await micReady;await mic.stop();eq((await micEnd).reason,'stopped','microphone stop');
            const wake=await speech.wake('hello',{model:kws.id,thenListen:{model:asr.id}});
            const wakeReady=event(wake,'ready'),wakeEnd=event(wake,'end');await wakeReady;await wake.stop();eq((await wakeEnd).reason,'stopped','wake stop');
            const body="const session=await speech.listen({source:'external',model:"+JSON.stringify(asr.id)+",timeoutMillis:0});session.once('ready',()=>parentPort.postMessage('ready'));session.on('error',e=>{throw e;});";
            const cjs="const {parentPort}=require('node:worker_threads'),speech=require('speech');(async()=>{"+body+"})().catch(e=>{throw e;});";
            const esm="import {parentPort} from 'node:worker_threads';import speech from 'autojs6:speech';"+body;
            const nested="const {parentPort,Worker}=require('node:worker_threads'),w=new Worker("+JSON.stringify(cjs)+",{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});";
            for(const [entry,options] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]) {
                const parent=await speech.listen({source:'external',model:asr.id,timeoutMillis:0});const ready=event(parent,'ready'),end=event(parent,'end');await ready;
                const worker=new Worker(entry,options);
                try{await new Promise((r,j)=>{worker.once('message',r);worker.once('error',j);});await worker.terminate();await parent.feed(new Int16Array(1600));await parent.cancel();eq((await end).reason,'cancelled','parent preserved');}
                finally{await worker.terminate();}
            }
            await speech.close();return {microphone:true,wake:true,workerModes:3,quiet:true};
        """.trimIndent(), capabilities = listOf("speech"))
        File(context.getExternalFilesDir(null), "runtime-speech-worker-modes.json").writeText(result.toString(2))
        assertEquals(3, result.getInt("workerModes"))
    }
}
