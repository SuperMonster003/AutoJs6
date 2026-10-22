package org.autojs.autojs.model.explorer

import org.autojs.autojs.core.pref.SettingsJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The entries of [PrivilegedFileOperationJournal] as plain values of [SettingsJson] (docs/settings/ROADMAP.md P5.3):
 * an entry that Android's org.json wrote reads the same, the entries round trip, and a preserved entry gains the error
 * code, message and recovery paths of the failed operation.
 *
 * zh-CN: [PrivilegedFileOperationJournal] 的记录以 [SettingsJson] 普通值表示 (ROADMAP P5.3): Android org.json 写的记录照常读取,
 * 记录写读往返不变, 保留的记录加上失败操作的错误码, 消息与恢复路径.
 */
class PrivilegedFileOperationJournalJsonTest {

    // An entry as the journal wrote it before P5.3: the request object of `PrivilegedFileOperations.executePrepared`
    // through Android's `JSONObject.toString()` (escaped slashes), then preserved with an error.
    private val oldEntry = """{"operation":"move","source":"\/storage\/emulated\/0\/a.js","deadlineMillis":1761134400000,""" +
        """"expected":{"size":12,"modified":1761130000000,"type":"file"},"nativeLibrary":"\/data\/app\/lib\/arm64\/libautojs6_file_ops.so",""" +
        """"apkPath":"\/data\/app\/base.apk","client64Bit":true,"destination":"\/storage\/emulated\/0\/b",""" +
        """"code":"OUTCOME_UNKNOWN","message":"connection ended","recoveryPaths":["\/storage\/emulated\/0\/b\/.autojs6-move"]}"""

    @Test
    fun entriesThatOrgJsonWroteStillRead() {
        val entry = PrivilegedFileOperationJournal.decode(oldEntry)
        assertEquals(
            listOf("operation", "source", "deadlineMillis", "expected", "nativeLibrary", "apkPath", "client64Bit", "destination", "code", "message", "recoveryPaths"),
            entry.keys.toList(),
        )
        assertEquals("/storage/emulated/0/a.js", entry["source"])
        assertEquals(1761134400000L, entry["deadlineMillis"])
        assertEquals(mapOf("size" to 12, "modified" to 1761130000000L, "type" to "file"), entry["expected"])
        assertEquals(true, entry["client64Bit"])
        assertEquals(listOf("/storage/emulated/0/b/.autojs6-move"), entry["recoveryPaths"])
    }

    @Test
    fun entriesRoundTrip() {
        val entry = PrivilegedFileOperationJournal.decode(oldEntry)
        val text = PrivilegedFileOperationJournal.encode(entry)
        assertFalse(text.contains("\\/"))
        assertEquals(entry, PrivilegedFileOperationJournal.decode(text))
        assertEquals(oldEntry.replace("\\/", "/"), text)
    }

    @Test
    fun requestsAtTheProcessBoundaryBecomeEntries() {
        // The request is an org.json object at the process boundary (the JVM artifact keeps no key order).
        val request = JSONObject().put("operation", "copy").put("source", "/a").put("deadlineMillis", 1761134400000L)
        val entry = SettingsJson.normalizeObject(request)
        assertEquals(mapOf("operation" to "copy", "source" to "/a", "deadlineMillis" to 1761134400000L), entry)
        assertEquals(entry, PrivilegedFileOperationJournal.decode(PrivilegedFileOperationJournal.encode(entry)))
    }

    @Test
    fun preservedEntriesKeepTheErrorAndTheRecoveryPaths() {
        val entry = PrivilegedFileOperationJournal.decode("""{"operation":"copy","source":"/a","code":"OLD","message":"old"}""")
        PrivilegedFileOperationJournal.preserved(entry, PrivilegedFileOperationException("NO_SPACE", "storage full", true, listOf("/b/.part", "/b/out")))
        assertEquals(
            """{"operation":"copy","source":"/a","code":"NO_SPACE","message":"storage full","recoveryPaths":["/b/.part","/b/out"]}""",
            PrivilegedFileOperationJournal.encode(entry),
        )
    }
}
