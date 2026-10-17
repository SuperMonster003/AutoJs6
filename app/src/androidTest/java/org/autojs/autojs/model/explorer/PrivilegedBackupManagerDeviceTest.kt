package org.autojs.autojs.model.explorer

import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class PrivilegedBackupManagerDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val mode = PrivilegedAccessMode.SHIZUKU

    @Test fun repeatedSavesRetainOnlyTheConfiguredNewestVersions() = fixture { f ->
        f.write("original")
        val uri = PrivilegedEditSessions.create(f.context, mode, f.source)
        PrivilegedBackupManager.setPolicy(f.context, PrivilegedBackupManager.RetentionPolicy(3, 64L * 1024 * 1024))
        for (i in 1..8) PrivilegedEditSessions.save(f.context, uri, "version-$i".toByteArray(), "UTF-8", false)
        PrivilegedBackupManager.awaitIdle()
        val entries = PrivilegedBackupManager.list(f.context)
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.status == "available" })
        assertEquals(setOf("version-5", "version-6", "version-7"), entries.map { PrivilegedBackupManager.view(f.context, it).readText() }.toSet())
        assertEquals("version-8", f.read())
    }

    @Test fun identicalSaveVerifiesTheSourceWithoutCreatingAnotherBackup() = fixture { f ->
        f.write("original")
        val uri = PrivilegedEditSessions.create(f.context, mode, f.source)
        PrivilegedEditSessions.save(f.context, uri, "edited".toByteArray(), "UTF-8", false)
        PrivilegedBackupManager.awaitIdle()
        val before = PrivilegedFileOperations.snapshot(f.context, mode, f.source)
        repeat(4) { PrivilegedEditSessions.save(f.context, uri, "edited".toByteArray(), "UTF-8", false) }
        assertEquals(1, PrivilegedBackupManager.list(f.context).size)
        assertEquals(before.getJSONObject("identity").toString(), PrivilegedFileOperations.snapshot(f.context, mode, f.source).getJSONObject("identity").toString())
        f.write("external")
        assertCode("SOURCE_CHANGED") { PrivilegedEditSessions.save(f.context, uri, "edited".toByteArray(), "UTF-8", false) }
        assertTrue(PrivilegedEditSessions.hasPendingDraft(f.context, uri))
        assertEquals("external", f.read())
    }

    @Test fun deletedOriginalCanBeRestoredAndACompetingNewFileIsKept() = fixture { f ->
        val entry = f.backup()
        f.shell("rm -- ${quote(f.source)}")
        val plan = PrivilegedBackupManager.prepareRestore(f.context, entry)
        assertTrue(plan.targetMissing)
        f.write("new owner's file")
        assertCode("SOURCE_CHANGED") { PrivilegedBackupManager.restore(f.context, plan) }
        assertEquals("new owner's file", f.read())
        f.shell("rm -- ${quote(f.source)}")
        PrivilegedBackupManager.restore(f.context, PrivilegedBackupManager.prepareRestore(f.context, entry))
        assertEquals("original", f.read())
        assertTrue(PrivilegedBackupManager.list(f.context).any { it.id == entry.id })
    }

    @Test fun discoveryFindsBackupsWithoutTheOldSessionCatalog() = fixture { f ->
        val entry = f.backup()
        PrivilegedBackupManager.awaitIdle()
        File(f.context.filesDir, "privileged-edit-backups").listFiles()!!.forEach { check(it.delete()) }
        assertTrue(PrivilegedBackupManager.list(f.context).isEmpty())
        PrivilegedBackupManager.discoverDirectory(f.context, mode, f.remote)
        assertEquals(entry.id, PrivilegedBackupManager.list(f.context).single().id)
        val prepared = PrivilegedBackupManager.prepareDelete(f.context, entry)
        PrivilegedBackupManager.delete(f.context, prepared)
        assertTrue(PrivilegedBackupManager.list(f.context).isEmpty())
        PrivilegedBackupManager.discoverDirectory(f.context, mode, f.remote)
        assertTrue("Small replay fences are not presented as restorable payloads", PrivilegedBackupManager.list(f.context).isEmpty())
    }

    @Test fun cleanupProtectsAnAlteredNewestBackupAndTheLastKnownGoodVersion() = fixture { f ->
        f.write("original")
        val uri = PrivilegedEditSessions.create(f.context, mode, f.source)
        PrivilegedEditSessions.save(f.context, uri, "one".toByteArray(), "UTF-8", false)
        PrivilegedEditSessions.save(f.context, uri, "two".toByteArray(), "UTF-8", false)
        PrivilegedBackupManager.awaitIdle()
        val newest = PrivilegedBackupManager.list(f.context).first()
        f.write("external altered recovery", newest.backupPath)
        PrivilegedBackupManager.setPolicy(f.context, PrivilegedBackupManager.RetentionPolicy(1, 1))
        PrivilegedBackupManager.cleanup(f.context)
        val kept = PrivilegedBackupManager.list(f.context)
        assertEquals(2, kept.size)
        assertTrue(kept.any { it.status == "available" })
        assertTrue(kept.any { it.status == "changed" })
        val confirmed = PrivilegedBackupManager.prepareDelete(f.context, kept.first { it.status == "changed" })
        f.write("changed after confirmation", confirmed.backupPath)
        assertCode("SOURCE_CHANGED") { PrivilegedBackupManager.delete(f.context, confirmed) }
        assertEquals("two", f.read())
    }

    @Test fun anAbsentPayloadLeavesOnlyAnUnlistedRetirementFence() = fixture { f ->
        val entry = f.backup()
        f.shell("rm -- ${quote(entry.backupPath)}")
        assertTrue(PrivilegedBackupManager.list(f.context).isEmpty())
        PrivilegedBackupManager.cleanup(f.context)
        assertEquals("edited", f.read())
    }

    private fun fixture(test: (Fixture) -> Unit) {
        assumeTrue(WrappedShizuku.isRunning() && WrappedShizuku.hasPermission())
        val f = Fixture(checkNotNull(WrappedShizuku.getServiceOrNull()))
        f.shell("mkdir -m 700 -- ${quote(f.remote)}")
        try { test(f) } finally {
            PrivilegedBackupManager.awaitIdle()
            check(f.remote.matches(Regex("/data/local/tmp/autojs6-backup-manager-[a-f0-9-]{36}")))
            f.shell("rm -rf -- ${quote(f.remote)}")
            f.preferenceNames.forEach { context.deleteSharedPreferences(it) }
            check(f.local.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            check(f.local.deleteRecursively())
        }
    }

    private inner class Fixture(val service: IUserService) {
        private val token = UUID.randomUUID().toString()
        val local = File(this@PrivilegedBackupManagerDeviceTest.context.cacheDir, "privileged-backup-manager-$token").apply { check(mkdir()) }
        val remote = "/data/local/tmp/autojs6-backup-manager-$token"
        val source = "$remote/source ' 中文.txt"
        val preferenceNames = mutableSetOf<String>()
        val context = object : ContextWrapper(this@PrivilegedBackupManagerDeviceTest.context) {
            private val privateFiles = File(local, "files").apply { check(mkdir()) }
            private val privateCache = File(local, "cache").apply { check(mkdir()) }
            override fun getFilesDir() = privateFiles
            override fun getCacheDir() = privateCache
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("backup-manager-$token-$name".also(preferenceNames::add), mode)
        }
        fun backup(): PrivilegedBackupManager.BackupEntry {
            write("original")
            val uri = PrivilegedEditSessions.create(context, mode, source)
            PrivilegedEditSessions.save(context, uri, "edited".toByteArray(), "UTF-8", false)
            PrivilegedBackupManager.awaitIdle()
            return PrivilegedBackupManager.list(context).single()
        }
        fun shell(command: String): String {
            val result = JSONObject(service.execCommand(command))
            assertEquals(result.toString(), 0, result.getInt("code"))
            return result.optString("result")
        }
        fun write(text: String, path: String = source) {
            shell("printf '%s' ${quote(Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))} | /system/bin/toybox base64 -d > ${quote(path)}")
        }
        fun read() = shell("cat -- ${quote(source)}").trimEnd('\n')
    }

    private fun assertCode(code: String, work: () -> Unit) {
        try { work(); fail("Expected $code") } catch (error: PrivilegedFileOperationException) { assertEquals(error.toString(), code, error.code) }
    }
    private fun quote(value: String) = PrivilegedExplorerProtocol.quoteShellArgument(value)
}
