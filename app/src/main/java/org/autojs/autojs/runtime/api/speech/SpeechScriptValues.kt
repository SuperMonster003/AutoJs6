package org.autojs.autojs.runtime.api.speech

import org.autojs.autojs.core.plugin.speech.SpeechCapabilities
import org.autojs.autojs.core.plugin.speech.SpeechValues
import org.autojs.plugin.speech.api.SpeechResultKeys

/**
 * The plain JVM values the `speech` script API hands to Rhino (3-Macaw Speech roadmap appendix B.3) and the text helpers
 * of the session objects. Nothing here touches Rhino or Android, so the JVM tests cover it.
 * zh-CN: `speech` 脚本 API 交给 Rhino 的普通 JVM 值 (3-Macaw Speech 路线图附录 B.3) 与会话对象的文本工具. 这里不触碰 Rhino 与 Android,
 * 因此由 JVM 测试覆盖.
 */
internal object SpeechScriptValues {

    /** `speech.capabilities()`. zh-CN: `speech.capabilities()`. */
    fun capabilities(capabilities: SpeechCapabilities): Map<String, Any?> = linkedMapOf(
        "contractVersion" to capabilities.contractVersion,
        "features" to capabilities.features,
        "catalogVersion" to capabilities.catalogVersion,
        "engineName" to capabilities.engineName,
        "engineVersion" to capabilities.engineVersion,
        "ttsEnginePackage" to capabilities.ttsEnginePackage,
        "microphoneGranted" to capabilities.microphoneGranted,
        "packageName" to capabilities.packageName,
        "versionName" to capabilities.pluginVersionName,
        "versionCode" to SpeechValues.number(capabilities.pluginVersionCode),
    )

    /**
     * The accumulated text of a session from its final results: segments are concatenated, with one space only where
     * two letters or digits would otherwise touch (so Chinese stays unspaced and English words stay apart).
     * zh-CN: 由最终结果累计的会话文本: 各段直接拼接, 只有在两个字母或数字会相连时插入一个空格 (中文保持无空格, 英文单词保持分隔).
     */
    fun joinText(parts: List<String>): String {
        val builder = StringBuilder()
        parts.map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            if (builder.isNotEmpty() && isWordChar(builder.last()) && isWordChar(part.first())) builder.append(' ')
            builder.append(part)
        }
        return builder.toString()
    }

    private fun isWordChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    /** The text of a `final` or `end` payload. zh-CN: `final` 或 `end` 载荷中的文本. */
    fun textOf(payload: Map<String, Any?>): String? = payload[SpeechResultKeys.TEXT] as? String

    /** The round index a payload carries, or null. zh-CN: 载荷携带的轮次序号, 或 null. */
    fun roundOf(payload: Map<String, Any?>): Int? = (payload[SpeechResultKeys.ROUND] as? Number)?.toInt()

    /** A payload without the transport-only `round` key (the script sees the round on the session object). zh-CN: 去掉仅用于传输的 `round` 键的载荷 (脚本在会话对象上看到轮次). */
    fun withoutRound(payload: Map<String, Any?>): Map<String, Any?> = LinkedHashMap(payload).apply { remove(SpeechResultKeys.ROUND) }
}
