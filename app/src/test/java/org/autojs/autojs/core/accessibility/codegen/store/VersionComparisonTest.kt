package org.autojs.autojs.core.accessibility.codegen.store

import java.time.Instant
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.HOST_VERSION
import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.PACKAGE_SHA
import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.catalog
import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.installed
import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.manifest
import org.autojs.autojs.core.accessibility.codegen.store.VersionComparison.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
    private val formatter = ModelComparisonTestFixtures.Formatter()
    private val comparison = VersionComparison(formatter, "en")
    private fun info() = ModelVersionInfo.fromManifest(manifest(), PACKAGE_SHA, 4096, ModelSource.CUSTOM, HOST_VERSION)
    private fun List<VersionComparison.Row>.field(field: Field) = single { it.field == field }

    @Test
    fun fieldsFollowTheTwelveRowContractAndEqualValuesDoNotDiffer() {
        val value = info()
        val rows = comparison.build(value, value)
        assertEquals(listOf(Field.NAME, Field.VERSION, Field.SOURCE, Field.CONTENT, Field.SIZE, Field.COMPATIBILITY,
            Field.DATASET, Field.METRICS, Field.RELEASED_AT, Field.INSTALLED_AT, Field.LICENSE, Field.STATUS), rows.map { it.field })
        assertTrue(rows.none { it.differs })
        assertEquals("Ranker (autojs6.codegen.ranker)", rows.field(Field.NAME).oldText)
        assertEquals("1.0.0 (1)", rows.field(Field.VERSION).oldText)
        assertEquals("Custom", rows.field(Field.SOURCE).oldText)
        assertEquals("aaaaaaaaaaaa", rows.field(Field.CONTENT).oldText)
        assertEquals("4.0 KiB", rows.field(Field.SIZE).oldText)
        assertEquals("${FeatureSchema.featureSchemaVersion} (Compatible)", rows.field(Field.COMPATIBILITY).oldText)
        assertEquals("test-fixture (2026-10-01)", rows.field(Field.DATASET).oldText)
        assertEquals("90.0% (+10.0 pp)", rows.field(Field.METRICS).oldText)
        assertEquals("2026-10-02T00:00:00Z", rows.field(Field.RELEASED_AT).oldText)
        assertEquals("MPL-2.0", rows.field(Field.LICENSE).oldText)
    }

    @Test
    fun missingFieldsAreDashesAndPartiallyKnownFieldsKeepTheirAvailableValue() {
        val rows = comparison.build(ModelVersionInfo(), ModelVersionInfo())
        assertEquals(12, rows.size)
        assertTrue(rows.all { it.oldText == "-" && it.newText == "-" && !it.differs })
        val partial = ModelVersionInfo(modelId = "some.model", versionName = "1.0", datasetId = "dataset", realTestTop1Survival = .8)
        val missing = comparison.build(ModelVersionInfo(), partial)
        assertEquals("- (some.model)", missing.field(Field.NAME).newText)
        assertEquals("1.0 (-)", missing.field(Field.VERSION).newText)
        assertEquals("dataset (-)", missing.field(Field.DATASET).newText)
        assertEquals("80.0% (-)", missing.field(Field.METRICS).newText)
        assertEquals(setOf(Field.NAME, Field.VERSION, Field.DATASET, Field.METRICS), missing.filter { it.differs }.map { it.field }.toSet())
    }

    @Test
    fun displayNameUsesCurrentLanguageThenEnglishAndComparesOnlyTheSelectedTranslation() {
        val value = info()
        assertEquals("排序模型 (autojs6.codegen.ranker)", VersionComparison(formatter, "zh-cn").build(value, value).field(Field.NAME).oldText)
        assertEquals("Ranker (autojs6.codegen.ranker)", VersionComparison(formatter, "fr-FR").build(value, value).field(Field.NAME).oldText)
        val translated = value.copy(displayName = value.displayName + ("zh-CN" to "新版名称"))
        assertFalse(comparison.build(value, translated).field(Field.NAME).differs)
        assertTrue(VersionComparison(formatter, "zh-CN").build(value, translated).field(Field.NAME).differs)
        val noEnglish = value.copy(displayName = mapOf("zh-CN" to "名称"))
        assertEquals("- (autojs6.codegen.ranker)", comparison.build(noEnglish, noEnglish).field(Field.NAME).oldText)
    }

    @Test
    fun differencesUseFullHashesExactSizesAndUnroundedMetrics() {
        val old = info().copy(sha256 = "a".repeat(12) + "b".repeat(52), sizeBytes = 4096, realTestTop1Survival = .90001)
        val new = old.copy(sha256 = "a".repeat(12) + "c".repeat(52), sizeBytes = 4097, realTestTop1Survival = .90002)
        val rows = comparison.build(old, new)
        for (field in listOf(Field.CONTENT, Field.SIZE, Field.METRICS)) {
            val row = rows.field(field)
            assertEquals(row.oldText, row.newText)
            assertTrue("Hidden precision differs for $field", row.differs)
        }
        assertEquals(setOf(Field.CONTENT, Field.SIZE, Field.METRICS), rows.filter { it.differs }.map { it.field }.toSet())
    }

    @Test
    fun datasetTimestampsStillDifferWhenTheDisplayedDateMatches() {
        val old = info().copy(datasetCreatedAt = Instant.parse("2026-10-01T01:00:00Z"))
        val row = comparison.build(old, old.copy(datasetCreatedAt = old.datasetCreatedAt!!.plusSeconds(1))).field(Field.DATASET)
        assertEquals(row.oldText, row.newText)
        assertTrue(row.differs)
    }

    @Test
    fun releaseDateTakesPrecedenceOverTrainingAndEquivalentOffsetsCompareEqually() {
        val manifest = manifest()
        val offset = manifest.copy(training = manifest.training.copy(trainedAt = "2026-10-02T08:00:00+08:00"))
        val value = ModelVersionInfo.fromManifest(offset, PACKAGE_SHA, 4096, ModelSource.CUSTOM, HOST_VERSION)
        assertFalse(comparison.build(info(), value).field(Field.RELEASED_AT).differs)
        val released = value.copy(releasedAt = Instant.parse("2026-10-03T00:00:00Z"))
        val row = comparison.build(value, released).field(Field.RELEASED_AT)
        assertEquals("2026-10-02T00:00:00Z", row.oldText)
        assertEquals("2026-10-03T00:00:00Z", row.newText)
        assertTrue(row.differs)
    }

    @Test
    fun installationTimeAppearsOnlyInTheInstalledColumn() {
        val value = info().copy(installedAtMillis = ModelComparisonTestFixtures.INSTALLED_AT.toEpochMilli())
        val row = comparison.build(value, value).field(Field.INSTALLED_AT)
        assertEquals("2026-10-04T01:02:03Z", row.oldText)
        assertEquals("-", row.newText)
        assertTrue(row.differs)
        assertFalse(comparison.build(info(), value).field(Field.INSTALLED_AT).differs)
    }

    @Test
    fun catalogAdapterSupportsComparisonBeforeDownloadingAndMarksAbsentDates() {
        val model = catalog().models.single()
        val version = model.versions.single()
        val value = ModelVersionInfo.fromCatalog(model, version)
        assertEquals(PACKAGE_SHA, value.sha256)
        assertEquals(4096L, value.sizeBytes)
        assertEquals(ModelSource.OFFICIAL, value.source)
        assertNull(value.datasetCreatedAt)
        assertNull(value.installedAtMillis)
        assertNull(value.status)
        val rows = comparison.build(info(), value)
        assertEquals("test-fixture (-)", rows.field(Field.DATASET).newText)
        assertEquals("90.0% (+10.0 pp)", rows.field(Field.METRICS).newText)
        assertEquals("2026-10-03T00:00:00Z", rows.field(Field.RELEASED_AT).newText)
        assertEquals("Official", rows.field(Field.SOURCE).newText)
    }

    @Test
    fun manifestAdapterUsesPackageMetadataAndRealTestMetricsAndCopiesNames() {
        val names = linkedMapOf("en" to "Original")
        val manifest = manifest().copy(displayName = names)
        val value = ModelVersionInfo.fromManifest(manifest, PACKAGE_SHA, 4096, ModelSource.CUSTOM, HOST_VERSION)
        names["en"] = "Changed"
        assertEquals("Original", value.displayName["en"])
        assertThrows(UnsupportedOperationException::class.java) { (value.displayName as MutableMap<String, String>)["en"] = "Other" }
        assertEquals(PACKAGE_SHA, value.sha256)
        assertEquals(4096L, value.sizeBytes)
        assertEquals(.9, value.realTestTop1Survival!!, 0.0)
        assertEquals(.8, value.realTestRuleTop1Survival!!, 0.0)
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), value.datasetCreatedAt)
        assertNull(value.status)
    }

    @Test
    fun installedAdapterRecognizesOnlyTheExactCatalogArtifactAndRevocationOverridesCurrent() {
        val installed = installed()
        val plain = ModelVersionInfo.fromInstalled(installed, HOST_VERSION)
        assertEquals(ModelVersionStatus.INSTALLED, plain.status)
        assertEquals(installed.installedAtMillis, plain.installedAtMillis)
        assertNull(plain.releasedAt)
        val current = ModelVersionInfo.fromInstalled(installed, HOST_VERSION, installed.key)
        assertEquals(ModelVersionStatus.CURRENT, current.status)
        val revokedCatalog = catalog(revoked = true)
        val revoked = ModelVersionInfo.fromInstalled(installed, HOST_VERSION, installed.key, revokedCatalog)
        assertEquals(ModelVersionStatus.REVOKED, revoked.status)
        assertEquals(Instant.parse("2026-10-03T00:00:00Z"), revoked.releasedAt)
        val otherContent = ModelVersionInfo.fromInstalled(installed, HOST_VERSION, installed.key, catalog(hash = "c".repeat(64), revoked = true))
        assertEquals(ModelVersionStatus.CURRENT, otherContent.status)
        assertNull(otherContent.releasedAt)
        val otherIdentity = installed.copy(manifest = installed.manifest.copy(modelId = "custom.model"))
        assertEquals(ModelVersionStatus.INSTALLED, ModelVersionInfo.fromInstalled(otherIdentity, HOST_VERSION, catalog = revokedCatalog).status)
        val catalogModel = revokedCatalog.models.single()
        assertEquals(ModelVersionStatus.REVOKED, ModelVersionInfo.fromCatalog(catalogModel, catalogModel.versions.single()).status)
    }

    @Test
    fun compatibilityUsesTheCurrentHostSchemaAndNamesHashWithHostUpdateFirst() {
        val base = manifest()
        fun compatibility(manifest: org.autojs.autojs.core.accessibility.codegen.model.ModelManifest) =
            ModelVersionInfo.fromManifest(manifest, PACKAGE_SHA, 4096, ModelSource.CUSTOM, HOST_VERSION).compatibility
        assertEquals(ModelCatalogCompatibility.COMPATIBLE, compatibility(base))
        assertEquals(ModelCatalogCompatibility.NEEDS_HOST_UPDATE, compatibility(base.copy(minHostVersionCode = HOST_VERSION + 1, featureSchemaVersion = 99)))
        assertEquals(ModelCatalogCompatibility.INCOMPATIBLE, compatibility(base.copy(featureSchemaVersion = 99)))
        assertEquals(ModelCatalogCompatibility.INCOMPATIBLE, compatibility(base.copy(featureNamesHash = "0".repeat(16))))
        val customized = ModelVersionInfo.fromManifest(base.copy(featureSchemaVersion = 99, featureNamesHash = "0".repeat(16)),
            PACKAGE_SHA, 4096, ModelSource.CUSTOM, HOST_VERSION, 99..99) { "0".repeat(16) }
        assertEquals(ModelCatalogCompatibility.COMPATIBLE, customized.compatibility)
    }

    @Test
    fun allDisplayFormattingCanBeLocalizedWithoutAffectingDifferences() {
        val localized = object : ModelComparisonTestFixtures.Formatter() {
            override fun source(value: ModelSource) = "source:$value"
            override fun compatibility(value: ModelCatalogCompatibility) = "compatibility:$value"
            override fun status(value: ModelVersionStatus) = "status:$value"
            override fun size(bytes: Long) = "bytes:$bytes"
            override fun integer(value: Int) = "number:$value"
            override fun percentage(value: Double) = "percent:$value"
            override fun percentagePoints(value: Double) = "points:${kotlin.math.round(value)}"
            override fun date(value: Instant) = "date:$value"
            override fun dateTime(value: Instant) = "time:$value"
            override fun pair(field: Field, primary: String, secondary: String) = "$field|$primary|$secondary"
        }
        val value = info().copy(status = ModelVersionStatus.CURRENT)
        val rows = VersionComparison(localized, "zh-CN").build(value, value)
        assertEquals("NAME|排序模型|autojs6.codegen.ranker", rows.field(Field.NAME).oldText)
        assertEquals("VERSION|1.0.0|number:1", rows.field(Field.VERSION).oldText)
        assertEquals("source:CUSTOM", rows.field(Field.SOURCE).oldText)
        assertEquals("bytes:4096", rows.field(Field.SIZE).oldText)
        assertEquals("COMPATIBILITY|number:${FeatureSchema.featureSchemaVersion}|compatibility:COMPATIBLE", rows.field(Field.COMPATIBILITY).oldText)
        assertEquals("DATASET|test-fixture|date:2026-10-01T00:00:00Z", rows.field(Field.DATASET).oldText)
        assertEquals("METRICS|percent:0.9|points:10.0", rows.field(Field.METRICS).oldText)
        assertEquals("time:2026-10-02T00:00:00Z", rows.field(Field.RELEASED_AT).oldText)
        assertEquals("status:CURRENT", rows.field(Field.STATUS).oldText)
        assertTrue(rows.none { it.differs })
    }

    @Test
    fun semanticallyEqualHashesAndZeroMetricsDoNotDiffer() {
        val value = info().copy(realTestTop1Survival = 0.0, realTestRuleTop1Survival = 0.0)
        val other = value.copy(sha256 = PACKAGE_SHA.uppercase(), realTestTop1Survival = -0.0)
        assertTrue(comparison.build(value, other).none { it.differs })
        val lessEffective = info().copy(realTestTop1Survival = .7, realTestRuleTop1Survival = .8)
        assertEquals("70.0% (-10.0 pp)", comparison.build(value, lessEffective).field(Field.METRICS).newText)
    }
}
