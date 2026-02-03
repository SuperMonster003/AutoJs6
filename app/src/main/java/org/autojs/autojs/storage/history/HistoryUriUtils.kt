package org.autojs.autojs.storage.history

import android.net.Uri

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 */
object HistoryUriUtils {

    private const val INTERNAL_ROOT = "/storage/emulated/0"

    /**
     * Resolve a logical path for history indexing.
     *
     * Strategy:
     * - Prefer extracting "/storage/emulated/0/..." from uri.toString() for third-party providers.
     * - Fallback to uri.path.
     * - Return null if not under internal storage root.
     *
     * zh-CN:
     *
     * 为历史索引解析 logical path.
     *
     * 策略:
     * - 对第三方 provider 优先从 uri.toString() 中提取 "/storage/emulated/0/..." 子串.
     * - 回退到 uri.path.
     * - 若不在内部存储根目录下则返回 null.
     */
    fun toLogicalPathOrNull(uri: Uri): String? {
        val raw = uri.toString()
        val idx = raw.indexOf(INTERNAL_ROOT)
        val extracted = if (idx >= 0) {
            raw.substring(idx)
        } else {
            uri.path
        } ?: return null

        val normalized = extracted.trimEnd('/')
        return if (normalized.startsWith(INTERNAL_ROOT)) normalized else null
    }
}
