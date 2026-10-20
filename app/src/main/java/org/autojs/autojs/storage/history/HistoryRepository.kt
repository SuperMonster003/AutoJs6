package org.autojs.autojs.storage.history

import android.content.Context
import org.autojs.autojs.core.pref.keys.HistoryKeys
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingDetector
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 6, 2026.
 * Modified by Claude (Fable 5.1) as of Sep 16, 2026.
 */
class HistoryRepository(private val context: Context) {

    private val db by lazy { HistoryDatabase.getInstance(context.applicationContext) }
    private val dao by lazy { db.historyDao() }
    private val blobs by lazy { HistoryBlobStore(context.applicationContext) }

    /**
     * Read revision blob bytes.
     * zh-CN: 读取 revision 对应的 blob bytes.
     */
    fun readRevisionBytes(rev: HistoryEntities.Revision): ByteArray {
        val f = File(context.filesDir, rev.blobRelPath)
        return f.inputStream().use { it.readBytes() }
    }

    /**
     * Record a SAVE_PRE snapshot (old content bytes).
     *
     * Rules:
     * - Only tracks files under internal storage root.
     * - Uses logicalPath first; fallback by latestFingerprint matching when the old path is gone.
     * - The fingerprint of [newBytes] (the content about to be written) becomes the entry's
     *   latestFingerprint, so a later save of the renamed file can be re-attached.
     * - A snapshot identical to the newest stored revision is skipped.
     * - Applies per-file and global retention after inserting.
     *
     * zh-CN:
     *
     * 记录一次 SAVE_PRE 快照 (旧内容 bytes).
     *
     * 规则:
     * - 仅纳管内部存储根目录下的文件.
     * - 优先按 logicalPath 匹配; 找不到且旧路径已不存在时用 latestFingerprint 回退匹配.
     * - [newBytes] (即将写入的内容) 的指纹作为 entry 的 latestFingerprint, 使重命名后的下次保存可以重新绑定.
     * - 与最新 revision 内容相同的快照跳过.
     * - 插入后执行单文件与全局保留策略清理.
     *
     * @return the inserted revision, or null when skipped as a duplicate.
     */
    @JvmOverloads
    fun recordSavePre(
        logicalPath: String,
        oldBytes: ByteArray,
        encodingName: String,
        hadBom: Boolean,
        newBytes: ByteArray? = null,
    ): HistoryEntities.Revision? = recordSnapshot(
        logicalPath = HistoryPaths.normalizePath(logicalPath),
        bytes = oldBytes,
        encodingName = encodingName,
        hadBom = hadBom,
        op = OP_SAVE_PRE,
        newContentSha256 = newBytes?.let { sha256Hex(it) },
    )

    /**
     * Snapshot the current on-disk content of [path] right before the app overwrites it
     * (restore from history, restore from trash, copy / move with overwrite).
     *
     * Skipped (returns null) when the target is not a regular file under internal storage,
     * exceeds the tracked file size limit, or duplicates the newest revision. Never throws.
     *
     * zh-CN: 在应用覆盖写入 [path] 之前为其当前磁盘内容留一份快照 (历史恢复 / 回收站恢复 / 覆盖式复制移动).
     * 目标不是内部存储下的普通文件, 超过纳管大小上限, 或与最新 revision 重复时跳过 (返回 null); 不抛异常.
     *
     * @param newContentSha256 fingerprint of the content that will replace the file, if known.
     */
    @JvmOverloads
    fun recordSnapshotBeforeOverwrite(
        path: String,
        op: String,
        newContentSha256: String? = null,
    ): HistoryEntities.Revision? = runCatching {
        val logicalPath = HistoryPaths.normalizePath(path)
        if (!HistoryPaths.isUnderInternalStorage(logicalPath)) return@runCatching null

        val file = File(logicalPath)
        if (!file.isFile) return@runCatching null

        val limit = HistoryKeys.maxFileSizeToTrackBytes()
        if (!HistoryRetentionPolicy.isUnlimited(limit) && file.length() > limit) return@runCatching null

        val bytes = file.readBytes()
        val detection = EditorEncodingDetector.detect(bytes)
        recordSnapshot(
            logicalPath = logicalPath,
            bytes = bytes,
            encodingName = detection.charset.name(),
            hadBom = detection.hadBom,
            op = op,
            newContentSha256 = newContentSha256,
        )
    }.onFailure { it.printStackTrace() }.getOrNull()

