package org.autojs.autojs.runtime.api.modules.compose

import android.view.View
import org.autojs.autojs.core.plugin.compose.ComposeSessionRenderer
import org.autojs.autojs.core.plugin.compose.ScriptUiDispatcher
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.interop.*
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executor

/** Real exported Rhino calls, with only Android creation/attachment replaced by a recorder. */
class ComposeInteropTest {
    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private lateinit var platform: Platform
    private lateinit var compose: Compose

    @Before fun setup() {
        context = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
        with(RhinoUtils) { context.registerStandardObjects(scope) }
        platform = Platform()
        val runtime = RhinoTestRuntime.create(scope)
        compose = Compose(runtime, platform)
        mountCompose(runtime, compose)
        scope.defineProperty("viewA", RhinoUtils.toJsValue(scope, view()), 0)
        scope.defineProperty("viewB", RhinoUtils.toJsValue(scope, view()), 0)
    }
    @After fun cleanup() { try { compose.close() } finally { Context.exit() } }

    @Test fun multipleContainersReplaceOnlyTheirOwnSessionAndNativeDetachReleasesItsLease() {
        eval("var a=compose.attach('a',compose.Text({text:'a'})), b=compose.attach('b',compose.Text({text:'b'})); var page=compose.mount(compose.Text({text:'page'}));")
        platform.dispatcher.drain()
        eval("var replacement=compose.attach('a',compose.Text({text:'replacement'}));")
        platform.dispatcher.drain()
        assertTrue(bool("a.isClosed() && !b.isClosed() && !replacement.isClosed() && !page.isClosed()"))
        assertEquals(2, platform.leases)
        compose.beforeSetContentView(Any())
        assertTrue(bool("page.isClosed() && !b.isClosed() && !replacement.isClosed()"))
        platform.entries[1].destroy()
        platform.dispatcher.drain()
        assertTrue(bool("b.isClosed() && !replacement.isClosed()"))
        assertEquals(1, platform.leases)
        assertEquals(1, platform.entries[1].disposals)
    }

    @Test fun positionalNodeChildrenKeepIdentityInsteadOfBecomingMapProperties() {
        eval("var first=compose.AndroidView({view:viewA}), second=compose.Text({text:'tail'}); var row=compose.Row(first,second);var session=compose.mount(row);")
        platform.dispatcher.drain()
        assertTrue(bool("row.children.length===2 && row.children[0]===first && row.children[1]===second && first.parent===row"))
        assertEquals(1,platform.entries.single().views.size)
        assertTrue(platform.errors.isEmpty())
    }

    @Test fun replacementRejectsReentrantMountsButAnIndependentCloseHandlerCanOpenAnotherContainer() {
        eval("""
            var reentrantCode=null, followup=null;
            var original=compose.attach('a',compose.Text({text:'original'}));
            original.on('close',function(){
                try { compose.attach('a',compose.Text({text:'reentrant'})); }
                catch(error) { reentrantCode=error.code; }
            });
        """.trimIndent())
        platform.dispatcher.drain()
        eval("var replacement=compose.attach('a',compose.Text({text:'replacement'}));")
        platform.dispatcher.drain()
        assertEquals(ComposeUiErrorCodes.INVALID_ARGUMENT,eval("reentrantCode"))
        assertTrue(bool("original.isClosed() && !replacement.isClosed() && compose.sessions.length===1"))
        assertEquals(2,platform.entries.size)
        assertEquals(1,platform.leases)

        eval("replacement.on('close',function(){followup=compose.attach('b',compose.Text({text:'followup'}));});replacement.close();")
        platform.dispatcher.drain()
        assertTrue(bool("replacement.isClosed() && !followup.isClosed() && compose.sessions.length===1"))
        assertEquals(1,platform.leases)
        assertTrue(platform.errors.isEmpty())
    }

    @Test fun replacementAtEightSessionsSucceedsAndInvalidNewContentPreservesItsContainer() {
        eval("var sessions=[]; for(var i=0;i<8;i++) sessions.push(compose.attach(String(i),compose.Text({text:String(i)})));")
        platform.dispatcher.drain()
        assertEquals(ComposeUiErrorCodes.LIMIT_EXCEEDED, code("compose.attach('ninth',compose.Text({text:'ninth'}))"))
        assertEquals(ComposeUiErrorCodes.RENDER_FAILED, code("compose.attach('0',function(){throw new Error('invalid new render');})"))
        assertTrue(bool("!sessions[0].isClosed() && compose.sessions.length===8"))
        eval("var replacement=compose.attach('0',compose.Text({text:'new'}));")
        platform.dispatcher.drain()
        assertTrue(bool("sessions[0].isClosed() && !replacement.isClosed() && compose.sessions.length===8"))
        assertEquals(8, platform.leases)
    }

