package org.autojs.autojs.model.explorer

import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.optString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The records of [PrivilegedBackupManager] (`privileged-edit-backups/<id>.json`) as plain values of [SettingsJson]
 * (docs/settings/ROADMAP.md P5.3): records that Android's org.json wrote read the same, the records round trip, and the
 * record of an attempt, of a settled result, of a scanned backup and of a legacy session keep the member order they have
 * always had.
 *
 * zh-CN: [PrivilegedBackupManager] 的记录 (`privileged-edit-backups/<id>.json`) 以 [SettingsJson] 普通值表示 (ROADMAP P5.3):
 * Android org.json 写的记录照常读取, 记录写读往返不变, 尝试记录, 已结算结果, 扫描到的备份与旧会话导入的记录保持一贯的成员顺序.
 */
class PrivilegedBackupManagerJsonTest {

    private val tx0 = "3b9f1c2e-5d4a-4e6b-8c7d-9e0f1a2b3c4d"
    private val tx1 = "0f8e2a8c-4b1d-4c2e-9a7f-3d5b6c7e8f90"
    private val pending = "content-7b2c3d4e-5f6a-4b7c-9d0e-1f2a3b4c5d6e"
    private val digestA = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
    private val digestB = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"

    // Identities as `PrivilegedFileSaveEngine.Identity.json` gives them: the original, the saved file and the backup.
    private val identity = """{"dev":64768,"ino":1048602,"mode":33188,"size":12,"modified":1761130000000,"changed":1761130000123,"uid":10234,"gid":1023,"links":1}"""
    private val savedIdentity = """{"dev":64768,"ino":1048617,"mode":33188,"size":15,"modified":1761134400000,"changed":1761134400456,"uid":10234,"gid":1023,"links":1}"""
    private val backupIdentity = """{"dev":64768,"ino":1048602,"mode":33188,"size":12,"modified":1761130000000,"changed":1761134400321,"uid":10234,"gid":1023,"links":2}"""

    // The save request that `PrivilegedEditSessions.save` registers, and the result of the save, through Android's
    // `JSONObject.toString()` before P5.1 (escaped slashes).
    private val request = """{"operation":"save","source":"\/sdcard\/Scripts\/a.js","canonicalSource":"\/storage\/emulated\/0\/Scripts\/a.js",""" +
        """"expected":$identity,"expectedDigest":"$digestA","contentSize":15,"contentDigest":"$digestB","transactionId":"$tx1","deadlineMillis":1761134580000}"""
    private val result = """{"ok":true,"partial":false,"path":"\/storage\/emulated\/0\/Scripts\/a.js","identity":$savedIdentity,""" +
        """"digest":"$digestB","size":15,"modified":1761134400000,"transactionId":"$tx1","commitState":"committed","createdAt":1761134400000,""" +
        """"backupPath":"\/storage\/emulated\/0\/Scripts\/.autojs6-save-$tx1\/original","backupIdentity":$backupIdentity,"backupDigest":"$digestA",""" +
        """"cleanupPending":true,"directorySync":true,"recoveryPaths":["\/storage\/emulated\/0\/Scripts\/.autojs6-save-$tx1"]}"""

    // The record of the attempt as `registerAttempt` wrote it, and the record once `recordResult` settled it. No version
    // of the store wrote a JSON null, but a file may hold one (`error`); org.json's `has` was true for it.
    private val attemptRecord = """{"id":"$tx1","source":"\/storage\/emulated\/0\/Scripts\/a.js","requestedSource":"\/sdcard\/Scripts\/a.js",""" +
        """"mode":"ROOT","createdAt":1761134400000,"request":$request}"""
    private val failedRecord = attemptRecord.removeSuffix("}") + ""","error":null}"""
    private val settledRecord = attemptRecord.removeSuffix("}") + ""","result":$result,"settled":true}"""

    // A legacy session (the metadata of `PrivilegedEditSessions`) with a recovery path and an attempt in flight.
    private val legacySession = """{"version":1,"source":"\/sdcard\/Scripts\/a.js","canonicalSource":"\/storage\/emulated\/0\/Scripts\/a.js","mode":"SHIZUKU",""" +
        """"identity":$identity,"digest":"$digestA","baseline":"content-6a1b2c3d-4e5f-4a6b-8c9d-0e1f2a3b4c5d",""" +
        """"recovery":["\/storage\/emulated\/0\/Scripts\/.autojs6-save-$tx0\/original"],"pending":"$pending","charset":"UTF-8","hadBom":false,""" +
        """"attempt":{"operation":"save","transactionId":"$tx1","backupPath":"\/storage\/emulated\/0\/Scripts\/.autojs6-save-$tx1\/original"}}"""

