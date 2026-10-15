package org.autojs.autojs.core.plugin.batchrename

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.batchrename.api.BatchRenameCapabilityKeys
import org.autojs.plugin.batchrename.api.BatchRenameContract
import org.autojs.plugin.batchrename.api.BatchRenamePluginActions
import org.autojs.plugin.batchrename.api.BatchRenamePluginIds
import org.autojs.plugin.batchrename.api.IBatchRenamePlugin
import org.autojs.plugin.common.api.PluginInfo

/**
 * Host wrapper for the Batch Rename Binder contract (docs/dev/batch-rename-plugin-protocol-v1.md, plugin roadmap
 * D13 / D14). The plugin only computes rename plans; the executor and the undo journal that act on them, and the
 * `files.batchRename` script API, arrive with plugin roadmap P3. Missing, disabled, unauthorized and incompatible
 * plugins surface through the shared [AidlPluginHost] selection errors.
 *
 * The official plugin exports this service next to its INFO and Explorer Action services; the Plugin Center shows the
 * package once, under the identity of its common INFO service, and its enable and authorization records apply to all
 * three.
 *
 * zh-CN: Batch Rename Binder 契约的宿主封装. 插件只计算重命名方案; 据此执行的执行器, 撤销日志与 `files.batchRename` 脚本 API
 * 随插件路线图 P3 落地. 插件缺失, 停用, 未授权与不兼容时由共享的 [AidlPluginHost] 选择错误报告. 官方插件同时导出 INFO 与
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

    /** The contract version a plugin advertises; 0 when it advertises none. zh-CN: 插件声明的契约版本; 未声明时为 0. */
    fun contractVersionOf(info: PluginInfo?): Int =
        info?.capabilities?.getInt(BatchRenameCapabilityKeys.CONTRACT_VERSION, 0) ?: 0
}
