package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.autojs.core.plugin.compose.ComposeSessionRenderer
import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.core.plugin.compose.ScriptUiDispatcher
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiErrorCodes as Codes
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** Adversarial script inputs leave the last accepted frame/handles intact. */
class ComposeRobustnessTest {
    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private lateinit var platform: Platform
    private lateinit var compose: Compose

    @Before fun enter() {
        context = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
        with(RhinoUtils) { context.registerStandardObjects(scope) }
        platform = Platform()
        compose = Compose(RhinoTestRuntime.create(scope), platform, ComposeScriptValues(colorParser = {
            Colors.toFullHexRhino(it).substring(1).toLong(16).toInt()
        }))
        compose.augment(scope.globalThis)
    }
    @After fun exit() { try { compose.close() } finally { Context.exit() } }

    @Test fun fiveThousandAndOneNodesAndDepthSixtyFiveCannotReplaceTheAcceptedFrame() {
        evaluate("var bad=false; var page=compose.mount(function(){return compose.Text({text:'safe'});});")
        platform.dispatcher.drain()
        val before = platform.entries.single().batches.size
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("""
            compose.batch(function(){
                var groups=[], group=[];
                for(var i=0;i<4997;i++) {
                    group.push(compose.Text({text:String(i)}));
                    if(group.length===2000){groups.push(compose.Column({},group));group=[];}
                }
                if(group.length)groups.push(compose.Column({},group));
                compose.mount(compose.Column({},groups));
            })
        """))
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("""
            compose.batch(function(){
                var node=compose.Text({text:'deep'});
                for(var i=0;i<64;i++)node=compose.Column({},node);
                compose.mount(node);
            })
        """))
        platform.dispatcher.drain()
        assertEquals(before, platform.entries.first().batches.size)
        assertEquals("safe", evaluate("page.root.text"))
        assertFalse(Context.toBoolean(evaluate("page.isClosed()")))
    }

    @Test fun overlongStringsAndInvalidValuesRollbackOtherMutationsInTheSameBatch() {
        evaluate("var text=compose.Text({text:'before'});var page=compose.mount(text);")
        platform.dispatcher.drain()
        val invalid = listOf(
            "text.text=new Array(65538).join('x')" to Codes.LIMIT_EXCEEDED,
            "text.color='#12345'" to Codes.INVALID_ARGUMENT,
            "text.fontSize='-1sp'" to Codes.INVALID_ARGUMENT,
            "text.textAlign='unsupported-alignment'" to Codes.INVALID_ARGUMENT,
        )
        for ((operation, code) in invalid) {
            assertEquals(code, failureCode("compose.batch(function(){text.text='partial';$operation;})"))
            assertEquals("before", evaluate("text.text"))
        }
        platform.dispatcher.drain()
        assertEquals(1, platform.entries.single().batches.size)
    }

    @Test fun twoThousandAndOnePropertyPatchesFailWithoutPublishingOrChangingHandles() {
        evaluate("""
            var rows=[], a=[], b=[];
            compose.batch(function(){
                for(var i=0;i<2001;i++){var row=compose.Text({text:'before'});rows.push(row);(i<1000?a:b).push(row);}
            });
            var page=compose.mount(compose.Row({},compose.Column({},a),compose.Column({},b)));
        """)
        platform.dispatcher.drain()
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("compose.batch(function(){rows.forEach(function(row){row.text='after';});})"))
        platform.dispatcher.drain()
        assertEquals(1, platform.entries.single().batches.size)
        assertTrue(Context.toBoolean(evaluate("rows.every(function(row){return row.text==='before';})")))
    }

    @Test fun cyclesAndRenderTimeNestedMountsCannotCreateExtraOwners() {
        evaluate("var child=compose.Column();var root=compose.Column({},child);var page=compose.mount(root);")
        platform.dispatcher.drain()
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("child.append(root)"))
        assertTrue(Context.toBoolean(evaluate("root.parent===null && child.parent===root && child.children.length===0")))
        val initial = platform.entries.size
        for (nested in listOf("mount", "floaty")) {
            assertEquals(Codes.INVALID_ARGUMENT, failureCode("compose.mount(function(){compose.$nested(compose.Text({text:'nested'}));return compose.Text();})"))
            platform.dispatcher.drain()
            assertEquals(initial + 1, platform.entries.size)
            assertEquals(1, platform.entries.last().disposals)
            assertEquals(0, platform.leases)
            assertFalse(Context.toBoolean(evaluate("page.isClosed()")))
            platform.entries.removeLast()
        }
    }

    @Test fun foreignStateWritesLeaveItsValueAndRenderedFrameUnchanged() {
        evaluate("var state=compose.state(0), code=null;var page=compose.mount(function(){return compose.Text({text:String(state.value)});});")
        platform.dispatcher.drain()
        val failure = AtomicReference<Throwable?>()
        Thread {
            val worker = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
            try { worker.evaluateString(scope, "try{state.value=42;}catch(e){code=e.code;}", "worker-robustness.js", 1, null) }
            catch (error: Throwable) { failure.set(error) } finally { Context.exit() }
        }.also { it.start(); it.join(5000); assertFalse(it.isAlive) }
        failure.get()?.let { throw AssertionError(it) }
        assertEquals(Codes.INVALID_ARGUMENT, evaluate("code"))
        assertEquals("0", evaluate("page.root.text"))
        assertEquals(0.0, Context.toNumber(evaluate("state.value")), 0.0)
        assertEquals(1, platform.entries.single().batches.size)
    }

    @Test fun invalidatedPackagesCloseBothKindsAndReleaseTheWatchBeforeNewMounts() {
        evaluate("var ref=compose.ref();var page=compose.mount(compose.Text({ref:ref,text:'old'}));var overlay=compose.floaty(compose.Text({text:'overlay'}));")
        platform.dispatcher.drain()
        assertEquals(2, platform.watches.size)
        platform.invalidate()
        platform.dispatcher.drain()
        assertEquals(0, platform.watches.size)
        assertEquals(0, platform.leases)
        assertTrue(Context.toBoolean(evaluate("page.isClosed() && overlay.isClosed() && ref.current===null")))
        assertEquals(listOf(Codes.PLUGIN_UNAVAILABLE, Codes.PLUGIN_UNAVAILABLE), platform.errors.map { (it as ComposeUiError).code })
        evaluate("var fresh=compose.mount(compose.Text({text:'new'}));")
        platform.dispatcher.drain()
        assertEquals("new", evaluate("fresh.root.text"))
        assertEquals(1, platform.watches.size)
        compose.close()
        assertTrue(platform.watches.isEmpty())
    }

    @Test fun attachFailureClosesOnlyItsSessionAndTheSameScriptScopeCanMountAgain() {
        platform.attachFailure = IllegalStateException("Native view rejected attachment")
        evaluate("var ref=compose.ref(), closed=0;var page=compose.mount(compose.Text({ref:ref,text:'broken'}));page.on('close',function(){closed++;});")
        platform.dispatcher.drain()
        assertTrue(Context.toBoolean(evaluate("page.isClosed() && page.root===null && ref.current===null && closed===1 && compose.sessions.length===0")))
        assertEquals(Codes.RENDERER_FAILED, (platform.errors.single() as ComposeUiError).code)
        assertEquals(1, platform.entries.single().disposals)
        assertTrue(platform.entries.single().batches.isEmpty())
        assertTrue(platform.watches.isEmpty())

        platform.attachFailure = null
        evaluate("var count=compose.state(0);var fresh=compose.mount(function(){return compose.Text({text:String(count.value)});});count.value=42;")
        platform.dispatcher.drain()
        assertEquals("42", evaluate("fresh.root.text"))
        assertFalse(Context.toBoolean(evaluate("fresh.isClosed()")))
        assertEquals(1, platform.errors.size)
    }

    @Test fun aPackageChangeDuringWatchRegistrationKeepsItsUnavailableCodeAndNeverAttaches() {
        platform.invalidateDuringWatch = true
        evaluate("var page=compose.mount(compose.Text({text:'already unavailable'}));")
        platform.dispatcher.drain()
        assertEquals(Codes.PLUGIN_UNAVAILABLE, (platform.errors.single() as ComposeUiError).code)
        assertTrue(Context.toBoolean(evaluate("page.isClosed() && compose.sessions.length===0")))
        assertEquals(0, platform.entries.single().attaches)
        assertEquals(1, platform.entries.single().disposals)
        assertTrue(platform.entries.single().batches.isEmpty())
        assertTrue(platform.watches.isEmpty())
    }

    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "compose-robustness.js", 1, null)
    private fun failureCode(source: String): Any? = evaluate("(function(){try{$source;return null;}catch(error){if(!(error instanceof ComposeError))throw error;return error.code;}})()")

    private class Dispatcher : ScriptUiDispatcher {
        private val origin = Thread.currentThread()
        private val actions = ConcurrentLinkedQueue<() -> Unit>()
        private var closed = false
        override fun isDispatchThread() = Thread.currentThread() === origin
        override fun post(action: () -> Unit) { if (!closed) actions += action }
        fun drain() { var count=0; while(true) { val next=actions.poll() ?: return; check(count++<1000); next() } }
        override fun close() { closed=true;actions.clear() }
    }
    private class Platform : ComposeScriptPlatform {
        override val dispatcher = Dispatcher()
        override val mainExecutor = Executor { it.run() }
        val entries = ArrayList<Entry>()
        val watches = LinkedHashSet<(Throwable) -> Unit>()
        val errors = ArrayList<Throwable>()
        var leases = 0
        var attachFailure: RuntimeException? = null
        var invalidateDuringWatch = false
        override fun version() = mapOf<String,Any>("plugin" to "test", "contract" to 1, "compose" to "test")
        override fun createRenderer(sessionId:Int, beforeMount:(Any)->Unit, onDestroyed:()->Unit) = open(beforeMount,onDestroyed,false)
        override fun createFloatyRenderer(sessionId:Int, options:ComposeFloatyOptions, beforeMount:(Any)->Unit, onDestroyed:()->Unit, onFailure:(Throwable)->Unit) = open(beforeMount,onDestroyed,true)
        private fun open(beforeMount:(Any)->Unit, onDestroyed:()->Unit, floating:Boolean):ComposeScriptPlatform.Opened {
            val entry=Entry(beforeMount, attachFailure).also(entries::add)
            val window=if(floating) Control(onDestroyed) else null
            if(floating)leases++
            return ComposeScriptPlatform.Opened(entry,ComponentCatalog.V1.components.map { it.name }.toSet(),window,
                Closeable {if(floating)leases--},watchInvalidation={ callback ->
                    watches += callback
                    if (invalidateDuringWatch) callback(ComposeUiError(Codes.PLUGIN_UNAVAILABLE))
                    Closeable { watches -= callback }
                })
        }
        fun invalidate() { watches.toList().forEach { it(ComposeUiError(Codes.PLUGIN_UNAVAILABLE)) } }
        override fun resolveImage(value:Any):ComposeScriptValues.ResolvedImage = error("No Android images")
        override fun post(delayMillis:Long,action:()->Unit)=dispatcher.post(action)
        override fun warn(message:String)=Unit
        override fun report(error:Throwable){errors+=error}
        override fun close(){watches.clear();dispatcher.close()}
    }
    private class Entry(private val mount:(Any)->Unit, private val attachFailure: RuntimeException?):ComposeSessionRenderer {
        val batches=ArrayList<UiPatchBatch>();var disposals=0
        var attaches = 0
        override fun open(sink:ComposeUiEventSink)=Unit
        override fun attach(){attaches++;mount(Any());attachFailure?.let { throw it }}
        override fun detach()=Unit
        override fun apply(batch:UiPatchBatch){batches+=batch}
        override fun execute(command:UiCommand)=Unit
        override fun setTheme(theme:ThemeSpec)=Unit
        override fun dispose(){disposals++}
    }
    private class Control(private val destroyed:()->Unit):ComposeFloatyControl {
        override val x=0;override val y=0;override val width=100;override val height=100
        override fun setPosition(x:Int,y:Int)=Unit
        override fun setSize(width:Int,height:Int)=Unit
        override fun requestFocus()=Unit
        override fun disableFocus()=Unit
        override fun setTouchable(touchable:Boolean)=Unit
        override fun setAdjustEnabled(enabled:Boolean)=Unit
        override fun close(){destroyed()}
    }
}
