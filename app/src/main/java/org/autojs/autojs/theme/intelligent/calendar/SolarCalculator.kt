package org.autojs.autojs.theme.intelligent.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise and sunset by the NOAA solar position algorithm, offline (roadmap P3.1). Accurate to about a minute
 * between the polar circles; uses the standard zenith of 90.833 degrees (refraction and the solar disc).
 * zh-CN: 按 NOAA 太阳位置算法离线计算日出日落 (Roadmap P3.1). 在南北极圈之间误差约 1 分钟;
 * 使用 90.833 度的标准天顶角 (计入大气折射与日面半径).
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object SolarCalculator {

    sealed interface Day {

        data class Normal(val sunrise: Instant, val sunset: Instant) : Day

        /** The sun stays above the horizon all day. zh-CN: 太阳全天在地平线以上. */
        data object PolarDay : Day

        /** The sun stays below the horizon all day. zh-CN: 太阳全天在地平线以下. */
        data object PolarNight : Day

    }

    private const val ZENITH = 90.833

    /**
     * Sunrise and sunset of the local calendar [date] at [latitude] / [longitude] (degrees, north and east positive).
     * The result is computed around the solar noon of that date, so it belongs to the local day whatever the time zone.
     * zh-CN: 本地日历日期 [date] 在 [latitude] / [longitude] (度, 北纬与东经为正) 处的日出与日落.
     * 结果围绕该日期的太阳正午计算, 因此无论时区如何都属于当地的这一天.
     */
    fun dayOf(date: LocalDate, latitude: Double, longitude: Double): Day {
        val julianDay = julianDay(date)
        val rise = eventMinutes(rise = true, julianDay, latitude, longitude) ?: return polar(julianDay, latitude, longitude)
        val set = eventMinutes(rise = false, julianDay, latitude, longitude) ?: return polar(julianDay, latitude, longitude)
        val midnight = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        return Day.Normal(midnight.plusMillis((rise * 60_000).toLong()), midnight.plusMillis((set * 60_000).toLong()))
    }

    /** Minutes after 00:00 UTC of the Julian day, refined once at the event time; null when the sun does not cross the horizon. */
    private fun eventMinutes(rise: Boolean, julianDay: Double, latitude: Double, longitude: Double): Double? {
        val first = eventMinutesAt(rise, julianDay, latitude, longitude) ?: return null
        return eventMinutesAt(rise, julianDay + first / 1440.0, latitude, longitude)
    }

    private fun eventMinutesAt(rise: Boolean, at: Double, latitude: Double, longitude: Double): Double? {
        val t = julianCentury(at)
        val hourAngle = hourAngle(latitude, declination(t)) ?: return null
        val delta = longitude + Math.toDegrees(if (rise) hourAngle else -hourAngle)
        return 720.0 - 4.0 * delta - equationOfTime(t)
    }

    private fun polar(julianDay: Double, latitude: Double, longitude: Double): Day {
        // At solar noon the sun is at its highest; above the horizon there means polar day.
        // zh-CN: 太阳正午时高度最高; 此时位于地平线以上即为极昼.
        val noon = julianDay + (720.0 - 4.0 * longitude) / 1440.0
        val declination = declination(julianCentury(noon))
        val altitude = 90.0 - kotlin.math.abs(latitude - declination)
        return if (altitude > 90.0 - ZENITH) Day.PolarDay else Day.PolarNight
    }

    /** Julian day at 00:00 UTC of [date]. zh-CN: [date] 当天 00:00 UTC 的儒略日. */
    fun julianDay(date: LocalDate): Double {
        var year = date.year
        var month = date.monthValue
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + date.dayOfMonth + b - 1524.5
    }

    private fun julianCentury(julianDay: Double) = (julianDay - 2451545.0) / 36525.0

    private fun geometricMeanLongitude(t: Double): Double {
        val longitude = 280.46646 + t * (36000.76983 + t * 0.0003032)
        return ((longitude % 360.0) + 360.0) % 360.0
    }

    private fun geometricMeanAnomaly(t: Double) = 357.52911 + t * (35999.05029 - 0.0001537 * t)

    private fun eccentricity(t: Double) = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun equationOfCenter(t: Double): Double {
        val m = Math.toRadians(geometricMeanAnomaly(t))
        return sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(2 * m) * (0.019993 - 0.000101 * t) + sin(3 * m) * 0.000289
    }

    private fun apparentLongitude(t: Double): Double {
        val trueLongitude = geometricMeanLongitude(t) + equationOfCenter(t)
        val omega = 125.04 - 1934.136 * t
        return trueLongitude - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
    }

    private fun obliquity(t: Double): Double {
        val seconds = 21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))
        val mean = 23.0 + (26.0 + seconds / 60.0) / 60.0
        val omega = 125.04 - 1934.136 * t
        return mean + 0.00256 * cos(Math.toRadians(omega))
    }

    private fun declination(t: Double): Double {
        return Math.toDegrees(asin(sin(Math.toRadians(obliquity(t))) * sin(Math.toRadians(apparentLongitude(t)))))
    }

    /** In minutes. zh-CN: 单位为分钟. */
    private fun equationOfTime(t: Double): Double {
        val epsilon = Math.toRadians(obliquity(t))
        val l0 = Math.toRadians(geometricMeanLongitude(t))
        val e = eccentricity(t)
        val m = Math.toRadians(geometricMeanAnomaly(t))
        val y = tan(epsilon / 2).let { it * it }
        val value = y * sin(2 * l0) - 2 * e * sin(m) + 4 * e * y * sin(m) * cos(2 * l0) - 0.5 * y * y * sin(4 * l0) - 1.25 * e * e * sin(2 * m)
        return Math.toDegrees(value) * 4.0
    }

    /** Hour angle of sunrise in radians, or null when the sun does not cross the horizon. zh-CN: 日出时角 (弧度); 太阳不穿过地平线时为 null. */
    private fun hourAngle(latitude: Double, declination: Double): Double? {
        val lat = Math.toRadians(latitude)
        val dec = Math.toRadians(declination)
        val argument = cos(Math.toRadians(ZENITH)) / (cos(lat) * cos(dec)) - tan(lat) * tan(dec)
        return if (argument in -1.0..1.0) acos(argument) else null
    }

}
