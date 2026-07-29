package org.autojs.autojs.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DocumentPreviewPathPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sameDirectoryResourcesUseOnlyWhitelistedMimeTypes() {
        val root = temporaryFolder.newFolder("document")
        val resources = mapOf(
            "styles/site.css" to "text/css",
            "images/picture.PNG" to "image/png",
            "fonts/body.woff2" to "font/woff2",
            "media/movie.mp4" to "video/mp4",
            "media/sound.ogg" to "audio/ogg",
        )

        resources.forEach { (path, expectedMimeType) ->
            val file = createFile(root, path)
            val resolved = requireNotNull(DocumentPreviewPathPolicy.resolve(root, path))

            assertEquals(file.canonicalFile, resolved.file)
            assertEquals(expectedMimeType, resolved.mimeType)
        }
    }

    @Test
    fun traversalEncodedAndBackslashPathsAreRejected() {
        val root = temporaryFolder.newFolder("document")
        createFile(root, "images/picture.png")
        val unsafePaths = listOf(
            "../outside.png",
            "images/../picture.png",
            "images/%2e%2e/outside.png",
            "images%2Fpicture.png",
            "images\\picture.png",
            "/images/picture.png",
            "images//picture.png",
            "images/./picture.png",
            "images/picture.png?size=large",
            "images/picture.png#fragment",
        )

        unsafePaths.forEach { path ->
            assertFalse("$path should fail validation", DocumentPreviewPathPolicy.isSafeRelativePath(path))
            assertNull("$path should not resolve", DocumentPreviewPathPolicy.resolve(root, path))
        }
    }

    @Test
    fun unsupportedMimeTypesAndDirectoriesAreRejected() {
        val root = temporaryFolder.newFolder("document")
        listOf("payload.js", "document.html", "notes.txt").forEach { path ->
            createFile(root, path)
            assertNull(path, DocumentPreviewPathPolicy.resolve(root, path))
        }
        File(root, "images").mkdirs()

        assertNull(DocumentPreviewPathPolicy.resolve(root, "images"))
    }

    @Test
    fun filesOutsideTheDocumentRootCannotResolve() {
        val root = temporaryFolder.newFolder("document")
        val outside = temporaryFolder.newFile("outside.png")
        outside.writeBytes(byteArrayOf(1))

        assertNull(DocumentPreviewPathPolicy.resolve(root, "../${outside.name}"))
        assertNull(DocumentPreviewPathPolicy.resolve(root, outside.absolutePath))
    }

    @Test
    fun ordinaryNestedRelativePathIsAccepted() {
        assertTrue(DocumentPreviewPathPolicy.isSafeRelativePath("images/diagrams/preview.svg"))
    }

    private fun createFile(root: File, relativePath: String): File =
        File(root, relativePath).also { file ->
            check(file.parentFile?.mkdirs() != false || file.parentFile?.isDirectory == true)
            file.writeBytes(byteArrayOf(1))
        }
}
