package org.autojs.autojs.core.pref

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
 * One domain of key declarations (`core/pref/keys/<Domain>Keys.kt`, P2 onwards): its keys and its registered names.
 * zh-CN: 一个领域的键声明 (P2 起的 `core/pref/keys/<Domain>Keys.kt`): 其中的键与登记名称.
 */
interface SettingsDomain {

    val keys: List<PrefKey<*>>

    val names: List<RegisteredName>
        get() = emptyList()
}

/**
 * The registry of every declared key (docs/settings/ROADMAP.md T4): it aggregates the domain objects, and
 * [describe] turns them into [SettingDescriptor] rows. Keys name their category where they are declared; the registry
 * only builds the reverse index ([byCategory]).
 *
 * P1.3 is the skeleton: no domain is declared yet ([DOMAINS] is empty until P2.1), so every `key_*` resource, literal
 * key and named store is still "pending migration" and is allowed by `tools/settings/classification.json` in the
 * guard tests (T5); the count goes down as P2 to P5 declare them. The deployment items of T18 are not implemented yet
 * either ([DEPLOYMENT_IMPLEMENTATIONS], P7.6).
 *
 * zh-CN: 全部键声明的注册表 (ROADMAP T4): 聚合各领域 `object`, [describe] 把它们转为 [SettingDescriptor] 行; 键在声明处写分类,
 * 注册表只做反向索引. P1.3 为骨架: 尚未声明任何领域 ([DOMAINS] 在 P2.1 之前为空), 因此所有 `key_*` 资源, 字面量键与命名存储仍是
 * "待迁移", 在守卫测试 (T5) 中由 `classification.json` 放行, 计数随 P2 到 P5 的声明下降. T18 的部署项也尚未实现 (P7.6).
 */
object SettingsRegistry {

    /** The domain objects in page order (appendix A.1); P2.1 adds the first ones. */
    @JvmField
    val DOMAINS: List<SettingsDomain> = emptyList()

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

    /** Descriptors of [DOMAINS]: keys first, then registered names, both in declaration order. */
    @JvmStatic
    fun describe(): List<SettingDescriptor> = describe(DOMAINS)

    /**
     * Descriptors of [domains] (also used by tests with their own domains).
     * zh-CN: [domains] 的描述行 (测试也以自己的领域调用).
     */
    @JvmStatic
    fun describe(domains: List<SettingsDomain>): List<SettingDescriptor> =
        domains.flatMap { it.keys }.map(::describe) + domains.flatMap { it.names }.map(::describe)

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
