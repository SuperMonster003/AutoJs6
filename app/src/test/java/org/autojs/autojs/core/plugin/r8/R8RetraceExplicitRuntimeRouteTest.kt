package org.autojs.autojs.core.plugin.r8

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class R8RetraceExplicitRuntimeRouteTest {
    @Test
    fun pathsResolveOnceAndCompletedOutputCrossesTheScriptBoundary() = withInputs { root ->
        val resolved = mutableListOf<String>()
        var captured: R8RetraceHostSpec? = null

        val output = R8RetraceExplicitRuntimeRoute.dispatch(
            obfuscatedStackTrace = R8RetraceHostFixtures.stack,
            mappingPath = "mapping.txt",
            retraceMetadataPath = "retrace-metadata.bin",
            resolvePath = { path -> resolved += path; File(root, path).path },
            retrace = { spec ->
                captured = spec
                R8RetraceHostDispatchOutcome.Completed("sample.Main.crash(Main.java:42)\n")
            },
        )

        assertEquals(listOf("mapping.txt", "retrace-metadata.bin"), resolved)
        assertEquals("sample.Main.crash(Main.java:42)\n", output)
        assertTrue(checkNotNull(captured).mapping.openInputStream().use { it.readBytes() }.isNotEmpty())
    }

    @Test
    fun noProviderAndProviderFailureAreTerminalWithoutFallback() = withInputs { root ->
        val noProvider = assertThrows(R8RetraceExplicitException::class.java) {
            dispatch(root, R8RetraceHostDispatchOutcome.NotSelected(R8CompilerSelectionDecision.Disabled))
        }
        assertTrue(noProvider.message.orEmpty().contains("no local retrace fallback"))

        val failure = assertThrows(R8RetraceExplicitException::class.java) {
            dispatch(
                root,
                R8RetraceHostDispatchOutcome.Failed(
                    R8RetraceHostFailureStage.REMOTE_FAILURE,
                    IllegalStateException("synthetic retrace failure"),
                ),
            )
        }
        assertTrue(failure.message.orEmpty().contains("no fallback"))
    }

    private fun dispatch(root: File, outcome: R8RetraceHostDispatchOutcome): String =
        R8RetraceExplicitRuntimeRoute.dispatch(
            R8RetraceHostFixtures.stack,
            "mapping.txt",
            "retrace-metadata.bin",
            { path -> File(root, path).path },
            { outcome },
        )

    private fun <T> withInputs(action: (File) -> T): T {
        val root = Files.createTempDirectory("r8-retrace-route-").toFile()
        return try {
            File(root, "mapping.txt").writeBytes(R8RetraceHostFixtures.mapping)
            File(root, "retrace-metadata.bin").writeBytes(R8RetraceHostFixtures.metadata())
            action(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