    @Test
    fun recordsThatOrgJsonWroteStillRead() {
        val record = PrivilegedBackupManager.decodeRecord(settledRecord)
        assertEquals(listOf("id", "source", "requestedSource", "mode", "createdAt", "request", "result", "settled"), record.keys.toList())
        assertEquals("/storage/emulated/0/Scripts/a.js", record["source"])
        assertEquals("/sdcard/Scripts/a.js", record["requestedSource"])
        assertEquals(1761134400000L, record["createdAt"])
        assertEquals(true, record["settled"])

        @Suppress("UNCHECKED_CAST")
        val request = record["request"] as Map<String, Any?>
        assertEquals(1761134580000L, request["deadlineMillis"])
        assertEquals(mapOf<String, Any?>("dev" to 64768, "ino" to 1048602, "mode" to 33188, "size" to 12, "modified" to 1761130000000L,
            "changed" to 1761130000123L, "uid" to 10234, "gid" to 1023, "links" to 1), request["expected"])

        @Suppress("UNCHECKED_CAST")
        val result = record["result"] as Map<String, Any?>
        assertEquals("/storage/emulated/0/Scripts/.autojs6-save-$tx1/original", result["backupPath"])
        assertEquals(SettingsJson.parse(backupIdentity), result["backupIdentity"])
        assertEquals(listOf("/storage/emulated/0/Scripts/.autojs6-save-$tx1"), result["recoveryPaths"])
        assertEquals(15, result["size"])

        // A JSON null is a present member (`has` -> `containsKey`) that reads as missing.
        val failed = PrivilegedBackupManager.decodeRecord(failedRecord)
        assertTrue(failed.containsKey("error"))
        assertNull(failed["error"])
        assertEquals("", failed.optString("error"))
    }

    @Test
    fun recordsRoundTrip() {
        for (old in listOf(attemptRecord, failedRecord, settledRecord)) {
            val record = PrivilegedBackupManager.decodeRecord(old)
            val text = PrivilegedBackupManager.encodeRecord(record)
            assertFalse(text.contains("\\/"))
            assertEquals(old.replace("\\/", "/"), text)
            assertEquals(record, PrivilegedBackupManager.decodeRecord(text))
        }
    }

    @Test
    fun attemptRecordsKeepTheirMemberOrder() {
        val request = SettingsJson.parseMap(this.request)
        val record = PrivilegedBackupManager.attemptRecord(tx1, request, "/storage/emulated/0/Scripts/a.js", PrivilegedAccessMode.ROOT, 1761134400000L)
        assertEquals(attemptRecord.replace("\\/", "/"), PrivilegedBackupManager.encodeRecord(record))
        // The record keeps a copy of the request, which the transport may still change.
        request["deadlineMillis"] = 0
        @Suppress("UNCHECKED_CAST")
        val kept = record["request"] as Map<String, Any?>
        assertEquals(1761134580000L, kept["deadlineMillis"])
    }

    @Test
    fun settledRecordsTakeTheResult() {
        // The result is an org.json object at the process boundary; on Android `normalizeObject` keeps its member
        // order, the JVM artifact of org.json does not, so the values are compared with it and the order is taken from the text.
        val result = SettingsJson.parseMap(this.result)
        assertEquals(result, SettingsJson.normalizeObject(JSONObject(this.result)))

        val record = PrivilegedBackupManager.decodeRecord(failedRecord)
        PrivilegedBackupManager.settle(record, result)
        assertEquals(settledRecord.replace("\\/", "/"), PrivilegedBackupManager.encodeRecord(record))

        // A restore to a missing target settles at the restored path, which replaces the source in place.
        val restored = PrivilegedBackupManager.decodeRecord(attemptRecord)
        restored["error"] = "OUTCOME_UNKNOWN"
        PrivilegedBackupManager.settle(restored, SettingsJson.parseMap("""{"ok":true,"path":"/storage/emulated/0/Scripts/b.js","missingTargetRestored":true}"""))
        assertEquals(listOf("id", "source", "requestedSource", "mode", "createdAt", "request", "result", "settled", "removed"), restored.keys.toList())
        assertEquals("/storage/emulated/0/Scripts/b.js", restored["source"])
        assertEquals(true, restored["removed"])

        // A result without a path keeps the source.
        val unchanged = PrivilegedBackupManager.decodeRecord(attemptRecord)
        PrivilegedBackupManager.settle(unchanged, SettingsJson.parseMap("""{"ok":true,"unchanged":true}"""))
        assertEquals("/storage/emulated/0/Scripts/a.js", unchanged["source"])
        assertEquals(true, unchanged["removed"])
    }

