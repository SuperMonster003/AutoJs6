package org.autojs.autojs.storage.history

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 */
class HistoryBlobStore(private val context: Context) {

    fun writeRevisionBlob(fileId: String, revId: String, bytes: ByteArray): String {
        val rel = "history/blob/$fileId/$revId.bin"
        val f = File(context.filesDir, rel)
        f.parentFile?.mkdirs()
        f.outputStream().use { it.write(bytes) }
        return rel
    }

    fun deleteBlobByRelPath(relPath: String) {
        val f = File(context.filesDir, relPath)
        if (f.exists()) {
            // noinspection ResultOfMethodCallIgnored
            f.delete()
        }
    }

    fun newFileId(): String = UUID.randomUUID().toString()

    fun newRevId(): String = UUID.randomUUID().toString()
}
