package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Roadmap appendix C Q19 (P2.4): the errors of the generated layer name a member by its script path, as the module
 * mounter does for Java exceptions: functions of a sub-module as `util.java.array`, an invocable sub-module as
 * `util.inspect` and an invocable module as `species` (before: `java.array`, `inspect()`, `species()`).
 *
 * zh-CN: 附录 C Q19 (P2.4): 生成层的错误以脚本路径称呼成员, 与挂载器对 Java 异常的做法一致: 子模块的函数为 `util.java.array`, 可调用的子模块为
 * `util.inspect`, 可调用模块为 `species` (原为 `java.array`, `inspect()`, `species()`).
 */
class ScriptModuleDisplayNameTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("util", "species", "colors")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun messagesNameTheScriptPath() {
        val cases = listOf(
            "util.inspect(1, {}, 2, false, 'extra')" to "TypeError: Failed to call method \"util.inspect\": [ ",
            "util.java.array()" to "TypeError: Failed to call method \"util.java.array\": [ ",
            "util.morseCode.getCode()" to "TypeError: Failed to call method \"util.morseCode.getCode\": [ ",
            "species()" to "TypeError: Failed to call method \"species\": [ ",
            "colors.theme.set()" to "TypeError: Failed to call method \"colors.theme.set\": [ ",
            "colors.theme.reset(1)" to "TypeError: Failed to call method \"colors.theme.reset\": [ ",
            "util.isString()" to "TypeError: Failed to call method \"util.isString\": [ ",
        )
        val failures = cases.mapNotNull { (expression, expected) ->
            val rendered = rhino.render(DifferentialRhino.Side.KT, expression)
            val actual = "${rendered.key.removePrefix("throws ")}: ${rendered.detail}"
            if (actual.startsWith(expected)) null else "$expression\n    expected $expected...\n    actual   $actual"
        }
        assertTrue("Unexpected messages:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

}
