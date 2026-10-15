package org.autojs.autojs.core.plugin.explorer

import android.os.Bundle
import androidx.core.os.BundleCompat
import org.autojs.plugin.explorer.api.ExplorerActionCatalogKeys
import org.autojs.plugin.explorer.api.ExplorerActionProtocol

internal object ExplorerActionCatalogParser {

    fun parse(
        packageName: String,
        catalog: Bundle,
        advertisedProtocolVersion: Long,
    ): List<ExplorerActionDescriptor> {
        val protocolVersion = catalog.getInt(ExplorerActionCatalogKeys.PROTOCOL_VERSION, 0)
        require(
            ExplorerActionAvailabilityPolicy.isCatalogProtocolCompatible(
                advertisedProtocolVersion,
                protocolVersion,
            ),
        ) {
            "Explorer Action catalog protocol version $protocolVersion does not match advertised version " +
                advertisedProtocolVersion
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
        val descriptors = actionBundles.mapNotNull { action ->
            val descriptor = ExplorerActionCatalogPolicy.normalizeDescriptor(
                protocolVersion = protocolVersion,
                packageName = packageName,
                id = action.getString(ExplorerActionCatalogKeys.ID),
                labelResourceName = action.getString(ExplorerActionCatalogKeys.LABEL_RESOURCE_NAME),
                labelFallback = action.getString(ExplorerActionCatalogKeys.LABEL_FALLBACK),
                activityClassName = action.getString(ExplorerActionCatalogKeys.ACTIVITY_CLASS_NAME),
                priority = action.getInt(ExplorerActionCatalogKeys.PRIORITY, 0),
                targetKind = action.getInt(ExplorerActionCatalogKeys.TARGET_KIND, 0),
                cardinality = action.getIntOrNull(ExplorerActionCatalogKeys.CARDINALITY),
                accessMode = action.getInt(ExplorerActionCatalogKeys.ACCESS_MODE, 0),
                placement = action.getInt(ExplorerActionCatalogKeys.PLACEMENT, 0),
                presentation = action.getIntOrNull(ExplorerActionCatalogKeys.PRESENTATION),
                mimeTypes = action.getStringArrayList(ExplorerActionCatalogKeys.MIME_TYPES),
                extensions = action.getStringArrayList(ExplorerActionCatalogKeys.EXTENSIONS),
                fileNameSuffixes = action.getStringArrayList(
                    ExplorerActionCatalogKeys.FILE_NAME_SUFFIXES,
                ),
                probeForActionId = action.getStringOrNull(
                    ExplorerActionCatalogKeys.PROBE_FOR_ACTION_ID,
                ),
                outputMode = action.getIntOrNull(ExplorerActionCatalogKeys.OUTPUT_MODE),
                outputMimeTypes = action.getStringArrayList(ExplorerActionCatalogKeys.OUTPUT_MIME_TYPES),
                outputNameSuffix = action.getStringOrNull(ExplorerActionCatalogKeys.OUTPUT_NAME_SUFFIX),
                maxOutputBytes = action.getLongOrNull(ExplorerActionCatalogKeys.MAX_OUTPUT_BYTES),
                readSiblings = action.getBooleanOrNull(ExplorerActionCatalogKeys.READ_SIBLINGS),
                playbackProgress = action.getBooleanOrNull(ExplorerActionCatalogKeys.PLAYBACK_PROGRESS),
                relatedFileSuffixes = action.getStringArrayList(
                    ExplorerActionCatalogKeys.RELATED_FILE_SUFFIXES,
                ),
                filesystemPath = action.getBooleanOrNull(ExplorerActionCatalogKeys.FILESYSTEM_PATH),
                targetTrash = action.getBooleanOrNull(ExplorerActionCatalogKeys.TARGET_TRASH),
                targetRename = action.getBooleanOrNull(ExplorerActionCatalogKeys.TARGET_RENAME),
            ) ?: return@mapNotNull null
            descriptor.takeIf { actionIds.add(it.id) }
        }
        return ExplorerActionCatalogPolicy.validateDescriptorReferences(descriptors)
    }

    private fun Bundle.getIntOrNull(key: String): Int? {
        return if (containsKey(key)) getInt(key) else null
    }

    private fun Bundle.getLongOrNull(key: String): Long? {
        return if (containsKey(key)) getLong(key) else null
    }

    private fun Bundle.getStringOrNull(key: String): String? {
        return if (containsKey(key)) getString(key) ?: "" else null
    }

    private fun Bundle.getBooleanOrNull(key: String): Boolean? {
        return if (containsKey(key)) getBoolean(key) else null
    }
}
