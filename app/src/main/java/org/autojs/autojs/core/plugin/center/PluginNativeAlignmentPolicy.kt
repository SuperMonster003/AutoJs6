package org.autojs.autojs.core.plugin.center

/** Zero means no native code. Missing/invalid metadata stays unknown. */
object PluginNativeAlignmentPolicy {
    fun parse(value: Any?): Long? {
        val text = when (value) {
            is String -> value
            is Byte, is Short, is Int, is Long -> value.toString()
            else -> return null
        }
        if (!text.matches(Regex("0|[1-9][0-9]*"))) return null
        return text.toLongOrNull()?.takeIf { it >= 0 && (it == 0L || it and (it - 1) == 0L) }
    }

    fun needsWarning(alignment: Long?, pageSize: Long): Boolean =
        pageSize >= 16384 && alignment != null && alignment in 1 until pageSize

    fun installedAlignment(declared: Long?, versionCode: Long?, releases: List<PluginIndexRelease>): Long? =
        declared ?: versionCode?.let { code -> releases.firstOrNull { it.versionCode == code }?.nativePageAlignment }
}
