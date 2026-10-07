package org.autojs.autojs.theme

import org.autojs.autojs.theme.ThemeColorSource.ACTIVATION
import org.autojs.autojs.theme.ThemeColorSource.INTELLIGENT
import org.autojs.autojs.theme.ThemeColorSource.RESTORE
import org.autojs.autojs.theme.ThemeColorSource.SCRIPT
import org.autojs.autojs.theme.ThemeColorSource.USER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScriptUndoTest {

    private val plain = ScriptUndoSnapshot(
        libraryId = 0x02, itemId = 120, legacyIndex = 122, customColor = null,
        libraryName = null, itemName = null, primary = 0xFF009688.toInt(), dark = 0xFF00796B.toInt(), accent = 0xFF009688.toInt(),
    )

    @Test
    fun snapshotsRoundTrip() {
        assertEquals(plain, ScriptUndoSnapshot.decode(plain.encode()))
        val intelligent = plain.copy(
            libraryId = 0x0200_0003, itemId = 7, customColor = 0xFF123456.toInt(), libraryName = "My \"library\"", itemName = "Teal",
            intelligentSnapshot = """{"libraryId":33554435,"name":"My"}""", intelligentRuntime = """{"currentGroupId":7}""",
        )
        assertEquals(intelligent, ScriptUndoSnapshot.decode(intelligent.encode()))
        assertNull(ScriptUndoSnapshot.decode(null))
        assertNull(ScriptUndoSnapshot.decode("{"))
        assertNull(ScriptUndoSnapshot.decode("""{"libraryId":1}"""))
    }

    /** Replays commits and returns the snapshot index after each one (null: none). zh-CN: 重放提交并返回每次之后的快照序号 (null 表示无快照). */
    private fun replay(vararg sources: ThemeColorSource): List<Int?> {
        var snapshot: Int? = null
        return sources.mapIndexed { index, source ->
            if (ScriptUndoRules.capturesBefore(source, snapshot != null)) snapshot = index
            if (ScriptUndoRules.dropsAfter(source)) snapshot = null
            snapshot
        }
    }

    @Test
    fun theFirstScriptChangeOfARunIsUndone() {
        // Later script changes and intelligent rotations keep the state before the first one.
        // zh-CN: 之后的脚本修改与智能换组保留第一次之前的状态.
        assertEquals(listOf(null, 1, 1, 1, null, 5), replay(USER, SCRIPT, SCRIPT, INTELLIGENT, ACTIVATION, SCRIPT))
        assertEquals(listOf(0, null, 2, null), replay(SCRIPT, RESTORE, SCRIPT, USER))
    }

}
