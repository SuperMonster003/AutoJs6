package com.stardust.autojs.core.database;

import android.database.Cursor;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.R;

public class CursorHelper {


    public static Object getValue(Cursor cursor, int column) {
        return switch (cursor.getType(column)) {
            case Cursor.FIELD_TYPE_STRING -> cursor.getShort(column);
            case Cursor.FIELD_TYPE_FLOAT -> cursor.getFloat(column);
            case Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(column);
            case Cursor.FIELD_TYPE_NULL -> null;
            case Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(column);
            default -> throw new IllegalArgumentException(GlobalAppContext
                    .getString(R.string.error_illegal_argument, "column", column));
        };
    }
}
