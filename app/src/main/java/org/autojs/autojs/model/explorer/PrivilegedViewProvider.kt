package org.autojs.autojs.model.explorer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.OpenableColumns
import org.autojs.autojs6.R
import java.io.FileNotFoundException

/** Only opaque, individually granted, read-only file links are accepted. Never parses a path from a URI. */
class PrivilegedViewProvider : ContentProvider() {
    override fun onCreate() = true

    private fun requireReadGrant(uri: Uri) {
        val uid = Binder.getCallingUid()
        if (uid != Process.myUid() && checkNotNull(context).checkUriPermission(uri, Binder.getCallingPid(), uid,
                Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("A read grant for this file is required")
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        requireReadGrant(uri)
        val context = checkNotNull(context)
        if (mode != "r") throw FileNotFoundException(context.getString(R.string.text_explorer_stream_read_only))
        val identity = Binder.clearCallingIdentity()
        try { return PrivilegedExternalFiles.open(context, uri) }
        finally { Binder.restoreCallingIdentity(identity) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor {
        requireReadGrant(uri)
        val metadata = PrivilegedExternalFiles.metadata(checkNotNull(context), uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(columns.map {
            when (it) { OpenableColumns.DISPLAY_NAME -> metadata.name; OpenableColumns.SIZE -> metadata.size; else -> null }
        }) }
    }

    override fun getType(uri: Uri): String {
        requireReadGrant(uri)
        return PrivilegedExternalFiles.metadata(checkNotNull(context), uri).mimeType
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
}
