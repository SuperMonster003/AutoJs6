package org.autojs.autojs.ui.edit.editor.engine

import org.autojs.autojs.ui.edit.editor.ace.health.AceFailureType
import org.autojs.autojs.ui.edit.editor.ace.health.AceHealthState
import org.autojs.plugin.editor.api.EditorPluginBreakpoint
import org.autojs.plugin.editor.api.EditorPluginFailure
import org.autojs.plugin.editor.api.EditorPluginFailureType
import org.autojs.plugin.editor.api.EditorPluginHealthState
import org.autojs.plugin.editor.api.EditorPluginSearchOptions
import org.autojs.plugin.editor.api.EditorPluginSelection
import org.autojs.plugin.editor.api.EditorPluginSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class AceEditorPluginMappingsTest {

    @Test
    fun `search options preserve host semantics`() {
        val host = EditorSearchOptions(usingRegex = true, caseSensitive = true, wholeWord = true)

        val plugin = AceEditorPluginMappings.toPlugin(host)

        assertEquals(EditorPluginSearchOptions(true, true, true), plugin)
        assertEquals(host, AceEditorPluginMappings.toHost(plugin))
    }

    @Test
    fun `snapshot round trip preserves editor state`() {
        val plugin = EditorPluginSnapshot(
            text = "const answer = 42",
            dirty = true,
            cursorLine = 3,
            cursorColumn = 7,
            selection = EditorPluginSelection(2, 9, 0, 2, 1, 4),
            firstVisibleLine = 1,
            firstVisibleColumn = 2,
            breakpoints = listOf(EditorPluginBreakpoint(4, true), EditorPluginBreakpoint(8, false)),
            debuggingLine = 4,
            readOnly = true,
            fontSizeSp = 17.5f,
            themeId = "tomorrow_night",
            searchQuery = "answer",
            searchOptions = EditorPluginSearchOptions(usingRegex = false, caseSensitive = false),
        )

        val roundTrip = AceEditorPluginMappings.toPlugin(AceEditorPluginMappings.toHost(plugin))

        assertEquals(plugin, roundTrip)
    }

    @Test
    fun `failure preserves health and failure metadata`() {
        val plugin = EditorPluginFailure(
            type = EditorPluginFailureType.HEARTBEAT_TIMEOUT,
            message = "heartbeat lost",
            detail = "revision=12",
            state = EditorPluginHealthState.DEGRADED,
            fatal = true,
            timestampUptimeMillis = 1234L,
        )

        val host = AceEditorPluginMappings.toHost(plugin)

        assertEquals(AceFailureType.HEARTBEAT_TIMEOUT, host.type)
        assertEquals(AceHealthState.DEGRADED, host.state)
        assertEquals(plugin.message, host.message)
        assertEquals(plugin.detail, host.detail)
        assertEquals(plugin.fatal, host.fatal)
        assertEquals(plugin.timestampUptimeMillis, host.timestampUptimeMillis)
    }
}
