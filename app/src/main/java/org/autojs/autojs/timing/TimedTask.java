package org.autojs.autojs.timing;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import org.autojs.autojs.annotation.ScriptInterface;
import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.script.PersistedScriptLaunchContext;
import org.autojs.autojs.script.ScriptLaunchSourceFactory;
import org.autojs.autojs.storage.database.BaseModel;
import org.autojs.autojs6.R;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;

import java.util.concurrent.TimeUnit;

public class TimedTask extends BaseModel {

    public static final String TABLE = "TimedTask";

    private static final int FLAG_DISPOSABLE = 0;
    public final static int FLAG_SUNDAY = 0x1;
    public final static int FLAG_MONDAY = 0x2;
    public final static int FLAG_TUESDAY = 0x4;
    public final static int FLAG_WEDNESDAY = 0x8;
    public final static int FLAG_THURSDAY = 0x10;
    public final static int FLAG_FRIDAY = 0x20;
    public final static int FLAG_SATURDAY = 0x40;
    private static final int FLAG_EVERYDAY = 0x7F;
    private static final int REQUEST_CODE = 2000;

    public static final int REPEAT_MODE_UNKNOWN = -1;
    public static final int REPEAT_MODE_DISPOSABLE = 0;
    public static final int REPEAT_MODE_DAILY = 1;
    public static final int REPEAT_MODE_WEEKLY = 2;
    public static final int REPEAT_MODE_MONTHLY = 3;
    public static final int REPEAT_MODE_YEARLY = 4;
    public static final int REPEAT_MODE_COUNTDOWN = 5;

    public static final int END_MODE_FOREVER = 0;
    public static final int END_MODE_UNTIL_DATE = 1;
    public static final int END_MODE_COUNT = 2;

    long mTimeFlag;

    boolean mScheduled;

    long mDelay = 0;

    long mInterval = 0;

    int mLoopTimes = 1;

    long mMillis;

    String mScriptPath;

    String mSourceKind;

    String mProjectRoot;

    int mRepeatMode = REPEAT_MODE_UNKNOWN;

    int mEndMode = END_MODE_FOREVER;

    long mEndValue = 0;

    int mFinishedTimes = 0;

    int mDayOfMonth = 0;

    int mMonthOfYear = 0;

    private final Context mGlobalAppContext = GlobalAppContext.get();

    public TimedTask() {
        /* Empty body. */
    }

    public TimedTask(long millis, long timeFlag, String scriptPath, ExecutionConfig config) {
        mMillis = millis;
        mTimeFlag = timeFlag;
        mScriptPath = scriptPath;
        mDelay = config.getDelay();
        mLoopTimes = config.getLoopTimes();
        mInterval = config.getInterval();
        mRepeatMode = inferRepeatMode(timeFlag);
        captureLaunchContext();
    }

    public boolean isDisposable() {
        return getRepeatMode() == REPEAT_MODE_DISPOSABLE || isCountdown();
    }

    public boolean isScheduled() {
        return mScheduled;
    }

    public void setScheduled(boolean scheduled) {
        mScheduled = scheduled;
    }

    @ScriptInterface
    public long getNextTime() {
        return getNextTime(mGlobalAppContext);
    }

    public long getNextTime(Context context) {
        if (isDisposable()) {
            return mMillis;
        }
        if (isRepeatCountReached()) {
            return -1;
        }
        long nextTime;
        if (isDaily()) {
            nextTime = getNextTimeOfDailyTask();
        } else if (isWeekly()) {
            nextTime = getNextTimeOfWeeklyTask(context);
        } else if (isMonthly()) {
            nextTime = getNextTimeOfMonthlyTask();
        } else if (isYearly()) {
            nextTime = getNextTimeOfYearlyTask();
        } else {
            nextTime = getNextTimeOfWeeklyTask(context);
        }
        return isWithinEndDate(nextTime) ? nextTime : -1;
    }

    private long getNextTimeOfDailyTask() {
        return CalendarTaskTimes.nextWeekday(mMillis, FLAG_EVERYDAY, DateTime.now());
    }

