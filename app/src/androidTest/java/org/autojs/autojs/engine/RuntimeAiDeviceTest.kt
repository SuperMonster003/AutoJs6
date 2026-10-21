package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RuntimeAiDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val harness = CaptureWorkerBridgeDeviceTest()

    internal fun fixture(granted: Boolean = true, action: () -> Unit) {
        val keys = RuntimeAiPrograms.grants.map(CredentialKeys.BUN_HOST_CAPABILITY_GRANT::key)
        val saved = keys.associateWith { it.storedOrNull() }
        val plugin = "io.github.supermonster003.autojs6.plugin.threestoneai"
        assertTrue("3-Stone AI is enabled in Plugin Center", PluginEnableStore.isEnabled(context, plugin))
        try { keys.forEach { it.set(granted) }; harness.withFixture(setup = { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }) { action() } }
        finally { saved.forEach { (key, value) -> if (value == null) key.remove(commit = true) else key.set(value, commit = true) } }
    }

    private fun exercise(mode: String) = fixture {
        val program = RuntimeAiPrograms.exercise()
        val script = when (mode) {
            "bun" -> "const ai=host.ai;$program"
            "node" -> "const ai=require('autojs6:ai');$program"
            else -> """
                const {Worker}=require('node:worker_threads');
                const worker=new Worker(${JSONObject.quote("const {parentPort}=require('node:worker_threads'),ai=require('ai');(async()=>{$program})().then(value=>parentPort.postMessage(value)).catch(error=>{throw error;});")},{eval:true});
                try{return await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});}finally{await worker.terminate();}
            """.trimIndent()
        }
        val result = harness.execute(if (mode == "bun") "bun" else "node", script,
            capabilities = RuntimeAiPrograms.capabilities, timeoutMs = 600000)
        assertTrue(result.toString(), result.getInt("checks") >= 20)
        File(context.getExternalFilesDir(null), "runtime-ai-core-$mode.json").writeText(result.toString(2))
    }

    @Test fun nodeCoreUsesRealProvider() = exercise("node")
    @Test fun bunCoreUsesRealProvider() = exercise("bun")
    @Test fun workerCoreUsesRealProvider() = exercise("worker")

    @Test fun workerModesCloseSessionsAndKeepParentClientUsable() = fixture {
        val result = harness.execute("node", """
            const ai=require('ai'),{Worker}=require('node:worker_threads');
            const catalog=await ai.catalog();const locals=catalog.targets.filter(t=>t.locality==='local'&&t.available);
            const target=(locals.find(t=>/E2B/i.test(t.displayName))||locals[0]).id;
            const body="const session=await ai.session({target:"+JSON.stringify(target)+",backend:'cpu',maxTokens:8});parentPort.postMessage({ready:!session.isClosed,target:session.target});setInterval(()=>{},1000);";
            const cjs="const {parentPort}=require('node:worker_threads'),ai=require('ai');(async()=>{"+body+"})().catch(e=>{throw e;});";
            const esm="import {parentPort} from 'node:worker_threads';import ai from 'autojs6:ai';"+body;
            const nested="const {parentPort,Worker}=require('node:worker_threads');const w=new Worker("+JSON.stringify(cjs)+",{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});";
            try {
                for(const [entry,options] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
                    const worker=new Worker(entry,options);
                    try{eq(await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);}),{ready:true,target},'worker session');}
                    finally{await worker.terminate();}
                    let current;for(let i=0;i<100;i++){try{current=await ai.catalog();break;}catch(e){if(e.code!=='BUSY')throw e;await pause(50);}}
                    if(!current?.targets.some(t=>t.id===target))throw Error('Parent client and provider admission were not released');
                }
                return {modes:3,parentPreserved:true,target};
            }finally{await ai.close();}
        """.trimIndent(), capabilities = RuntimeAiPrograms.capabilities, timeoutMs = 600000)
        File(context.getExternalFilesDir(null), "runtime-ai-core-worker-modes.json").writeText(result.toString(2))
    }

    @Test fun bunGrantAndNodeDeclarationRejectBeforeProviderAccess() = fixture(granted = false) {
        val bun = harness.execute("bun", "const error=await host.ai.catalog().then(()=>null,e=>e);if(error?.code!=='NOT_GRANTED')throw Error('AI grant');return {code:error.code};")
        assertEquals("NOT_GRANTED", bun.getString("code"))
        val node = harness.execute("node", "const ai=require('ai');const error=await ai.catalog().then(()=>null,e=>e);if(!error)throw Error('AI capability');return {code:error.code};", capabilities = emptyList())
        assertTrue(node.getString("code"), node.getString("code").contains("PERMISSION") || node.getString("code").contains("CAPABILITY"))
    }

    @Test fun terminalPacketsKeepCancellationOwnershipUntilAcknowledged() {
        RuntimeAiBridge(context).use { bridge ->
            fun call(method: String, args: JSONArray): Any {
                val reply = java.util.concurrent.atomic.AtomicReference<NodeBridgeResponse>()
                bridge.dispatch(NodeBridgeRequest("fixture", "ai", method, args.toString(), 5000)) { reply.set(it) }
                val value = requireNotNull(reply.get())
                assertTrue(value.error.toString(), value.ok)
                return JSONArray("[${value.resultJson}]").get(0)
            }
            val a = (call("open", JSONArray()) as JSONObject).getString("id")
            val b = (call("open", JSONArray()) as JSONObject).getString("id")
            fun start() = (call("start", JSONArray().put(a).put("hasAccess").put(JSONArray())) as JSONObject).getString("id")
            val late = start()
            assertTrue((call("poll", JSONArray().put(a).put(JSONArray().put(late))) as JSONArray).length() > 0)
            assertEquals(false, call("cancel", JSONArray().put(b).put(late)))
            assertEquals(true, call("cancel", JSONArray().put(a).put(late)))
            val accepted = start()
            call("poll", JSONArray().put(a).put(JSONArray().put(accepted)))
            assertEquals(true, call("ack", JSONArray().put(a).put(accepted)))
            assertEquals(false, call("cancel", JSONArray().put(a).put(accepted)))
        }
    }
}
