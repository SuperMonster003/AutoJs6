package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.autojs.core.plugin.compose.ComposeSessionRenderer
import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.core.plugin.compose.ScriptUiDispatcher
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiProps
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.catalog.ComponentInvocation
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.ThemeSpec
import org.autojs.plugin.compose.api.model.TextSelection
import org.autojs.plugin.compose.api.model.SnackbarDuration
import org.autojs.plugin.compose.api.model.UiCommand
import org.autojs.plugin.compose.api.model.UiPatch
import org.autojs.plugin.compose.api.model.UiPatchBatch
import org.autojs.plugin.compose.api.model.UiValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import java.util.ArrayDeque
import java.util.concurrent.Executor

/** Real Rhino exercises the exported API; the seam records only its immutable renderer traffic. */
class ComposeScriptApiTest {
    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private lateinit var platform: Platform
    private lateinit var compose: Compose

    @Before
    fun enterRuntime() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
        with(RhinoUtils) { context.registerStandardObjects(scope) }
        platform = Platform()
        compose = Compose(RhinoTestRuntime.create(scope), platform)
        compose.augment(scope.globalThis)
    }

    @After
    fun leaveRuntime() {
        try { if (::compose.isInitialized) compose.close() } finally { Context.exit() }
    }

    @Test
    fun globalsVersionAndFactoriesFollowTheFrozenNodeCatalog() {
        val module = evaluate("compose") as Scriptable
        assertTrue(module is Function)
        assertSame(module, evaluate("\$compose"))
        assertTrue(jsBoolean("ComposeError === compose.ComposeError && new ComposeError('test') instanceof Error"))
        val names = org.autojs.plugin.compose.api.v2.ComposeUiV2.catalog.components.filter { it.invocation == ComponentInvocation.NODE }.map { it.name }.toSet()
        assertEquals(54, names.size)
        val factories = module.ids.filterIsInstance<String>().filter { it.firstOrNull()?.isUpperCase() == true && it != "ComposeError" }.toSet()
        assertEquals(names + "AndroidView", factories)
        names.forEach { assertTrue("Factory $it is missing", module.get(it, module) is Function) }
        assertTrue(jsBoolean("compose.isAvailable() && compose.version.plugin === '1.0.0' && compose.version.contract === 1 && compose.version.compose === '1.12.1'"))
        assertTrue(jsBoolean("typeof compose.Snackbar === 'undefined'"))
        platform.available = false
        assertTrue(jsBoolean("!compose.isAvailable() && compose.version === null"))
        assertEquals(0, platform.renderers.size)
    }

    @Test
    fun exportedFactoriesKeepPropertyAliasesAndNativeHandleIdentity() {
        evaluate("""
            var label = compose.createElement('Text', {key:'label', text:'before', w:8});
            var radio = compose.RadioButton({selected:true});
            var column = compose.Column({verticalArrangement:'center'}, [label, 'tail']);
            label.text = 'after';
            label.set({fontSize:'18sp', testTag:'label'});
        """.trimIndent())
        assertTrue(jsBoolean("label.type === 'Text' && label.key === 'label' && label.nodeId === -1"))
        assertTrue(jsBoolean("label.text === 'after' && label.get('text') === 'after' && label.w === 8 && label.fontSize === 18"))
        assertTrue(jsBoolean("radio.checked === true && radio.selected === true && column.arrangement === 'center'"))
        assertTrue(jsBoolean("column.children.length === 2 && column.children[0] === label && label.parent === column && column.children[1].text === 'tail'"))
        assertTrue(jsBoolean("""
            (function() {
                try { label.unknown = true; return false; }
                catch (error) { return error instanceof ComposeError && error.code === 'UNKNOWN_PROP' && error.prop === 'unknown'; }
            })()
        """.trimIndent()))
    }

    @Test
    fun mountAndDirectUpdatesShareOneStableHandleAndQueueOnlyTheFinalFrame() {
        evaluate("var label = compose.Text({text:'initial'}); var session = compose.mount(label);")
        val renderer = platform.renderers.single()
        assertEquals(1, renderer.opens)
        assertEquals(1, renderer.attaches)
        assertTrue(renderer.batches.isEmpty())
        assertTrue(jsBoolean("session.root === label && !session.isClosed() && label.nodeId > 0"))
        platform.dispatcher.drain()
        val id = jsInt("label.nodeId")
        assertEquals(1, renderer.batches.size)
        assertEquals("initial", renderer.text(id))

        evaluate("session.update(function() { label.text='middle'; label.set({text:'final'}); });")
        assertEquals(1, renderer.batches.size)
        platform.dispatcher.drain()
        assertEquals(2, renderer.batches.size)
        assertEquals(id, jsInt("label.nodeId"))
        val patch = renderer.batches.last().patches.single() as UiPatch.SetProps
        assertEquals(id, patch.nodeId)
        assertEquals(UiValue.Str("final"), patch.props[ComposeUiProps.TEXT])
        assertEquals(2L, renderer.batches.last().generation)
        assertTrue(platform.errors.isEmpty())
    }

    @Test
    fun editorAndSnackbarCommandsWaitForTheInitialAcceptedTree() {
        evaluate("""
            var field=compose.TextField({text:'initial'});
            var session=compose.mount(compose.Scaffold({},field));
            field.edit({text:'first',selection:[1,1]});
            field.edit({text:'final',selection:[3,3]});
            field.focus();
            var actionPart='Do', durationPart='lo';
            session.showSnackbar('ready',{actionLabel:actionPart+'ne',duration:durationPart+'ng',onAction:function(){},onDismiss:function(){}});
        """.trimIndent())
        val renderer = platform.renderers.single()
        assertTrue(renderer.batches.isEmpty())
        assertTrue(renderer.commands.isEmpty())
        platform.dispatcher.drain()
        assertEquals(1, renderer.batches.size)
        assertEquals(3, renderer.commands.size)
        val edit = renderer.commands[0] as UiCommand.Edit
        assertEquals(jsInt("field.nodeId"), edit.nodeId)
        assertEquals("final", edit.text)
        assertEquals(TextSelection(3, 3), edit.selection)
        assertEquals(0L, edit.editSeq)
        assertTrue(renderer.commands[1] is UiCommand.Focus)
        val snackbar = renderer.commands[2] as UiCommand.ShowSnackbar
        assertEquals("ready", snackbar.message)
        assertEquals("Done", snackbar.actionLabel)
        assertEquals(SnackbarDuration.LONG, snackbar.duration)
        assertTrue(snackbar.callbackId!! > 0)
        assertTrue(platform.errors.isEmpty())
    }

    @Test
    fun tenStateWritesScheduleOneRenderAndOneUpdatedFrame() {
        evaluate("""
            var counter = compose.state(0), renders = 0;
            var session = compose.mount(function() {
                renders++;
                return compose.Text({key:'counter', text:String(counter.value)});
            });
        """.trimIndent())
        platform.dispatcher.drain()
        val renderer = platform.renderers.single()
        val id = jsInt("session.root.nodeId")
        evaluate("for (var i=1; i<=10; i++) counter.value=i;")
        assertEquals(1, jsInt("renders"))
        assertEquals(1, renderer.batches.size)
        platform.dispatcher.drain()
        assertEquals(2, jsInt("renders"))
        assertEquals(2, renderer.batches.size)
        assertEquals("10", renderer.text(id))
        assertEquals(id, jsInt("session.root.nodeId"))

        evaluate("compose.batch(function() { counter.value=11; compose.batch(function() { counter.value=12; counter.value=13; }); });")
        platform.dispatcher.drain()
        assertEquals(3, jsInt("renders"))
        assertEquals(3, renderer.batches.size)
        assertEquals("13", renderer.text(id))
    }

    @Test
    fun conditionalReadsRemoveDependenciesAfterASuccessfulRender() {
        evaluate("""
            var left = compose.state('left'), right = compose.state('right'), chooseLeft = compose.state(true), renders = 0;
            var session = compose.mount(function() {
                renders++;
                return compose.Text({text:chooseLeft.value ? left.value : right.value});
            });
        """.trimIndent())
        platform.dispatcher.drain()
        val renderer = platform.renderers.single()
        val id = jsInt("session.root.nodeId")
        evaluate("right.value = 'new right';")
        platform.dispatcher.drain()
        assertEquals(1, jsInt("renders"))
        evaluate("chooseLeft.value = false;")
        platform.dispatcher.drain()
        assertEquals(2, jsInt("renders"))
        assertEquals("new right", renderer.text(id))
        evaluate("left.value = 'unused';")
        platform.dispatcher.drain()
        assertEquals(2, jsInt("renders"))
        assertEquals(2, renderer.batches.size)
        evaluate("right.value = 'active';")
        platform.dispatcher.drain()
        assertEquals(3, jsInt("renders"))
        assertEquals("active", renderer.text(id))
    }

    @Test
    fun renderExceptionKeepsPublishedUiAndRemainsReactiveForRecovery() {
        evaluate("""
            var message = compose.state('accepted'), fail = compose.state(false), renders = 0;
            var session = compose.mount(function renderPage() {
                renders++;
                var text = message.value;
                if (fail.value) throw new Error('planned render failure');
                return compose.Text({key:'message', text:text});
            });
        """.trimIndent())
        platform.dispatcher.drain()
        val renderer = platform.renderers.single()
        val original = evaluate("session.root")
        val id = jsInt("session.root.nodeId")
        evaluate("fail.value = true; message.value = 'recovered';")
        platform.dispatcher.drain()
        assertEquals(2, jsInt("renders"))
        assertEquals(1, renderer.batches.size)
        assertEquals("accepted", renderer.text(id))
        assertEquals("accepted", evaluate("session.root.text"))
        assertSame(original, evaluate("session.root"))
        assertEquals(ComposeUiErrorCodes.RENDER_FAILED, (platform.errors.single() as ComposeUiError).code)

        evaluate("fail.value = false;")
        platform.dispatcher.drain()
        assertEquals(3, jsInt("renders"))
        assertEquals(2, renderer.batches.size)
        assertEquals("recovered", renderer.text(id))
        assertSame(original, evaluate("session.root"))
    }

    @Test
    fun refsPublishAfterReceiptAndReuseTheOriginalKeyedHandle() {
        evaluate("""
            var value = compose.state(0), ref = compose.ref();
            var session = compose.mount(function() {
                return compose.Column({key:'root'}, [
                    compose.Text({key:'value', ref:ref, text:String(value.value)})
                ]);
            });
        """.trimIndent())
        assertTrue(jsBoolean("ref.current === null"))
        platform.dispatcher.drain()
        val original = evaluate("ref.current")
        val id = jsInt("ref.current.nodeId")
        assertTrue(jsBoolean("ref.current === session.root.children[0]"))
        evaluate("value.value=1;")
        platform.dispatcher.drain()
        assertSame(original, evaluate("ref.current"))
        assertEquals(id, jsInt("ref.current.nodeId"))
        assertEquals("1", evaluate("ref.current.text"))
        evaluate("session.close();")
        assertTrue(jsBoolean("ref.current === null"))
    }

    @Test
    fun precreatedManualNodesSurviveRenderReconciliationAndFurtherUpdates() {
        evaluate("""
            var manual = compose.Text({key:'manual', text:'original'}), count = compose.state(0);
            var session = compose.mount(function() {
                return compose.Column({key:'container'}, [manual, compose.Text({key:'count', text:String(count.value)})]);
            });
        """.trimIndent())
        platform.dispatcher.drain()
        val renderer = platform.renderers.single()
        val id = jsInt("manual.nodeId")
        val original = evaluate("manual")
        evaluate("manual.text = 'edited manually';")
        platform.dispatcher.drain()
        evaluate("count.value = 1;")
        platform.dispatcher.drain()
        assertSame(original, evaluate("session.root.children[0]"))
        assertEquals(id, jsInt("manual.nodeId"))
        assertEquals("edited manually", renderer.text(id))
        assertTrue(jsBoolean("manual.parent === session.root && manual.text === 'edited manually'"))
        assertTrue(platform.errors.isEmpty())
    }

    @Test
    fun failedReplacementMountPreservesThePreviousSession() {
        evaluate("var original = compose.mount(compose.Text({text:'keep'}));")
        platform.dispatcher.drain()
        val renderer = platform.renderers.single()
        assertTrue(jsBoolean("""
            (function() {
                try { compose.mount(function() { throw new Error('cannot render'); }); return false; }
                catch (error) { return error instanceof ComposeError && error.code === 'RENDER_FAILED'; }
            })()
        """.trimIndent()))
        assertTrue(jsBoolean("!original.isClosed() && original.root.text === 'keep' && compose.sessions.length === 1"))
        assertEquals(0, renderer.disposals)
        assertEquals(1, platform.renderers.last().disposals)
    }

    @Test
    fun nonNodeRenderResultReportsInvalidArgumentWithoutReplacingUi() {
        evaluate("var original = compose.mount(compose.Text({text:'keep'}));")
        platform.dispatcher.drain()
        assertTrue(jsBoolean("""
            (function() {
                try { compose.mount(function() { return 42; }); return false; }
                catch (error) { return error instanceof ComposeError && error.code === 'INVALID_ARGUMENT'; }
            })()
        """.trimIndent()))
        assertTrue(jsBoolean("!original.isClosed() && original.root.text === 'keep'"))
        assertEquals(0, platform.renderers.first().disposals)
        assertEquals(1, platform.renderers.last().disposals)
    }

    @Test
    fun rendererRejectionRollsBackHandlesAndRefsBeforeTheNextSuccessfulRender() {
        evaluate("""
            var value=compose.state(0), ref=compose.ref();
            var session=compose.mount(function(){return compose.Text({key:'value',ref:ref,text:String(value.value)});});
        """.trimIndent())
        platform.dispatcher.drain()
        val renderer = platform.renderers.single()
        val original = evaluate("ref.current")
        val id = jsInt("ref.current.nodeId")
        renderer.rejectNext = true
        evaluate("value.value=1;")
        platform.dispatcher.drain()
        assertEquals(1, renderer.batches.size)
        assertEquals("0", renderer.text(id))
        assertEquals("0", evaluate("session.root.text"))
        assertSame(original, evaluate("ref.current"))
        assertEquals(ComposeUiErrorCodes.RENDER_FAILED, (platform.errors.single() as ComposeUiError).code)

        evaluate("value.value=2;")
        platform.dispatcher.drain()
        assertEquals(2, renderer.batches.size)
        assertEquals("2", renderer.text(id))
        assertSame(original, evaluate("ref.current"))
    }

    @Test
    fun anInitiallyFailingMountReleasesItsReactionInsteadOfRetryingAfterClose() {
        evaluate("""
            var failedInput=compose.state(0), attempts=0, failureCode=null;
            try {
                compose.mount(function(){ attempts++; var read=failedInput.value; throw new Error('initial failure'); });
            } catch (error) { failureCode=error.code; }
        """.trimIndent())
        assertEquals(ComposeUiErrorCodes.RENDER_FAILED, evaluate("failureCode"))
        assertEquals(1, platform.renderers.single().disposals)
        evaluate("failedInput.value=1;")
        platform.dispatcher.drain()
        assertEquals(1, jsInt("attempts"))
        assertEquals(0, jsInt("compose.sessions.length"))
    }

    @Test
    fun callableModuleReplacesOneMountAndCloseListenersRunOnce() {
        evaluate("var closeCount=0, closePart='cl'; var first=compose.mount(compose.Text({text:'first'})); first.on(closePart+'ose',function(){closeCount++;});")
        platform.dispatcher.drain()
        val firstRenderer = platform.renderers.single()
        evaluate("var second=compose(compose.Text({text:'second'})); second.on('close',function(){closeCount++;});")
        platform.dispatcher.drain()
        val secondRenderer = platform.renderers.last()
        assertNotSame(firstRenderer, secondRenderer)
        assertTrue(jsBoolean("first.isClosed() && !second.isClosed() && closeCount === 1 && compose.sessions.length === 1"))
        assertEquals(1, firstRenderer.disposals)
        assertEquals(0, secondRenderer.disposals)
        evaluate("second.close(); second.close(); first.close();")
        assertEquals(2, jsInt("closeCount"))
        assertEquals(1, secondRenderer.disposals)
        assertTrue(jsBoolean("compose.sessions.length === 0"))
        assertFalse(platform.dispatcher.closed)
    }

    @Test
    fun sessionPostDefersMutationAndClosingCancelsQueuedWork() {
        evaluate("var label=compose.Text({text:'before'}), ran=0; var session=compose.mount(label);")
        platform.dispatcher.drain()
        val renderer = platform.renderers.single()
        val id = jsInt("label.nodeId")
        evaluate("session.post(function(){ran++; label.text='posted';});")
        assertEquals(0, jsInt("ran"))
        assertEquals("before", renderer.text(id))
        platform.dispatcher.drain()
        assertEquals(1, jsInt("ran"))
        assertEquals("posted", renderer.text(id))

        evaluate("session.post(function(){ran++;}); compose.post(function(){ran++;});")
        compose.close()
        platform.dispatcher.drain()
        assertEquals(1, jsInt("ran"))
        assertEquals(1, renderer.disposals)
        assertTrue(platform.closed)
        assertTrue(platform.dispatcher.closed)
        assertTrue(jsBoolean("session.isClosed()"))
        assertTrue(jsBoolean("""
            (function() {
                try { label.text='after close'; return false; }
                catch (error) { return error instanceof ComposeError && error.code === 'SESSION_CLOSED'; }
            })()
        """.trimIndent()))
    }

    @Test
    fun themeInputIsCopiedBeforeMountAndLiveThemeUpdatesReachRenderer() {
        evaluate("var input={dark:true,typography:{scale:1.2}}; compose.theme(input); input.dark=false; input.typography.scale=2;")
        evaluate("var session=compose.mount(compose.Text({text:'theme'}));")
        val renderer = platform.renderers.single()
        assertEquals(true, renderer.themes.single().dark)
        assertEquals(1.2, renderer.themes.single().fontScale)
        evaluate("compose.theme({dark:false});")
        assertEquals(false, renderer.themes.last().dark)
        assertEquals(2, renderer.themes.size)
    }

    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "compose-script-api", 1, null)
    private fun jsBoolean(source: String): Boolean = Context.toBoolean(evaluate(source))
    private fun jsInt(source: String): Int = Context.toNumber(evaluate(source)).toInt()

    private class Dispatcher : ScriptUiDispatcher {
        private val origin = Thread.currentThread()
        private val pending = ArrayDeque<() -> Unit>()
        var closed = false
            private set
        override fun isDispatchThread() = Thread.currentThread() === origin
        override fun post(action: () -> Unit) { if (!closed) pending.addLast(action) }
        fun drain() {
            var count = 0
            while (pending.isNotEmpty()) {
                check(count++ < 1000) { "Compose scheduling did not settle" }
                pending.removeFirst().invoke()
            }
        }
        override fun close() { closed = true; pending.clear() }
    }

    private class Platform : ComposeScriptPlatform {
        override val dispatcher = Dispatcher()
        override val mainExecutor = Executor { it.run() }
        val renderers = ArrayList<Renderer>()
        val errors = ArrayList<Throwable>()
        val warnings = ArrayList<String>()
        var available = true
        var closed = false
        override fun version(): Map<String, Any>? = if (available) mapOf("plugin" to "1.0.0", "contract" to 1, "compose" to "1.12.1") else null
        override fun createRenderer(sessionId: Int, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit): ComposeScriptPlatform.Opened {
            if (!available) throw ComposeUiContractException(ComposeUiErrorCodes.PLUGIN_UNAVAILABLE)
            val renderer = Renderer(beforeMount).also(renderers::add)
            return ComposeScriptPlatform.Opened(renderer, ComponentCatalog.V1.components.map { it.name }.toSet())
        }
        override fun resolveImage(value: Any): ComposeScriptValues.ResolvedImage = error("Images are outside this JVM seam")
        override fun post(delayMillis: Long, action: () -> Unit) { dispatcher.post(action) }
        override fun warn(message: String) { warnings += message }
        override fun report(error: Throwable) { errors += error }
        override fun close() { closed = true; dispatcher.close() }
    }

    private class Renderer(private val beforeMount: (Any) -> Unit) : ComposeSessionRenderer {
        val batches = ArrayList<UiPatchBatch>()
        val themes = ArrayList<ThemeSpec>()
        val commands = ArrayList<UiCommand>()
        private val viewIdentity = Any()
        var opens = 0
        var attaches = 0
        var disposals = 0
        var rejectNext = false
        override fun open(sink: ComposeUiEventSink) { opens++ }
        override fun attach() { attaches++; beforeMount(viewIdentity) }
        override fun detach() = Unit
        override fun apply(batch: UiPatchBatch) {
            if (rejectNext) {
                rejectNext = false
                throw ComposeUiContractException(ComposeUiErrorCodes.RENDER_FAILED, "Rejected renderer frame")
            }
            batches += batch
        }
        override fun execute(command: UiCommand) { commands += command }
        override fun setTheme(theme: ThemeSpec) { themes += theme }
        override fun dispose() { disposals++ }

        /** Read the latest full property map sent for this node, without duplicating a node store. */
        fun text(nodeId: Int): String? {
            batches.asReversed().forEach { batch -> batch.patches.asReversed().forEach { patch ->
                val props = when (patch) {
                    is UiPatch.SetProps -> patch.props.takeIf { patch.nodeId == nodeId }
                    is UiPatch.SetRoot -> patch.tree.nodes.firstOrNull { it.nodeId == nodeId }?.props
                    is UiPatch.Insert -> patch.subtree.nodes.firstOrNull { it.nodeId == nodeId }?.props
                    is UiPatch.ReplaceSlot -> patch.subtree?.nodes?.firstOrNull { it.nodeId == nodeId }?.props
                    else -> null
                }
                if (props != null) return (props[ComposeUiProps.TEXT] as? UiValue.Str)?.value
            } }
            return null
        }
    }
}
