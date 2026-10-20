package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.store.StoreId
import org.autojs.autojs.util.StringUtils

/**
 * Resolves the `keyRes` of a key declaration to its key name (docs/settings/ROADMAP.md T1): `StringUtils.key` in the
 * app; JVM tests either declare keys with a literal name or install their own [resolver].
 *
 * zh-CN: 把键声明的 `keyRes` 解析为键名 (ROADMAP T1): 应用内为 `StringUtils.key`; JVM 测试以字面量名称声明键, 或安装自己的
 * [resolver].
 */
object SettingKeyNames {

    @JvmStatic
    @Volatile
    var resolver: (Int) -> String = { StringUtils.key(it) }

    @JvmStatic
    fun resolve(keyRes: Int): String = resolver(keyRes)
}

/**
 * Default value of a key (T1): a [Constant], a [Computed] value read on every call (for example from `R.bool` or
 * `R.integer`, like the current readers do, R2) or [None] (the reader gets null, like `getStringOrNull`). [text] is
 * what descriptors show (T4 `defaultText`); a [Computed] default shows its [Computed.source] so that describing it
 * needs no Android resources (R3).
 *
 * zh-CN: 键的默认值 (T1): 常量, 每次读取时计算的值 (例如读 `R.bool` / `R.integer`, 与现有读法一致, R2) 或 [None] (读取方得到
 * null, 同 `getStringOrNull`). [text] 为描述中显示的文本; 计算型默认值显示其来源, 描述时无需 Android 资源 (R3).
 */
sealed class SettingDefault<out T : Any> {

    abstract fun valueOrNull(): T?

    class Constant<T : Any>(val value: T) : SettingDefault<T>() {
        override fun valueOrNull(): T = value
        override fun toString() = "Constant($value)"
    }

    class Computed<T : Any>(val source: String, private val provider: () -> T) : SettingDefault<T>() {
        override fun valueOrNull(): T = provider()
        override fun toString() = "Computed($source)"
    }

    object None : SettingDefault<Nothing>() {
        override fun valueOrNull(): Nothing? = null
        override fun toString() = "None"
    }

    companion object {

        @JvmStatic
        fun <T : Any> of(value: T): SettingDefault<T> = Constant(value)

        @JvmStatic
        fun <T : Any> computed(source: String, provider: () -> T): SettingDefault<T> = Computed(source, provider)
    }
}

/**
 * Common part of [SettingKey], [StateKey] and [CacheKey] (T1 / T2): a key name in one store, its [type] and its
 * [default]. The name comes from [keyRes] through [SettingKeyNames] on first use, or from a literal name (keys built
 * from code such as `key_$_...`).
 *
 * zh-CN: [SettingKey], [StateKey] 与 [CacheKey] 的共同部分 (T1 / T2): 某个存储中的键名, 值类型与默认值. 键名在首次使用时
 * 由 [keyRes] 经 [SettingKeyNames] 解析, 或直接取字面量名称 (如代码拼出的 `key_$_...`).
 */
sealed class PrefKey<T : Any>(
    val keyRes: Int,
    literalName: String?,
    val store: StoreId,
    val type: SettingType<T>,
    val default: SettingDefault<T>,
) {

    init {
        require(literalName == null || literalName.isNotBlank()) { "Key name is blank" }
        require((keyRes != 0) != (literalName != null)) { "A key has either a keyRes or a literal name" }
    }

    abstract val kind: SettingKind

    /** Category for export and import; null for a [CacheKey], which is never exported. */
    abstract val category: SettingCategory?

    val name: String by lazy { literalName ?: SettingKeyNames.resolve(keyRes) }

    /** The default value, or null for [SettingDefault.None]. */
    fun defaultOrNull(): T? = default.valueOrNull()

    /** Descriptor text of the default (T4): the constant rendered by [type], the source of a computed one, or null. */
    val defaultText: String?
        get() = when (val d = default) {
            is SettingDefault.Constant -> type.describe(d.value)
            is SettingDefault.Computed -> d.source
            SettingDefault.None -> null
        }

    /** A stored value read back as [T]; null when absent, of another type or invalid (see [SettingType]). */
    fun decode(raw: Any?): T? = raw?.let(type::fromStored)

    /** The stored form of [value] (one of the SharedPreferences types). */
    fun encode(value: T): Any = type.toStored(value)

    override fun toString(): String = "${javaClass.simpleName}($name, ${type.tag}, $store)"
}

/**
 * Declaration of a user setting (docs/settings/ROADMAP.md T1): its [category] for the wizards (never `other`, never a
 * data category), its [effect] after a change (D10), [sensitive] for credentials (D2, only in the `credentials`
 * category) and an optional [deployment] item (D20 / T18).
 *
 * The subclasses are the value types: [Bool], [Int], [Long], [Float], [Str], [StrSet], [Enum] and [Json]. Domain
 * objects (`core/pref/keys/<Domain>Keys.kt`, P2 onwards) declare them; P1.1 only defines the model and no caller uses
 * it yet.
 *
 * zh-CN: 用户设置的声明 (ROADMAP T1): 向导中的分类 (不能是 `other` 或数据分类), 变化后的生效方式 (D10), 凭据标记 (D2, 只在
 * `credentials` 分类) 与可选的部署项 (D20 / T18). 子类即值类型. 领域 `object` (P2 起) 声明这些键; P1.1 只定义模型, 尚无调用方.
 */
