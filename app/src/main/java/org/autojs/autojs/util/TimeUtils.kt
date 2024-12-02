package org.autojs.autojs.util

import java.util.concurrent.TimeUnit

/**
 * Created by SuperMonster003 on Jan 27, 2024.
 */
object TimeUtils {

    @JvmStatic
    fun toNanos(timeUtil: TimeUnit, duration: Long) = timeUtil.toNanos(duration).toDouble() / TimeUnit.NANOSECONDS.toNanos(1)

    @JvmStatic
    fun toMicros(timeUtil: TimeUnit, duration: Long) = timeUtil.toNanos(duration).toDouble() / TimeUnit.MICROSECONDS.toNanos(1)

    @JvmStatic
    fun toMillis(timeUtil: TimeUnit, duration: Long) = timeUtil.toNanos(duration).toDouble() / TimeUnit.MILLISECONDS.toNanos(1)

    @JvmStatic
    fun toSeconds(timeUtil: TimeUnit, duration: Long) = timeUtil.toNanos(duration).toDouble() / TimeUnit.SECONDS.toNanos(1)

    @JvmStatic
    fun toMinutes(timeUtil: TimeUnit, duration: Long) = timeUtil.toNanos(duration).toDouble() / TimeUnit.MINUTES.toNanos(1)

    @JvmStatic
    fun toHours(timeUtil: TimeUnit, duration: Long) = timeUtil.toNanos(duration).toDouble() / TimeUnit.HOURS.toNanos(1)

    @JvmStatic
    fun toDays(timeUtil: TimeUnit, duration: Long) = timeUtil.toNanos(duration).toDouble() / TimeUnit.DAYS.toNanos(1)

}
