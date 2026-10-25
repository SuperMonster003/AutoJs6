package org.autojs.autojs.runtime.api.modules.installer

import com.google.gson.JsonNull
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.installer.InstallerScriptArguments
import org.autojs.autojs.runtime.api.installer.InstallerScriptOptions
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class InstallerJsArgumentsTest {
    private lateinit var cx: Context
    private lateinit var scope: TopLevelScope

    @Before fun enter() {
        cx = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        scope = TopLevelScope().apply { initStandardObjects(cx, false) }
    }

    @After fun exit() { Context.exit() }

    @Test fun fileWrappersAndNestedSplitArraysKeepTheirSourceMeaning() {
        val file = File("Unicode 测试.apk")
        ScriptableObject.putProperty(scope, "file", Context.javaToJS(file, scope))
        val plain = InstallerJsArguments.source(evaluate("[{splits: [file, 'content://provider/split%20one.apk']}]")) as List<*>
        val values = (plain.single() as Map<*, *>)["splits"] as List<*>
        assertEquals(file, values[0])
        assertEquals("content://provider/split%20one.apk", values[1])
        val plan = InstallerScriptArguments.sources(plain, File(".").absolutePath)
        assertTrue(plan.isBatch)
        assertEquals(2, plan.items.single().size)
        assertFalse(InstallerScriptArguments.sources(plain.single(), null).isBatch)
    }

    @Test fun snapshotsAreDetachedFromSubsequentScriptAndJavaMutations() {
        val input = evaluate("var source = { splits: ['base.apk', 'split.apk'] }; source")
        val snapshot = InstallerJsArguments.source(input) as Map<*, *>
        evaluate("source.splits[0] = 'changed.apk'; source.splits.push('extra.apk')")
        assertEquals(listOf("base.apk", "split.apk"), snapshot["splits"])
        val mutable = AtomicInteger(42)
        val scalar = InstallerJsArguments.json(mutable)
        mutable.set(12)
        assertEquals(42, scalar!!.asInt)
    }

    @Test fun omittedAndUndefinedOptionsDifferFromExplicitNull() {
        assertNull(InstallerJsArguments.optional(emptyArray(), 0))
        assertNull(InstallerJsArguments.json(Undefined.instance))
        assertEquals(JsonNull.INSTANCE, InstallerJsArguments.json(null))
        assertEquals("auto", InstallerScriptOptions.install(InstallerJsArguments.json(Undefined.instance)).interaction)
        invalid { InstallerScriptOptions.install(InstallerJsArguments.json(null)) }
    }

    @Test fun sparseArraysFunctionsAndUnsupportedObjectsAreRejectedBeforeWorkStarts() {
        for (source in listOf("[, 'a.apk']", "[function(){}]", "new Date()", "({splits: ['a'], extra: function(){}})", "new (function Custom(){this.splits=['a'];})()")) {
            invalid { InstallerJsArguments.source(evaluate(source)) }
        }
        invalid { InstallerJsArguments.source(Any()) }
        invalid { InstallerJsArguments.json(evaluate("({timeout: function(){ return 5 }})")) }
    }

    @Test fun cyclesAndWrapperCyclesCannotEscapeTheOriginThreadSnapshot() {
        invalid { InstallerJsArguments.source(evaluate("var a=[]; a.push(a); a")) }
        invalid { InstallerJsArguments.json(evaluate("var o={}; o.self=o; o")) }
        val wrapped = object : Wrapper { override fun unwrap(): Any = this }
        invalid { InstallerJsArguments.source(wrapped) }
    }

    @Test fun plainNullPrototypeObjectsWorkAndToJsonIsNeverInvoked() {
        val value = evaluate("var o=Object.create(null); o.splits=['base.apk']; o")
        assertEquals(listOf("base.apk"), (InstallerJsArguments.source(value) as Map<*, *>)["splits"])
        invalid { InstallerJsArguments.source(evaluate("({splits:['base.apk'],toJSON:function(){throw 'must not run'}})")) }
    }

    @Test fun oversizedAndNonFiniteInputsAreRejected() {
        invalid { InstallerJsArguments.source(evaluate("Array(65).fill('a.apk')")) }
        invalid { InstallerJsArguments.json(evaluate("({timeout: NaN})")) }
        invalid { InstallerJsArguments.json(evaluate("({timeout: Infinity})")) }
        invalid { InstallerJsArguments.source(evaluate("var a='a'; for(var i=0;i<20;i++)a=[a]; a")) }
    }

    private fun evaluate(source: String): Any? = cx.evaluateString(scope, source, "test.js", 1, null)
    private fun invalid(block: () -> Unit) {
        try { block(); fail("expected INVALID_ARGUMENT") } catch (failure: InstallerPluginException) {
            assertEquals(InstallerErrorCodes.INVALID_ARGUMENT, failure.code)
        }
    }
}
