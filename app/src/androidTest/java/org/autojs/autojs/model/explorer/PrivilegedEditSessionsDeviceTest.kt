package org.autojs.autojs.model.explorer

import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PrivilegedEditSessionsDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val mode = PrivilegedAccessMode.SHIZUKU

    @Before
    fun removeFixturesLeftByAnInterruptedTestRun() {
        PrivilegedBackupManager.awaitIdle()
        if (!WrappedShizuku.isRunning() || !WrappedShizuku.hasPermission()) return
        val service = WrappedShizuku.getServiceOrNull() ?: return
        val sessions = File(context.filesDir, "privileged-edit-sessions")
        sessions.listFiles().orEmpty().forEach { directory ->
            val state = runCatching { JSONObject(AtomicFile(File(directory, "session.json")).openRead().bufferedReader().use { it.readText() }) }.getOrNull()
                ?: return@forEach
            val parent = File(state.optString("source")).parent.orEmpty()
            if (parent.matches(Regex("/data/local/tmp/autojs6-edit-save-[a-f0-9-]{36}"))) {
                assertTrue(directory.name.matches(Regex("[a-f0-9-]{36}")))
                val response = JSONObject(service.execCommand("rm -rf -- ${quote(parent)}"))
                assertEquals(response.toString(), 0, response.getInt("code"))
                removeFixture(directory)
            }
        }
        context.cacheDir.listFiles().orEmpty().filter {
            it.name.matches(Regex("privileged-edit-(session-test|process)-[a-f0-9-]{36}"))
        }.forEach(::removeFixture)
        cleanupCatalog()
    }

    @Test
    fun packagedRootSaveStreamsBytesAndKeepsTheOriginal() {
        val root = File(context.cacheDir, "privileged-edit-process-${UUID.randomUUID()}").apply { check(mkdir()) }
        try {
            val source = File(root, "source ' 中文 ${'$'}HOME.txt").apply { writeText("original") }
            val snapshot = rootCall(JSONObject().put("operation", "snapshot").put("source", source.path))
            val bytes = ByteArray(128 * 1024 + 341) { (it % 251).toByte() }
            val result = rootCall(saveRequest(source.path, snapshot, bytes), bytes)
            assertTrue(result.toString(), result.getBoolean("ok"))
            assertArrayEquals(bytes, source.readBytes())
            assertEquals("original", File(result.getString("backupPath")).readText())
            val stale = rootCall(saveRequest(source.path, snapshot, "stale".toByteArray()), "stale".toByteArray())
            assertEquals(stale.toString(), "SOURCE_CHANGED", stale.getString("code"))
            assertArrayEquals(bytes, source.readBytes())
        } finally { removeFixture(root) }
    }

    @Test
    fun authorizedSessionWritesTheOriginalTwiceAndProviderCannotBypassSaveChecks() = fixture { fixture ->
        val initial = "initial\n中文".toByteArray()
        fixture.write(initial)
        val uri = fixture.open()
        assertFalse(File(fixture.source).canRead())
        context.contentResolver.query(uri, null, null, null, null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(File(fixture.source).name, cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
            assertEquals(initial.size.toLong(), cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)))
        }
        assertArrayEquals(initial, read(uri))
        try {
            context.contentResolver.openOutputStream(uri, "rwt")?.close()
            fail("The private provider must never expose a writable descriptor")
        } catch (_: java.io.FileNotFoundException) { }
        val bytes = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + "saved\n新内容".toByteArray()
        PrivilegedEditSessions.save(context, uri, bytes, "UTF-8", true)
        assertArrayEquals(bytes, fixture.read())
        assertArrayEquals(bytes, read(uri))
        assertFalse(PrivilegedEditSessions.hasPendingDraft(context, uri))
        assertNotNull(PrivilegedEditSessions.consumeSaveNotice(context, uri))
        assertNull(PrivilegedEditSessions.consumeSaveNotice(context, uri))
        val second = "second save".toByteArray()
        PrivilegedEditSessions.save(context, uri, second, "UTF-8", false)
        assertArrayEquals(second, fixture.read())
        assertFalse(PrivilegedEditSessions.hasPendingDraft(context, uri))
        val metadata = metadata(uri)
        val recovery = metadata.getJSONArray("recovery")
        assertTrue(recovery.length() >= 2)
    }

    @Test
    fun secondWindowAndExternalChangesKeepTheirOriginalBaselineAndRecoverableDrafts() = fixture { fixture ->
        fixture.write("original".toByteArray())
        val first = fixture.open()
        val second = fixture.open()
        assertNotEquals(first, second)
        PrivilegedEditSessions.save(context, first, "first window".toByteArray(), "UTF-8", false)
        val legacy = "第二个窗口的编辑".toByteArray(charset("GB18030"))
        expectFailure("SOURCE_CHANGED") { PrivilegedEditSessions.save(context, second, legacy, "GB18030", false) }
        assertEquals("first window", fixture.read().toString(Charsets.UTF_8))
        assertArrayEquals(legacy, read(second))
        assertEquals(charset("GB18030"), PrivilegedEditSessions.pendingCharset(context, second))
        assertTrue(PrivilegedEditSessions.hasPendingDraft(context, second))
        PrivilegedEditSessions.detach(context, second)
        val recovered = fixture.open()
        assertEquals("Reopening after a closed/crashed editor must restore its draft", second, recovered)
        fixture.write("external writer".toByteArray())
        expectFailure("SOURCE_CHANGED") { PrivilegedEditSessions.save(context, recovered, legacy, "GB18030", false) }
        repeat(3) {
            expectFailure("SOURCE_CHANGED") { PrivilegedEditSessions.save(context, recovered, legacy, "GB18030", false) }
        }
        assertEquals("Repeated failures retain only the baseline and latest pending draft", 2,
            directory(recovered).listFiles()!!.count { it.name.startsWith("content-") })
        assertEquals("external writer", fixture.read().toString(Charsets.UTF_8))
        PrivilegedEditSessions.discardDraft(context, recovered)
        assertFalse(PrivilegedEditSessions.hasPendingDraft(context, recovered))
    }

    @Test
    fun disappearanceDoesNotRecreateTheOriginalOrLoseTheDraft() = fixture { fixture ->
        fixture.write("original".toByteArray())
        val uri = fixture.open()
        fixture.shell("mv -- ${quote(fixture.source)} ${quote("${fixture.root}/moved.txt")}")
        val draft = "edits after removal".toByteArray()
        expectFailure("NOT_FOUND") { PrivilegedEditSessions.save(context, uri, draft, "UTF-8", false) }
        assertTrue(PrivilegedEditSessions.hasPendingDraft(context, uri))
        assertArrayEquals(draft, read(uri))
        assertEquals("original", fixture.shell("cat -- ${quote("${fixture.root}/moved.txt")}").trim())
        assertEquals("missing", fixture.shell("if [ -e ${quote(fixture.source)} ]; then printf exists; else printf missing; fi").trim())
    }

    @Test
    fun lostSaveAcknowledgementIsReconciledBeforeTheNextExplicitSave() = fixture { fixture ->
        fixture.write("original".toByteArray())
        val uri = fixture.open()
        val lost = "saved without acknowledgement".toByteArray()
        val before = PrivilegedFileOperations.snapshot(context, mode, fixture.source)
        val request = saveRequest(fixture.source, before, lost)
        val directory = directory(uri)
        val content = File(directory, "content-${UUID.randomUUID()}").apply { writeBytes(lost) }
        val committed = PrivilegedFileOperations.save(context, mode, request, content)
        assertTrue(committed.getBoolean("ok"))
        // Simulate process death between the remote commit and local acknowledgement persistence.
        val metadata = metadata(uri).put("pending", content.name).put("charset", "UTF-8")
            .put("attempt", JSONObject(request.toString()).put("content", content.name).put("charset", "UTF-8")
                .put("backupPath", committed.getString("backupPath")).put("deadlineMillis", 0))
        writeMetadata(uri, metadata)
        val next = "next explicit save".toByteArray()
        PrivilegedEditSessions.save(context, uri, next, "UTF-8", false)
        assertArrayEquals(next, fixture.read())
        assertFalse(PrivilegedEditSessions.hasPendingDraft(context, uri))
        assertFalse(metadata(uri).has("attempt"))
    }

    @Test
    fun aSaveThatNeverStartedCanBeRetiredBeforeSavingTheLatestEdit() = fixture { fixture ->
        fixture.write("original".toByteArray())
        val uri = fixture.open()
        val metadata = metadata(uri)
        val attempt = saveRequest(fixture.source, PrivilegedFileOperations.snapshot(context, mode, fixture.source), "first attempt".toByteArray())
            .put("content", metadata.getString("baseline"))
            .apply { put("backupPath", "${fixture.root}/.autojs6-save-${getString("transactionId")}/original") }
        writeMetadata(uri, metadata.put("attempt", attempt))
        val latest = "newer edits".toByteArray()
        PrivilegedEditSessions.save(context, uri, latest, "UTF-8", false)
        assertArrayEquals(latest, fixture.read())
        assertArrayEquals(latest, read(uri))
        assertFalse(metadata(uri).has("attempt"))
        assertFalse(PrivilegedEditSessions.hasPendingDraft(context, uri))
        // The old request cannot run after reconciliation, even if its delayed sender resumes.
        val oldContent = File(directory(uri), "late-${UUID.randomUUID()}").apply { writeText("first attempt") }
        try {
            expectFailure("SOURCE_CHANGED") { PrivilegedFileOperations.save(context, mode, attempt, oldContent) }
        } finally { oldContent.delete() }
    }

    private fun saveRequest(source: String, snapshot: JSONObject, bytes: ByteArray) = JSONObject()
        .put("operation", "save").put("source", source).put("expected", snapshot.getJSONObject("identity"))
        .put("expectedDigest", snapshot.getString("digest")).put("contentSize", bytes.size)
        .put("contentDigest", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        .put("transactionId", UUID.randomUUID().toString()).put("deadlineMillis", System.currentTimeMillis() + 30_000)

    private fun rootCall(request: JSONObject, bytes: ByteArray? = null): JSONObject {
        if (!request.has("deadlineMillis")) request.put("deadlineMillis", System.currentTimeMillis() + 30_000)
        val command = PrivilegedFileOperations.rootCommand(context, request, 30_000)
        val result = PrivilegedExplorerProcess.run(listOf("/system/bin/sh", "-c", command[2]), 40_000, bytes?.let(::ByteArrayInputStream))
        assertEquals(result.stderr, 0, result.exitCode)
        return JSONObject(result.stdout.toString(Charsets.UTF_8))
    }

    private fun read(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    private fun directory(uri: Uri) = File(context.filesDir, "privileged-edit-sessions/${uri.pathSegments.single()}")
    private fun metadata(uri: Uri) = JSONObject(AtomicFile(File(directory(uri), "session.json")).openRead().bufferedReader().use { it.readText() })
    private fun writeMetadata(uri: Uri, value: JSONObject) {
        val atomic = AtomicFile(File(directory(uri), "session.json"))
        val output = atomic.startWrite()
        output.write(value.toString().toByteArray())
        atomic.finishWrite(output)
    }

    private fun expectFailure(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") } catch (error: PrivilegedFileOperationException) { assertEquals(error.toString(), code, error.code) }
    }

    private fun fixture(action: (Fixture) -> Unit) {
        assumeTrue("Uses only existing Shizuku authorization", WrappedShizuku.isRunning() && WrappedShizuku.hasPermission())
        val fixture = Fixture(checkNotNull(WrappedShizuku.getServiceOrNull()))
        fixture.shell("mkdir -m 700 -- ${quote(fixture.root)}")
        var primary: Throwable? = null
        try { action(fixture) } catch (error: Throwable) { primary = error; throw error } finally {
            PrivilegedBackupManager.awaitIdle()
            val cleanupErrors = listOf<() -> Unit>(
                { fixture.uris.forEach { uri -> PrivilegedEditSessions.detach(context, uri) } },
                { fixture.uris.distinct().forEach { uri -> removeFixture(directory(uri)) } },
                { removeFixture(fixture.cache) },
                { cleanupCatalog(fixture.root) },
                {
                    check(fixture.root.matches(Regex("/data/local/tmp/autojs6-edit-save-[a-f0-9-]{36}")))
                    fixture.shell("rm -rf -- ${quote(fixture.root)}")
                },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if (primary != null) cleanupErrors.forEach(primary::addSuppressed)
            else if (cleanupErrors.isNotEmpty()) throw cleanupErrors.first().apply { cleanupErrors.drop(1).forEach(::addSuppressed) }
        }
    }

    private fun cleanupCatalog(root: String? = null) {
        File(context.filesDir, "privileged-edit-backups").listFiles().orEmpty().filter { it.extension == "json" }.forEach { file ->
            val record = runCatching { AtomicFile(file).openRead().bufferedReader().use { JSONObject(it.readText()) } }.getOrNull() ?: return@forEach
            val parent = File(record.optString("source")).parent.orEmpty()
            if (parent.matches(Regex("/data/local/tmp/autojs6-edit-save-[a-f0-9-]{36}")) && (root == null || parent == root)) AtomicFile(file).delete()
        }
    }

    private inner class Fixture(val service: IUserService) {
        val root = "/data/local/tmp/autojs6-edit-save-${UUID.randomUUID()}"
        val source = "$root/source ' 中文.txt"
        val uris = mutableListOf<Uri>()
        val cache = File(context.cacheDir, "privileged-edit-session-test-${UUID.randomUUID()}").apply { check(mkdir()) }
        private val previewContext = object : ContextWrapper(context) { override fun getCacheDir() = cache }
        fun open() = PrivilegedEditSessions.create(previewContext, mode, source).also { uris += it; PrivilegedEditSessions.attach(context, it) }
        fun write(bytes: ByteArray) { shell("printf '%s' ${quote(Base64.encodeToString(bytes, Base64.NO_WRAP))} | /system/bin/toybox base64 -d > ${quote(source)}") }
        fun read(): ByteArray = Base64.decode(shell("/system/bin/toybox base64 ${quote(source)}"), Base64.DEFAULT)
        fun shell(command: String): String {
            val result = JSONObject(service.execCommand(command))
            assertEquals(result.toString(), 0, result.getInt("code"))
            return result.optString("result")
        }
    }

    private fun removeFixture(file: File) {
        val privateRoots = listOf(context.cacheDir.canonicalPath, File(context.filesDir, "privileged-edit-sessions").canonicalPath)
        // Android may expose /data/user/0 and /data/data aliases. Resolve ancestors without
        // following a leaf symlink before enforcing the fixture-only cleanup boundary.
        val resolved = File(checkNotNull(file.parentFile).canonicalFile, file.name).path
        check(privateRoots.any { resolved.startsWith("$it/") }) { "Not a private fixture: $resolved" }
        if (OsConstants.S_ISDIR(Os.lstat(file.path).st_mode)) file.listFiles()!!.forEach(::removeFixture)
        Os.remove(file.path)
    }

    private fun quote(value: String) = PrivilegedExplorerProtocol.quoteShellArgument(value)
}