    @Test fun factoryIsPreparedOnceGetterReturnsViewAndReactiveIdentityRetainsTheLease() {
        eval("var calls=0, tick=compose.state(0); var session=compose.mount(function(){tick.value; return compose.AndroidView({key:'native',view:function(ctx){if(ctx!=='native context')throw Error('context'); calls++;return viewA;}});});")
        platform.dispatcher.drain()
        val renderer = platform.entries.single()
        val binding = renderer.views.single()
        val original = (eval("session.root") as ComposeNodeNativeObject).node
        assertSame(ComposeScriptValues.unwrap(eval("viewA")), ComposeScriptValues.unwrap(eval("session.root.view")))
        eval("tick.value=1;")
        platform.dispatcher.drain()
        assertEquals(2, Context.toNumber(eval("calls")).toInt())
        assertSame(original, (eval("session.root") as ComposeNodeNativeObject).node)
        assertEquals(binding.leaseId, original.state.nativeView!!.id)
        assertEquals(1, renderer.batches.size)
        eval("session.root.set({view:function(){calls++;return viewB;}});")
        platform.dispatcher.drain()
        assertEquals(3, Context.toNumber(eval("calls")).toInt())
        assertNotEquals(binding.leaseId, renderer.views.single().leaseId)
        assertSame(ComposeScriptValues.unwrap(eval("viewB")), renderer.views.single().view)
    }

    @Test fun throwingOrReentrantFactoriesCannotReplaceTheOldPage() {
        eval("var original=compose.mount(compose.Text({text:'keep'}));")
        platform.dispatcher.drain()
        assertEquals(ComposeUiErrorCodes.RENDER_FAILED, code("compose.AndroidView({view:function(){throw Error('failed native factory');}})"))
        for (mount in listOf("compose.mount(compose.Text({text:'bad'}))", "compose.attach('a',compose.Text({text:'bad'}))", "compose.floaty(compose.Text({text:'bad'}))", "compose.dialog(compose.Text({text:'bad'}))")) {
            assertEquals(ComposeUiErrorCodes.INVALID_ARGUMENT, code("compose.AndroidView({view:function(){ $mount; return viewA;}})"))
        }
        assertTrue(bool("!original.isClosed() && original.root.text==='keep' && compose.sessions.length===1"))
        assertEquals(1, platform.entries.size)
    }

    @Test fun coalescedViewChangesReuseTheAcceptedLeaseAndDoNotReparentTheSameView() {
        eval("var native=compose.AndroidView({view:viewA}), session=compose.mount(native);")
        platform.dispatcher.drain()
        val renderer=platform.entries.single()
        val accepted=renderer.views.single()
        eval("native.set({view:viewB}); native.set({view:viewA});")
        platform.dispatcher.drain()
        assertEquals(1,renderer.batches.size)
        assertEquals(accepted.leaseId,(eval("native") as ComposeNodeNativeObject).node.state.nativeView!!.id)
        assertSame(accepted.view,renderer.views.single().view)
    }

    @Test fun duplicateAndCrossSessionViewsAreRejectedBeforeReplacingContent() {
        assertEquals(ComposeUiErrorCodes.INVALID_ARGUMENT, code("compose.Column([compose.AndroidView({view:viewA}),compose.AndroidView({view:viewA})])"))
        eval("var first=compose.attach('a',compose.AndroidView({view:viewA})); var second=compose.attach('b',compose.Text({text:'keep'}));")
        platform.dispatcher.drain()
        assertEquals(ComposeUiErrorCodes.INVALID_ARGUMENT, code("compose.attach('b',compose.AndroidView({view:viewA}))"))
        assertTrue(bool("!first.isClosed() && !second.isClosed() && second.root.text==='keep'"))
        assertEquals(2, platform.leases)
    }

    @Test fun rendererRejectsViewReplacementAtomicallyAndASecondUpdateCanRecover() {
        eval("var native=compose.AndroidView({view:viewA}), session=compose.mount(native);")
        platform.dispatcher.drain()
        val renderer = platform.entries.single()
        val old = renderer.views.single()
        renderer.reject = true
        eval("native.set({view:viewB});")
        platform.dispatcher.drain()
        assertSame(old.view, ComposeScriptValues.unwrap(eval("native.view")))
        assertSame(old.view, renderer.views.single().view)
        eval("native.set({view:viewB});")
        platform.dispatcher.drain()
        assertSame(ComposeScriptValues.unwrap(eval("viewB")), renderer.views.single().view)
        assertTrue(bool("!session.isClosed()"))
        assertEquals(1, platform.errors.size)
    }