    private fun recordSnapshot(
        logicalPath: String,
        bytes: ByteArray,
        encodingName: String,
        hadBom: Boolean,
        op: String,
        newContentSha256: String?,
    ): HistoryEntities.Revision? {
        val now = System.currentTimeMillis()

        val sha = sha256Hex(bytes)
        val fileEntry = getOrCreateFileEntry(
            logicalPath = logicalPath,
            lookupFingerprint = sha,
            latestFingerprint = newContentSha256 ?: sha,
            now = now,
        )

        val latest = dao.findLatestRevision(fileEntry.fileId)
        if (HistoryFingerprintPolicy.isDuplicateSnapshot(latest?.sha256, latest?.sizeBytes, sha, bytes.size.toLong())) {
            return null
        }

        val revId = blobs.newRevId()
        val blobRel = blobs.writeRevisionBlob(fileEntry.fileId, revId, bytes)

        val revision = HistoryEntities.Revision(
            revId = revId,
            fileId = fileEntry.fileId,
            op = op,
            createdAt = now,
            logicalPathAtThatTime = logicalPath,
            encoding = encodingName,
            hadBom = hadBom,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha,
            blobRelPath = blobRel,
        )
        dao.insertRevision(revision)

        cleanupPerFile(fileEntry.fileId, now)
        cleanupGlobal()

        return revision
    }

    /**
     * Store [bytes] as a MANUAL revision of [logicalPath]: pinned, with an optional label.
     * If the newest revision already holds this content it is pinned and labelled instead of
     * storing a duplicate blob. The entry's fingerprint follows the on-disk content (the snapshot
     * may come from an unsaved editor buffer) so a later rename can still re-attach the entry.
     *
     * zh-CN: 把 [bytes] 存为 [logicalPath] 的 MANUAL revision: 默认固定, 可选标签.
     * 若最新 revision 已是相同内容, 则改为固定并写入标签, 不再重复存储 blob. 条目指纹跟随磁盘内容
     * (快照可能来自未保存的编辑器缓冲), 以便之后重命名仍能重新绑定.
     */
    fun recordManualSnapshot(
        logicalPath: String,
        bytes: ByteArray,
        encodingName: String,
        hadBom: Boolean,
        label: String?,
    ): HistoryEntities.Revision? = runCatching {
        val normalizedLabel = HistorySnapshotLabel.normalizeOrNull(label)
        val now = System.currentTimeMillis()
        val sha = sha256Hex(bytes)
        val diskSha = runCatching {
            File(logicalPath).takeIf { it.isFile }?.readBytes()?.let { sha256Hex(it) }
        }.getOrNull()

        val fileEntry = getOrCreateFileEntry(
            logicalPath = logicalPath,
            lookupFingerprint = diskSha ?: sha,
            latestFingerprint = diskSha ?: sha,
            now = now,
        )

        val latest = dao.findLatestRevision(fileEntry.fileId)
        if (latest != null && HistoryFingerprintPolicy.isDuplicateSnapshot(latest.sha256, latest.sizeBytes, sha, bytes.size.toLong())) {
            if (normalizedLabel != null) dao.updateRevisionLabel(latest.revId, normalizedLabel)
            dao.updateRevisionPinned(latest.revId, true)
            return@runCatching latest.copy(label = normalizedLabel ?: latest.label, pinned = true)
        }

        val revId = blobs.newRevId()
        val blobRel = blobs.writeRevisionBlob(fileEntry.fileId, revId, bytes)
        val revision = HistoryEntities.Revision(
            revId = revId,
            fileId = fileEntry.fileId,
            op = OP_MANUAL,
            createdAt = now,
            logicalPathAtThatTime = logicalPath,
            encoding = encodingName,
            hadBom = hadBom,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha,
            blobRelPath = blobRel,
            label = normalizedLabel,
            pinned = true,
        )
        dao.insertRevision(revision)

        cleanupPerFile(fileEntry.fileId, now)
        cleanupGlobal()

        revision
    }.onFailure { it.printStackTrace() }.getOrNull()

