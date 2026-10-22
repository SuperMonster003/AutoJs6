package org.autojs.autojs.engine

import org.autojs.autojs.core.pref.SettingsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The download records of [RuntimeWebLocalDownloads] as plain values of [SettingsJson] (docs/settings/ROADMAP.md P5.3):
 * a record that Android's org.json wrote reads the same, the records round trip with the members in the order they
 * always had, a stored download that was still running reads as interrupted, and the page still gets an org.json object.
 *
 * zh-CN: [RuntimeWebLocalDownloads] 的下载记录以 [SettingsJson] 普通值表示 (ROADMAP P5.3): Android org.json 写的记录照常读取,
 * 记录写读往返不变且成员顺序与以往相同, 进程结束时仍在下载的记录读作中断, 页面拿到的仍是 org.json 对象.
 */
class RuntimeWebLocalDownloadsJsonTest {

    private val id = "web:3f2b8a5e-0c1d-4e5f-9a7b-1c2d3e4f5a6b"

    // A record as the store wrote it before P5.1 (Android's `JSONObject.toString()`, escaped slashes): a large download
    // whose copy to the destination had started when the process ended.
    private val oldRunning = """{"id":"$id","url":"https:\/\/example.test\/files\/a.zip","fileName":"a.zip","status":"running",""" +
        """"reason":0,"bytesDownloaded":5368709120,"totalBytes":10737418240,"mimeType":"application\/zip","localUri":null,""" +
        """"contentUri":null,"managed":true,"pendingUri":"content:\/\/media\/external\/downloads\/42","pendingOwned":true}"""

    // A finished record as the store wrote it before P5.1.
    private val oldSuccessful = """{"id":"$id","url":"data:text\/plain,hello","fileName":"hello.txt","status":"successful",""" +
        """"reason":0,"bytesDownloaded":5,"totalBytes":5,"mimeType":"text\/plain","localUri":"content:\/\/media\/external\/downloads\/7",""" +
        """"contentUri":"content:\/\/media\/external\/downloads\/7","managed":true}"""

    @Test
    fun recordsThatOrgJsonWroteStillRead() {
        val record = RuntimeWebLocalDownloads.decode(oldRunning)
        assertEquals(
            listOf("id", "url", "fileName", "status", "reason", "bytesDownloaded", "totalBytes", "mimeType", "localUri", "contentUri", "managed", "pendingUri", "pendingOwned"),
            record.keys.toList(),
        )
        assertEquals("https://example.test/files/a.zip", record["url"])
        assertEquals(5368709120L, record["bytesDownloaded"])
        assertEquals(10737418240L, record["totalBytes"])
        assertEquals("application/zip", record["mimeType"])
        assertTrue(record.containsKey("contentUri"))
        assertEquals(null, record["contentUri"])
        assertEquals("content://media/external/downloads/42", record["pendingUri"])
        assertEquals(true, record["pendingOwned"])
    }

    @Test
    fun recordsRoundTrip() {
        for (old in listOf(oldRunning, oldSuccessful)) {
            val record = RuntimeWebLocalDownloads.decode(old)
            val text = RuntimeWebLocalDownloads.encode(record)
            assertFalse(text.contains("\\/"))
            assertEquals(old.replace("\\/", "/"), text)
            assertEquals(record, RuntimeWebLocalDownloads.decode(text))
        }
    }

    @Test
    fun aStoredDownloadThatWasStillRunningReadsAsInterrupted() {
        val record = RuntimeWebLocalDownloads.decode(oldRunning)
        assertTrue(RuntimeWebLocalDownloads.interrupted(record))
        // The status and the reason change in place; the error comes last, as org.json's `put` appended it.
        assertEquals(
            oldRunning.replace("\\/", "/").replace("\"status\":\"running\"", "\"status\":\"failed\"")
                .replace("\"reason\":0", "\"reason\":1000").removeSuffix("}") + ""","error":"Download interrupted by process termination"}""",
            RuntimeWebLocalDownloads.encode(record),
        )
        val finished = RuntimeWebLocalDownloads.decode(oldSuccessful)
        assertFalse(RuntimeWebLocalDownloads.interrupted(finished))
        assertEquals(oldSuccessful.replace("\\/", "/"), RuntimeWebLocalDownloads.encode(finished))
    }

    @Test
    fun aDownloadIsWrittenAsBeforeFromItsStartToItsCompletion() {
        val record = RuntimeWebLocalDownloads.started(id, "data:text/plain,hello", "hello.txt", "text/plain", true)
        assertEquals(
            """{"id":"$id","url":"data:text/plain,hello","fileName":"hello.txt","status":"running","reason":0,"bytesDownloaded":0,""" +
                """"totalBytes":-1,"mimeType":"text/plain","localUri":null,"contentUri":null,"managed":true}""",
            RuntimeWebLocalDownloads.encode(record),
        )
        record["totalBytes"] = 5
        record["bytesDownloaded"] = 5L
        RuntimeWebLocalDownloads.pending(record, "content://media/external/downloads/7", true)
        assertTrue(RuntimeWebLocalDownloads.encode(record).endsWith(""""managed":true,"pendingUri":"content://media/external/downloads/7","pendingOwned":true}"""))
        RuntimeWebLocalDownloads.completed(record, "content://media/external/downloads/7")
        assertEquals(oldSuccessful.replace("\\/", "/"), RuntimeWebLocalDownloads.encode(record))
    }

    @Test
    fun aFailureWithoutAMessageLeavesTheErrorOut() {
        val record = RuntimeWebLocalDownloads.started(id, "https://example.test/a.bin", "a.bin", "application/octet-stream", false)
        RuntimeWebLocalDownloads.failed(record, "Download HTTP 404")
        assertEquals("Download HTTP 404", record["error"])
        RuntimeWebLocalDownloads.failed(record, null)
        assertFalse(record.containsKey("error"))
        assertEquals("failed", record["status"])
        assertEquals(1000, record["reason"])
        assertFalse(RuntimeWebLocalDownloads.interrupted(record))
    }

    @Test
    fun thePageStillReceivesAnOrgJsonObject() {
        // `get` converts the record at the page boundary; JSON nulls stay JSON nulls and large sizes stay exact.
        val page = SettingsJson.toOrgObject(RuntimeWebLocalDownloads.decode(oldRunning))
        assertTrue(page.isNull("contentUri"))
        assertTrue(page.isNull("localUri"))
        assertEquals(5368709120L, page.getLong("bytesDownloaded"))
        assertEquals("https://example.test/files/a.zip", page.getString("url"))
        assertEquals(RuntimeWebLocalDownloads.decode(oldRunning), SettingsJson.normalizeObject(page))
    }
}
