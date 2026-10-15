package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExplorerActionHostFileInfoPolicyTest {

    @Test
    fun hostFileInfoCapabilityDoesNotRenumberTheBaseProtocol() {
        assertEquals(25, ExplorerActionProtocol.VERSION)
        assertEquals(1, ExplorerActionProtocol.HOST_FILE_INFO_VERSION)
    }

    @Test
    fun titleAndSummaryRemoveUnsafeControlsWithoutFlatteningLines() {
        assertEquals(
            "APK Inspector",
            ExplorerActionHostFileInfoPolicy.normalizeTitle(" APK\u0000  Inspector "),
        )
        assertEquals(
            "Signatures: V2 verified\nPermissions: 4",
            ExplorerActionHostFileInfoPolicy.normalizeSummary(
                " Signatures:\tV2\u202Everified\r\n Permissions:  4 ",
            ),
        )
        assertEquals(
            "a".repeat(64),
            ExplorerActionHostFileInfoPolicy.normalizeSha256("A".repeat(64)),
        )
    }

    @Test
    fun hostPresentationLimitsRejectOversizedOrAmbiguousResults() {
        assertNull(
            ExplorerActionHostFileInfoPolicy.normalizeTitle(
                "x".repeat(ExplorerActionProtocol.MAX_HOST_FILE_INFO_TITLE_LENGTH + 1),
            ),
        )
        assertNull(
            ExplorerActionHostFileInfoPolicy.normalizeSummary(
                List(ExplorerActionProtocol.MAX_HOST_FILE_INFO_SUMMARY_LINES + 1) { "line" }
                    .joinToString("\n"),
            ),
        )
        assertNull(
            ExplorerActionHostFileInfoPolicy.normalizeSummary(
                "x".repeat(ExplorerActionProtocol.MAX_HOST_FILE_INFO_SUMMARY_LINE_LENGTH + 1),
            ),
        )
        assertNull(ExplorerActionHostFileInfoPolicy.normalizeSummary("\u0000\u202E"))
        assertNull(ExplorerActionHostFileInfoPolicy.normalizeSha256("0".repeat(63)))
        assertNull(ExplorerActionHostFileInfoPolicy.normalizeSha256("g".repeat(64)))
    }
}