    fun setRevisionLabel(rev: HistoryEntities.Revision, label: String?) {
        dao.updateRevisionLabel(rev.revId, HistorySnapshotLabel.normalizeOrNull(label))
    }

    fun setRevisionPinned(rev: HistoryEntities.Revision, pinned: Boolean) {
        dao.updateRevisionPinned(rev.revId, pinned)
    }

    /**
     * Clear all history revisions/blobs for a given path.
     *
     * Rules:
     * - If target is a file: clear exact match.
     * - If target is a directory: clear all entries under this directory by path prefix.
     *
     * zh-CN:
     *
     * 清除指定路径对应的全部历史版本与 blob.
     *
     * 规则:
     * - 若目标是文件: 精确匹配清理.
     * - 若目标是目录: 按路径前缀清理该目录下所有已纳管条目.
     */
    fun clearHistoryForPath(path: String) {
        val normalized = HistoryPaths.normalizePath(path)

        val target = File(normalized)
        val fileIds = if (target.isDirectory) {
            val prefix = "$normalized/"
            dao.listFilesByPathPrefix(prefix).map { it.fileId }
        } else {
            dao.findFileByPath(normalized)?.let { listOf(it.fileId) } ?: emptyList()
        }

        if (fileIds.isEmpty()) return

        val revisions = dao.listRevisionsByFileIds(fileIds)
        dao.deleteRevisionsByFileIds(fileIds)
        revisions.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
        dao.deleteFileEntriesByIds(fileIds)
    }

    /**
     * Snapshot every tracked regular file under [dirPath] before the directory is replaced.
     * Only files that already own a history entry are read, so the cost is bounded by the history,
     * not by the directory size. Never throws.
     *
     * zh-CN: 目录被整体覆盖前, 为其下所有已纳管的普通文件留快照. 只读取已有 file entry 的文件,
     * 代价由历史规模而非目录大小决定. 不抛异常.
     *
     * @return the number of revisions recorded.
     */
    fun recordSnapshotsBeforeOverwriteUnderDirectory(dirPath: String): Int = runCatching {
        val dir = HistoryPaths.normalizePath(dirPath)
        if (!HistoryPaths.isUnderInternalStorage(dir)) return@runCatching 0
        val prefix = "$dir/"
        dao.listFilesByPathPrefix(prefix)
            .filter { it.logicalPath.startsWith(prefix) }
            .count { recordSnapshotBeforeOverwrite(it.logicalPath, OP_OVERWRITE_PRE) != null }
    }.onFailure { it.printStackTrace() }.getOrDefault(0)

    /**
     * Follow a rename or move: entries for [oldPath] (a file) or beneath it (a directory) get the
     * corresponding path under [newPath]. When the mapped path already owns an entry (the move
     * overwrote a tracked file), the revisions of both are merged into that entry and the moved
     * file's fingerprint wins, so the history of a path stays continuous. Never throws.
     *
     * zh-CN: 跟随重命名 / 移动: [oldPath] (文件) 或其下 (目录) 的条目改为 [newPath] 下的对应路径.
     * 映射后的路径已有条目时 (移动覆盖了已纳管文件), 两者的 revision 合并到该条目, 并沿用被移动文件的指纹,
     * 使同一路径的历史保持连续. 不抛异常.
     *
     * @return the number of entries relocated (including merged ones).
     */
    fun relocate(oldPath: String, newPath: String): Int = runCatching {
        val old = HistoryPaths.normalizePath(oldPath)
        val new = HistoryPaths.normalizePath(newPath)
        if (old == new) return@runCatching 0
        if (!HistoryPaths.isUnderInternalStorage(old) || !HistoryPaths.isUnderInternalStorage(new)) return@runCatching 0

        val prefix = "$old/"
        val affected = LinkedHashMap<String, HistoryEntities.FileEntry>()
        dao.findFileByPath(old)?.let { affected[it.fileId] = it }
        dao.listFilesByPathPrefix(prefix)
            .filter { HistoryRelocation.isAffected(it.logicalPath, old) }
            .forEach { affected[it.fileId] = it }
        if (affected.isEmpty()) return@runCatching 0

        val now = System.currentTimeMillis()
        var relocated = 0
        for (entry in affected.values) {
            val mapped = HistoryRelocation.mapPath(entry.logicalPath, old, new) ?: continue
            val existing = dao.findFileByPath(mapped)
            if (existing != null && existing.fileId != entry.fileId) {
                // Merge into the entry that already owns the destination path.
                // zh-CN: 合并到已拥有目标路径的条目.
                dao.moveRevisionsToFile(entry.fileId, existing.fileId)
                dao.deleteFileEntriesByIds(listOf(entry.fileId))
                dao.upsertFile(existing.copy(lastSeenAt = now, latestFingerprint = entry.latestFingerprint))
            } else {
                dao.upsertFile(entry.copy(logicalPath = mapped, lastSeenAt = now))
            }
            relocated++
        }
        relocated
    }.onFailure { it.printStackTrace() }.getOrDefault(0)