    @ScriptInterface
    private long getNextTimeOfWeeklyTask() {
        return getNextTimeOfWeeklyTask(mGlobalAppContext);
    }

    private long getNextTimeOfWeeklyTask(Context context) {
        return CalendarTaskTimes.nextWeekday(mMillis, mTimeFlag, DateTime.now());
    }

    private long getNextTimeOfMonthlyTask() {
        DateTime now = DateTime.now();
        DateTime nextTime = createMonthlyDateTime(now.getYear(), now.getMonthOfYear());
        if (now.getMillis() > nextTime.getMillis()) {
            DateTime nextMonth = now.plusMonths(1);
            nextTime = createMonthlyDateTime(nextMonth.getYear(), nextMonth.getMonthOfYear());
        }
        return nextTime.getMillis();
    }

    private DateTime createMonthlyDateTime(int year, int monthOfYear) {
        DateTime firstDay = new LocalDate(year, monthOfYear, 1).toDateTimeAtStartOfDay();
        int dayOfMonth = normalizeDayOfMonth(firstDay);
        return new DateTime(CalendarTaskTimes.atLocalTime(firstDay.withDayOfMonth(dayOfMonth).toLocalDate(), mMillis, firstDay.getZone()), firstDay.getZone());
    }

    private long getNextTimeOfYearlyTask() {
        DateTime now = DateTime.now();
        DateTime nextTime = createYearlyDateTime(now.getYear());
        if (now.getMillis() > nextTime.getMillis()) {
            nextTime = createYearlyDateTime(now.getYear() + 1);
        }
        return nextTime.getMillis();
    }

    private DateTime createYearlyDateTime(int year) {
        int monthOfYear = mMonthOfYear <= 0 ? 1 : Math.min(mMonthOfYear, DateTimeConstants.DECEMBER);
        DateTime firstDay = new LocalDate(year, monthOfYear, 1).toDateTimeAtStartOfDay();
        int dayOfMonth = normalizeDayOfMonth(firstDay);
        return new DateTime(CalendarTaskTimes.atLocalTime(firstDay.withDayOfMonth(dayOfMonth).toLocalDate(), mMillis, firstDay.getZone()), firstDay.getZone());
    }

    private int normalizeDayOfMonth(DateTime dateTime) {
        int maxDay = dateTime.dayOfMonth().getMaximumValue();
        int targetDay = mDayOfMonth <= 0 ? 1 : mDayOfMonth;
        return Math.min(targetDay, maxDay);
    }

    private boolean isRepeatCountReached() {
        return mEndMode == END_MODE_COUNT && mEndValue > 0 && mFinishedTimes >= mEndValue;
    }

    private boolean isWithinEndDate(long nextTimeMillis) {
        if (nextTimeMillis < 0) {
            return false;
        }
        if (mEndMode != END_MODE_UNTIL_DATE) {
            return true;
        }
        if (mEndValue <= 0) {
            return false;
        }
        return !new LocalDate(nextTimeMillis).isAfter(new LocalDate(mEndValue));
    }

    @ScriptInterface
    public static long getDayOfWeekTimeFlag(int dayOfWeek) {
        return getDayOfWeekTimeFlag(GlobalAppContext.get(), dayOfWeek);
    }

    public static long getDayOfWeekTimeFlag(Context context, int dayOfWeek) {
        dayOfWeek = (dayOfWeek - 1) % 7 + 1;
        switch (dayOfWeek) {
            case DateTimeConstants.SUNDAY:
                return FLAG_SUNDAY;
            case DateTimeConstants.MONDAY:
                return FLAG_MONDAY;
            case DateTimeConstants.SATURDAY:
                return FLAG_SATURDAY;
            case DateTimeConstants.WEDNESDAY:
                return FLAG_WEDNESDAY;
            case DateTimeConstants.TUESDAY:
                return FLAG_TUESDAY;
            case DateTimeConstants.THURSDAY:
                return FLAG_THURSDAY;
            case DateTimeConstants.FRIDAY:
                return FLAG_FRIDAY;
        }
        throw new IllegalArgumentException(context.getString(R.string.error_illegal_argument, "dayOfWeek", String.valueOf(dayOfWeek)));
    }

