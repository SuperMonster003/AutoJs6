package com.wdullaer.materialdatetimepicker.time;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Unit tests for DefaultTimepointLimiter which need to run on an android device
 * Mostly used to test Parcelable serialisation logic
 * Created by wdullaer on 1/11/17.
 */
@RunWith(AndroidJUnit4.class)
public class DefaultTimepointLimiterTest {
    @Test
    public void shouldCorrectlySaveAndRestoreAParcelWithMinTime() {
        Timepoint minTime = new Timepoint(1, 2, 3);

        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        limiter.setMinTime(minTime);

        Parcel limiterParcel = Parcel.obtain();
        limiter.writeToParcel(limiterParcel, 0);
        limiterParcel.setDataPosition(0);

        DefaultTimepointLimiter clonedLimiter = DefaultTimepointLimiter.CREATOR.createFromParcel(limiterParcel);

        assertEquals(clonedLimiter.getMinTime(), minTime);
    }

    @Test
    public void shouldCorrectlySaveAndRestoreAParcelWithMaxTime() {
        Timepoint maxTime = new Timepoint(1, 2, 3);

        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        limiter.setMaxTime(maxTime);

        Parcel limiterParcel = Parcel.obtain();
        limiter.writeToParcel(limiterParcel, 0);
        limiterParcel.setDataPosition(0);

        DefaultTimepointLimiter clonedLimiter = DefaultTimepointLimiter.CREATOR.createFromParcel(limiterParcel);

        assertEquals(clonedLimiter.getMaxTime(), maxTime);
    }

    @Test
    public void shouldCorrectlySaveAndRestoreAParcelWithSelectableTimes() {
        Timepoint[] disabledTimes = {
                new Timepoint(1, 2, 3),
                new Timepoint(10, 11, 12)
        };

        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        limiter.setDisabledTimes(disabledTimes);

        Parcel limiterParcel = Parcel.obtain();
        limiter.writeToParcel(limiterParcel, 0);
        limiterParcel.setDataPosition(0);

        DefaultTimepointLimiter clonedLimiter = DefaultTimepointLimiter.CREATOR.createFromParcel(limiterParcel);

        assertArrayEquals(clonedLimiter.getDisabledTimes(), disabledTimes);
    }

    @Test
    public void shouldCorrectlySaveAndRestoreAParcelWithDisabledTimes() {
        Timepoint[] selectableTimes = {
                new Timepoint(1, 2, 3),
                new Timepoint(10, 11, 12)
        };

        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        limiter.setSelectableTimes(selectableTimes);

        Parcel limiterParcel = Parcel.obtain();
        limiter.writeToParcel(limiterParcel, 0);
        limiterParcel.setDataPosition(0);

        DefaultTimepointLimiter clonedLimiter = DefaultTimepointLimiter.CREATOR.createFromParcel(limiterParcel);

        assertArrayEquals(clonedLimiter.getSelectableTimes(), selectableTimes);
    }

    @Test
    public void shouldCorrectlySaveAndRestoreAParcel() {
        Timepoint minTime = new Timepoint(1, 2, 3);
        Timepoint maxTime = new Timepoint(12, 13, 14);
        Timepoint[] disabledTimes = {
                new Timepoint(2, 3, 4),
                new Timepoint(3, 4, 5)
        };
        Timepoint[] selectableTimes = {
                new Timepoint(2, 3, 4),
                new Timepoint(10, 11, 12)
        };

        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        limiter.setMinTime(minTime);
        limiter.setMaxTime(maxTime);
        limiter.setDisabledTimes(disabledTimes);
        limiter.setSelectableTimes(selectableTimes);

        Parcel limiterParcel = Parcel.obtain();
        limiter.writeToParcel(limiterParcel, 0);
        limiterParcel.setDataPosition(0);

        DefaultTimepointLimiter clonedLimiter = DefaultTimepointLimiter.CREATOR.createFromParcel(limiterParcel);

        assertEquals(clonedLimiter.getMinTime(), minTime);
        assertEquals(clonedLimiter.getMaxTime(), maxTime);
        assertArrayEquals(clonedLimiter.getDisabledTimes(), disabledTimes);
        assertArrayEquals(clonedLimiter.getSelectableTimes(), selectableTimes);
    }
}