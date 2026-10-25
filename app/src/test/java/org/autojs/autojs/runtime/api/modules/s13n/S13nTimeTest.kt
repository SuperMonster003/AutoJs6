package org.autojs.autojs.runtime.api.modules.s13n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * `s13n.time(source, fromUnit, toUnit)` resolves the `TimeUtils.toXxx` conversion for the target unit by name;
 * every one of the seven conversions has to stay reachable through string units and `TimeUnit` values.
 * zh-CN: `s13n.time(source, fromUnit, toUnit)` 按名字解析目标单位对应的 `TimeUtils.toXxx` 换算函数;
 * 七个换算函数都必须能通过字符串单位与 `TimeUnit` 值到达.
 */
class S13nTimeTest {

    @Test
    fun `converts between string units`() {
        assertEquals(1.5, S13n.time(arrayOf<Any?>(90, "s", "min")), 0.0)
        assertEquals(24.0, S13n.time(arrayOf<Any?>(1, "d", "h")), 0.0)
        assertEquals(1.5, S13n.time(arrayOf<Any?>(1500, "ms", "s")), 0.0)
        assertEquals(7_200_000.0, S13n.time(arrayOf<Any?>(2, "hours", "milliseconds")), 0.0)
        assertEquals(2_000_000.0, S13n.time(arrayOf<Any?>(2, "s", "micros")), 0.0)
        assertEquals(3_000_000_000.0, S13n.time(arrayOf<Any?>(3, "seconds", "nanos")), 0.0)
        assertEquals(0.5, S13n.time(arrayOf<Any?>(12, "h", "days")), 0.0)
    }

    @Test
    fun `defaults both units to milliseconds`() {
        assertEquals(1000.0, S13n.time(arrayOf<Any?>(1, "s")), 0.0)
        assertEquals(60.0, S13n.time(arrayOf<Any?>(60_000, null, "s")), 0.0)
        assertEquals(1234.0, S13n.time(arrayOf<Any?>(1234)), 0.0)
    }

    @Test
    fun `reaches every conversion function through TimeUnit target values`() {
        // A TimeUnit source unit is taken for an options object by `time`, so the source stays a string here.
        // zh-CN: `time` 会把 TimeUnit 类型的源单位当作选项对象, 因此这里的源单位仍用字符串.
        val expected = mapOf(
            TimeUnit.NANOSECONDS to 8.64e13,
            TimeUnit.MICROSECONDS to 8.64e10,
            TimeUnit.MILLISECONDS to 8.64e7,
            TimeUnit.SECONDS to 86_400.0,
            TimeUnit.MINUTES to 1_440.0,
            TimeUnit.HOURS to 24.0,
            TimeUnit.DAYS to 1.0,
        )
        assertEquals(TimeUnit.values().toSet(), expected.keys)
        expected.forEach { (unit, value) ->
            assertEquals(unit.name, value, S13n.time(arrayOf<Any?>(1, "d", unit)), 0.0)
        }
    }

}