    public long getMillis() {
        return mMillis;
    }

    public String getScriptPath() {
        return mScriptPath;
    }

    public long getTimeFlag() {
        return mTimeFlag;
    }

    public void setTimeFlag(long time) {
        mTimeFlag = time;
    }

    public long getDelay() {
        return mDelay;
    }

    public void setDelay(long delay) {
        mDelay = delay;
    }

    public long getInterval() {
        return mInterval;
    }

    public void setInterval(long interval) {
        mInterval = interval;
    }

    public int getLoopTimes() {
        return mLoopTimes;
    }

    public void setLoopTimes(int loopTimes) {
        mLoopTimes = loopTimes;
    }

    public void setMillis(long millis) {
        mMillis = millis;
    }

    public void setScriptPath(String scriptPath) {
        mScriptPath = scriptPath;
    }

    public String getSourceKind() {
        return mSourceKind;
    }

    public void setSourceKind(String sourceKind) {
        mSourceKind = sourceKind;
    }

    public String getProjectRoot() {
        return mProjectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        mProjectRoot = projectRoot;
    }

    public void captureLaunchContext() {
        PersistedScriptLaunchContext context = ScriptLaunchSourceFactory.capturePersistedLaunchContext(mScriptPath);
        mSourceKind = context.getSourceKind();
        mProjectRoot = context.getProjectRoot();
    }

    public boolean isDaily() {
        return getRepeatMode() == REPEAT_MODE_DAILY;
    }

    public boolean isWeekly() {
        return getRepeatMode() == REPEAT_MODE_WEEKLY;
    }

    public boolean isMonthly() {
        return getRepeatMode() == REPEAT_MODE_MONTHLY;
    }

    public boolean isYearly() {
        return getRepeatMode() == REPEAT_MODE_YEARLY;
    }

    public boolean isCountdown() {
        return getRepeatMode() == REPEAT_MODE_COUNTDOWN;
    }

    public int getRepeatMode() {
        if (mRepeatMode == REPEAT_MODE_UNKNOWN) {
            mRepeatMode = inferRepeatMode(mTimeFlag);
        }
        return mRepeatMode;
    }

    public void setRepeatMode(int repeatMode) {
        mRepeatMode = repeatMode;
    }

    public int getEndMode() {
        return mEndMode;
    }

    public void setEndMode(int endMode) {
        mEndMode = endMode;
    }

    public long getEndValue() {
        return mEndValue;
    }

    public void setEndValue(long endValue) {
        mEndValue = endValue;
    }

    public int getFinishedTimes() {
        return mFinishedTimes;
    }

    public void setFinishedTimes(int finishedTimes) {
        mFinishedTimes = finishedTimes;
    }

    public int getDayOfMonth() {
        return mDayOfMonth;
    }

    public void setDayOfMonth(int dayOfMonth) {
        mDayOfMonth = dayOfMonth;
    }

    public int getMonthOfYear() {
        return mMonthOfYear;
    }

    public void setMonthOfYear(int monthOfYear) {
        mMonthOfYear = monthOfYear;
    }

    public void notifyFinished() {
        if (!isDisposable()) {
            mFinishedTimes++;
        }
    }

    public Intent createIntent() {
        Intent intent = new Intent(TaskReceiver.ACTION_TASK)
                .putExtra(TaskReceiver.EXTRA_TASK_ID, getId())
                .putExtra(ScriptIntents.EXTRA_KEY_PATH, mScriptPath)
                .putExtra(ScriptIntents.EXTRA_KEY_DELAY, mDelay)
                .putExtra(ScriptIntents.EXTRA_KEY_LOOP_TIMES, mLoopTimes)
                .putExtra(ScriptIntents.EXTRA_KEY_LOOP_INTERVAL, mInterval);
        if (mSourceKind != null) {
            intent.putExtra(ScriptIntents.EXTRA_KEY_SOURCE_KIND, mSourceKind);
        }
        if (mProjectRoot != null) {
            intent.putExtra(ScriptIntents.EXTRA_KEY_PROJECT_ROOT, mProjectRoot);
        }
        return intent;
    }

