package org.autojs.autojs.ui.edit.keyboard

import android.content.Context
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.pref.keys.EditorKeyboardKeys
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

    // The named store editor_symbols_config: EditorKeyboardKeys.SYMBOLS_ACTIVE_PROFILE and SYMBOLS_PROFILES (P3.2).
    // zh-CN: 命名存储 editor_symbols_config: EditorKeyboardKeys.SYMBOLS_ACTIVE_PROFILE 与 SYMBOLS_PROFILES (P3.2).

    // Internal stable id for default profile.
    // zh-CN: 默认配置的内部稳定标识 (不随语言变化).
    const val PROFILE_DEFAULT_ID = "__default__"

    data class SymbolItem(
        val text: String,
        val enabled: Boolean = true,
    )

    enum class DefaultSymbolSet {
        NATIVE,
        ACE,
    }

    @Suppress("UNUSED_PARAMETER")
    fun getActiveProfileName(context: Context): String = EditorKeyboardKeys.SYMBOLS_ACTIVE_PROFILE.get()

    @Suppress("UNUSED_PARAMETER")
    fun setActiveProfileName(context: Context, name: String) {
        EditorKeyboardKeys.SYMBOLS_ACTIVE_PROFILE.set(name)
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

    fun loadProfile(
        context: Context,
        name: String,
        defaultSymbolSet: DefaultSymbolSet = DefaultSymbolSet.NATIVE,
    ): List<SymbolItem> {
        if (name == PROFILE_DEFAULT_ID && defaultSymbolSet == DefaultSymbolSet.ACE) {
            return defaultAceSymbols()
        }

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

    fun getEnabledSymbolsForActiveProfile(
        context: Context,
        defaultSymbolSet: DefaultSymbolSet = DefaultSymbolSet.NATIVE,
    ): List<String> {
        ensureDefaultProfileExists(context)

        val active = getActiveProfileName(context)
        val items = loadProfile(context, active, defaultSymbolSet)
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
        if (!EditorKeyboardKeys.SYMBOLS_ACTIVE_PROFILE.isStored()) {
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
    fun exportProfileToJson(
        context: Context,
        name: String,
        defaultSymbolSet: DefaultSymbolSet = DefaultSymbolSet.NATIVE,
    ): JSONObject {
        val items = loadProfile(context, name, defaultSymbolSet)
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

    @Suppress("UNUSED_PARAMETER")
    private fun readRootOrCreate(context: Context): JSONObject {
        val raw = EditorKeyboardKeys.SYMBOLS_PROFILES.storedText()
        return try {
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Throwable) {
            JSONObject()
        }.also { r ->
            if (!r.has("profiles")) r.put("profiles", JSONObject())
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun persistRoot(context: Context, root: JSONObject) {
        EditorKeyboardKeys.SYMBOLS_PROFILES.setStoredText(root.toString())
    }

    private fun defaultAceSymbols(): List<SymbolItem> = listOf(
        "(", ")", "/", "=", ",", ";", "\"", "'", "{", "}", "[", "]", "`",
        "<", ">", "-", "+", "|", ":", ".", "_", "*", "%", "\\", "#", "$", "&", "!", "?", "^", "@",
        "×", "÷", "∈", "∩", "∪", "∉", "⊙", "∅", "¥", "€",
        "°", "℃", "∵", "∴", "±", "≠", "≈",
        "α", "β", "γ", "λ", "μ", "π", "σ", "ω",
        "®", "©", "♂", "♀", "√", "×", "✔", "✘",
        "♥", "♠", "♦", "♣", "★", "◀", "▶", "●", "■", "▲", "◆",
    ).map { SymbolItem(it, true) }

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
