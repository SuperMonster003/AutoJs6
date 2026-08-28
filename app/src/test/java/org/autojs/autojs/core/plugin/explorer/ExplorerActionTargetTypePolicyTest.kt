package org.autojs.autojs.core.plugin.explorer

import org.autojs.autojs.util.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerActionTargetTypePolicyTest {

    @Test
    fun typeScriptTsSuppressesAmbiguousExtensionOnlyVideoMatching() {
        val resolved = ExplorerActionTargetTypePolicy.resolve(
            type = FileUtils.TYPE.TYPESCRIPT,
            fallbackMimeType = "video/mp2t",
            rawExtension = "TS",
        )

        assertEquals("text/typescript", resolved.mimeType)
        assertEquals("", resolved.extension)
    }

    @Test
    fun declarationTsAlsoSuppressesAmbiguousVideoMatching() {
        val resolved = ExplorerActionTargetTypePolicy.resolve(
            type = FileUtils.TYPE.TYPESCRIPT_DECLARATION,
            fallbackMimeType = "video/mp2t",
            rawExtension = "ts",
        )

        assertEquals("text/typescript", resolved.mimeType)
        assertEquals("", resolved.extension)
    }

    @Test
    fun transportStreamKeepsTsExtensionAndUsesVideoMime() {
        val resolved = ExplorerActionTargetTypePolicy.resolve(
            type = FileUtils.TYPE.MPEG2_TS,
            fallbackMimeType = "application/octet-stream",
            rawExtension = "TS",
        )

        assertEquals("video/*", resolved.mimeType)
        assertEquals("ts", resolved.extension)
    }

    @Test
    fun everyExplorerRecognizedVideoUsesVideoWildcard() {
        val videoTypes = FileUtils.TYPE.entries.filter { type -> type.isVideoFile() }
        videoTypes.forEach { type ->
            val resolved = ExplorerActionTargetTypePolicy.resolve(
                type = type,
                fallbackMimeType = "application/octet-stream",
                rawExtension = type.extension,
            )

            assertEquals(type.name, "video/*", resolved.mimeType)
            assertEquals(type.name, type.extension, resolved.extension)
        }
    }

    @Test
    fun unambiguousSourcePreservesNormalizedExtension() {
        val resolved = ExplorerActionTargetTypePolicy.resolve(
            type = FileUtils.TYPE.PYTHON,
            fallbackMimeType = "text/x-python",
            rawExtension = "PY",
        )

        assertEquals("text/x-python", resolved.mimeType)
        assertEquals("py", resolved.extension)
    }
}
