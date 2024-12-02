package org.autojs.autojs.runtime.api

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.database.Database
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.ArrayExtensions.toNativeObject
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

class SQLite(private val context: Context, private val scriptRuntime: ScriptRuntime) {

    fun open(name: String, version: Int, readOnly: Boolean, callback: Database.DatabaseCallback?): Database {
        return Database(context, scriptRuntime, name, version, readOnly, callback, TypeAdapterImpl())
    }

    private class TypeAdapterImpl : Database.TypeAdapter {

        override fun toContentValues(obj: Any?): ContentValues {
            require(obj is Map<*, *>) { "Argument obj for toContentValue must be a Map<String, Object> instead of ${obj.jsBrief()}" }
            val contentValues = ContentValues()
            for ((k, v) in obj) {
                val key = coerceString(k)
                val value = v.jsUnwrapped()
                when {
                    value.isJsNullish() -> contentValues.putNull(key)
                    value is Double -> when {
                        value % 1.0 == 0.0 -> contentValues.put(key, value.toLong())
                        else -> contentValues.put(key, value)
                    }
                    value is Boolean -> contentValues.put(key, value)
                    value is String -> contentValues.put(key, value)
                    value is ByteArray -> contentValues.put(key, value)
                    else -> throw IllegalArgumentException("Unsupported data type for key: $key")
                }
            }
            return contentValues
        }

        override fun wrapCursor(cursor: Cursor) = CursorWrapper(cursor)

    }

    @Suppress("unused")
    class CursorWrapper(private val cursor: Cursor) : Cursor by cursor {

        @ScriptInterface
        fun get(index: Int) = when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
            else -> null
        }

        @ScriptInterface
        fun getByColumn(column: String) = get(cursor.getColumnIndexOrThrow(column))

        @ScriptInterface
        @JvmOverloads
        fun all(close: Any? = true): NativeArray {
            val result = mutableListOf<NativeObject>()
            while (cursor.moveToNext()) {
                result.add(pick())
            }
            if (coerceBoolean(close, true)) {
                cursor.close()
            }
            return result.toNativeArray()
        }

        @ScriptInterface
        fun pick(): NativeObject {
            val names = cursor.columnNames
            val result = mutableMapOf<String, Any?>()
            for (i in names.indices) {
                result[names[i]] = get(i)
            }
            return result.toNativeObject()
        }

        @ScriptInterface
        fun next(): NativeObject? = when {
            cursor.moveToNext() -> pick()
            else -> null
        }

        @ScriptInterface
        fun single(): NativeObject? = next().also { cursor.close() }

    }

    companion object {

        const val DEFAULT_VERSION = 1
        const val DEFAULT_READ_ONLY = false

    }

}