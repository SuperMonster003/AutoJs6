package com.wdullaer.materialdatetimepicker.date;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;

import static org.junit.Assert.*;

/**
 * Unit tests for DefaultDateRangeLimiter which need to run on an android device
 * Mostly used to test Parcelable serialisation logic
 * Created by wdullaer on 2/11/17.
 */
@RunWith(AndroidJUnit4.class)
public class DefaultDateRangeLimiterTest {
    @Test
    public void shouldCorrectlySaveAndRestoreAParcelWithAYearRange() {
        int minYear = 1985;
        int maxYear = 2015;

        DefaultDateRangeLimiter limiter = new DefaultDateRangeLimiter();
        limiter.setYearRange(minYear, maxYear);

        Parcel parcel = Parcel.obtain();
        limiter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DefaultDateRangeLimiter clonedLimiter = DefaultDateRangeLimiter.CREATOR.createFromParcel(parcel);

        assertEquals(clonedLimiter.getMinYear(), minYear);
        assertEquals(clonedLimiter.getMaxYear(), maxYear);
    }

    @Test
    public void shouldCorrectlySaveAndRestoreAParcelWithAMinDate() {
        Calendar minDate = Calendar.getInstance();
        minDate.set(Calendar.YEAR, 1980);

        DefaultDateRangeLimiter limiter = new DefaultDateRangeLimiter();
        limiter.setMinDate(minDate);

        Parcel parcel = Parcel.obtain();
        limiter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DefaultDateRangeLimiter clonedLimiter = DefaultDateRangeLimiter.CREATOR.createFromParcel(parcel);

        assertEquals(clonedLimiter.getMinDate(), limiter.getMinDate());
    }

    @Test
    public void shouldCorrectlySaveAndRestoreAParcelWithAMaxDate() {
        Calendar maxDate = Calendar.getInstance();

        DefaultDateRangeLimiter limiter = new DefaultDateRangeLimiter();
        limiter.setMaxDate(maxDate);

        Parcel parcel = Parcel.obtain();
        limiter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DefaultDateRangeLimiter clonedLimiter = DefaultDateRangeLimiter.CREATOR.createFromParcel(parcel);

        assertEquals(clonedLimiter.getMaxDate(), limiter.getMaxDate());
    }

    @Test
    public void shouldCorrectlySaveAndRestoreAParcelWithSelectableDays() {
        Calendar day1 = Calendar.getInstance();
        day1.set(Calendar.YEAR, 1985);
        Calendar day2 = Calendar.getInstance();
        Calendar[] selectableDays = {
                day1,
                day2
        };

        DefaultDateRangeLimiter limiter = new DefaultDateRangeLimiter();
        limiter.setSelectableDays(selectableDays);

        Parcel parcel = Parcel.obtain();
        limiter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DefaultDateRangeLimiter clonedLimiter = DefaultDateRangeLimiter.CREATOR.createFromParcel(parcel);

        assertArrayEquals(clonedLimiter.getSelectableDays(), limiter.getSelectableDays());
    }

    @Test
    public void shouldCorrectlySaveAndRestoreAParcelWithDisabledDays() {
        Calendar day1 = Calendar.getInstance();
        day1.set(Calendar.YEAR, 1985);
        Calendar day2 = Calendar.getInstance();
        Calendar[] disabledDays = {
                day1,
                day2
        };

        DefaultDateRangeLimiter limiter = new DefaultDateRangeLimiter();
        limiter.setDisabledDays(disabledDays);

        Parcel parcel = Parcel.obtain();
        limiter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DefaultDateRangeLimiter clonedLimiter = DefaultDateRangeLimiter.CREATOR.createFromParcel(parcel);

        assertArrayEquals(clonedLimiter.getDisabledDays(), limiter.getDisabledDays());
    }

    @Test
    public void shouldCorrectlySaveAndRestoreAParcel() {
        int minYear = 1970;
        int maxYear = 2020;

        Calendar minDate = Calendar.getInstance();
        minDate.set(Calendar.YEAR, 1985);
        Calendar maxDate = Calendar.getInstance();
        maxDate.set(Calendar.YEAR, 2019);

        Calendar day1 = Calendar.getInstance();
        day1.set(Calendar.MONTH, Calendar.JANUARY);
        Calendar day2 = Calendar.getInstance();
        day2.set(Calendar.MONTH, Calendar.AUGUST);
        Calendar[] disabledDays = {
                day1,
                day2
        };

        Calendar[] selectableDays = {
                Calendar.getInstance()
        };

        DefaultDateRangeLimiter limiter = new DefaultDateRangeLimiter();
        limiter.setYearRange(minYear, maxYear);
        limiter.setMinDate(minDate);
        limiter.setMaxDate(maxDate);
        limiter.setDisabledDays(disabledDays);
        limiter.setSelectableDays(selectableDays);

        Parcel parcel = Parcel.obtain();
        limiter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DefaultDateRangeLimiter clonedLimiter = DefaultDateRangeLimiter.CREATOR.createFromParcel(parcel);

        assertEquals(clonedLimiter.getMinYear(), limiter.getMinYear());
        assertEquals(clonedLimiter.getMaxYear(), limiter.getMaxYear());
        assertEquals(clonedLimiter.getMinDate(), limiter.getMinDate());
        assertEquals(clonedLimiter.getMaxDate(), limiter.getMaxDate());
        assertArrayEquals(clonedLimiter.getDisabledDays(), limiter.getDisabledDays());
        assertArrayEquals(clonedLimiter.getSelectableDays(), limiter.getSelectableDays());
    }
}