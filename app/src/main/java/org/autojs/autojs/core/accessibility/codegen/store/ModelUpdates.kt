package org.autojs.autojs.core.accessibility.codegen.store

/** Update decisions use only local state. Catalogs must come from the verified repository. */
object ModelUpdates {
    data class Update(
        val installed: InstalledModel,
        val model: CatalogModel,
        val version: CatalogModelVersion,
    )

    /**
     * A display-only catalog never authorizes an update. Deprecated versions remain available
     * for an explicit download, but an update should not recommend them.
     */
    fun find(installed: InstalledModel, catalog: ModelCatalog?): Update? {
        if (installed.source != ModelSource.OFFICIAL || catalog?.remoteArtifactsAllowed != true) return null
        val model = catalog.find(installed.modelId) ?: return null
        val version = model.versions.asSequence()
            .filter { it.versionCode > installed.versionCode && it.canDownload && !it.deprecated }
            .maxByOrNull { it.versionCode } ?: return null
        return Update(installed, model, version)
    }

    /**
     * Read currentKey when the update completes, never when the download starts. A store must
     * evaluate this condition and persist the new selection under the same lock so a user's
     * intervening choice is preserved. Installation itself retains the old version.
     */
    fun shouldSwitch(old: InstalledModel, currentKey: String?): Boolean = currentKey == old.key

    /** Run on the process I/O executor, independently of the progress dialog's lifetime. */
    fun complete(store: ModelStore, update: Update, installed: InstalledModel): Boolean {
        require(installed.modelId == update.installed.modelId && installed.modelId == update.model.modelId &&
            installed.versionCode == update.version.versionCode && installed.versionCode > update.installed.versionCode &&
            installed.sha256 == update.version.artifact.sha256) { "Completed package does not match the requested update" }
        return store.selectIfCurrent(update.installed.key, installed.key)
    }
}
