package org.autojs.autojs.core.plugin.batchrename

import android.content.Context
import android.os.Bundle
import android.os.RemoteException
import android.os.TransactionTooLargeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.batchrename.api.BatchRenameCapabilityKeys
import org.autojs.plugin.batchrename.api.BatchRenameContract
import org.autojs.plugin.batchrename.api.BatchRenameLimits
import org.autojs.plugin.batchrename.api.BatchRenamePluginActions
import org.autojs.plugin.batchrename.api.BatchRenamePluginIds
import org.autojs.plugin.batchrename.api.BatchRenameValues
import org.autojs.plugin.batchrename.api.IBatchRenamePlugin
import org.autojs.plugin.common.api.PluginInfo
import kotlin.coroutines.cancellation.CancellationException

/**
 * Host wrapper for the Batch Rename Binder contract (docs/dev/batch-rename-plugin-protocol-v1.md, plugin roadmap
 * D13 / D14). The plugin only computes rename plans; the host executor and journal act on them, and the
 * `files.batchRename` script API reaches the plugin through [planForScript]. Missing, disabled, unauthorized and
 * incompatible plugins surface through the shared [AidlPluginHost] selection errors, which [planForScript] tags with
 * the script error codes of roadmap D27.
 *
 * The official plugin exports this service next to its INFO and Explorer Action services; the Plugin Center shows the
 * package once, under the identity of its common INFO service, and its enable and authorization records apply to all
 * three.
 *
 * zh-CN: Batch Rename Binder 契约的宿主封装. 插件只计算重命名方案; 宿主执行器与日志据此执行, `files.batchRename` 脚本 API 经
 * [planForScript] 访问插件. 插件缺失, 停用, 未授权与不兼容时由共享的 [AidlPluginHost] 选择错误报告, [planForScript] 再为其标上
 * 路线图 D27 的脚本错误码. 官方插件同时导出 INFO 与
 * Explorer Action 服务; 插件中心以公共 INFO 服务的身份只显示一次该包, 其启用与授权记录对三个服务都生效.
 */
object BatchRenamePluginHost {

    const val ACTION_BATCH_RENAME = BatchRenamePluginActions.BATCH_RENAME

