package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.autojs.autojs.network.UpdateIgnoreStore
import org.autojs.autojs6.R

/**
 * Loads both index plugins and locally installed plugins,
 * merging them into a PluginCenterItem list.
 *
 * Behavior strategy:
 * - First load local plugins and immediately push the list (local priority).
 * - Then load the index in the background, merge with local plugins and push again after success.
 * - If local discovery fails, notify UI to show a dialog and exit Activity through fatalError.
 *
 * zh-CN:
 *
 * 统一加载索引插件与本地已安装插件, 合并为 PluginCenterItem 列表.
 *
 * 行为策略:
 * - 先加载本地插件并立即推送列表 (本地优先).
 * - 紧接着在后台加载索引, 成功后与本地合并再推送一次.
 * - 若本地发现失败, 通过 fatalError 通知 UI 弹窗并退出 Activity.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Nov 26, 2025.
 * Modified by SuperMonster003 as of Jan 17, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 */
class PluginCenterViewModel : ViewModel() {

    // TODO by SuperMonster003 on Jan 17, 2026.
    // private val indexRepo = PluginIndexRepository()

    private val installedRepo = InstalledPluginRepository()
    private val legacyRepo = LegacyInstalledPluginRepository()
    private val enableStore = PluginEnableStore

    private val _items = MutableStateFlow<List<PluginCenterItem>>(emptyList())
    val items: StateFlow<List<PluginCenterItem>> = _items

    // Whether index loading is completed (includes "success/failure/empty" results, only means "no longer loading").
    // zh-CN: 索引加载是否已完成 (包含 "成功/失败/空" 三种结果, 仅代表 "不再加载").
    private val _indexLoaded = MutableStateFlow(false)
    val indexLoaded: StateFlow<Boolean> = _indexLoaded

    // Fatal exception discovered locally (requires user notification and Activity exit).
    // zh-CN: 本地发现的致命异常 (需要提示用户并退出 Activity).
    private val _fatalError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val fatalError: SharedFlow<String> = _fatalError

    /**
     * Load plugin list.
     *
     * zh-CN: 加载插件列表.
     *
     * @param forceRefreshIndex Whether to force refresh index.
     * - false: Use 1 minute throttling + ETag/backoff (normal page entry).
     * - true: Attempt to refresh index every time (still respects backoff window, for pull-to-refresh usage).
     *
     * zh-CN: 是否强制刷新索引.
     * - false: 使用 1 分钟节流 + ETag/退避 (普通进入页面).
     * - true: 每次都尝试刷新索引 (仍遵守退避窗口, 供下拉刷新使用).
     */
    fun load(context: Context, forceRefreshIndex: Boolean = false) {
        Log.d(TAG, "load: forceRefreshIndex = $forceRefreshIndex")
        viewModelScope.launch {
            _indexLoaded.value = false

            // First load local plugins (local priority).
            // zh-CN: 先加载本地插件 (本地优先).
            val installed = runCatching {
                installedRepo.discoverInstalled(context)
            }.onFailure { e ->
                // Treat "local discovery" failure as a serious exception.
                // zh-CN: 将 "本地发现" 失败视为严重异常.
                val message = context.getString(
                    R.string.error_failed_to_load_plugins_with_reason,
                    e.message ?: e.toString(),
                )
                _fatalError.tryEmit(message)
            }.getOrNull() ?: run {
                // Do not continue subsequent logic when unable to obtain local list.
                // zh-CN: 无法获取本地列表时不再继续后续逻辑.
                _items.value = emptyList()
                _indexLoaded.value = true
                return@launch
            }

            val legacyInstalled = runCatching {
                legacyRepo.discoverInstalled(context)
            }.onFailure { e ->
                Log.w(TAG, "legacy discovery failed: ${e.message}")
            }.getOrElse { emptyList() }

            installed.forEach { local ->
                if (local.bindError != null) {
                    val isAutoWakeSuccess = PluginWakeManager.tryAutoWakeIfNeeded(context, local.packageName)
                    enableStore.setEnabled(context, local.packageName, isAutoWakeSuccess)
                }
            }

            // Render list using "local only".
            // zh-CN: 使用 "仅本地" 渲染列表.
            val localAidlItems = installed.mapNotNull { local ->
                toPluginCenterItem(context, index = null, local = local)
            }
            val localLegacyItems = legacyInstalled.mapNotNull { local ->
                toLegacyPluginCenterItem(context, local)
            }
            val onlyLocalItems = localAidlItems + localLegacyItems
            _items.value = onlyLocalItems

            // Asynchronously load index and merge.
            // zh-CN: 异步加载索引并合并.
            val indexEntries = runCatching {
                // TODO by SuperMonster003 on Jan 17, 2026.
                // indexRepo.fetchOfficialIndex(context, forceRefresh = forceRefreshIndex)
                emptyList<PluginIndexEntry>()
            }.onFailure {
                // Index fetch exception is not fatal, just log it.
                // zh-CN: 索引获取异常不算致命, 使用日志记录即可.
                Log.w(TAG, "fetchOfficialIndex failed: ${it.message}")
            }.getOrElse { emptyList() }

            val installedByPkg = installed.associateBy { it.packageName }

            // Use index to drive UI, showing installable/updatable.
            // zh-CN: 用索引驱动 UI, 展示 installable/updatable.
            val fromIndex = indexEntries.mapNotNull { e ->
                val local = installedByPkg[e.packageName]
                toPluginCenterItem(context, index = e, local = local)
            }

            // Supplement plugins that "exist locally but not in index" (third-party/not yet in index).
            // zh-CN: 补充 "本地有但索引没有" 的插件 (第三方/暂未入索引).
            val extraAidlLocals = installed
                .filter { ins -> indexEntries.none { it.packageName == ins.packageName } }
                .mapNotNull { local -> toPluginCenterItem(context, index = null, local = local) }
            val extraLocals = extraAidlLocals + localLegacyItems

            _items.value = fromIndex + extraLocals
            _indexLoaded.value = true

            // When returning to the page, refresh if the details dialog is being displayed.
            // zh-CN: 返回页面时, 若详情对话框正在显示则刷新.
            PluginInfoDialogManager.refreshIfShowing(context, _items.value)
        }
    }