    public PendingIntent createPendingIntent(Context context) {
        return PendingIntent.getBroadcast(context, (int) ((REQUEST_CODE + 1 + getId()) % 65535),
                createIntent(), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @NonNull
    @Override
    public String toString() {
        return "TimedTask{" +
                "mId=" + getId() +
                ", mTimeFlag=" + mTimeFlag +
                ", mScheduled=" + mScheduled +
                ", mDelay=" + mDelay +
                ", mInterval=" + mInterval +
                ", mLoopTimes=" + mLoopTimes +
                ", mMillis=" + mMillis +
                ", mRepeatMode=" + getRepeatMode() +
                ", mEndMode=" + mEndMode +
                ", mEndValue=" + mEndValue +
                ", mFinishedTimes=" + mFinishedTimes +
                ", mDayOfMonth=" + mDayOfMonth +
                ", mMonthOfYear=" + mMonthOfYear +
                ", mScriptPath='" + mScriptPath + '\'' +
                '}';
    }

    public static TimedTask dailyTask(LocalTime time, String scriptPath, ExecutionConfig config) {
        return new TimedTask(time.getMillisOfDay(), FLAG_EVERYDAY, scriptPath, config);
    }

    public static TimedTask disposableTask(LocalDateTime dateTime, String scriptPath, ExecutionConfig config) {
        return new TimedTask(dateTime.toDateTime().getMillis(), FLAG_DISPOSABLE, scriptPath, config);
    }

    public static TimedTask countdownTask(long delayMillis, String scriptPath, ExecutionConfig config) {
        if (delayMillis <= 0) {
            throw new IllegalArgumentException("delayMillis must be greater than 0");
        }
        long targetMillis;
        try {
            targetMillis = Math.addExact(System.currentTimeMillis(), delayMillis);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("delayMillis is too large", e);
        }
        TimedTask task = new TimedTask(targetMillis, FLAG_DISPOSABLE, scriptPath, config);
        task.mRepeatMode = REPEAT_MODE_COUNTDOWN;
        return task;
    }

    public static TimedTask weeklyTask(LocalTime time, long timeFlag, String scriptPath, ExecutionConfig config) {
        TimedTask task = new TimedTask(time.getMillisOfDay(), timeFlag, scriptPath, config);
        task.mRepeatMode = REPEAT_MODE_WEEKLY;
        return task;
    }

    public static TimedTask monthlyTask(LocalTime time, int dayOfMonth, String scriptPath, ExecutionConfig config) {
        TimedTask task = new TimedTask(time.getMillisOfDay(), FLAG_DISPOSABLE, scriptPath, config);
        task.mRepeatMode = REPEAT_MODE_MONTHLY;
        task.mDayOfMonth = dayOfMonth;
        return task;
    }

    public static TimedTask yearlyTask(LocalTime time, int monthOfYear, int dayOfMonth, String scriptPath, ExecutionConfig config) {
        TimedTask task = new TimedTask(time.getMillisOfDay(), FLAG_DISPOSABLE, scriptPath, config);
        task.mRepeatMode = REPEAT_MODE_YEARLY;
        task.mMonthOfYear = monthOfYear;
        task.mDayOfMonth = dayOfMonth;
        return task;
    }

    public boolean hasDayOfWeek(Context context, int dayOfWeek) {
        return (mTimeFlag & getDayOfWeekTimeFlag(context, dayOfWeek)) != 0;
    }

    private static int inferRepeatMode(long timeFlag) {
        if (timeFlag == FLAG_DISPOSABLE) {
            return REPEAT_MODE_DISPOSABLE;
        }
        if (timeFlag == FLAG_EVERYDAY) {
            return REPEAT_MODE_DAILY;
        }
        return REPEAT_MODE_WEEKLY;
    }
}
