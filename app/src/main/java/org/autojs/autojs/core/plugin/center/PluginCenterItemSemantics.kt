package org.autojs.autojs.core.plugin.center

/**
 * Content-level equality for [PluginCenterItem], shared by the ViewModel publish gate
 * and the RecyclerView diff so both agree on what counts as a visible change.
 * Drawable instances differ on every discovery pass and are therefore excluded;
 * icon changes are tracked through [PluginCenterItem.iconSignature] instead.
 * Comparisons are deliberately allocation-free because they run for every row on
 * every incremental discovery result.
 *
 * zh-CN: [PluginCenterItem] 的内容级等价判断, 由 ViewModel 发布门控与 RecyclerView
 * 差分共用, 确保二者对 "可见变化" 的认定一致. Drawable 实例在每轮发现中都会变化,
 * 因此被排除在比较之外; 图标变化改由 [PluginCenterItem.iconSignature] 跟踪.
 * 比较过程不创建临时对象, 因为每次增量发现都会对每一行执行此比较.
 */
object PluginCenterItemSemantics {

    fun areItemsTheSame(a: PluginCenterItem, b: PluginCenterItem): Boolean {
        return a.packageName == b.packageName
    }

    fun areContentsTheSame(a: PluginCenterItem, b: PluginCenterItem): Boolean {
        return haveSameVersionMetadata(a, b)
                && haveSameDownloadMetadata(a, b)
                && haveSamePresentationMetadata(a, b)
                && haveSameInstallationState(a, b)
                && haveSamePluginCapabilities(a, b)
                && haveSameRuntimeState(a, b)
    }

    fun isSameList(current: List<PluginCenterItem>, next: List<PluginCenterItem>): Boolean {
        if (current.size != next.size) {
            return false
        }
        return current.indices.all { areContentsTheSame(current[it], next[it]) }
    }

    private fun haveSameVersionMetadata(a: PluginCenterItem, b: PluginCenterItem): Boolean {
        return a.title == b.title
                && a.packageName == b.packageName
                && a.versionName == b.versionName
                && a.versionCode == b.versionCode
                && a.versionDate == b.versionDate
                && a.updatableVersionName == b.updatableVersionName
                && a.updatableVersionCode == b.updatableVersionCode
                && a.updatableVersionDate == b.updatableVersionDate
    }

    private fun haveSameDownloadMetadata(a: PluginCenterItem, b: PluginCenterItem): Boolean {
        return a.updatableApkUrl == b.updatableApkUrl
                && a.updatableApkSha256 == b.updatableApkSha256
                && a.updatableApkSizeBytes == b.updatableApkSizeBytes
                && a.updatableChangelogUrl == b.updatableChangelogUrl
                && a.updatableChangelogText == b.updatableChangelogText
                && a.requiresDowngradeReinstall == b.requiresDowngradeReinstall
                && a.packageSize == b.packageSize
                && a.installableApkUrl == b.installableApkUrl
                && a.installableApkSha256 == b.installableApkSha256
                && a.installableApkSizeBytes == b.installableApkSizeBytes
                && a.supportedAbis == b.supportedAbis
                && a.installedAbis == b.installedAbis
                && a.nativePageAlignment == b.nativePageAlignment
                && a.updatableNativePageAlignment == b.updatableNativePageAlignment
    }

    private fun haveSamePresentationMetadata(a: PluginCenterItem, b: PluginCenterItem): Boolean {
        return a.author == b.author
                && a.collaborators == b.collaborators
                && a.description == b.description
                && a.instructionHardCoded == b.instructionHardCoded
                && a.instructionMarkdownUrl == b.instructionMarkdownUrl
                && a.remoteIconUrl == b.remoteIconUrl
                && a.remoteNightIconUrl == b.remoteNightIconUrl
                && a.forceIgnoreLocalIcon == b.forceIgnoreLocalIcon
                && a.iconBackgroundColor == b.iconBackgroundColor
                && a.nightIconBackgroundColor == b.nightIconBackgroundColor
                && a.iconSignature == b.iconSignature
                && a.attention == b.attention
    }

    private fun haveSameInstallationState(a: PluginCenterItem, b: PluginCenterItem): Boolean {
        return a.isEnabled == b.isEnabled
                && a.isInstalled == b.isInstalled
                && a.firstInstallTime == b.firstInstallTime
                && a.lastUpdateTime == b.lastUpdateTime
                && a.settings == b.settings
                && a.source == b.source
                && a.isFeatured == b.isFeatured
    }

    private fun haveSamePluginCapabilities(a: PluginCenterItem, b: PluginCenterItem): Boolean {
        return a.engine == b.engine
                && a.variant == b.variant
                && a.engineId == b.engineId
                && a.isOcrService == b.isOcrService
                && a.isBarcodeService == b.isBarcodeService
                && a.serviceAction == b.serviceAction
                && a.mechanism == b.mechanism
                && a.requiresHostVersion == b.requiresHostVersion
                && a.hostVersionCode == b.hostVersionCode
    }

    private fun haveSameRuntimeState(a: PluginCenterItem, b: PluginCenterItem): Boolean {
        return a.authorizedState == b.authorizedState
                && a.activatedState == b.activatedState
                && a.enabledState == b.enabledState
                && a.lastError == b.lastError
                && a.signingFingerprintSha256 == b.signingFingerprintSha256
                && a.isOfficialVerified == b.isOfficialVerified
                && a.canActivate == b.canActivate
    }

}
