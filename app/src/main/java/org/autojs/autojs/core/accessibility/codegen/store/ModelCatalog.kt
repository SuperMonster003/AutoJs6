package org.autojs.autojs.core.accessibility.codegen.store

import org.autojs.autojs.core.accessibility.codegen.model.ModelMetrics

data class ModelCatalog(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val releaseTag: String,
    val generatedAt: String,
    val repository: String,
    val models: List<CatalogModel>,
    /** A stale verified catalog can be displayed, but cannot authorize model downloads.
     * zh-CN: 已验证的旧目录可用于展示, 不能据此下载模型. */
    val remoteArtifactsAllowed: Boolean = true,
) {
    fun find(modelId: String): CatalogModel? = models.firstOrNull { it.modelId == modelId }
    fun canDownload(modelId: String, versionCode: Int): Boolean = remoteArtifactsAllowed &&
        find(modelId)?.versions?.firstOrNull { it.versionCode == versionCode }?.canDownload == true
}

data class CatalogModel(
    val modelId: String,
    val displayName: Map<String, String>,
    val summary: Map<String, String>,
    val recommendedVersionCode: Int?,
    val versions: List<CatalogModelVersion>,
) {
    fun displayName(languageTag: String): String = displayName[languageTag] ?: displayName.getValue("en")
}

enum class ModelCatalogCompatibility { COMPATIBLE, NEEDS_HOST_UPDATE, INCOMPATIBLE }

data class CatalogModelVersion(
    val versionCode: Int,
    val versionName: String,
    val featureSchemaVersion: Int,
    val featureNamesHash: String,
    val minHostVersionCode: Int,
    val artifact: CatalogModelArtifact,
    val releasedAt: String,
    val datasetId: String,
    val metrics: ModelMetrics,
    val license: String,
    val deprecated: Boolean,
    val revoked: Boolean,
    val compatibility: ModelCatalogCompatibility,
    /** All known immutable wire fields, normalized independently of JSON field order.
     * zh-CN: 全部已知不可变字段的标准化摘要, 与 JSON 字段顺序无关. */
    internal val immutableFingerprint: String,
) {
    // Deprecated versions remain available for an explicit version choice; never recommended.
    val canDownload: Boolean get() = !revoked && compatibility == ModelCatalogCompatibility.COMPATIBLE
}

data class CatalogModelArtifact(val fileName: String, val size: Long, val sha256: String, val url: String, val urls: List<String>)

open class ModelCatalogException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ModelCatalogParseException(message: String, cause: Throwable? = null) : ModelCatalogException(message, cause)
class ModelCatalogSignatureException(message: String, cause: Throwable? = null) : ModelCatalogException(message, cause)
class ModelCatalogRollbackException(message: String) : ModelCatalogException(message)
class ModelCatalogNetworkException(message: String, cause: Throwable? = null) : ModelCatalogException(message, cause)
class ModelCatalogStorageException(message: String, cause: Throwable? = null) : ModelCatalogException(message, cause)
