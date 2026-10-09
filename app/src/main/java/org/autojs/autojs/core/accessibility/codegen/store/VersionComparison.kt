package org.autojs.autojs.core.accessibility.codegen.store

import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.model.ModelManifest
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Collections
import java.util.Locale

enum class ModelVersionStatus { CURRENT, INSTALLED, REVOKED }

/** Common comparison metadata for a validated import, verified catalog entry, or installed package. */
data class ModelVersionInfo(
    val modelId: String? = null,
    val displayName: Map<String, String> = emptyMap(),
    val versionCode: Int? = null,
    val versionName: String? = null,
    val source: ModelSource? = null,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val featureSchemaVersion: Int? = null,
    val compatibility: ModelCatalogCompatibility? = null,
    val datasetId: String? = null,
    val datasetCreatedAt: Instant? = null,
    val realTestTop1Survival: Double? = null,
    val realTestRuleTop1Survival: Double? = null,
    val releasedAt: Instant? = null,
    val trainedAt: Instant? = null,
    val installedAtMillis: Long? = null,
    val license: String? = null,
    val status: ModelVersionStatus? = null,
) {
    companion object {
        /** No download is needed: the catalog already carries the package identity and metrics. */
        fun fromCatalog(model: CatalogModel, version: CatalogModelVersion): ModelVersionInfo = ModelVersionInfo(
            modelId = model.modelId,
            displayName = freezeNames(model.displayName),
            versionCode = version.versionCode,
            versionName = version.versionName,
            source = ModelSource.OFFICIAL,
            sha256 = version.artifact.sha256,
            sizeBytes = version.artifact.size,
            featureSchemaVersion = version.featureSchemaVersion,
            compatibility = version.compatibility,
            datasetId = version.datasetId,
            // Catalog v1 does not carry the dataset creation date; leave it explicitly missing.
            realTestTop1Survival = version.metrics.realTest.top1Survival,
            realTestRuleTop1Survival = version.metrics.realTest.ruleTop1Survival,
            releasedAt = timestamp(version.releasedAt),
            license = version.license,
            status = if (version.revoked) ModelVersionStatus.REVOKED else null,
        )

        /** Package size/hash and source must be supplied from the validated artifact, not model.bin. */
        fun fromManifest(
            manifest: ModelManifest,
            sha256: String,
            sizeBytes: Long,
            source: ModelSource,
            hostVersionCode: Int,
            supportedSchemas: IntRange = FeatureSchema.SUPPORTED_SCHEMA,
            expectedNamesHash: (Int) -> String? = FeatureSchema::namesHashOrNull,
        ): ModelVersionInfo = ModelVersionInfo(
            modelId = manifest.modelId,
            displayName = freezeNames(manifest.displayName),
            versionCode = manifest.versionCode,
            versionName = manifest.versionName,
            source = source,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            featureSchemaVersion = manifest.featureSchemaVersion,
            compatibility = when {
                manifest.minHostVersionCode > hostVersionCode -> ModelCatalogCompatibility.NEEDS_HOST_UPDATE
                manifest.featureSchemaVersion !in supportedSchemas || manifest.featureNamesHash != expectedNamesHash(manifest.featureSchemaVersion) -> ModelCatalogCompatibility.INCOMPATIBLE
                else -> ModelCatalogCompatibility.COMPATIBLE
            },
            datasetId = manifest.dataset.datasetId,
            datasetCreatedAt = timestamp(manifest.dataset.createdAt),
            realTestTop1Survival = manifest.metrics.realTest.top1Survival,
            realTestRuleTop1Survival = manifest.metrics.realTest.ruleTop1Survival,
            trainedAt = timestamp(manifest.training.trainedAt),
            license = manifest.license,
        )

        fun fromInstalled(
            installed: InstalledModel,
            hostVersionCode: Int,
            currentKey: String? = null,
            catalog: ModelCatalog? = null,
            supportedSchemas: IntRange = FeatureSchema.SUPPORTED_SCHEMA,
            expectedNamesHash: (Int) -> String? = FeatureSchema::namesHashOrNull,
        ): ModelVersionInfo {
            // A custom package with the same model/version is not the catalog artifact.
            val catalogVersion = catalog?.find(installed.modelId)?.versions?.firstOrNull {
                it.versionCode == installed.versionCode && it.artifact.sha256.equals(installed.sha256, ignoreCase = true)
            }
            return fromManifest(installed.manifest, installed.sha256, installed.sizeBytes, installed.source,
                hostVersionCode, supportedSchemas, expectedNamesHash).copy(
                releasedAt = catalogVersion?.releasedAt?.let(::timestamp),
                installedAtMillis = installed.installedAtMillis,
                status = when {
                    catalogVersion?.revoked == true -> ModelVersionStatus.REVOKED
                    installed.key == currentKey -> ModelVersionStatus.CURRENT
                    else -> ModelVersionStatus.INSTALLED
                },
            )
        }

        private fun timestamp(value: String): Instant = OffsetDateTime.parse(value).toInstant()
        private fun freezeNames(names: Map<String, String>): Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(names))
    }
}

