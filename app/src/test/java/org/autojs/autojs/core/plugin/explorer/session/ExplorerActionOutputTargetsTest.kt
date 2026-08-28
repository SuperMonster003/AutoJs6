package org.autojs.autojs.core.plugin.explorer.session

import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExplorerActionOutputTargetsTest {

    @Test
    fun `auto rename preserves the extension and selects the first available name`() {
        withTempDirectory { parent ->
            File(parent, "report.txt").writeText("existing")
            val reserved = ExplorerActionOutputTargets.reserveFile(
                parent = parent,
                displayName = "report.txt",
                conflictPolicy = ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME,
            )
            try {
                assertEquals("report (1).txt", reserved.name)
            } finally {
                ExplorerActionOutputTargets.release(reserved)
            }
        }
    }

    @Test
    fun `reservation prevents another session from claiming the same path`() {
        withTempDirectory { parent ->
            val first = ExplorerActionOutputTargets.reserveFile(
                parent = parent,
                displayName = "result.bin",
                conflictPolicy = ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL,
            )
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    ExplorerActionOutputTargets.reserveFile(
                        parent = parent,
                        displayName = first.name,
                        conflictPolicy = ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL,
                    )
                }
            } finally {
                ExplorerActionOutputTargets.release(first)
            }

            val afterRelease = ExplorerActionOutputTargets.reserveFile(
                parent = parent,
                displayName = first.name,
                conflictPolicy = ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL,
            )
            ExplorerActionOutputTargets.release(afterRelease)
        }
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("explorer-output-targets").toFile().canonicalFile
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
