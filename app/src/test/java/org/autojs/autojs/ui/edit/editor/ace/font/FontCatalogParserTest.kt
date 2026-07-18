package org.autojs.autojs.ui.edit.editor.ace.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FontCatalogParserTest {
    private val parser = FontCatalogParser(hostVersionCode = 5221)

    @Test
    fun parsesDistributionWireShapeAndConvenienceProperties() {
        val catalog = parser.parse(FontTestFixtures.catalogJson())
        val font = catalog.fonts.single()

        assertEquals(1, catalog.schemaVersion)
        assertEquals(1L, catalog.catalogVersion)
        assertEquals("test_font", font.id)
        assertEquals("Test Font", font.displayName)
        assertEquals("SIL Open Font License 1.1", font.licenseName)
        assertEquals("https://example.test/LICENSE.txt", font.licenseUrl)
        assertEquals("LICENSE.txt", font.license.files.single().name)
        assertEquals("1", font.version)
        assertEquals(font.artifact.urls.first(), font.artifact.url)
        assertEquals(font.artifact.sizeBytes, font.artifact.size)
        assertFalse(font.artifact.revoked)
    }

    @Test
    fun rejectsHostOwnedRemoteIdsIncludingBundledIosevka() {
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(FontTestFixtures.catalogJson(fontId = AceFontIds.BUNDLED_IOSEVKA))
        }
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(FontTestFixtures.catalogJson(fontId = AceFontIds.SYSTEM_MONOSPACE))
        }
        val slab = parser.parse(FontTestFixtures.catalogJson(fontId = "iosevka_slab"))
        assertEquals("iosevka_slab", slab.fonts.single().id)
    }

    @Test
    fun rejectsIncompatibleHostAndSchema() {
        assertThrows(FontCatalogCompatibilityException::class.java) {
            parser.parse(FontTestFixtures.catalogJson(minHostVersionCode = 5222))
        }
        val wrongSchema = FontTestFixtures.catalogJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        assertThrows(FontCatalogCompatibilityException::class.java) { parser.parse(wrongSchema) }
    }

    @Test
    fun revocationMarksMatchingArtifact() {
        val bytes = FontTestFixtures.validWoff2()
        val sha = FontTestFixtures.sha256(bytes)
        val json = FontTestFixtures.catalogJson(bytes = bytes).replace(
            "\"fonts\": [",
            "\"revokedSha256\": [\"$sha\"], \"fonts\": [",
        )
        val catalog = parser.parse(json)
        assertTrue(catalog.fonts.single().artifact.revoked)
    }

    @Test
    fun rejectsArtifactLargerThanHardLimit() {
        val json = FontTestFixtures.catalogJson().replace(
            "\"size\": 96",
            "\"size\": ${FontFileVerifier.MAX_FONT_BYTES + 1}",
        )
        assertThrows(FontCatalogParseException::class.java) { parser.parse(json) }
    }
}
