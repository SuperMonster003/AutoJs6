package com.wdullaer.materialdatetimepicker.time;

/**
 * A collection of methods which need to be shared with all components of the TimePicker
 *
 * Created by wdullaer on 6/10/15.
 */
interface TimePickerController {
    /**
     * @return boolean - true if the dark theme should be used
     */
    boolean isThemeDark();

    /**
     * @return boolean - true if 24 hour mode is used / false if AM/PM is used
     */
    boolean is24HourMode();

    /**
     * @return int - the accent color currently in use
     */
    int getAccentColor();

    /**
     * @return Version - The current version to render
     */
    TimePickerDialog.Version getVersion();

    /**
     * Request the device to vibrate
     */
    void tryVibrate();

    /**
     * @param time Timepoint - the selected point in time
     * @param index int - The current view to consider when calculating the range
     * @return boolean - true if this is not a selectable value
     */
    boolean isOutOfRange(Timepoint time, int index);

    /**
     * @return boolean - true if AM times are outside the range of valid selections
     */
    boolean isAmDisabled();

    /**
     * @return boolean - true if PM times are outside the range of valid selections
     */
    boolean isPmDisabled();

    /**
     * Will round the given Timepoint to the nearest valid Timepoint given the following restrictions:
     *   - TYPE.HOUR, it will just round to the next valid point, possible adjusting minutes and seconds
     *   - TYPE.MINUTE, it will round to the next valid point, without adjusting the hour, but possibly adjusting the seconds
     *   - TYPE.SECOND, it will round to the next valid point, only adjusting the seconds
     * @param time Timepoint - the timepoint to validate
     * @param type Timepoint.TYPE - whether we should round the hours, minutes or seconds
     * @return timepoint - the nearest valid timepoint
     */
    Timepoint roundToNearest(Timepoint time, Timepoint.TYPE type);
}
