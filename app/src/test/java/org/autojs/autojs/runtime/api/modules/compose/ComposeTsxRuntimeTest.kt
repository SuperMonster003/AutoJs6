package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.core.plugin.compose.ComposeSessionRenderer
import org.autojs.autojs.core.plugin.compose.ScriptUiDispatcher
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.api.modules.ui.TypeScriptTsxRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiErrorCodes as Codes
import org.autojs.plugin.compose.api.ComposeUiLimits
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor

/** The classic compiler ABI uses real Rhino functions, scopes and the normal Compose transaction path. */
class ComposeTsxRuntimeTest {
    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private lateinit var compose: Compose
    private lateinit var platform: Platform
    private val messages = TypeScriptTsxRuntime.MessageResolver { resource, args -> "$resource:${args.joinToString("|")}" }

    @Before fun enter() {
        context = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        scope = newScope()
        platform = Platform()
        compose = module(scope, platform)
        ScriptableObject.putProperty(scope.globalThis, "__autojs6TsxFragment", RhinoUtils.toJsValue(scope, TypeScriptTsxRuntime.FragmentToken))
        ScriptableObject.putProperty(scope.globalThis, "__autojs6Tsx", RhinoUtils.newBaseFunction(scope, "__autojs6Tsx", { args ->
            RhinoUtils.toJsValue(scope, TypeScriptTsxRuntime.create(args, messages, compose))
        }, RhinoUtils.NOT_CONSTRUCTABLE))
        ScriptableObject.putProperty(scope.globalThis, "xml", RhinoUtils.newBaseFunction(scope, "xml", { args ->
            TypeScriptTsxRuntime.renderOrNull(args[0], messages, compose)?.xml
        }, RhinoUtils.NOT_CONSTRUCTABLE))
    }
    @After fun exit() { try { compose.close() } finally { Context.exit() } }

    @Test fun actualFactoryAliasesExactNamesAndNamespacedStringsUseTheSameNodes() {
        evaluate("""
            var Alias=compose.Text;
            var a=__autojs6Tsx(Alias,{text:'alias',key:'a'});
            var b=__autojs6Tsx('Text',{text:'exact',key:'b'});
            var c=__autojs6Tsx('compose:Text',{text:'qualified',key:'c'});
            var page=compose.mount(__autojs6Tsx(compose.Column,null,a,b,c));
        """)
        platform.dispatcher.drain()
        assertEquals("alias,exact,qualified", evaluate("page.root.children.map(function(n){return n.text;}).join(',')"))
        assertEquals("Column", evaluate("page.root.type"))
        assertTrue(platform.errors.isEmpty())
    }

