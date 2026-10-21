package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.PluginSettingsProvider
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingEffect
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.archive.ImportPlan.Prerequisite
import org.autojs.autojs.core.pref.archive.ImportPlan.SkipReason
import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId

/**
 * Plans an import (docs/settings/ROADMAP.md T8, D8 / D9 / D20, appendix B "import rules"): a pure function of the key
 * declarations, the category files read from the archive, the selected categories and what [ImportEnvironment] says
 * about this device; the stores are only read (the stored names of key families, the keys to remove).
 *
 * Every selected category is overwritten as a whole (D8): an entry the archive has and its declaration accepts is put
 * (in the stored form of the declaration, so that an Enum keeps its key string and a Json value the text its codec
 * writes); a declared key, or a member of a key family of the category, that the store holds but the archive does not
 * is removed and gets its default back. Categories that are not selected are not touched, data categories are left to
 * their own codecs (T19). Entries are skipped by [SkipReason] (D9): a name the category does not declare (for `other`
 * only the registered `StateKey`s are written, the unregistered entries of the run-time fallback are `UNKNOWN_KEY`),
 * a type tag that is not the declared one, a value the declaration refuses, a plugin that is not installed
 * ([ImportTarget]), a category or type tag of a newer format. A key with a deployment item (D20 / T18) whose value is
 * not deployed on this device yet goes to [ImportPlan.deployments] instead of [ImportPlan.puts]; giving it up is
 * [ImportPlan.decline]. Undoing an import plans without deployments ([withDeployments] false, T9).
 *
 * A plugin directory of the archive (D3, P13.1) goes to [ImportPlan.plugins] when the plugin is installed and has a
 * [PluginSettingsProvider]; otherwise, and so for every directory in the first phase, which registers no provider, it
 * is skipped as a whole as `TARGET_MISSING`.
 *
 * Declarations are taken as [PrefKey]s rather than as `describe()` rows, since checking a value needs its type (the
 * candidates of an Enum, the codec of a Json key). Pure JVM (R3).
 *
 * zh-CN: 生成导入计划 (ROADMAP T8, D8 / D9 / D20, 附录 B "导入规则"): 输入键声明, 从归档读出的分类文件, 勾选的分类以及
 * [ImportEnvironment] 给出的本机情况, 是纯函数; 存储只读不写 (键族的已存储名称, 待删除的键). 每个勾选分类整体覆盖 (D8): 归档中有
 * 且声明接受的条目写入 (按声明的存储形式, Enum 保持键字符串, Json 为其编解码写出的文本); 存储中有而归档中没有的已声明键或分类键族
 * 成员被删除, 恢复默认. 未勾选的分类不动, 数据分类交给各自的编解码 (T19). 条目按 [SkipReason] 跳过 (D9): 分类未声明的名称 (`other`
 * 只写登记过的 `StateKey`, 运行时兜底的未登记条目为 `UNKNOWN_KEY`), 类型标签与声明不符, 声明拒绝的值, 插件未安装 ([ImportTarget]),
 * 更新格式的分类或类型标签. 带部署项的键 (D20 / T18) 在值尚未在本机部署时进入 [ImportPlan.deployments] 而非 [ImportPlan.puts];
 * 放弃即 [ImportPlan.decline]. 撤销导入时不生成部署项 ([withDeployments] 为 false, T9). 归档中的插件目录 (D3, P13.1) 在插件已安装且
 * 有 [PluginSettingsProvider] 时进入 [ImportPlan.plugins], 否则整体计为 `TARGET_MISSING` (第一期不登记提供方, 因此全部如此).
 * 声明以 [PrefKey] 而非 `describe()` 行传入,
 * 因为校验值需要其类型 (Enum 的候选项, Json 键的编解码). 纯 JVM (R3).
 */
object SettingsImportPlanner {

