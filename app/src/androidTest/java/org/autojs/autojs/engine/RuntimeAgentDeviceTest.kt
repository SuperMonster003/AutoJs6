package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs.project.ProjectConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RuntimeAgentDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val harness = CaptureWorkerBridgeDeviceTest()

    internal fun fixture(granted: Boolean = true, action: () -> Unit) {
        val keys = RuntimeAgentPrograms.grants.map(CredentialKeys.BUN_HOST_CAPABILITY_GRANT::key)
        val saved = keys.associateWith { it.storedOrNull() }
        assertTrue(PluginEnableStore.isEnabled(context, "io.github.supermonster003.autojs6.plugin.three.stove.agent"))
        assertTrue(PluginEnableStore.isEnabled(context, "io.github.supermonster003.autojs6.plugin.threestoneai"))
        try {
            keys.forEach { it.set(granted) }
            harness.withFixture(setup = { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }) { action() }
        } finally { saved.forEach { (key, value) -> if (value == null) key.remove(commit = true) else key.set(value, commit = true) } }
    }

    internal fun localTarget(): String = harness.execute("node", """
        const ai=require('ai');try{const rows=(await ai.catalog()).targets.filter(t=>t.locality==='local'&&t.available);
        const target=rows.find(t=>/E2B/i.test(t.displayName))||rows[0];if(!target)throw Error('No available local model');return {target:target.id};}
        finally{await ai.close();}
    """.trimIndent(), capabilities = listOf("ai")).getString("target")

    private fun exercise(mode: String) = fixture {
        val program = RuntimeAgentPrograms.exercise(localTarget())
        val body = when (mode) {
            "bun" -> "const ai=host.ai;$program"
            "node" -> "const ai=require('ai');$program"
            else -> {
                val child = "const {parentPort}=require('node:worker_threads'),ai=require('ai');(async()=>{$program})().then(value=>parentPort.postMessage(value)).catch(error=>{throw error;});"
                "const {Worker}=require('node:worker_threads');const worker=new Worker(${JSONObject.quote(child)},{eval:true});try{return await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});}finally{await worker.terminate();}"
            }
        }
        val result = harness.execute(if (mode == "bun") "bun" else "node", body, capabilities = RuntimeAgentPrograms.capabilities, timeoutMs = 600000)
        assertTrue(result.toString(), result.getInt("checks") >= 23)
        File(context.getExternalFilesDir(null), "runtime-ai-agent-$mode.json").writeText(result.toString(2))
    }
    @Test fun nodeAgentUsesRealPluginAndModel() = exercise("node")
    @Test fun bunAgentUsesIndependentGrantWithRealPluginAndModel() = exercise("bun")
    @Test fun workerAgentUsesRealPluginAndModel() = exercise("worker")

    @Test fun agentDiscoveryUsesTheOriginalProtectedService() = fixture {
        val plugin = "io.github.supermonster003.autojs6.plugin.three.stove.agent"
        val info = kotlinx.coroutines.runBlocking { org.autojs.autojs.core.plugin.agent.ThreeStoveAgentPluginHost.probe(context, plugin) }
        assertEquals("three-stove-agent", info.id)
        assertEquals("three-stove-agent", info.engine)
        assertEquals(1, org.autojs.autojs.core.plugin.agent.ThreeStoveAgentPluginHost.queryServiceCount(context))
        assertTrue(info.supportedAbis.isNullOrEmpty())
    }

    @Test fun coreAiCapabilityDoesNotAuthorizeAgentTasks() = fixture(granted = false) {
        val bun = harness.execute("bun", "const e=await host.ai.agent.status().then(()=>null,e=>e);return {code:e?.code};")
        assertEquals("NOT_GRANTED", bun.getString("code"))
        val node = harness.execute("node", "const e=await require('ai').agent.status().then(()=>null,e=>e);return {code:e?.code};", capabilities = listOf("ai"))
        assertTrue(node.getString("code"), node.getString("code").contains("PERMISSION") || node.getString("code").contains("CAPABILITY"))
    }

    @Test fun nativeTaskAcknowledgmentAndExplicitDetachedCancellationRetainOwnership() = fixture {
        val target = localTarget()
        RuntimeAgentBridge(context, context.cacheDir.path).use { bridge ->
            fun call(method: String, args: JSONArray): Any {
                var response: NodeBridgeResponse? = null
                bridge.dispatch(NodeBridgeRequest("fixture", "aiAgent", method, args.toString(), 10000)) { response = it }
                val value = requireNotNull(response); assertTrue(value.error.toString(), value.ok)
                return JSONArray("[${value.resultJson}]").get(0)
            }
            val a = (call("open", JSONArray()) as JSONObject).getString("id")
            val b = (call("open", JSONArray()) as JSONObject).getString("id")
            fun start(method: String, args: JSONArray) = (call("start", JSONArray().put(a).put(method).put(args)) as JSONObject).getString("id")
            fun receive(id: String): JSONObject {
                val bytes = java.io.ByteArrayOutputStream(); val deadline = android.os.SystemClock.elapsedRealtime() + 30000
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    val packets = call("poll", JSONArray().put(a).put(JSONArray().put(id))) as JSONArray
                    for (i in 0 until packets.length()) {
                        val packet = packets.getJSONObject(i)
                        bytes.write(android.util.Base64.decode(packet.getString("data"), android.util.Base64.DEFAULT))
                        if (packet.getBoolean("last")) return JSONObject(bytes.toString(Charsets.UTF_8.name()))
                    }
                    Thread.sleep(20)
                }
                throw AssertionError("Agent packet timed out")
            }
            val status = start("status", JSONArray()); assertEquals("resolve", receive(status).getString("type"))
            assertEquals(false, call("cancel", JSONArray().put(b).put(status)))
            assertEquals(true, call("ack", JSONArray().put(a).put(status)))
            assertEquals(false, call("cancel", JSONArray().put(a).put(status)))
            val options = JSONObject().put("target", target).put("tools", JSONArray()).put("memory", false).put("plan", false)
                .put("locale", "en").put("detached", true).put("budget", JSONObject().put("maxDurationMs", 120000))
            val task = start("run", JSONArray().put(RuntimeAgentPrograms.goal("Write a long detailed explanation of clouds in the final summary.")).put(JSONObject()).put(options))
            val created = receive(task); assertEquals(created.toString(), "resolve", created.getString("type"))
            val info = created.getJSONObject("value"); val id = info.getString("id")
            assertFalse("Task must still be active at the cancellation checkpoint", info.getString("state") in setOf("completed", "partial", "failed", "blocked", "cancelled"))
            assertEquals(false, call("cancel", JSONArray().put(b).put(task)))
            assertEquals(true, call("cancel", JSONArray().put(a).put(task)))
            val record = requireNotNull(org.autojs.autojs.core.plugin.agent.ThreeStoveAgentLinkController.get(context).scriptRuns.get(id))
            val deadline = android.os.SystemClock.elapsedRealtime() + 120000
            while (!record.settled && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
            assertEquals("cancelled", record.result?.get("status")?.asString)
            File(context.getExternalFilesDir(null), "runtime-ai-agent-ownership.json").writeText(JSONObject()
                .put("id", id).put("detached", true).put("explicitAdmissionCancellation", true).put("status", "cancelled").toString(2))
        }
    }

    @Test fun registeredNodeBunAndWorkerScriptsShareOnlyTheirExecutionContext() = fixture {
        val results = JSONArray()
        for (mode in listOf("node", "bun", "worker")) {
            val root = File(context.cacheDir, "runtime-ai-agent-registered-${UUID.randomUUID()}").canonicalFile
            check(root.mkdir())
            try {
                val bun = mode == "bun"; val entry = if (bun) "main.bun.js" else "main.cjs"
                val config = ProjectConfig().setName("runtime-ai-agent-fixture").setMainScriptFileName(entry).setProjectType(if (bun) "bun" else "node")
                if (bun) config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(RuntimeAgentPrograms.grants).setTimeoutMs(120000))
                else config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(RuntimeAgentPrograms.capabilities).setTimeoutMs(120000))
                val project = JSONObject(config.toJson(false)).put("agent", JSONObject()
                    .put("description", "runtime-ai-agent-fixture")
                    .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()
                        .put("value", JSONObject().put("type", "integer"))).put("required", JSONArray().put("value")).put("additionalProperties", false))
                    .put("risk", "readonly").put("confirm", "never").put("timeoutMs", 120000))
                assertNotNull(org.autojs.autojs.project.AgentManifestParser.parseProject(project.toString(), "runtime-ai-agent-fixture"))
                File(root, "project.json").writeText(project.toString())
                val content = """
                    const context=await ai.agent.context();if(context.parameters.value!==7||context.presetName!=='runtime-fixture')throw Error('Registered context');
                    context.parameters.value=99;if((await ai.agent.context()).parameters.value!==7)throw Error('Context is not a snapshot');
                    if(!await ai.agent.result({discarded:true}))throw Error('First report');
                    if(!await ai.agent.result({runId:context.runId,value:14,unicode:'\u96ea\ud83e\uddea'.repeat(3000)}))throw Error('Final report');
                    await ai.agent.close();
                """.trimIndent()
                val body = when (mode) {
                    "bun" -> "const {createHostClient}=await import(process.env.AUTOJS6_HOST_CLIENT_MODULE),host=await createHostClient(),ai=host.ai;$content"
                    "node" -> "const ai=require('ai');$content"
                    else -> {
                        val child = "const {parentPort}=require('node:worker_threads'),ai=require('ai');(async()=>{$content})().then(()=>parentPort.postMessage('done')).catch(e=>{throw e;});"
                        "const {Worker}=require('node:worker_threads');const w=new Worker(${JSONObject.quote(child)},{eval:true});try{await new Promise((r,j)=>{w.once('message',r);w.once('error',j);});}finally{await w.terminate();}"
                    }
                }
                val file = File(root, entry)
                file.writeText("\"${if (bun) "bun" else "nodejs"}\";(async()=>{$body})().catch(e=>{console.error(e.stack||e);process.exitCode=1;});")
                val id = "registered-${UUID.randomUUID()}"
                val result = AgentOwnedScriptExecution.registered(root.path, root.path, file.path, "{\"value\":7}", id, "runtime-fixture", 120000, false) { false }.execute()
                assertEquals(result.toString(), "success", result.getString("outcome"))
                assertTrue(result.getBoolean("resultReported"))
                val value = result.getJSONObject("result")
                assertEquals(id, value.getString("runId")); assertEquals(14, value.getInt("value"))
                assertEquals("\u96ea\uD83E\uDDEA".repeat(3000), value.getString("unicode"))
                results.put(JSONObject().put("mode", mode).put("runId", id).put("resultReported", true).put("unicodeLength", value.getString("unicode").length))
            } finally { check(root.parentFile == context.cacheDir.canonicalFile); root.deleteRecursively() }
        }
        File(context.getExternalFilesDir(null), "runtime-ai-agent-registered.json").writeText(results.toString(2))
    }

    @Test fun workerModesCancelOwnedRunsButRetainDetachedRunsAndParentClient() = fixture {
        val target = localTarget()
        val result = harness.execute("node", """
            const ai=require('ai'),{Worker}=require('node:worker_threads'),agent=ai.agent,target=${JSONObject.quote(target)};await agent.presets();
            const proofs=[];
            try {
                for(const detached of [false,true]){
                    const body="const run=await ai.agent.run('${RuntimeAgentPrograms.goalPrefix}Write a long detailed explanation of clouds in the final summary.',{target:"+JSON.stringify(target)+",tools:[],memory:false,plan:false,locale:'en',detached:"+detached+",budget:{maxSteps:4,maxModelCalls:6,maxDurationMs:120000,maxTotalTokens:8192}});parentPort.postMessage({id:run.id,state:run.state});setInterval(()=>{},1000);";
                    const cjs="const {parentPort}=require('node:worker_threads'),ai=require('ai');(async()=>{"+body+"})().catch(e=>{throw e;});";
                    const esm="import {parentPort} from 'node:worker_threads';import ai from 'autojs6:ai';"+body;
                    const nested="const {parentPort,Worker}=require('node:worker_threads');const w=new Worker("+JSON.stringify(cjs)+",{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});";
                    for(const [entry,options] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
                        const w=new Worker(entry,options);let created;try{created=await new Promise((r,j)=>{w.once('message',r);w.once('error',j);});}finally{await w.terminate();}
                        const run=await agent.get(created.id);if(!run)throw Error('Worker task disappeared');
                        if(detached){if(run.state==='cancelled')throw Error('Detached task was cancelled on Worker exit');await run.cancel('fixture-finished');}
                        const terminal=await run.join(120000);if(!['cancelled','completed'].includes(terminal.status))throw Error('Worker cleanup: '+terminal.status);
                        proofs.push({id:created.id,detached,status:terminal.status});
                        if(!Array.isArray(await agent.presets()))throw Error('Parent client closed');
                    }
                }
                return {modes:3,proofs,parentPreserved:true};
            }finally{await agent.close();}
        """.trimIndent(), capabilities = RuntimeAgentPrograms.capabilities, timeoutMs = 900000)
        assertEquals(6, result.getJSONArray("proofs").length())
        File(context.getExternalFilesDir(null), "runtime-ai-agent-worker-modes.json").writeText(result.toString(2))
    }
}
