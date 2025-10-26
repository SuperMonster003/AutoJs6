package org.autojs.autojs.deviceid

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import androidx.core.content.edit
import java.util.*

class DeviceIdProvider : ContentProvider() {

    companion object {
        private const val AUTH = "org.autojs.autojs6.deviceid.provider"
        private const val PATH = "v1/device_id"
        private const val CODE = 1
        private const val COL = "device_id"
        private const val SP_NAME = "device_id_prefs"
        private const val SP_KEY = "device_id"
        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTH, PATH, CODE)
        }
    }

    override fun onCreate(): Boolean {
        ensureId()
        return true
    }

    private fun ensureId(): String {
        val sp = context!!.getSharedPreferences(SP_NAME, 0)
        val exist = sp.getString(SP_KEY, null)
        if (exist != null) return exist
        val id = UUID.randomUUID().toString()
        sp.edit { putString(SP_KEY, id) }
        return id
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? {
        if (matcher.match(uri) != CODE) return null
        val id = ensureId()
        val cursor = MatrixCursor(arrayOf(COL), 1)
        cursor.addRow(arrayOf(id))
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "getDeviceId") {
            val id = ensureId()
            return Bundle().apply { putString(COL, id) }
        }
        return super.call(method, arg, extras)
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.item/$AUTH.$PATH"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

}
