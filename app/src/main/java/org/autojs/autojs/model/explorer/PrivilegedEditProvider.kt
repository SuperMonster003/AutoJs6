package org.autojs.autojs.model.explorer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Private read channel for editor drafts; no URI grant or writable descriptor can bypass save checks. */
class PrivilegedEditProvider : ContentProvider() {
    override fun onCreate() = true

    private fun verifyCaller() { check(Binder.getCallingUid() == Process.myUid()) { "Private editor document" } }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        verifyCaller()
        if (mode != "r") throw FileNotFoundException("Privileged documents must be saved through the editor")
        return PrivilegedEditSessions.openReadDescriptor(checkNotNull(context), uri)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor {
        verifyCaller()
        val context = checkNotNull(context)
        val file = PrivilegedEditSessions.readFile(context, uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map {
                when (it) {
                    OpenableColumns.DISPLAY_NAME -> File(checkNotNull(PrivilegedEditSessions.originalPath(context, uri))).name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            })
        }
    }

    override fun getType(uri: Uri): String { verifyCaller(); return "text/plain" }
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
