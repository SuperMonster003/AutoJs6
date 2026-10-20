package org.autojs.autojs.core.pref

/**
 * One descriptor row with exactly the T4 fields of docs/settings/ROADMAP.md
 * (`SettingDescriptor(name, type, store, category, defaultText, effect, kind, deployment)`), as primitive values only
 * (R3).
 *
 * Tests build rows of this type through [SettingDescriptorFixtures]; [toDescriptor] and [SettingDescriptor.toSpec]
 * map it to and from the main `SettingDescriptor` of P1.3, so the construction code of tests does not change.
 *
 * zh-CN: 只含 ROADMAP T4 字段的描述行, 全部为原始类型 (R3). 测试经 [SettingDescriptorFixtures] 构造本类型; [toDescriptor] 与
 * [SettingDescriptor.toSpec] 在它与 P1.3 的主代码 `SettingDescriptor` 之间转换, 测试的构造代码不必改动.
 */
data class SettingDescriptorSpec(
    val name: String,
    val type: String,
    val store: String,
    val category: String?,
    val defaultText: String?,
    val effect: String,
    val kind: String,
    val deployment: String? = null,
)

/**
 * Builders and checks for [SettingDescriptorSpec] rows used by the registry, guard, import planner and deployment
 * planner tests (P0.5). The allowed values come from the roadmap: type tags T6, effects D10, kinds T2 (a `StateKey`
 * always sits in `other`), deployment kinds T18 / P2.2 / P2.3 (only a Setting carries one).
 *
 * zh-CN: 供注册表, 守卫, 导入计划与部署计划测试使用的描述行构造与校验 (P0.5). 取值范围来自路线图: 类型标签 T6, 生效方式
 * D10, kind T2 (`StateKey` 固定归 `other`), 部署项种类 T18 / P2.2 / P2.3 (只有 Setting 可以带).
 */
object SettingDescriptorFixtures {

    val TYPES = listOf("bool", "int", "long", "float", "string", "stringSet", "json")
    val EFFECTS = listOf("NONE", "APPLY", "RESTART")
    val KINDS = listOf("Setting", "State", "Cache", "ValueConstant", "Excluded")
    val DEPLOYMENTS = listOf("CREATE_DIRECTORY", "DOWNLOAD_EDITOR_FONT")

    const val DEFAULT_STORE = InMemorySettingsStore.DEFAULT_NAME
    const val OTHER_CATEGORY = "other"

    fun setting(
        name: String,
        type: String,
        category: String,
        defaultText: String? = null,
        effect: String = "NONE",
        store: String = DEFAULT_STORE,
        deployment: String? = null,
    ) = spec(name, type, store, category, defaultText, effect, "Setting", deployment)

    fun state(
        name: String,
        type: String,
        defaultText: String? = null,
        store: String = DEFAULT_STORE,
    ) = spec(name, type, store, OTHER_CATEGORY, defaultText, "NONE", "State")

    fun cache(name: String, type: String, store: String = DEFAULT_STORE) =
        spec(name, type, store, null, null, "NONE", "Cache")

    fun valueConstant(name: String) = spec(name, "string", DEFAULT_STORE, null, null, "NONE", "ValueConstant")

    fun excluded(name: String, store: String = DEFAULT_STORE) = spec(name, "string", store, null, null, "NONE", "Excluded")

    /**
     * Builds a row after checking it against the roadmap rules; throws [IllegalArgumentException] otherwise.
     * zh-CN: 按路线图规则校验后构造描述行, 不符合时抛 [IllegalArgumentException].
     */
    fun spec(
        name: String,
        type: String,
        store: String,
        category: String?,
        defaultText: String?,
        effect: String,
        kind: String,
        deployment: String? = null,
    ): SettingDescriptorSpec {
        require(name.isNotBlank()) { "name is blank" }
        require(store.isNotBlank()) { "$name: store is blank" }
        require(type in TYPES) { "$name: unknown type \"$type\"" }
        require(effect in EFFECTS) { "$name: unknown effect \"$effect\"" }
        require(kind in KINDS) { "$name: unknown kind \"$kind\"" }
        require(deployment == null || deployment in DEPLOYMENTS) { "$name: unknown deployment \"$deployment\"" }
        when (kind) {
            "Setting" -> require(!category.isNullOrBlank() && category != OTHER_CATEGORY) {
                "$name: a Setting needs a category other than \"$OTHER_CATEGORY\""
            }
            "State" -> require(category == OTHER_CATEGORY && effect == "NONE" && deployment == null) {
                "$name: a State belongs to \"$OTHER_CATEGORY\" with effect NONE and no deployment"
            }
            else -> require(category == null && effect == "NONE" && deployment == null) {
                "$name: a $kind has no category, no deployment and effect NONE"
            }
        }
        return SettingDescriptorSpec(name, type, store, category, defaultText, effect, kind, deployment)
    }

    /** Rows grouped by category in first-seen order; rows without a category are left out. */
    fun byCategory(rows: Iterable<SettingDescriptorSpec>): Map<String, List<SettingDescriptorSpec>> =
        rows.filter { it.category != null }.groupByTo(LinkedHashMap()) { it.category!! }

    /** Names that appear in more than one row of the same store (T5 c: every key exactly once). */
    fun duplicateNames(rows: Iterable<SettingDescriptorSpec>): Set<String> =
        rows.groupingBy { it.store to it.name }.eachCount().filterValues { it > 1 }.keys.mapTo(LinkedHashSet()) { it.second }

    /** Deployment kinds used by the rows (T5 e checks each one against the registered deployment items). */
    fun deploymentKinds(rows: Iterable<SettingDescriptorSpec>): Set<String> =
        rows.mapNotNullTo(LinkedHashSet()) { it.deployment }

    /**
     * A store holding the given rows' defaults that can be parsed back from [SettingDescriptorSpec.defaultText]
     * (bool / int / long / float / string); rows of other types or without a default are skipped.
     * zh-CN: 按描述行的 defaultText 写入默认值的测试存储 (只处理 bool / int / long / float / string).
     */
    fun storeWithDefaults(rows: Iterable<SettingDescriptorSpec>, name: String = DEFAULT_STORE): InMemorySettingsStore {
        val store = InMemorySettingsStore(name)
        store.edit {
            rows.filter { it.store == name && it.defaultText != null }.forEach { row ->
                val text = row.defaultText!!
                when (row.type) {
                    "bool" -> putBoolean(row.name, text.toBooleanStrict())
                    "int" -> putInt(row.name, text.toInt())
                    "long" -> putLong(row.name, text.toLong())
                    "float" -> putFloat(row.name, text.toFloat())
                    "string" -> putString(row.name, text)
                }
            }
        }
        return store
    }
}

/** Maps a fixture row to the main descriptor type of P1.3. */
fun SettingDescriptorSpec.toDescriptor() = SettingDescriptor(
    name = name,
    type = type,
    store = store,
    category = category,
    defaultText = defaultText,
    effect = SettingEffect.valueOf(effect),
    kind = SettingKind.ofLabel(kind),
    deployment = deployment?.let(DeploymentKind::valueOf),
)

/** Maps a main descriptor back to a fixture row, checking it against the roadmap rules. */
fun SettingDescriptor.toSpec() = SettingDescriptorFixtures.spec(name, type, store, category, defaultText, effect.name, kind.label, deployment?.name)