    /**
     * The plan of importing [selected] from [files] (the decoded category files, at least those of [selected]; the
     * reader gives them already renamed, P7.1). A selected settings category without a file is an error: the wizard
     * lists only the categories of the archive. [plugins] are the plugin directories to import (the manifest's
     * `plugins`; the first phase has no choice per plugin), [providers] the plugin settings providers of this device.
     * zh-CN: 从 [files] (解码后的分类文件, 至少包含 [selected] 的; 读取方已做改名, P7.1) 导入 [selected] 的计划. 勾选的设置分类没有
     * 对应文件属于调用错误: 向导只列出归档中的分类. [plugins] 为要导入的插件目录 (manifest 的 `plugins`; 第一期不能逐个插件选择),
     * [providers] 为本机的插件设置提供方.
     */
    @JvmStatic
    @JvmOverloads
    fun plan(
        files: List<SettingsSnapshotCodec.Decoded>,
        selected: Collection<String>,
        environment: ImportEnvironment,
        withDeployments: Boolean = true,
        keys: List<PrefKey<*>> = SettingsRegistry.keys,
        families: List<KeyFamily<*, *>> = SettingsRegistry.families,
        targets: List<ImportTarget> = SettingsImportTargets.ALL,
        open: (StoreId) -> SettingsStore = { SettingsStores[it] },
        plugins: List<SettingsArchiveManifest.PluginEntry> = emptyList(),
        providers: List<PluginSettingsProvider> = SettingsRegistry.pluginSettingsProviders,
    ): ImportPlan {
        val byId = files.associateBy { it.category.id }
        val owners = Owners(keys, families)
        val ids = selected.distinct().sortedBy { SettingCategory.ofIdOrNull(it)?.order ?: Int.MAX_VALUE }
        val builder = Builder(environment, withDeployments, owners, targets, open)
        val planned = ArrayList<String>()
        ids.forEach { id ->
            val category = SettingCategory.ofIdOrNull(id)
            if (category?.isData == true) return@forEach
            val file = byId[id] ?: throw IllegalArgumentException("No category file for the selected category \"$id\"")
            planned.add(id)
            if (category == null) builder.newer(file) else builder.category(category, file)
        }
        val imported = ArrayList<String>()
        plugins.distinctBy { it.id }.forEach { plugin ->
            val installed = plugin.id in environment.installedPlugins
            if (installed && providers.any { it.packageName == plugin.id }) {
                imported.add(plugin.id)
            } else {
                val detail = if (installed) "no settings provider" else "not installed"
                builder.skips.add(ImportPlan.Skip(ImportPlan.PLUGINS, plugin.id, null, SkipReason.TARGET_MISSING, detail))
            }
        }
        return ImportPlan(planned, builder.puts, builder.removes, builder.skips, builder.deployments, imported)
    }

    /** The declaration that owns a stored name: a declared key, else a member of the longest matching key family. */
    private class Owners(keys: List<PrefKey<*>>, families: List<KeyFamily<*, *>>) {

        private val byName: Map<Pair<String, String>, PrefKey<*>> = keys.associateBy { it.store.name to it.name }

        private val familiesByStore: Map<String, List<KeyFamily<*, *>>> =
            families.groupBy { it.template.store.name }.mapValues { (_, list) -> list.sortedByDescending { it.prefix.length } }

        fun of(store: String, name: String): PrefKey<*>? = byName[store to name]
            ?: familiesByStore[store]?.firstOrNull { it.matches(name) }?.let { it.key(it.suffixOf(name)!!) }

        fun keysOf(category: SettingCategory): List<PrefKey<*>> = byName.values.filter { it.category == category }

        fun familiesOf(category: SettingCategory): List<KeyFamily<*, *>> = familiesByStore.values.flatten().filter { it.category == category }
    }

