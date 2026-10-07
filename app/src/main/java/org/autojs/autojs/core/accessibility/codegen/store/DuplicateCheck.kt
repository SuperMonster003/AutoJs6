package org.autojs.autojs.core.accessibility.codegen.store

import org.autojs.autojs.core.accessibility.codegen.model.ModelManifest
import java.util.Collections
import java.util.Locale

/** Package identity is available in the signed catalog before a download starts. */
data class IncomingModel(val modelId: String, val versionCode: Int, val sha256: String) {
    init {
        require(modelId.isNotBlank()) { "Missing model identity" }
        require(versionCode > 0) { "Invalid model version" }
        require(Regex("[0-9a-fA-F]{64}").matches(sha256)) { "Invalid package SHA-256" }
    }

    companion object {
        fun fromCatalog(model: CatalogModel, version: CatalogModelVersion): IncomingModel =
            IncomingModel(model.modelId, version.versionCode, version.artifact.sha256)

        /** sha256 is the entire package hash, not manifest.model.sha256 (the binary hash). */
        fun fromManifest(manifest: ModelManifest, sha256: String): IncomingModel =
            IncomingModel(manifest.modelId, manifest.versionCode, sha256)

        fun fromInstalled(model: InstalledModel): IncomingModel =
            IncomingModel(model.modelId, model.versionCode, model.sha256)
    }
}

/** Read-only duplicate detection. The caller obtains replacement consent before installing. */
object DuplicateCheck {
    sealed interface Result
    data object None : Result
    data class Identical(val existing: InstalledModel) : Result
    data class SameModel(val existing: List<InstalledModel>, val compareWith: InstalledModel) : Result

    fun evaluate(incoming: IncomingModel, registry: Collection<InstalledModel>, currentKey: String? = null): Result {
        // Check the full content hash across all identities, before considering version numbers.
        val identical = registry.filter { it.sha256.equals(incoming.sha256, ignoreCase = true) }
            .minWithOrNull(compareByDescending<InstalledModel> { it.key == currentKey }
                .thenByDescending { it.modelId == incoming.modelId }
                .thenByDescending { it.versionCode }
                .thenBy { it.modelId }
                .thenBy { it.key })
        if (identical != null) return Identical(identical)

        val existing = registry.filter { it.modelId == incoming.modelId }
            .sortedWith(compareByDescending<InstalledModel> { it.versionCode }
                .thenBy { it.sha256.lowercase(Locale.ROOT) }
                .thenBy { it.key })
        if (existing.isEmpty()) return None

        // Same-version imports may coexist. Prefer the current one within that version,
        // then use the full hash to make the choice independent of registry iteration order.
        val compareWith = existing.minWith(compareByDescending<InstalledModel> { it.versionCode == incoming.versionCode }
            .thenByDescending { it.key == currentKey }
            .thenByDescending { it.versionCode }
            .thenBy { it.sha256.lowercase(Locale.ROOT) }
            .thenBy { it.key })
        return SameModel(Collections.unmodifiableList(existing), compareWith)
    }
}
