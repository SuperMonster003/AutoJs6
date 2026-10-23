package org.autojs.autojs.storage.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Schema helpers that keep {@code onUpgrade} idempotent. A no-op downgrade leaves the columns of the newer version
 * in place while SQLiteOpenHelper lowers {@code user_version}, so the next upgrade meets columns that already exist.
 * zh-CN: 让 {@code onUpgrade} 保持幂等的 schema 工具. 无操作降级保留较新版本的列, 而 SQLiteOpenHelper 会调低
 * {@code user_version}, 所以下一次升级会遇到已经存在的列.
 *
 * Created by Claude (Opus 5.5) on Oct 23, 2026.
 */
public final class SQLiteSchemaUtils {

    private SQLiteSchemaUtils() {
        /* Empty body. */
    }

    public static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(`" + table + "`)", null)) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                if (column.equalsIgnoreCase(cursor.getString(nameIndex))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Adds the column unless the table already has it. Errors other than an existing column are not swallowed.
     * zh-CN: 表中没有该列时才添加. 列已存在之外的错误不会被吞掉.
     *
     * @param definition type and constraints, e.g. {@code INTEGER DEFAULT 0}
     * @return whether the column was added
     */
    public static boolean addColumnIfMissing(SQLiteDatabase db, String table, String column, String definition) {
        if (hasColumn(db, table, column)) {
            return false;
        }
        db.execSQL("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        return true;
    }

}
