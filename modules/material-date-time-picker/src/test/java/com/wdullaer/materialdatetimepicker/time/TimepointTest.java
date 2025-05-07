package com.wdullaer.materialdatetimepicker.time;

import org.junit.Test;
import org.junit.Assert;

import java.util.HashSet;

public class TimepointTest {

    @Test
    public void timepointsWithSameFieldsShouldHaveSameHashCode() {
        Timepoint first = new Timepoint(12, 0, 0);
        Timepoint second = new Timepoint(12, 0, 0);
        Assert.assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void timepointsWithSameFieldsShouldBeEquals() {
        Timepoint first = new Timepoint(12, 0, 0);
        Timepoint second = new Timepoint(12, 0, 0);
        Assert.assertEquals(first, second);
    }

    @Test
    public void timepointsWithSameFieldsShouldBeDistinctInHashSet() {
        HashSet<Timepoint> timepoints = new HashSet<>(4);
        timepoints.add(new Timepoint(12, 0, 0));
        timepoints.add(new Timepoint(12, 0, 0));
        timepoints.add(new Timepoint(12, 0, 0));
        timepoints.add(new Timepoint(12, 0, 0));
        Assert.assertEquals(timepoints.size(), 1);
    }

    @Test
    public void timepointsWithDifferentFieldsShouldNotBeDistinctInHashSet() {
        HashSet<Timepoint> timepoints = new HashSet<>(4);
        timepoints.add(new Timepoint(12, 1, 0));
        timepoints.add(new Timepoint(12, 2, 0));
        timepoints.add(new Timepoint(12, 3, 0));
        timepoints.add(new Timepoint(12, 4, 0));
        Assert.assertEquals(timepoints.size(), 4);
    }

    @Test
    public void compareToShouldReturnNegativeIfArgumentIsBigger() {
        Timepoint orig = new Timepoint(12, 11, 10);
        Timepoint arg = new Timepoint(13, 14, 15);
        Assert.assertTrue(orig.compareTo(arg) < 0);
    }

    @Test
    public void compareToShouldReturnPositiveIfArgumentIsSmaller() {
        Timepoint orig = new Timepoint(12, 11, 10);
        Timepoint arg = new Timepoint(10, 14, 15);
        Assert.assertTrue(orig.compareTo(arg) > 0);
    }

    @Test
    public void compareToShouldReturnZeroIfArgumentIsEqual() {
        Timepoint orig = new Timepoint(12, 11, 10);
        Timepoint arg = new Timepoint(12, 11, 10);
        Assert.assertEquals(0, orig.compareTo(arg));
    }

    @Test
    public void isAMShouldReturnTrueIfTimepointIsBeforeMidday() {
        Timepoint timepoint = new Timepoint(11);
        Assert.assertTrue(timepoint.isAM());
    }

    @Test
    public void isAMShouldReturnFalseIfTimepointIsMidday() {
        Timepoint timepoint = new Timepoint(12);
        Assert.assertFalse(timepoint.isAM());
    }

    @Test
    public void isAMShouldReturnFalseIfTimepointIsAfterMidday() {
        Timepoint timepoint = new Timepoint(13);
        Assert.assertFalse(timepoint.isAM());
    }

    @Test
    public void isAMShouldReturnTrueIfTimepointIsMidnight() {
        Timepoint timepoint = new Timepoint(0);
        Assert.assertTrue(timepoint.isAM());
    }

    @Test
    public void isPMShouldReturnFalseIfTimepointIsBeforeMidday() {
        Timepoint timepoint = new Timepoint(11);
        Assert.assertFalse(timepoint.isPM());
    }

    @Test
    public void isPMShouldReturnTrueIfTimepointIsMidday() {
        Timepoint timepoint = new Timepoint(12);
        Assert.assertTrue(timepoint.isPM());
    }

    @Test
    public void isPMShouldReturnTrueIfTimepointIsAfterMidday() {
        Timepoint timepoint = new Timepoint(13);
        Assert.assertTrue(timepoint.isPM());
    }

    @Test
    public void isPMShouldReturnFalseIfTimepointIsMidnight() {
        Timepoint timepoint = new Timepoint(0);
        Assert.assertFalse(timepoint.isPM());
    }

    @Test
    public void setAMShouldDoNothingIfTimepointIsBeforeMidday() {
        Timepoint timepoint = new Timepoint(11);
        timepoint.setAM();
        Assert.assertEquals(timepoint.getHour(), 11);
    }

    @Test
    public void setAMShouldSetToMidnightIfTimepointIsMidday() {
        Timepoint timepoint = new Timepoint(12);
        timepoint.setAM();
        Assert.assertEquals(timepoint.getHour(), 0);
    }

    @Test
    public void setAMShouldSetBeforeMiddayIfTimepointIsAfterMidday() {
        Timepoint timepoint = new Timepoint(13);
        timepoint.setAM();
        Assert.assertEquals(timepoint.getHour(), 1);
    }

    @Test
    public void setAMShouldDoNothingIfTimepointIsMidnight() {
        Timepoint timepoint = new Timepoint(0);
        timepoint.setAM();
        Assert.assertEquals(timepoint.getHour(), 0);
    }

    @Test
    public void setAMShouldNotChangeMinutesOrSeconds() {
        Timepoint timepoint = new Timepoint(13, 14, 15);
        timepoint.setAM();
        Assert.assertEquals(timepoint.getMinute(), 14);
        Assert.assertEquals(timepoint.getSecond(), 15);
    }

    @Test
    public void setPMShouldDoNothingIfTimepointIsAfterMidday() {
        Timepoint timepoint = new Timepoint(13);
        timepoint.setPM();
        Assert.assertEquals(timepoint.getHour(), 13);
    }

    @Test
    public void setPMShouldSetToMiddayIfTimepointIsMidnight() {
        Timepoint timepoint = new Timepoint(0);
        timepoint.setPM();
        Assert.assertEquals(timepoint.getHour(), 12);
    }

    @Test
    public void setPMShouldSetAfterMiddayIfTimepointIsBeforeMidday() {
        Timepoint timepoint = new Timepoint(5);
        timepoint.setPM();
        Assert.assertEquals(timepoint.getHour(), 17);
    }

    @Test
    public void setPMShouldDoNothingIfTimepointIsMidday() {
        Timepoint timepoint = new Timepoint(12);
        timepoint.setPM();
        Assert.assertEquals(timepoint.getHour(), 12);
    }

    @Test
    public void setPMShouldNotChangeMinutesOrSeconds() {
        Timepoint timepoint = new Timepoint(1, 14, 15);
        timepoint.setPM();
        Assert.assertEquals(timepoint.getMinute(), 14);
        Assert.assertEquals(timepoint.getSecond(), 15);
    }

    @Test
    public void equalsShouldReturnTrueWhenInputsAreEqualWithSecondsResolution() {
        Timepoint timepoint1 = new Timepoint(1, 14, 15);
        Timepoint timepoint2 = new Timepoint(1, 14, 15);
        Assert.assertTrue(timepoint1.equals(timepoint2, Timepoint.TYPE.SECOND));
    }

    @Test
    public void equalsShouldReturnFalseWhenInputsDifferWithSecondsResolution() {
        Timepoint timepoint1 = new Timepoint(1, 14, 15);
        Timepoint timepoint2 = new Timepoint(1, 14, 16);
        Assert.assertFalse(timepoint1.equals(timepoint2, Timepoint.TYPE.SECOND));
    }

    @Test
    public void equalsShouldIgnoreSecondsWithMinuteResolution() {
        Timepoint timepoint1 = new Timepoint(1, 14, 15);
        Timepoint timepoint2 = new Timepoint(1, 14, 16);
        Assert.assertTrue(timepoint1.equals(timepoint2, Timepoint.TYPE.MINUTE));
    }

    @Test
    public void equalsShouldReturnFalseWhenInputsDifferWithMinuteResolution() {
        Timepoint timepoint1 = new Timepoint(1, 14, 15);
        Timepoint timepoint2 = new Timepoint(1, 15, 15);
        Assert.assertFalse(timepoint1.equals(timepoint2, Timepoint.TYPE.MINUTE));
    }

    @Test
    public void equalsShouldIgnoreSecondsWithHourResolution() {
        Timepoint timepoint1 = new Timepoint(1, 14, 15);
        Timepoint timepoint2 = new Timepoint(1, 14, 16);
        Assert.assertTrue(timepoint1.equals(timepoint2, Timepoint.TYPE.HOUR));
    }

    @Test
    public void equalsShouldIgnoreMinutesWithHourResolution() {
        Timepoint timepoint1 = new Timepoint(1, 14, 15);
        Timepoint timepoint2 = new Timepoint(1, 15, 16);
        Assert.assertTrue(timepoint1.equals(timepoint2, Timepoint.TYPE.HOUR));
    }

    @Test
    public void equalsShouldReturnFalseWhenInputsDifferWithHourResolution() {
        Timepoint timepoint1 = new Timepoint(1, 14, 15);
        Timepoint timepoint2 = new Timepoint(2, 14, 15);
        Assert.assertFalse(timepoint1.equals(timepoint2, Timepoint.TYPE.HOUR));
    }
}
