package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real runtime processes, native windows and Android accessibility actions. No fake renderer. */
class RuntimeUiComposeDeviceTest {
    private val harness = CaptureWorkerBridgeDeviceTest()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val modes = listOf("node", "node-esm", "worker", "worker-esm", "worker-nested", "bun")
    private val evidence = JSONArray()

    @Test fun layoutsBindingsAndClicksAcrossRuntimeModes() = grants {
        for (mode in modes) {
            val label = "Layout-$mode"
            interact(mode, """
                const state=ui.state({text:'$label:0'});
                const window=await ui.showLayout({type:'Column',children:[
                    {type:'Text',id:'value',text:ui.bind(state,'text')},{type:'Button',id:'next',text:'$label-next'}]});
                let clicked=0;window.on('click',event=>{if(event.id==='next'){clicked++;state.set('text','$label:1');}});
                const end=Date.now()+20000;while(!clicked&&Date.now()<end)await new Promise(r=>setTimeout(r,30));
                if(clicked!==1)throw Error('Missing layout callback');await new Promise(r=>setTimeout(r,500));
                await window.close();if(!window.closed)throw Error('Closed state');return {clicked,closed:window.closed};
            """.trimIndent(), "$label-next")
        }
        write("layouts")
    }

    @Test fun composeClicksTextInputReactiveFramesAndRefsAcrossRuntimeModes() = grants {
        for (mode in modes) {
            val label = "Compose-$mode"
            interact(mode, """
                if(!await compose.isAvailable())throw Error('Compose provider unavailable');
                const count=compose.state(0),text=compose.state('$label-input'),ref=compose.ref();let edits=0;
                const window=await compose.mount(()=>compose.Column({padding:16},
                    compose.Text({text:'$label:'+count.value,ref}),
                    compose.TextField({text:text.value,singleLine:true,onValueChange(e){edits++;text.value=e.text;}}),
                    compose.Button({onClick(){compose.batch(()=>{count.value++;count.value++;});}},compose.Text({text:'$label-next'}))));
                const end=Date.now()+25000;while((count.value!==2||text.value!=='$label-edited')&&Date.now()<end)await new Promise(r=>setTimeout(r,30));
                await window.flush();if(count.value!==2||edits<1||text.value!=='$label-edited'||!ref.current)throw Error('Compose state/input/ref '+JSON.stringify({count:count.value,edits,text:text.value,ref:!!ref.current}));
                await new Promise(r=>setTimeout(r,300));
                await window.close();if(!window.isClosed()||ref.current!==null||compose.sessions.length)throw Error('Compose close');
                return {count:count.value,edits,text:text.value,closed:window.isClosed(),version:await compose.version()};
            """.trimIndent(), "$label-next", "$label-input" to "$label-edited")
            harness.await("Compose sessions released") { RuntimeComposeActivity.sessions.isEmpty() }
        }
        write("compose")
    }

    @Test fun dialogsValidationAndOwnershipRemainExecutionScoped() = grants {
        for (mode in modes) {
            val result = run(mode, """
                async function rejects(fn){try{await fn();}catch(e){return true;}throw Error('Expected rejection');}
                await rejects(()=>compose.mount(compose.Text({unknownProperty:1})));
                const dialog=await compose.dialog(compose.Text({text:'Dialog-$mode'}));
                await dialog.setTheme({dark:true,seed:'#336699'});await new Promise(r=>setTimeout(r,150));
                await dialog.close();return {closed:dialog.isClosed(),sessions:compose.sessions.length};
            """.trimIndent())
            assertTrue(result.toString(), result.getBoolean("closed")); assertEquals(0, result.getInt("sessions"))
            evidence.put(JSONObject().put("mode", mode).put("result", result))
        }
        BunHostCapabilityGrants.setUiLayoutGranted(context, false)
        BunHostCapabilityGrants.setUiComposeGranted(context, false)
        val denied = harness.execute("bun", """
            eq((await rejects(()=>host.compose.isAvailable())).code,'NOT_GRANTED','Compose grant');
            eq((await rejects(()=>host.ui.showLayout({type:'Text',text:'denied'}))).code,'NOT_GRANTED','UI grant');
            await rejects(()=>host.call('image.process',{method:'mount',args:[]}));return {denied:true};
        """.trimIndent(), capabilities=listOf("ui", "image"))
        assertTrue(denied.getBoolean("denied"));write("ownership")
    }