    /**
     * Apply the complete retention policy to every tracked file: per-file days / count / bytes,
     * then the global byte limit, then drop file entries that no longer own any revision.
     * Used by the cleanup worker, the on-demand cleanup after a settings change and "Clean up now".
     *
     * zh-CN: 对所有已纳管文件执行完整保留策略: 单文件的天数 / 条数 / 容量, 然后是全局容量上限,
     * 最后移除不再拥有任何 revision 的 file entry. 供清理 Worker, 设置变更后的即时清理与 "立即清理" 使用.
     */
    @JvmOverloads
    fun enforceRetentionPolicy(now: Long = System.currentTimeMillis()) {
        dao.listAllFiles().forEach { entry -> cleanupPerFile(entry.fileId, now) }
        cleanupGlobal()

        val emptyFileIds = dao.listFileIdsWithoutRevisions()
        if (emptyFileIds.isNotEmpty()) {
            dao.deleteFileEntriesByIds(emptyFileIds)
        }
    }

    /**
     * Find or create the file entry for a logical path.
     *
     * [lookupFingerprint] is the fingerprint of the content currently on disk (used to find an
     * entry whose file was renamed or moved); [latestFingerprint] is what the entry will carry
     * afterwards (the content that will be on disk once the pending write completes).
     *
     * zh-CN: 查找或创建 logical path 对应的 file entry.
     * [lookupFingerprint] 是当前磁盘内容的指纹 (用于找到被重命名 / 移动的文件的条目);
     * [latestFingerprint] 是本次写入完成后磁盘内容的指纹, 写入条目供下次匹配.
     */
    private fun getOrCreateFileEntry(
        logicalPath: String,
        lookupFingerprint: String,
        latestFingerprint: String,
        now: Long,
    ): HistoryEntities.FileEntry {
        val byPath = dao.findFileByPath(logicalPath)
        val byFingerprint = if (byPath == null) dao.findFileByLatestFingerprint(lookupFingerprint) else null

        val decision = HistoryFingerprintPolicy.decideAttach(
            hasEntryByPath = byPath != null,
            hasEntryByFingerprint = byFingerprint != null,
            fingerprintEntryPathStillExists = byFingerprint?.let { File(it.logicalPath).exists() } ?: false,
        )

        return when (decision) {
            HistoryFingerprintPolicy.Attach.EXISTING_BY_PATH -> {
                val updated = requireNotNull(byPath).copy(
                    lastSeenAt = now,
                    latestFingerprint = latestFingerprint,
                )
                dao.upsertFile(updated)
                updated
            }
            HistoryFingerprintPolicy.Attach.REATTACH_BY_FINGERPRINT -> {
                // Re-attach to new path (rename / move happened outside our hooks).
                // zh-CN: 重新绑定到新路径 (重命名 / 移动发生在钩子之外).
                val updated = requireNotNull(byFingerprint).copy(
                    logicalPath = logicalPath,
                    lastSeenAt = now,
                    latestFingerprint = latestFingerprint,
                )
                dao.upsertFile(updated)
                updated
            }
            HistoryFingerprintPolicy.Attach.CREATE_NEW -> {
                val created = HistoryEntities.FileEntry(
                    fileId = blobs.newFileId(),
                    logicalPath = logicalPath,
                    createdAt = now,
                    lastSeenAt = now,
                    latestFingerprint = latestFingerprint,
                )
                dao.upsertFile(created)
                created
            }
        }
    }

