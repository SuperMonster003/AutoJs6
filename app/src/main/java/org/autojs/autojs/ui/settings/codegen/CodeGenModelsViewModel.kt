package org.autojs.autojs.ui.settings.codegen

import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.store.CatalogModel
import org.autojs.autojs.core.accessibility.codegen.store.CatalogModelVersion
import org.autojs.autojs.core.accessibility.codegen.store.InstalledModel
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalog
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogCompatibility
import org.autojs.autojs.core.accessibility.codegen.store.ModelUpdates
import java.util.Collections
import java.util.Locale

/** Pure presentation state. The activity owns IO, refresh timing and localized text. */
object CodeGenModelsViewModel {
    enum class RowStatus { AVAILABLE, UPDATE_AVAILABLE, INCOMPATIBLE, NEEDS_HOST_UPDATE, REVOKED }

    data class InstalledRow(
        val installed: InstalledModel,
        val compatibility: ModelCatalogCompatibility,
        val status: RowStatus,
        val isCurrent: Boolean,
        val deprecated: Boolean,
        val update: ModelUpdates.Update?,
        val isBusy: Boolean,
        val canSetCurrent: Boolean,
        val canDelete: Boolean,
        val canViewModelCard: Boolean,
        val canUpdate: Boolean,
    ) {
        val key: String get() = installed.key
    }

    data class InstalledGroup(
        val modelId: String,
        val displayName: Map<String, String>,
        val rows: List<InstalledRow>,
        val installedBytes: Long,
    )

    data class DownloadableRow(
        val model: CatalogModel,
        val version: CatalogModelVersion,
        val status: RowStatus,
        val isBusy: Boolean,
        val canDownload: Boolean,
    ) {
        val key: String get() = downloadKey(model.modelId, version)
    }

    data class DownloadableGroup(val model: CatalogModel, val rows: List<DownloadableRow>)

    data class State(
        val installedGroups: List<InstalledGroup>,
        val downloadableGroups: List<DownloadableGroup>,
        val current: InstalledModel?,
        val totalInstalledBytes: Long,
        val loading: Boolean,
        val catalogError: Throwable?,
        val canCheckUpdates: Boolean,
    )

    /** Same identity as the eventual installed record; busy keys can span both sections. */
    fun downloadKey(modelId: String, version: CatalogModelVersion): String =
        "$modelId@${version.versionCode}-${version.artifact.sha256.take(8)}"

    /**
     * The catalog is an already verified repository snapshot. A failed refresh or a display-only
     * fallback affects remote actions only: compatible installed packages remain usable.
     * busyKeys contains installed/download keys for operations that are currently in progress.
     */
    fun build(
        installed: List<InstalledModel>,
        currentKey: String?,
        catalog: ModelCatalog?,
        hostVersionCode: Int,
        busyKeys: Set<String> = emptySet(),
        loading: Boolean = false,
        catalogError: Throwable? = null,
    ): State {
        val installedHashes = installed.mapTo(HashSet()) { it.sha256.lowercase(Locale.ROOT) }
        val sortedInstalled = installed.sortedWith(compareBy<InstalledModel> { it.modelId }
            .thenByDescending { it.versionCode }.thenBy { it.sha256.lowercase(Locale.ROOT) }.thenBy { it.key })
        val current = sortedInstalled.firstOrNull { it.key == currentKey }
        val installedGroups = sortedInstalled.groupBy { it.modelId }.map { (modelId, versions) ->
            val rows = versions.map { item ->
                val compatibility = compatibility(item, hostVersionCode)
                val catalogVersion = catalog?.find(modelId)?.versions?.firstOrNull {
                    it.versionCode == item.versionCode && it.artifact.sha256.equals(item.sha256, ignoreCase = true)
                }
                // A newer version already on disk can be selected directly without downloading it.
                val update = ModelUpdates.find(item, catalog)?.takeUnless {
                    it.version.artifact.sha256.lowercase(Locale.ROOT) in installedHashes
                }
                val busy = item.key in busyKeys
                val isCurrent = item.key == current?.key
                InstalledRow(
                    installed = item,
                    compatibility = compatibility,
                    status = status(compatibility, catalogVersion?.revoked == true, update != null),
                    isCurrent = isCurrent,
                    deprecated = catalogVersion?.deprecated == true,
                    update = update,
                    isBusy = busy,
                    canSetCurrent = compatibility == ModelCatalogCompatibility.COMPATIBLE && !isCurrent && !busy,
                    canDelete = !busy,
                    canViewModelCard = !busy,
                    canUpdate = update != null && !busy && downloadKey(update.model.modelId, update.version) !in busyKeys,
                )
            }
            val named = versions.firstOrNull { it.key == current?.key } ?: versions.first()
            InstalledGroup(modelId, Collections.unmodifiableMap(LinkedHashMap(named.manifest.displayName)),
                freeze(rows), versions.sumOf { it.sizeBytes })
        }
        val downloadableGroups = catalog?.models.orEmpty().sortedBy { it.modelId }.mapNotNull { model ->
            val rows = model.versions.sortedByDescending { it.versionCode }.filter {
                it.artifact.sha256.lowercase(Locale.ROOT) !in installedHashes
            }.map { version ->
                val busy = downloadKey(model.modelId, version) in busyKeys
                DownloadableRow(model, version, status(version.compatibility, version.revoked), busy,
                    catalog?.remoteArtifactsAllowed == true && version.canDownload && !busy)
            }
            if (rows.isEmpty()) null else DownloadableGroup(model, freeze(rows))
        }
        return State(freeze(installedGroups), freeze(downloadableGroups), current,
            installed.sumOf { it.sizeBytes }, loading, catalogError, canCheckUpdates = !loading)
    }

    private fun compatibility(installed: InstalledModel, hostVersionCode: Int): ModelCatalogCompatibility = when {
        installed.manifest.minHostVersionCode > hostVersionCode -> ModelCatalogCompatibility.NEEDS_HOST_UPDATE
        installed.manifest.featureSchemaVersion !in FeatureSchema.SUPPORTED_SCHEMA ||
            installed.manifest.featureNamesHash != FeatureSchema.namesHash -> ModelCatalogCompatibility.INCOMPATIBLE
        else -> ModelCatalogCompatibility.COMPATIBLE
    }

    private fun status(compatibility: ModelCatalogCompatibility, revoked: Boolean, update: Boolean = false): RowStatus = when {
        revoked -> RowStatus.REVOKED
        compatibility == ModelCatalogCompatibility.NEEDS_HOST_UPDATE -> RowStatus.NEEDS_HOST_UPDATE
        compatibility == ModelCatalogCompatibility.INCOMPATIBLE -> RowStatus.INCOMPATIBLE
        update -> RowStatus.UPDATE_AVAILABLE
        else -> RowStatus.AVAILABLE
    }

    private fun <T> freeze(values: List<T>): List<T> = Collections.unmodifiableList(values)
}
