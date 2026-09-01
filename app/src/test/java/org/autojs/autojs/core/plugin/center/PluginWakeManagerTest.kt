package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginWakeManagerTest {

    @Test
    fun `selects relative activity from an application id suffix variant`() {
        assertEquals(
            "io.github.example.plugin.WakeActivity",
            PluginWakeManager.selectWakeActivityClassName(
                packageName = "io.github.example.plugin.mobile",
                configuredName = ".WakeActivity",
                declaredNames = listOf("io.github.example.plugin.WakeActivity"),
            ),
        )
    }

    @Test
    fun `selects conventional relative and simple activity names`() {
        val declared = listOf("io.github.example.plugin.WakeActivity")
        assertEquals(
            declared.single(),
            PluginWakeManager.selectWakeActivityClassName(
                packageName = "io.github.example.plugin",
                configuredName = ".WakeActivity",
                declaredNames = declared,
            ),
        )
        assertEquals(
            declared.single(),
            PluginWakeManager.selectWakeActivityClassName(
                packageName = "io.github.example.plugin",
                configuredName = "WakeActivity",
                declaredNames = declared,
            ),
        )
    }

    @Test
    fun `rejects undeclared or ambiguous metadata activities`() {
        assertNull(
            PluginWakeManager.selectWakeActivityClassName(
                packageName = "io.github.example.plugin.mobile",
                configuredName = ".WakeActivity",
                declaredNames = emptyList(),
            ),
        )
        assertNull(
            PluginWakeManager.selectWakeActivityClassName(
                packageName = "io.github.example.plugin.mobile",
                configuredName = ".WakeActivity",
                declaredNames = listOf("one.WakeActivity", "two.WakeActivity"),
            ),
        )
    }
}