    fun setEnabled(context: Context, packageName: String, enabled: Boolean, error: PluginError? = null) {
        enableStore.setEnabled(context, packageName, enabled)
        _items.value = _items.value.map {
            if (it.packageName == packageName) {
                val nextEnabledState = when {
                    !enabled -> PluginEnabledState.DISABLED
                    error != null -> PluginEnabledState.ERROR(error)
                    else -> PluginEnabledState.READY
                }
                it.copy(
                    isEnabled = enabled,
                    enabledState = nextEnabledState,
                    lastError = error,
                )
            } else it
        }
        PluginInfoDialogManager.refreshIfShowing(context, _items.value)
    }

    fun ignoreUpdatableVersion(item: PluginCenterItem) {
        val v = item.updatableVersionCode ?: return
        UpdateIgnoreStore.ignoreVersion(item.packageName, v)
    }

    private fun toPluginCenterItem(context: Context, index: PluginIndexEntry?, local: InstalledPluginRepository.InstalledPlugin?): PluginCenterItem? {
        val packageName = local?.packageName ?: index?.packageName
        if (packageName.isNullOrBlank()) return null

        val title = local?.title ?: index?.title ?: packageName
        val description = local?.description ?: index?.description
        val author = local?.author ?: index?.author
        val collaborators = index?.collaborators ?: emptyList()

        val versionNameLocal = local?.versionName ?: index?.releases?.firstOrNull()?.versionName ?: context.getString(R.string.text_unknown)
        val versionCodeLocal = local?.versionCode
        val isInstalled = local != null

        // 未安装时: installable 取 releases 最新 (第一个).
        val latestRelease = index?.releases?.maxByOrNull { it.versionCode }

        // 已安装时: updatable 取 "大于 installed 且未忽略" 的最高版本.
        val targetUpdate = run {
            if (!isInstalled || versionCodeLocal == null) return@run null
            val candidates = index?.releases
                ?.filter { it.versionCode > versionCodeLocal }
                ?.sortedByDescending { it.versionCode }
                ?: emptyList()
            candidates.firstOrNull { !UpdateIgnoreStore.isIgnored(packageName, it.versionCode) }
        }

        val trustInfo = runCatching { PluginTrustManager.resolveTrustInfo(context, packageName) }.getOrElse {
            PluginTrustManager.TrustInfo(
                authorizedState = PluginAuthorizedState.REQUIRED,
                isOfficial = false,
                isTrusted = false,
                primaryFingerprintSha256 = null,
                fingerprintsSha256 = emptyList(),
            )
        }

        var enabled = enableStore.isEnabled(context, packageName, defaultEnabled = isInstalled)
        if (trustInfo.authorizedState == PluginAuthorizedState.REQUIRED && enabled) {
            enableStore.setEnabled(context, packageName, false)
            enabled = false
        }

        val canActivate = isInstalled && PluginWakeManager.buildWakeIntent(context, packageName) != null
        var activatedState = when {
            !canActivate -> PluginActivatedState.NOT_SUPPORTED
            else -> PluginActivatedState.UNKNOWN
        }

        val mappedError = local?.bindError?.let { PluginErrorMapper.fromThrowable(it) }
        if (mappedError != null && canActivate && activatedState == PluginActivatedState.UNKNOWN && PluginErrorMapper.shouldRecommendActivation(mappedError)) {
            activatedState = PluginActivatedState.RECOMMENDED
        }
        val authError = if (trustInfo.authorizedState == PluginAuthorizedState.REQUIRED) PluginError(PluginErrorCode.NOT_AUTHORIZED) else null
        val lastError = authError ?: mappedError
        val enabledState = when {
            !enabled -> PluginEnabledState.DISABLED
            mappedError != null -> PluginEnabledState.ERROR(mappedError)
            else -> PluginEnabledState.READY
        }
        val readyEnabled = enabled && enabledState is PluginEnabledState.READY

        return PluginCenterItem(
            title = title,
            packageName = packageName,
            versionName = versionNameLocal,
            versionCode = versionCodeLocal ?: latestRelease?.versionCode,
            versionDate = latestRelease?.versionDate,

            updatableVersionName = targetUpdate?.versionName,
            updatableVersionCode = targetUpdate?.versionCode,
            updatableVersionDate = targetUpdate?.versionDate,
            updatableApkUrl = targetUpdate?.apkUrl,
            updatableApkSha256 = targetUpdate?.apkSha256,
            updatableApkSizeBytes = targetUpdate?.apkSizeBytes,
            updatableChangelogUrl = targetUpdate?.changelogUrl,
            updatableChangelogText = targetUpdate?.changelogText,

            author = author ?: trustInfo.developer,
            collaborators = collaborators,
            description = description,

            packageSize = local?.packageSize ?: 0L,

            installableApkUrl = latestRelease?.apkUrl,
            installableApkSha256 = latestRelease?.apkSha256,
            installableApkSizeBytes = latestRelease?.apkSizeBytes,

            // TODO 已安装优先用应用图标; 未安装走默认占位图.
            icon = local?.icon,
            isEnabled = readyEnabled,
            isInstalled = isInstalled,
            firstInstallTime = local?.firstInstallTime,
            lastUpdateTime = local?.lastUpdateTime,
            // TODO M1 暂不接入单插件设置入口.
            settings = null,
            mechanism = PluginMechanism.AIDL,
            authorizedState = trustInfo.authorizedState,
            activatedState = activatedState,
            enabledState = enabledState,
            lastError = lastError,
            signingFingerprintSha256 = trustInfo.primaryFingerprintSha256,
            isOfficialVerified = trustInfo.isOfficial,
            canActivate = canActivate,
        )
    }

