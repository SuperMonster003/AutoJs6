package org.autojs.autojs.core.pref.store

/**
 * Identity of one settings store (docs/settings/ROADMAP.md T3): the default SharedPreferences ([DEFAULT], named
 * "default" in archives, appendix B), a named SharedPreferences file (the name passed to `getSharedPreferences`,
 * unchanged by R1) or a JSON file under `filesDir` ([Backend.JSON_FILE], the path relative to `filesDir`).
 * [excluded] marks the stores of appendix A.2 that are registered only so that guard (b) of T5 knows them (P3.5).
 *
 * zh-CN: 一个设置存储的标识 (ROADMAP T3): 默认 SharedPreferences ([DEFAULT], 归档中名为 "default"), 命名 SharedPreferences
 * 文件 (即传给 `getSharedPreferences` 的名称, R1 不变) 或 `filesDir` 下的 JSON 文件 (名称为相对 `filesDir` 的路径).
 * [excluded] 标记附录 A.2 的存储, 只为让 T5 守卫 (b) 认识它们而登记 (P3.5).
 */
data class StoreId @JvmOverloads constructor(
    val name: String,
    val backend: Backend = Backend.SHARED_PREFERENCES,
    val excluded: Boolean = false,
) {

    init {
        require(name.isNotBlank()) { "Store name is blank" }
    }

    enum class Backend {
        SHARED_PREFERENCES,
        JSON_FILE,
    }

    val isDefault: Boolean
        get() = this == DEFAULT

    override fun toString(): String = when (backend) {
        Backend.SHARED_PREFERENCES -> name
        Backend.JSON_FILE -> "json:$name"
    } + if (excluded) " (excluded)" else ""

    companion object {

        const val DEFAULT_NAME = "default"

        /** The default SharedPreferences (`PreferenceManager.getDefaultSharedPreferences`). */
        @JvmField
        val DEFAULT = StoreId(DEFAULT_NAME)
    }
}
