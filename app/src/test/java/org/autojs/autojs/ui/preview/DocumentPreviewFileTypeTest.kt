package org.autojs.autojs.ui.preview

import org.autojs.autojs.util.FileUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DocumentPreviewFileTypeTest {

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
                "$extension should use the Markdown preview renderer",
                FileUtils.PreviewType.MARKDOWN,
                type.previewType,
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
                "$extension should use the HTML preview renderer",
                FileUtils.PreviewType.HTML,
                type.previewType,
            )
        }
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
            assertSame(null, type.previewType)
        }
    }

    @Test
    fun segaHeaderWinsOverMarkdownExtensionAndDisablesPreview() {
        val file = temporaryFolder.newFile("game.md")
        val bytes = ByteArray(512)
        "SEGA".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        file.writeBytes(bytes)

        val type = FileUtils.TYPE.determineBy(file)

        assertSame(FileUtils.TYPE.SEGA_MD, type)
        assertFalse(type.isPreviewable())
        assertSame(null, type.previewType)
    }

    private fun determine(name: String, content: String): FileUtils.TYPE {
        val file = temporaryFolder.newFile(name)
        file.writeText(content)
        return FileUtils.TYPE.determineBy(file)
    }
}
