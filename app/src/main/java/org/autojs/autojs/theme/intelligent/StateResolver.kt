package org.autojs.autojs.theme.intelligent

import org.autojs.autojs.theme.intelligent.calendar.SolarCalculator
import org.autojs.autojs.theme.intelligent.calendar.TimeZoneLocations
import java.time.Instant
import java.time.Month
import java.time.ZoneId

/**
 * Device facts read by the engine glue for the device state sources (roadmap P3.3); [batteryLevel] is a percentage.
 * zh-CN: 引擎胶水层为设备类状态源读取的设备状态 (Roadmap P3.3); [batteryLevel] 为百分比.
 */
data class DeviceFacts(
    val isNight: Boolean = false,
    val batteryLevel: Int? = null,
    val charging: Boolean = false,
    val powerSave: Boolean = false,
    val accessibilityServiceRunning: Boolean = false,
    val scriptRunning: Boolean = false,
)

/**
 * The current state and, for time based sources, the time it may change next (null when changes are event driven).
 * zh-CN: 当前状态, 以及时间类状态源下一次可能变化的时间 (由事件驱动的状态源为 null).
 */
data class StateResolution(val stateKey: String, val nextChangeAt: Long?)

/**
 * Resolves the current state of a [StateSource] (roadmap P3.1 to P3.3); pure, so the engine glue only gathers facts.
 * zh-CN: 计算 [StateSource] 的当前状态 (Roadmap P3.1 至 P3.3); 纯函数, 引擎胶水层只负责收集设备状态.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object StateResolver {

    /** Battery points beyond a threshold needed to leave the current range. zh-CN: 离开当前区间所需越过阈值的电量百分点. */
    const val BATTERY_HYSTERESIS = 2

    fun resolve(source: StateSource, now: Long, zone: ZoneId, facts: DeviceFacts, previousKey: String? = null): StateResolution = when (source) {
        StateSource.None, is StateSource.Unsupported -> StateResolution(StateKeys.DEFAULT, null)
        StateSource.NightMode -> StateResolution(if (facts.isNight) StateKeys.NIGHT else StateKeys.DAY, null)
        is StateSource.TimePeriods -> periods(source, now, zone)
        is StateSource.SunriseSunset -> sun(source, now, zone)
        is StateSource.Weekday -> weekday(source, now, zone)
        is StateSource.Seasons -> season(source, now, zone)
        is StateSource.Battery -> StateResolution(StateKeys.battery(batteryIndex(source.thresholds, facts.batteryLevel, previousKey)), null)
        StateSource.Charging -> onOff(facts.charging)
        StateSource.PowerSave -> onOff(facts.powerSave)
        StateSource.AccessibilityService -> onOff(facts.accessibilityServiceRunning)
        StateSource.ScriptRunning -> onOff(facts.scriptRunning)
    }

    private fun onOff(on: Boolean) = StateResolution(if (on) StateKeys.ON else StateKeys.OFF, null)

    private fun periods(source: StateSource.TimePeriods, now: Long, zone: ZoneId): StateResolution {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val minute = local.hour * 60 + local.minute
        val index = source.starts.indexOfLast { it <= minute }.takeIf { it >= 0 } ?: source.starts.lastIndex
        return StateResolution(StateKeys.period(index), DailySchedule.nextDue(source.starts, now, zone))
    }

    private fun sun(source: StateSource.SunriseSunset, now: Long, zone: ZoneId): StateResolution {
        val location = source.location ?: TimeZoneLocations.infer(zone, Instant.ofEpochMilli(now)).location
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val midnight = DailySchedule.nextMidnight(now, zone)
        when (SolarCalculator.dayOf(today, location.latitude, location.longitude)) {
            SolarCalculator.Day.PolarDay -> return StateResolution(StateKeys.DAY, midnight)
            SolarCalculator.Day.PolarNight -> return StateResolution(StateKeys.NIGHT, midnight)
            is SolarCalculator.Day.Normal -> Unit
        }
        // Events of the neighbouring days decide the state around midnight. zh-CN: 相邻日期的事件决定午夜前后的状态.
        val events = (-1L..1L).flatMap { offset ->
            when (val day = SolarCalculator.dayOf(today.plusDays(offset), location.latitude, location.longitude)) {
                is SolarCalculator.Day.Normal -> listOf(day.sunrise.toEpochMilli() to true, day.sunset.toEpochMilli() to false)
                else -> emptyList()
            }
        }.sortedBy { it.first }
        val isDay = events.lastOrNull { it.first <= now }?.second ?: false
        val next = events.firstOrNull { it.first > now }?.first ?: midnight
        return StateResolution(if (isDay) StateKeys.DAY else StateKeys.NIGHT, next)
    }

    private fun weekday(source: StateSource.Weekday, now: Long, zone: ZoneId): StateResolution {
        val day = Instant.ofEpochMilli(now).atZone(zone).dayOfWeek.value
        val key = when {
            !source.workdaysOnly -> StateKeys.weekday(day)
            day <= 5 -> StateKeys.WORKDAY
            else -> StateKeys.WEEKEND
        }
        return StateResolution(key, DailySchedule.nextMidnight(now, zone))
    }

    private fun season(source: StateSource.Seasons, now: Long, zone: ZoneId): StateResolution {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val hemisphere = source.hemisphere ?: TimeZoneLocations.infer(zone, local.toInstant()).location.hemisphere
        val season = seasonOf(local.month, hemisphere)
        val nextMonth = local.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return StateResolution(StateKeys.season(season), nextMonth)
    }

    /** Meteorological seasons; the south is six months ahead. zh-CN: 气象季节; 南半球与北半球相差六个月. */
    fun seasonOf(month: Month, hemisphere: Hemisphere): Season {
        val shifted = if (hemisphere == Hemisphere.SOUTH) month.plus(6) else month
        return when (shifted) {
            Month.MARCH, Month.APRIL, Month.MAY -> Season.SPRING
            Month.JUNE, Month.JULY, Month.AUGUST -> Season.SUMMER
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> Season.AUTUMN
            else -> Season.WINTER
        }
    }

    /**
     * The battery range of [level]: range `i` holds levels from `thresholds[i - 1]` (inclusive) up to `thresholds[i]`.
     * Leaving the range of [previousKey] needs [BATTERY_HYSTERESIS] extra points past its bound, which stops flicker
     * around a threshold. An unknown level keeps the previous range, or assumes a full battery.
     * zh-CN: [level] 所在的电量区间: 区间 `i` 包含从 `thresholds[i - 1]` (含) 到 `thresholds[i]` 的电量.
     * 离开 [previousKey] 所在区间时须越过其边界 [BATTERY_HYSTERESIS] 个百分点, 以免在阈值附近反复切换.
     * 电量未知时保持原区间, 否则视为满电.
     */
    fun batteryIndex(thresholds: List<Int>, level: Int?, previousKey: String?): Int {
        val previous = previousKey?.removePrefix("battery:")?.takeIf { previousKey.startsWith("battery:") }?.toIntOrNull()
            ?.takeIf { it in 0..thresholds.size }
        level ?: return previous ?: thresholds.size
        val raw = thresholds.count { level >= it }
        if (previous == null || raw == previous) return raw
        val lower = thresholds.getOrNull(previous - 1)
        val upper = thresholds.getOrNull(previous)
        val leaves = (upper != null && level >= upper + BATTERY_HYSTERESIS) || (lower != null && level < lower - BATTERY_HYSTERESIS)
        return if (leaves) raw else previous
    }

}
