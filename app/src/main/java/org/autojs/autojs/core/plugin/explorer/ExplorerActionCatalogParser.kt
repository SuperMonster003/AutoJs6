package org.autojs.autojs.core.plugin.explorer

import android.os.Bundle
import androidx.core.os.BundleCompat
import org.autojs.plugin.explorer.api.ExplorerActionCatalogKeys
import org.autojs.plugin.explorer.api.ExplorerActionProtocol

internal object ExplorerActionCatalogParser {

    fun parse(packageName: String, catalog: Bundle): List<ExplorerActionDescriptor> {
        val protocolVersion = catalog.getInt(ExplorerActionCatalogKeys.PROTOCOL_VERSION, 0)
        require(protocolVersion in ExplorerActionProtocol.MIN_SUPPORTED_VERSION..ExplorerActionProtocol.MAX_SUPPORTED_VERSION) {
            "Unsupported Explorer Action catalog protocol version: $protocolVersion"
        }
        val actionBundles = BundleCompat.getParcelableArrayList(
            catalog,
            ExplorerActionCatalogKeys.ACTIONS,
            Bundle::class.java,
        ).orEmpty()
        require(actionBundles.size <= ExplorerActionProtocol.MAX_ACTIONS_PER_PLUGIN) {
            "Explorer Action catalog contains too many actions: ${actionBundles.size}"
        }

        val actionIds = HashSet<String>()
        return actionBundles.mapNotNull { action ->
            val descriptor = ExplorerActionCatalogPolicy.normalizeDescriptor(
                packageName = packageName,
                id = action.getString(ExplorerActionCatalogKeys.ID),
                labelResourceName = action.getString(ExplorerActionCatalogKeys.LABEL_RESOURCE_NAME),
                labelFallback = action.getString(ExplorerActionCatalogKeys.LABEL_FALLBACK),
                activityClassName = action.getString(ExplorerActionCatalogKeys.ACTIVITY_CLASS_NAME),
                priority = action.getInt(ExplorerActionCatalogKeys.PRIORITY, 0),
                targetKind = action.getInt(ExplorerActionCatalogKeys.TARGET_KIND, 0),
                accessMode = action.getInt(ExplorerActionCatalogKeys.ACCESS_MODE, 0),
                placement = action.getInt(ExplorerActionCatalogKeys.PLACEMENT, 0),
                mimeTypes = action.getStringArrayList(ExplorerActionCatalogKeys.MIME_TYPES),
                extensions = action.getStringArrayList(ExplorerActionCatalogKeys.EXTENSIONS),
            ) ?: return@mapNotNull null
            descriptor.takeIf { actionIds.add(it.id) }
        }
    }
}
