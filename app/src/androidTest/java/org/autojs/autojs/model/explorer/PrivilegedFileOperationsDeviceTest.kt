package org.autojs.autojs.model.explorer

import android.content.Context
import android.content.ContextWrapper
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Exercises the installed APK's process entry point and the already-authorized remote service.
 * Root commands run under the app UID by replacing only `su -c` with `/system/bin/sh -c`.
 * These tests never request/revoke authorization or operate outside their own UUID fixtures.
 */
@RunWith(AndroidJUnit4::class)
class PrivilegedFileOperationsDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val shizuku get() = PrivilegedAccessMode.SHIZUKU

    @Test
    fun packagedRootCommandCopiesRenamesAndDeletesQuotedNames() = localFixture { root ->
        val payload = "first line\n第二行\n".toByteArray(Charsets.UTF_8)
        val source = File(root, "source ' \" ${'$'}(printf injected)\n中文.txt").apply { writeBytes(payload) }
        val copied = File(root, "copy ' \" ${'$'}HOME\n.txt")
        val renamed = File(root, "renamed ' \"\n.txt")
        val initial = rootStat(source)
        assertEquals("file", initial.getString("kind"))
        assertEquals(payload.size.toLong(), initial.getLong("size"))

        val copy = rootJson(request("copy", source, copied, initial.getJSONObject("identity")))
        assertOk(copy)
        assertEquals(payload.size.toLong(), copy.getLong("bytes"))
        assertEquals(1, copy.getInt("entries"))
        assertFalse(copy.toString(), copy.getBoolean("cleanupPending"))
        assertArrayEquals(payload, copied.readBytes())

        val copiedStat = rootStat(copied)
        val copiedNativeStat = Os.lstat(copied.absolutePath)
        assertOk(rootJson(request("rename", copied, renamed, copiedStat.getJSONObject("identity"))))
        assertFalse(copied.exists())
        assertArrayEquals(payload, renamed.readBytes())
        val renamedNativeStat = Os.lstat(renamed.absolutePath)
        assertEquals("Same-volume rename must preserve the inode", copiedNativeStat.st_ino, renamedNativeStat.st_ino)
        assertEquals(copiedNativeStat.st_uid, renamedNativeStat.st_uid)
        assertEquals(copiedNativeStat.st_gid, renamedNativeStat.st_gid)
        assertEquals(copiedNativeStat.st_mode, renamedNativeStat.st_mode)

        assertOk(rootJson(request("delete", renamed, expected = rootStat(renamed).getJSONObject("identity"))))
        assertFalse(renamed.exists())
        assertArrayEquals(payload, source.readBytes())
        assertEquals(setOf(source.name), root.list()!!.toSet())
    }

    @Test
    fun packagedRootReadReturnsEveryBinaryByteWithoutAJsonEnvelope() = localFixture { root ->
        val payload = binaryPayload()
        val source = File(root, "binary ' \"\n.bin").apply { writeBytes(payload) }
        val before = rootStat(source)
        val response = rootProcess(request("read", source, expected = before.getJSONObject("identity")))
        assertEquals(processDetails(response, includeStdout = false), 0, response.exitCode)
        assertEquals(payload.size, response.stdout.size)
        assertArrayEquals(payload, response.stdout)
        assertIdentityEquals(before.getJSONObject("identity"), rootStat(source).getJSONObject("identity"))
    }

    @Test
    fun packagedRootFailuresKeepBothSourceAndExistingDestination() = localFixture { root ->
        val source = File(root, "source.txt").apply { writeText("source data") }
        val existing = File(root, "existing.txt").apply { writeText("existing data") }
        val identity = rootStat(source).getJSONObject("identity")
        assertCode("DESTINATION_EXISTS", rootJson(request("copy", source, existing, identity)))
        assertCode("INVALID_PATH", rootJson(request("move", source, source, identity)))
        val stale = JSONObject(identity.toString()).put("ino", identity.getLong("ino") + 1L)
        assertCode("SOURCE_CHANGED", rootJson(request("delete", source, expected = stale)))
        assertCode("TIMEOUT", rootJson(request("delete", source, expected = identity)
            .put("deadlineMillis", System.currentTimeMillis() - 1L)))
        assertEquals("source data", source.readText())
        assertEquals("existing data", existing.readText())
        assertEquals(setOf(source.name, existing.name), root.list()!!.toSet())
    }

    @Test
    fun authorizedShizukuClientCopiesMovesRenamesAndDeletesThroughBinder() = shizukuFixture { service, root ->
        val payload = "Shizuku file operation\n中文\n".toByteArray(Charsets.UTF_8)
        val source = "$root/source ' \" ${'$'}(printf injected).txt"
        val copied = "$root/copied ' \"\n.txt"
        val moved = "$root/target/moved ' \"\n.txt"
        val renamed = "$root/target/renamed ' \"\n.txt"
        remoteWrite(service, source, payload)
        shell(service, "mkdir -- ${quote("$root/target")}")

        val original = PrivilegedFileOperations.stat(context, shizuku, source)
        assertEquals("file", original.getString("kind"))
        assertEquals(payload.size.toLong(), original.getLong("size"))
        val copy = PrivilegedFileOperations.execute(context, shizuku, "copy", source, copied, original.getJSONObject("identity"))
        assertOk(copy)
        assertFalse(copy.toString(), copy.getBoolean("cleanupPending"))
        val copiedIdentity = PrivilegedFileOperations.stat(context, shizuku, copied).getJSONObject("identity")

        assertOk(PrivilegedFileOperations.execute(context, shizuku, "move", copied, moved, copiedIdentity))
        expectClientFailure("NOT_FOUND") { PrivilegedFileOperations.stat(context, shizuku, copied) }
        val movedIdentity = PrivilegedFileOperations.stat(context, shizuku, moved).getJSONObject("identity")
        assertEquals("Same-volume move must preserve the inode", copiedIdentity.getLong("ino"), movedIdentity.getLong("ino"))
        assertOk(PrivilegedFileOperations.execute(context, shizuku, "rename", moved, renamed, movedIdentity))
        expectClientFailure("NOT_FOUND") { PrivilegedFileOperations.stat(context, shizuku, moved) }
        withPreview(renamed) { assertArrayEquals(payload, it.readBytes()) }

        val renamedIdentity = PrivilegedFileOperations.stat(context, shizuku, renamed).getJSONObject("identity")
        assertOk(PrivilegedFileOperations.execute(context, shizuku, "delete", renamed, expected = renamedIdentity))
        expectClientFailure("NOT_FOUND") { PrivilegedFileOperations.stat(context, shizuku, renamed) }
        assertIdentityEquals(original.getJSONObject("identity"), PrivilegedFileOperations.stat(context, shizuku, source).getJSONObject("identity"))
        withPreview(source) { assertArrayEquals(payload, it.readBytes()) }
    }

    @Test
    fun authorizedShizukuMaterializesAnExactPrivateReadOnlySnapshot() = shizukuFixture { service, root ->
        val payload = binaryPayload()
        val source = "$root/binary ' \" 中文.bin"
        remoteWrite(service, source, payload)
        assertFalse("The 0700 remote fixture must require the authorized reader", File(source).canRead())
        val before = PrivilegedFileOperations.stat(context, shizuku, source)

        withPreview(source) { preview ->
            assertEquals(File(source).name, preview.name)
            assertArrayEquals(payload, preview.readBytes())
            assertEquals(payload.size.toLong(), preview.length())
            assertEquals("Preview files must be read only", 0, Os.stat(preview.absolutePath).st_mode and 0x92)
            assertEquals("privileged-file-previews", preview.parentFile!!.parentFile!!.name)
        }
        assertIdentityEquals(before.getJSONObject("identity"), PrivilegedFileOperations.stat(context, shizuku, source).getJSONObject("identity"))
    }

    @Test
    fun authorizedShizukuClientPreservesStructuredFailuresAndSourceContents() = shizukuFixture { service, root ->
        val source = "$root/source.txt"
        val destination = "$root/existing.txt"
        val sourceBytes = "source remains".toByteArray(Charsets.UTF_8)
        val destinationBytes = "existing remains".toByteArray(Charsets.UTF_8)
        remoteWrite(service, source, sourceBytes)
        remoteWrite(service, destination, destinationBytes)
        val identity = PrivilegedFileOperations.stat(context, shizuku, source).getJSONObject("identity")

        val conflict = expectClientFailure("DESTINATION_EXISTS") {
            PrivilegedFileOperations.execute(context, shizuku, "copy", source, destination, identity)
        }
        assertFalse(conflict.partial)
        assertTrue(conflict.recoveryPaths.isEmpty())
        expectClientFailure("INVALID_PATH") {
            PrivilegedFileOperations.execute(context, shizuku, "move", source, source, identity)
        }
        val stale = JSONObject(identity.toString()).put("ino", identity.getLong("ino") + 1L)
        val changed = expectClientFailure("SOURCE_CHANGED") {
            PrivilegedFileOperations.execute(context, shizuku, "delete", source, expected = stale)
        }
        assertFalse(changed.partial)
        assertTrue(changed.recoveryPaths.isEmpty())
        withPreview(source) { assertArrayEquals(sourceBytes, it.readBytes()) }
        withPreview(destination) { assertArrayEquals(destinationBytes, it.readBytes()) }
    }

    private fun request(operation: String, source: File, destination: File? = null, expected: JSONObject? = null) = JSONObject()
        .put("operation", operation).put("source", source.absolutePath)
        .put("deadlineMillis", System.currentTimeMillis() + PROCESS_TIMEOUT_MS)
        .apply {
            destination?.let { put("destination", it.absolutePath) }
            expected?.let { put("expected", it) }
        }

    private fun rootStat(file: File) = rootJson(request("stat", file)).also(::assertOk)

    private fun rootProcess(request: JSONObject): PrivilegedExplorerProcess.Result {
        val command = PrivilegedFileOperations.rootCommand(context, request, PROCESS_TIMEOUT_MS)
        return PrivilegedExplorerProcess.run(listOf("/system/bin/sh", "-c", command[2]), PROCESS_TIMEOUT_MS + 10_000L)
    }

    private fun rootJson(request: JSONObject): JSONObject {
        val response = rootProcess(request)
        assertEquals(processDetails(response), 0, response.exitCode)
        return try {
            JSONObject(response.stdout.toString(Charsets.UTF_8))
        } catch (error: Exception) {
            throw AssertionError("The packaged command must return complete JSON.\n${processDetails(response)}", error)
        }
    }

    private fun processDetails(response: PrivilegedExplorerProcess.Result, includeStdout: Boolean = true) =
        "exit=${response.exitCode}\nstderr=${response.stderr}\nstdout=${if (includeStdout) response.stdout.toString(Charsets.UTF_8) else "${response.stdout.size} bytes"}"

    private fun assertOk(response: JSONObject) = assertTrue(response.toString(), response.getBoolean("ok"))

    private fun assertCode(code: String, response: JSONObject) {
        assertFalse(response.toString(), response.getBoolean("ok"))
        assertEquals(response.toString(), code, response.getString("code"))
        assertFalse(response.toString(), response.getBoolean("partial"))
        assertEquals(response.toString(), 0, response.getJSONArray("recoveryPaths").length())
    }

    private fun assertIdentityEquals(expected: JSONObject, actual: JSONObject) {
        for (key in listOf("dev", "ino", "mode", "size", "modified", "changed")) {
            assertEquals("Source identity changed at $key", expected.getLong(key), actual.getLong(key))
        }
    }

    private inline fun expectClientFailure(code: String, action: () -> Unit): PrivilegedFileOperationException {
        try {
            action()
        } catch (error: PrivilegedFileOperationException) {
            assertEquals(error.toString(), code, error.code)
            assertTrue("Errors should explain the failure", !error.message.isNullOrBlank())
            return error
        }
        throw AssertionError("Expected a structured $code failure")
    }

    private inline fun localFixture(action: (File) -> Unit) {
        val root = File(context.cacheDir, "privileged-file-operations-${UUID.randomUUID()}")
        check(root.mkdir())
        try {
            action(root)
        } finally {
            check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            removeLocalFixture(root)
        }
    }

    private fun removeLocalFixture(file: File) {
        if (OsConstants.S_ISDIR(Os.lstat(file.absolutePath).st_mode)) file.listFiles()!!.forEach(::removeLocalFixture)
        Os.remove(file.absolutePath)
    }

    private inline fun shizukuFixture(action: (IUserService, String) -> Unit) {
        assumeTrue("Shizuku is not already running and authorized", WrappedShizuku.isRunning() && WrappedShizuku.hasPermission())
        val service = checkNotNull(WrappedShizuku.getServiceOrNull()) { "The already-authorized Shizuku service could not be connected" }
        assertTrue("The Shizuku service Binder must be alive", service.asBinder().pingBinder())
        val root = "/data/local/tmp/autojs6-privileged-${UUID.randomUUID()}"
        shell(service, "mkdir -m 700 -- ${quote(root)}")
        try {
            action(service, root)
        } finally {
            check(root.matches(Regex("/data/local/tmp/autojs6-privileged-[a-f0-9-]{36}")))
            // The only recursive remote deletion is this test's successfully-created UUID directory.
            shell(service, "rm -rf -- ${quote(root)}")
        }
    }

    private fun remoteWrite(service: IUserService, path: String, contents: ByteArray) {
        val encoded = Base64.encodeToString(contents, Base64.NO_WRAP)
        val chunks = encoded.chunked(16 * 1024).joinToString("\n") { "printf '%s' ${quote(it)}" }
        shell(service, "{\n$chunks\n} | /system/bin/toybox base64 -d > ${quote(path)}")
    }

    private fun shell(service: IUserService, command: String): String {
        val raw = service.execCommand(command)
        val result = JSONObject(raw)
        assertEquals(raw, 0, result.getInt("code"))
        return result.optString("result")
    }

    private inline fun withPreview(path: String, action: (File) -> Unit) = localFixture { localRoot ->
        // Isolate the client's normal expired-preview cleanup from existing app preview files.
        val preview = PrivilegedFileOperations.materialize(PreviewContext(context, localRoot), shizuku, path)
        val parent = checkNotNull(preview.parentFile)
        assertTrue(parent.name.matches(Regex("[a-f0-9-]{36}")))
        assertEquals(File(localRoot, "privileged-file-previews").canonicalFile, parent.parentFile!!.canonicalFile)
        action(preview)
    }

    private fun binaryPayload() = ByteArray(2 * 64 * 1024 + 523) { (it % 256).toByte() }
    private fun quote(value: String) = PrivilegedExplorerProtocol.quoteShellArgument(value)

    private class PreviewContext(base: Context, private val previewCache: File) : ContextWrapper(base) {
        override fun getCacheDir() = previewCache
    }

    private companion object {
        const val PROCESS_TIMEOUT_MS = 30_000L
    }
}