/** Fixed E.5 rows. Localization is supplied by the caller; no Android dependency or UI language default. */
class VersionComparison(
    private val formatter: Formatter,
    private val languageTag: String = Locale.getDefault().toLanguageTag(),
) {
    enum class Field { NAME, VERSION, SOURCE, CONTENT, SIZE, COMPATIBILITY, DATASET, METRICS, RELEASED_AT, INSTALLED_AT, LICENSE, STATUS }
    data class Row(val field: Field, val oldText: String, val newText: String, val differs: Boolean)

    /** Implement with resource strings and locale-aware number/date formatting at the UI boundary. */
    interface Formatter {
        fun source(value: ModelSource): String
        fun compatibility(value: ModelCatalogCompatibility): String
        fun status(value: ModelVersionStatus): String
        fun size(bytes: Long): String
        fun integer(value: Int): String
        fun percentage(value: Double): String
        /** Input is percentage points, e.g. 10.0 for a survival improvement from 0.8 to 0.9. */
        fun percentagePoints(value: Double): String
        fun date(value: Instant): String
        fun dateTime(value: Instant): String
        /** Both parts are already localized; a missing individual part is "-". */
        fun pair(field: Field, primary: String, secondary: String): String
    }

    fun build(old: ModelVersionInfo, new: ModelVersionInfo): List<Row> = listOf(
        pairRow(Field.NAME, localizedName(old) to text(old.modelId), localizedName(new) to text(new.modelId), { it }, { it }),
        pairRow(Field.VERSION, text(old.versionName) to old.versionCode, text(new.versionName) to new.versionCode, { it }, formatter::integer),
        row(Field.SOURCE, old.source, new.source, formatter::source),
        row(Field.CONTENT, digest(old.sha256), digest(new.sha256)) { it.take(12) },
        row(Field.SIZE, old.sizeBytes, new.sizeBytes, formatter::size),
        pairRow(Field.COMPATIBILITY, old.featureSchemaVersion to old.compatibility, new.featureSchemaVersion to new.compatibility,
            formatter::integer, formatter::compatibility),
        pairRow(Field.DATASET, text(old.datasetId) to old.datasetCreatedAt, text(new.datasetId) to new.datasetCreatedAt, { it }, formatter::date),
        metricsRow(old, new),
        row(Field.RELEASED_AT, old.releasedAt ?: old.trainedAt, new.releasedAt ?: new.trainedAt, formatter::dateTime),
        // The incoming column never reports an installation time, even if its DTO was reused.
        row(Field.INSTALLED_AT, old.installedAtMillis?.let(Instant::ofEpochMilli), null, formatter::dateTime),
        row(Field.LICENSE, text(old.license), text(new.license)) { it },
        row(Field.STATUS, old.status, new.status, formatter::status),
    )

    private fun localizedName(info: ModelVersionInfo): String? {
        fun name(tag: String): String? = text(info.displayName[tag]) ?: info.displayName.entries
            .sortedBy { it.key }.firstOrNull { it.key.equals(tag, ignoreCase = true) && text(it.value) != null }?.value
        return name(languageTag) ?: name("en")
    }

    private fun metricsRow(old: ModelVersionInfo, new: ModelVersionInfo): Row {
        fun value(info: ModelVersionInfo) = presentPair(number(info.realTestTop1Survival) to number(info.realTestRuleTop1Survival))
        return row(Field.METRICS, value(old), value(new)) { (survival, rule) ->
            val delta = if (survival != null && rule != null) (survival - rule) * 100 else null
            formatter.pair(Field.METRICS, survival?.let(formatter::percentage) ?: MISSING,
                delta?.let(formatter::percentagePoints) ?: MISSING)
        }
    }

    private fun <A, B> pairRow(field: Field, old: Pair<A?, B?>, new: Pair<A?, B?>, first: (A) -> String, second: (B) -> String): Row =
        row(field, presentPair(old), presentPair(new)) {
            formatter.pair(field, it.first?.let(first) ?: MISSING, it.second?.let(second) ?: MISSING)
        }

    private fun <T> row(field: Field, old: T?, new: T?, format: (T) -> String): Row =
        Row(field, old?.let(format) ?: MISSING, new?.let(format) ?: MISSING, old != new)

    private fun <A, B> presentPair(value: Pair<A?, B?>): Pair<A?, B?>? = value.takeUnless { it.first == null && it.second == null }
    private fun text(value: String?): String? = value?.takeIf { it.isNotBlank() }
    private fun digest(value: String?): String? = text(value)?.lowercase(Locale.ROOT)
    private fun number(value: Double?): Double? = value?.let { if (it == 0.0) 0.0 else it }

    companion object {
        const val MISSING = "-"
    }
}