    @Test fun terminatingWorkerClosesItsWindowWithoutEndingMain() = grants {
        val worker = "const {parentPort}=require('node:worker_threads');const c=require('compose');c.mount(c.Text({text:'Worker-stop-fixture'})).then(s=>parentPort.postMessage(s.id));"
        val result = harness.execute("node", """
            const {Worker}=require('node:worker_threads');const compose=require('compose');
            const parent=await compose.mount(compose.Text({text:'Parent-survives'}));
            const worker=new Worker(${JSONObject.quote(worker)},{eval:true});const id=await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});
            await worker.terminate();await pause(500);
            await rejects(()=>require('autojs6:bridge').callAutoJs('compose','render',[id,{}],{permissions:['ui']}));
            await parent.render(compose.Text({text:'Parent-still-alive'}));await parent.close();return {workerReleased:true,parentUsable:true};
        """.trimIndent(), capabilities=listOf("ui", "image"))
        assertTrue(result.toString(), result.getBoolean("workerReleased"));
        harness.await("Worker window cleanup") { RuntimeComposeActivity.sessions.isEmpty() };write("worker-cleanup")
    }

    @Test fun floatingComposeWindowsRenderAndCloseAcrossRuntimeModes() = grants {
        assertTrue("Existing Android overlay grant is required", android.provider.Settings.canDrawOverlays(context))
        for(mode in modes) {
            val label="Floating-$mode"
            interact(mode,"""
                let clicked=0;
                const window=await compose.floaty(compose.Button({onClick(){clicked++;}},compose.Text({text:'$label-next'})),{x:20,y:120,width:360,height:160});
                await window.setPosition(40,140);await window.setSize(400,180);await window.disableFocus();await window.setTouchable(true);
                await new Promise(r=>setTimeout(r,200));
                if(await window.getX()!==40||await window.getY()!==140||await window.getWidth()!==400||await window.getHeight()!==180)throw Error('Floating geometry');
                const end=Date.now()+20000;while(!clicked&&Date.now()<end)await new Promise(r=>setTimeout(r,30));
                if(clicked!==1)throw Error('Floating click');await window.close();return {clicked,closed:window.isClosed()};
            """.trimIndent(),"$label-next")
        }
        harness.await("Floating window cleanup") { RuntimeComposeActivity.sessions.isEmpty() };write("floaty")
    }

    @Test fun snackbarActionsReachTheCreatingRuntime() = grants {
        for(mode in modes) {
            val label="Snackbar-$mode"
            interact(mode,"""
                let actions=0;
                const window=await compose.mount(compose.Scaffold({slots:{content:compose.Text({text:'$label'})}}));
                await window.showSnackbar('Message-$mode',{actionLabel:'$label-next',duration:'indefinite',onAction(){actions++;}});
                const end=Date.now()+20000;while(!actions&&Date.now()<end)await new Promise(r=>setTimeout(r,30));
                if(actions!==1)throw Error('Snackbar callback');await window.close();return {actions,closed:window.isClosed()};
            """.trimIndent(),"$label-next")
        }
        write("snackbar")
    }

