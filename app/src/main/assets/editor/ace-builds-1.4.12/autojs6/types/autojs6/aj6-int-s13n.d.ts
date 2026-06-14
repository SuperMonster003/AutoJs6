// Type definitions for AutoJs6 internal module s13n
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.7.3
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/s13n/S13n.kt
 */

import ColorHex = Colors.ColorHex;
import ColorName = Colors.ColorName;
import ColorInt = Colors.ColorInt;

declare namespace Internal {

    interface S13n {

        color(o: OmniColor): ColorInt;

        throwable(o: OmniThrowable): java.lang.Throwable;

        point(o: { x: number, y: number }): android.graphics.Point;
        point(o: android.graphics.Point): android.graphics.Point;
        point(o: org.opencv.core.Point): android.graphics.Point;
        point(o: [number, number]): android.graphics.Point;
        point(x: number, y?: number): android.graphics.Point;

        time(source: S13n.TimeSourceParam, options: {
            fromUnit?: S13n.TimeUnitParam;
            toUnit?: S13n.TimeUnitParam;
        }): number;
        time(source: S13n.TimeSourceParam, fromUnit?: S13n.TimeUnitParam, toUnit?: S13n.TimeUnitParam): number;

        bytes(source: Converter.BytesValue, fromUnit?: S13n.BytesUnitParam): Converter.BytesResult;

    }

    namespace S13n {
        type BytesUnitParam = Converter.BytesUnit | string;
        type TimeSourceParam = number | string | Date | Tuple2<number | string | Date>;
        type TimeUnitParam = TimeUnit | TimeUnitString | string;
        type TimeUnitString =
            'NANO' | 'NANOS' | 'NANOSECONDS' |
            'MICRO' | 'MICROS' | 'MICROSECONDS' |
            'MILLI' | 'MILLIS' | 'MILLISECONDS' | 'MS' |
            'S' | 'SECONDS' | 'SECS' |
            'M' | 'MINUTES' | 'MIN' | 'MINS' |
            'H' | 'HOURS' | 'HR' | 'HRS' |
            'D' | 'DAYS'
            |
            'nano' | 'nanos' | 'nanoseconds' |
            'micro' | 'micros' | 'microseconds' |
            'milli' | 'millis' | 'milliseconds' | 'ms' |
            's' | 'seconds' | 'secs' |
            'm' | 'minutes' | 'min' | 'mins' |
            'h' | 'hours' | 'hr' | 'hrs' |
            'd' | 'days'
            ;
    }

}

declare type OmniColor = ColorHex | ColorInt | ColorName | Internal.Color | org.autojs.autojs.theme.ThemeColor;

declare type OmniRegion = [X?, Y?, Width?, Height?] | org.opencv.core.Rect | android.graphics.Rect;

declare type OmniThrowable = java.lang.Throwable | string | AutoJsError | Error;

declare type OmniVibrationPattern = number[] | string;
