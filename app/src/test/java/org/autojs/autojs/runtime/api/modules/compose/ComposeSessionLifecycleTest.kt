package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.core.plugin.compose.ComposeSessionRenderer
import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.core.plugin.compose.ScriptUiDispatcher
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiErrorCodes as Codes
import org.autojs.plugin.compose.api.ComposeUiLimits
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.ThemeSpec
import org.autojs.plugin.compose.api.model.UiCommand
import org.autojs.plugin.compose.api.model.UiPatchBatch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** Engine/session ownership, independent of Android window APIs and renderer implementation details. */
class ComposeSessionLifecycleTest {
    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private lateinit var platform: Platform
    private lateinit var compose: Compose

    @Before fun enterRuntime() {
        context = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
        with(RhinoUtils) { context.registerStandardObjects(scope) }
        platform = Platform()
        compose = Compose(RhinoTestRuntime.create(scope), platform)
        compose.augment(scope.globalThis)
    }

    @After fun leaveRuntime() {
        try { if (::compose.isInitialized) compose.close() } finally { Context.exit() }
    }

    @Test fun dialogsAreIndependentOfUiAndFloatiesAndShareTheEightSessionBudget() {
        evaluate("var page=compose.mount(compose.Text({text:'page'})); var floating=compose.floaty(compose.Text({text:'floaty'})); var dialogs=[];for(var i=0;i<6;i++)dialogs.push(compose.dialog(compose.Text({text:'dialog'}),{type:i%2?'bottomSheet':'alert'}));")
        platform.dispatcher.drain()
        assertEquals(8, number("compose.sessions.length"))
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("compose.dialog(compose.Text({text:'ninth'}))"))
        evaluate("var replacement=compose.mount(compose.Text({text:'replacement'}));")
        platform.dispatcher.drain()
        assertTrue(boolean("page.isClosed() && !floating.isClosed() && dialogs.every(function(d){return !d.isClosed();})"))
        compose.beforeSetContentView(Any())
        assertTrue(boolean("replacement.isClosed() && dialogs.every(function(d){return !d.isClosed();})"))
        assertEquals(7, platform.leases)
        assertEquals(6, platform.dialogOptions.size)
    }

    @Test fun dialogImmediateCloseAndOuterTransactionRollbackNeverAttachOrRetainTheirLease() {
        evaluate("var closes=0;var immediate=compose.dialog(compose.Text({text:'immediate'}));immediate.on('close',function(){closes++;});immediate.close();var orphan;try{compose.batch(function(){orphan=compose.dialog(compose.Text({text:'orphan'}));throw Error('rollback');});}catch(error){}")
        platform.dispatcher.drain()
        assertTrue(boolean("immediate.isClosed() && orphan.isClosed() && closes===1 && compose.sessions.length===0"))
        assertTrue(platform.entries.all { it.attaches==0 && it.disposals==1 && it.lifetimeCloses==1 })
        assertEquals(0,platform.leases)
    }

    @Test fun unsupportedDialogProviderAndNestedDialogRenderingPreserveExistingUi() {
        evaluate("var page=compose.mount(compose.Text({text:'keep'}));")
        platform.dispatcher.drain()
        platform.openFailure=ComposeUiError(Codes.PLUGIN_INCOMPATIBLE)
        assertEquals(Codes.PLUGIN_INCOMPATIBLE,failureCode("compose.dialog(compose.Text({text:'unsupported'}))"))
        platform.openFailure=null
        assertEquals(Codes.INVALID_ARGUMENT,failureCode("compose.dialog(function(){return compose.dialog(compose.Text({text:'nested'}));})"))
        assertTrue(boolean("!page.isClosed() && page.root.text==='keep' && compose.sessions.length===1"))
        assertEquals(0,platform.leases)
    }

    @Test fun dialogUsesTheCommonThemeAndNativeFailureClosesOnlyOnce() {
        evaluate("compose.theme({dark:true});var closes=0;var dialog=compose.dialog(compose.Text({text:'themed'}));dialog.on('close',function(){closes++;});")
        platform.dispatcher.drain()
        val entry=platform.entries.single()
        assertEquals(true,entry.themes.single().dark)
        evaluate("compose.theme({dark:false});")
        assertEquals(false,entry.themes.last().dark)
        val failure=ComposeUiError(Codes.PERMISSION_REQUIRED)
        entry.onFailure!!(failure);entry.onFailure!!(failure)
        platform.dispatcher.drain()
        assertTrue(boolean("dialog.isClosed() && closes===1"))
        assertEquals(listOf(failure),platform.errors)
        assertEquals(1,entry.disposals);assertEquals(1,entry.lifetimeCloses);assertEquals(0,platform.leases)
    }

    @Test fun concurrentFloatingSessionsAreBoundedAndAClosedSlotCanBeReused() {
        evaluate("var windows=[]; for(var i=0;i<8;i++) windows.push(compose.floaty(compose.Text({text:String(i)}),{raw:true}));")
        platform.dispatcher.drain()
        assertEquals(ComposeUiLimits.MAX_SESSIONS_PER_ENGINE, platform.leases)
        assertEquals(8, number("compose.sessions.length"))
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("compose.floaty(compose.Text({text:'ninth'}))"))
        assertEquals(8, platform.entries.size)

        evaluate("windows[0].close(); var replacement=compose.floaty(compose.Text({text:'replacement'}));")
        platform.dispatcher.drain()
        assertEquals(8, platform.leases)
        assertEquals(8, number("compose.sessions.length"))
        assertEquals(1, platform.entries.first().disposals)
        assertEquals(9, platform.entries.size)
        assertTrue(boolean("windows[0].isClosed() && !replacement.isClosed()"))
    }

    @Test fun replacingUiAtTheSessionLimitDoesNotCloseUnrelatedFloaties() {
        evaluate("var windows=[]; for(var i=0;i<7;i++) windows.push(compose.floaty(compose.Text({text:'floaty'}))); var uiSession=compose.mount(compose.Text({text:'first'}));")
        platform.dispatcher.drain()
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("compose.floaty(compose.Text({text:'too many'}))"))
        evaluate("var nextUi=compose.mount(compose.Text({text:'second'}));")
        platform.dispatcher.drain()
        assertEquals(8, number("compose.sessions.length"))
        assertEquals(7, platform.leases)
        assertTrue(boolean("uiSession.isClosed() && !nextUi.isClosed() && windows.every(function(window){return !window.isClosed();})"))
        assertTrue(platform.entries.take(7).all { it.disposals == 0 })
        assertEquals(1, platform.entries[7].disposals)
        assertEquals(0, platform.entries.last().disposals)
    }

    @Test fun replacingLegacyUiContentLeavesFloatingSessionsAlive() {
        evaluate("var overlay=compose.floaty(compose.Text({text:'overlay'})); var page=compose.mount(compose.Text({text:'page'}));")
        platform.dispatcher.drain()
        compose.beforeSetContentView(Any())
        platform.dispatcher.drain()
        assertTrue(boolean("page.isClosed() && !overlay.isClosed() && compose.sessions.length === 1"))
        assertEquals(0, platform.entries[0].disposals)
        assertEquals(1, platform.entries[1].disposals)
        assertEquals(1, platform.leases)
    }

    @Test fun windowMethodsDelegateGeometryFocusAndTouchabilityToTheOwnedWindow() {
        evaluate("var window=compose.floaty(compose.Text({text:'window'}),{raw:false}); window.setPosition(-4,12).setSize(-1,-2).requestFocus().setTouchable(false).setAdjustEnabled(false);")
        val control = platform.entries.single().control!!
        assertEquals(-4, control.x)
        assertEquals(12, control.y)
        assertEquals(-1, control.width)
        assertEquals(-2, control.height)
        assertTrue(control.focused)
        assertFalse(control.touchableState)
        assertFalse(control.adjustableState)
        assertTrue(boolean("window.getX() === -4 && window.getY() === 12 && window.getWidth() === -1 && window.getHeight() === -2 && window.session.root === window.root"))
        evaluate("window.disableFocus();")
        assertFalse(control.focused)
        evaluate("var raw=compose.floaty(compose.Text({text:'raw'}),{raw:true});")
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("raw.setAdjustEnabled(true)"))
        assertFalse(boolean("raw.isClosed()"))
    }

    @Test fun closingOneSessionDropsItsCallbacksAndPostsWhileOtherSessionsKeepRunning() {
        evaluate("""
            var closeCalls=0, firstPosts=0, secondPosts=0;
            var firstNode=compose.Text({text:'first',onClick:function(){}}), secondNode=compose.Text({text:'second'});
            var first=compose.floaty(firstNode), second=compose.floaty(secondNode);
            first.on('close',function(){closeCalls++;});
            first.post(function(){firstPosts++;}); second.post(function(){secondPosts++;});
            first.close(); first.close();
        """.trimIndent())
        platform.dispatcher.drain()
        assertEquals(1, number("closeCalls"))
        assertEquals(0, number("firstPosts"))
        assertEquals(1, number("secondPosts"))
        assertEquals(1, platform.leases)
        assertEquals(1, platform.entries.first().disposals)
        assertEquals(0, platform.entries.last().disposals)
        val node = (evaluate("firstNode") as ComposeNodeNativeObject).node
        assertTrue(node.state.listeners.isEmpty())
        assertTrue(node.state.modifierCallbacks.isEmpty())
        assertEquals(Codes.SESSION_CLOSED, failureCode("firstNode.text='closed'"))
        assertEquals(Codes.SESSION_CLOSED, failureCode("first.setPosition(1,2)"))
        assertEquals(Codes.SESSION_CLOSED, failureCode("first.session.update(function(){})"))
        evaluate("secondNode.text='still open';")
        platform.dispatcher.drain()
        assertTrue(boolean("secondNode.text === 'still open' && !second.isClosed()"))
    }

    @Test fun closingAnOlderWindowDoesNotClearARefThatAnotherWindowNowOwns() {
        evaluate("var shared=compose.ref(); var first=compose.floaty(compose.Text({ref:shared,text:'first'}));")
        platform.dispatcher.drain()
        evaluate("var second=compose.floaty(compose.Text({ref:shared,text:'second'}));")
        platform.dispatcher.drain()
        assertTrue(boolean("shared.current === second.root"))
        evaluate("first.close();")
        assertTrue(boolean("shared.current === second.root && !second.isClosed()"))
        evaluate("second.close();")
        assertTrue(boolean("shared.current === null"))
        assertEquals(0, platform.leases)
    }

    @Test fun engineExitClearsRefsCallbacksReactionsPostsAndEveryLifetimeLease() {
        evaluate("""
            var state=compose.state(0), windows=[], refs=[], nodes=[], closeCalls=0, posted=0;
            function openWindow() {
                var ref=compose.ref(); refs.push(ref);
                var window=compose.floaty(function(){return compose.Text({ref:ref,text:String(state.value),onClick:function(){posted++;}});});
                window.on('close',function(){closeCalls++;}); windows.push(window);
            }
            openWindow(); openWindow(); openWindow();
        """.trimIndent())
        platform.dispatcher.drain()
        evaluate("nodes=windows.map(function(window){return window.root;}); state.value=1; windows.forEach(function(window){window.post(function(){posted++;},100);}); compose.post(function(){posted++;},200);")
        val nodes = (0 until 3).map { (evaluate("nodes[$it]") as ComposeNodeNativeObject).node }
        val frames = platform.entries.map { it.batches.size }
        compose.close()
        platform.dispatcher.drain()
        assertEquals(0, platform.leases)
        assertTrue(platform.closed)
        assertTrue(platform.dispatcher.closed)
        assertEquals(0, platform.dispatcher.pendingCount)
        assertTrue(platform.entries.all { it.disposals == 1 && it.control?.closed == true && it.lifetimeCloses == 1 })
        assertEquals(frames, platform.entries.map { it.batches.size })
        assertEquals(3, number("closeCalls"))
        assertEquals(0, number("posted"))
        assertTrue(boolean("refs.every(function(ref){return ref.current === null;}) && windows.every(function(window){return window.isClosed();})"))
        nodes.forEach { node ->
            assertTrue(node.state.listeners.isEmpty())
            assertTrue(node.state.modifierCallbacks.isEmpty())
            assertTrue(node.state.images.isEmpty())
            assertNull(node.state.ref)
        }
        assertEquals(Codes.SESSION_CLOSED, failureCode("nodes[0].text='after exit'"))
        assertEquals(Codes.SESSION_CLOSED, failureCode("state.value=2"))
    }

    @Test fun defaultThemeCanChangeWhileTheLastClosedWindowAwaitsScriptCleanup() {
        evaluate("var ref=compose.ref(); var window=compose.floaty(compose.Text({text:'closing',ref:ref}));")
        platform.dispatcher.drain()
        val failure = AtomicReference<Throwable>()
        val nativeClose = Thread { try { platform.entries.single().control!!.close() } catch (error: Throwable) { failure.set(error) } }
        nativeClose.start(); nativeClose.join(5000)
        assertFalse(nativeClose.isAlive)
        failure.get()?.let { throw AssertionError("Native close failed", it) }
        assertTrue(platform.dispatcher.pendingCount > 0)
        evaluate("compose.theme({dark:true});")
        assertTrue(boolean("window.isClosed() && window.root === null && ref.current === null && compose.theme().dark === true"))
        assertEquals(0, platform.leases)
        platform.dispatcher.drain()
        assertTrue(boolean("compose.sessions.length === 0"))
    }

    @Test fun nativeWindowCloseReleasesItsSessionAndIsIdempotentWithScriptClose() {
        evaluate("var ref=compose.ref(), closed=0; var window=compose.floaty(compose.Text({ref:ref,text:'native'})); window.on('close',function(){closed++;});")
        platform.dispatcher.drain()
        val entry = platform.entries.single()
        entry.control!!.close()
        platform.dispatcher.drain()
        evaluate("window.close(); window.session.close();")
        assertEquals(1, number("closed"))
        assertEquals(1, entry.disposals)
        assertEquals(0, platform.leases)
        assertTrue(boolean("window.isClosed() && ref.current === null && compose.sessions.length === 0"))
    }

    @Test fun permissionAndRenderFailuresLeaveNoLiveSessionOrLifetimeLease() {
        platform.openFailure = ComposeUiError(Codes.PERMISSION_REQUIRED)
        assertTrue(boolean("""
            (function(){try{compose.floaty(compose.Text({text:'permission'}));return false;}
            catch(error){return error instanceof ComposeError && error.code==='PERMISSION_REQUIRED' && error.message.indexOf('localized PERMISSION_REQUIRED')===0;}})()
        """.trimIndent()))
        assertEquals(0, platform.entries.size)
        assertEquals(0, platform.leases)
        platform.openFailure = null
        assertEquals(Codes.RENDER_FAILED, failureCode("compose.floaty(function(){throw new Error('bad render');})"))
        platform.dispatcher.drain()
        assertEquals(0, platform.leases)
        assertEquals(1, platform.entries.single().disposals)
        assertEquals(0, number("compose.sessions.length"))
    }

    @Test fun deferredNativeFailureReportsOnceAndClosesItsWindowSessionAndLease() {
        evaluate("var closes=0; var window=compose.floaty(compose.Text({text:'pending'})); window.on('close',function(){closes++;});")
        val entry = platform.entries.single()
        val failure = ComposeUiError(Codes.RENDERER_FAILED, "Deferred native initialization failure")
        val notifyFailure = requireNotNull(entry.onFailure)
        notifyFailure(failure)
        notifyFailure(failure)
        platform.dispatcher.drain()
        assertEquals(listOf(failure), platform.errors)
        assertEquals(1, entry.disposals)
        assertEquals(1, entry.lifetimeCloses)
        assertEquals(0, platform.leases)
        assertEquals(1, number("closes"))
        assertTrue(boolean("window.isClosed() && compose.sessions.length === 0"))
    }

    @Test fun aFloatingMountInsideARolledBackBatchNeverAttachesOrRetainsItsReaction() {
        evaluate("""
            var orphan, ref=compose.ref(), state=compose.state(0), closes=0, rolledBack=false;
            try {
                compose.batch(function(){
                    orphan=compose.floaty(function(){return compose.Text({ref:ref,text:String(state.value)});});
                    orphan.on('close',function(){closes++;});
                    throw new Error('rollback the enclosing transaction');
                });
            } catch(error) { rolledBack=true; }
        """.trimIndent())
        val entry = platform.entries.single()
        assertTrue(boolean("rolledBack"))
        assertEquals(0, entry.attaches)
        platform.dispatcher.drain()
        assertEquals(0, entry.attaches)
        assertTrue(entry.batches.isEmpty())
        assertEquals(1, entry.disposals)
        assertEquals(1, entry.lifetimeCloses)
        assertEquals(0, platform.leases)
        assertEquals(1, number("closes"))
        assertTrue(boolean("orphan.isClosed() && ref.current === null && compose.sessions.length === 0"))
        evaluate("state.value=1;")
        platform.dispatcher.drain()
        assertTrue(entry.batches.isEmpty())
        assertTrue(platform.errors.isEmpty())
    }

    @Test fun failedStartupReleasesItsLeaseWithoutWaitingForAnotherDispatcherTurn() {
        assertEquals(Codes.RENDER_FAILED, failureCode("compose.floaty(function(){throw new Error('startup failed');})"))
        val entry = platform.entries.single()
        assertEquals(1, entry.disposals)
        assertEquals(1, entry.lifetimeCloses)
        assertEquals(0, platform.leases)
        platform.dispatcher.close()
        assertEquals(1, entry.lifetimeCloses)
    }

    @Test fun asynchronousRendererOpenFailureClosesTheSessionAndReleasesItsLease() {
        platform.queueMain = true
        platform.rendererOpenFailure = IllegalStateException("Renderer initialization failed")
        evaluate("var ref=compose.ref(), closed=0; var node=compose.Text({ref:ref,text:'startup',onClick:function(){}}); var window=compose.floaty(node); window.on('close',function(){closed++;});")
        assertEquals(1, platform.leases)
        platform.dispatcher.drain()
        platform.drainMain()
        platform.dispatcher.drain()
        platform.drainMain()

        assertEquals(0, platform.leases)
        assertEquals(1, platform.entries.single().disposals)
        assertEquals(1, platform.entries.single().lifetimeCloses)
        assertTrue(platform.entries.single().batches.isEmpty())
        assertEquals(0, platform.entries.single().attaches)
        assertEquals(1, number("closed"))
        assertEquals(1, platform.errors.size)
        assertEquals(Codes.RENDERER_FAILED, (platform.errors.single() as ComposeUiError).code)
        assertTrue(boolean("ref.current === null && window.isClosed() && compose.sessions.length === 0"))
        assertTrue((evaluate("node") as ComposeNodeNativeObject).node.state.listeners.isEmpty())
    }

    @Test fun aRejectedCommandPostIsDiscardedAndLaterCommandsCanRecover() {
        evaluate("var window=compose.floaty(compose.Scaffold({},compose.Text({text:'commands'})));")
        platform.dispatcher.drain()
        platform.postFailure = ComposeUiError(Codes.LIMIT_EXCEEDED)
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("window.showSnackbar('rejected')"))
        platform.postFailure = null
        evaluate("window.showSnackbar('accepted');")
        platform.dispatcher.drain()
        val commands = platform.entries.single().commands
        assertEquals(1, commands.size)
        assertEquals("accepted", (commands.single() as UiCommand.ShowSnackbar).message)
        assertFalse(boolean("window.isClosed()"))
    }

    @Test fun rejectedControlReservationDisposesProvisionalNativeResourcesBeforeReturning() {
        platform.dispatcher.rejectControls = true
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("compose.floaty(compose.Text({text:'no control slot'}))"))
        val rejected = platform.entries.single()
        assertEquals(0, platform.leases)
        assertEquals(1, rejected.lifetimeCloses)
        assertEquals(1, rejected.disposals)
        assertTrue(rejected.control!!.closed)
        assertEquals(0, number("compose.sessions.length"))
        platform.dispatcher.rejectControls = false
        evaluate("var retry=compose.floaty(compose.Text({text:'retry'}));")
        platform.dispatcher.drain()
        assertFalse(boolean("retry.isClosed()"))
        assertEquals(1, platform.leases)
    }

    @Test fun aWorkerMustPostStateChangesBackToTheOwningDispatcher() {
        evaluate("var state=compose.state(0), workerCode=null; var window=compose.floaty(function(){return compose.Text({text:String(state.value)});});")
        platform.dispatcher.drain()
        val failure = AtomicReference<Throwable>()
        val worker = Thread {
            val current = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
            try {
                current.evaluateString(scope, "try{state.value=99;}catch(error){workerCode=error.code;} compose.post(function(){state.value=1;});", "compose-worker.js", 1, null)
            } catch (error: Throwable) { failure.set(error) } finally { Context.exit() }
        }
        worker.start()
        worker.join(5000)
        assertFalse("Worker must not wait for the script dispatcher", worker.isAlive)
        failure.get()?.let { throw AssertionError("Worker script failed", it) }
        assertEquals(Codes.INVALID_ARGUMENT, evaluate("workerCode"))
        assertEquals(0, number("state.value"))
        platform.dispatcher.drain()
        assertEquals(1, number("state.value"))
        assertEquals("1", evaluate("window.root.text"))
    }

    @Test fun foreignThreadEngineStopDisposesRenderersBeforeScriptCleanup() {
        evaluate("var ref=compose.ref(), closed=0; var window=compose.floaty(compose.Text({ref:ref,text:'stop'})); window.on('close',function(){closed++;});")
        platform.dispatcher.drain()
        val failure = AtomicReference<Throwable>()
        val stopper = Thread { try { compose.close() } catch (error: Throwable) { failure.set(error) } }
        stopper.start()
        stopper.join(5000)
        assertFalse("Engine stop must not wait for the script dispatcher", stopper.isAlive)
        failure.get()?.let { throw AssertionError("Engine stop failed", it) }
        assertEquals(1, platform.entries.single().disposals)
        platform.dispatcher.drain()
        assertEquals(0, platform.leases)
        assertEquals(1, number("closed"))
        assertTrue(platform.closed)
        assertTrue(boolean("ref.current === null && window.isClosed()"))
    }

    @Test fun ownerExitRetriesCleanupWhenTheForeignStopQueueWasDiscarded() {
        evaluate("var ref=compose.ref(), closed=0; var node=compose.Text({ref:ref,text:'stop',onClick:function(){}}); var window=compose.floaty(node); window.on('close',function(){closed++;});")
        platform.dispatcher.drain()
        val failure = AtomicReference<Throwable>()
        val stopper = Thread { try { compose.close() } catch (error: Throwable) { failure.set(error) } }
        stopper.start()
        stopper.join(5000)
        assertFalse("Engine stop must not wait for the script dispatcher", stopper.isAlive)
        failure.get()?.let { throw AssertionError("Engine stop failed", it) }
        val entry = platform.entries.single()
        assertEquals(1, entry.disposals)
        assertEquals(0, platform.leases)
        assertTrue(platform.dispatcher.pendingCount > 0)
        platform.dispatcher.close()

        compose.close()
        compose.close()

        val node = (evaluate("node") as ComposeNodeNativeObject).node
        assertTrue(node.state.listeners.isEmpty())
        assertTrue(node.state.modifierCallbacks.isEmpty())
        assertTrue(node.state.images.isEmpty())
        assertEquals(1, number("closed"))
        assertEquals(1, entry.disposals)
        assertEquals(1, entry.lifetimeCloses)
        assertEquals(0, platform.dispatcher.pendingCount)
        assertTrue(platform.closed)
        assertTrue(boolean("ref.current === null && window.root === null && window.isClosed()"))
    }

    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "compose-lifecycle.js", 1, null)
    private fun number(source: String) = Context.toNumber(evaluate(source)).toInt()
    private fun boolean(source: String) = Context.toBoolean(evaluate(source))
    private fun failureCode(action: String): Any? = evaluate("(function(){try{$action;return null;}catch(error){if(!(error instanceof ComposeError))throw error;return error.code;}})()")

    private class Dispatcher : ScriptUiDispatcher {
        private val origin = Thread.currentThread()
        private val queue = ConcurrentLinkedQueue<() -> Unit>()
        @Volatile var closed = false
        var rejectControls = false
        val pendingCount get() = queue.size
        override fun isDispatchThread() = Thread.currentThread() === origin
        override fun dispatch(action: () -> Unit) {
            if (!isDispatchThread()) post(action) else action()
        }
        override fun post(action: () -> Unit) { if (!closed) queue.add(action) }
        override fun createControlChannel(action: () -> Unit): org.autojs.autojs.core.plugin.compose.ScriptControlChannel {
            if (rejectControls) throw ComposeUiError(Codes.LIMIT_EXCEEDED)
            return super.createControlChannel(action)
        }
        fun drain() {
            var count = 0
            while (true) {
                val action = queue.poll() ?: return
                check(count++ < 1000) { "Lifecycle queue did not settle" }
                action()
            }
        }
        override fun close() { closed = true; queue.clear() }
    }

    private class Platform : ComposeScriptPlatform {
        override val dispatcher = Dispatcher()
        private val mainQueue = ConcurrentLinkedQueue<Runnable>()
        var queueMain = false
        override val mainExecutor = Executor { if (queueMain) mainQueue.add(it) else it.run() }
        fun drainMain() { while (true) (mainQueue.poll() ?: return).run() }
        val entries = ArrayList<Entry>()
        val errors = ArrayList<Throwable>()
        var leases = 0
        var closed = false
        var openFailure: Throwable? = null
        var rendererOpenFailure: Throwable? = null
        var postFailure: Throwable? = null
        val dialogOptions = ArrayList<ComposeDialogOptions>()
        override fun version() = mapOf<String, Any>("plugin" to "1.0.0", "contract" to 1, "compose" to "1.12.1")
        override fun createRenderer(sessionId: Int, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit): ComposeScriptPlatform.Opened =
            opened(beforeMount, onDestroyed, floating = false)
        override fun createFloatyRenderer(sessionId: Int, options: ComposeFloatyOptions, beforeMount: (Any) -> Unit,
            onDestroyed: () -> Unit, onFailure: (Throwable) -> Unit): ComposeScriptPlatform.Opened =
            opened(beforeMount, onDestroyed, floating = true, onFailure = onFailure)
        override fun createDialogRenderer(sessionId: Int, options: ComposeDialogOptions, beforeMount: (Any) -> Unit,
            onDestroyed: () -> Unit, onFailure: (Throwable) -> Unit): ComposeScriptPlatform.Opened {
            dialogOptions += options
            return opened(beforeMount,onDestroyed,floating=true,onFailure=onFailure)
        }
        private fun opened(beforeMount: (Any) -> Unit, onDestroyed: () -> Unit, floating: Boolean,
            onFailure: ((Throwable) -> Unit)? = null): ComposeScriptPlatform.Opened {
            openFailure?.let { throw it }
            val entry = Entry(beforeMount, if (floating) Control(onDestroyed) else null, onFailure, rendererOpenFailure).also(entries::add)
            if (floating) leases++
            val lifetime = Closeable { entry.lifetimeCloses++; if (floating) leases-- }
            return ComposeScriptPlatform.Opened(entry, ComponentCatalog.V1.components.map { it.name }.toSet(), entry.control, lifetime)
        }
        override fun resolveImage(value: Any): ComposeScriptValues.ResolvedImage = error("No Android image effects in lifecycle tests")
        override fun post(delayMillis: Long, action: () -> Unit) { postFailure?.let { throw it }; dispatcher.post(action) }
        override fun warn(message: String) = Unit
        override fun report(error: Throwable) { errors += error }
        override fun errorMessage(error: ComposeUiError) = error.localizedMessage { "localized ${error.code}." }
        override fun close() { closed = true; dispatcher.close() }
    }

    private class Entry(private val beforeMount: (Any) -> Unit, val control: Control?, val onFailure: ((Throwable) -> Unit)?,
        private val openFailure: Throwable?) : ComposeSessionRenderer {
        val batches = ArrayList<UiPatchBatch>()
        val commands = ArrayList<UiCommand>()
        val themes = ArrayList<ThemeSpec>()
        var disposals = 0
        var lifetimeCloses = 0
        var attaches = 0
        override fun open(sink: ComposeUiEventSink) { openFailure?.let { throw it } }
        override fun attach() { attaches++; beforeMount(Any()) }
        override fun detach() = Unit
        override fun apply(batch: UiPatchBatch) { batches += batch }
        override fun execute(command: UiCommand) { commands += command }
        override fun setTheme(theme: ThemeSpec) { themes += theme }
        override fun dispose() { disposals++; control?.close() }
    }

    private class Control(private val onDestroyed: () -> Unit) : ComposeFloatyControl {
        override var x = 0
            private set
        override var y = 0
            private set
        override var width = 100
            private set
        override var height = 100
            private set
        var focused = false
        var touchableState = true
        var adjustableState = true
        var closed = false
        override fun setPosition(x: Int, y: Int) { this.x = x; this.y = y }
        override fun setSize(width: Int, height: Int) { this.width = width; this.height = height }
        override fun requestFocus() { focused = true }
        override fun disableFocus() { focused = false }
        override fun setTouchable(touchable: Boolean) { touchableState = touchable }
        override fun setAdjustEnabled(enabled: Boolean) { adjustableState = enabled }
        override fun close() { if (!closed) { closed = true; onDestroyed() } }
    }
}