    @Test fun anOldPluginFailsWithCapabilityErrorAndAnAbortedContainerBatchNeverAttaches() {
        platform.interop = false
        assertEquals(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE, code("compose.mount(compose.AndroidView({view:viewA}))"))
        platform.interop = true
        eval("var orphan; try{compose.batch(function(){orphan=compose.attach('a',compose.Text({text:'orphan'}));throw Error('rollback');});}catch(error){}")
        platform.dispatcher.drain()
        assertTrue(bool("orphan.isClosed() && compose.sessions.length===0"))
        assertEquals(0, platform.entries.last().attaches)
        assertEquals(0, platform.leases)
    }

    private fun eval(source: String): Any? = context.evaluateString(scope, source, "compose-interop.js", 1, null)
    private fun bool(source: String) = Context.toBoolean(eval(source))
    private fun code(source: String) = eval("(function(){try{$source;return null;}catch(error){return error instanceof ComposeError ? error.code : String(error);}})()")
    private fun view(): View {
        // Plain JVM: do not invoke an Android constructor. Production View behavior is device-tested.
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as sun.misc.Unsafe).allocateInstance(View::class.java) as View
    }

    private class Dispatcher : ScriptUiDispatcher {
        private val origin = Thread.currentThread()
        private val queue = ArrayDeque<() -> Unit>()
        override fun isDispatchThread() = Thread.currentThread() === origin
        override fun post(action: () -> Unit) { queue += action }
        fun drain() { var count=0; while(queue.isNotEmpty()) { check(count++ < 1000); queue.removeFirst()() } }
        override fun close() { queue.clear() }
    }
    private class Platform : ComposeScriptPlatform {
        override val dispatcher = Dispatcher()
        override val mainExecutor = Executor { it.run() }
        val entries = ArrayList<Renderer>()
        val errors = ArrayList<Throwable>()
        val containers = HashMap<String, Any>()
        var leases = 0
        var interop = true
        override fun version(): Map<String, Any> = mapOf("contract" to 1)
        override fun container(value: Any?) = containers.getOrPut(value as? String ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)) { Any() }
        override fun createRenderer(sessionId: Int, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit) = opened(beforeMount, onDestroyed, false)
        override fun createContainerRenderer(sessionId: Int, container: Any, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit) = opened(beforeMount, onDestroyed, true)
        private fun opened(beforeMount: (Any) -> Unit, onDestroyed: () -> Unit, lease: Boolean): ComposeScriptPlatform.Opened {
            val renderer = Renderer(beforeMount, onDestroyed).also(entries::add)
            if (lease) leases++
            return ComposeScriptPlatform.Opened(renderer, (if(interop) AndroidViewInteropV1.catalog else org.autojs.plugin.compose.api.catalog.ComponentCatalog.V1).components.map { it.name }.toSet(),
                lifetime = Closeable { if(lease) leases-- })
        }
        override fun prepareView(value: Any?, factory: (Function, Any) -> Any?): View {
            val raw = ComposeScriptValues.unwrap(value)
            return ComposeScriptValues.unwrap(if(raw is Function) factory(raw, "native context") else raw) as? View
                ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "view")
        }
        override fun resolveImage(value: Any): ComposeScriptValues.ResolvedImage = error("No images")
        override fun post(delayMillis: Long, action: () -> Unit) = dispatcher.post(action)
        override fun warn(message: String) = Unit
        override fun report(error: Throwable) { errors += error }
        override fun close() = dispatcher.close()
    }
    private class Renderer(private val beforeMount: (Any) -> Unit, val destroy: () -> Unit) : ComposeSessionRenderer {
        val batches = ArrayList<UiPatchBatch>()
        var views: List<AndroidViewBindingV1> = emptyList()
        var reject = false
        var attaches = 0
        var disposals = 0
        override fun open(sink: ComposeUiEventSink) = Unit
        override fun attach() { attaches++; beforeMount(this) }
        override fun detach() = Unit
        override fun apply(batch: UiPatchBatch) { batches += batch }
        override fun apply(batch: UiPatchBatch, views: List<AndroidViewBindingV1>) {
            if(reject) { reject=false; throw ComposeUiContractException(ComposeUiErrorCodes.RENDER_FAILED) }
            batches += batch; this.views = views
        }
        override fun execute(command: UiCommand) = Unit
        override fun setTheme(theme: ThemeSpec) = Unit
        override fun dispose() { disposals++; views=emptyList() }
    }
}
