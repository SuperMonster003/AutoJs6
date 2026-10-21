package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId
import org.autojs.autojs.core.pref.store.get
import org.autojs.autojs.core.pref.store.getOrNull
import org.autojs.autojs.core.pref.store.put
import org.autojs.autojs.core.pref.store.storedOrNull
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

    // Reads and writes through the store of the key (P2): the readers that move to declared keys call these, with
    // the semantics of the typed getters of SharedPreferences (a value of another type throws ClassCastException).
    // zh-CN: 经键所在存储的读写 (P2): 迁移到键声明的读取方调用这些成员, 语义同 SharedPreferences 的类型化读取 (其他类型的值抛
    // ClassCastException).

    /** The store of this key ([SettingsStores]). */
    val settingsStore: SettingsStore
        get() = SettingsStores[store]

    /** The stored value, else the default; throws [IllegalStateException] for a key with neither ([SettingDefault.None]). */
    fun get(): T = settingsStore[this]

    /** The stored value, else the default (null only for [SettingDefault.None]). */
    fun getOrNull(): T? = settingsStore.getOrNull(this)

    /** The stored value, or null when absent or invalid (see [SettingType]). */
    fun storedOrNull(): T? = settingsStore.storedOrNull(this)

    /** True when the store holds a value for [name] (`Pref.containsKey`). */
    fun isStored(): Boolean = settingsStore.contains(name)

    /**
     * Writes [value] (null removes the key); [commit] is `commit()` like `Pref.putXxxSync`, otherwise `apply()`.
     * zh-CN: 写入 [value] (null 即删除); [commit] 为 `commit()` (同 `Pref.putXxxSync`), 否则为 `apply()`.
     */
    @JvmOverloads
    fun set(value: T?, commit: Boolean = false) {
        settingsStore.put(this, value, commit)
    }

    @JvmOverloads
    fun remove(commit: Boolean = false) {
        settingsStore.remove(name, commit)
    }

    /**
     * The stored text of a [SettingType.Json] key as it is, or null when absent; a value of another type throws
     * [ClassCastException] like `getString`. For readers that keep parsing the text themselves (with org.json, so that
     * lenient inputs and the written text stay as they were, R1 / R2, P3).
     * zh-CN: 原样返回 [SettingType.Json] 键存储的文本, 不存在时为 null; 其他类型的存储值与 `getString` 一样抛 [ClassCastException].
     * 供仍自行解析文本的读取方使用 (仍用 org.json, 使宽松输入与写出的文本保持原样, R1 / R2, P3).
     */
    fun storedText(): String? {
        check(type is SettingType.Json<*>) { "$name is not a Json key" }
        return settingsStore.getString(name, null)
    }

    /**
     * Writes [text] as the stored text of a [SettingType.Json] key without encoding it (null removes the key); for the
     * writers of [storedText]. [commit] as in [set].
     * zh-CN: 不经编码直接写入 [SettingType.Json] 键的文本 (null 即删除), 供 [storedText] 的写入方使用; [commit] 同 [set].
     */
    @JvmOverloads
    fun setStoredText(text: String?, commit: Boolean = false) {
        check(type is SettingType.Json<*>) { "$name is not a Json key" }
        settingsStore.put(name, text, commit)
    }

    override fun toString(): String = "${javaClass.simpleName}($name, ${type.tag}, $store)"
}

/**
 * Declaration of a user setting (docs/settings/ROADMAP.md T1): its [category] for the wizards (never `other`, never a
 * data category), its [effect] after a change (D10), [sensitive] for credentials (D2, only in the `credentials`
 * category) and an optional [deployment] item (D20 / T18).
 *
 * The subclasses are the value types: [Bool], [Int], [Long], [Float], [Str], [StrSet], [Enum] and [Json]. Domain
 * objects (`core/pref/keys/<Domain>Keys.kt`, P2 onwards) declare them and their callers read and write through them.
 *
 * zh-CN: 用户设置的声明 (ROADMAP T1): 向导中的分类 (不能是 `other` 或数据分类), 变化后的生效方式 (D10), 凭据标记 (D2, 只在
 * `credentials` 分类) 与可选的部署项 (D20 / T18). 子类即值类型. 领域 `object` (P2 起) 声明这些键, 调用方经它们读写.
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

        /**
         * The stored key string as it is, also one that is not a candidate, or null when absent; a stored value of
         * another type throws [ClassCastException] like `getString`. For readers that compared the raw string
         * (`Pref.getStringOrNull(key) == ...`), so that a value out of the candidates keeps matching none (R2).
         * zh-CN: 原样返回存储的键字符串 (不在候选项中也返回), 不存在时为 null; 其他类型的存储值与 `getString` 一样抛
         * [ClassCastException]. 供原先比较原始字符串的读取方使用, 使候选项之外的值仍然一个都不匹配 (R2).
         */
        fun storedKey(): String? = settingsStore.getString(name, null)

        /**
         * [storedKey], else the key of the default: what `Pref.getString(key, defaultKey)` returned (R2).
         * zh-CN: [storedKey], 不存在时为默认值的键: 即 `Pref.getString(key, defaultKey)` 的返回值 (R2).
         */
        fun storedKeyOrDefault(): String = storedKey() ?: defaultOrNull()?.let(enumType::keyOf)
            ?: throw IllegalStateException("$name has neither a stored value nor a default")

        /**
         * Writes [key] as it is (null removes it), without the candidate check of [set]: for writers that copy a key
         * string read from elsewhere, as `Pref.putString` did (R2).
         * zh-CN: 原样写入 [key] (null 即删除), 不做 [set] 的候选项检查: 供复制他处读来的键字符串的写入方使用, 同 `Pref.putString` (R2).
         */
        @JvmOverloads
        fun setStoredKey(key: String?, commit: Boolean = false) {
            settingsStore.put(name, key, commit)
        }
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