    /**
     * Enforce per-file retention (days, count, bytes); zero or negative limits mean unlimited.
     * Pinned revisions neither count towards the limits nor get deleted here.
     * zh-CN: 执行单文件保留策略 (天数 / 条数 / 容量); 上限为 0 或负数表示不限.
     * 固定的 revision 既不计入上限, 也不会在此被删除.
     */
    private fun cleanupPerFile(fileId: String, now: Long) {
        val revisions = dao.listRevisionsAsc(fileId).filter { !it.pinned }
        if (revisions.isEmpty()) return

        val toDelete = HistoryRetentionPolicy.selectToDelete(
            itemsOldestFirst = revisions,
            now = now,
            maxDays = HistoryKeys.historyMaxDays(),
            maxCount = HistoryKeys.historyMaxVersions(),
            maxBytes = HistoryKeys.historyMaxTotalBytesPerFile(),
            createdAt = { it.createdAt },
            sizeOf = { it.sizeBytes },
        )
        deleteRevisionsAndBlobs(toDelete)
    }

    /**
     * Delete a single revision and its blob.
     * zh-CN: 删除单个 revision 及其 blob.
     */
    fun deleteRevision(rev: HistoryEntities.Revision) {
        deleteRevisionsAndBlobs(listOf(rev))
    }

    /**
     * Clear all version history (all file entries, all revisions, all blobs).
     * zh-CN: 清空全部版本历史 (所有 file_entry, 所有 revision, 所有 blobs).
     */
    fun clearAllHistory() {
        val revs = dao.listAllRevisionsAsc()
        if (revs.isNotEmpty()) {
            revs.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
        }
        dao.deleteAllRevisions()
        dao.deleteAllFileEntries()
    }

    fun getRevisionsCount(fileId: String): Int {
        return dao.listRevisionsAsc(fileId).size
    }

    private fun deleteRevisionsAndBlobs(revs: List<HistoryEntities.Revision>) {
        if (revs.isEmpty()) return
        dao.deleteRevisionsByIds(revs.map { it.revId })
        revs.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }

    /**
     * Enforce global history total bytes limit (remove oldest revisions first). Unpinned revisions
     * go first; pinned ones are only removed once no unpinned revision is left.
     * zh-CN: 约束全局历史总容量上限 (优先删除最旧 revision). 先删未固定的, 仅当没有未固定的 revision 时才删固定的.
     */
    private fun cleanupGlobal() {
        val maxTotalBytes = HistoryKeys.historyMaxTotalBytes()
        if (HistoryRetentionPolicy.isUnlimited(maxTotalBytes)) return

        var totalBytes = (dao.sumAllRevisionBytes() ?: 0L).coerceAtLeast(0L)
        if (totalBytes <= maxTotalBytes) return

        // Delete in batches to reduce DB roundtrips.
        // zh-CN: 批量删除以减少 DB 往返次数.
        val batchSize = 50

        while (totalBytes > maxTotalBytes) {
            val oldest = dao.listOldestUnpinnedRevisions(batchSize).ifEmpty { dao.listOldestRevisions(batchSize) }
            if (oldest.isEmpty()) break

            val toRemove = HistoryRetentionPolicy.selectOverBytes(oldest, maxTotalBytes, totalBytes) { it.sizeBytes }
            if (toRemove.isEmpty()) break

            deleteRevisionsAndBlobs(toRemove)
            totalBytes = (totalBytes - toRemove.sumOf { it.sizeBytes.coerceAtLeast(0L) }).coerceAtLeast(0L)
        }
    }

    companion object {
        /** Content captured right before an editor save overwrote it. */
        const val OP_SAVE_PRE: String = "SAVE_PRE"

        /** Content captured right before a restore (history or trash) overwrote it. */
        const val OP_RESTORE_PRE: String = "RESTORE_PRE"

        /** Content captured right before an explorer copy / move overwrote it. */
        const val OP_OVERWRITE_PRE: String = "OVERWRITE_PRE"

        /** Snapshot created on purpose by the user (pinned by default, may carry a label). */
        const val OP_MANUAL: String = "MANUAL"
    }
}
