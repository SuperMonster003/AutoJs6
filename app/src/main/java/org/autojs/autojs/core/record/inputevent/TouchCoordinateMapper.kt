package org.autojs.autojs.core.record.inputevent

import android.content.Context
import android.util.Log
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.util.RootUtils
import kotlin.math.roundToInt

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 7, 2026.
 */
class TouchCoordinateMapper(private val context: Context) {

    private var mCurrentTouchDevice = -1
    private var mAxisBounds: AxisBounds? = null

    fun updateTouchDevice(device: Int) {
        if (device < 0 || mCurrentTouchDevice == device) {
            return
        }
        mCurrentTouchDevice = device
        mAxisBounds = queryAxisBounds(device)
    }

    fun mapX(rawValue: Int) = mAxisBounds?.mapX(rawValue) ?: rawValue

    fun mapY(rawValue: Int) = mAxisBounds?.mapY(rawValue) ?: rawValue

    fun mapScreenXToRaw(screenValue: Int) = mAxisBounds?.toRawX(screenValue) ?: screenValue

    fun mapScreenYToRaw(screenValue: Int) = mAxisBounds?.toRawY(screenValue) ?: screenValue

    private fun queryAxisBounds(device: Int): AxisBounds? {
        val devicePath = "/dev/input/event$device"
        val output = runCommand("getevent -lp $devicePath")
            ?: runCommand("getevent -p $devicePath")
            ?: return null
        val xAxis = parseAxis(output, AXIS_X_PATTERNS) ?: return null
        val yAxis = parseAxis(output, AXIS_Y_PATTERNS) ?: return null
        return AxisBounds(xAxis, yAxis)
    }

    private fun runCommand(command: String): String? {
        runCatching {
            if (WrappedShizuku.isOperational()) {
                val result = WrappedShizuku.execCommand(context, command)
                if (result.code == 0 && result.result.isNotBlank()) {
                    return result.result
                }
            }
        }.onFailure {
            Log.w(LOG_TAG, "Failed to run command with shizuku: $command", it)
        }

        if (RootUtils.isRootAvailable()) {
            val result = ProcessShell.execCommand(command, true)
            if (result.code == 0 && result.result.isNotBlank()) {
                return result.result
            }
        }
        return null
    }

    private fun parseAxis(output: String, patterns: Array<Regex>): AxisRange? {
        patterns.forEach { regex ->
            val match = regex.find(output) ?: return@forEach
            val min = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val max = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@forEach
            if (max > min) {
                return AxisRange(min, max)
            }
        }
        return null
    }

    private data class AxisRange(val min: Int, val max: Int) {
        fun map(rawValue: Int, screenSize: Int): Int {
            if (screenSize <= 1 || max <= min) {
                return rawValue
            }
            val ratio = ((rawValue - min).toDouble() / (max - min).toDouble()).coerceIn(0.0, 1.0)
            return (ratio * (screenSize - 1)).roundToInt()
        }

        fun mapFromScreen(screenValue: Int, screenSize: Int): Int {
            if (screenSize <= 1 || max <= min) {
                return screenValue
            }
            val ratio = (screenValue.toDouble() / (screenSize - 1)).coerceIn(0.0, 1.0)
            return (min + ratio * (max - min)).roundToInt()
        }
    }

    private data class AxisBounds(val xAxis: AxisRange, val yAxis: AxisRange) {
        fun mapX(rawValue: Int) = xAxis.map(rawValue, ScreenMetrics.deviceScreenWidth)
        fun mapY(rawValue: Int) = yAxis.map(rawValue, ScreenMetrics.deviceScreenHeight)
        fun toRawX(screenValue: Int) = xAxis.mapFromScreen(screenValue, ScreenMetrics.deviceScreenWidth)
        fun toRawY(screenValue: Int) = yAxis.mapFromScreen(screenValue, ScreenMetrics.deviceScreenHeight)
    }

    companion object {
        private const val LOG_TAG = "TouchCoordinateMapper"

        private val AXIS_X_PATTERNS = arrayOf(
            Regex("""(?im)^\s*ABS_MT_POSITION_X\s*:\s*.*?\bmin\s+(-?\d+),\s*max\s+(-?\d+)"""),
            Regex("""(?im)^\s*0035\s*:\s*.*?\bmin\s+(-?\d+),\s*max\s+(-?\d+)"""),
        )

        private val AXIS_Y_PATTERNS = arrayOf(
            Regex("""(?im)^\s*ABS_MT_POSITION_Y\s*:\s*.*?\bmin\s+(-?\d+),\s*max\s+(-?\d+)"""),
            Regex("""(?im)^\s*0036\s*:\s*.*?\bmin\s+(-?\d+),\s*max\s+(-?\d+)"""),
        )
    }
}
