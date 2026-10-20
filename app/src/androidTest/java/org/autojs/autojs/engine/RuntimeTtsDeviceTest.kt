package org.autojs.autojs.engine

import android.content.Context
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.autojs.autojs.runtime.api.tts.TtsException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RuntimeTtsDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private fun fixture(action:(File)->Unit) {
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val keys=listOf(Grants.TTS_CONTROL,Grants.JAVA_INTEROP)
        val old=keys.associateWith { if(prefs.contains(it)) prefs.getBoolean(it,false) else null }
        prefs.edit().apply { keys.forEach { putBoolean(it,true) } }.commit()
        val root=File(context.cacheDir,"tts-device-"+java.util.UUID.randomUUID()).canonicalFile.apply { mkdirs() }
        try { harness.withFixture(setup={it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}) { action(root) } }
        finally { check(root.parentFile==context.cacheDir.canonicalFile);root.deleteRecursively();prefs.edit().apply { old.forEach { (key,value)->if(value==null)remove(key)else putBoolean(key,value) } }.commit() }
    }
    private fun source(mode:String,program:String):String=when(mode) {
        "bun"->"const java=host.java,tts=host.tts;$program"
        "node"->"const java=require('java'),tts=require('autojs6:tts');$program"
        else->{
            val child="const {parentPort}=require('node:worker_threads'),java=require('java'),tts=require('tts');const eq=(a,b,label)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label+': '+JSON.stringify(a));};(async()=>{$program})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e;});"
            "const {Worker}=require('node:worker_threads');const w=new Worker(${JSONObject.quote(child)},{eval:true});try{return await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});}finally{await w.terminate();}"
        }
    }
    private fun exercise(mode:String)=fixture { root ->
        val result=harness.execute(if(mode=="bun")"bun" else "node",source(mode,RuntimeTtsPrograms.body(root.path)),capabilities=listOf("tts","java_interop"),workingDirectory=root.path)
        assertTrue(result.getBoolean("quiet"));assertTrue(result.getBoolean("file"))
        val file=File(result.getString("path")).canonicalFile
        assertEquals(root,file.parentFile)
        assertTrue(file.name.startsWith("runtime-tts-"));assertEquals(result.getLong("length"),file.length())
        result.put("sha256",RuntimeWebMediaProof.sha256(file.readBytes()));check(file.delete())
        File(context.getExternalFilesDir(null),"runtime-tts-$mode.json").writeText(result.toString(2))
    }
    @Test fun nodeSynthesisPlaybackQueriesAndCancellation()=exercise("node")
    @Test fun bunSynthesisPlaybackQueriesAndCancellation()=exercise("bun")
    @Test fun workerSynthesisPlaybackQueriesAndCancellation()=exercise("worker")
    @Test fun queuedEventsStayNativeUntilTheClientHasItsTaskHandle() {
        val client=RuntimeTtsBridge.Client();var terminals=0
        val first=client.createTask("first","file","one",{}, {terminals++})
        val second=client.createTask("second","file","two",{}, {terminals++})
        first.queued(mapOf("value" to 1));second.complete(mapOf("path" to "two.wav"));first.complete(mapOf("path" to "one.wav"))
        val events=client.drain(selected=setOf("first"));assertEquals(2,events.length());assertEquals("first",events.getJSONObject(1).getString("id"))
        assertEquals("done",client.task("second").state)
        val later=client.drain(selected=setOf("second"));assertEquals("two.wav",later.getJSONObject(0).getJSONObject("value").getString("path"));assertEquals(2,terminals)
        assertFalse(second.fail(TtsException("TEST","late")));assertFalse(second.start(emptyMap<String,Any>()));assertEquals("done",second.task.state);client.close()
    }
    @Test fun missingBunGrantDoesNotAllowTts()=fixture {
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Grants.TTS_CONTROL,false).commit()
        try {
            val result=harness.execute("bun","let denied=false;try{await host.tts.ready();}catch(error){denied=error.code==='NOT_GRANTED';}return {denied};")
            assertTrue(result.getBoolean("denied"))
        }finally{prefs.edit().putBoolean(Grants.TTS_CONTROL,true).commit()}
    }
    @Test fun esmAndNestedWorkersCloseOnlyTheirOwnTtsClient()=fixture {
        val result=harness.execute("node","""
            const tts=require('tts'),{Worker}=require('node:worker_threads');
            let configuration;const unavailable=[];
            for(const engine of await tts.engines()) {
                try {const voices=await tts.voices({engine:engine.name,installedOnly:true,offlineOnly:true});if(voices.length){configuration={engine:engine.name,voice:voices[0].name,volume:0};break;}}
                catch(error){unavailable.push(String(error));}
            }
            if(!configuration)throw Error('No available TTS engine: '+JSON.stringify(unavailable));
            await tts.configure(configuration);await tts.ready();
            const body="await tts.configure("+JSON.stringify(configuration)+");await tts.ready();const task=await tts.silenceTask(30000);parentPort.postMessage('ready');await task.result;";
            const cjs="const {parentPort}=require('node:worker_threads'),tts=require('tts');(async()=>{"+body+"})().catch(e=>{throw e;});";
            const esm="import {parentPort} from 'node:worker_threads';import tts from 'autojs6:tts';"+body;
            const nested="const {parentPort,Worker}=require('node:worker_threads');const child=new Worker("+JSON.stringify(cjs)+",{eval:true});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e;});";
            try {for(const [entry,options] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]) {
                const worker=new Worker(entry,options);
                try {
                    await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});
                    const parent=await tts.silenceTask(1800);await worker.terminate();
                    let deadline;
                    try {eq((await Promise.race([parent.result,new Promise((_,reject)=>{deadline=setTimeout(()=>reject(Error('Worker TTS was not released promptly')),10000);})])).status,'done','parent TTS survives child exit');}
                    finally{clearTimeout(deadline);}
                }finally{await worker.terminate();}
            }}finally{await tts.close();}
            return {workerModes:3,parentPreserved:true,quiet:true};
        """.trimIndent(),capabilities=listOf("tts"))
        assertEquals(3,result.getInt("workerModes"));assertTrue(result.getBoolean("parentPreserved"))
        File(context.getExternalFilesDir(null),"runtime-tts-worker-modes.json").writeText(result.toString(2))
    }
}
