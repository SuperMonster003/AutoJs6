package org.autojs.autojs.core.plugin.center

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.autojs.autojs6.R

/**
 * Loads both index plugins and locally installed plugins,
 * merging them into a PluginCenterItem list.
 * 
 * zh-CN: 统一加载索引插件与本地已安装插件, 合并为 PluginCenterItem 列表.
 */
class PluginCenterViewModel : ViewModel() {

    private val indexRepo = PluginIndexRepository()
    private val installedRepo = InstalledPluginRepository()

    private val _items = MutableStateFlow<List<PluginCenterItem>>(emptyList())
    val items: StateFlow<List<PluginCenterItem>> = _items

    fun load(context: Context) {
        viewModelScope.launch {
            val idxDeferred = async { runCatching { indexRepo.fetchOfficialIndex(context) }.getOrElse { emptyList() } }
            val insDeferred = async { runCatching { installedRepo.discoverInstalled(context) }.getOrElse { emptyList() } }

            val indexEntries = idxDeferred.await()
            val installed = insDeferred.await()

            val installedByPkg = installed.associateBy { it.packageName }

            // 1. Use index to drive UI first (ensuring "installable but not installed" items are displayed).
            // 1. [ zh-CN ] 优先用索引驱动 UI (确保 "未安装但可安装" 的项也能显示).
            val fromIndex = indexEntries.map { e ->
                val local = installedByPkg[e.packageName]
                toPluginCenterItem(context, index = e, local = local)
            }

            // 2. Add items that "exist locally but not in index" (third-party or not indexed yet).
            // 2. [ zh-CN ] 补充 "本地存在但索引里暂时没有" 的项 (第三方或暂未入索引).
            val extraLocals = installed
                .filter { ins -> indexEntries.none { it.packageName == ins.packageName } }
                .map { local -> toPluginCenterItem(context, index = null, local = local) }

            _items.value = fromIndex + extraLocals
        }
    }

    private fun toPluginCenterItem(context: Context, index: PluginIndexEntry?, local: InstalledPluginRepository.InstalledPlugin?): PluginCenterItem {
        val packageName = local?.packageName ?: index?.packageName.orEmpty()
        val title = local?.title ?: index?.title ?: packageName
        val description = local?.description ?: index?.description.orEmpty()
        val author = local?.author ?: index?.author
        val collaborators = index?.collaborators ?: emptyList()

        val versionName = local?.versionName ?: index?.versionName ?: context.getString(R.string.text_unknown)
        val localCode = local?.versionCode
        val indexCode = index?.versionCode

        val isInstalled = local != null
        val isUpdatable = isInstalled && (indexCode != null && indexCode > (localCode ?: -1))

        return PluginCenterItem(
            packageName = packageName,
            title = title,
            description = description,
            author = author,
            collaborators = collaborators,
            versionName = versionName,
            versionCode = localCode ?: indexCode,
            versionDate = index?.versionDate, // M1: 显示索引日期; 仅本地项时可为空
            isEnabled = true,                 // M1: 先统一 true, M2 再接入启用状态持久化
            isUpdatable = isUpdatable,
            icon = local?.icon,               // 已安装优先用应用图标; 未安装走默认占位图
            settings = null,                  // M1 暂不接入单插件设置入口
        )
    }
}