sealed class SettingKey<T : Any>(
    keyRes: kotlin.Int,
    literalName: String?,
    store: StoreId,
    type: SettingType<T>,
    default: SettingDefault<T>,
    override val category: SettingCategory,
    val effect: SettingEffect,
    val sensitive: Boolean,
    val deployment: DeploymentKind?,
) : PrefKey<T>(keyRes, literalName, store, type, default) {

    init {
        require(category.kind == SettingCategory.Kind.SETTINGS) { "Data category \"$category\" holds no keys" }
        require(category != SettingCategory.OTHER) { "Category \"other\" is only for StateKey (T2)" }
        require(sensitive == category.sensitive) {
            "Sensitive keys belong to \"credentials\" and every key there is sensitive (D2, T5 c), got $category / sensitive = $sensitive"
        }
    }

    override val kind: SettingKind
        get() = SettingKind.SETTING

    class Bool @JvmOverloads constructor(
        keyRes: kotlin.Int = 0,
        category: SettingCategory,
        default: SettingDefault<Boolean>,
        effect: SettingEffect = SettingEffect.NONE,
        sensitive: Boolean = false,
        deployment: DeploymentKind? = null,
        store: StoreId = StoreId.DEFAULT,
        name: String? = null,
    ) : SettingKey<Boolean>(keyRes, name, store, SettingType.Bool, default, category, effect, sensitive, deployment)

    class Int @JvmOverloads constructor(
        keyRes: kotlin.Int = 0,
        category: SettingCategory,
        default: SettingDefault<kotlin.Int>,
        effect: SettingEffect = SettingEffect.NONE,
        sensitive: Boolean = false,
        deployment: DeploymentKind? = null,
        store: StoreId = StoreId.DEFAULT,
        name: String? = null,
    ) : SettingKey<kotlin.Int>(keyRes, name, store, SettingType.Int, default, category, effect, sensitive, deployment)

    class Long @JvmOverloads constructor(
        keyRes: kotlin.Int = 0,
        category: SettingCategory,
        default: SettingDefault<kotlin.Long>,
        effect: SettingEffect = SettingEffect.NONE,
        sensitive: Boolean = false,
        deployment: DeploymentKind? = null,
        store: StoreId = StoreId.DEFAULT,
        name: String? = null,
    ) : SettingKey<kotlin.Long>(keyRes, name, store, SettingType.Long, default, category, effect, sensitive, deployment)

    class Float @JvmOverloads constructor(
        keyRes: kotlin.Int = 0,
        category: SettingCategory,
        default: SettingDefault<kotlin.Float>,
        effect: SettingEffect = SettingEffect.NONE,
        sensitive: Boolean = false,
        deployment: DeploymentKind? = null,
        store: StoreId = StoreId.DEFAULT,
        name: String? = null,
    ) : SettingKey<kotlin.Float>(keyRes, name, store, SettingType.Float, default, category, effect, sensitive, deployment)

    class Str @JvmOverloads constructor(
        keyRes: kotlin.Int = 0,
        category: SettingCategory,
        default: SettingDefault<String>,
        effect: SettingEffect = SettingEffect.NONE,
        sensitive: Boolean = false,
        deployment: DeploymentKind? = null,
        store: StoreId = StoreId.DEFAULT,
        name: String? = null,
    ) : SettingKey<String>(keyRes, name, store, SettingType.Str, default, category, effect, sensitive, deployment)

    class StrSet @JvmOverloads constructor(
        keyRes: kotlin.Int = 0,
        category: SettingCategory,
        default: SettingDefault<Set<String>>,
        effect: SettingEffect = SettingEffect.NONE,
        sensitive: Boolean = false,
        deployment: DeploymentKind? = null,
        store: StoreId = StoreId.DEFAULT,
        name: String? = null,
    ) : SettingKey<Set<String>>(keyRes, name, store, SettingType.StrSet, default, category, effect, sensitive, deployment)

    /**
     * A value out of [candidates] stored as its key string (T1). Candidates and defaults may need Android resources,
     * so the declaration does not check that the default is a candidate; [isDefaultCandidate] is for the registry
     * tests and the app.
     * zh-CN: 取自候选项, 以键字符串存储的值 (T1). 候选项与默认值可能需要 Android 资源, 因此声明时不检查默认值是否为候选项;
     * [isDefaultCandidate] 供注册表测试与应用使用.
     */
    class Enum<E : Any> @JvmOverloads constructor(
        keyRes: kotlin.Int = 0,
        category: SettingCategory,
        val enumType: SettingType.Enum<E>,
        default: SettingDefault<E>,
        effect: SettingEffect = SettingEffect.NONE,
        sensitive: Boolean = false,
        deployment: DeploymentKind? = null,
        store: StoreId = StoreId.DEFAULT,
        name: String? = null,
    ) : SettingKey<E>(keyRes, name, store, enumType, default, category, effect, sensitive, deployment) {

        val candidates: List<E>
            get() = enumType.candidates

        /** True when the default is a candidate (computed defaults are evaluated). */
        fun isDefaultCandidate(): Boolean = defaultOrNull()?.let(enumType::isCandidate) ?: true
    }

    class Json<V : Any> @JvmOverloads constructor(
        keyRes: kotlin.Int = 0,
        category: SettingCategory,
        val jsonType: SettingType.Json<V>,
        default: SettingDefault<V>,
        effect: SettingEffect = SettingEffect.NONE,
        sensitive: Boolean = false,
        deployment: DeploymentKind? = null,
        store: StoreId = StoreId.DEFAULT,
        name: String? = null,
    ) : SettingKey<V>(keyRes, name, store, jsonType, default, category, effect, sensitive, deployment)
}
