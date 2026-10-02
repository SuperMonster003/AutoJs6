package org.autojs.autojs.core.accessibility.inspector

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.BooleanProperty
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.IntRangeSpec
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.Tri

/**
 * The user's filter profiles plus the built-in default (roadmap P3.1, D6): the default is first,
 * immutable, cannot be renamed or deleted; user profiles are created by [saveAs], replaced by
 * [update], renamed and deleted; [select] falls back to the default for an unknown id (also after a
 * deletion). Names must be non-blank and unique ([validateName]). [toJson] / [fromJson] persist the
 * user profiles only; the decoder tolerates garbage, unknown fields and missing fields (defaults).
 *
 * Free of Android types (D13); the UI owns the preferences for the JSON and the selected id.
 *
 * zh-CN: 用户的筛选配置加内置默认配置 (Roadmap P3.1, D6): 默认配置排在首位, 不可修改, 不可改名或删除; 用户配置由 [saveAs] 建立,
 * [update] 替换, 可改名与删除; [select] 对未知 id (包括刚被删除的) 回退默认. 名称须非空且不重复 ([validateName]).
 * [toJson] / [fromJson] 只持久化用户配置; 解码容忍垃圾内容, 未知字段与缺失字段 (取默认值). 不含 Android 类型 (D13); JSON 与已选 id
 * 的偏好项由界面层持有.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class FilterProfileCatalog private constructor(
    private val userProfiles: MutableList<FilterProfile>,
    selectedId: String?,
    private val newId: () -> String,
) {

    enum class NameError { BLANK, DUPLICATE }

    /** Default first, then the user profiles in creation order. zh-CN: 默认配置在前, 其后为按建立顺序排列的用户配置. */
    val profiles: List<FilterProfile> get() = listOf(FilterProfile.DEFAULT) + userProfiles

    var selectedId: String = FilterProfile.DEFAULT_ID
        private set

    val selected: FilterProfile get() = get(selectedId) ?: FilterProfile.DEFAULT

    init {
        select(selectedId)
    }

    operator fun get(id: String?): FilterProfile? = when (id) {
        FilterProfile.DEFAULT_ID -> FilterProfile.DEFAULT
        else -> userProfiles.firstOrNull { it.id == id }
    }

    /** Selects [id], or the default when it is unknown; returns the selection. zh-CN: 选中 [id], 未知时选中默认; 返回选中的配置. */
    fun select(id: String?): FilterProfile {
        val profile = get(id) ?: FilterProfile.DEFAULT
        selectedId = profile.id
        return profile
    }

    fun validateName(name: String, exceptId: String? = null): NameError? = when {
        name.isBlank() -> NameError.BLANK
        userProfiles.any { it.id != exceptId && it.name.trim() == name.trim() } -> NameError.DUPLICATE
        else -> null
    }

    /** Whether [profile] can be written back in place (a known user profile, never the default). zh-CN: [profile] 能否原地写回 (已知的用户配置, 默认配置不行). */
    fun canUpdate(profile: FilterProfile): Boolean = !profile.isDefault && userProfiles.any { it.id == profile.id }

    /** Replaces the stored profile with the same id; false for the default or an unknown id. zh-CN: 替换同 id 的已存配置; 默认或未知 id 返回 false. */
    fun update(profile: FilterProfile): Boolean {
        val index = userProfiles.indexOfFirst { it.id == profile.id }
        if (profile.isDefault || index < 0) return false
        userProfiles[index] = profile
        return true
    }

    /**
     * Stores the conditions of [profile] as a new user profile named [name] (which must pass
     * [validateName]) and selects it; this is also how "save" on the default profile ends (D6).
     * zh-CN: 把 [profile] 的条件存为名为 [name] 的新用户配置 (须通过 [validateName]) 并选中它; 对默认配置 "保存" 也走这里 (D6).
     */
    fun saveAs(profile: FilterProfile, name: String): FilterProfile {
        validateName(name)?.let { throw IllegalArgumentException("Invalid profile name ($it): \"$name\"") }
        var id = newId()
        while (get(id) != null) id = newId()
        val created = profile.copy(id = id, name = name.trim())
        userProfiles += created
        selectedId = created.id
        return created
    }

    fun rename(id: String, name: String): Boolean {
        val index = userProfiles.indexOfFirst { it.id == id }
        if (id == FilterProfile.DEFAULT_ID || index < 0 || validateName(name, exceptId = id) != null) return false
        userProfiles[index] = userProfiles[index].copy(name = name.trim())
        return true
    }

    /** Deletes a user profile; the selection falls back to the default when it was selected. zh-CN: 删除用户配置; 若它正被选中则回退默认. */
    fun delete(id: String): Boolean {
        if (id == FilterProfile.DEFAULT_ID || !userProfiles.removeAll { it.id == id }) return false
        if (selectedId == id) selectedId = FilterProfile.DEFAULT_ID
        return true
    }

    fun toJson(): String = GSON.toJson(userProfiles.map(::ProfileJson))

    /** The persisted shape; every field nullable so partial or old JSON decodes. zh-CN: 持久化形态; 字段全部可空以便解码残缺或旧版 JSON. */
    private class ProfileJson(
        val id: String?,
        val name: String?,
        val booleans: Map<String, String>?,
        val requireId: Boolean?,
        val requireText: Boolean?,
        val requireDesc: Boolean?,
        val classPresets: List<String>?,
        val depth: RangeJson?,
        val childCount: RangeJson?,
        val indexInParent: RangeJson?,
        val minWidth: Int?,
        val minHeight: Int?,
        val onScreenOnly: Boolean?,
        val packages: List<String>?,
        val actions: List<String>?,
    ) {
        constructor(p: FilterProfile) : this(
            p.id, p.name,
            p.booleans.filterValues { it != Tri.ANY }.entries.associate { (k, v) -> k.name to v.name },
            p.requireId, p.requireText, p.requireDesc,
            p.classPresets.map { it.name },
            RangeJson.of(p.depth), RangeJson.of(p.childCount), RangeJson.of(p.indexInParent),
            p.minWidth, p.minHeight, p.onScreenOnly,
            p.packages.toList(), p.actions.toList(),
        )

        fun toProfile(): FilterProfile? {
            val id = id?.takeIf { it.isNotBlank() && it != FilterProfile.DEFAULT_ID } ?: return null
            return FilterProfile(
                id = id,
                name = name?.trim().orEmpty(),
                booleans = booleans.orEmpty().mapNotNull { (k, v) ->
                    val property = enumOrNull<BooleanProperty>(k) ?: return@mapNotNull null
                    val tri = enumOrNull<Tri>(v) ?: return@mapNotNull null
                    if (tri == Tri.ANY) null else property to tri
                }.toMap(),
                requireId = requireId ?: false,
                requireText = requireText ?: false,
                requireDesc = requireDesc ?: false,
                classPresets = classPresets.orEmpty().mapNotNull { enumOrNull<ClassPreset>(it) }.toSet(),
                depth = depth?.toSpec() ?: IntRangeSpec.ANY,
                childCount = childCount?.toSpec() ?: IntRangeSpec.ANY,
                indexInParent = indexInParent?.toSpec() ?: IntRangeSpec.ANY,
                minWidth = minWidth?.takeIf { it > 0 },
                minHeight = minHeight?.takeIf { it > 0 },
                onScreenOnly = onScreenOnly ?: false,
                packages = packages.orEmpty().filter { it.isNotBlank() }.toSet(),
                actions = actions.orEmpty().filter { it.isNotBlank() }.toSet(),
            )
        }
    }

    private class RangeJson(val min: Int?, val max: Int?) {

        fun toSpec() = IntRangeSpec(min, max)

        companion object {
            fun of(spec: IntRangeSpec): RangeJson? = if (spec.isAny) null else RangeJson(spec.min, spec.max)
        }
    }

    companion object {

        private val GSON = Gson()

        private val PROFILE_LIST = object : TypeToken<List<ProfileJson?>>() {}.type

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }

        /** Default ids are time-based; tests pass a deterministic [newId]. zh-CN: 默认 id 基于时间; 测试传入确定性的 [newId]. */
        fun empty(selectedId: String? = null, newId: () -> String = ::timeId): FilterProfileCatalog = FilterProfileCatalog(ArrayList(), selectedId, newId)

        /**
         * Decodes [toJson] output; unreadable JSON yields an empty catalog, unreadable entries and
         * duplicate ids are skipped, and [selectedId] is applied through [select].
         * zh-CN: 解码 [toJson] 的输出; 不可读的 JSON 得到空目录, 不可读的条目与重复 id 被跳过, [selectedId] 经 [select] 生效.
         */
        fun fromJson(json: String?, selectedId: String? = null, newId: () -> String = ::timeId): FilterProfileCatalog {
            if (json.isNullOrBlank()) return empty(selectedId, newId)
            val entries: List<ProfileJson?> = try {
                GSON.fromJson(json, PROFILE_LIST) ?: return empty(selectedId, newId)
            } catch (e: JsonParseException) {
                return empty(selectedId, newId)
            } catch (e: IllegalStateException) {
                return empty(selectedId, newId)
            }
            val profiles = ArrayList<FilterProfile>()
            for (entry in entries) {
                val profile = entry?.toProfile() ?: continue
                if (profiles.none { it.id == profile.id }) profiles += profile
            }
            return FilterProfileCatalog(profiles, selectedId, newId)
        }

        private fun timeId(): String = "p" + System.currentTimeMillis().toString(36) + (System.nanoTime() and 0xFFF).toString(36)

    }

}
