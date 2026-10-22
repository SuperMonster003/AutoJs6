package org.autojs.autojs.model.explorer

import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.optBoolean
import org.autojs.autojs.core.pref.optString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `session.json` metadata of [PrivilegedEditSessions] as plain values of [SettingsJson] (docs/settings/ROADMAP.md
 * P5.3): metadata that Android's org.json wrote reads the same, the metadata round trips, a new session, a save request
 * and its attempt keep the member order they have always had, a confirmed save is taken into the metadata, and the
 * metadata given to the backup catalog gains the time of its last write.
 *
 * zh-CN: [PrivilegedEditSessions] 的 `session.json` 元数据以 [SettingsJson] 普通值表示 (ROADMAP P5.3): Android org.json 写的
 * 元数据照常读取, 元数据写读往返不变, 新会话, 保存请求及其尝试记录保持一贯的成员顺序, 已确认的保存结果写入元数据, 交给备份目录的
 * 元数据加上最后写入的时间.
 */
class PrivilegedEditSessionsJsonTest {

    private val tx0 = "3b9f1c2e-5d4a-4e6b-8c7d-9e0f1a2b3c4d"
    private val tx1 = "0f8e2a8c-4b1d-4c2e-9a7f-3d5b6c7e8f90"
    private val baseline = "content-6a1b2c3d-4e5f-4a6b-8c9d-0e1f2a3b4c5d"
    private val pending = "content-7b2c3d4e-5f6a-4b7c-9d0e-1f2a3b4c5d6e"
    private val digestA = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
    private val digestB = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"

    // The identity of the original as `PrivilegedFileSaveEngine.Identity.json` gives it.
    private val identity = """{"dev":64768,"ino":1048602,"mode":33188,"size":12,"modified":1761130000000,"changed":1761130000123,"uid":10234,"gid":1023,"links":1}"""
    private val savedIdentity = """{"dev":64768,"ino":1048617,"mode":33188,"size":15,"modified":1761134400000,"changed":1761134400456,"uid":10234,"gid":1023,"links":1}"""

    // The metadata as `create` wrote it, and the request and the attempt of a save, through Android's
    // `JSONObject.toString()` before P5.1 (escaped slashes).
    private val created = """{"version":1,"source":"\/sdcard\/Scripts\/a.js","canonicalSource":"\/storage\/emulated\/0\/Scripts\/a.js","mode":"ROOT",""" +
        """"identity":$identity,"digest":"$digestA","baseline":"$baseline","recovery":[]}"""
    private val request = """{"operation":"save","source":"\/sdcard\/Scripts\/a.js","canonicalSource":"\/storage\/emulated\/0\/Scripts\/a.js",""" +
        """"expected":$identity,"expectedDigest":"$digestA","contentSize":15,"contentDigest":"$digestB","transactionId":"$tx1","deadlineMillis":1761134580000}"""
    private val attempt = request.removeSuffix("}") +
        ""","content":"$pending","charset":"UTF-8","backupPath":"\/storage\/emulated\/0\/Scripts\/.autojs6-save-$tx1\/original"}"""

    // A session with a draft whose save is in flight, after an earlier save left a recovery path. No version of the
    // store wrote a JSON null, but a file may hold one (`draftResolved`); org.json's `has` was true for it.
    private val oldSession = """{"version":1,"source":"\/sdcard\/Scripts\/a.js","canonicalSource":"\/storage\/emulated\/0\/Scripts\/a.js","mode":"ROOT",""" +
        """"identity":$identity,"digest":"$digestA","baseline":"$baseline",""" +
        """"recovery":["\/storage\/emulated\/0\/Scripts\/.autojs6-save-$tx0\/original"],""" +
        """"pending":"$pending","charset":"UTF-8","hadBom":false,"attempt":$attempt,"draftResolved":null}"""

