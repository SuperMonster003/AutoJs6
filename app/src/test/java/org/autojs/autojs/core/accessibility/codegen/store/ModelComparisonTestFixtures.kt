package org.autojs.autojs.core.accessibility.codegen.store

import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.model.ModelBinaryMetadata
import org.autojs.autojs.core.accessibility.codegen.model.ModelDatasetMetadata
import org.autojs.autojs.core.accessibility.codegen.model.ModelManifest
import org.autojs.autojs.core.accessibility.codegen.model.ModelMetricSummary
import org.autojs.autojs.core.accessibility.codegen.model.ModelMetrics
import org.autojs.autojs.core.accessibility.codegen.model.ModelTrainingMetadata
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object ModelComparisonTestFixtures {
    const val MODEL_ID = "autojs6.codegen.ranker"
    const val HOST_VERSION = 100
    val PACKAGE_SHA = "a".repeat(64)
    val BINARY_SHA = "b".repeat(64)
    val INSTALLED_AT: Instant = Instant.parse("2026-10-04T01:02:03Z")

    fun manifest(code: Int = 1, id: String = MODEL_ID) = ModelManifest(
        format = 1,
        modelId = id,
        displayName = mapOf("en" to "Ranker", "zh-CN" to "排序模型"),
        versionCode = code,
        versionName = "$code.0.0",
        kind = "ranker",
        algorithm = "catboost-oblivious",
        featureSchemaVersion = FeatureSchema.featureSchemaVersion,
        featureNamesHash = FeatureSchema.namesHash,
        model = ModelBinaryMetadata("model.bin", 2048, BINARY_SHA, 1, 1, 64),
        dataset = ModelDatasetMetadata("test-fixture", 150, "2026-10-01T00:00:00Z"),
        metrics = ModelMetrics(ModelMetricSummary(.8, .7, .1, 150), ModelMetricSummary(.9, .8, .1, 10)),
        training = ModelTrainingMetadata("1.2", "3.12", "c".repeat(40), "2026-10-02T00:00:00Z"),
        minHostVersionCode = HOST_VERSION,
        license = "MPL-2.0",
        publisher = "AutoJs6",
    )

    fun installed(code: Int = 1, hash: String = PACKAGE_SHA, id: String = MODEL_ID) = InstalledModel(
        manifest = manifest(code, id),
        sha256 = hash,
        sizeBytes = 4096,
        source = ModelSource.CUSTOM,
        origin = "fixture.ajcgm",
        installedAtMillis = INSTALLED_AT.toEpochMilli(),
    )

    fun catalog(code: Int = 1, hash: String = PACKAGE_SHA, revoked: Boolean = false): ModelCatalog {
        val manifest = manifest(code)
        val file = "autojs6-codegen-$MODEL_ID-v$code-${hash.take(8)}.ajcgm"
        val url = "https://github.com/SuperMonster003/AutoJs6-Shared-Assets/releases/download/codegen-models-v1/$file"
        val version = CatalogModelVersion(code, manifest.versionName, manifest.featureSchemaVersion,
            manifest.featureNamesHash, HOST_VERSION, CatalogModelArtifact(file, 4096, hash, url, listOf(url)),
            "2026-10-03T00:00:00Z", manifest.dataset.datasetId, manifest.metrics, manifest.license,
            deprecated = false, revoked = revoked, compatibility = ModelCatalogCompatibility.COMPATIBLE,
            immutableFingerprint = "comparison-fixture")
        return ModelCatalog(1, 1, "codegen-models-v1", "2026-10-03T01:00:00Z",
            "SuperMonster003/AutoJs6-Shared-Assets", listOf(CatalogModel(MODEL_ID, manifest.displayName,
                mapOf("en" to "Test catalog"), if (revoked) null else code, listOf(version))))
    }

    /** English text lives only in the fixture; production callers supply localized resources. */
    open class Formatter : VersionComparison.Formatter {
        override fun source(value: ModelSource) = when (value) {
            ModelSource.OFFICIAL -> "Official"
            ModelSource.CUSTOM -> "Custom"
        }
        override fun compatibility(value: ModelCatalogCompatibility) = when (value) {
            ModelCatalogCompatibility.COMPATIBLE -> "Compatible"
            ModelCatalogCompatibility.INCOMPATIBLE -> "Incompatible"
            ModelCatalogCompatibility.NEEDS_HOST_UPDATE -> "Update AutoJs6"
        }
        override fun status(value: ModelVersionStatus) = when (value) {
            ModelVersionStatus.CURRENT -> "Current"
            ModelVersionStatus.INSTALLED -> "Installed"
            ModelVersionStatus.REVOKED -> "Revoked"
        }
        override fun size(bytes: Long): String = if (bytes >= 1024 * 1024) {
            String.format(Locale.ENGLISH, "%.1f MiB", bytes / (1024.0 * 1024))
        } else String.format(Locale.ENGLISH, "%.1f KiB", bytes / 1024.0)
        override fun integer(value: Int) = value.toString()
        override fun percentage(value: Double) = String.format(Locale.ENGLISH, "%.1f%%", value * 100)
        override fun percentagePoints(value: Double) = String.format(Locale.ENGLISH, "%+.1f pp", value)
        override fun date(value: Instant) = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(value)
        override fun dateTime(value: Instant) = DateTimeFormatter.ISO_INSTANT.format(value)
        override fun pair(field: VersionComparison.Field, primary: String, secondary: String) = "$primary ($secondary)"
    }
}
