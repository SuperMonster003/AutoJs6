package org.autojs.autojs.storage.history

/**
 * Decisions about how a snapshot attaches to a file entry.
 * zh-CN: 快照如何关联到 file entry 的决策.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object HistoryFingerprintPolicy {

    enum class Attach {
        /** An entry with the same logical path exists; keep using it. */
        EXISTING_BY_PATH,

        /** No entry for the path, but an entry whose latest content matches and whose old path is gone (rename / move). */
        REATTACH_BY_FINGERPRINT,

        /** Create a fresh entry. */
        CREATE_NEW,
    }

    /**
     * Re-attaching by fingerprint is only allowed when the old path no longer exists on disk.
     * Otherwise an identical copy (copy-paste in the explorer) would steal the original file's history.
     * zh-CN: 仅当旧路径已不存在时才允许按指纹重新绑定, 否则内容相同的副本会 "偷走" 原文件的历史.
     */
    fun decideAttach(
        hasEntryByPath: Boolean,
        hasEntryByFingerprint: Boolean,
        fingerprintEntryPathStillExists: Boolean,
    ): Attach = when {
        hasEntryByPath -> Attach.EXISTING_BY_PATH
        hasEntryByFingerprint && !fingerprintEntryPathStillExists -> Attach.REATTACH_BY_FINGERPRINT
        else -> Attach.CREATE_NEW
    }

    /**
     * A snapshot whose content equals the newest stored revision adds nothing and is skipped.
     * zh-CN: 与最新 revision 内容相同的快照没有价值, 跳过写入.
     */
    fun isDuplicateSnapshot(
        latestSha256: String?,
        latestSizeBytes: Long?,
        sha256: String,
        sizeBytes: Long,
    ): Boolean {
        if (latestSha256 == null || latestSizeBytes == null) return false
        return latestSizeBytes == sizeBytes && latestSha256.equals(sha256, ignoreCase = true)
    }
}
