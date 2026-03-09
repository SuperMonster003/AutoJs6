package org.autojs.autojs.ui.edit.keyboard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.util.LocaleUtils
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Symbol profiles store for editor symbol bar.
 * zh-CN: 编辑器符号栏的多配置存储.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 8, 2026.
 */
object SymbolsConfigStore {

    // Cache for default-name aliases.
    // zh-CN: "默认名称别名集合" 的缓存 (按语言环境 key 缓存).
    private val sDefaultAliasesCache = ConcurrentHashMap<String, Set<String>>()

    private const val PREF_NAME = "editor_symbols_config"
    private const val KEY_ACTIVE_PROFILE = "active_profile"
    private const val KEY_PROFILES_JSON = "profiles_json"

    // Internal stable id for default profile.
    // zh-CN: 默认配置的内部稳定标识 (不随语言变化).
    const val PROFILE_DEFAULT_ID = "__default__"

    data class SymbolItem(
        val text: String,
        val enabled: Boolean = true,
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getActiveProfileName(context: Context): String =
        prefs(context).getString(KEY_ACTIVE_PROFILE, PROFILE_DEFAULT_ID) ?: PROFILE_DEFAULT_ID

    fun setActiveProfileName(context: Context, name: String) {
        prefs(context).edit { putString(KEY_ACTIVE_PROFILE, name) }
    }

    fun getPrefillProfileName(context: Context): String {
        val prefix = "config-"
        val candidates = listProfiles(context)
            .filter { it.startsWith(prefix) }
            .mapNotNull { it.removePrefix(prefix).toIntOrNull() }
        var candidate = 1
        while (true) {
            if (!candidates.contains(candidate)) {
                return "$prefix$candidate"
            }
            candidate++
        }
    }

    fun listProfiles(context: Context): List<String> {
        val root = readRootOrCreate(context)
        val profiles = root.optJSONObject("profiles") ?: JSONObject()
        return profiles.keys().asSequence().toList().sorted()
    }

    fun loadProfile(context: Context, name: String): List<SymbolItem> {
        val root = readRootOrCreate(context)
        val profiles = root.optJSONObject("profiles") ?: JSONObject()
        val arr = profiles.optJSONArray(name) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val t = o.optString("t", "")
            if (!isValidSymbolText(t)) return@mapNotNull null
            SymbolItem(
                text = t,
                enabled = o.optBoolean("e", true),
            )
        }
    }

    fun saveProfile(context: Context, name: String, items: List<SymbolItem>) {
        val root = readRootOrCreate(context)
        val profiles = root.optJSONObject("profiles") ?: JSONObject().also { root.put("profiles", it) }

        val arr = JSONArray()
        items.forEach { item ->
            if (!isValidSymbolText(item.text)) return@forEach
            arr.put(JSONObject().apply {
                put("t", item.text)
                put("e", item.enabled)
            })
        }
        profiles.put(name, arr)

        persistRoot(context, root)
    }

    fun deleteProfile(context: Context, name: String) {
        val root = readRootOrCreate(context)
        val profiles = root.optJSONObject("profiles") ?: return
        profiles.remove(name)
        persistRoot(context, root)

        // If active profile deleted, fallback to default.
        // zh-CN: 删除当前激活配置时回退到默认.
        if (getActiveProfileName(context) == name) {
            setActiveProfileName(context, PROFILE_DEFAULT_ID)
        }
    }

    fun getEnabledSymbolsForActiveProfile(context: Context): List<String> {
        ensureDefaultProfileExists(context)

        val active = getActiveProfileName(context)
        val items = loadProfile(context, active)
        val enabled = items.filter { it.enabled }.map { it.text }

        return enabled
    }

    fun ensureDefaultProfileExists(context: Context) {
        val root = readRootOrCreate(context)
        val profiles = root.optJSONObject("profiles") ?: JSONObject().also { root.put("profiles", it) }
        if (profiles.has(PROFILE_DEFAULT_ID)) return

        val arr = JSONArray()
        defaultSymbols().forEach { item ->
            arr.put(JSONObject().apply {
                put("t", item.text)
                put("e", item.enabled)
            })
        }
        profiles.put(PROFILE_DEFAULT_ID, arr)

        persistRoot(context, root)

        // Also set SharedPreferences active_profile if absent.
        // zh-CN: 也写入 active_profile, 便于快速读取.
        if (!prefs(context).contains(KEY_ACTIVE_PROFILE)) {
            setActiveProfileName(context, PROFILE_DEFAULT_ID)
        }
    }

