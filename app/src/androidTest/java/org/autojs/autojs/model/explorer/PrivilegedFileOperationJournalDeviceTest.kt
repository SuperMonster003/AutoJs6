package org.autojs.autojs.model.explorer

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PrivilegedFileOperationJournalDeviceTest {
    @Test
    fun uncertainResultsSurviveUntilTheUserCanSeeThem() = fixture { context ->
        val receipt = PrivilegedFileOperationJournal.begin(context, JSONObject().put("operation", "move").put("source", "/source").put("destination", "/target"))
        assertTrue(PrivilegedFileOperationJournal.interrupted(context).isEmpty())
        PrivilegedFileOperationJournal.preserve(receipt, PrivilegedFileOperationException("OUTCOME_UNKNOWN", "connection ended"))
        val firstRead = PrivilegedFileOperationJournal.interrupted(context)
        assertEquals(1, firstRead.size)
        assertEquals("OUTCOME_UNKNOWN", firstRead.single().second.getString("code"))
        assertEquals("/source", firstRead.single().second.getString("source"))
        // Reading while the activity is detached must not acknowledge or erase the result.
        assertEquals(1, PrivilegedFileOperationJournal.interrupted(context).size)
        PrivilegedFileOperationJournal.finish(receipt)
        assertTrue(PrivilegedFileOperationJournal.interrupted(context).isEmpty())
    }

    @Test
    fun partialFailuresKeepExactRecoveryLocations() = fixture { context ->
        val receipt = PrivilegedFileOperationJournal.begin(context, JSONObject().put("operation", "copy").put("source", "/source"))
        PrivilegedFileOperationJournal.preserve(receipt, PrivilegedFileOperationException("NO_SPACE", "storage full", true, listOf("/target/.autojs6-copy-test", "/target/output")))
        val result = PrivilegedFileOperationJournal.interrupted(context).single().second
        assertEquals("NO_SPACE", result.getString("code"))
        assertEquals(2, result.getJSONArray("recoveryPaths").length())
        PrivilegedFileOperationJournal.finish(receipt)
    }

    private fun fixture(test: (ContextWrapper) -> Unit) {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(original.cacheDir, "privileged-journal-test-${UUID.randomUUID()}").apply { check(mkdir()) }
        val context = object : ContextWrapper(original) { override fun getFilesDir() = directory }
        try { test(context) } finally { directory.deleteRecursively() }
    }
}
