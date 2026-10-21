package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.keys.A11yKeys
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.ConnectionKeys
import org.autojs.autojs.core.pref.keys.ConsoleKeys
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.core.pref.keys.EditorKeyboardKeys
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.core.pref.keys.ExplorerKeys
import org.autojs.autojs.core.pref.keys.ExtensibilityKeys
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.core.pref.keys.HistoryKeys
import org.autojs.autojs.core.pref.keys.LayoutInspectorKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.autojs.autojs.core.pref.keys.RecordKeys
import org.autojs.autojs.core.pref.keys.RunningKeys
import org.autojs.autojs.core.pref.keys.TimedTaskKeys
import org.autojs.autojs.core.pref.keys.UpdatesKeys
import org.autojs.autojs.core.pref.store.StoreId

/**
 * A registered name that holds no value of its own (docs/settings/ROADMAP.md T2): a candidate value such as
 * `key_night_mode_follow_system` ([SettingKind.VALUE_CONSTANT]) or a name that is not a preference key, such as a
 * Bundle argument name or an action item ([SettingKind.EXCLUDED]). Registered only so that the guard tests (T5 a)
 * know every `key_*` resource.
 *
 * zh-CN: 不持有值的登记名称 (ROADMAP T2): 设置值常量 (候选值) 或不是偏好键的名称 (Bundle 参数名, 动作条目). 登记只为让守卫
 * 测试 (T5 a) 认识每个 `key_*` 资源.
 */
class RegisteredName @JvmOverloads constructor(
    val keyRes: Int = 0,
    val kind: SettingKind,
    literalName: String? = null,
    val store: StoreId = StoreId.DEFAULT,
) {

    init {
        require(kind == SettingKind.VALUE_CONSTANT || kind == SettingKind.EXCLUDED) { "A RegisteredName is a ValueConstant or Excluded, not $kind" }
        require((keyRes != 0) != (literalName != null)) { "A registered name has either a keyRes or a literal name" }
    }

    val name: String by lazy { literalName ?: SettingKeyNames.resolve(keyRes) }

    override fun toString(): String = "RegisteredName($name, ${kind.label})"
}

/**
 * One domain of key declarations (`core/pref/keys/<Domain>Keys.kt`, P2 onwards): its keys, its key families (keys
 * named by a prefix and a run-time part, P3) and its registered names.
 * zh-CN: 一个领域的键声明 (P2 起的 `core/pref/keys/<Domain>Keys.kt`): 其中的键, 键族 (以前缀加运行时部分命名的键, P3) 与登记名称.
 */
interface SettingsDomain {

    val keys: List<PrefKey<*>>

    val families: List<KeyFamily<*, *>>
        get() = emptyList()

    val names: List<RegisteredName>
        get() = emptyList()
}

/**
 * The registry of every declared key (docs/settings/ROADMAP.md T4): it aggregates the domain objects, and
 * [describe] turns them into [SettingDescriptor] rows. Keys name their category where they are declared; the registry
 * only builds the reverse index ([byCategory]).
 *
 * [DOMAINS] grows from P2.1 on; every `key_*` resource, literal key and named store not declared yet is "pending
 * migration" and is allowed by `tools/settings/classification.json` in the guard tests (T5), and the count goes down
 * as P2 to P5 declare them. The deployment items of T18 are not implemented yet ([DEPLOYMENT_IMPLEMENTATIONS], P7.6).
 *
 * zh-CN: 全部键声明的注册表 (ROADMAP T4): 聚合各领域 `object`, [describe] 把它们转为 [SettingDescriptor] 行; 键在声明处写分类,
 * 注册表只做反向索引. [DOMAINS] 自 P2.1 起逐步填充; 尚未声明的 `key_*` 资源, 字面量键与命名存储为 "待迁移", 在守卫测试 (T5)
 * 中由 `classification.json` 放行, 计数随 P2 到 P5 的声明下降. T18 的部署项尚未实现 (P7.6).
 */
object SettingsRegistry {

    /** The domain objects in page order (appendix A.1), filled by P2 to P5. */
    @JvmField
    val DOMAINS: List<SettingsDomain> = listOf(
        AppearanceKeys,
        GeneralKeys,
        ExplorerKeys,
        HistoryKeys,
        EditorKeys,
        EditorKeyboardKeys,
        TimedTaskKeys,
        A11yKeys,
        RunningKeys,
        RecordKeys,
        ExtensibilityKeys,
        UpdatesKeys,
        ConsoleKeys,
        ConnectionKeys,
        LayoutInspectorKeys,
        DeveloperKeys,
        PluginCenterKeys,
        CredentialKeys,
        MiscStateKeys,
    )

