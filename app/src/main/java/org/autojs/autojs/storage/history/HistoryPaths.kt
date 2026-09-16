package org.autojs.autojs.storage.history

import org.autojs.autojs.util.EnvironmentUtils

/**
 * Internal storage root and logical path helpers shared by trash and version history.
 * zh-CN: 回收站与版本历史共用的内部存储根目录与 logical path 工具.
 *
 * Pure functions accept the root explicitly so they can be unit tested without Android.
 * zh-CN: 纯函数显式接收根目录参数, 便于脱离 Android 进行单元测试.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object HistoryPaths {

    const val DEFAULT_INTERNAL_STORAGE_ROOT: String = "/storage/emulated/0"

    /**
     * Internal storage root of the current user, e.g. "/storage/emulated/0" or "/storage/emulated/10".
     * zh-CN: 当前用户的内部存储根目录, 如 "/storage/emulated/0" 或 "/storage/emulated/10".
     */
    @JvmStatic
    val internalStorageRoot: String by lazy {
        runCatching { EnvironmentUtils.externalStoragePath }
            .getOrNull()
            ?.let { normalizePath(it) }
            ?.takeIf { it.length > 1 && it.startsWith("/") }
            ?: DEFAULT_INTERNAL_STORAGE_ROOT
    }

    /**
     * Trim surrounding whitespace and trailing slashes (keeps a lone "/").
     * zh-CN: 去除首尾空白与末尾斜杠 (保留单独的 "/").
     */
    @JvmStatic
    fun normalizePath(path: String): String {
        var p = path.trim()
        while (p.length > 1 && p.endsWith("/")) p = p.dropLast(1)
        return p
    }

    /**
     * Whether [path] is the root itself or lies under it ("/storage/emulated/0abc" does not count).
     * zh-CN: [path] 是否为根目录本身或位于其下 ("/storage/emulated/0abc" 不算).
     */
    @JvmStatic
    @JvmOverloads
    fun isUnderInternalStorage(path: String, root: String = internalStorageRoot): Boolean {
        val normalized = normalizePath(path)
        val normalizedRoot = normalizePath(root)
        return normalized == normalizedRoot || normalized.startsWith("$normalizedRoot/")
    }

    @JvmStatic
    @JvmOverloads
    fun isInternalStorageRoot(path: String?, root: String = internalStorageRoot): Boolean {
        if (path == null) return false
        return normalizePath(path) == normalizePath(root)
    }

    /**
     * Resolve a logical path for history indexing.
     *
     * Strategy:
     * - Prefer extracting "<root>/..." from the raw uri string for third-party providers.
     * - Fallback to the uri path component.
     * - Return null if not under the internal storage root.
     *
     * zh-CN:
     *
     * 为历史索引解析 logical path.
     *
     * 策略:
     * - 对第三方 provider 优先从 uri 原文中提取 "<root>/..." 子串.
     * - 回退到 uri 的 path 部分.
     * - 若不在内部存储根目录下则返回 null.
     */
    @JvmStatic
    @JvmOverloads
    fun toLogicalPathOrNull(rawUri: String, uriPath: String?, root: String = internalStorageRoot): String? {
        val normalizedRoot = normalizePath(root)
        val idx = rawUri.indexOf(normalizedRoot)
        val extracted = if (idx >= 0) rawUri.substring(idx) else uriPath
        val normalized = extracted?.let { normalizePath(it) } ?: return null
        return if (isUnderInternalStorage(normalized, normalizedRoot)) normalized else null
    }
}
