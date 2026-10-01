package org.autojs.autojs.runtime.api.installer

import org.autojs.autojs.core.plugin.installer.InstallerJson
import org.autojs.plugin.installer.api.InstallerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class InstallerSourceDeletionTest {

    @Test
    fun `batch failure keeps its source while successful items are deleted`() {
        val completion = InstallerJson.decodeCompletion("""{"results":[{"ok":true},{"ok":false,"error":{"code":"INSTALL_FAILED","message":"bad"}}]}""")
        val first = FakeSource(0)
        val second = FakeSource(1)
        val result = InstallerSourceDeletion.apply(completion, listOf(first, second), true)
        assertEquals(1, first.deletes)
        assertEquals(0, second.deletes)
        assertEquals(true, result.results[0].sourceDeleted)
        assertEquals(false, result.results[1].sourceDeleted)
        val decoded = InstallerJson.decodeCompletion(result.json)
        assertEquals(result.results.map { it.sourceDeleted }, decoded.results.map { it.sourceDeleted })
    }

    @Test
    fun `sources are untouched unless deletion was explicitly requested`() {
        val completion = success()
        val source = FakeSource(0)
        assertSame(completion, InstallerSourceDeletion.apply(completion, listOf(source), false))
        assertEquals(0, source.deletes)
    }

    @Test
    fun `content URI ownership is never claimed and failures do not alter install success`() {
        val source = FakeSource(0, isOwnedFile = false)
        val result = InstallerSourceDeletion.apply(success(), listOf(source), true).single
        assertEquals(0, source.deletes)
        assertTrue(result.ok)
        assertEquals(false, result.sourceDeleted)
        assertTrue(result.notes.single().contains("Content URI"))
    }

    @Test
    fun `replacement or deletion exception leaves successful result with an explanatory note`() {
        val replaced = FakeSource(0, allowed = false)
        val denied = FakeSource(0, error = SecurityException("denied"))
        val result = InstallerSourceDeletion.apply(success(), listOf(replaced, denied), true).single
        assertEquals(1, replaced.deletes)
        assertEquals(1, denied.deletes)
        assertTrue(result.ok)
        assertEquals(false, result.sourceDeleted)
        assertFalse(result.notes.isEmpty())
        assertEquals(result.notes, InstallerJson.decodeCompletion(result.json).single.notes)
    }

    @Test
    fun `split set only reports sourceDeleted after every file was deleted`() {
        val first = FakeSource(0)
        val second = FakeSource(0)
        val result = InstallerSourceDeletion.apply(success(), listOf(first, second), true).single
        assertEquals(true, result.sourceDeleted)
        assertEquals(1, first.deletes)
        assertEquals(1, second.deletes)
    }

    @Test
    fun `file shared by a successful and failed batch item is kept for both`() {
        val completion = InstallerJson.decodeCompletion("""{"results":[{"ok":true},{"ok":false}]}""")
        val first = FakeSource(0, ownershipKey = "/same.apk")
        val second = FakeSource(1, ownershipKey = "/same.apk")
        val result = InstallerSourceDeletion.apply(completion, listOf(first, second), true)
        assertEquals(0, first.deletes)
        assertEquals(0, second.deletes)
        assertTrue(result.results.all { it.sourceDeleted == false })
        assertTrue(result.results.first().notes.single().contains("unsuccessful"))
    }

    @Test
    fun `file shared with an unprocessed batch item is retained`() {
        val first = FakeSource(0, ownershipKey = "/same.apk")
        val unprocessed = FakeSource(1, ownershipKey = "/same.apk")
        val result = InstallerSourceDeletion.apply(success(), listOf(first, unprocessed), true)
        assertEquals(0, first.deletes)
        assertEquals(0, unprocessed.deletes)
        assertEquals(false, result.single.sourceDeleted)
    }

    @Test
    fun `successful items sharing a file share its single deletion result`() {
        val completion = InstallerJson.decodeCompletion("""{"results":[{"ok":true},{"ok":true}]}""")
        val first = FakeSource(0, ownershipKey = "/same.apk")
        val second = FakeSource(1, ownershipKey = "/same.apk")
        val result = InstallerSourceDeletion.apply(completion, listOf(first, second), true)
        assertEquals(1, first.deletes)
        assertEquals(0, second.deletes)
        assertTrue(result.results.all { it.sourceDeleted == true })
    }

    @Test
    fun `cleanup notes on a maximum size success still reach script values without dropping plugin fields`() {
        val prefix = "{\"ok\":true,\"versionName\":\""
        val suffix = "\",\"notes\":[\"Plugin note\"]}"
        val version = "v".repeat(InstallerContract.MAX_JSON_BYTES - prefix.length - suffix.length)
        val wire = prefix + version + suffix
        assertEquals(InstallerContract.MAX_JSON_BYTES, wire.toByteArray(Charsets.UTF_8).size)
        val completion = InstallerJson.decodeCompletion(wire)
        val source = FakeSource(0, isOwnedFile = false)
        val result = InstallerSourceDeletion.apply(completion, listOf(source), true)
        assertEquals(wire, result.single.json)
        assertEquals(wire, result.json)
        val plain = InstallerScriptValues.completion(result, false) as Map<*, *>
        assertEquals(true, plain["ok"])
        assertEquals(false, plain["sourceDeleted"])
        assertEquals(version, plain["versionName"])
        val notes = plain["notes"] as List<*>
        assertTrue(notes.contains("Plugin note"))
        assertTrue(notes.any { it.toString().contains("Content URI") })
    }

    @Test
    fun `cleanup preserves the batch wire budget while all typed item notes reach script values`() {
        val prefix = "{\"results\":[{\"ok\":true,\"versionName\":\""
        val middle = "\"},{\"ok\":true,\"versionName\":\""
        val suffix = "\"}]}"
        val budget = InstallerContract.MAX_JSON_BYTES - prefix.length - middle.length - suffix.length
        val firstVersion = "a".repeat(budget / 2)
        val secondVersion = "b".repeat(budget - budget / 2)
        val wire = prefix + firstVersion + middle + secondVersion + suffix
        assertEquals(InstallerContract.MAX_JSON_BYTES, wire.toByteArray(Charsets.UTF_8).size)
        val completion = InstallerJson.decodeCompletion(wire)
        val result = InstallerSourceDeletion.apply(completion, listOf(
            FakeSource(0, isOwnedFile = false), FakeSource(1, allowed = false),
        ), true)
        assertEquals(wire, result.json)
        assertTrue(result.results.all { it.json.toByteArray(Charsets.UTF_8).size <= InstallerContract.MAX_JSON_BYTES })
        val plain = InstallerScriptValues.completion(result, true) as List<*>
        val first = plain[0] as Map<*, *>
        val second = plain[1] as Map<*, *>
        assertEquals(firstVersion, first["versionName"])
        assertEquals(secondVersion, second["versionName"])
        for (item in listOf(first, second)) {
            assertEquals(true, item["ok"])
            assertEquals(false, item["sourceDeleted"])
            assertTrue((item["notes"] as List<*>).isNotEmpty())
        }
    }

    private fun success() = InstallerJson.decodeCompletion("""{"ok":true,"sourceDeleted":false}""")

    private class FakeSource(
        override val item: Int,
        override val isOwnedFile: Boolean = true,
        override val ownershipKey: String? = UUID.randomUUID().toString(),
        private val allowed: Boolean = true,
        private val error: Throwable? = null,
    ) : InstallerSourceDeletion.Source {
        var deletes = 0
        override fun deleteIfUnchanged(): Boolean {
            deletes++
            error?.let { throw it }
            return allowed
        }
    }
}
