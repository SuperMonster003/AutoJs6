package org.autojs.autojs.model.explorer

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Real filesystem tests use isolated app-cache/shell fixtures, never user files or new authorization. */
@RunWith(AndroidJUnit4::class)
class PrivilegedFileSaveEngineDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun snapshotHashesTheReadContentAndRetainsTheExistingIdentityFields() = fixture { root ->
        val file = File(root, "中文 😀 ' ${'$'}(never-run)\n.txt").apply { writeText("first\n第二行") }
        val before = snapshot(file)
        assertEquals(hash(file.readBytes()), before.getString("digest"))
        val ordinary = JSONObject(PrivilegedFileEngine.execute(JSONObject().put("operation", "stat")
            .put("source", file.absolutePath).toString())).getJSONObject("identity")
        for (key in listOf("dev", "ino", "mode", "size", "modified", "changed")) {
            assertEquals(ordinary.getLong(key), before.getJSONObject("identity").getLong(key))
        }
        assertEquals(1L, before.getJSONObject("identity").getLong("links"))
        assertEquals(setOf(file.name), root.list()!!.toSet())
    }

    @Test
    fun savesAndSavesAgainUsingTheReturnedBaselineAndRetainsBothOriginals() = fixture { root ->
        val file = File(root, "文件😀.txt").apply { writeText("original") }
        val first = save(request(file, snapshot(file), "first edit"), "first edit")
        assertOk(first)
        assertEquals("committed", first.getString("commitState"))
        assertEquals("first edit", file.readText())
        assertEquals(hash(file.readBytes()), first.getString("digest"))
        val firstBackup = File(first.getString("backupPath"))
        assertEquals("original", firstBackup.readText())
        assertEquals(first.getJSONObject("backupIdentity").getLong("ino"), Os.lstat(firstBackup.path).st_ino)
        val second = save(request(file, first, "second edit"), "second edit")
        assertOk(second)
        assertEquals("second edit", file.readText())
        assertEquals("first edit", File(second.getString("backupPath")).readText())
        assertEquals("original", firstBackup.readText())
        assertTrue(second.getBoolean("cleanupPending"))
        assertEquals(1, second.getJSONArray("recoveryPaths").length())
        assertTrue(File(firstBackup.parentFile, "request.json").exists())
        val completion = JSONObject(File(firstBackup.parentFile, "result.json").readText())
        assertTrue(completion.getBoolean("completed"))
        assertEquals(first.getString("transactionId"), completion.getString("transactionId"))
        assertEquals(first.toString(), completion.toString())
        assertFalse(File(firstBackup.parentFile, "result.pending").exists())
    }

    @Test
    fun emptyFileEditsCanBeSavedWithoutTruncatingTheRecoveryCopy() = fixture { root ->
        val file = File(root, "file").apply { writeText("keep in recovery") }
        val result = save(request(file, snapshot(file), ""), "")
        assertOk(result)
        assertEquals(0L, file.length())
        assertEquals("keep in recovery", File(result.getString("backupPath")).readText())
    }

    @Test
    fun sameSizeAndMtimeChangeIsRejectedEvenWhenTheProvidedStatMetadataMatches() = fixture { root ->
        val file = File(root, "file").apply { writeText("before") }
        val before = snapshot(file)
        val modified = file.lastModified()
        file.writeText("extern")
        check(file.setLastModified(modified))
        // Deliberately accept the fresh stat metadata to exercise SHA-256 protection rather than
        // relying on whether Android's second-resolution ctime crossed a boundary in this test.
        before.put("identity", snapshot(file).getJSONObject("identity"))
        val result = save(request(file, before, "my edit"), "my edit")
        assertCode("SOURCE_CHANGED", result)
        assertEquals("unchanged", result.getString("commitState"))
        assertEquals("extern", file.readText())
        assertEquals(setOf("file"), root.list()!!.toSet())
    }

    @Test
    fun replacementInodeIsRejectedAndTheNewFileIsUntouched() = fixture { root ->
        val file = File(root, "file").apply { writeText("old") }
        val before = snapshot(file)
        check(file.renameTo(File(root, "old-inode")))
        file.writeText("external replacement")
        val result = save(request(file, before, "mine"), "mine")
        assertCode("SOURCE_CHANGED", result)
        assertEquals("external replacement", file.readText())
    }

    @Test
    fun externalModificationDuringUploadIsDetectedBeforePublication() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val request = request(file, snapshot(file), "my edit")
        val input = completingInput("my edit") { file.writeText("external edit") }
        val result = JSONObject(PrivilegedFileSaveEngine.save(request.toString(), input))
        assertCode("SOURCE_CHANGED", result)
        assertEquals("unchanged", result.getString("commitState"))
        assertEquals("external edit", file.readText())
        assertEquals("my edit", recoveryPayload(result).readText())
        val completion = JSONObject(File(recoveryPayload(result).parentFile, "result.json").readText())
        assertTrue(completion.getBoolean("completed"))
        assertEquals("SOURCE_CHANGED", completion.getString("code"))
    }

    @Test
    fun changedParentIsRejectedAndTheRecoveryPathFollowsTheOpenedDirectory() = fixture { root ->
        val parent = File(root, "parent").apply { check(mkdir()) }
        val movedParent = File(root, "renamed-parent")
        val file = File(parent, "file").apply { writeText("original") }
        val request = request(file, snapshot(file), "my edit")
        val input = completingInput("my edit") {
            check(parent.renameTo(movedParent))
            check(parent.mkdir())
            file.writeText("external replacement")
        }
        val result = JSONObject(PrivilegedFileSaveEngine.save(request.toString(), input))
        assertCode("SOURCE_CHANGED", result)
        assertEquals("original", File(movedParent, "file").readText())
        assertEquals("external replacement", file.readText())
        assertEquals("my edit", recoveryPayload(result).readText())
        assertTrue(recoveryPayload(result).canonicalPath.startsWith(movedParent.canonicalPath + "/"))
    }

    @Test
    fun incompleteLongAndWrongChecksumTransfersNeverReplaceTheSource() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        for (incoming in listOf("sho", "too much input", "wrong!")) {
            val result = save(request(file, snapshot(file), "wanted"), incoming)
            assertCode("TRANSFER_MISMATCH", result)
            assertFalse(result.getBoolean("partial"))
            assertEquals("original", file.readText())
        }
    }

    @Test
    fun interruptedInputRetainsOriginalAndThePartialUpload() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val input = object : ByteArrayInputStream("new".toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (available() == 0) throw IOException("transport closed")
                return super.read(buffer, offset, length)
            }
        }
        val result = JSONObject(PrivilegedFileSaveEngine.save(request(file, snapshot(file), "new content").toString(), input))
        assertCode("IO", result)
        assertEquals("original", file.readText())
        assertEquals("new", recoveryPayload(result).readText())
    }

    @Test
    fun readOnlyFilePermissionFailurePreservesTheOriginal() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        Os.chmod(file.path, 0x124) // 0444
        try {
            val result = save(request(file, snapshot(file), "edit"), "edit")
            assertCode("PERMISSION_DENIED", result)
            assertEquals("original", file.readText())
            assertEquals(setOf("file"), root.list()!!.toSet())
        } finally {
            Os.chmod(file.path, 0x180)
        }
    }

    @Test
    fun directoryWritePermissionRevokedDuringUploadDoesNotReplaceTheSource() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val request = request(file, snapshot(file), "edit")
        val input = completingInput("edit") { Os.chmod(root.path, 0x140) } // 0500
        try {
            val result = JSONObject(PrivilegedFileSaveEngine.save(request.toString(), input))
            assertCode("PERMISSION_DENIED", result)
            assertEquals("unchanged", result.getString("commitState"))
            assertEquals("original", file.readText())
        } finally {
            Os.chmod(root.path, 0x1c0)
        }
    }

    @Test
    fun hardLinksAreRefusedWithoutBreakingTheirRelationship() {
        val probe = hardLinkProbe("existing")
        val result = probe.getJSONObject("result")
        assertCode("UNSUPPORTED_HARD_LINK", result)
        assertEquals(probe.getLong("fileInode"), probe.getLong("aliasInode"))
        assertEquals("original", probe.getString("fileContents"))
        assertEquals("original", probe.getString("aliasContents"))
    }

    @Test
    fun symbolicLinksDirectoriesAndFifosAreRejectedWithoutOpeningTheirTargets() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val before = snapshot(file)
        val link = File(root, "link").also { Os.symlink(file.path, it.path) }
        val directory = File(root, "directory").apply { check(mkdir()) }
        val fifo = File(root, "fifo").also { Os.mkfifo(it.path, 0x180) }
        for (special in listOf(link, directory, fifo)) {
            val snapshot = JSONObject(PrivilegedFileSaveEngine.statSnapshot(JSONObject().put("source", special.path).toString()))
            assertCode("UNSUPPORTED_TYPE", snapshot)
            assertCode("UNSUPPORTED_TYPE", save(request(special, before, "edit"), "edit"))
        }
        assertEquals("original", file.readText())
    }

    @Test
    fun ownershipModeAndSelinuxAttributesArePreservedWhileMtimeAdvances() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        Os.chmod(file.path, 0x1a0) // 0640
        check(file.setLastModified(1_600_000_000_000L))
        val before = Os.lstat(file.path)
        val label = runCatching { Os.getxattr(file.path, "security.selinux") }.getOrNull()
        val result = save(request(file, snapshot(file), "edit"), "edit")
        assertOk(result)
        val after = Os.lstat(file.path)
        assertEquals(before.st_uid, after.st_uid)
        assertEquals(before.st_gid, after.st_gid)
        assertEquals(before.st_mode, after.st_mode)
        assertTrue(after.st_mtime > before.st_mtime)
        if (label != null) assertEquals(label.toList(), Os.getxattr(file.path, "security.selinux").toList())
        assertEquals(before.st_mtime, Os.lstat(result.getString("backupPath")).st_mtime)
    }

    @Test
    fun userExtendedAttributesAreCopiedExactly() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val value = byteArrayOf(0, 1, 2, 0, 0x7f, -1)
        try {
            Os.setxattr(file.path, "user.autojs6-save-test", value, 0)
        } catch (error: ErrnoException) {
            assumeTrue("The device forbids application user xattrs", error.errno !in setOf(OsConstants.EOPNOTSUPP, OsConstants.EACCES, OsConstants.EPERM))
            throw error
        }
        val result = save(request(file, snapshot(file), "edit"), "edit")
        assertOk(result)
        assertEquals(value.toList(), Os.getxattr(file.path, "user.autojs6-save-test").toList())
    }

    @Test
    fun failureImmediatelyAfterExchangeRollsBackWithoutDeletingEitherVersion() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val result = JSONObject(PrivilegedFileSaveEngine.saveWithCommitObserver(request(file, snapshot(file), "edit").toString(),
            ByteArrayInputStream("edit".toByteArray())) { throw IOException("injected post-exchange failure") })
        assertCode("IO", result)
        assertEquals("rolled_back", result.getString("commitState"))
        assertFalse(result.getBoolean("partial"))
        assertEquals("original", file.readText())
        assertEquals("edit", File(result.getString("backupPath")).readText())
    }

    @Test
    fun anExternalEditOfTheDisplacedOriginalIsRestoredOnConflict() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val result = JSONObject(PrivilegedFileSaveEngine.saveWithCommitObserver(request(file, snapshot(file), "edit").toString(),
            ByteArrayInputStream("edit".toByteArray())) { backup -> File(backup).writeText("external edit") })
        assertCode("SOURCE_CHANGED", result)
        assertEquals("rolled_back", result.getString("commitState"))
        assertEquals("external edit", file.readText())
        assertEquals("edit", File(result.getString("backupPath")).readText())
    }

    @Test
    fun aHardLinkAddedDuringTheExchangeIsRetainedWhenRollingBack() {
        val probe = hardLinkProbe("concurrent")
        val result = probe.getJSONObject("result")
        assertCode("SOURCE_CHANGED", result)
        assertEquals("rolled_back", result.getString("commitState"))
        assertEquals("original", probe.getString("fileContents"))
        assertEquals("original", probe.getString("aliasContents"))
        assertEquals(probe.getLong("fileInode"), probe.getLong("aliasInode"))
        assertEquals(2L, probe.getLong("links"))
    }

    @Test
    fun anExternalEditOfThePublishedFileIsNeverOverwrittenByAutomaticRollback() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val result = JSONObject(PrivilegedFileSaveEngine.saveWithCommitObserver(request(file, snapshot(file), "edit").toString(),
            ByteArrayInputStream("edit".toByteArray())) { file.writeText("external edit of new inode") })
        assertCode("SAVE_STATE_UNKNOWN", result)
        assertEquals("unknown", result.getString("commitState"))
        assertTrue(result.getBoolean("partial"))
        assertEquals("external edit of new inode", file.readText())
        assertEquals("original", File(result.getString("backupPath")).readText())
    }

    @Test
    fun losingDirectoryPermissionDuringRollbackPreservesRecoveryPathsAndReportsUnknown() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        try {
            val result = JSONObject(PrivilegedFileSaveEngine.saveWithCommitObserver(request(file, snapshot(file), "edit").toString(),
                ByteArrayInputStream("edit".toByteArray())) {
                Os.chmod(root.path, 0x140)
                throw IOException("lost permission before recovery")
            })
            assertCode("SAVE_STATE_UNKNOWN", result)
            assertTrue(result.getBoolean("partial"))
            assertEquals("edit", file.readText())
            assertEquals("original", File(result.getString("backupPath")).readText())
        } finally {
            Os.chmod(root.path, 0x1c0)
        }
    }

    @Test
    fun aWriterHoldingTheOriginalDescriptorContinuesWritingTheRetainedBackup() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        FileOutputStream(file, true).use { externalWriter ->
            val result = save(request(file, snapshot(file), "edit"), "edit")
            assertOk(result)
            externalWriter.write(" plus external write".toByteArray())
            externalWriter.fd.sync()
            assertEquals("edit", file.readText())
            assertEquals("original plus external write", File(result.getString("backupPath")).readText())
        }
    }

    @Test
    fun duplicateTransactionIdIsNotReusedForAnotherSave() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val firstRequest = request(file, snapshot(file), "edit")
        val first = save(firstRequest, "edit")
        assertOk(first)
        val next = request(file, first, "second").put("transactionId", firstRequest.getString("transactionId"))
        val result = save(next, "second")
        assertCode("DESTINATION_EXISTS", result)
        assertEquals("edit", file.readText())
        assertEquals("original", File(first.getString("backupPath")).readText())
    }

    @Test
    fun reconciliationReturnsTheStoredCompletionAfterAllOriginalActionsFinish() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val request = request(file, snapshot(file), "edit")
        val result = save(request, "edit")
        assertOk(result)
        val reconciled = JSONObject(PrivilegedFileSaveEngine.reconcile(request.toString()))
        assertOk(reconciled)
        assertTrue(reconciled.getBoolean("completed"))
        assertEquals(request.getString("transactionId"), reconciled.getString("transactionId"))
        assertEquals(result.toString(), reconciled.getJSONObject("result").toString())
        assertEquals("edit", file.readText())
    }

    @Test
    fun reconciliationCannotClaimCompletionWhileTheSaveLeaseIsHeld() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val request = request(file, snapshot(file), "edit")
        var busyResult: JSONObject? = null
        val result = JSONObject(PrivilegedFileSaveEngine.saveWithCommitObserver(request.toString(),
            ByteArrayInputStream("edit".toByteArray())) {
            busyResult = JSONObject(PrivilegedFileSaveEngine.reconcile(request.toString()))
        })
        assertOk(result)
        assertCode("TRANSACTION_BUSY", requireNotNull(busyResult))
        assertFalse(requireNotNull(busyResult).getBoolean("completed"))
        assertEquals("edit", file.readText())
    }

    @Test
    fun reconciliationFencesAnOldRequestThatHasNotCreatedItsStageYet() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val request = request(file, snapshot(file), "edit")
        val result = JSONObject(PrivilegedFileSaveEngine.reconcile(request.toString()))
        assertOk(result)
        assertTrue(result.getBoolean("completed"))
        assertCode("SAVE_STATE_UNKNOWN", result.getJSONObject("result"))
        assertEquals("original", file.readText())
        assertCode("DESTINATION_EXISTS", save(request, "edit"))
        assertEquals("original", file.readText())
        val marker = JSONObject(File(root, ".autojs6-save-${request.getString("transactionId")}/result.json").readText())
        assertTrue(marker.getBoolean("completed"))
        assertEquals(request.getString("transactionId"), marker.getString("transactionId"))
    }

    @Test
    fun reconciliationFinishesAnAbandonedStageWithoutTrustingAPartialResult() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val request = request(file, snapshot(file), "edit")
        val stage = File(root, ".autojs6-save-${request.getString("transactionId")}").apply { check(mkdir()) }
        File(stage, "original").writeText("partial upload")
        File(stage, "result.pending").writeText("{\"completed\":")
        val reconciled = JSONObject(PrivilegedFileSaveEngine.reconcile(request.toString()))
        assertOk(reconciled)
        assertTrue(reconciled.getBoolean("completed"))
        assertCode("SAVE_STATE_UNKNOWN", reconciled.getJSONObject("result"))
        assertTrue(JSONObject(File(stage, "result.json").readText()).getBoolean("completed"))
        assertEquals("original", file.readText())
        assertEquals("partial upload", File(stage, "original").readText())
    }

    @Test
    fun anotherSaveInTheSameDirectoryCannotRaceAnActiveTransaction() = fixture { root ->
        val first = File(root, "first").apply { writeText("one") }
        val second = File(root, "second").apply { writeText("two") }
        val secondRequest = request(second, snapshot(second), "two edited")
        var concurrentResult: JSONObject? = null
        val result = JSONObject(PrivilegedFileSaveEngine.saveWithCommitObserver(request(first, snapshot(first), "one edited").toString(),
            ByteArrayInputStream("one edited".toByteArray())) {
            concurrentResult = save(secondRequest, "two edited")
        })
        assertOk(result)
        assertCode("TRANSACTION_BUSY", requireNotNull(concurrentResult))
        assertEquals("two", second.readText())
        assertOk(save(secondRequest, "two edited"))
    }

    @Test
    fun expiredAndCancelledSavesDoNotStageOrChangeFiles() = fixture { root ->
        val file = File(root, "file").apply { writeText("original") }
        val before = snapshot(file)
        assertCode("TIMEOUT", save(request(file, before, "edit").put("deadlineMillis", System.currentTimeMillis() - 1), "edit"))
        val response = AtomicReference<JSONObject>()
        val worker = thread {
            Thread.currentThread().interrupt()
            response.set(save(request(file, before, "edit"), "edit"))
        }
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertCode("CANCELLED", response.get())
        assertEquals("original", file.readText())
        assertEquals(setOf("file"), root.list()!!.toSet())
    }

    private fun request(file: File, before: JSONObject, content: String): JSONObject {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return JSONObject().put("source", file.absolutePath).put("expected", before.getJSONObject("identity"))
            .put("expectedDigest", before.getString("digest")).put("contentSize", bytes.size)
            .put("contentDigest", hash(bytes)).put("transactionId", UUID.randomUUID().toString())
    }

    private fun save(request: JSONObject, content: String) = JSONObject(PrivilegedFileSaveEngine.save(request.toString(),
        ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))))

    private fun snapshot(file: File) = JSONObject(PrivilegedFileSaveEngine.statSnapshot(
        JSONObject().put("source", file.absolutePath).toString())).also(::assertOk)

    private fun completingInput(text: String, completed: () -> Unit) = object : ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) {
        private var called = false
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count < 0 && !called) {
                called = true
                completed()
            }
            return count
        }
    }

    private fun recoveryPayload(response: JSONObject) = File(response.getJSONArray("recoveryPaths").getString(0), "original")

    private fun hardLinkProbe(scenario: String): JSONObject {
        // App-domain SELinux policy can forbid link(2) even in our own cache. Exercise the real
        // hard-link behavior under the already-authorized shell identity instead of skipping it.
        assumeTrue("Uses only existing Shizuku authorization", WrappedShizuku.isRunning() && WrappedShizuku.hasPermission())
        val service = checkNotNull(WrappedShizuku.getServiceOrNull())
        val testApk = InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir
        val classPath = "${context.applicationInfo.sourceDir}:$testApk"
        val preferred = if (android.os.Process.is64Bit()) "/system/bin/app_process64" else "/system/bin/app_process32"
        val process = if (File(preferred).exists()) preferred else "/system/bin/app_process"
        val quote = PrivilegedExplorerProtocol::quoteShellArgument
        val nativeLibrary = File(context.applicationInfo.nativeLibraryDir, "libautojs6_file_ops.so").path
        val command = "CLASSPATH=${quote(classPath)} /system/bin/toybox timeout -k 2 -s TERM 35 " +
            "$process /system/bin ${PrivilegedFileSaveHardLinkProbe::class.java.name} ${quote(nativeLibrary)} ${quote(scenario)}"
        val execution = JSONObject(service.execCommand(command))
        assertEquals(execution.toString(), 0, execution.getInt("code"))
        return JSONObject(execution.getString("result")).also(::assertOk)
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun assertOk(response: JSONObject) = assertTrue(response.toString(), response.getBoolean("ok"))
    private fun assertCode(code: String, response: JSONObject) {
        assertFalse(response.toString(), response.getBoolean("ok"))
        assertEquals(response.toString(), code, response.getString("code"))
    }

    private inline fun fixture(action: (File) -> Unit) {
        val root = File(context.cacheDir, "privileged-file-save-${UUID.randomUUID()}")
        check(root.mkdir())
        try {
            action(root)
        } finally {
            removeFixture(root)
        }
    }

    private fun removeFixture(file: File) {
        val stat = Os.lstat(file.path)
        if (OsConstants.S_ISDIR(stat.st_mode)) {
            Os.chmod(file.path, 0x1c0)
            file.listFiles()!!.forEach(::removeFixture)
        }
        Os.remove(file.path)
    }
}