    @Test
    fun discoveredRecordsKeepExistingOnes() {
        val entry = SettingsJson.parseMap("""{"transactionId":"$tx0","sourcePath":"/storage/emulated/0/Scripts/a.js","createdAt":1761120000000,"tombstone":false}""")
        val record = PrivilegedBackupManager.discoveredRecord(null, tx0, "/storage/emulated/0/Scripts/a.js", entry, PrivilegedAccessMode.SHIZUKU)
        assertEquals(
            """{"id":"$tx0","source":"/storage/emulated/0/Scripts/a.js","createdAt":1761120000000,"mode":"SHIZUKU"}""",
            PrivilegedBackupManager.encodeRecord(record),
        )

        // A record that exists is kept as it is (its own creation time and mode), only marked removed for a tombstone.
        val tombstone = SettingsJson.parseMap("""{"transactionId":"$tx1","createdAt":1761120000000,"tombstone":true}""")
        val existing = PrivilegedBackupManager.decodeRecord(settledRecord)
        val kept = PrivilegedBackupManager.discoveredRecord(existing, tx1, "/storage/emulated/0/Scripts/a.js", tombstone, PrivilegedAccessMode.SHIZUKU)
        assertSame(existing, kept)
        assertEquals(settledRecord.replace("\\/", "/").removeSuffix("}") + ""","removed":true}""", PrivilegedBackupManager.encodeRecord(kept))
    }

    @Test
    fun legacySessionsBecomeRecords() {
        val session = PrivilegedEditSessions.legacyBackupMetadata(PrivilegedEditSessions.decodeMetadata(legacySession), 1761134500000L)
        val records = PrivilegedBackupManager.legacyRecords(session)
        assertEquals(listOf(tx0, tx1), records.keys.toList())
        for (id in listOf(tx0, tx1)) {
            assertEquals(
                """{"id":"$id","source":"/storage/emulated/0/Scripts/a.js","mode":"SHIZUKU","createdAt":1761134500000,"legacy":true}""",
                PrivilegedBackupManager.encodeRecord(records.getValue(id)),
            )
        }
    }

    @Test
    fun legacySessionsWithoutUsableRecoveryAreSkipped() {
        // An attempt also listed as a recovery path gives one record; other recovery entries give none.
        val repeated = SettingsJson.parseMap(
            """{"source":"/storage/emulated/0/a.js","mode":"ROOT","recovery":["/storage/emulated/0/.autojs6-save-$tx1/original",""" +
                """"/storage/emulated/0/a.js.bak","/storage/emulated/0/.autojs6-save-not-an-id/original",42,null],""" +
                """"attempt":{"transactionId":"$tx1"},"_sessionUpdatedAt":1761134500000}""",
        )
        val records = PrivilegedBackupManager.legacyRecords(repeated)
        assertEquals(listOf(tx1), records.keys.toList())
        assertEquals("/storage/emulated/0/a.js", records.getValue(tx1)["source"])

        // Neither a relative source nor an unknown mode is imported.
        assertTrue(PrivilegedBackupManager.legacyRecords(SettingsJson.parseMap("""{"source":"Scripts/a.js","mode":"ROOT","attempt":{"transactionId":"$tx1"}}""")).isEmpty())
        assertTrue(PrivilegedBackupManager.legacyRecords(SettingsJson.parseMap("""{"source":"/a.js","mode":"SAF","attempt":{"transactionId":"$tx1"}}""")).isEmpty())
        assertTrue(PrivilegedBackupManager.legacyRecords(SettingsJson.parseMap("""{"source":"/a.js","mode":"ROOT"}""")).isEmpty())
    }
}
