package org.autojs.autojs.ui.explorer

import org.autojs.autojs.util.FileUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerItemActionPolicyTest {

    @Test
    fun executableTextFilePrioritizesEditAndRunWithoutRedundantInfo() {
        val state = resolve(FileUtils.TYPE.JAVASCRIPT)

        assertNull(state.primaryAction)
        assertTrue(state.editVisible)
        assertTrue(state.runVisible)
        assertFalse(state.runAsMedia)
        assertFalse(state.installVisible)
        assertFalse(state.infoVisible)
    }

    @Test
    fun supportedAudioUsesPrimaryPlayerInsteadOfLegacyRunButton() {
        val state = resolve(FileUtils.TYPE.MP3)

        assertSame(ExplorerPrimaryAction.MEDIA_PLAY, state.primaryAction)
        assertFalse(state.editVisible)
        assertFalse(state.runVisible)
        assertFalse(state.runAsMedia)
        assertFalse(state.installVisible)
        assertTrue(state.infoVisible)
    }

    @Test
    fun m3uPlaylistsKeepOnlyPlayAndEditEvenWithoutInstalledPlugins() {
        listOf(FileUtils.TYPE.M3U_AUDIO, FileUtils.TYPE.M3U_VIDEO).forEach { type ->
            val state = resolve(type)

            assertSame(ExplorerPrimaryAction.MEDIA_PLAY, state.primaryAction)
            assertTrue(state.editVisible)
            assertFalse(state.runVisible)
            assertFalse(state.runAsMedia)
            assertFalse(state.installVisible)
            assertFalse(state.infoVisible)
        }
    }

    @Test
    fun imageAlwaysKeepsInformationActionBesideItsViewer() {
        val state = resolve(FileUtils.TYPE.PNG)

        assertSame(ExplorerPrimaryAction.IMAGE_VIEW, state.primaryAction)
        assertFalse(state.editVisible)
        assertFalse(state.runVisible)
        assertTrue(state.infoVisible)
    }

    private fun resolve(type: FileUtils.TYPE) = ExplorerItemActionPolicy.resolve(
        item = TestExplorerItem("/workspace/item.${type.typeName}", type),
        pluginPrimaryAction = null,
    )
}
