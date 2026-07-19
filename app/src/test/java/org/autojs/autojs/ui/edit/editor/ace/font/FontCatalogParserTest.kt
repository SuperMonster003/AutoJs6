package org.autojs.autojs.ui.edit.editor.ace.font

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FontCatalogParserTest {
    private val parser = FontCatalogParser(hostVersionCode = 5221)

    @Test
    fun bundledV4CatalogMatchesHostGroupingAndFeatureContract() {
        val workingDirectory = File(checkNotNull(System.getProperty("user.dir")))
        val catalogFile = listOf(
            File(workingDirectory, "app/src/main/res/raw/ace_font_catalog_v1.json"),
            File(workingDirectory, "src/main/res/raw/ace_font_catalog_v1.json"),
        ).firstOrNull(File::isFile)
        val catalog = FontCatalogParser(hostVersionCode = 5230)
            .parse(checkNotNull(catalogFile) { "Bundled Ace font catalog was not found" }.readBytes())

        assertEquals(4L, catalog.catalogVersion)
        assertEquals(5230, catalog.minHostVersionCode)
        assertEquals(60, catalog.fonts.size)
        assertEquals(43, catalog.fonts.map(RemoteFont::groupId).distinct().size)
        assertEquals(43, catalog.fonts.count(RemoteFont::isDefaultVariant))
        assertEquals(
            "geist_pixel_triangle",
            catalog.fonts.single { it.groupId == "geist_pixel" && it.isDefaultVariant }.id,
        )
        assertEquals(
            setOf(FontFeature.MONO, FontFeature.NERD, FontFeature.CN, FontFeature.JP),
            catalog.find("sarasa_mono_sc_nerd")?.features,
        )
        assertTrue(FontFeature.JP in checkNotNull(catalog.find("m_plus_1")).features)
        assertFalse(FontFeature.CN in checkNotNull(catalog.find("m_plus_1")).features)
        assertFalse(catalog.fonts.any { it.id == "sarasa_term_sc_nerd" })
    }

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
        assertTrue(font.features.isEmpty())
        assertEquals("test_font", font.groupId)
        assertEquals(null, font.variantName)
        assertEquals(0, font.variantOrder)
        assertTrue(font.isDefaultVariant)
        assertEquals(font.artifact.urls.first(), font.artifact.url)
        assertEquals(font.artifact.sizeBytes, font.artifact.size)
        assertFalse(font.artifact.revoked)
    }

    @Test
    fun parsesOptionalVariantGroupingMetadata() {
        val catalog = parser.parse(twoVariantCatalog())
        val regular = catalog.find("test_font_regular")!!
        val nerd = catalog.find("test_font_nerd")!!

        assertEquals("test_font", regular.groupId)
        assertEquals("Regular", regular.variantName)
        assertEquals(0, regular.variantOrder)
        assertFalse(regular.isDefaultVariant)
        assertEquals("test_font", nerd.groupId)
        assertEquals("Nerd", nerd.variantName)
        assertEquals(10, nerd.variantOrder)
        assertTrue(nerd.isDefaultVariant)
    }

    @Test
    fun rejectsInvalidVariantMetadataFields() {
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(singleFontCatalog { it.addProperty("groupId", "Invalid-Group") })
        }
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(singleFontCatalog { it.addProperty("groupId", AceFontIds.BUNDLED_IOSEVKA) })
        }
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(singleFontCatalog { it.addProperty("variantOrder", -1) })
        }
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(singleFontCatalog { it.addProperty("isDefaultVariant", "true") })
        }
    }

    @Test
    fun rejectsGroupsWithoutExactlyOneDefaultVariant() {
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(twoVariantCatalog(secondEdit = { it.addProperty("isDefaultVariant", false) }))
        }
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(twoVariantCatalog(firstEdit = { it.addProperty("isDefaultVariant", true) }))
        }
    }

    @Test
    fun rejectsInconsistentOrAmbiguousVariantGroups() {
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(twoVariantCatalog(secondEdit = { it.addProperty("displayName", "Other Font") }))
        }
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(twoVariantCatalog(firstEdit = { it.remove("variantName") }))
        }
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(twoVariantCatalog(secondEdit = { it.addProperty("variantName", "Regular") }))
        }
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(twoVariantCatalog(secondEdit = { it.addProperty("variantOrder", 0) }))
        }
    }

    @Test
    fun parsesKnownFeaturesInCanonicalOrder() {
        val json = FontTestFixtures.catalogJson().replace(
            "\"author\": \"Test Author\"",
            "\"features\": [\"jp\", \"cn\", \"nerd\", \"mono\", \"variable\"], \"author\": \"Test Author\"",
        )

        assertEquals(
            listOf(
                FontFeature.MONO,
                FontFeature.NERD,
                FontFeature.VARIABLE,
                FontFeature.CN,
                FontFeature.JP,
            ),
            parser.parse(json).fonts.single().features.toList(),
        )
    }

    @Test
    fun rejectsUnknownDuplicateAndNonStringFeatures() {
        val base = FontTestFixtures.catalogJson()
        fun withFeatures(value: String) = base.replace(
            "\"author\": \"Test Author\"",
            "\"features\": $value, \"author\": \"Test Author\"",
        )

        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(withFeatures("[\"nerd\", \"future_feature\"]"))
        }
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(withFeatures("[\"cn\", \"cn\"]"))
        }
        assertThrows(FontCatalogParseException::class.java) {
            parser.parse(withFeatures("[\"variable\", 1]"))
        }
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

    private fun singleFontCatalog(edit: (JsonObject) -> Unit): String {
        val root = JsonParser.parseString(FontTestFixtures.catalogJson()).asJsonObject
        edit(root.getAsJsonArray("fonts").single().asJsonObject)
        return root.toString()
    }

    private fun twoVariantCatalog(
        firstEdit: (JsonObject) -> Unit = {},
        secondEdit: (JsonObject) -> Unit = {},
    ): String {
        val root = JsonParser.parseString(FontTestFixtures.catalogJson()).asJsonObject
        val template = root.getAsJsonArray("fonts").single().asJsonObject
        val first = template.deepCopy().apply {
            addProperty("id", "test_font_regular")
            addProperty("groupId", "test_font")
            addProperty("variantName", "Regular")
            addProperty("variantOrder", 0)
            addProperty("isDefaultVariant", false)
            firstEdit(this)
        }
        val second = template.deepCopy().apply {
            addProperty("id", "test_font_nerd")
            addProperty("groupId", "test_font")
            addProperty("variantName", "Nerd")
            addProperty("variantOrder", 10)
            addProperty("isDefaultVariant", true)
            secondEdit(this)
        }
        root.add("fonts", JsonArray().apply {
            add(first)
            add(second)
        })
        return root.toString()
    }
}
