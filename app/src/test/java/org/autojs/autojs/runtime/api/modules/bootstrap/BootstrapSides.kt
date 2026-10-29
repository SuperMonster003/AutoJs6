package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino.Side
import org.junit.Assert.assertTrue
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.io.File

/**
 * The two independent scopes of [DifferentialRhino] for roadmap P2.5: the "js" side runs the replaced startup module
 * (the copy of its last version under `src/test/resources/augment-p25/`) the way the engine ran it, the "kt" side
 * installs the Kotlin replacement; [assertSame] compares the rendered results of the same expressions.
 *
 * zh-CN: ROADMAP P2.5 用的 DifferentialRhino 两个独立作用域: "js" 侧按引擎原来的方式执行被替代的启动模块 (`src/test/resources/augment-p25/` 中其最后版本的副本),
 * "kt" 侧安装 Kotlin 实现; [assertSame] 比较同一组表达式的结果.
 */
internal class BootstrapSides : AutoCloseable {

    val rhino = DifferentialRhino()
    val cx: Context get() = rhino.context
    val jsScope: TopLevelScope get() = rhino.jsScope
    val ktScope: TopLevelScope get() = rhino.ktScope
    val jsGlobal: ScriptableObject get() = jsScope.globalThis as ScriptableObject
    val ktGlobal: ScriptableObject get() = ktScope.globalThis as ScriptableObject

    init {
        listOf(jsScope, ktScope).forEach { scope -> ScriptableObject.putProperty(scope.globalThis, "global", scope.globalThis) }
    }

    fun js(source: String): Any? = rhino.evalJs(source)

    fun kt(source: String): Any? = rhino.evalKt(source)

    /** A third scope with the standard objects only, for a case that needs a scope of its own. */
    fun freshScope(): TopLevelScope = TopLevelScope().apply {
        initStandardObjects(cx, false)
        ScriptableObject.putProperty(globalThis, "global", globalThis)
    }

    fun eval(scope: TopLevelScope, source: String): Any? = cx.evaluateString(scope, source, "fresh", 1, null)

    fun both(source: String) {
        js(source)
        kt(source)
    }

    /** The rendered value, or `throws <ErrorName>` without the message. */
    fun render(side: Side, expression: String): String = rhino.render(side, expression).key

    /** The rendered value, or `throws <ErrorName> [message]`. */
    fun renderWithMessage(side: Side, expression: String): String = rhino.render(side, expression).toString()

    /** Runs the CommonJS module [file] in [scope] and returns its `module.exports`. */
    fun module(scope: TopLevelScope, file: File): Any? {
        val wrapper = cx.evaluateString(scope, "(function (module, exports) {\n${file.readText()}\n})", file.name, 0, null) as Callable
        val module = cx.newObject(scope)
        val exports = cx.newObject(scope)
        ScriptableObject.putProperty(module, "exports", exports)
        wrapper.call(cx, scope, scope.globalThis, arrayOf(module, exports))
        return ScriptableObject.getProperty(module, "exports")
    }

    /** The replaced module [name] as it was before P2.5. */
    fun original(name: String): File = File(ORIGINALS, name)

    /** The module [name] as the APK ships it now. */
    fun asset(name: String): File = File(ASSETS, name)

    /** The module [name] of the Bundled Libraries plugin, from its copy in the test resources (P5.2 removed it from the APK). */
    fun plugin(name: String): File = File(PLUGIN, name)

    /** The module [name] as the APK shipped it last before P5.2 removed it. */
    fun obsolete(name: String): File = File(OBSOLETE, name)

    /** Every expression renders the same on both sides (error messages are not compared). */
    fun assertSame(expressions: List<String>) {
        val failures = expressions.mapNotNull { expression ->
            val js = render(Side.JS, expression)
            val kt = render(Side.KT, expression)
            if (js == kt) null else "$expression\n    js $js\n    kt $kt"
        }
        assertTrue("Different results:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** Each expression renders as expected on [side]. */
    fun assertRenders(side: Side, cases: List<Pair<String, String>>) {
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = renderWithMessage(side, expression)
            if (actual == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results on $side:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    override fun close() = rhino.close()

    companion object {
        const val ORIGINALS = "src/test/resources/augment-p25"
        const val ASSETS = "src/main/assets/modules"
        const val PLUGIN = "src/test/resources/script-modules/bundled-libraries"
        const val OBSOLETE = "src/test/resources/script-modules/obsolete"
    }

}
