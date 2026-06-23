package org.autojs.autojs.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.system.Os
import java.io.File

object ScriptOriginResolver {

    private const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"

    @JvmStatic
    fun resolveFilePath(context: Context, uri: Uri?): String? {
        uri ?: return null

        return when {
            ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true) ->
                canonicalAccessibleFile(uri.path)

            ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true) ->
                resolveExternalStorageDocument(context, uri)
                    ?: resolveFromFileDescriptor(context, uri)

            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveExternalStorageDocument(context: Context, uri: Uri): String? {
        if (uri.authority != EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY) {
            return null
        }

        val isDocumentUri = runCatching {
            DocumentsContract.isDocumentUri(context, uri)
        }.getOrDefault(false)

        if (!isDocumentUri) {
            return null
        }

        val documentId = runCatching {
            DocumentsContract.getDocumentId(uri)
        }.getOrNull() ?: return null

        val parts = documentId.split(':', limit = 2)
        if (parts.size != 2) {
            return null
        }

        val volume = parts[0]
        val relativePath = parts[1]
        if (!volume.equals("primary", ignoreCase = true)) {
            return null
        }

        return canonicalAccessibleFile(File(Environment.getExternalStorageDirectory(), relativePath).path)
    }

    private fun resolveFromFileDescriptor(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val target = Os.readlink("/proc/self/fd/${descriptor.fd}")
                .removeSuffix(" (deleted)")

            canonicalAccessibleFile(target)
        }
    }.getOrNull()

    private fun canonicalAccessibleFile(path: String?): String? {
        if (path.isNullOrBlank() || !path.startsWith(File.separator)) {
            return null
        }

        return runCatching {
            File(path)
                .takeIf { it.isFile && it.canRead() }
                ?.canonicalPath
        }.getOrNull()
    }
}