    private class Builder(
        private val environment: ImportEnvironment,
        private val withDeployments: Boolean,
        private val owners: Owners,
        private val targets: List<ImportTarget>,
        private val open: (StoreId) -> SettingsStore,
    ) {

        val puts = ArrayList<ImportPlan.Put>()
        val removes = ArrayList<ImportPlan.Remove>()
        val skips = ArrayList<ImportPlan.Skip>()
        val deployments = ArrayList<ImportPlan.Deployment>()

        /** A category of a newer version: every entry is skipped. */
        fun newer(file: SettingsSnapshotCodec.Decoded) {
            val id = file.category.id
            file.category.stores.forEach { (store, entries) -> entries.keys.forEach { skips.add(ImportPlan.Skip(id, store, it, SkipReason.NEWER_FORMAT)) } }
            file.rejections.forEach { skips.add(ImportPlan.Skip(id, it.store, it.name, SkipReason.NEWER_FORMAT, it.reason)) }
        }

        fun category(category: SettingCategory, file: SettingsSnapshotCodec.Decoded) {
            val id = category.id
            // Names the archive has, read or not: they are not removed. A store that could not be read is not touched.
            val present = HashSet<Pair<String, String>>()
            val unreadable = HashSet<String>()
            file.rejections.forEach { rejection ->
                val reason = if (rejection.unknownTag) SkipReason.NEWER_FORMAT else SkipReason.INVALID_VALUE
                skips.add(ImportPlan.Skip(id, rejection.store, rejection.name, reason, rejection.reason))
                if (rejection.name == null) unreadable.add(rejection.store) else present.add(rejection.store to rejection.name)
            }
            file.category.stores.forEach { (store, entries) ->
                val missing = HashMap<String, String>()
                targets.forEach { target ->
                    target.packages(store, entries).forEach { (name, packageName) -> if (packageName !in environment.installedPlugins) missing[name] = packageName }
                }
                entries.forEach { (name, value) ->
                    present.add(store to name)
                    entry(id, category, store, name, value, missing[name])
                }
            }
            val stored = HashMap<StoreId, Set<String>>()
            fun storedNames(store: StoreId) = stored.getOrPut(store) { open(store).all().keys }
            owners.keysOf(category).forEach { key ->
                if (key.store.name !in unreadable && (key.store.name to key.name) !in present && key.name in storedNames(key.store)) {
                    removes.add(ImportPlan.Remove(id, key.store, key.name, effectOf(key)))
                }
            }
            owners.familiesOf(category).forEach { family ->
                val store = family.template.store
                if (store.name in unreadable) return@forEach
                storedNames(store).filter { family.matches(it) && (store.name to it) !in present }.sorted().forEach { name ->
                    // A longer prefix of another category may own the name.
                    val owner = owners.of(store.name, name)
                    if (owner?.category == category && removes.none { it.store == store && it.name == name }) {
                        removes.add(ImportPlan.Remove(id, store, name, effectOf(owner)))
                    }
                }
            }
        }

        private fun entry(id: String, category: SettingCategory, store: String, name: String, value: TypedValue, missingPackage: String?) {
            fun skip(reason: SkipReason, detail: String? = null) {
                skips.add(ImportPlan.Skip(id, store, name, reason, detail))
            }
            val key = owners.of(store, name)?.takeIf { it.category == category }
            val stored = key?.takeIf { value.type == it.type.tag }?.let { storedForm(it, value.value) }
            val deployment = (key as? SettingKey<*>)?.deployment
            when {
                key == null -> skip(SkipReason.UNKNOWN_KEY)
                value.type != key.type.tag -> skip(SkipReason.TYPE_MISMATCH, "${value.type}, not ${key.type.tag}")
                stored == null -> skip(SkipReason.INVALID_VALUE)
                missingPackage != null -> skip(SkipReason.TARGET_MISSING, missingPackage)
                withDeployments && deployment != null && !environment.isDeployed(deployment, stored) ->
                    deployments.add(ImportPlan.Deployment(deployment, id, key.store, name, stored, effectOf(key), prerequisitesOf(deployment)))
                else -> puts.add(ImportPlan.Put(id, key.store, name, stored, effectOf(key)))
            }
        }

        private fun prerequisitesOf(kind: DeploymentKind): Set<Prerequisite> = when (kind) {
            DeploymentKind.DOWNLOAD_EDITOR_FONT -> if (environment.aceEditorPluginInstalled) emptySet() else setOf(Prerequisite.ACE_EDITOR_PLUGIN)
            DeploymentKind.CREATE_DIRECTORY -> emptySet()
        }
    }

    /** [raw] in the stored form [key] writes, or null when its type refuses the value (D9 `INVALID_VALUE`). */
    private fun <T : Any> storedForm(key: PrefKey<T>, raw: Any): Any? = key.decode(raw)?.let(key::encode)

    private fun effectOf(key: PrefKey<*>): SettingEffect = (key as? SettingKey<*>)?.effect ?: SettingEffect.NONE
}
