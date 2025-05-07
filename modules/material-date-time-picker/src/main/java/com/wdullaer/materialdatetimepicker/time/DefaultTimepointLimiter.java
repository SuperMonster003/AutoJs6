package com.wdullaer.materialdatetimepicker.time;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.TreeSet;

import static com.wdullaer.materialdatetimepicker.time.TimePickerDialog.HOUR_INDEX;
import static com.wdullaer.materialdatetimepicker.time.TimePickerDialog.MINUTE_INDEX;

/**
 * An implementation of TimepointLimiter which implements the most common ways to restrict Timepoints
 * in a TimePickerDialog
 * Created by wdullaer on 20/06/17.
 */

class DefaultTimepointLimiter implements TimepointLimiter {
    private TreeSet<Timepoint> mSelectableTimes = new TreeSet<>();
    private TreeSet<Timepoint> mDisabledTimes = new TreeSet<>();
    private TreeSet<Timepoint> exclusiveSelectableTimes = new TreeSet<>();
    private Timepoint mMinTime;
    private Timepoint mMaxTime;

    DefaultTimepointLimiter() {}

    @SuppressWarnings("WeakerAccess")
    public DefaultTimepointLimiter(Parcel in) {
        mMinTime = in.readParcelable(Timepoint.class.getClassLoader());
        mMaxTime = in.readParcelable(Timepoint.class.getClassLoader());
        mSelectableTimes.addAll(Arrays.asList(in.createTypedArray(Timepoint.CREATOR)));
        mDisabledTimes.addAll(Arrays.asList(in.createTypedArray(Timepoint.CREATOR)));
        exclusiveSelectableTimes = getExclusiveSelectableTimes(mSelectableTimes, mDisabledTimes);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mMinTime, flags);
        out.writeParcelable(mMaxTime, flags);
        out.writeTypedArray(mSelectableTimes.toArray(new Timepoint[mSelectableTimes.size()]), flags);
        out.writeTypedArray(mDisabledTimes.toArray(new Timepoint[mDisabledTimes.size()]), flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @SuppressWarnings("WeakerAccess")
    public static final Parcelable.Creator<DefaultTimepointLimiter> CREATOR
            = new Parcelable.Creator<DefaultTimepointLimiter>() {
        public DefaultTimepointLimiter createFromParcel(Parcel in) {
            return new DefaultTimepointLimiter(in);
        }

        public DefaultTimepointLimiter[] newArray(int size) {
            return new DefaultTimepointLimiter[size];
        }
    };

    void setMinTime(@NonNull Timepoint minTime) {
        if(mMaxTime != null && minTime.compareTo(mMaxTime) > 0)
            throw new IllegalArgumentException("Minimum time must be smaller than the maximum time");
        mMinTime = minTime;
    }

    void setMaxTime(@NonNull Timepoint maxTime) {
        if(mMinTime != null && maxTime.compareTo(mMinTime) < 0)
            throw new IllegalArgumentException("Maximum time must be greater than the minimum time");
        mMaxTime = maxTime;
    }

    void setSelectableTimes(@NonNull Timepoint[] selectableTimes) {
        mSelectableTimes.addAll(Arrays.asList(selectableTimes));
        exclusiveSelectableTimes = getExclusiveSelectableTimes(mSelectableTimes, mDisabledTimes);
    }

    void setDisabledTimes(@NonNull Timepoint[] disabledTimes) {
        mDisabledTimes.addAll(Arrays.asList(disabledTimes));
        exclusiveSelectableTimes = getExclusiveSelectableTimes(mSelectableTimes, mDisabledTimes);
    }

    @Nullable Timepoint getMinTime() {
        return mMinTime;
    }

    @Nullable Timepoint getMaxTime() {
        return mMaxTime;
    }

    @NonNull Timepoint[] getSelectableTimes() {
        return mSelectableTimes.toArray(new Timepoint[mSelectableTimes.size()]);
    }

    @NonNull Timepoint[] getDisabledTimes() {
        return mDisabledTimes.toArray(new Timepoint[mDisabledTimes.size()]);
    }

    @NonNull private TreeSet<Timepoint> getExclusiveSelectableTimes(@NonNull TreeSet<Timepoint> selectable, @NonNull TreeSet<Timepoint> disabled) {
        TreeSet<Timepoint> output = new TreeSet<>(selectable);
        output.removeAll(disabled);
        return output;
    }

    @Override
    public boolean isOutOfRange(@Nullable Timepoint current, int index, @NonNull Timepoint.TYPE resolution) {
        if (current == null) return false;

        if (index == HOUR_INDEX) {
            if (mMinTime != null && mMinTime.getHour() > current.getHour()) return true;

            if (mMaxTime != null && mMaxTime.getHour()+1 <= current.getHour()) return true;

            if (!exclusiveSelectableTimes.isEmpty()) {
                Timepoint ceil = exclusiveSelectableTimes.ceiling(current);
                Timepoint floor = exclusiveSelectableTimes.floor(current);
                return !(current.equals(ceil, Timepoint.TYPE.HOUR) || current.equals(floor, Timepoint.TYPE.HOUR));
            }

            if (!mDisabledTimes.isEmpty() && resolution == Timepoint.TYPE.HOUR) {
                Timepoint ceil = mDisabledTimes.ceiling(current);
                Timepoint floor = mDisabledTimes.floor(current);
                return current.equals(ceil, Timepoint.TYPE.HOUR) || current.equals(floor, Timepoint.TYPE.HOUR);
            }

            return false;
        }
        else if (index == MINUTE_INDEX) {
            if (mMinTime != null) {
                Timepoint roundedMin = new Timepoint(mMinTime.getHour(), mMinTime.getMinute());
                if (roundedMin.compareTo(current) > 0) return true;
            }

            if (mMaxTime != null) {
                Timepoint roundedMax = new Timepoint(mMaxTime.getHour(), mMaxTime.getMinute(), 59);
                if (roundedMax.compareTo(current) < 0) return true;
            }

            if (!exclusiveSelectableTimes.isEmpty()) {
                Timepoint ceil = exclusiveSelectableTimes.ceiling(current);
                Timepoint floor = exclusiveSelectableTimes.floor(current);
                return !(current.equals(ceil, Timepoint.TYPE.MINUTE) || current.equals(floor, Timepoint.TYPE.MINUTE));
            }

            if (!mDisabledTimes.isEmpty() && resolution == Timepoint.TYPE.MINUTE) {
                Timepoint ceil = mDisabledTimes.ceiling(current);
                Timepoint floor = mDisabledTimes.floor(current);
                boolean ceilExclude = current.equals(ceil, Timepoint.TYPE.MINUTE);
                boolean floorExclude = current.equals(floor, Timepoint.TYPE.MINUTE);
                return ceilExclude || floorExclude;
            }

            return false;
        }
        else return isOutOfRange(current);
    }

    public boolean isOutOfRange(@NonNull Timepoint current) {
        if (mMinTime != null && mMinTime.compareTo(current) > 0) return true;

        if (mMaxTime != null && mMaxTime.compareTo(current) < 0) return true;

        if (!exclusiveSelectableTimes.isEmpty()) return !exclusiveSelectableTimes.contains(current);

        return mDisabledTimes.contains(current);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean isAmDisabled() {
        Timepoint midday = new Timepoint(12);

        if (mMinTime != null && mMinTime.compareTo(midday) >= 0) return true;

        if (!exclusiveSelectableTimes.isEmpty()) return exclusiveSelectableTimes.first().compareTo(midday) >= 0;

        return false;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean isPmDisabled() {
        Timepoint midday = new Timepoint(12);

        if (mMaxTime != null && mMaxTime.compareTo(midday) < 0) return true;

        if (!exclusiveSelectableTimes.isEmpty()) return exclusiveSelectableTimes.last().compareTo(midday) < 0;

        return false;
    }

    @Override
    public @NonNull Timepoint roundToNearest(@NonNull Timepoint time,@Nullable Timepoint.TYPE type, @NonNull Timepoint.TYPE resolution) {
        if (mMinTime != null && mMinTime.compareTo(time) > 0) return mMinTime;

        if (mMaxTime != null && mMaxTime.compareTo(time) < 0) return mMaxTime;

        // type == SECOND: cannot change anything, return input
        if (type == Timepoint.TYPE.SECOND) return time;

        if (!exclusiveSelectableTimes.isEmpty()) {
            Timepoint floor = exclusiveSelectableTimes.floor(time);
            Timepoint ceil = exclusiveSelectableTimes.ceiling(time);

            if (floor == null || ceil == null) {
                Timepoint t = floor == null ? ceil : floor;
                if (type == null) return t;
                if (t.getHour() != time.getHour()) return time;
                if (type == Timepoint.TYPE.MINUTE && t.getMinute() != time.getMinute()) return time;
                return t;
            }

            if (type == Timepoint.TYPE.HOUR) {
                if (floor.getHour() != time.getHour() && ceil.getHour() == time.getHour()) return ceil;
                if (floor.getHour() == time.getHour() && ceil.getHour() != time.getHour()) return floor;
                if (floor.getHour() != time.getHour() && ceil.getHour() != time.getHour()) return time;
            }

            if (type == Timepoint.TYPE.MINUTE) {
                if (floor.getHour() != time.getHour() && ceil.getHour() != time.getHour()) return time;
                if (floor.getHour() != time.getHour() && ceil.getHour() == time.getHour()) {
                    return ceil.getMinute() == time.getMinute() ? ceil : time;
                }
                if (floor.getHour() == time.getHour() && ceil.getHour() != time.getHour()) {
                    return floor.getMinute() == time.getMinute() ? floor : time;
                }
                if (floor.getMinute() != time.getMinute() && ceil.getMinute() == time.getMinute()) return ceil;
                if (floor.getMinute() == time.getMinute() && ceil.getMinute() != time.getMinute()) return floor;
                if (floor.getMinute() != time.getMinute() && ceil.getMinute() != time.getMinute()) return time;
            }

            int floorDist = Math.abs(time.compareTo(floor));
            int ceilDist = Math.abs(time.compareTo(ceil));

            return floorDist < ceilDist ? floor : ceil;
        }

        if (!mDisabledTimes.isEmpty()) {
            // if type matches resolution: cannot change anything, return input
            if (type != null && type == resolution) return time;

            if (resolution == Timepoint.TYPE.SECOND) {
                if (!mDisabledTimes.contains(time)) return time;
                return searchValidTimePoint(time, type, resolution);
            }

            if (resolution == Timepoint.TYPE.MINUTE) {
                Timepoint ceil = mDisabledTimes.ceiling(time);
                Timepoint floor = mDisabledTimes.floor(time);
                boolean ceilDisabled = time.equals(ceil, Timepoint.TYPE.MINUTE);
                boolean floorDisabled = time.equals(floor, Timepoint.TYPE.MINUTE);

                if (ceilDisabled || floorDisabled) return searchValidTimePoint(time, type, resolution);
                return time;
            }

            if (resolution == Timepoint.TYPE.HOUR) {
                Timepoint ceil = mDisabledTimes.ceiling(time);
                Timepoint floor = mDisabledTimes.floor(time);
                boolean ceilDisabled = time.equals(ceil, Timepoint.TYPE.HOUR);
                boolean floorDisabled = time.equals(floor, Timepoint.TYPE.HOUR);

                if (ceilDisabled || floorDisabled) return searchValidTimePoint(time, type, resolution);
                return time;
            }
        }

        return time;
    }

    private Timepoint searchValidTimePoint(@NonNull Timepoint time, @Nullable Timepoint.TYPE type, @NonNull Timepoint.TYPE resolution) {
        Timepoint forward = new Timepoint(time);
        Timepoint backward = new Timepoint(time);
        int iteration = 0;
        int resolutionMultiplier = 1;
        if (resolution == Timepoint.TYPE.MINUTE) resolutionMultiplier = 60;
        if (resolution == Timepoint.TYPE.SECOND) resolutionMultiplier = 3600;

        while (iteration < 24 * resolutionMultiplier) {
            iteration++;
            forward.add(resolution, 1);
            backward.add(resolution, -1);

            if (type == null || forward.get(type) == time.get(type)) {
                Timepoint forwardCeil = mDisabledTimes.ceiling(forward);
                Timepoint forwardFloor = mDisabledTimes.floor(forward);
                if (!forward.equals(forwardCeil, resolution) && !forward.equals(forwardFloor, resolution))
                    return forward;
            }

            if (type == null || backward.get(type) == time.get(type)) {
                Timepoint backwardCeil = mDisabledTimes.ceiling(backward);
                Timepoint backwardFloor = mDisabledTimes.floor(backward);
                if (!backward.equals(backwardCeil, resolution) && !backward.equals(backwardFloor, resolution))
                    return backward;
            }

            if (type != null && backward.get(type) != time.get(type) && forward.get(type) != time.get(type))
                break;
        }
        // If this step is reached, the user has disabled all timepoints
        return time;
    }
}
