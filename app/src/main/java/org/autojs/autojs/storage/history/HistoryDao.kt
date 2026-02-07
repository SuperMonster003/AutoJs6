package org.autojs.autojs.storage.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Flowable

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 7, 2026.
 */
@Dao
interface HistoryDao {

    @Query("SELECT * FROM file_entry WHERE logicalPath = :logicalPath LIMIT 1")
    fun findFileByPath(logicalPath: String): HistoryEntities.FileEntry?

    @Query("SELECT * FROM file_entry WHERE latestFingerprint = :fingerprint ORDER BY lastSeenAt DESC LIMIT 1")
    fun findFileByLatestFingerprint(fingerprint: String): HistoryEntities.FileEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertFile(entry: HistoryEntities.FileEntry)

    @Insert
    fun insertRevision(rev: HistoryEntities.Revision)

    @Query("SELECT * FROM revision WHERE fileId = :fileId ORDER BY createdAt ASC")
    fun listRevisionsAsc(fileId: String): List<HistoryEntities.Revision>

    @Query("DELETE FROM revision WHERE revId IN (:revIds)")
    fun deleteRevisionsByIds(revIds: List<String>)

    @Query("SELECT SUM(sizeBytes) FROM revision WHERE fileId = :fileId")
    fun sumBytesByFileId(fileId: String): Long?

    /**
     * Sum revision bytes for multiple fileIds in one query.
     * zh-CN: 在一次查询中批量统计多个 fileId 的 revision 字节总和.
     */
    @Query(
        "SELECT fileId AS fileId, SUM(sizeBytes) AS totalBytes " +
            "FROM revision WHERE fileId IN (:fileIds) GROUP BY fileId"
    )
    fun sumBytesByFileIds(fileIds: List<String>): List<FileBytesSumRow>

    /**
     * Aggregate revision stats for multiple fileIds in one query.
     * zh-CN: 在一次查询中批量聚合多个 fileId 的 revision 统计信息.
     */
    @Query(
        "SELECT fileId AS fileId, COUNT(*) AS revisionCount, SUM(sizeBytes) AS totalBytes " +
                "FROM revision WHERE fileId IN (:fileIds) GROUP BY fileId"
    )
    fun aggStatsByFileIds(fileIds: List<String>): List<FileAggStatsRow>

    @Query("SELECT * FROM file_entry")
    fun listAllFiles(): List<HistoryEntities.FileEntry>

    @Query("SELECT * FROM revision WHERE createdAt < :expiredBefore ORDER BY createdAt ASC")
    fun listExpiredRevisions(expiredBefore: Long): List<HistoryEntities.Revision>

    @Query("SELECT blobRelPath FROM revision")
    fun listAllBlobRelPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTrashItem(item: TrashEntities.TrashItem)

    @Query("SELECT * FROM trash_item ORDER BY trashedAt DESC")
    fun listTrashItemsDesc(): List<TrashEntities.TrashItem>

    @Query("SELECT * FROM trash_item WHERE trashedAt < :expiredBefore ORDER BY trashedAt ASC")
    fun listExpiredTrashItems(expiredBefore: Long): List<TrashEntities.TrashItem>

    @Query("DELETE FROM trash_item WHERE trashId IN (:trashIds)")
    fun deleteTrashItemsByIds(trashIds: List<String>)

    @Query("SELECT blobRelPath FROM trash_item")
    fun listAllTrashBlobRelPaths(): List<String>

    @Query("DELETE FROM trash_item")
    fun deleteAllTrashItems()

    @Query("SELECT * FROM file_entry WHERE logicalPath LIKE :pathPrefixEscape || '%'")
    fun listFilesByPathPrefix(pathPrefixEscape: String): List<HistoryEntities.FileEntry>

    @Query("DELETE FROM file_entry WHERE fileId IN (:fileIds)")
    fun deleteFileEntriesByIds(fileIds: List<String>)

    @Query("SELECT * FROM revision WHERE fileId IN (:fileIds) ORDER BY createdAt ASC")
    fun listRevisionsByFileIds(fileIds: List<String>): List<HistoryEntities.Revision>

    @Query("DELETE FROM revision WHERE fileId IN (:fileIds)")
    fun deleteRevisionsByFileIds(fileIds: List<String>)

    @Query("SELECT SUM(sizeBytes) FROM revision")
    fun sumAllRevisionBytes(): Long?

    @Query("SELECT * FROM revision ORDER BY createdAt ASC LIMIT :limit")
    fun listOldestRevisions(limit: Int): List<HistoryEntities.Revision>

    /**
     * Aggregated stats for trash.
     * zh-CN: 回收站聚合统计.
     */
    @Query("SELECT COUNT(*) AS count, SUM(sizeBytes) AS totalBytes FROM trash_item")
    fun observeTrashStats(): Flowable<TrashStatsRow>

    /**
     * Aggregated stats for version history (all revisions).
     * zh-CN: 版本历史聚合统计 (所有 revisions).
     */
    @Query("SELECT COUNT(*) AS fileCount, (SELECT SUM(sizeBytes) FROM revision) AS totalBytes FROM file_entry")
    fun observeVersionHistoryStats(): Flowable<VersionHistoryStatsRow>

    @Query("SELECT * FROM revision ORDER BY createdAt ASC")
    fun listAllRevisionsAsc(): List<HistoryEntities.Revision>

    @Query("DELETE FROM revision")
    fun deleteAllRevisions()

    @Query("DELETE FROM file_entry")
    fun deleteAllFileEntries()
}

/**
 * Aggregated bytes row for revisions.
 * zh-CN: revision 的聚合字节统计行.
 */
data class FileBytesSumRow(
    val fileId: String,
    val totalBytes: Long?,
)

/**
 * Aggregated stats row for revisions by fileId.
 * zh-CN: 按 fileId 聚合的 revision 统计行.
 */
data class FileAggStatsRow(
    val fileId: String,
    val revisionCount: Long,
    val totalBytes: Long?,
)

/**
 * Aggregated stats row for trash.
 * zh-CN: 回收站统计行.
 */
data class TrashStatsRow(
    val count: Long,
    val totalBytes: Long?,
)

/**
 * Aggregated stats row for version history.
 * zh-CN: 版本历史统计行.
 */
data class VersionHistoryStatsRow(
    val fileCount: Long,
    val totalBytes: Long?,
)
