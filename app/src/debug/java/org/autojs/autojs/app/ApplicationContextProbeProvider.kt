package org.autojs.autojs.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.autojs.autojs.App

/** Debug-only startup probe. Capture the state before Application.onCreate can repair it. */
class ApplicationContextProbeProvider : ContentProvider() {
    private var earlyContextReady = false
    override fun onCreate(): Boolean {
        earlyContextReady = runCatching {
            val application = requireNotNull(context).applicationContext
            application is App && GlobalAppContext.get() === application && App.app === application
        }.getOrDefault(false)
        return true
    }
    override fun call(method: String, arg: String?, extras: Bundle?) = Bundle().apply {
        require(method == "probe")
        putBoolean("earlyContextReady", earlyContextReady)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