    private fun toLegacyPluginCenterItem(context: Context, local: LegacyInstalledPluginRepository.LegacyInstalledPlugin): PluginCenterItem? {
        val packageName = local.packageName
        if (packageName.isBlank()) return null

        val trustInfo = runCatching { PluginTrustManager.resolveTrustInfo(context, packageName) }.getOrElse {
            PluginTrustManager.TrustInfo(
                authorizedState = PluginAuthorizedState.REQUIRED,
                isOfficial = false,
                isTrusted = false,
                primaryFingerprintSha256 = null,
                fingerprintsSha256 = emptyList(),
            )
        }

        var enabled = enableStore.isEnabled(context, packageName, defaultEnabled = true)
        if (trustInfo.authorizedState == PluginAuthorizedState.REQUIRED && enabled) {
            enableStore.setEnabled(context, packageName, false)
            enabled = false
        }

        val enabledState = if (enabled) PluginEnabledState.READY else PluginEnabledState.DISABLED
        val lastError = if (trustInfo.authorizedState == PluginAuthorizedState.REQUIRED) PluginError(PluginErrorCode.NOT_AUTHORIZED) else null

        return PluginCenterItem(
            title = local.title,
            packageName = packageName,
            versionName = local.versionName,
            versionCode = local.versionCode,
            versionDate = null,

            updatableVersionName = null,
            updatableVersionCode = null,
            updatableVersionDate = null,
            updatableApkUrl = null,
            updatableApkSha256 = null,
            updatableApkSizeBytes = null,
            updatableChangelogUrl = null,
            updatableChangelogText = null,

            author = local.author ?: trustInfo.developer,
            collaborators = emptyList(),
            description = local.description,

            packageSize = local.packageSize,

            installableApkUrl = null,
            installableApkSha256 = null,
            installableApkSizeBytes = null,

            icon = local.icon,
            isEnabled = enabled,
            isInstalled = true,
            firstInstallTime = local.firstInstallTime,
            lastUpdateTime = local.lastUpdateTime,
            settings = null,
            mechanism = PluginMechanism.SDK,
            authorizedState = trustInfo.authorizedState,
            activatedState = PluginActivatedState.NOT_SUPPORTED,
            enabledState = enabledState,
            lastError = lastError,
            signingFingerprintSha256 = trustInfo.primaryFingerprintSha256,
            isOfficialVerified = trustInfo.isOfficial,
            canActivate = false,
        )
    }

    companion object {
        private const val TAG = "PluginCenterViewModel"
    }

}
