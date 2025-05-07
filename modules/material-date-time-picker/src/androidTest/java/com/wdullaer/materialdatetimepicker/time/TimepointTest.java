package com.wdullaer.materialdatetimepicker.time;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for Timepoint which need to run on an actual device
 * Created by wdullaer on 1/11/17.
 */
@RunWith(AndroidJUnit4.class)
public class TimepointTest {
    @Test
    public void shouldCorrectlySaveAndRestoreAParcel() {
        Timepoint input = new Timepoint(1, 2, 3);
        Parcel timepointParcel = Parcel.obtain();
        input.writeToParcel(timepointParcel, 0);
        timepointParcel.setDataPosition(0);

        Timepoint output = Timepoint.CREATOR.createFromParcel(timepointParcel);
        assertEquals(input.getHour(), output.getHour());
        assertEquals(input.getMinute(), output.getMinute());
        assertEquals(input.getSecond(), output.getSecond());
    }
}