package com.wdullaer.materialdatetimepicker.time;

import static com.wdullaer.materialdatetimepicker.time.TimePickerDialog.HOUR_INDEX;
import static com.wdullaer.materialdatetimepicker.time.TimePickerDialog.MINUTE_INDEX;

import org.junit.Test;
import org.junit.Assert;

/**
 * Unit tests for the default implementation of TimepointLimiter
 * Mostly used to assert that the rounding logic works
 *
 * Created by wdullaer on 22/06/17.
 */
public class DefaultTimepointLimiterTest {
    @Test
    public void isAmDisabledShouldReturnTrueWhenMinTimeIsInTheAfternoon() {
        Timepoint minTime = new Timepoint(13);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMinTime(minTime);

        Assert.assertTrue(limiter.isAmDisabled());
    }

    @Test
    public void isAmDisabledShouldReturnFalseWhenMinTimeIsInTheMorning() {
        Timepoint minTime = new Timepoint(8);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMinTime(minTime);

        Assert.assertFalse(limiter.isAmDisabled());
    }

    @Test
    public void isAmDisabledShouldReturnTrueWhenMinTimeIsMidday() {
        Timepoint minTime = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMinTime(minTime);

        Assert.assertTrue(limiter.isAmDisabled());
    }

    @Test
    public void isAmDisabledShouldReturnFalseWhenMaxTimeIsInTheMorning() {
        Timepoint maxTime = new Timepoint(8);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMaxTime(maxTime);

        Assert.assertFalse(limiter.isAmDisabled());
    }

    @Test
    public void isAmDisabledShouldReturnFalseWhenMaxTimeIsMidday() {
        Timepoint maxTime = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMaxTime(maxTime);

        Assert.assertFalse(limiter.isAmDisabled());
    }

    @Test
    public void isPmDisabledShouldReturnTrueWhenMaxTimeIsInTheMorning() {
        Timepoint maxTime = new Timepoint(9);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMaxTime(maxTime);

        Assert.assertTrue(limiter.isPmDisabled());
    }

    @Test
    public void isPmDisabledShouldReturnFalseWhenMinTimeIsInTheAfternoon() {
        Timepoint minTime = new Timepoint(13);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMinTime(minTime);

        Assert.assertFalse(limiter.isPmDisabled());
    }

    @Test
    public void isPmDisabledShouldReturnFalseWhenMaxTimeIsMidday() {
        Timepoint maxTime = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMaxTime(maxTime);

        Assert.assertFalse(limiter.isPmDisabled());
    }

    @Test
    public void isAmDisabledShouldReturnTrueIfSelectableDaysAreInTheAfternoon() {
        Timepoint[] selectableDays = {
                new Timepoint(13),
                new Timepoint(22)
        };
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableDays);

