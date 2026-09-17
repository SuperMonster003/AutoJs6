package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginResourceResolverTest {

    @Test
    fun `parses local string resource reference`() {
        assertEquals(
            PluginResourceResolver.ResourceReference(
                packageName = null,
                resType = PluginResourceResolver.ResType.STRING,
                resName = "plugin_name",
            ),
            PluginResourceResolver.parseResourceReference("@string/plugin_name"),
        )
    }

    @Test
    fun `parses qualified raw resource reference`() {
        assertEquals(
            PluginResourceResolver.ResourceReference(
                packageName = "org.autojs.plugin.opencc",
                resType = PluginResourceResolver.ResType.RAW,
                resName = "plugin_instruction",
            ),
            PluginResourceResolver.parseResourceReference(
                " @org.autojs.plugin.opencc:raw/plugin_instruction ",
            ),
        )
    }

    @Test
    fun `rejects unsupported or malformed resource reference`() {
        listOf(
            "@drawable/plugin_icon",
            "@raw/",
            "raw/plugin_instruction",
            "@org.autojs.plugin.opencc:/plugin_instruction",
        ).forEach { value ->
            assertNull(value, PluginResourceResolver.parseResourceReference(value))
        }
    }
}