    @Test fun composeStateAndTextSurviveActivityRotation() = grants {
        harness.withFixture {
            for(mode in listOf("node","worker","bun")) {
                harness.automation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0)
                val display=(context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
                harness.await("Portrait display") { display.rotation==android.view.Surface.ROTATION_0 }
                val label="Rotation-$mode"
                val executor=Executors.newSingleThreadExecutor()
                try {
                    val future=executor.submit<JSONObject>{run(mode,"""
                        const count=compose.state(0),text=compose.state('$label-input');
                        const window=await compose.mount(()=>compose.Column({padding:16},compose.Text({text:'$label:'+count.value}),
                            compose.TextField({text:text.value,singleLine:true,onValueChange(e){text.value=e.text;}}),
                            compose.Button({onClick(){count.value++;}},compose.Text({text:'$label-next'}))));
                        const end=Date.now()+30000;while(count.value<2&&Date.now()<end)await new Promise(r=>setTimeout(r,30));
                        await window.flush();if(count.value!==2||text.value!=='$label-edited')throw Error('State lost on rotation');
                        await window.close();return {count:count.value,text:text.value,closed:window.isClosed()};
                    """.trimIndent())}
                    harness.await("Rotation text input") { if(future.isDone)fail(future.get().toString());find("$label-input")!=null }
                    assertTrue(requireNotNull(find("$label-input")).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"$label-edited")}))
                    fun click() {harness.await("Rotation button ready") { find("$label-next")!=null };var node=requireNotNull(find("$label-next"));while(!node.isClickable&&node.parent!=null)node=node.parent;assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))}
                    click();harness.await("State before rotation") { find("$label:1")!=null }
                    val instrumentation=InstrumentationRegistry.getInstrumentation()
                    val monitor=instrumentation.addMonitor(RuntimeComposeActivity::class.java.name,null,false)
                    try {
                        harness.automation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
                        assertNotNull("Compose Activity was recreated",monitor.waitForActivityWithTimeout(10000))
                        assertEquals(android.view.Surface.ROTATION_90,display.rotation)
                    } finally {instrumentation.removeMonitor(monitor)}
                    harness.await("State after rotation") { find("$label:1")!=null&&find("$label-edited")!=null&&find("$label-next")!=null }
                    click();val result=future.get(35,TimeUnit.SECONDS);evidence.put(JSONObject().put("mode",mode).put("result",result))
                    harness.automation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0)
                } finally {executor.shutdownNow()}
            }
        }
        write("rotation")
    }

    private fun interact(mode: String, code: String, click: String, edit: Pair<String, String>? = null) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<JSONObject> { run(mode, code) }
            harness.await("$mode native window", 25000) { if(result.isDone) fail(result.get().toString()); find(click) != null }
            edit?.let { (before, after) ->
                val node = requireNotNull(find(before))
                assertTrue("Native text edit", node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, after) }))
                harness.await("$mode text updated") { find(after) != null }
            }
            var target = requireNotNull(find(click))
            while (!target.isClickable && target.parent != null) target = target.parent
            assertTrue("Native click: $click", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val value = result.get(35, TimeUnit.SECONDS)
            evidence.put(JSONObject().put("mode", mode).put("result", value))
        } finally { executor.shutdownNow() }
    }

    private fun find(text: String): AccessibilityNodeInfo? {
        fun walk(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) walk(node.getChild(i))?.let { return it }
            return null
        }
        return harness.automation.windows.firstNotNullOfOrNull { walk(it.root) } ?: walk(harness.automation.rootInActiveWindow)
    }

    private fun run(mode: String, code: String): JSONObject {
        val code = "try{$code}finally{for(const session of compose.sessions)await session.close().catch(()=>{});}"
        val body = when(mode) {
            "bun" -> "const ui=host.ui,compose=host.compose;$code"
            "node" -> "const ui=require('ui'),compose=require('compose');$code"
            "node-esm" -> "const ui=(await import('autojs6:ui')).default,compose=(await import('autojs6:compose')).default;$code"
            else -> {
                val worker = if(mode=="worker-esm") "import ui from 'ui';import compose from 'compose';import {parentPort} from 'node:worker_threads';parentPort.postMessage(await(async()=>{$code})());"
                    else "const ui=require('ui'),compose=require('compose'),{parentPort}=require('node:worker_threads');(async()=>{$code})().then(v=>parentPort.postMessage(v));"
                val entry = if(mode=="worker-nested") "const {Worker,parentPort}=require('node:worker_threads');new Worker(${JSONObject.quote(worker)},{eval:true}).on('message',v=>parentPort.postMessage(v)).on('error',e=>{throw e;});" else worker
                "const {Worker}=require('node:worker_threads');return await new Promise((resolve,reject)=>{let value;const w=new Worker("+
                    (if(mode=="worker-esm") "new URL('data:text/javascript,'+encodeURIComponent(${JSONObject.quote(entry)}))" else "${JSONObject.quote(entry)},{eval:true}")+
                    ");w.on('message',v=>value=v);w.on('error',reject);w.on('exit',c=>c?reject(Error('Worker exit '+c)):resolve(value));});"
            }
        }
        return harness.execute(if(mode=="bun") "bun" else "node", body, capabilities=listOf("ui", "image"))
    }
    private fun grants(action: () -> Unit) {
        val prefs=context.getSharedPreferences("bun_host_capability_grants", Context.MODE_PRIVATE)
        val keys=listOf(BunHostCapabilityGrants.UI_LAYOUT, BunHostCapabilityGrants.UI_COMPOSE)
        val before=keys.associateWith { if(prefs.contains(it)) prefs.getBoolean(it,false) else null }
        prefs.edit().apply { keys.forEach { putBoolean(it,true) } }.commit()
        try { action() } finally { prefs.edit().apply { before.forEach { (key,value)->if(value==null)remove(key)else putBoolean(key,value) } }.commit() }
    }
    private fun write(name: String) { File(context.getExternalFilesDir(null), "runtime-ui-compose-$name.json").writeText(evidence.toString(2)) }
}
