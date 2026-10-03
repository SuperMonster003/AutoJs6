package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginIconResourcePolicyTest {
    @Test fun dedicatedArtworkPrecedesLegacyArtwork() {
        assertEquals(listOf("ic_plugin_center", "ic_launcher"),
            PluginIconResourcePolicy.candidates("io.github.supermonster003.autojs6.plugin.three.setup.installer"))
        assertEquals(listOf("ic_plugin_center", "ic_launcher_transparent"),
            PluginIconResourcePolicy.candidates("io.github.supermonster003.autojs6.plugin.readium.epub.reader"))
    }

    @Test fun newlyAddedPluginsCanOptInWithoutAHostAllowlistUpdate() {
        assertEquals(listOf("ic_plugin_center"), PluginIconResourcePolicy.candidates("example.new.plugin"))
    }

    @Test fun ordinaryApplicationIconsAreNotAssumedTransparent() {
        assertEquals(listOf("ic_plugin_center"),
            PluginIconResourcePolicy.candidates("io.github.supermonster003.autojs6.plugin.nodejs"))
    }
}
