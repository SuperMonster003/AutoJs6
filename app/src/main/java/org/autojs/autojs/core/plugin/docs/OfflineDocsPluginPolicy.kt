package org.autojs.autojs.core.plugin.docs

import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract

/** What a candidate is checked for. zh-CN: 候选包的检查用途. */
internal enum class OfflineDocsEligibilityCheck {

    /** Serving the content: every enable state must allow it. zh-CN: 提供内容: 各项启用状态都必须允许. */
    LOAD,

    /**
     * The Plugin Center switch turning the plugin on: its saved state stays off until this handshake succeeds,
     * so only the system enable state counts.
     * zh-CN: 插件中心开关正在启用插件: 握手成功前已存状态保持关闭, 因此只看系统层面的启用状态.
     */
    ENABLE_HANDSHAKE,
}

internal object OfflineDocsPluginPolicy {

    private val contentVersionPattern = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}")
    private val sha256Pattern = Regex("[0-9a-f]{64}")

    fun rejectionCode(
        check: OfflineDocsEligibilityCheck,
        systemEnabled: Boolean,
        centerEnabled: Boolean,
        authorizedState: PluginAuthorizedState,
        sameSignerAsHost: Boolean,
        contractVersion: Int,
        contentVersion: String,
        contentFormat: String,
        assetRoot: String,
        entryPoint: String,
        fileCount: Long,
        totalBytes: Long,
        contentSha256: String,
        hostVersionCode: Long,
        requiresHostVersion: Long?,
        maxHostVersion: Long?,
    ): OfflineDocsPluginLoadError? = when {
        !systemEnabled -> OfflineDocsPluginLoadError.PLUGIN_DISABLED
        !centerEnabled && check != OfflineDocsEligibilityCheck.ENABLE_HANDSHAKE ->
            OfflineDocsPluginLoadError.PLUGIN_DISABLED
        !sameSignerAsHost && authorizedState != PluginAuthorizedState.OFFICIAL ->
            OfflineDocsPluginLoadError.IN_PROCESS_TRUST_REQUIRED
        contractVersion !in OfflineDocsPluginContract.MIN_SUPPORTED_VERSION..
                OfflineDocsPluginContract.MAX_SUPPORTED_VERSION ->
            OfflineDocsPluginLoadError.CONTRACT_VERSION_UNSUPPORTED
        !contentVersionPattern.matches(contentVersion) ->
            OfflineDocsPluginLoadError.CONTENT_VERSION_INVALID
        contentFormat != OfflineDocsPluginContract.CONTENT_FORMAT ->
            OfflineDocsPluginLoadError.CONTENT_FORMAT_UNSUPPORTED
        assetRoot != OfflineDocsPluginContract.ASSET_ROOT ->
            OfflineDocsPluginLoadError.ASSET_ROOT_UNSUPPORTED
        entryPoint != OfflineDocsPluginContract.ENTRY_POINT ->
            OfflineDocsPluginLoadError.ENTRY_POINT_UNSUPPORTED
        fileCount !in 1L..OfflineDocsPluginContract.MAX_FILE_COUNT ->
            OfflineDocsPluginLoadError.FILE_COUNT_INVALID
        totalBytes !in 1L..OfflineDocsPluginContract.MAX_TOTAL_BYTES ->
            OfflineDocsPluginLoadError.TOTAL_BYTES_INVALID
        !sha256Pattern.matches(contentSha256) ->
            OfflineDocsPluginLoadError.CONTENT_SHA256_INVALID
        requiresHostVersion == null ||
                requiresHostVersion < OfflineDocsPluginContract.REQUIRED_HOST_VERSION_CODE ||
                maxHostVersion != null && maxHostVersion < requiresHostVersion ->
            OfflineDocsPluginLoadError.CONTRACT_METADATA_INVALID
        !PluginCapabilityResolver.isHostVersionCompatible(hostVersionCode, requiresHostVersion) ->
            OfflineDocsPluginLoadError.HOST_VERSION_TOO_LOW
        maxHostVersion != null && hostVersionCode > maxHostVersion ->
            OfflineDocsPluginLoadError.HOST_VERSION_TOO_HIGH
        else -> null
    }
}
