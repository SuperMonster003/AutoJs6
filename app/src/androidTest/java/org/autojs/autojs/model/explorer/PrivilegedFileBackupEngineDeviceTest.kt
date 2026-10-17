package org.autojs.autojs.model.explorer

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Every test uses a newly-created UUID cache directory; no user documents or permissions change. */
@RunWith(AndroidJUnit4::class)
class PrivilegedFileBackupEngineDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun inspectProvidesFastMetadataAndAnExplicitlyVerifiedContentSnapshot() = fixture { root ->
        val saved = saveFixture(root)
        val fast = execute(backupRequest("backup_inspect", saved))
        assertOk(fast)
        assertEquals("available", fast.getString("status"))
        assertFalse(fast.getBoolean("digestVerified"))
        assertTrue(fast.getBoolean("canRestore"))
        assertTrue(fast.getBoolean("autoClean"))
        assertTrue(fast.getLong("createdAt") > 0)
        val verified = inspect(saved)
        assertTrue(verified.getBoolean("digestVerified"))
        assertEquals(hash("original"), verified.getString("digest"))
        assertEquals(8L, verified.getLong("size"))
        assertEquals(Os.lstat(saved.backup.path).st_ino, verified.getJSONObject("identity").getLong("ino"))
    }

    @Test
    fun restoringKeepsTheSelectedHistoryAndCreatesABackupOfTheCurrentContent() = fixture { root ->
        val saved = saveFixture(root)
        Os.chmod(saved.source.path, 0x1a0)
        val mode = Os.lstat(saved.source.path).st_mode
        val request = restoreRequest(saved, inspect(saved))
        val restored = execute(request)
        assertOk(restored)
        assertEquals("original", saved.source.readText())
        assertEquals("original", saved.backup.readText())
        assertEquals("edited", File(restored.getString("backupPath")).readText())
        assertEquals(mode, Os.lstat(saved.source.path).st_mode)
        assertEquals(saved.id, restored.getString("restoredFromTransactionId"))
        assertEquals(request.getString("restoreTransactionId"), restored.getString("transactionId"))
        assertTrue(restored.getBoolean("completed"))
        val completion = JSONObject(File(File(restored.getString("backupPath")).parentFile, "result.json").readText())
        assertTrue(completion.getBoolean("completed"))
    }

    @Test
    fun changedOriginalBetweenConfirmationAndRestoreIsNotOverwritten() = fixture { root ->
        val saved = saveFixture(root)
        val request = restoreRequest(saved, inspect(saved))
        saved.source.writeText("external change")
        val restored = execute(request)
        assertCode("SOURCE_CHANGED", restored)
        assertEquals("external change", saved.source.readText())
        assertEquals("original", saved.backup.readText())
    }

    @Test
    fun changingOnlyTheExpectedDigestStillRejectsAnExternalEdit() = fixture { root ->
        val saved = saveFixture(root)
        val request = restoreRequest(saved, inspect(saved))
        saved.source.writeText("extern")
        request.put("expected", snapshot(saved.source).getJSONObject("identity"))
        val restored = execute(request)
        assertCode("SOURCE_CHANGED", restored)
        assertEquals("extern", saved.source.readText())
    }

    @Test
    fun aDeletedTargetIsRestoredWithoutAlteringTheSelectedBackupOrInventingAPreimage() = fixture { root ->
        val saved = saveFixture(root)
        val backupStat = Os.lstat(saved.backup.path)
        val before = inspect(saved)
        check(saved.source.delete())
        val restored = execute(backupRequest("backup_restore", saved).put("expectedMissing", true)
            .put("backupExpected", before.getJSONObject("identity")).put("backupDigest", before.getString("digest"))
            .put("restoreTransactionId", UUID.randomUUID().toString()))
        assertOk(restored)
        assertTrue(restored.getBoolean("missingTargetRestored"))
        assertFalse(restored.has("backupPath"))
        assertEquals("original", saved.source.readText())
        assertEquals("original", saved.backup.readText())
        val actual = Os.lstat(saved.source.path)
        assertEquals(backupStat.st_uid, actual.st_uid)
        assertEquals(backupStat.st_gid, actual.st_gid)
        assertEquals(backupStat.st_mode, actual.st_mode)
        assertTrue(File(restored.getString("stagePath"), "result.json").exists())
        assertFalse(File(restored.getString("stagePath"), "original").exists())
    }

    @Test
    fun expectedMissingNeverReplacesANewlyAppearingFile() = fixture { root ->
        val saved = saveFixture(root)
        val before = inspect(saved)
        val restored = execute(backupRequest("backup_restore", saved).put("expectedMissing", true)
            .put("backupExpected", before.getJSONObject("identity")).put("backupDigest", before.getString("digest"))
            .put("restoreTransactionId", UUID.randomUUID().toString()))
        assertCode("SOURCE_CHANGED", restored)
        assertEquals("edited", saved.source.readText())
        assertEquals("original", saved.backup.readText())
    }

    @Test
    fun aTargetAppearingDuringTheMissingRestoreIsPreserved() = fixture { root ->
        val saved = saveFixture(root)
        val backup = inspect(saved)
        check(saved.source.delete())
        val request = JSONObject().put("source", saved.source.path).put("expectedMissing", true)
            .put("transactionId", UUID.randomUUID().toString()).put("contentSize", saved.backup.length())
            .put("contentDigest", backup.getString("digest")).put("deadlineMillis", System.currentTimeMillis() + 30_000)
        PrivilegedFileSaveEngine.Directory.open(root.canonicalPath).use { parent ->
            parent.acquireLease()
            val identity = PrivilegedFileSaveEngine.identity(saved.backup.path)
            PrivilegedFileSaveEngine.openFile(saved.backup.path, identity, writable = false).use { descriptor ->
                var checks = 0
                ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { input ->
                    val restored = JSONObject(PrivilegedFileSaveEngine.saveFromBackup(request.toString(), input, parent, descriptor) {
                        if (++checks == 2) saved.source.writeText("new external file")
                    })
                    assertCode("SOURCE_CHANGED", restored)
                }
            }
        }
        assertEquals("new external file", saved.source.readText())
        assertEquals("original", saved.backup.readText())
    }

    @Test
    fun aBackupChangedAfterSelectionCannotBeRestoredOrDeleted() = fixture { root ->
        val saved = saveFixture(root)
        val selected = inspect(saved)
        val restore = restoreRequest(saved, selected)
        val delete = deleteRequest(saved, selected)
        saved.backup.writeText("external backup edit")
        assertCode("BACKUP_CHANGED", execute(restore))
        assertCode("BACKUP_CHANGED", execute(delete))
        assertEquals("edited", saved.source.readText())
        assertEquals("external backup edit", saved.backup.readText())
    }

    @Test
    fun aFreshlyConfirmedChangedBackupCanBeRestoredManuallyButNotCleanedAutomatically() = fixture { root ->
        val saved = saveFixture(root)
        saved.backup.writeText("externally updated backup")
        val before = inspect(saved)
        assertEquals("changed", before.getString("status"))
        assertFalse(before.getBoolean("autoClean"))
        assertTrue(before.getBoolean("canRestore"))
        assertCode("BACKUP_CHANGED", execute(deleteRequest(saved, before).put("auto", true)))
        assertOk(execute(restoreRequest(saved, before)))
        assertEquals("externally updated backup", saved.source.readText())
        assertEquals("externally updated backup", saved.backup.readText())
    }

    @Test
    fun automaticDeleteRehashesContentEvenWhenTheProvidedMetadataAndDigestWereStale() = fixture { root ->
        val saved = saveFixture(root)
        val selected = inspect(saved)
        val modified = saved.backup.lastModified()
        saved.backup.writeText("external")
        check(saved.backup.setLastModified(modified))
        val request = deleteRequest(saved, selected).put("auto", true)
            .put("backupExpected", snapshot(saved.backup).getJSONObject("identity"))
        assertCode("BACKUP_CHANGED", execute(request))
        assertEquals("external", saved.backup.readText())
    }

    @Test
    fun deletingPayloadLeavesAReplayFenceAndKeepsTheCurrentOriginal() = fixture { root ->
        val saved = saveFixture(root)
        val deleted = execute(deleteRequest(saved, inspect(saved)).put("auto", true))
        assertOk(deleted)
        assertTrue(deleted.getBoolean("removed"))
        assertFalse(deleted.getBoolean("retired"))
        assertTrue(deleted.getBoolean("tombstone"))
        assertFalse(saved.backup.exists())
        assertEquals("edited", saved.source.readText())
        assertTrue(File(saved.stage, "deleted.json").exists())
        val missing = inspect(saved)
        assertEquals("missing", missing.getString("status"))
        assertTrue(missing.getBoolean("tombstone"))
        assertOk(execute(backupRequest("backup_delete", saved)))
        val replay = saveRequest(saved.source, snapshot(saved.source), "replay").put("transactionId", saved.id)
        assertCode("DESTINATION_EXISTS", JSONObject(PrivilegedFileSaveEngine.save(replay.toString(), ByteArrayInputStream("replay".toByteArray()))))
        assertEquals("edited", saved.source.readText())
    }

    @Test
    fun expiredMetadataIsRemovedOnlyAfterThePayloadHasBeenValidatedAndDeleted() = fixture { root ->
        val saved = saveFixture(root)
        rewriteManifest(saved) { put("createdAt", System.currentTimeMillis() - 20 * 60_000L).put("deadlineMillis", System.currentTimeMillis() - 5 * 60_000L) }
        val deleted = execute(deleteRequest(saved, inspect(saved)))
        assertOk(deleted)
        assertTrue(deleted.getBoolean("removed"))
        assertTrue(deleted.getBoolean("retired"))
        assertFalse(saved.stage.exists())
        assertEquals("edited", saved.source.readText())
        assertFalse(inspect(saved).getBoolean("stageExists"))
    }

    @Test
    fun oldManifestsWithoutAbsoluteDeadlinesRetainASmallFence() = fixture { root ->
        val saved = saveFixture(root)
        rewriteManifest(saved) { remove("createdAt"); remove("deadlineMillis") }
        val deleted = execute(deleteRequest(saved, inspect(saved)))
        assertOk(deleted)
        assertFalse(deleted.getBoolean("retired"))
        assertTrue(saved.stage.exists())
        assertFalse(saved.backup.exists())
    }

    @Test
    fun unknownEntriesAreNeverRecursivelyRemoved() = fixture { root ->
        val saved = saveFixture(root)
        val foreign = File(saved.stage, "keep-user-data").apply { mkdir() }
        File(foreign, "keep.txt").writeText("keep")
        rewriteManifest(saved) { put("createdAt", System.currentTimeMillis() - 20 * 60_000L).put("deadlineMillis", System.currentTimeMillis() - 5 * 60_000L) }
        val before = inspect(saved)
        assertFalse(before.getBoolean("autoClean"))
        assertCode("BACKUP_CHANGED", execute(deleteRequest(saved, before).put("auto", true)))
        val deleted = execute(deleteRequest(saved, before))
        assertOk(deleted)
        assertTrue(deleted.getBoolean("removed"))
        assertFalse(deleted.getBoolean("retired"))
        assertEquals("keep", File(foreign, "keep.txt").readText())
    }

    @Test
    fun completedRecoveryPayloadCanBePreviewedAndExplicitlyDeletedButNeverAutoCleaned() = fixture { root ->
        val source = File(root, "source").apply { writeText("original") }
        val request = saveRequest(source, snapshot(source), "long content")
        val failed = JSONObject(PrivilegedFileSaveEngine.save(request.toString(), ByteArrayInputStream("short".toByteArray())))
        assertCode("TRANSFER_MISMATCH", failed)
        val stage = File(root, ".autojs6-save-${request.getString("transactionId")}")
        val saved = Saved(source, request.getString("transactionId"), stage, File(stage, "original"))
        val before = inspect(saved)
        assertEquals("recovery", before.getString("status"))
        assertTrue(before.getBoolean("canView"))
        assertTrue(before.getBoolean("canDelete"))
        assertFalse(before.getBoolean("canRestore"))
        assertCode("BACKUP_CHANGED", execute(deleteRequest(saved, before).put("auto", true)))
        assertCode("BACKUP_RECOVERY_REQUIRED", execute(restoreRequest(saved, before)))
        assertOk(execute(deleteRequest(saved, before)))
        assertFalse(saved.backup.exists())
        assertEquals("original", source.readText())
    }

    @Test
    fun interruptedDeletionRemainsVisibleAndCanBeExplicitlyFinished() = fixture { root ->
        val saved = saveFixture(root)
        val quarantined = File(saved.stage, ".delete-payload")
        check(saved.backup.renameTo(quarantined))
        val before = inspect(saved)
        assertEquals("recovery", before.getString("status"))
        assertEquals(quarantined.path, before.getString("backupPath"))
        assertEquals(8L, before.getLong("size"))
        assertTrue(before.getBoolean("canView"))
        assertTrue(before.getBoolean("canDelete"))
        assertFalse(before.getBoolean("autoClean"))
        assertCode("BACKUP_CHANGED", execute(deleteRequest(saved, before).put("auto", true)))
        assertOk(execute(deleteRequest(saved, before)))
        assertFalse(quarantined.exists())
        assertEquals("edited", saved.source.readText())
    }

    @Test
    fun anUnknownRecoveryPayloadIsNotHiddenAsACleanedTombstone() = fixture { root ->
        val saved = saveFixture(root)
        val unknown = File(saved.stage, ".discard-${UUID.randomUUID()}")
        check(saved.backup.renameTo(unknown))
        val before = inspect(saved)
        assertEquals("recovery", before.getString("status"))
        assertFalse(before.getBoolean("tombstone"))
        assertFalse(before.getBoolean("canDelete"))
        assertCode("BACKUP_RECOVERY_REQUIRED", execute(backupRequest("backup_delete", saved)))
        val scan = execute(JSONObject().put("operation", "backup_scan").put("source", root.path))
        assertFalse(scan.getJSONArray("entries").getJSONObject(0).getBoolean("tombstone"))
        assertEquals("original", unknown.readText())
    }

    @Test
    fun activeSaveIsReportedBusyAndCannotBeDeletedOrRestored() = fixture { root ->
        val source = File(root, "source").apply { writeText("original") }
        val request = saveRequest(source, snapshot(source), "edited")
        val stage = File(root, ".autojs6-save-${request.getString("transactionId")}")
        val saved = Saved(source, request.getString("transactionId"), stage, File(stage, "original"))
        val result = JSONObject(PrivilegedFileSaveEngine.saveWithCommitObserver(request.toString(), ByteArrayInputStream("edited".toByteArray())) {
            val inspection = execute(backupRequest("backup_inspect", saved))
            assertOk(inspection)
            assertEquals("busy", inspection.getString("status"))
            assertCode("TRANSACTION_BUSY", execute(backupRequest("backup_delete", saved)))
            assertCode("TRANSACTION_BUSY", execute(backupRequest("backup_restore", saved)))
        })
        assertOk(result)
        assertEquals("edited", source.readText())
        assertEquals("original", saved.backup.readText())
    }

    @Test
    fun aMismatchedManifestCannotAuthorizeDeletion() = fixture { root ->
        val saved = saveFixture(root)
        rewriteManifest(saved) { put("source", File(root, "different-file").path) }
        val before = inspect(saved)
        assertEquals("recovery", before.getString("status"))
        assertFalse(before.getBoolean("canDelete"))
        assertCode("INVALID_BACKUP", execute(deleteRequest(saved, before)))
        assertEquals("original", saved.backup.readText())
    }

    @Test
    fun aSymlinkPayloadIsNotFollowedOrRemoved() = fixture { root ->
        val saved = saveFixture(root)
        val foreign = File(root, "outside").apply { writeText("keep") }
        check(saved.backup.delete())
        Os.symlink(foreign.path, saved.backup.path)
        val before = inspect(saved)
        assertEquals("recovery", before.getString("status"))
        assertFalse(before.getBoolean("canView"))
        assertCode("UNSUPPORTED_TYPE", execute(backupRequest("backup_delete", saved)))
        assertEquals("keep", foreign.readText())
        assertTrue(OsConstants.S_ISLNK(Os.lstat(saved.backup.path).st_mode))
    }

    @Test
    fun readPermissionFailureDoesNotRemoveAnyContent() = fixture { root ->
        val saved = saveFixture(root)
        val selected = inspect(saved)
        Os.chmod(saved.backup.path, 0)
        try {
            assertCode("PERMISSION_DENIED", execute(deleteRequest(saved, selected)))
            assertTrue(saved.backup.exists())
            assertEquals("edited", saved.source.readText())
        } finally {
            Os.chmod(saved.backup.path, 0x180)
        }
        assertEquals("original", saved.backup.readText())
    }

    @Test
    fun scanningFindsOnlyDirectTransactionsWhoseManifestBelongsToThatDirectory() = fixture { root ->
        val direct = saveFixture(root)
        val nested = File(root, "nested").apply { mkdir() }
        saveFixture(nested)
        val falseId = UUID.randomUUID().toString()
        val falseStage = File(root, ".autojs6-save-$falseId").apply { mkdir() }
        File(falseStage, "request.json").writeText(JSONObject().put("transactionId", falseId).put("source", "/different/source").toString())
        Os.symlink(direct.stage.path, File(root, ".autojs6-save-${UUID.randomUUID()}").path)
        val scanned = execute(JSONObject().put("operation", "backup_scan").put("source", root.path))
        assertOk(scanned)
        assertEquals(1, scanned.getJSONArray("entries").length())
        assertEquals(direct.id, scanned.getJSONArray("entries").getJSONObject(0).getString("transactionId"))
        assertFalse(scanned.getBoolean("truncated"))
        assertEquals("original", direct.backup.readText())
    }

    @Test
    fun scanningHasAnExplicitBoundAndIncludesSmallTombstones() = fixture { root ->
        val first = saveFixture(root)
        val second = saveFixture(root, "second")
        assertOk(execute(deleteRequest(first, inspect(first))))
        val scanned = execute(JSONObject().put("operation", "backup_scan").put("source", root.path).put("limit", 1))
        assertOk(scanned)
        assertEquals(1, scanned.getJSONArray("entries").length())
        assertTrue(scanned.getBoolean("truncated"))
        val all = execute(JSONObject().put("operation", "backup_scan").put("source", root.path)).getJSONArray("entries")
        val rows = (0 until all.length()).map { all.getJSONObject(it) }
        assertTrue(rows.single { it.getString("transactionId") == first.id }.getBoolean("tombstone"))
        assertFalse(rows.single { it.getString("transactionId") == second.id }.getBoolean("tombstone"))
    }

    private data class Saved(val source: File, val id: String, val stage: File, val backup: File)

    private fun saveFixture(root: File, name: String = "source"): Saved {
        val source = File(root, name).apply { writeText("original") }
        val request = saveRequest(source, snapshot(source), "edited")
        val saved = JSONObject(PrivilegedFileSaveEngine.save(request.toString(), ByteArrayInputStream("edited".toByteArray())))
        assertOk(saved)
        val backup = File(saved.getString("backupPath"))
        return Saved(source, request.getString("transactionId"), backup.parentFile!!, backup)
    }

    private fun saveRequest(source: File, snapshot: JSONObject, content: String) = JSONObject().put("source", source.path)
        .put("expected", snapshot.getJSONObject("identity")).put("expectedDigest", snapshot.getString("digest"))
        .put("contentSize", content.toByteArray().size).put("contentDigest", hash(content))
        .put("transactionId", UUID.randomUUID().toString()).put("deadlineMillis", System.currentTimeMillis() + 30_000L)

    private fun backupRequest(operation: String, saved: Saved) = JSONObject().put("operation", operation)
        .put("source", saved.source.path).put("transactionId", saved.id).put("deadlineMillis", System.currentTimeMillis() + 30_000)

    private fun restoreRequest(saved: Saved, inspected: JSONObject): JSONObject {
        val original = snapshot(saved.source)
        return backupRequest("backup_restore", saved).put("backupExpected", inspected.getJSONObject("identity"))
            .put("backupDigest", inspected.getString("digest")).put("expected", original.getJSONObject("identity"))
            .put("expectedDigest", original.getString("digest")).put("restoreTransactionId", UUID.randomUUID().toString())
    }

    private fun deleteRequest(saved: Saved, inspected: JSONObject) = backupRequest("backup_delete", saved)
        .put("backupExpected", inspected.getJSONObject("identity")).put("backupDigest", inspected.getString("digest"))

    private fun rewriteManifest(saved: Saved, rewrite: JSONObject.() -> Unit) {
        val file = File(saved.stage, "request.json")
        val manifest = JSONObject(file.readText()).apply(rewrite)
        file.writeText(manifest.toString())
    }

    private fun inspect(saved: Saved) = execute(backupRequest("backup_inspect", saved).put("verifyContent", true)).also(::assertOk)
    private fun execute(request: JSONObject) = JSONObject(PrivilegedFileBackupEngine.execute(request.toString()))
    private fun snapshot(file: File) = JSONObject(PrivilegedFileSaveEngine.statSnapshot(JSONObject().put("source", file.path).toString())).also(::assertOk)
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun assertOk(result: JSONObject) = assertTrue(result.toString(), result.getBoolean("ok"))
    private fun assertCode(code: String, result: JSONObject) {
        assertFalse(result.toString(), result.getBoolean("ok"))
        assertEquals(result.toString(), code, result.getString("code"))
    }

    private inline fun fixture(action: (File) -> Unit) {
        val root = File(context.cacheDir, "privileged-backup-engine-${UUID.randomUUID()}")
        check(root.mkdir())
        try { action(root) } finally { removeFixture(root) }
    }

    private fun removeFixture(file: File) {
        if (OsConstants.S_ISDIR(Os.lstat(file.path).st_mode)) {
            Os.chmod(file.path, 0x1c0)
            file.listFiles()!!.forEach(::removeFixture)
        }
        Os.remove(file.path)
    }
}
