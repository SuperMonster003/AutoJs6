package org.autojs.plugin.tool.api

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The result ceilings of PluginToolProviderService (MCP roadmap D65): UTF-16 units, as the Parcel of the oneway report holds
 * them, and UTF-8 bytes, both over the JSON and the text together.
 *
 * zh-CN: 提供者基类的结果上限: UTF-16 单元 (oneway 回报的 Parcel 按此存放) 与 UTF-8 字节, 都按 JSON 与文本合计.
 */
class PluginToolResultLimitTest {

    @Test
    fun theUnitCeilingCoversJsonAndTextTogether() {
        val ceiling = PluginToolLimits.MAX_RESULT_CHARS
        assertNull(result(ceiling - 1000, 1000).limitProblem())
        val problem = result(ceiling - 1000, 1001).limitProblem()!!
        assertTrue(problem, problem.contains("${ceiling + 1} UTF-16 units"))
        assertTrue(problem, result(ceiling + 1, 0).limitProblem()!!.contains("UTF-16 units"))
    }

    @Test
    fun surrogatePairsCountTwice() {
        val emoji = String(Character.toChars(0x1F600))
        val text = emoji.repeat(PluginToolLimits.MAX_RESULT_CHARS / 2)
        assertNull(PluginToolResult("{}").withText(text.dropLast(2)).limitProblem())
        // 4 UTF-8 bytes each, so the bytes stay below their ceiling while the units break theirs.
        assertTrue(PluginToolResult("{}").withText(text).limitProblem()!!.contains("UTF-16 units"))
    }

    @Test
    fun wideCharactersReachTheByteCeilingFirst() {
        // Three UTF-8 bytes each: the bytes break their ceiling well before the units.
        val han = 0x6C49.toChar().toString()
        val text = han.repeat(PluginToolLimits.MAX_RESULT_BYTES / 3 + 1)
        assertNull(PluginToolResult("{}").withText(text.drop(1)).limitProblem())
        val problem = PluginToolResult("{}").withText(text).limitProblem()!!
        assertTrue(problem, problem.contains("${PluginToolLimits.MAX_RESULT_BYTES}") && problem.contains("bytes"))
    }

    private fun result(jsonUnits: Int, textUnits: Int) =
        PluginToolResult("[" + "1".repeat(jsonUnits - 2) + "]").withText(if (textUnits > 0) "y".repeat(textUnits) else null)
}
