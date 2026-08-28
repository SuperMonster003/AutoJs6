package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import org.autojs.plugin.editor.api.EditorPluginProjectRenameFile
import org.autojs.plugin.editor.api.EditorPluginProjectRenameRequest
import org.autojs.plugin.editor.api.EditorPluginTextEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TypeScriptEditorProjectRenameTransactionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun preparesAndPublishesExactThreeFileRename() {
        val fixture = projectFixture()
        val request = request(fixture)
        val transaction = TypeScriptEditorProjectRenameTransaction(JvmFileOperations())

        val prepared = transaction.prepare(
            documentPath = fixture.main.path,
            currentDocumentText = fixture.main.readText(),
            request = request,
        )
        assertEquals(3, prepared.fileCount)
        assertEquals(5, prepared.editCount)
        assertTrue(prepared.activeDocumentResultText.contains("projectAnswer"))

        val publication = transaction.publish(prepared)
        fixture.sources.forEach { source ->
            assertFalse(source.readText().contains("answer"))
            assertTrue(source.readText().contains("projectAnswer"))
        }
        transaction.complete(publication)
        assertTrue(renameTemporaryFiles(fixture.root).isEmpty())
    }

    @Test
    fun preservesActiveDocumentUtf8BomWhileUsingEditorOffsets() {
        val fixture = projectFixture()
        fixture.main.writeText(UTF8_BOM_CHARACTER + fixture.main.readText())
        val currentDocumentText = fixture.main.readText()
            .removePrefix(UTF8_BOM_CHARACTER.toString())
        val transaction = TypeScriptEditorProjectRenameTransaction(JvmFileOperations())

        val prepared = transaction.prepare(
            documentPath = fixture.main.path,
            currentDocumentText = currentDocumentText,
            request = request(fixture),
        )
        assertFalse(prepared.activeDocumentResultText.startsWith(UTF8_BOM_CHARACTER))

        val publication = transaction.publish(prepared)
        val publishedText = fixture.main.readText()
        assertTrue(publishedText.startsWith(UTF8_BOM_CHARACTER))
        assertTrue(
            publishedText.removePrefix(UTF8_BOM_CHARACTER.toString()).contains("projectAnswer"),
        )
        transaction.complete(publication)
        assertTrue(renameTemporaryFiles(fixture.root).isEmpty())
    }

    @Test
    fun rejectsUnsavedActiveDocumentAndStaleInventory() {
        val fixture = projectFixture()
        val request = request(fixture)
        val transaction = TypeScriptEditorProjectRenameTransaction(JvmFileOperations())

        val unsaved = captureFailure {
            transaction.prepare(
                fixture.main.path,
                fixture.main.readText() + "// draft\n",
                request,
            )
        }
        assertEquals(TypeScriptEditorProjectRenameFailureReason.UNSAVED_DOCUMENT, unsaved.reason)

        fixture.consumer.appendText("// external change\n")
        val stale = captureFailure {
            transaction.prepare(fixture.main.path, fixture.main.readText(), request)
        }
        assertEquals(TypeScriptEditorProjectRenameFailureReason.STALE, stale.reason)
    }

    @Test
    fun rejectsEditThatDoesNotSelectTheNamedSymbol() {
        val fixture = projectFixture()
        val request = request(fixture)
        val first = request.files.first()
        val tampered = request.copy(
            files = listOf(
                first.copy(
                    edits = first.edits.mapIndexed { index, edit ->
                        if (index == 0) edit.copy(startOffset = edit.startOffset + 1, endOffset = edit.endOffset + 1)
                        else edit
                    },
                ),
            ) + request.files.drop(1),
        )

        val failure = captureFailure {
            TypeScriptEditorProjectRenameTransaction(JvmFileOperations()).prepare(
                fixture.main.path,
                fixture.main.readText(),
                tampered,
            )
        }
        assertEquals(TypeScriptEditorProjectRenameFailureReason.REJECTED, failure.reason)
    }

    @Test
    fun publicationFailureRollsBackEveryPublishedFile() {
        val fixture = projectFixture()
        val originals = fixture.sources.associateWith(File::readText)
        val operations = JvmFileOperations(failOnStagedRename = 2)
        val transaction = TypeScriptEditorProjectRenameTransaction(operations)
        val prepared = transaction.prepare(
            fixture.main.path,
            fixture.main.readText(),
            request(fixture),
        )

        val failure = captureFailure { transaction.publish(prepared) }
        assertEquals(TypeScriptEditorProjectRenameFailureReason.FAILED, failure.reason)
        originals.forEach { (file, text) -> assertEquals(text, file.readText()) }
        assertTrue(renameTemporaryFiles(fixture.root).isEmpty())
    }

    @Test
    fun detectsConflictBetweenPreviewAndPublicationWithoutWriting() {
        val fixture = projectFixture()
        val transaction = TypeScriptEditorProjectRenameTransaction(JvmFileOperations())
        val prepared = transaction.prepare(
            fixture.main.path,
            fixture.main.readText(),
            request(fixture),
        )
        val mainBefore = fixture.main.readText()
        val sharedBefore = fixture.shared.readText()
        fixture.consumer.appendText("// external change\n")
        val consumerAfterChange = fixture.consumer.readText()

        val failure = captureFailure { transaction.publish(prepared) }
        assertEquals(TypeScriptEditorProjectRenameFailureReason.STALE, failure.reason)
        assertEquals(mainBefore, fixture.main.readText())
        assertEquals(sharedBefore, fixture.shared.readText())
        assertEquals(consumerAfterChange, fixture.consumer.readText())
        assertTrue(renameTemporaryFiles(fixture.root).isEmpty())
    }

    private fun request(fixture: Fixture): EditorPluginProjectRenameRequest {
        val snapshot = requireNotNull(
            TypeScriptEditorProjectSnapshotProvider.captureRequired(fixture.main.path),
        )
        val files = snapshot.sourceFiles.map { source ->
            val activeSourceHasBom = source.relativePath == snapshot.documentRelativePath &&
                source.text.startsWith(UTF8_BOM_CHARACTER)
            val editorText = if (activeSourceHasBom) source.text.drop(1) else source.text
            val edits = symbolEdits(editorText, OLD_NAME, NEW_NAME)
            val editorResult = replay(editorText, edits)
            val diskResult = if (activeSourceHasBom) {
                UTF8_BOM_CHARACTER + editorResult
            } else {
                editorResult
            }
            EditorPluginProjectRenameFile(
                relativePath = source.relativePath,
                baseContentSha256 = source.sha256,
                resultContentSha256 = sha256(diskResult.toByteArray()),
                edits = edits,
            )
        }.filter { file -> file.edits.isNotEmpty() }
            .sortedBy(EditorPluginProjectRenameFile::relativePath)
        return EditorPluginProjectRenameRequest(
            projectRootPath = snapshot.projectRootPath,
            projectSourceInventoryFingerprint = snapshot.sourceInventoryFingerprint,
            documentRelativePath = snapshot.documentRelativePath,
            symbolName = OLD_NAME,
            newName = NEW_NAME,
            files = files,
        )
    }

    private fun symbolEdits(text: String, oldName: String, newName: String): List<EditorPluginTextEdit> {
        val edits = ArrayList<EditorPluginTextEdit>()
        var fromIndex = 0
        while (true) {
            val index = text.indexOf(oldName, fromIndex)
            if (index < 0) return edits
            edits += EditorPluginTextEdit(index, index + oldName.length, newName)
            fromIndex = index + oldName.length
        }
    }

    private fun replay(text: String, edits: List<EditorPluginTextEdit>): String =
        StringBuilder(text).apply {
            edits.asReversed().forEach { edit -> replace(edit.startOffset, edit.endOffset, edit.newText) }
        }.toString()

    private fun projectFixture(): Fixture {
        val root = temporaryFolder.newFolder("rename-project").canonicalFile
        File(root, "tsconfig.json").writeText("{\"compilerOptions\":{\"strict\":true}}")
        val sourceDirectory = File(root, "src").apply { mkdirs() }
        val main = File(sourceDirectory, "main.ts").apply {
            writeText(
                "import { answer } from './shared';\n" +
                    "console.log(answer('main'));\n",
            )
        }.canonicalFile
        val shared = File(sourceDirectory, "shared.ts").apply {
            writeText("export function answer(value: string): number { return value.length; }\n")
        }.canonicalFile
        val consumer = File(sourceDirectory, "consumer.ts").apply {
            writeText(
                "import { answer } from './shared';\n" +
                    "export const consumed = answer('consumer');\n",
            )
        }.canonicalFile
        return Fixture(root, main, shared, consumer)
    }

    private fun renameTemporaryFiles(root: File): List<File> =
        root.walkTopDown().filter { file ->
            file.name.startsWith(".autojs6-rename-")
        }.toList()

    private fun captureFailure(block: () -> Unit): TypeScriptEditorProjectRenameException =
        try {
            block()
            throw AssertionError("Expected project rename failure")
        } catch (error: TypeScriptEditorProjectRenameException) {
            error
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private class JvmFileOperations(
        private val failOnStagedRename: Int? = null,
    ) : TypeScriptEditorProjectRenameTransaction.FileOperations {
        private var stagedRenameCount = 0

        override fun metadata(file: File): TypeScriptEditorProjectRenameTransaction.FileMetadata {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            return TypeScriptEditorProjectRenameTransaction.FileMetadata(
                identity = attributes.fileKey()?.toString() ?: file.canonicalPath,
                mode = 0,
                linkCount = 1,
                regularFile = attributes.isRegularFile,
            )
        }

        override fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }

        override fun createTemporaryFile(parent: File, prefix: String): File =
            File.createTempFile(prefix, ".tmp", parent)

        override fun writeAndSync(file: File, bytes: ByteArray, mode: Int) {
            FileOutputStream(file).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
        }

        override fun rename(source: File, target: File) {
            if (source.name.startsWith(".autojs6-rename-stage-")) {
                stagedRenameCount++
                if (stagedRenameCount == failOnStagedRename) {
                    throw java.io.IOException("Injected staged rename failure")
                }
            }
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }

        override fun delete(file: File) {
            Files.deleteIfExists(file.toPath())
        }

        override fun syncDirectory(directory: File) = Unit
    }

    private data class Fixture(
        val root: File,
        val main: File,
        val shared: File,
        val consumer: File,
    ) {
        val sources = listOf(consumer, main, shared)
    }

    private companion object {
        const val OLD_NAME = "answer"
        const val NEW_NAME = "projectAnswer"
        const val UTF8_BOM_CHARACTER = '\uFEFF'
    }
}
