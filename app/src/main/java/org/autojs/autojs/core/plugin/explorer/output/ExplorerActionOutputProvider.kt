package org.autojs.autojs.core.plugin.explorer.output

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor

internal class ExplorerActionOutputProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val providerContext = context ?: throw IllegalStateException("Explorer Action output provider has no context")
        return ExplorerActionOutputCoordinator.openOutputWriter(
            context = providerContext,
            uri = uri,
            mode = mode,
            callingPackage = callingPackage,
        )
    }

    override fun getType(uri: Uri): String? = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = throw UnsupportedOperationException("Explorer Action output provider is write-only")

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Explorer Action output provider does not support insert")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Explorer Action output provider does not support delete")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Explorer Action output provider does not support update")

    companion object {
        private const val AUTHORITY_SUFFIX = "explorer_action_output"

        fun authority(packageName: String): String = "$packageName.$AUTHORITY_SUFFIX"

        fun uri(packageName: String, transactionId: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(authority(packageName))
            .appendPath(transactionId)
            .build()
    }
}
