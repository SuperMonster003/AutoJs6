package org.autojs.autojs.ui.explorer

import org.autojs.autojs.util.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DocumentPreviewerFileTypeTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun markdownAliasesArePreviewableAsMarkdown() {
        val aliases = listOf(
            "md",
            "markdown",
            "mdown",
            "mkd",
            "mkdn",
            "mdwn",
            "mdtext",
            "mdtxt",
            "rmd",
            "qmd",
        )

        aliases.forEach { extension ->
            val type = determine("sample.$extension", "# Preview\n\nMarkdown content.")

            assertTrue("$extension should be previewable", type.isPreviewable())
            assertSame(
                "$extension should target the Markdown Previewer",
                FileUtils.PreviewerType.MARKDOWN,
                type.previewerType,
            )
        }
    }

    @Test
    fun htmlWhitelistIsPreviewableAsHtml() {
        val extensions = listOf("html", "htm", "shtm", "shtml", "xht", "xhtml")

        extensions.forEach { extension ->
            val type = determine("sample.$extension", "<!doctype html><title>Preview</title>")

            assertTrue("$extension should be previewable", type.isPreviewable())
            assertSame(
                "$extension should target the HTML Previewer",
                FileUtils.PreviewerType.HTML,
                type.previewerType,
            )
        }
    }

    @Test
    fun epubIsPreviewableAsEpubByExtensionRegardlessOfContent() {
        val zipLike = temporaryFolder.newFile("novel.epub")
        zipLike.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "mimetypeapplication/epub+zip".toByteArray(Charsets.US_ASCII))
        val plain = determine("notes.EPUB", "not a zip at all")

        listOf(FileUtils.TYPE.determineBy(zipLike), plain).forEach { type ->
            assertSame(FileUtils.TYPE.EPUB, type)
            assertTrue("epub should be previewable", type.isPreviewable())
            assertSame(FileUtils.PreviewerType.EPUB, type.previewerType)
            assertFalse("epub is a binary container", type.isTextEditable())
        }
        assertSame(ExplorerPrimaryAction.DOCUMENT_PREVIEWER, ExplorerPrimaryActionResolver.resolve(plain))
    }

    @Test
    fun archiveDocumentLimitIsPerPreviewerType() {
        assertEquals(256L * 1024L * 1024L, ExplorerDocumentPreviewerPluginUi.maximumArchiveDocumentBytes(FileUtils.PreviewerType.EPUB))
        assertEquals(8L * 1024L * 1024L, ExplorerDocumentPreviewerPluginUi.maximumArchiveDocumentBytes(FileUtils.PreviewerType.HTML))
        assertEquals(8L * 1024L * 1024L, ExplorerDocumentPreviewerPluginUi.maximumArchiveDocumentBytes(FileUtils.PreviewerType.MARKDOWN))
        assertEquals(8L * 1024L * 1024L, ExplorerDocumentPreviewerPluginUi.maximumArchiveDocumentBytes(null))
    }

    @Test
    fun encapsulatedExecutableAndDataFormatsAreNotPreviewable() {
        val excluded = mapOf(
            "sample.mht" to FileUtils.TYPE.MHT,
            "sample.mhtml" to FileUtils.TYPE.HMTML,
            "sample.hta" to FileUtils.TYPE.HTA,
            "sample.json" to FileUtils.TYPE.JSON,
        )

        excluded.forEach { (name, expectedType) ->
            val type = determine(name, "{}")

            assertSame(expectedType, type)
            assertFalse("$name must not be document-previewable", type.isPreviewable())
            assertSame(null, type.previewerType)
        }
    }

    @Test
    fun segaHeaderWinsOverMarkdownExtensionAndDisablesPreviewerEntry() {
        val file = temporaryFolder.newFile("game.md")
        val bytes = ByteArray(512)
        "SEGA".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        file.writeBytes(bytes)

        val type = FileUtils.TYPE.determineBy(file)

        assertSame(FileUtils.TYPE.SEGA_MD, type)
        assertFalse(type.isPreviewable())
        assertSame(null, type.previewerType)
    }

    private fun determine(name: String, content: String): FileUtils.TYPE {
        val file = temporaryFolder.newFile(name)
        file.writeText(content)
        return FileUtils.TYPE.determineBy(file)
    }
}