    /** Deployment kinds that have a registered implementation (T5 e); filled by P7.6. */
    @JvmField
    val DEPLOYMENT_IMPLEMENTATIONS: Set<DeploymentKind> = emptySet()

    @JvmStatic
    val categories: List<SettingCategory>
        get() = SettingCategory.ALL

    /** Every declared key of [DOMAINS], in declaration order. */
    @JvmStatic
    val keys: List<PrefKey<*>>
        get() = DOMAINS.flatMap { it.keys }

    /** Every declared key family of [DOMAINS], in declaration order. */
    @JvmStatic
    val families: List<KeyFamily<*, *>>
        get() = DOMAINS.flatMap { it.families }

    /**
     * Every declared named store ([NamedStores], P3): the stores of the declared keys and the permanent exclusions of
     * appendix A.2; reading it registers them in `SettingsStores` (guard (b) of T5).
     * zh-CN: 全部已声明的命名存储 (P3): 键声明所在的存储与附录 A.2 的永久排除项; 读取时即登记到 `SettingsStores` (T5 守卫 (b)).
     */
    @JvmStatic
    val stores: List<StoreId>
        get() = NamedStores.ALL

    /**
     * The declared key named [name] in [store], or null.
     * zh-CN: [store] 中名为 [name] 的键声明, 不存在时为 null.
     */
    @JvmStatic
    @JvmOverloads
    fun find(name: String, store: StoreId = StoreId.DEFAULT): PrefKey<*>? = keys.firstOrNull { it.name == name && it.store == store }

    /**
     * The declared key family of [store] that [name] is a member of, or null.
     * zh-CN: [store] 中 [name] 所属的键族, 不存在时为 null.
     */
    @JvmStatic
    @JvmOverloads
    fun findFamily(name: String, store: StoreId = StoreId.DEFAULT): KeyFamily<*, *>? =
        families.firstOrNull { it.template.store == store && it.matches(name) }

    /** Descriptors of [DOMAINS]: keys first, then key families, then registered names, each in declaration order. */
    @JvmStatic
    fun describe(): List<SettingDescriptor> = describe(DOMAINS)

    /**
     * Descriptors of [domains] (also used by tests with their own domains).
     * zh-CN: [domains] 的描述行 (测试也以自己的领域调用).
     */
    @JvmStatic
    fun describe(domains: List<SettingsDomain>): List<SettingDescriptor> =
        domains.flatMap { it.keys }.map(::describe) +
                domains.flatMap { it.families }.map(::describe) +
                domains.flatMap { it.names }.map(::describe)

    @JvmStatic
    fun describe(key: PrefKey<*>): SettingDescriptor = SettingDescriptor(
        name = key.name,
        type = key.type.tag,
        store = key.store.name,
        category = key.category?.id,
        defaultText = key.defaultText,
        effect = (key as? SettingKey<*>)?.effect ?: SettingEffect.NONE,
        kind = key.kind,
        deployment = (key as? SettingKey<*>)?.deployment,
    )

    /** The row of [family]: the row of its template, named by the prefix and marked as a family. */
    @JvmStatic
    fun describe(family: KeyFamily<*, *>): SettingDescriptor = describe(family.template).copy(family = true)

    @JvmStatic
    fun describe(name: RegisteredName): SettingDescriptor = SettingDescriptor(
        name = name.name,
        type = SettingType.Str.tag,
        store = name.store.name,
        category = null,
        defaultText = null,
        effect = SettingEffect.NONE,
        kind = name.kind,
    )

    /**
     * Rows grouped by category in the order of [SettingCategory.ALL]; rows without a category are left out and every
     * category is present, empty or not.
     * zh-CN: 按 [SettingCategory.ALL] 顺序分组的描述行; 无分类的行不计入, 每个分类都出现 (可为空).
     */
    @JvmStatic
    @JvmOverloads
    fun byCategory(rows: List<SettingDescriptor> = describe()): Map<SettingCategory, List<SettingDescriptor>> {
        val grouped = rows.filter { it.category != null }.groupBy { it.category!! }
        val unknown = grouped.keys - SettingCategory.ALL.map { it.id }.toSet()
        require(unknown.isEmpty()) { "Unknown category ids: $unknown" }
        return SettingCategory.ALL.associateWithTo(LinkedHashMap()) { grouped[it.id].orEmpty() }
    }

    /** Deployment kinds used by [rows] (T5 e). */
    @JvmStatic
    @JvmOverloads
    fun deploymentKinds(rows: List<SettingDescriptor> = describe()): Set<DeploymentKind> = rows.mapNotNullTo(LinkedHashSet()) { it.deployment }
}