    /**
     * Export a profile to JSON object.
     *
     * Format:
     * {
     *   "name": "xxx",
     *   "items": [ {"t":"...", "e":true}, ... ]
     * }
     */
    fun exportProfileToJson(context: Context, name: String): JSONObject {
        val items = loadProfile(context, name)
        val arr = JSONArray()
        items.forEach { item ->
            if (!isValidSymbolText(item.text)) return@forEach
            arr.put(JSONObject().apply {
                put("t", item.text)
                put("e", item.enabled)
            })
        }
        return JSONObject().apply {
            put("name", name)
            put("items", arr)
        }
    }

    /**
     * Import profile from JSON object.
     * Returns imported profile name and items.
     *
     * Rules:
     * - Symbol text must be non-blank and contain NO whitespace chars.
     * - Duplicates are removed (keep first occurrence order).
     */
    fun importProfileFromJson(json: JSONObject): Pair<String, List<SymbolItem>> {
        val name = json.optString("name", "").trim()
        require(name.isNotBlank()) { "Invalid profile name" }

        val arr = json.optJSONArray("items") ?: JSONArray()

        val out = ArrayList<SymbolItem>(arr.length())
        val seen = HashSet<String>()

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val t = o.optString("t", "")
            if (!isValidSymbolText(t)) continue
            if (!seen.add(t)) continue

            out.add(SymbolItem(text = t, enabled = o.optBoolean("e", true)))
        }

        return name to out
    }

    /**
     * A symbol is valid iff:
     * - not blank
     * - contains no whitespace chars (\\s)
     */
    fun isValidSymbolText(text: String?): Boolean {
        val s = text ?: return false
        if (s.isBlank()) return false
        return !s.any { it.isWhitespace() }
    }

    /**
     * Display name of default profile under current app language.
     * zh-CN: 当前 App 语言下 "默认配置" 的显示名 (来自 strings.xml).
     */
    fun getDefaultProfileDisplayName(context: Context): String {
        return context.getString(R.string.text_symbols_profile_default_name)
    }

    /**
     * All localized aliases for "default" across all supported app languages.
     * zh-CN: 遍历 Language 枚举, 收集所有语言下 "默认" 的显示名, 作为保留字集合.
     */
    fun getAllDefaultNameAliases(context: Context): Set<String> {
        val appLang = Language.getPrefLanguageOrNull()
        val appKey = appLang?.languageTag ?: "null"
        val sysKey = LocaleUtils.getPrimarySystemLocale().toLanguageTag()
        val cacheKey = "app=$appKey|sys=$sysKey"

        sDefaultAliasesCache[cacheKey]?.let { return it }

        val computed = LinkedHashSet<String>().apply {
            add(PROFILE_DEFAULT_ID)

            Language.values().forEach { lang ->
                if (lang == Language.AUTO) return@forEach
                val s = StringUtils.getStringForLocale(context, lang.locale, R.string.text_symbols_profile_default_name)
                    .trim()
                if (s.isNotBlank()) add(s)
            }

            add(getDefaultProfileDisplayName(context).trim())
        }.toSet()

        sDefaultAliasesCache[cacheKey] = computed
        return computed
    }

    fun isReservedDefaultName(context: Context, name: String?): Boolean {
        val n = name?.trim().orEmpty()
        if (n.isBlank()) return false
        return getAllDefaultNameAliases(context).any { it == n }
    }

    private fun readRootOrCreate(context: Context): JSONObject {
        val sp = prefs(context)
        val raw = sp.getString(KEY_PROFILES_JSON, null)
        return try {
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Throwable) {
            JSONObject()
        }.also { r ->
            if (!r.has("profiles")) r.put("profiles", JSONObject())
        }
    }

    private fun persistRoot(context: Context, root: JSONObject) {
        prefs(context).edit { putString(KEY_PROFILES_JSON, root.toString()) }
    }

    private fun defaultSymbols(): List<SymbolItem> = listOf(
        ",", ".", "=", ";", "\"", "'", "/", "-", "_",
        "(", ")", "[", "]", "{", "}", "<", ">",
        "+", "*", "?", ":", "$", "#", "@", "`",
        "\\", "&", "|", "!", "%", "×", "÷",
        "∈", "∩", "∪", "∉", "⊙", "∅", "¥", "€",
        "°", "℃", "∵", "∴", "±", "≠", "≈",
        "α", "β", "γ", "λ", "μ", "π", "σ", "ω",
        "®", "©", "♂", "♀", "√", "×", "✔", "✘",
        "♥", "♠", "♦", "♣", "★", "◀", "▶", "●", "■", "▲", "◆",
    ).map { SymbolItem(it, true) }
}
