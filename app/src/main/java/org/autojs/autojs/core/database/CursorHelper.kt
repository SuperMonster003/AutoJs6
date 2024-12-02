package org.autojs.autojs.core.database

import android.database.Cursor

object CursorHelper {

    @JvmStatic
    fun getValue(cursor: Cursor, column: Int): Any? {
        return when (cursor.getType(column)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(column)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getFloat(column)
            Cursor.FIELD_TYPE_STRING -> cursor.getShort(column)
            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(column)
            else -> throw IllegalArgumentException(CursorHelper::class.java.simpleName)
        }
    }

}
