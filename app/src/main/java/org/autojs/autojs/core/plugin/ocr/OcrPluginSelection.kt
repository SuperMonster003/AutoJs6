package org.autojs.autojs.core.plugin.ocr

import org.autojs.autojs.core.plugin.center.PluginVariantPriorityStore
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.paddle.ocr.api.PaddleOcrPluginCapabilityKeys

/**
 * The criteria and the default order that pick an OCR plugin in [OcrPluginHost], kept apart from its Android state
 * (paddle-ocr-v3 roadmap D22 / D23).
 * zh-CN: [OcrPluginHost] 选择 OCR 插件的条件与默认顺序, 与其 Android 状态分开 (paddle-ocr-v3 路线图 D22 / D23).
 */
internal object OcrPluginSelection {

    fun declaredModelProfile(pluginInfo: PluginInfo?): String? {
        return pluginInfo?.capabilities?.getString(PaddleOcrPluginCapabilityKeys.MODEL_PROFILE)?.takeIf { it.isNotBlank() }
    }

    fun isRecommendedDefault(pluginInfo: PluginInfo?): Boolean {
        return pluginInfo?.capabilities?.getBoolean(PaddleOcrPluginCapabilityKeys.RECOMMENDED_FOR_MOBILE_DEFAULT, false) == true
    }

    fun hasExplicitCriteria(engineId: String?, engine: String?, variant: String?, profile: String?): Boolean {
        return engineId != null || engine != null || variant != null || profile != null
    }

    fun matches(
        pluginInfo: PluginInfo,
        engineId: String?,
        engine: String?,
        variant: String?,
        profile: String?,
        modelProfileOf: (PluginInfo) -> String? = ::declaredModelProfile,
    ): Boolean {
        if (engineId != null && pluginInfo.id != engineId) return false
        if (engine != null && pluginInfo.engine != engine) return false
        if (variant != null && pluginInfo.variant != variant) return false
        // An explicit profile only matches a plugin that declares the same model profile.
        // zh-CN: 显式 profile 只匹配声明了相同模型 profile 的插件.
        if (profile != null) {
            val expected = normalizeProfile(profile) ?: return false
            if (normalizeProfile(modelProfileOf(pluginInfo)) != expected) return false
        }
        return true
    }

    /**
     * Compares profiles case-insensitively and treats spaces, "_" and "-" alike, so that "mobile-en" matches
     * the "Mobile EN" profile of the PP-OCRv4 plugin.
     * zh-CN: 忽略大小写比较 profile, 空格, "_" 与 "-" 视为相同, 因此 "mobile-en" 能匹配 PP-OCRv4 插件的 "Mobile EN".
     */
    fun normalizeProfile(raw: String?): String? {
        return raw?.trim()?.lowercase()?.replace(Regex("[\\s_-]+"), "-")?.takeIf { it.isNotEmpty() }
    }

    /**
     * The order without a stored priority: the higher variant number, then a plugin declaring
     * `recommendedForMobileDefault`, then the smallest package name (D22).
     * zh-CN: 没有已存优先级时的顺序: variant 数字大者优先, 其次是声明 `recommendedForMobileDefault` 的插件, 最后取包名最小者 (D22).
     */
    fun <T> selectDefault(
        candidates: List<T>,
        pluginInfoOf: (T) -> PluginInfo?,
        packageNameOf: (T) -> String,
        recommendedDefaultOf: (PluginInfo?) -> Boolean = ::isRecommendedDefault,
    ): T? {
        return PluginVariantPriorityStore.selectWithoutStoredOrder(
            candidates = candidates,
            packageNameOf = packageNameOf,
            variantOf = { pluginInfoOf(it)?.variant },
            isRecommendedDefault = { recommendedDefaultOf(pluginInfoOf(it)) },
        )
    }

    /**
     * The distinct model profiles that the plugins matching the other criteria declare, listed when no plugin
     * matches the requested profile.
     * zh-CN: 符合其他条件的插件所声明的模型 profile (去重), 在没有插件匹配所请求的 profile 时列出.
     */
    fun installedProfiles(
        pluginInfos: List<PluginInfo>,
        engineId: String?,
        engine: String?,
        variant: String?,
        modelProfileOf: (PluginInfo) -> String? = ::declaredModelProfile,
    ): List<String> {
        return pluginInfos
            .filter { matches(it, engineId, engine, variant, profile = null) }
            .mapNotNull { modelProfileOf(it)?.trim() }
            .distinctBy { normalizeProfile(it) }
    }

}
