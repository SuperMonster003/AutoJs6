package org.autojs.autojs.core.plugin.agent

import android.content.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.plugin.three.stove.agent.api.*
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract as C
import org.autojs.plugin.host.capability.api.HostCapabilityContract as H
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Public Rhino API and installed production plugin; only model decisions are deterministic fixtures. */
class ThreeStoveAgentPublicApiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val pkg = "io.github.supermonster003.autojs6.plugin.three.stove.agent"
    private fun bundle(key: String, value: JSONObject) = Bundle().apply { putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION); putString(key, value.toString()) }
    private fun done() = JSONObject("""{"kind":"done","done":{"status":"completed","summary":"Verified fixture","evidence":["Observed fixture reply"]}}""")
    private inner class Model(private val decide: (Int, JSONObject) -> JSONObject?) : IThreeStoveAgentModelBroker.Stub() {
        val calls = AtomicInteger(); val cancellations = AtomicInteger()
        @Volatile var held: Pair<String, IThreeStoveAgentModelCallback>? = null
        override fun getBrokerInfo() = bundle(C.KEY_MODEL_BROKER_INFO_JSON, JSONObject("""{"available":true,"providerId":"conformance","maximumInputBytes":131072,"maximumOutputBytes":65536,"maximumResponseSchemaBytes":16384}""")).apply {
            putString(H.KEY_GRANT_JSON, """{"maxInputBytesPerRequest":131072,"maxTotalTokens":1000000,"consumedTokens":0}""")
        }
        fun event(callback: IThreeStoveAgentModelCallback, id: String, type: String, sequence: Int, value: JSONObject = JSONObject()) {
            callback.onEvent(bundle(C.KEY_MODEL_EVENT_JSON, value.put("requestId", id).put("type", type).put("sequence", sequence)))
        }
        override fun listTargets(request: Bundle, callback: IThreeStoveAgentModelCallback) {
            val id=JSONObject(request.getString(C.KEY_MODEL_REQUEST_JSON)!!).getString("requestId")
            event(callback,id,"started",1)
            event(callback,id,"completed",2,JSONObject().put("targets",JSONArray().put(JSONObject("""{"targetId":"conformance:scripted","displayName":"Scripted","locality":2,"configured":true,"available":true,"maximumContextBytes":131072,"capabilityIds":[],"supportedControls":["maximum-output-tokens"]}"""))))
        }
        override fun generate(request: Bundle, callback: IThreeStoveAgentModelCallback) {
            val value=JSONObject(request.getString(C.KEY_MODEL_REQUEST_JSON)!!); val id=value.getString("requestId")
            held=id to callback; event(callback,id,"started",1)
            decide(calls.getAndIncrement(),value)?.let { finish(it) }
        }
        fun finish(value: JSONObject = done()) { held?.let { (id,cb) -> event(cb,id,"completed",2,JSONObject().put("text",value.toString()).put("targetId","conformance:scripted").put("finishReason",0)) } }
        override fun cancel(reference: Bundle?) { cancellations.incrementAndGet() }
        override fun destroy(reason: Bundle?) = Unit
        override fun submitToolResults(request: Bundle?) { error("Scripted JSON model does not advertise tool calling") }
    }
    private fun withPlugin(model: Model, action: (ThreeStoveAgentLinkController, File) -> Unit) {
        assumeTrue("Opt in: -e autojs.agent.plugin true", InstrumentationRegistry.getArguments().getString("autojs.agent.plugin").toBoolean())
        instrumentation.runOnMainSync { AutoJs.instance.infoProvider }
        val ready=CountDownLatch(1); var api: IThreeStoveAgentPlugin?=null
        val connection=object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { api=IThreeStoveAgentPlugin.Stub.asInterface(binder); ready.countDown() }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        check(context.bindService(Intent().setComponent(ComponentName(pkg,"$pkg.ThreeStoveAgentPluginService")),connection,Context.BIND_AUTO_CREATE))
        val singleton=ThreeStoveAgentLinkController::class.java.getDeclaredField("instance").apply { isAccessible=true }
        val old=singleton.get(null)
        val work=File(context.cacheDir,"agent-p5-${UUID.randomUUID()}").apply { check(mkdirs()) }
        var controller: ThreeStoveAgentLinkController?=null
        try {
            assertTrue(ready.await(15,TimeUnit.SECONDS))
            controller=ThreeStoveAgentLinkController(context,available={true},uid={context.packageManager.getApplicationInfo(pkg,0).uid},
                attachLink={config,brokers,callback -> AidlPluginHost.LeasedCall(api!!.attach(config,model,brokers.capabilityBroker,callback),org.autojs.autojs.core.plugin.ServiceBindingLease {}.apply { markRegistered() }) })
            singleton.set(null,controller)
            runBlocking { controller.attach() }
            action(controller,work)
        } finally {
            runBlocking { controller?.detach("test-finished") }
            singleton.set(null,old)
            context.unbindService(connection)
            // Only this UUID directory under the application's private cache is owned by the fixture.
            assertEquals(context.cacheDir.canonicalFile,work.canonicalFile.parentFile)
            work.deleteRecursively()
        }
    }
    @Test fun pluginProcessDeathFailsEveryHostOwnedHandle() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("autojs.agent.lifecycle").toBoolean())
        assertTrue("Private AVD only", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val model = Model { _, _ -> null }
        withPlugin(model) { controller, _ ->
            val handles = (0..2).map { controller.scriptRuns.start("Lifecycle fixture", com.google.gson.JsonObject()) }
            waitFor("First model call") { model.calls.get() == 1 }
            instrumentation.uiAutomation.executeShellCommand("am force-stop $pkg").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
            }
            waitFor("All host handles fail") { handles.all { it.settled } }
            handles.forEach { handle ->
                assertEquals("failed", handle.state); assertNull(handle.result)
                assertEquals(C.ERROR_PLUGIN_UNAVAILABLE, handle.failure!!["code"].asString)
            }
            assertEquals(1, model.calls.get())
        }
    }
    private class Running(val execution: ScriptExecution, val finished: CountDownLatch, val failure: AtomicReference<Throwable?>) {
        fun await() {
            assertTrue("Public script body finishes",finished.await(30,TimeUnit.SECONDS))
            val deadline=SystemClock.elapsedRealtime()+30000
            while (!execution.engine.isDestroyed && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(20)
            assertTrue("Script event loop finishes",execution.engine.isDestroyed)
            failure.get()?.let { throw AssertionError("Public Agent script failed",it) }
        }
    }
    private fun start(work: File, code: String, arguments: Map<String, Any> = emptyMap(), name: String = "test-${UUID.randomUUID()}.js"): Running {
        val source=File(work,name).apply { writeText(code) }
        val latch=CountDownLatch(1); val error=AtomicReference<Throwable?>()
        val execution=AutoJs.instance.scriptEngineService.execute(JavaScriptFileSource(source),object: SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution,result: Any?) { latch.countDown() }
            override fun onException(execution: ScriptExecution,failure: Throwable) { error.set(failure); latch.countDown() }
        },ExecutionConfig(workingDirectory=work.path).apply { arguments.forEach { (key,value) -> setArgument(key,value) } })
        return Running(execution,latch,error)
    }
    private fun waitFor(label: String, predicate: () -> Boolean) {
        val end=SystemClock.elapsedRealtime()+15000
        while (!predicate() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(20)
        assertTrue(label,predicate())
    }

    @Test fun publicJoinPumpsProgressInputAndConfirmationOnTheScriptThread() {
        val model=Model { step,request -> when(step) {
            0 -> JSONObject("""{"kind":"tool","tool":"report_progress","arguments":{"message":"fixture progress"}}""")
            1 -> JSONObject("""{"kind":"ask","ask":{"kind":"text","question":"How many days?"}}""")
            2 -> { check(request.toString().contains("30")); JSONObject("""{"kind":"tool","tool":"ui_press_key","arguments":{"key":"home"}}""") }
            else -> { check(request.toString().contains("USER_DENIED")); done() }
        } }
        withPlugin(model) { _,work ->
            start(work,"""
                let thread = java.lang.Thread.currentThread().getId();
                let events = [], onceCount = 0, removed = 0;
                let run = ai.agent.create({target:'conformance:scripted',interaction:'script',confirm:'cautious',tools:['user','act']}).run('Controlled fixture');
                function checkThread() { if (java.lang.Thread.currentThread().getId() !== thread) throw Error('wrong thread'); }
                function unused() { removed++; }
                run.on('progress', unused).off('progress', unused);
                run.once('progress', function(e) { checkThread(); onceCount++; events.push('progress'); });
                run.on('input', function(e) { checkThread(); if(e.readOnly) throw Error('readonly'); events.push('input'); run.respond(e.requestId,'30'); });
                run.on('confirmation', function(e) { checkThread(); events.push('confirmation'); run.confirm(e.requestId,false); });
                run.on('done', function(e) { checkThread(); events.push('done'); });
                let result = run.join(20000);
                if(result.status !== 'completed' || onceCount !== 1 || removed !== 0) throw Error('bad result');
                run.result.then(function(r) { files.write('result.json',JSON.stringify({status:r.status,events:events,id:run.id,state:run.state})); });
            """.trimIndent()).await()
            val result=JSONObject(File(work,"result.json").readText())
            assertEquals("completed",result.getString("status")); assertEquals("completed",result.getString("state"))
            assertEquals(listOf("progress","input","confirmation","done"),result.getJSONArray("events").let { (0 until it.length()).map(it::getString) })
        }
    }
    @Test fun pluginInteractionIsReadOnlyToScriptsAndCancellationResolves() = withPlugin(Model { _,_ ->
        JSONObject("""{"kind":"ask","ask":{"kind":"text","question":"Plugin input"}}""")
    }) { controller,work ->
        val running=start(work,"""
            let run=ai.agent.run('Plugin interaction fixture',{target:'conformance:scripted',tools:['user']});
            run.on('input',function(e){
                if(!e.readOnly) throw Error('mutable notification');
                let denied=false; try { run.respond(e.requestId,'forbidden'); } catch(err) { denied=err.code==='RUN_NOT_INTERACTIVE'; }
                if(!denied) throw Error('script responded to plugin interaction');
                files.write('pending.json',JSON.stringify({runId:run.id,requestId:e.requestId,value:'forbidden'}));
            });
            run.result.then(function(r){ files.write('result.json',JSON.stringify(r)); });
        """.trimIndent())
        waitFor("Plugin-owned input ready") { File(work,"pending.json").exists() }
        val response=JSONObject(File(work,"pending.json").readText())
        val denied=runBlocking { controller.withLink { it.respond(bundle(C.KEY_RUN_RESPONSE_JSON,response)) } }
        assertEquals(C.ERROR_RUN_NOT_INTERACTIVE,denied.getString(C.KEY_ERROR_CODE))
        runBlocking { controller.withLink { it.cancelRun(bundle(C.KEY_RUN_REF_JSON,JSONObject().put("runId",response.getString("runId")))) } }
        running.await()
        assertEquals("cancelled",JSONObject(File(work,"result.json").readText()).getString("status"))
    }
    @Test fun detachedTaskSurvivesScriptExitAndCanBeObservedByANewScript() {
        val model=Model { _,_ -> null }
        withPlugin(model) { _,work ->
            start(work,"""let r=ai.agent.run('Detached fixture',{target:'conformance:scripted',tools:['user'],detached:true}); files.write('id.txt',r.id);""").await()
            waitFor("Detached model started") { model.calls.get()==1 }
            val id=File(work,"id.txt").readText()
            val next=start(work,"""
                let r=ai.agent.get('${id}');
                if(!r || !r.detached) throw Error('missing detached task');
                r.result.then(function(result){ files.write('result.json',JSON.stringify(result)); });
                files.write('observing.txt','yes');
            """.trimIndent())
            waitFor("New script observes task") { File(work,"observing.txt").exists() }
            assertFalse("Explicit result observation keeps the new script alive",next.execution.engine.isDestroyed)
            model.finish(); next.await()
            assertEquals("completed",JSONObject(File(work,"result.json").readText()).getString("status"))
        }
    }
    @Test fun stoppingAnOwningScriptCancelsItsNonDetachedTask() {
        val model=Model { _,_ -> null }
        withPlugin(model) { controller,work ->
            val running=start(work,"""let r=ai.agent.run('Owned fixture',{target:'conformance:scripted',tools:['user']}); files.write('id.txt',r.id);""")
            try {
                waitFor("Model started") { model.calls.get()==1 && File(work,"id.txt").exists() }
                val id=File(work,"id.txt").readText()
                running.execution.engine.forceStop()
                waitFor("Non-detached task cancelled") { controller.scriptRuns.get(id)?.state=="cancelled" }
                assertTrue(model.cancellations.get()>0)
            } finally { running.execution.engine.forceStop() }
        }
    }
    @Test fun unavailablePluginReturnsRejectedHandleAndArgumentsStillThrowSynchronously() = withPlugin(Model { _,_ -> done() }) { controller,work ->
        runBlocking { controller.detach() }
        start(work,"""
            let invalid=0;
            for(let f of [function(){ai.agent.run('');},function(){ai.agent.run('x',{typo:true});},function(){ai.agent.run('x',{detached:1});}]) { try{f();}catch(e){invalid++;} }
            if(invalid!==3) throw Error('missing argument validation');
            let r=ai.agent.run('Unavailable fixture');
            if(r.state!=='failed' || r.error.code!=='PLUGIN_UNAVAILABLE') throw Error('wrong unavailable state');
            r.result.then(function(){throw Error('unexpected resolution');},function(e){files.write('result.json',JSON.stringify(e));});
        """.trimIndent()).await()
        val result=JSONObject(File(work,"result.json").readText())
        assertEquals("PLUGIN_UNAVAILABLE",result.getString("code")); assertTrue(result.getString("hint").contains("drawer"))
    }

    @Test fun queriesAssistantSnapshotsAndTerminalGetUsePublicJavaScriptValues() = withPlugin(Model { _,_ -> done() }) { _,work ->
        start(work,"""
            let assistant=ai.agent.create({target:'conformance:scripted',tools:['user'],budget:{maxSteps:5}});
            let snapshot=assistant.options; snapshot.budget.maxSteps=99;
            if(assistant.options.budget.maxSteps!==5) throw Error('options are shared');
            let denied=false; try{assistant.run('x',{budget:{maxSteps:6}});}catch(e){denied=true;}
            if(!denied) throw Error('assistant budget expanded');
            let r=assistant.run('Query fixture');
            r.result.then(function(result){
                if(ai.agent.get(r.id)!==r) throw Error('unstable handle identity');
                if(ai.agent.get('00000000-0000-0000-0000-000000000000')!==null) throw Error('missing get');
                return ai.agent.list({state:'completed',preset:'default',since:r.startedAt,limit:50});
            }).then(function(rows){
                if(!Array.isArray(rows) || !rows.some(function(v){return v.id===r.id;})) throw Error('missing list entry');
                return ai.agent.presets();
            }).then(function(names){
                if(names.indexOf('default')<0) throw Error('missing preset');
                return ai.agent.catalog('not-a-fixture-registration');
            }).then(function(entries){
                if(!Array.isArray(entries)) throw Error('catalog is not array');
                files.write('queries.txt',ai.agent.status().state);
            });
        """.trimIndent()).await()
        assertEquals("attached",File(work,"queries.txt").readText())
    }

    @Test fun joinTimeoutIsCatchableAndDoesNotCancelTheTask() {
        val model=Model { _,_ -> null }
        withPlugin(model) { _,work ->
            val running=start(work,"""
                let r=ai.agent.run('Timeout fixture',{target:'conformance:scripted',tools:['user']});
                let timeout=false;try{r.join(100);}catch(e){timeout=e.code==='JOIN_TIMEOUT';}
                if(!timeout || r.state==='cancelled') throw Error('wrong join timeout');
                files.write('waiting.txt','yes');
                r.result.then(function(result){files.write('result.json',JSON.stringify(result));});
            """.trimIndent())
            waitFor("Timed out join continues observing") { File(work,"waiting.txt").exists() && model.calls.get()==1 }
            assertEquals(0,model.cancellations.get()); model.finish(); running.await()
            assertEquals("completed",JSONObject(File(work,"result.json").readText()).getString("status"))
        }
    }

    @Test fun linkLossRejectsTheHandleWithoutResolvingAnInventedTaskResult() {
        val model=Model { _,_ -> null }
        withPlugin(model) { controller,work ->
            val running=start(work,"""
                let r=ai.agent.run('Link loss fixture',{target:'conformance:scripted',tools:['user']});
                r.result.then(function(){throw Error('invented task result');},function(e){files.write('error.json',JSON.stringify(e));});
            """.trimIndent())
            waitFor("Model active before detach") { model.calls.get()==1 }
            runBlocking { controller.detach() }; running.await()
            assertEquals("LINK_DETACHED",JSONObject(File(work,"error.json").readText()).getString("code"))
        }
    }

    @Test fun uiThreadJoinIsRejectedAndCatalogWorksWithoutAttachment() = withPlugin(Model { _,_ -> done() }) { controller,work ->
        runBlocking { controller.detach() }
        start(work,"""
            ui.run(function(){
                let r=ai.agent.run('UI fixture');
                let denied=false;try{r.join(10);}catch(e){denied=String(e.message).indexOf('UI thread')>=0;}
                if(!denied) throw Error('UI join was allowed');
                r.result.catch(function(){});
            });
            ai.agent.catalog('not-a-fixture-registration').then(function(entries){files.write('catalog.txt',String(Array.isArray(entries)));});
        """.trimIndent()).await()
        assertEquals("true",File(work,"catalog.txt").readText())
    }

    @Test fun stoppingAnObserverDoesNotCancelAnotherScriptsTask() {
        val model=Model { _,_ -> null }
        withPlugin(model) { controller,work ->
            val owner=start(work,"""let r=ai.agent.run('Owner',{target:'conformance:scripted',tools:['user']}); files.write('id.txt',r.id);""")
            try {
                waitFor("Owner active") { model.calls.get()==1 && File(work,"id.txt").exists() }
                val id=File(work,"id.txt").readText()
                val observer=start(work,"""let r=ai.agent.get('$id'); files.write('observer.txt',r.state);""")
                waitFor("Observer active") { File(work,"observer.txt").exists() }
                observer.execution.engine.forceStop()
                waitFor("Observer stopped") { observer.execution.engine.isDestroyed }
                assertEquals(0,model.cancellations.get())
                assertEquals("running",controller.scriptRuns.get(id)?.state)
                model.finish(); owner.await()
            } finally { owner.execution.engine.forceStop() }
        }
    }

    @Test fun bundledExamplesAreDiscoverableAndRunThroughThePublicApi() {
        val catalog=org.autojs.autojs.engine.NodeBridgeSampleCatalog(
            { context.assets.list(it) }, { path -> runCatching { context.assets.open(path) }.getOrNull() })
        val names=setOf("agent-run.js","agent-input.js","agent-detached.js")
        assertTrue(catalog.list("ai").entries.map { it.name }.containsAll(names))
        fun source(name: String)=catalog.read("ai/$name").content
        withPlugin(Model { _,_ -> done() }) { _,work ->
            start(work,source("agent-run.js"),mapOf("target" to "conformance:scripted","output" to File(work,"run.json").path)).await()
            assertEquals("completed",JSONObject(File(work,"run.json").readText()).getString("status"))
        }
        withPlugin(Model { step,request -> if(step==0) JSONObject("""{"kind":"ask","ask":{"kind":"text","question":"Name?"}}""")
            else { check(request.toString().contains("Sample reader")); done() } }) { _,work ->
            start(work,source("agent-input.js"),mapOf("target" to "conformance:scripted","answer" to "Sample reader","output" to File(work,"input.json").path)).await()
            assertEquals("completed",JSONObject(File(work,"input.json").readText()).getString("status"))
        }
        val model=Model { _,_ -> null }
        withPlugin(model) { _,work ->
            val name="agent-detached.js"; val code=source(name)
            start(work,code,mapOf("target" to "conformance:scripted"),name).await()
            waitFor("Detached sample still active after exit") { model.calls.get()==1 && File(work,"$name.run-id").exists() }
            val observer=start(work,code,mapOf("observe" to true,"output" to File(work,"detached.json").path),name)
            model.finish(); observer.await()
            assertEquals("completed",JSONObject(File(work,"detached.json").readText()).getString("status"))
        }
    }
}
