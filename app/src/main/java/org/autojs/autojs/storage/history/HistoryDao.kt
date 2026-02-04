package org.autojs.autojs.storage.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
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
}