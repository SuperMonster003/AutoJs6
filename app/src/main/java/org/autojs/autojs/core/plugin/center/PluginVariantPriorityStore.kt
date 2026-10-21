package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.json.JSONArray

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 21, 2026.
 *
 * The variant priority per engine in the default preferences, the key family [PluginCenterKeys.VARIANT_PRIORITY]
 * (docs/settings/ROADMAP.md P3.1, names unchanged); the JSON array text is still read and written with org.json here.
 */
object PluginVariantPriorityStore {

    private val family = PluginCenterKeys.VARIANT_PRIORITY

    fun getOrder(engine: String?): List<String> {
        val key = keyOf(engine) ?: return emptyList()
        val text = key.storedText().orEmpty().trim()
        if (text.isBlank()) return emptyList()
        return parseOrderText(text)
    }

    fun setOrder(engine: String?, packageNames: List<String>) {
        val key = keyOf(engine) ?: return
        val normalized = packageNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (normalized.isEmpty()) {
            key.remove()
            return
        }
        val arr = JSONArray()
        normalized.forEach { arr.put(it) }
        key.setStoredText(arr.toString())
    }

    fun <T> selectByPriority(
        engine: String?,
        candidates: List<T>,
        packageNameOf: (T) -> String,
        variantOf: (T) -> String?,
    ): T? {
        if (candidates.isEmpty()) return null
        val order = getOrder(engine)
        if (order.isNotEmpty()) {
            val byPkg = candidates.associateBy { packageNameOf(it) }
            order.firstNotNullOfOrNull { byPkg[it] }?.let { return it }
        }
        return candidates.maxWithOrNull(
            compareBy<T> { parseVariantWeight(variantOf(it)) }
                .thenByDescending { packageNameOf(it) }
        )
    }

    fun normalizeKey(raw: String?): String? {
        return raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    fun normalizeFamilyKey(raw: String?): String? {
        val normalized = normalizeKey(raw) ?: return null
        return normalized
            .replace(Regex("([._-])?v\\d+$"), "")
            .trim('.', '-', '_')
            .takeIf { it.isNotEmpty() }
    }

    fun buildPriorityKeys(engine: String?, engineId: String?): List<String> {
        return listOfNotNull(
            normalizeFamilyKey(engineId),
            normalizeKey(engineId),
            normalizeKey(engine),
            normalizeFamilyKey(engine),
        ).distinct()
    }

    fun getBestOrderForPackages(packageNames: Set<String>): List<String> {
        if (packageNames.isEmpty()) return emptyList()
        val requiredMatches = minOf(2, packageNames.size)
        var bestOrder: List<String> = emptyList()
        var bestScore = 0
        var bestKeyLength = 0

        family.settingsStore.all().forEach { (key, value) ->
            if (!family.matches(key)) return@forEach
            val text = value as? String ?: return@forEach
            val order = parseOrderText(text)
            if (order.isEmpty()) return@forEach
            val score = order.count { it in packageNames }
            val keyLength = key.length
            if (score > bestScore || (score == bestScore && keyLength > bestKeyLength)) {
                bestScore = score
                bestKeyLength = keyLength
                bestOrder = order
            }
        }

        return if (bestScore >= requiredMatches) bestOrder else emptyList()
    }

    private fun parseVariantWeight(variant: String?): Int {
        val raw = variant?.trim().orEmpty()
        return raw.replace(Regex("\\D"), "").toIntOrNull() ?: 0
    }

    private fun keyOf(engine: String?) = normalizeKey(engine)?.let(family::key)

    private fun parseOrderText(text: String): List<String> {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(normalizedText)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optString(i)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { add(it) }
                }
            }
        }.getOrElse { emptyList() }
    }
}
