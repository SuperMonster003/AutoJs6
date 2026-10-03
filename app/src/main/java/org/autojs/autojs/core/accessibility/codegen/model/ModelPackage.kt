package org.autojs.autojs.core.accessibility.codegen.model

sealed interface ModelPackageResult

data class ModelPackage(
    val manifest: ModelManifest,
    val features: ModelFeatures,
    val model: ObliviousTreeModel,
) : ModelPackageResult

data class Rejected(val reason: ModelPackageRejectionReason, val detail: String) : ModelPackageResult

enum class ModelPackageRejectionReason {
    IO_ERROR, INVALID_ZIP, UNSUPPORTED_ZIP, INVALID_ENTRY, DUPLICATE_ENTRY, MISSING_ENTRY,
    ENTRY_TOO_LARGE, PACKAGE_TOO_LARGE, COMPRESSION_RATIO, ZIP_CRC_MISMATCH,
    INVALID_JSON, INVALID_MANIFEST, INCOMPATIBLE_SCHEMA, FEATURE_MISMATCH, HOST_TOO_OLD,
    MODEL_HASH_MISMATCH, MODEL_METADATA_MISMATCH, INVALID_MODEL, MODEL_CRC_MISMATCH,
}

data class ModelManifest(
    val format: Int,
    val modelId: String,
    val displayName: Map<String, String>,
    val versionCode: Int,
    val versionName: String,
    val kind: String,
    val algorithm: String,
    val featureSchemaVersion: Int,
    val featureNamesHash: String,
    val model: ModelBinaryMetadata,
    val dataset: ModelDatasetMetadata,
    val metrics: ModelMetrics,
    val training: ModelTrainingMetadata,
    val minHostVersionCode: Int,
    val license: String,
    val publisher: String,
)

data class ModelBinaryMetadata(val file: String, val size: Long, val sha256: String, val trees: Int, val depth: Int, val features: Int)
data class ModelDatasetMetadata(val datasetId: String, val groups: Int, val createdAt: String)
data class ModelMetricSummary(val top1Survival: Double?, val ruleTop1Survival: Double?, val regret: Double?, val groups: Int)
data class ModelMetrics(val test: ModelMetricSummary, val realTest: ModelMetricSummary)
data class ModelTrainingMetadata(val catboostVersion: String, val pythonVersion: String, val hostCommit: String, val trainedAt: String)
data class ModelFeatures(val format: Int, val featureSchemaVersion: Int, val names: List<String>, val namesHash: String)
