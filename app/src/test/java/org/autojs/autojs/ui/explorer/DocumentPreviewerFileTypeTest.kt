package org.autojs.autojs.ui.explorer

import org.autojs.autojs.util.FileUtils
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