    @Test fun arbitraryAndForeignFactoryFunctionsAreNeverExecuted() {
        evaluate("var called=0;function Text(){called++;return compose.Text({text:'wrong'});}")
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("__autojs6Tsx(Text,null)"))
        assertEquals(0.0, Context.toNumber(evaluate("called")), 0.0)
        val foreignScope = newScope()
        module(foreignScope, Platform()).use {
            val function = context.evaluateString(foreignScope, "compose.Text", "foreign.tsx", 1, null)
            ScriptableObject.putProperty(scope.globalThis, "foreignFactory", function)
            assertEquals(Codes.INVALID_ARGUMENT, failureCode("__autojs6Tsx(foreignFactory,{text:'foreign'})"))
        }
        assertEquals(Codes.UNKNOWN_COMPONENT, failureCode("__autojs6Tsx('compose:NotAComponent',null)"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("__autojs6Tsx('compose:Snackbar',{message:'command'})"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("__autojs6Tsx('Snackbar',{message:'command'})"))
    }

    @Test fun fragmentsSnapshotRawScalarsThenApplyTheConsumersOwnRules() {
        evaluate("""
            var values=['before',false,null];
            var frozen=__autojs6Tsx(__autojs6TsxFragment,null,values);
            values[0]='after';values.push('later');
            var node=__autojs6Tsx('compose:Column',null,frozen);
            var neutral=__autojs6Tsx(__autojs6TsxFragment,null,3,true,false,'x',null);
            var legacy=__autojs6Tsx('text',null,neutral);
        """)
        assertEquals("before", evaluate("node.children[0].text"))
        assertEquals(1.0, Context.toNumber(evaluate("node.children.length")), 0.0)
        assertEquals("<text text=\"3x\" />", evaluate("xml(legacy)"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("__autojs6Tsx('compose:Column',null,neutral)"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("__autojs6Tsx('compose:Column',null,__autojs6Tsx(__autojs6TsxFragment,null,true))"))
    }

    @Test fun rootAndSlotsAcceptOnlyTheirExistingCardinality() {
        evaluate("""
            var fragment=__autojs6Tsx(__autojs6TsxFragment,null,false,__autojs6Tsx('compose:Text',{text:'root'}));
            var page=compose.mount(fragment);
            var title=__autojs6Tsx(__autojs6TsxFragment,null,'title');
            var empty=__autojs6Tsx(__autojs6TsxFragment,null,null,false,[]);
            var bar=__autojs6Tsx('compose:TopAppBar',{title:title,navigationIcon:empty});
        """)
        platform.dispatcher.drain()
        assertEquals("root", evaluate("page.root.text"))
        val bar = requireNotNull(compose.graph.node(evaluate("bar")))
        assertEquals("title", bar.state.slots.getValue("title").get("text"))
        assertFalse(bar.state.slots.containsKey("navigationIcon"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("compose.mount(empty)"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("compose.mount(__autojs6Tsx(__autojs6TsxFragment,null,'one','two'))"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("bar.slot('title',__autojs6Tsx(__autojs6TsxFragment,null,'one','two'))"))
        assertEquals("title", bar.state.slots.getValue("title").get("text"))
        assertEquals("root", evaluate("page.root.text"))
        evaluate("var textPage=compose.mount(__autojs6Tsx(__autojs6TsxFragment,null,'implicit root'));")
        platform.dispatcher.drain()
        assertEquals("implicit root", evaluate("textPage.root.text"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("compose.mount('ordinary strings still need a node')"))
    }

    @Test fun renderFragmentsRetainKeysRefsAndRecoverWithoutReplacingTheLastFrame() {
        evaluate("""
            var count=compose.state(0), ref=compose.ref();
            var page=compose.mount(function(){
                if(count.value===1)return __autojs6Tsx(__autojs6TsxFragment,null,'bad','roots');
                return __autojs6Tsx(__autojs6TsxFragment,null,__autojs6Tsx('compose:Column',null,
                    __autojs6Tsx(compose.Text,{key:'count',ref:ref,text:String(count.value)})));
            });
        """)
        platform.dispatcher.drain()
        val previous = evaluate("ref.current.nodeId")
        evaluate("count.value=1")
        platform.dispatcher.drain()
        assertEquals("0", evaluate("ref.current.text"))
        assertEquals(1, platform.errors.size)
        assertFalse(Context.toBoolean(evaluate("page.isClosed()")))
        evaluate("count.value=2")
        platform.dispatcher.drain()
        assertEquals("2", evaluate("ref.current.text"))
        assertEquals(previous, evaluate("ref.current.nodeId"))
    }

    @Test fun mixedTreesAndDuplicateChildrenSourcesAreRejectedBeforeReplacingThePage() {
        evaluate("""
            var page=compose.mount(compose.Text({text:'safe'}));
            var xmlNode=__autojs6Tsx('text',{text:'legacy'});
            var composeNode=__autojs6Tsx('compose:Text',{text:'new'});
            var fragment=__autojs6Tsx(__autojs6TsxFragment,null,composeNode);
        """)
        platform.dispatcher.drain()
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("__autojs6Tsx('compose:Column',null,xmlNode)"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("__autojs6Tsx('vertical',null,composeNode)"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("xml(composeNode)"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("xml(fragment)"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("__autojs6Tsx('compose:Column',{children:[composeNode]},composeNode)"))
        assertEquals("safe", evaluate("page.root.text"))
        assertFalse(Context.toBoolean(evaluate("page.isClosed()")))
        assertEquals(1, platform.entries.single().batches.size)
        assertEquals("<text text=\"legacy\" />", evaluate("xml(xmlNode)"))
    }

    @Test fun foreignNodesAndEvenScalarOnlyFragmentsKeepTheirRuntimeIdentity() {
        val foreignScope = newScope()
        module(foreignScope, Platform()).use { foreign ->
            val node = foreign.graph.create("Text", mapOf("text" to "foreign")).js
            val fragment = TypeScriptTsxRuntime.create(arrayOf(TypeScriptTsxRuntime.FragmentToken, null, "scalar"), messages, foreign)
            ScriptableObject.putProperty(scope.globalThis, "foreignNode", node)
            ScriptableObject.putProperty(scope.globalThis, "foreignFragment", RhinoUtils.toJsValue(scope, fragment))
            assertEquals(Codes.NODE_DETACHED, failureCode("__autojs6Tsx('compose:Column',null,foreignNode)"))
            assertEquals(Codes.NODE_DETACHED, failureCode("compose.mount(foreignNode)"))
            assertEquals(Codes.NODE_DETACHED, failureCode("compose.mount(foreignFragment)"))
            assertEquals(Codes.NODE_DETACHED, failureCode("__autojs6Tsx(__autojs6TsxFragment,null,foreignFragment)"))
            assertEquals(Codes.NODE_DETACHED, failureCode("__autojs6Tsx('vertical',null,foreignFragment)"))
        }
    }

    @Test fun cyclesDepthSizeAndIgnoredValuesCannotEscapeTheFragmentBudget() {
        val childLimit = ComposeUiLimits.MAX_LAZY_CHILDREN_PER_NODE
        val depthLimit = ComposeUiLimits.MAX_DEPTH
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("var cycle=[];cycle.push(cycle);__autojs6Tsx(__autojs6TsxFragment,null,cycle)"))
        assertEquals(Codes.INVALID_ARGUMENT, failureCode("var cycle=[];cycle.push(cycle);__autojs6Tsx('compose:Column',null,cycle)"))
        evaluate("var deep=__autojs6Tsx(__autojs6TsxFragment,null,compose.Text({text:'deep'}));for(var i=1;i<$depthLimit;i++)deep=__autojs6Tsx(__autojs6TsxFragment,null,deep);")
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("__autojs6Tsx(__autojs6TsxFragment,null,deep)"))
        evaluate("var page=compose.mount(deep);")
        platform.dispatcher.drain()
        assertEquals("deep", evaluate("page.root.text"))
        // A sparse array at the actual input limit is legal: ignored entries create no UI nodes.
        evaluate("var sparse=__autojs6Tsx(__autojs6TsxFragment,null,new Array($childLimit));var empty=__autojs6Tsx('compose:Column',null,sparse);")
        assertEquals(0.0, Context.toNumber(evaluate("empty.children.length")), 0.0)
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("__autojs6Tsx(__autojs6TsxFragment,null,new Array(${childLimit + 1}))"))
        // One root array plus childLimit arrays/ignored values reaches the traversal budget.
        evaluate("var atBudget=[];for(var i=0;i<$childLimit;i++)atBudget.push([false]);__autojs6Tsx('compose:Column',{children:atBudget});")
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("atBudget[0].push(false);__autojs6Tsx('compose:Column',{children:atBudget})"))
        assertEquals(Codes.LIMIT_EXCEEDED, failureCode("var arrays='x';for(var i=0;i<${depthLimit + 1};i++)arrays=[arrays];__autojs6Tsx(__autojs6TsxFragment,null,arrays)"))
        assertEquals("deep", evaluate("page.root.text"))
    }

    private fun newScope() = TopLevelScope().apply {
        initStandardObjects(context, false)
        with(RhinoUtils) { context.registerStandardObjects(this@apply) }
    }
    private fun module(scope: TopLevelScope, platform: Platform) = Compose(RhinoTestRuntime.create(scope), platform).also { it.augment(scope.globalThis) }
    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "compose-tsx-classic.js", 1, null)
    private fun failureCode(source: String): Any? = evaluate("(function(){try{$source;return null;}catch(e){if(!(e instanceof ComposeError))throw e;return e.code;}})()")

    private class Dispatcher : ScriptUiDispatcher {
        private val thread = Thread.currentThread()
        private val pending = ConcurrentLinkedQueue<() -> Unit>()
        override fun isDispatchThread() = Thread.currentThread() === thread
        override fun post(action: () -> Unit) { pending += action }
        fun drain() { var count = 0; while (true) { val action = pending.poll() ?: return; check(++count < 1000); action() } }
        override fun close() { pending.clear() }
    }
    private class Platform : ComposeScriptPlatform {
        override val dispatcher = Dispatcher()
        override val mainExecutor = Executor { it.run() }
        val entries = ArrayList<Entry>()
        val errors = ArrayList<Throwable>()
        override fun version() = mapOf<String, Any>("plugin" to "test", "compose" to "test", "contract" to 1)
        override fun createRenderer(sessionId: Int, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit): ComposeScriptPlatform.Opened {
            val entry = Entry(beforeMount).also(entries::add)
            return ComposeScriptPlatform.Opened(entry, ComponentCatalog.V1.components.map { it.name }.toSet())
        }
        override fun resolveImage(value: Any): ComposeScriptValues.ResolvedImage = error("No images in this fixture")
        override fun post(delayMillis: Long, action: () -> Unit) = dispatcher.post(action)
        override fun warn(message: String) = Unit
        override fun report(error: Throwable) { errors += error }
        override fun close() = dispatcher.close()
    }
    private class Entry(private val mount: (Any) -> Unit) : ComposeSessionRenderer {
        val batches = ArrayList<UiPatchBatch>()
        override fun open(sink: ComposeUiEventSink) = Unit
        override fun attach() { mount(Any()) }
        override fun detach() = Unit
        override fun apply(batch: UiPatchBatch) { batches += batch }
        override fun execute(command: UiCommand) = Unit
        override fun setTheme(theme: ThemeSpec) = Unit
        override fun dispose() = Unit
    }
}
