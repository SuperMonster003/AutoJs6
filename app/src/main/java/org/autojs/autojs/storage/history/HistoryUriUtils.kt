package org.autojs.autojs.storage.history

import android.net.Uri

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 * Modified by Claude (Fable 5.1) as of Sep 16, 2026.
 */
object HistoryUriUtils {

    /**
     * Resolve a logical path for history indexing; see [HistoryPaths.toLogicalPathOrNull].
     * zh-CN: 为历史索引解析 logical path; 规则见 [HistoryPaths.toLogicalPathOrNull].
     */
    fun toLogicalPathOrNull(uri: Uri): String? = HistoryPaths.toLogicalPathOrNull(uri.toString(), uri.path)
}
