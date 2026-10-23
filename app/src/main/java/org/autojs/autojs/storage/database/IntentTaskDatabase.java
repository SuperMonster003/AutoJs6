package org.autojs.autojs.storage.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.autojs.autojs.timing.IntentTask;

/**
 * Schema changes of this table follow the same rule as {@link TimedTaskDatabase}: ADD COLUMN with a DEFAULT
 * (or nullable) only, so the no-op {@code onDowngrade} keeps the data and {@code onUpgrade} stays idempotent
 * (docs/db-downgrade/ROADMAP.md, T8).
 * zh-CN: 本表的 schema 变更与 {@link TimedTaskDatabase} 同一约定: 只做带 DEFAULT (或可为空) 的 ADD COLUMN,
 * 这样无操作的 {@code onDowngrade} 保留数据, {@code onUpgrade} 保持幂等 (docs/db-downgrade/ROADMAP.md, T8).
 */
public class IntentTaskDatabase extends Database<IntentTask> {

    public static final int VERSION = 4;
    private static final String NAME = "IntentTaskDatabase";
    public static final String FILE_NAME = NAME + ".db";

    public IntentTaskDatabase(Context context) {
        super(new SQLHelper(context), IntentTask.TABLE);
    }

    @Override
    protected ContentValues asContentValues(IntentTask model) {
        ContentValues values = new ContentValues();
        values.put("script_path", model.getScriptPath());
        values.put("source_kind", model.getSourceKind());
        values.put("project_root", model.getProjectRoot());
        values.put("action", model.getAction());
        values.put("category", model.getCategory());
        values.put("data_type", model.getDataType());
        values.put("local", model.isLocal() ? 1 : 0);
        values.put("extras", model.getExtras());
        return values;
    }

    @Override
    protected IntentTask createModelFromCursor(Cursor cursor) {
        IntentTask task = new IntentTask();
        task.setId(cursor.getInt(0));
        task.setScriptPath(cursor.getString(1));
        task.setAction(cursor.getString(2));
        task.setCategory(cursor.getString(3));
        task.setDataType(cursor.getString(4));
        task.setLocal(cursor.getInt(5) != 0);
        task.setSourceKind(getString(cursor, "source_kind"));
        task.setProjectRoot(getString(cursor, "project_root"));
        task.setExtras(getString(cursor, "extras"));
        return task;
    }

    private static String getString(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static class SQLHelper extends SQLiteOpenHelper {

        public SQLHelper(Context context) {
            super(context, FILE_NAME, null, VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE `" + IntentTask.TABLE + "`(" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`script_path` TEXT NOT NULL ON CONFLICT FAIL, " +
                    "`action` TEXT, " +
                    "`category` TEXT, " +
                    "`data_type` TEXT, " +
                    "`local` INT, " +
                    "`source_kind` TEXT, " +
                    "`project_root` TEXT, " +
                    "`extras` TEXT);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                SQLiteSchemaUtils.addColumnIfMissing(db, IntentTask.TABLE, "local", "INT");
            }
            if (oldVersion < 3) {
                SQLiteSchemaUtils.addColumnIfMissing(db, IntentTask.TABLE, "source_kind", "TEXT");
                SQLiteSchemaUtils.addColumnIfMissing(db, IntentTask.TABLE, "project_root", "TEXT");
            }
            if (oldVersion < 4) {
                SQLiteSchemaUtils.addColumnIfMissing(db, IntentTask.TABLE, "extras", "TEXT");
            }
        }

        /**
         * Same as {@code TimedTaskDatabase.SQLHelper#onDowngrade}: rows and extra columns are kept.
         * zh-CN: 与 {@code TimedTaskDatabase.SQLHelper#onDowngrade} 相同: 保留行与多出的列.
         */
        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(NAME, FILE_NAME + ": downgrade " + oldVersion + " -> " + newVersion + ", keeping data");
        }
    }
}