        Assert.assertTrue(limiter.isAmDisabled());
    }

    @Test
    public void isAmDisabledShouldReturnFalseIfSelectableDaysHasOneTimeInTheMorning() {
        Timepoint[] selectableDays = {
                new Timepoint(4),
                new Timepoint(13),
                new Timepoint(22)
        };
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableDays);

        Assert.assertFalse(limiter.isAmDisabled());
    }

    @Test
    public void isPmDisabledShouldReturnTrueIfSelectableDaysAreInTheMorning() {
        Timepoint[] selectableDays = {
                new Timepoint(4),
                new Timepoint(9),
                new Timepoint(11)
        };
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableDays);

        Assert.assertTrue(limiter.isPmDisabled());
    }

    @Test
    public void isPmDisabledShouldReturnFalseIfSelectableDaysHasOneTimeInTheAfternoon() {
        Timepoint[] selectableDays = {
                new Timepoint(4),
                new Timepoint(22)
        };
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableDays);

        Assert.assertFalse(limiter.isPmDisabled());
    }

    @Test
    public void isPmDisabledShouldReturnFalseIfSelectableDaysContainsMidday() {
        Timepoint[] selectableDays = {
                new Timepoint(4),
                new Timepoint(9),
                new Timepoint(12)
        };
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableDays);

        Assert.assertFalse(limiter.isPmDisabled());
    }

    @Test
    public void isPmDisabledShouldReturnFalseWithoutConstraints() {
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        Assert.assertFalse(limiter.isPmDisabled());
    }

    @Test
    public void isAmDisabledShouldReturnFalseWithoutConstraints() {
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        Assert.assertFalse(limiter.isAmDisabled());
    }

    @Test
    public void setMinTimeShouldThrowExceptionWhenBiggerThanMaxTime() {
        Timepoint maxTime = new Timepoint(2);
        Timepoint minTime = new Timepoint(3);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMaxTime(maxTime);

        try {
            limiter.setMinTime(minTime);
            Assert.fail("setMinTime() should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void setMaxTimeShouldThrowExceptionWhenSmallerThanMinTime() {
        Timepoint maxTime = new Timepoint(2);
        Timepoint minTime = new Timepoint(3);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMinTime(minTime);

        try {
            limiter.setMaxTime(maxTime);
            Assert.fail("setMaxTime() should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void isOutOfRangeShouldReturnTrueIfInputSmallerThanMinTime() {
        Timepoint minTime = new Timepoint(10);
        Timepoint input = new Timepoint(2);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMinTime(minTime);

        Assert.assertTrue(limiter.isOutOfRange(input));
    }

    @Test
    public void isOutOfRangeShouldReturnTrueIfInputLargerThanMaxTime() {
        Timepoint maxTime = new Timepoint(2);
        Timepoint input = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMaxTime(maxTime);

        Assert.assertTrue(limiter.isOutOfRange(input));
    }

    @Test
    public void isOutOfRangeShouldReturnFalseIfInputIsBetweenMinAndMaxTime() {
        Timepoint minTime = new Timepoint(1);
        Timepoint maxTime = new Timepoint(13);
        Timepoint input = new Timepoint(4);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMinTime(minTime);
        limiter.setMaxTime(maxTime);

        Assert.assertFalse(limiter.isOutOfRange(input));
    }

    @Test
    public void isOutOfRangeShouldReturnFalseWithoutRestraints() {
        Timepoint input = new Timepoint(14);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        Assert.assertFalse(limiter.isOutOfRange(input));
    }

    @Test
    public void isOutOfRangeShouldReturnTrueIfInputNotSelectable() {
        Timepoint input = new Timepoint(1);
        Timepoint[] selectableDays = {
                new Timepoint(13),
                new Timepoint(14)
        };
        DefaultTimepointLimiter limiter  = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableDays);

        Assert.assertTrue(limiter.isOutOfRange(input));
    }

    @Test
    public void isOutOfRangeShouldReturnFalseIfInputSelectable() {
        Timepoint input = new Timepoint(15);
        Timepoint[] selectableDays = {
                new Timepoint(4),
                new Timepoint(10),
                new Timepoint(15)
        };
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableDays);

        Assert.assertFalse(limiter.isOutOfRange(input));
    }

    @Test
    public void isOutOfRangeWithIndexShouldHandleNull() {
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        Assert.assertFalse(limiter.isOutOfRange(null, HOUR_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeMinuteShouldReturnFalseWhenMinTimeEqualsToTheMinute() {
        Timepoint minTime = new Timepoint(12, 13, 14);
        Timepoint input = new Timepoint(12, 13);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMinTime(minTime);

        Assert.assertFalse(limiter.isOutOfRange(input, MINUTE_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeMinuteShouldReturnFalseWhenMaxTimeEqualsToTheMinute() {
        Timepoint maxTime = new Timepoint(12, 13, 14);
        Timepoint input = new Timepoint(12, 13);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMaxTime(maxTime);

        Assert.assertFalse(limiter.isOutOfRange(input, MINUTE_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeMinuteShouldReturnFalseWhenTimeEqualsSelectableTimeToTheMinute() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = new Timepoint(12, 13);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertFalse(limiter.isOutOfRange(input, MINUTE_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeMinuteShouldReturnFalseWhenTimeEqualsSelectableTimeToTheMinute2() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = new Timepoint(13, 14, 59);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertFalse(limiter.isOutOfRange(input, MINUTE_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeMinuteShouldReturnFalseWhenTimeEqualsSelectableTimeToTheMinute3() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = new Timepoint(11, 12, 0);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertFalse(limiter.isOutOfRange(input, MINUTE_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeMinuteShouldReturnTrueWhenTimeDoesNotEqualSelectableTimeToTheMinute() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = new Timepoint(11, 11, 0);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertTrue(limiter.isOutOfRange(input, MINUTE_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeHourShouldReturnFalseWhenMinTimeEqualsToTheHour() {
        Timepoint minTime = new Timepoint(12, 13, 14);
        Timepoint input = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMinTime(minTime);

        Assert.assertFalse(limiter.isOutOfRange(input, HOUR_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeHourShouldReturnFalseWhenMaxTimeEqualsToTheHour() {
        Timepoint maxTime = new Timepoint(12, 13, 14);
        Timepoint input = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMaxTime(maxTime);

        Assert.assertFalse(limiter.isOutOfRange(input, HOUR_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeHourShouldReturnFalseWhenTimeEqualsSelectableTimeToTheHour() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertFalse(limiter.isOutOfRange(input, HOUR_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeHourShouldReturnFalseWhenTimeEqualsSelectableTimeToTheHour2() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = new Timepoint(13, 15, 15);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertFalse(limiter.isOutOfRange(input, HOUR_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeHourShouldReturnFalseWhenTimeEqualsSelectableTimeToTheHour3() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = new Timepoint(11);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertFalse(limiter.isOutOfRange(input, HOUR_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeShouldWorkWhenSelectableTimesContainsDuplicateEntries() {
        Timepoint[] selectableTimes = {
                new Timepoint(11),
                new Timepoint(12),
                new Timepoint(12),
                new Timepoint(13)
        };
        Timepoint input = new Timepoint(11, 30);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertTrue(limiter.isOutOfRange(input));
    }

    @Test
    public void isOutOfRangeShouldReturnTrueWhenInputIsInDisabledTimes() {
        Timepoint[] disabledTimes = {
                new Timepoint(11),
                new Timepoint(12),
                new Timepoint(13)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertTrue(limiter.isOutOfRange(input));
    }

    @Test
    public void isOutOfRangeShouldUseDisabledOverSelectable() {
        Timepoint[] disabledTimes = {
                new Timepoint(11),
                new Timepoint(12),
                new Timepoint(13)
        };
        Timepoint[] selectableTimes = {
                new Timepoint(12),
                new Timepoint(14)
        };
        Timepoint input = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setDisabledTimes(disabledTimes);
        limiter.setSelectableTimes(selectableTimes);

        Assert.assertTrue(limiter.isOutOfRange(input));
    }

    @Test
    public void isOutOfRangeHourShouldReturnFalseWhenADisabledTimeIsTestedWithSecondResolution() {
        // If there are only disabledTimes, there will still be other times that are valid
        Timepoint[] disabledTimes = {
                new Timepoint(12),
                new Timepoint(13),
                new Timepoint(14)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertFalse(limiter.isOutOfRange(input, HOUR_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeHourShouldReturnFalseWhenADisabledTimeIsTestedWithMinuteResolution() {
        // If there are only disabledTimes, there will still be other times that are valid
        Timepoint[] disabledTimes = {
                new Timepoint(12),
                new Timepoint(13),
                new Timepoint(14)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertFalse(limiter.isOutOfRange(input, HOUR_INDEX, Timepoint.TYPE.MINUTE));
    }

    @Test
    public void isOutOfRangeHourShouldReturnTrueWhenADisabledTimeIsTestedWithHourResolution() {
        // If there are only disabledTimes, there will still be other times that are valid
        Timepoint[] disabledTimes = {
                new Timepoint(12),
                new Timepoint(13),
                new Timepoint(14)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertTrue(limiter.isOutOfRange(input, HOUR_INDEX, Timepoint.TYPE.HOUR));
    }

    @Test
    public void isOutOfRangeMinuteShouldReturnFalseWhenADisabledTimeIsTestedWithSecondResolution() {
        // If there are only disabledTimes, there will still be other times that are valid
        Timepoint[] disabledTimes = {
                new Timepoint(12, 15),
                new Timepoint(13, 16),
                new Timepoint(14, 17)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertFalse(limiter.isOutOfRange(input, MINUTE_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void isOutOfRangeMinuteShouldReturnFalseWhenADisabledTimeIsTestedWithMinuteResolution() {
        // If there are only disabledTimes, there will still be other times that are valid
        Timepoint[] disabledTimes = {
                new Timepoint(12, 15),
                new Timepoint(13, 16),
                new Timepoint(14, 17)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertTrue(limiter.isOutOfRange(input, MINUTE_INDEX, Timepoint.TYPE.MINUTE));
    }

    @Test
    public void isOutOfRangeHourShouldReturnTrueWhenADisabledTimeCancelsASelectableTime() {
        Timepoint[] disabledTimes = {
                new Timepoint(12),
                new Timepoint(13)
        };
        Timepoint[] selectableTimes = {
                new Timepoint(12),
                new Timepoint(14),
                new Timepoint(15)
        };
        Timepoint input = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setDisabledTimes(disabledTimes);
        limiter.setSelectableTimes(selectableTimes);

        Assert.assertTrue(limiter.isOutOfRange(input, HOUR_INDEX, Timepoint.TYPE.SECOND));
    }

    @Test
    public void roundToNearestShouldWorkWhenSelectableTimesContainsDuplicateEntries() {
        Timepoint[] selectableTimes = {
                new Timepoint(11),
                new Timepoint(12),
                new Timepoint(12),
                new Timepoint(13)
        };
        Timepoint input = new Timepoint(12, 29);
        Timepoint expected = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.SECOND), expected);
    }

    @Test
    public void roundToNearestShouldReturnMaxTimeIfBiggerThanMaxTime() {
        Timepoint maxTime = new Timepoint(8);
        Timepoint input = new Timepoint(12);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMaxTime(maxTime);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.SECOND), maxTime);
    }

    @Test
    public void roundToNearestShouldReturnMinTimeIfSmallerThanMinTime() {
        Timepoint minTime = new Timepoint(8);
        Timepoint input = new Timepoint(7);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setMinTime(minTime);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.SECOND), minTime);
    }

    @Test
    public void roundToNearestShouldReturnInputIfNotOutOfRange() {
        Timepoint input = new Timepoint(12, 13, 14);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.SECOND), input);
    }

    @Test
    public void roundToNearestShouldReturnSelectableTime() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 14),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = new Timepoint(11);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.SECOND), selectableTimes[0]);
    }

    @Test
    public void roundToNearestShouldNotChangeTheMinutesWhenOptionIsSet() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12),
                new Timepoint(12, 13, 14),
                new Timepoint(15, 16, 17)
        };
        Timepoint input = new Timepoint(11, 12, 59);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertEquals(limiter.roundToNearest(input, Timepoint.TYPE.MINUTE, Timepoint.TYPE.SECOND).getHour(), input.getHour());
        Assert.assertEquals(limiter.roundToNearest(input, Timepoint.TYPE.MINUTE, Timepoint.TYPE.SECOND).getMinute(), input.getMinute());
    }

    @Test
    public void roundToNearestShouldNotChangeTheHourWhenOptionIsSet() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12),
                new Timepoint(12, 13, 14),
                new Timepoint(13),
                new Timepoint(15, 16, 17)
        };
        Timepoint input = new Timepoint(12, 59, 59);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertEquals(limiter.roundToNearest(input, Timepoint.TYPE.HOUR, Timepoint.TYPE.SECOND).getHour(), input.getHour());
    }

    @Test
    public void roundToNearestShouldNotChangeTheHourWhenOptionIsSet2() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12),
                new Timepoint(12, 13, 14),
                new Timepoint(13),
                new Timepoint(15, 16, 17)
        };
        Timepoint input = new Timepoint(15, 59, 59);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertEquals(limiter.roundToNearest(input, Timepoint.TYPE.HOUR, Timepoint.TYPE.SECOND).getHour(), input.getHour());
    }

    @Test
    public void roundToNearestShouldNotChangeAnythingWhenSecondOptionIsSet() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12),
                new Timepoint(12, 13, 14),
                new Timepoint(13),
                new Timepoint(15, 16, 17)
        };
        Timepoint input = new Timepoint(12, 59, 59);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertEquals(limiter.roundToNearest(input, Timepoint.TYPE.SECOND, Timepoint.TYPE.SECOND), input);
    }

    @Test
    public void roundToNearestShouldRoundToNearest() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(11, 12, 16),
                new Timepoint(12)
        };
        Timepoint input = new Timepoint(11, 12, 14);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.SECOND), selectableTimes[0]);
    }

    @Test
    public void roundToNearestShouldRoundToNearestSelectableThatIsNotDisabled() {
        Timepoint[] selectableTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13)
        };
        Timepoint[] disabledTimes = {
                new Timepoint(12, 13, 14)
        };
        Timepoint input = new Timepoint(12, 13, 15);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();

        limiter.setSelectableTimes(selectableTimes);
        limiter.setDisabledTimes(disabledTimes);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.SECOND), selectableTimes[2]);
    }

    @Test
    public void roundToNearestShouldRoundWithSecondIncrementsIfInputIsDisabled() {
        Timepoint[] disabledTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        Timepoint expected = new Timepoint(11, 12, 14);

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.SECOND), expected);
    }

    @Test
    public void roundToNearestShouldRoundWithSecondIncrementsIfInputIsDisabled2() {
        Timepoint[] disabledTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(11, 12, 14),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        Timepoint expected = new Timepoint(11, 12, 12);

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.SECOND), expected);
    }

    @Test
    public void roundToNearestShouldRoundWithSecondIncrementsIfInputIsDisabled3() {
        Timepoint[] disabledTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(11, 12, 12),
                new Timepoint(11, 12, 14),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        Timepoint expected = new Timepoint(11, 12, 15);

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.SECOND), expected);
    }

    @Test
    public void roundToNearestShouldRoundWithMinuteIncrementsIfInputIsDisabled() {
        Timepoint[] disabledTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        Timepoint expected = new Timepoint(11, 13, 13);

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.MINUTE), expected);
    }

    @Test
    public void roundToNearestShouldRoundWithHourIncrementsIfInputIsDisabled() {
        Timepoint[] disabledTimes = {
                new Timepoint(11, 12, 13),
                new Timepoint(12, 13, 14),
                new Timepoint(13, 14, 15)
        };
        Timepoint input = disabledTimes[0];
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        Timepoint expected = new Timepoint(10, 12, 13);

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.HOUR), expected);
    }

    @Test
    public void roundToNearestIfInputDisablesAtLowerAndUpperTime() {
        Timepoint[] disabledTimes = new Timepoint[180];
        for(int l=0; l<=59; l++){ disabledTimes[l] = new Timepoint(0, l); }
        for(int l=0; l<=59; l++){ disabledTimes[60+l] = new Timepoint(1, l); }
        for(int l=0; l<=59; l++){ disabledTimes[120+l] = new Timepoint(23, l); }

        Timepoint input = new Timepoint(0, 29, 0);
        DefaultTimepointLimiter limiter = new DefaultTimepointLimiter();
        Timepoint expected = new Timepoint(2, 0, 0);
        Timepoint expected2 = new Timepoint(2, 29, 0);

        limiter.setDisabledTimes(disabledTimes);

        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.MINUTE), expected);
        Assert.assertEquals(limiter.roundToNearest(input, null, Timepoint.TYPE.HOUR), expected2);
    }
}