    // The result of a confirmed save as `PrivilegedFileSaveEngine` gives it (successSnapshot plus the transaction members).
    private val saveResult = """{"ok":true,"partial":false,"path":"/storage/emulated/0/Scripts/a.js","identity":$savedIdentity,""" +
        """"digest":"$digestB","size":15,"modified":1761134400000,"transactionId":"$tx1","commitState":"committed","createdAt":1761134400000,""" +
        """"backupPath":"/storage/emulated/0/Scripts/.autojs6-save-$tx1/original","backupIdentity":$identity,"backupDigest":"$digestA",""" +
        """"cleanupPending":true,"directorySync":true,"recoveryPaths":["/storage/emulated/0/Scripts/.autojs6-save-$tx1"]}"""

    @Test
    fun sessionsThatOrgJsonWroteStillRead() {
        val metadata = PrivilegedEditSessions.decodeMetadata(oldSession)
        assertEquals(
            listOf("version", "source", "canonicalSource", "mode", "identity", "digest", "baseline", "recovery", "pending", "charset", "hadBom", "attempt", "draftResolved"),
            metadata.keys.toList(),
        )
        assertEquals("/sdcard/Scripts/a.js", metadata["source"])
        assertEquals("/storage/emulated/0/Scripts/a.js", metadata["canonicalSource"])
        assertEquals(
            mapOf<String, Any?>("dev" to 64768, "ino" to 1048602, "mode" to 33188, "size" to 12, "modified" to 1761130000000L, "changed" to 1761130000123L,
                "uid" to 10234, "gid" to 1023, "links" to 1),
            metadata["identity"],
        )
        assertEquals(listOf("/storage/emulated/0/Scripts/.autojs6-save-$tx0/original"), metadata["recovery"])
        assertEquals(false, metadata["hadBom"])

        @Suppress("UNCHECKED_CAST")
        val attempt = metadata["attempt"] as Map<String, Any?>
        assertEquals(
            listOf("operation", "source", "canonicalSource", "expected", "expectedDigest", "contentSize", "contentDigest", "transactionId", "deadlineMillis",
                "content", "charset", "backupPath"),
            attempt.keys.toList(),
        )
        assertEquals(metadata["identity"], attempt["expected"])
        assertEquals(15, attempt["contentSize"])
        assertEquals(1761134580000L, attempt["deadlineMillis"])
        assertEquals("/storage/emulated/0/Scripts/.autojs6-save-$tx1/original", attempt["backupPath"])

        // A JSON null is a present member (`has` -> `containsKey`) that reads as missing.
        assertTrue(metadata.containsKey("draftResolved"))
        assertNull(metadata["draftResolved"])
        assertFalse(metadata.optBoolean("draftResolved"))
    }

    @Test
    fun sessionsRoundTrip() {
        val metadata = PrivilegedEditSessions.decodeMetadata(oldSession)
        val text = PrivilegedEditSessions.encodeMetadata(metadata)
        assertFalse(text.contains("\\/"))
        assertEquals(oldSession.replace("\\/", "/"), text)
        assertEquals(metadata, PrivilegedEditSessions.decodeMetadata(text))
    }

    @Test
    fun newSessionsTakeTheSnapshotAtTheProcessBoundary() {
        val snapshotText = """{"ok":true,"partial":false,"path":"/storage/emulated/0/Scripts/a.js","identity":$identity,"digest":"$digestA","size":12,"modified":1761130000000}"""
        // On Android `normalizeObject` keeps the member order of the snapshot; the JVM artifact of org.json does not,
        // so the values are compared with it and the member order is taken from the text.
        val snapshot = SettingsJson.parseMap(snapshotText)
        assertEquals(snapshot, SettingsJson.normalizeObject(JSONObject(snapshotText)))

        val metadata = PrivilegedEditSessions.newMetadata("/sdcard/Scripts/a.js", PrivilegedAccessMode.ROOT, snapshot, digestA, baseline)
        assertEquals(created.replace("\\/", "/"), PrivilegedEditSessions.encodeMetadata(metadata))
    }

