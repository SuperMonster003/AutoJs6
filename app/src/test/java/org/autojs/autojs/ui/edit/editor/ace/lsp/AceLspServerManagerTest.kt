package org.autojs.autojs.ui.edit.editor.ace.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AceLspServerManagerTest {

    @Test
    fun disabledManagerReturnsEmptyBridgeOptions() {
        val manager = AceLspServerManager { false }

        val snapshot = manager.snapshot()

        assertFalse(snapshot.enabled)
        assertEquals(AceLspServerManager.STATE_DISABLED, snapshot.state)
        assertEquals("{}", manager.bridgeOptionsJson())
    }

    @Test
    fun enabledManagerReportsNoServerFallbackOptions() {
        val manager = AceLspServerManager { true }
        manager.attach()

        val snapshot = manager.snapshot()
        val optionsJson = manager.bridgeOptionsJson()

        assertTrue(snapshot.enabled)
        assertTrue(snapshot.attached)
        assertEquals(1L, snapshot.sessionRevision)
        assertEquals(AceLspServerManager.STATE_CONFIGURED_NO_SERVER, snapshot.state)
        assertEquals(AceLspServerManager.TRANSPORT_UNAVAILABLE, snapshot.transport)
        assertFalse(snapshot.startSupported)
        assertFalse(snapshot.serverAvailable)
        assertEquals(AceLspServerManager.FALLBACK_STATIC_COMPLETION, snapshot.fallback)
        assertEquals(AceLspServerManager.SYNTHETIC_ROOT_URI, snapshot.rootUri)
        assertEquals(AceLspServerManager.SYNTHETIC_DOCUMENT_URI, snapshot.documentUri)

        assertTrue(optionsJson.contains("\"enabled\":true"))
        assertTrue(optionsJson.contains("\"manager\":\"${AceLspServerManager.MANAGER_NAME}\""))
        assertTrue(optionsJson.contains("\"state\":\"${AceLspServerManager.STATE_CONFIGURED_NO_SERVER}\""))
        assertTrue(optionsJson.contains("\"transport\":\"${AceLspServerManager.TRANSPORT_UNAVAILABLE}\""))
        assertTrue(optionsJson.contains("\"serverUri\":null"))
        assertTrue(optionsJson.contains("\"rootUri\":\"${AceLspServerManager.SYNTHETIC_ROOT_URI}\""))
        assertTrue(optionsJson.contains("\"documentUri\":\"${AceLspServerManager.SYNTHETIC_DOCUMENT_URI}\""))
        assertTrue(optionsJson.contains("\"libraryUris\":[\"${AceLspServerManager.DEFAULT_LIBRARY_URIS.first()}\"]"))
        assertTrue(optionsJson.contains("\"fallback\":\"${AceLspServerManager.FALLBACK_STATIC_COMPLETION}\""))
        assertTrue(optionsJson.contains("\"startSupported\":false"))
        assertTrue(optionsJson.contains("\"serverAvailable\":false"))
        assertTrue(optionsJson.contains("\"reason\":\"${AceLspServerManager.REASON_NO_ALPHA_SERVER_ENDPOINT}\""))
    }

    @Test
    fun attachAndDetachAdvanceSessionRevisionOnlyOnStateChange() {
        val manager = AceLspServerManager { true }

        assertEquals(0L, manager.snapshot().sessionRevision)
        manager.attach()
        manager.attach()
        assertEquals(1L, manager.snapshot().sessionRevision)
        manager.detach()
        manager.detach()
        assertEquals(2L, manager.snapshot().sessionRevision)
        assertFalse(manager.snapshot().attached)
    }
}
