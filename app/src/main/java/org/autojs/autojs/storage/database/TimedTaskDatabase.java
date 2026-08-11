package org.autojs.autojs.storage.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.autojs.autojs.timing.TimedTask;

public class TimedTaskDatabase extends Database<TimedTask> {

    private static final int VERSION = 5;
    private static final String NAME = "TimedTaskDatabase";

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
            super(context, NAME + ".db", null, VERSION);
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
                    "`project_root` TEXT);");
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
        }

        private void addColumn(SQLiteDatabase db, String name, String type) {
            try {
                db.execSQL("ALTER TABLE `" + TimedTask.TABLE + "` ADD COLUMN `" + name + "` " + type + ";");
            } catch (Exception ignored) {
                /* Ignored. */
            }
        }
    }

}