    @Test
    fun saveRequestsAndAttemptsKeepTheirMemberOrder() {
        val metadata = PrivilegedEditSessions.decodeMetadata(created)
        val request = PrivilegedEditSessions.saveRequest(metadata, "/sdcard/Scripts/a.js", 15, digestB, tx1, 1761134580000L)
        assertEquals(this.request.replace("\\/", "/"), PrivilegedEditSessions.encodeMetadata(request))

        val attempt = PrivilegedEditSessions.saveAttempt(request, pending, "UTF-8", "/storage/emulated/0/Scripts/.autojs6-save-$tx1/original")
        assertEquals(this.attempt.replace("\\/", "/"), PrivilegedEditSessions.encodeMetadata(attempt))
        // The attempt is a copy: the request that is sent keeps its own members.
        assertFalse(request.containsKey("content"))
        attempt["deadlineMillis"] = 0
        assertEquals(1761134580000L, request["deadlineMillis"])

        // The request is an org.json object only at the process boundary.
        val sent = SettingsJson.toOrgObject(request)
        assertEquals(1761134580000L, sent.getLong("deadlineMillis"))
        assertEquals(1761130000000L, sent.getJSONObject("expected").getLong("modified"))
        assertEquals("/storage/emulated/0/Scripts/a.js", sent.getString("canonicalSource"))
    }

    @Test
    fun confirmedSavesAreAdopted() {
        val metadata = PrivilegedEditSessions.decodeMetadata(oldSession)
        PrivilegedEditSessions.adoptResult(metadata, SettingsJson.parseMap(saveResult), pending)
        assertEquals(SettingsJson.parse(savedIdentity), metadata["identity"])
        assertEquals(digestB, metadata["digest"])
        assertEquals(pending, metadata["baseline"])
        assertEquals(
            listOf("/storage/emulated/0/Scripts/.autojs6-save-$tx0/original", "/storage/emulated/0/Scripts/.autojs6-save-$tx1"),
            metadata["recovery"],
        )
        assertEquals("/storage/emulated/0/Scripts/.autojs6-save-$tx1/original", metadata["notice"])
        // Replaced members keep their place; the notice is a new member at the end.
        assertEquals(
            listOf("version", "source", "canonicalSource", "mode", "identity", "digest", "baseline", "recovery", "pending", "charset", "hadBom", "attempt", "draftResolved", "notice"),
            metadata.keys.toList(),
        )

        // A snapshot has neither recovery paths nor a backup path.
        val reconciled = PrivilegedEditSessions.decodeMetadata(created)
        PrivilegedEditSessions.adoptResult(reconciled, SettingsJson.parseMap("""{"ok":true,"path":"/storage/emulated/0/Scripts/a.js","identity":$savedIdentity,"digest":"$digestB"}"""), pending)
        assertEquals(emptyList<Any?>(), reconciled["recovery"])
        assertFalse(reconciled.containsKey("notice"))
    }

    @Test
    fun recoveryPathsAreRecordedOnce() {
        val metadata = PrivilegedEditSessions.decodeMetadata(oldSession)
        val old = "/storage/emulated/0/Scripts/.autojs6-save-$tx0/original"
        PrivilegedEditSessions.recordRecovery(metadata, listOf("/b", old, "/b", "/c"))
        assertEquals(listOf(old, "/b", "/c"), metadata["recovery"])
        assertTrue(PrivilegedEditSessions.encodeMetadata(metadata).contains(""","recovery":["$old","/b","/c"],"pending":"""))

        // A session without the member gains one.
        val bare = SettingsJson.parseMap("""{"version":1}""")
        PrivilegedEditSessions.recordRecovery(bare, listOf("/a"))
        assertEquals("""{"version":1,"recovery":["/a"]}""", PrivilegedEditSessions.encodeMetadata(bare))
    }

    @Test
    fun legacyBackupMetadataGainsTheSessionTime() {
        val metadata = PrivilegedEditSessions.decodeMetadata(oldSession)
        val legacy = PrivilegedEditSessions.legacyBackupMetadata(metadata, 1761134500000L)
        assertSame(metadata, legacy)
        assertEquals("_sessionUpdatedAt", legacy.keys.last())
        assertEquals(1761134500000L, legacy["_sessionUpdatedAt"])
        assertEquals(oldSession.replace("\\/", "/").removeSuffix("}") + ""","_sessionUpdatedAt":1761134500000}""", PrivilegedEditSessions.encodeMetadata(legacy))
        assertEquals("/storage/emulated/0/Scripts/a.js", legacy.optString("canonicalSource", legacy.optString("source")))
    }
}
