package org.autojs.autojs.ui.edit.editor.ace.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AceLspServerManagerTest {

    @Test
    fun disabledManagerReturnsEmptyBridgeOptions() {
        val manager = manager(enabled = false)

        val snapshot = manager.snapshot()

        assertFalse(snapshot.enabled)
        assertEquals(AceLspServerManager.STATE_DISABLED, snapshot.state)
        assertEquals("{}", manager.bridgeOptionsJson())
    }

    @Test
    fun enabledManagerReportsLocalLanguageServiceOptions() {
        val manager = manager(enabled = true)
        manager.attach()

        val snapshot = manager.snapshot()
        val optionsJson = manager.bridgeOptionsJson()

        assertTrue(snapshot.enabled)
        assertTrue(snapshot.attached)
        assertEquals(1L, snapshot.sessionRevision)
        assertEquals(AceLspServerManager.STATE_LOCAL_LANGUAGE_SERVICE, snapshot.state)
        assertEquals(AceLspServerManager.TRANSPORT_IN_PROCESS, snapshot.transport)
        assertFalse(snapshot.startSupported)
        assertFalse(snapshot.serverAvailable)
        assertEquals(AceLspServerManager.FALLBACK_STATIC_COMPLETION, snapshot.fallback)
        assertEquals(AceLspServerManager.COMPLETION_PROVIDER_LOCAL_INDEX, snapshot.completionProvider)
        assertEquals(AceLspServerManager.HOVER_PROVIDER_LOCAL_INDEX, snapshot.hoverProvider)
        assertEquals(AceLspServerManager.DIAGNOSTIC_PROVIDER_ACE_JSHINT, snapshot.diagnosticProvider)
        assertEquals(AceLspServerManager.SIGNATURE_PROVIDER_STATIC_LOCAL, snapshot.signatureProvider)
        assertEquals(AceLspServerManager.DEFAULT_FEATURES, snapshot.features)
        assertEquals(AceLspServerManager.MAX_DOCUMENT_LENGTH, snapshot.maxDocumentLength)
        assertEquals(AceLspServerManager.SYNTHETIC_ROOT_URI, snapshot.rootUri)
        assertEquals(AceLspServerManager.SYNTHETIC_DOCUMENT_URI, snapshot.documentUri)

        assertTrue(optionsJson.contains("\"enabled\":true"))
        assertTrue(optionsJson.contains("\"manager\":\"${AceLspServerManager.MANAGER_NAME}\""))
        assertTrue(optionsJson.contains("\"state\":\"${AceLspServerManager.STATE_LOCAL_LANGUAGE_SERVICE}\""))
        assertTrue(optionsJson.contains("\"transport\":\"${AceLspServerManager.TRANSPORT_IN_PROCESS}\""))
        assertTrue(optionsJson.contains("\"serverUri\":null"))
        assertTrue(optionsJson.contains("\"rootUri\":\"${AceLspServerManager.SYNTHETIC_ROOT_URI}\""))
        assertTrue(optionsJson.contains("\"documentUri\":\"${AceLspServerManager.SYNTHETIC_DOCUMENT_URI}\""))
        AceLspServerManager.DEFAULT_LIBRARY_URIS.forEach { uri ->
            assertTrue(optionsJson.contains("\"$uri\""))
        }
        assertTrue(optionsJson.contains("\"fallback\":\"${AceLspServerManager.FALLBACK_STATIC_COMPLETION}\""))
        assertTrue(optionsJson.contains("\"startSupported\":false"))
        assertTrue(optionsJson.contains("\"serverAvailable\":false"))
        assertTrue(optionsJson.contains("\"reason\":\"${AceLspServerManager.REASON_LOCAL_LANGUAGE_SERVICE}\""))
        assertTrue(optionsJson.contains("\"completionProvider\":\"${AceLspServerManager.COMPLETION_PROVIDER_LOCAL_INDEX}\""))
        assertTrue(optionsJson.contains("\"hoverProvider\":\"${AceLspServerManager.HOVER_PROVIDER_LOCAL_INDEX}\""))
        assertTrue(optionsJson.contains("\"diagnosticProvider\":\"${AceLspServerManager.DIAGNOSTIC_PROVIDER_ACE_JSHINT}\""))
        assertTrue(optionsJson.contains("\"signatureProvider\":\"${AceLspServerManager.SIGNATURE_PROVIDER_STATIC_LOCAL}\""))
        assertTrue(optionsJson.contains("\"features\":[\"completion\", \"hover\", \"diagnostics\", \"signatureHelp\"]"))
        assertTrue(optionsJson.contains("\"maxDocumentLength\":${AceLspServerManager.MAX_DOCUMENT_LENGTH}"))
    }

    @Test
    fun attachAndDetachAdvanceSessionRevisionOnlyOnStateChange() {
        val manager = manager(enabled = true)

        assertEquals(0L, manager.snapshot().sessionRevision)
        manager.attach()
        manager.attach()
        assertEquals(1L, manager.snapshot().sessionRevision)
        manager.detach()
        manager.detach()
        assertEquals(2L, manager.snapshot().sessionRevision)
        assertFalse(manager.snapshot().attached)
    }

    @Test
    fun disabledFileTypeReturnsEmptyBridgeOptions() {
        val manager = manager(enabled = true)

        manager.setDocumentPath("notes.txt")
        val snapshot = manager.snapshot()

        assertFalse(snapshot.enabled)
        assertEquals(AceLspServerManager.STATE_DISABLED_FILE_TYPE, snapshot.state)
        assertEquals(AceLspServerManager.REASON_DISABLED_FILE_TYPE, snapshot.reason)
        assertEquals("{}", manager.bridgeOptionsJson())
    }

    @Test
    fun untitledDocumentUsesStableSyntheticJavaScriptUri() {
        val manager = manager(enabled = true)

        manager.setDocumentPath(null)
        val snapshot = manager.snapshot()

        assertTrue(snapshot.enabled)
        assertEquals(0L, snapshot.sessionRevision)
        assertEquals(AceLspServerManager.SYNTHETIC_DOCUMENT_URI, snapshot.documentUri)
    }

    @Test
    fun changingDocumentPathAdvancesSessionRevisionAndAllowsConfiguredType() {
        val manager = manager(enabled = true)

        manager.setDocumentPath("main.node.js")
        val snapshot = manager.snapshot()

        assertTrue(snapshot.enabled)
        assertEquals(1L, snapshot.sessionRevision)
        assertEquals(AceLspServerManager.STATE_LOCAL_LANGUAGE_SERVICE, snapshot.state)
        assertEquals("file:///autojs6/editor/main.node.js", snapshot.documentUri)
    }

    @Test
    fun jsonDocumentPathKeepsJsonSuffixInBridgeOptions() {
        val manager = AceLspServerManager(
            enabledProvider = { true },
            documentAllowedProvider = { it?.lowercase()?.endsWith(".json") == true },
        )

        manager.setDocumentPath("D:\\scripts\\config file.json")
        val snapshot = manager.snapshot()
        val optionsJson = manager.bridgeOptionsJson()

        assertTrue(snapshot.enabled)
        assertEquals("file:///autojs6/editor/config%20file.json", snapshot.documentUri)
        assertTrue(optionsJson.contains("\"documentUri\":\"file:///autojs6/editor/config%20file.json\""))
    }

    private fun manager(enabled: Boolean): AceLspServerManager {
        return AceLspServerManager(
            enabledProvider = { enabled },
            documentAllowedProvider = { path ->
                listOf(".js", ".node.js").any { suffix ->
                    path?.lowercase()?.endsWith(suffix) == true
                }
            },
        )
    }
}
