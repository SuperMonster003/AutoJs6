package org.autojs.autojs.storage.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.autojs.autojs.timing.IntentTask;

public class IntentTaskDatabase extends Database<IntentTask> {

    private static final int VERSION = 3;
    private static final String NAME = "IntentTaskDatabase";

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
        return task;
    }

    private static String getString(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static class SQLHelper extends SQLiteOpenHelper {

        public SQLHelper(Context context) {
            super(context, NAME + ".db", null, VERSION);
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
                    "`project_root` TEXT);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + IntentTask.TABLE + "\n" +
                        "ADD local INT");
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE " + IntentTask.TABLE + " ADD source_kind TEXT");
                db.execSQL("ALTER TABLE " + IntentTask.TABLE + " ADD project_root TEXT");
            }
        }
    }
}
