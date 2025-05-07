/*
 * Copyright (C) 2017 Wouter Dullaert
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wdullaer.materialdatetimepicker.date;

import android.os.Parcelable;
import androidx.annotation.NonNull;

import java.util.Calendar;

@SuppressWarnings("WeakerAccess")
public interface DateRangeLimiter extends Parcelable {
    /**
     * getMinYear returns the minimum selectable year of the picker.
     * This method should match getStartDate()
     * It is recommended to keep the default implementation
     * This method will be removed from this interface at the next semver major
     * @return the minimum selectable year of the picker
     */
    default int getMinYear() {
        return getStartDate().get(Calendar.YEAR);
    }

    /**
     * getMaxYear returns the maximum selectable year of the picker
     * This method should semantically match getEndDate()
     * It is recommended to keep the default implementation.
     * This method will be removed from this interface at the next semver major
     * @return the maximum selectable year of the picker
     */
    default int getMaxYear() {
        return getEndDate().get(Calendar.YEAR);
    }

    /**
     * getStartDate returns the minimum selectable date of the picker
     * It is called in various places, including the hot loop when rendering.
     * It is highly recommended to keep this method as simple as possible
     * @return the minimum selectable date of the picker
     */
    @NonNull Calendar getStartDate();

    /**
     * getEndDate returns the maximum selectable date of the picker
     * It is called in various places, including the hot loop when rendering.
     * It is highly recommended to keep this method as simple as possible
     * @return the maximum selectable date of the picker
     */
    @NonNull Calendar getEndDate();

    /**
     * isOutOfRange is called for each date when it is about to be rendered
     * Returning true from this function will cause that particular day to be non selectable
     * Since this code is called in the inner loop when rendering, it is highly recommended to
     * keep the logic as simple as possible
     * @param year the year of the date
     * @param month the month of the date
     * @param day the day of the month of the date
     * @return true if the date should be disabled, false otherwise
     */
    boolean isOutOfRange(int year, int month, int day);

    /**
     * setToNearestDate rounds a Date to the nearest selectable value.
     * It is called each time the user makes a year selection: the newly resulting date might not be
     * valid according to the constraints set by the limiter.
     * This method is not called when the user selects a day, since the picker prevents the
     * selection of values which satisfy `isOutOfRange`
     * @param day a date with the current user selection
     * @return the date after rounding to a selectable value
     */
    @NonNull Calendar setToNearestDate(@NonNull Calendar day);
}