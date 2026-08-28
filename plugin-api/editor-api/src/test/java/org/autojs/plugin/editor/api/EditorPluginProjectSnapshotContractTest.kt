package org.autojs.plugin.editor.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPluginProjectSnapshotContractTest {

    @Test
    fun contractTwoAcceptsOneCanonicalBoundedSnapshot() {
        assertTrue(EditorPluginContract.MIN_SUPPORTED_VERSION <= 1)
        assertTrue(EditorPluginContract.MAX_SUPPORTED_VERSION >= 2)
        assertTrue(EditorPluginProjectSnapshotContract.isSupported(snapshot()))
    }

    @Test
    fun rejectsIncompleteDuplicateAndMismatchedInventories() {
        val valid = snapshot()
        assertFalse(
            EditorPluginProjectSnapshotContract.isSupported(
                valid.copy(documentRelativePath = "src/missing.ts"),
            ),
        )
        assertFalse(
            EditorPluginProjectSnapshotContract.isSupported(
                valid.copy(sourceFiles = valid.sourceFiles + valid.sourceFiles.single()),
            ),
        )
        assertFalse(
            EditorPluginProjectSnapshotContract.isSupported(
                valid.copy(sourceByteLength = valid.sourceByteLength + 1),
            ),
        )
    }

    private fun snapshot(): EditorPluginProjectSnapshot {
        val source = "export const answer: number = 42;\n"
        val bytes = source.toByteArray().size.toLong()
        return EditorPluginProjectSnapshot(
            schemaRevision = EditorPluginProjectSnapshotContract.SCHEMA_REVISION,
            projectRootPath = "/project",
            documentPath = "/project/src/main.ts",
            documentRelativePath = "src/main.ts",
            targetProfile = EditorPluginProjectSnapshotContract.TARGET_PROFILE_RHINO,
            sourceFiles = listOf(
                EditorPluginProjectSource(
                    relativePath = "src/main.ts",
                    text = source,
                    utf8ByteLength = bytes,
                    sha256 = "1".repeat(64),
                ),
            ),
            sourceInventoryFingerprint = "2".repeat(64),
            sourceFileCount = 1,
            sourceByteLength = bytes,
        )
    }
}
