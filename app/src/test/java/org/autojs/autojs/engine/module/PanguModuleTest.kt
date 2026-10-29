package org.autojs.autojs.engine.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.commonjs.module.RequireBuilder
import java.io.File

class PanguModuleTest {

    @Test
    fun bundledTextApiLoadsWithoutBrowserOrNodeGlobals() = withContext { context ->
        val scope = createScope(context)
        assertEquals("10.1.0", evaluate(context, scope, "require('pangu').version"))
        assertEquals("object", evaluate(context, scope, "typeof require('pangu')"))
        assertEquals("undefined", evaluate(context, scope, "typeof document"))
        assertEquals("undefined", evaluate(context, scope, "typeof process"))
        assertEquals("undefined", evaluate(context, scope, "typeof require('pangu').spacePage"))
        assertEquals("undefined", evaluate(context, scope, "typeof require('pangu').spaceFile"))
        assertEquals("undefined", evaluate(context, scope, "typeof require('pangu').spacingText"))
    }

    @Test
    fun latestTextRulesWorkInRhino() = withContext { context ->
        val scope = createScope(context)
        val cases = listOf(
            "" to "",
            "中文" to "中文",
            "AutoJs6" to "AutoJs6",
            "使用AutoJs6处理文本" to "使用 AutoJs6 处理文本",
            "今天写了10行JavaScript代码" to "今天写了 10 行 JavaScript 代码",
            "中文 JavaScript" to "中文 JavaScript",
            "中文\nJavaScript" to "中文\nJavaScript",
            "中文\u00a0JavaScript" to "中文\u00a0JavaScript",
            "中文/英文" to "中文/英文",
            "使用C++开发" to "使用 C++ 开发",
            "使用Disney+服务" to "使用 Disney+ 服务",
            "公視+上架了新片" to "公視+ 上架了新片",
            "血型是AB-的人" to "血型是 AB- 的人",
            "HiNet光世代+MOD+影劇館+/全選" to "HiNet 光世代 + MOD + 影劇館+/全選",
            "访问https://example.com/中文路径" to "访问 https://example.com/中文路径",
            "中文<a>测试abc</a>结束" to "中文<a>测试 abc</a>结束",
            "版本( AutoJs6 )发布" to "版本 (AutoJs6) 发布",
        )
        for ((input, expected) in cases) {
            ScriptableObject.putProperty(scope.globalThis, "input", input)
            assertEquals(input, expected, evaluate(context, scope, "require('pangu').spaceText(input)"))
            assertEquals(input, input == expected, evaluate(context, scope, "require('pangu').hasProperSpacing(input)"))
            ScriptableObject.putProperty(scope.globalThis, "spaced", expected)
            assertEquals(input, expected, evaluate(context, scope, "require('pangu').spaceText(spaced)"))
        }
    }

    @Test
    fun moduleAliasesShareAnInstanceButSeparateEnginesDoNot() = withContext { context ->
        val first = createScope(context)
        assertTrue(evaluate(context, first, "require('pangu') === require('pangu.js')") as Boolean)
        assertTrue(evaluate(context, first, "require('pangu') === require('pangu.min.js')") as Boolean)
        val firstModule = evaluate(context, first, "require('pangu')") as Scriptable
        val secondModule = evaluate(context, createScope(context), "require('pangu')") as Scriptable
        assertNotSame(firstModule, secondModule)
    }

    @Test
    fun nonStringInputKeepsTheUpstreamWarningAndPassThroughBehavior() = withContext { context ->
        val scope = createScope(context)
        evaluate(context, scope, "var warnings = []; var console = { warn: function(message) { warnings.push(message); } };")
        assertTrue(evaluate(context, scope, "require('pangu').spaceText(null) === null") as Boolean)
        assertTrue(evaluate(context, scope, "require('pangu').spaceText(42) === 42") as Boolean)
        assertTrue(evaluate(context, scope, "require('pangu').spaceText() === undefined") as Boolean)
        assertEquals(3.0, Context.toNumber(evaluate(context, scope, "warnings.length")), 0.0)
    }

    private fun createScope(context: Context): TopLevel {
        // The plugin's pangu files (tools/pangu writes them to the test resources; P5.2 removed them from the APK).
        // zh-CN: 插件的 pangu 文件 (tools/pangu 输出到测试资源; P5.2 已从 APK 删除).
        val files = listOf(File(PLUGIN_FILES), File("app/$PLUGIN_FILES")).first { it.isDirectory }
        val provider = ProcessWideAssetModuleScriptProvider(object : RhinoAssetLoader {
            override val namespace = "pangu-module-test"
            override fun open(assetPath: String) = File(files, assetPath.removePrefix("modules/")).reader(Charsets.UTF_8)
        }, "modules")
        return context.initStandardObjects().also { scope ->
            RequireBuilder().setModuleScriptProvider(provider).setSandboxed(true)
                .createRequire(context, scope).install(scope)
        }
    }

    private fun evaluate(context: Context, scope: TopLevel, source: String): Any? =
        context.evaluateString(scope, source, "pangu-test", 1, null)

    private fun withContext(block: (Context) -> Unit) {
        Context.enter().use { context ->
            context.languageVersion = Context.VERSION_ES6
            context.isInterpretedMode = true
            block(context)
        }
    }

    private companion object {
        const val PLUGIN_FILES = "src/test/resources/script-modules/bundled-libraries"
    }
}