    private val host = AidlPluginHost(
        action = ACTION_BATCH_RENAME,
        category = BatchRenamePluginActions.SERVICE_CATEGORY,
        tag = "BatchRenamePluginHost",
        moduleLabel = "Batch Rename plugin",
        proxyFactory = { binder -> IBatchRenamePlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
    )

    suspend fun discover(context: Context, packageName: String? = null) = withContext(Dispatchers.IO) {
        host.discover(context, packageName)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        host.probe(context, packageName)
    }

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    /**
     * Sends one plan request to the selected plugin and returns its answer unchanged; the caller validates the answer
     * and maps its error code. Throws the shared selection errors when no usable plugin exists and
     * [IllegalStateException] when the plugin advertises a contract version this host does not speak.
     * zh-CN: 向选中的插件发送一个方案请求并原样返回应答; 由调用方校验应答并映射错误码. 没有可用插件时抛出共享的选择错误,
     * 插件声明的契约版本本宿主不支持时抛出 [IllegalStateException].
     */
    suspend fun plan(context: Context, request: Bundle): Bundle = withContext(Dispatchers.IO) {
        val target = host.selectOrThrow(context, engine = BatchRenamePluginIds.ENGINE)
        val contractVersion = contractVersionOf(target.pluginInfo)
        check(BatchRenameContract.supportsContractVersion(contractVersion)) {
            "Batch Rename plugin contract version $contractVersion is not supported by this AutoJs6 build"
        }
        host.call(context, target, retryOnRemoteException = false) { proxy ->
            checkNotNull(proxy.plan(request)) { "Batch Rename plugin returned no plan" }
        }
    }

    /** The optional features a plugin advertises. zh-CN: 插件声明的可选功能. */
    fun featuresOf(info: PluginInfo?): List<String> =
        info?.capabilities?.getStringArray(BatchRenameCapabilityKeys.FEATURES)?.toList().orEmpty()

    /** The contract version a plugin advertises; 0 when it advertises none. zh-CN: 插件声明的契约版本; 未声明时为 0. */
    fun contractVersionOf(info: PluginInfo?): Int =
        info?.capabilities?.getInt(BatchRenameCapabilityKeys.CONTRACT_VERSION, 0) ?: 0

    /**
     * Whether `files.batchRename` can reach an installed, enabled, authorized and compatible plugin right now; never
     * throws.
     * zh-CN: `files.batchRename` 当前能否找到已安装, 已启用, 已授权且兼容的插件; 从不抛出.
     */
    suspend fun isAvailable(context: Context): Boolean = try {
        selectForScript(context)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /**
     * [plan] for the script API: the request and answer as script models, every failure as a
     * [BatchRenameScriptException] with its script code (plugin roadmap D27). Options of features the plugin does not
     * advertise are left out (see [BatchRenameScriptArguments.forPluginFeatures]).
     * zh-CN: 供脚本 API 使用的 [plan]: 请求与应答为脚本模型, 每个失败都是带脚本错误码的 [BatchRenameScriptException]. 插件未声明的
     * 功能对应的选项不会发送 (见 [BatchRenameScriptArguments.forPluginFeatures]).
     */
    suspend fun planForScript(context: Context, request: BatchRenamePlanRequest): BatchRenamePlanAnswer = withContext(Dispatchers.IO) {
        val target = selectForScript(context)
        val sent = request.copy(options = BatchRenameScriptArguments.forPluginFeatures(request.options, featuresOf(target.pluginInfo)))
        val answer = try {
            host.call(context, target, retryOnRemoteException = false) { proxy -> proxy.plan(BatchRenameBundles.request(sent)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TransactionTooLargeException) {
            throw BatchRenameScriptException(BatchRenameScriptCodes.LIMIT_EXCEEDED, "the plan request is too large for one Binder transaction", e)
        } catch (e: RemoteException) {
            throw BatchRenameScriptException(BatchRenameScriptCodes.INTERNAL, "the Batch Rename plugin stopped answering: ${e.message ?: e}", e)
        } ?: throw BatchRenameScriptException(BatchRenameScriptCodes.INTERNAL, "the Batch Rename plugin returned no plan")
        BatchRenameBundles.answer(answer)
    }

    /**
     * `files.batchRename.presets()`: the presets the plugin keeps (plugin roadmap D36). A plugin without the presets
     * feature is `PLUGIN_INCOMPATIBLE`.
     * zh-CN: `files.batchRename.presets()`: 插件保存的预设 (插件路线图 D36). 没有预设功能的插件为 `PLUGIN_INCOMPATIBLE`.
     */
    suspend fun presetsForScript(context: Context): List<BatchRenamePreset> = withContext(Dispatchers.IO) {
        BatchRenameBundles.presets(presetCall(context) { proxy -> proxy.presets(BatchRenameBundles.presetRequest()) })
    }

    /**
     * `files.batchRename.importPresets(json, { onConflict })` (plugin roadmap D36 / D43). A document longer than the
     * contract limit is `LIMIT_EXCEEDED` before it reaches the Binder.
     * zh-CN: `files.batchRename.importPresets(json, { onConflict })` (插件路线图 D36 / D43). 超过契约上限的文档在进入 Binder 之前即为
     * `LIMIT_EXCEEDED`.
     */
    suspend fun importPresetsForScript(context: Context, document: String, onConflict: String): BatchRenamePresetImport = withContext(Dispatchers.IO) {
        if (document.length > BatchRenameLimits.MAX_PRESET_DOCUMENT_CHARS) {
            throw BatchRenameScriptException(
                BatchRenameScriptCodes.LIMIT_EXCEEDED,
                "the preset document has ${document.length} characters, more than ${BatchRenameLimits.MAX_PRESET_DOCUMENT_CHARS}",
            )
        }
        BatchRenameBundles.presetImport(presetCall(context) { proxy -> proxy.importPresets(BatchRenameBundles.presetRequest(document, onConflict)) })
    }

    private suspend fun presetCall(context: Context, call: (IBatchRenamePlugin) -> Bundle?): Bundle {
        val target = selectForScript(context)
        if (BatchRenameValues.FEATURE_PRESETS !in featuresOf(target.pluginInfo)) BatchRenameScriptArguments.pluginWithoutPresets()
        return try {
            host.call(context, target, retryOnRemoteException = false, block = call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RemoteException) {
            throw BatchRenameScriptException(BatchRenameScriptCodes.INTERNAL, "the Batch Rename plugin stopped answering: ${e.message ?: e}", e)
        } ?: throw BatchRenameScriptException(BatchRenameScriptCodes.INTERNAL, "the Batch Rename plugin returned no answer")
    }

    private suspend fun selectForScript(context: Context): AidlPluginHost.Discovered {
        val target = try {
            host.selectOrThrow(context, engine = BatchRenamePluginIds.ENGINE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BatchRenameScriptException(unavailableCode(context), e.message ?: "the Batch Rename plugin is unavailable", e)
        }
        val contractVersion = contractVersionOf(target.pluginInfo)
        if (!BatchRenameContract.supportsContractVersion(contractVersion)) {
            throw BatchRenameScriptException(
                BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE,
                "Batch Rename plugin contract version $contractVersion is not supported by this AutoJs6 build",
            )
        }
        return target
    }

    /**
     * Why no plugin was selected: none installed, none enabled and authorized, or none compatible.
     * zh-CN: 未选中插件的原因: 未安装, 没有已启用且已授权的, 或没有兼容的.
     */
    private suspend fun unavailableCode(context: Context): String {
        val packages = runCatching { host.discover(context).map { it.serviceInfo.packageName }.distinct() }.getOrDefault(emptyList())
        return when {
            packages.isEmpty() -> BatchRenameScriptCodes.PLUGIN_UNAVAILABLE
            packages.none { PluginTrustManager.isAuthorized(context, it) && PluginEnableStore.isEnabled(context, it) } -> BatchRenameScriptCodes.PLUGIN_DISABLED
            else -> BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE
        }
    }
}
