package org.autojs.autojs.storage.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.timing.TimedTask;

/**
 * Schema changes of this table are limited to ADD COLUMN with a DEFAULT (or nullable): no type changes, no dropped
 * columns, no table rebuilds. That keeps the no-op {@code onDowngrade} safe, as ADD COLUMN appends: an older build
 * still finds the leading columns at their positions and the later ones by name, and leaves the newer ones alone.
 * {@code onUpgrade} stays idempotent through {@link SQLiteSchemaUtils#addColumnIfMissing}.
 * A destructive migration needs a decision first (docs/db-downgrade/ROADMAP.md, T8).
 * zh-CN: 本表的 schema 变更只做带 DEFAULT (或可为空) 的 ADD COLUMN: 不改列类型, 不删列, 不重建表. 这样无操作的
 * {@code onDowngrade} 才是安全的: ADD COLUMN 把列加在末尾, 旧构建仍能按位置找到前面的列, 按列名找到后加的列, 不碰较新的列.
 * {@code onUpgrade} 经 {@link SQLiteSchemaUtils#addColumnIfMissing} 保持幂等. 需要破坏性迁移时先登记待决
 * (docs/db-downgrade/ROADMAP.md, T8).
 */
public class TimedTaskDatabase extends Database<TimedTask> {

    public static final int VERSION = 6;
    private static final String NAME = "TimedTaskDatabase";
    public static final String FILE_NAME = NAME + ".db";

    public TimedTaskDatabase(Context context) {
        super(new SQLHelper(context), TimedTask.TABLE);
    }

    @Override
    protected ContentValues asContentValues(TimedTask model) {
        ContentValues values = new ContentValues();
        values.put("time", model.getTimeFlag());
        values.put("scheduled", model.isScheduled());
        values.put("delay", model.getDelay());
        values.put("interval", model.getInterval());
        values.put("loop_times", model.getLoopTimes());
        values.put("millis", model.getMillis());
        values.put("script_path", model.getScriptPath());
        values.put("source_kind", model.getSourceKind());
        values.put("project_root", model.getProjectRoot());
        values.put("repeat_mode", model.getRepeatMode());
        values.put("end_mode", model.getEndMode());
        values.put("end_value", model.getEndValue());
        values.put("finished_times", model.getFinishedTimes());
        values.put("virtual_screen_mode", model.getVirtualScreenMode());
        values.put("day_of_month", model.getDayOfMonth());
        values.put("month_of_year", model.getMonthOfYear());
        return values;
    }

    @Override
    protected TimedTask createModelFromCursor(Cursor cursor) {
        TimedTask task = new TimedTask();
        task.setId(cursor.getLong(0));
        task.setTimeFlag(cursor.getLong(1));
        task.setScheduled(cursor.getInt(2) != 0);
        task.setDelay(cursor.getLong(3));
        task.setInterval(cursor.getLong(4));
        task.setLoopTimes(cursor.getInt(5));
        task.setMillis(cursor.getLong(6));
        task.setScriptPath(cursor.getString(7));
        task.setRepeatMode(getInt(cursor, "repeat_mode", TimedTask.REPEAT_MODE_UNKNOWN));
        task.setEndMode(getInt(cursor, "end_mode", TimedTask.END_MODE_FOREVER));
        task.setEndValue(getLong(cursor, "end_value", 0));
        task.setFinishedTimes(getInt(cursor, "finished_times", 0));
        task.setVirtualScreenMode(getInt(cursor, "virtual_screen_mode", ExecutionConfig.VIRTUAL_SCREEN_MODE_FOLLOW));
        task.setDayOfMonth(getInt(cursor, "day_of_month", 0));
        task.setMonthOfYear(getInt(cursor, "month_of_year", 0));
        task.setSourceKind(getString(cursor, "source_kind"));
        task.setProjectRoot(getString(cursor, "project_root"));
        return task;
    }

    private static int getInt(Cursor cursor, String columnName, int defaultValue) {
        int index = cursor.getColumnIndex(columnName);
        return index < 0 ? defaultValue : cursor.getInt(index);
    }

    private static long getLong(Cursor cursor, String columnName, long defaultValue) {
        int index = cursor.getColumnIndex(columnName);
        return index < 0 ? defaultValue : cursor.getLong(index);
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
            db.execSQL("CREATE TABLE `" + TimedTask.TABLE + "`(" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`time` INTEGER, " +
                    "`scheduled` INTEGER, " +
                    "`delay` INTEGER, " +
                    "`interval` INTEGER, " +
                    "`loop_times` INTEGER, " +
                    "`millis` INTEGER, " +
                    "`script_path` TEXT, " +
                    "`repeat_mode` INTEGER DEFAULT -1, " +
                    "`end_mode` INTEGER DEFAULT 0, " +
                    "`end_value` INTEGER DEFAULT 0, " +
                    "`finished_times` INTEGER DEFAULT 0, " +
                    "`day_of_month` INTEGER DEFAULT 0, " +
                    "`month_of_year` INTEGER DEFAULT 0, " +
                    "`source_kind` TEXT, " +
                    "`project_root` TEXT, " +
                    "`virtual_screen_mode` INTEGER DEFAULT 0);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 4) {
                addColumn(db, "repeat_mode", "INTEGER DEFAULT -1");
                addColumn(db, "end_mode", "INTEGER DEFAULT 0");
                addColumn(db, "end_value", "INTEGER DEFAULT 0");
                addColumn(db, "finished_times", "INTEGER DEFAULT 0");
                addColumn(db, "day_of_month", "INTEGER DEFAULT 0");
                addColumn(db, "month_of_year", "INTEGER DEFAULT 0");
            }
            if (oldVersion < 5) {
                addColumn(db, "source_kind", "TEXT");
                addColumn(db, "project_root", "TEXT");
            }
            if (oldVersion < 6) {
                addColumn(db, "virtual_screen_mode", "INTEGER DEFAULT 0");
            }
        }

        /**
         * A file written by a newer build keeps its rows and extra columns (see the class comment);
         * SQLiteOpenHelper then sets {@code user_version} to {@link #VERSION}.
         * zh-CN: 较新构建写过的文件保留其行与多出的列 (见类注释); 随后 SQLiteOpenHelper 把 {@code user_version} 设为 {@link #VERSION}.
         */
        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(NAME, FILE_NAME + ": downgrade " + oldVersion + " -> " + newVersion + ", keeping data");
        }

        private void addColumn(SQLiteDatabase db, String name, String type) {
            SQLiteSchemaUtils.addColumnIfMissing(db, TimedTask.TABLE, name, type);
        }
    }

}
