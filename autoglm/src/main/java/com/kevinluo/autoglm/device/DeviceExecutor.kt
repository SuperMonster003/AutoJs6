package com.kevinluo.autoglm.device

import com.kevinluo.autoglm.IUserService
import com.kevinluo.autoglm.util.Logger
import com.kevinluo.autoglm.util.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Executes device operations through Shizuku shell commands.
 *
 * This class provides methods to interact with the Android device using
 * shell commands executed via the Shizuku UserService. It handles various
 * device interactions including:
 * - Touch operations (tap, double tap, long press, swipe)
 * - Key press events
 * - App launching
 * - Screen information retrieval
 *
 * Note: Text input is handled by [TextInputManager] using AutoGLM Keyboard.
 *
 * All operations are executed through shell commands via the UserService,
 * which requires Shizuku permissions to be granted.
 *
 * @property userService The Shizuku UserService for executing shell commands
 *
 */
class DeviceExecutor(private val userService: IUserService) {
    /**
     * Performs a tap at the specified absolute coordinates.
     *
     * Executes a single tap gesture at the given screen position using
     * the `input tap` shell command.
     *
     * @param x Absolute X coordinate in pixels
     * @param y Absolute Y coordinate in pixels
     * @return Result of the command execution
     *
     */
    suspend fun tap(x: Int, y: Int): String = withContext(Dispatchers.IO) {
        executeCommand("input tap $x $y")
    }

    /**
     * Performs a double tap at the specified absolute coordinates.
     *
     * Two taps are executed in quick succession with a short interval
     * defined by [DOUBLE_TAP_INTERVAL_MS].
     *
     * @param x Absolute X coordinate in pixels
     * @param y Absolute Y coordinate in pixels
     * @return Result of the command execution
     *
     */
    suspend fun doubleTap(x: Int, y: Int): String = withContext(Dispatchers.IO) {
        val result1 = executeCommand("input tap $x $y")
        delay(DOUBLE_TAP_INTERVAL_MS)
        val result2 = executeCommand("input tap $x $y")
        "$result1\n$result2"
    }

    /**
     * Performs a long press at the specified absolute coordinates.
     *
     * Implemented as a swipe with the same start and end coordinates,
     * which effectively holds the touch at that position for the specified duration.
     *
     * @param x Absolute X coordinate in pixels
     * @param y Absolute Y coordinate in pixels
     * @param durationMs Duration of the long press in milliseconds, defaults to [DEFAULT_LONG_PRESS_DURATION_MS]
     * @return Result of the command execution
     *
     */
    suspend fun longPress(x: Int, y: Int, durationMs: Int = DEFAULT_LONG_PRESS_DURATION_MS): String =
        withContext(Dispatchers.IO) {
            executeCommand("input swipe $x $y $x $y $durationMs")
        }

    /**
     * Performs a swipe gesture using a list of points.
     *
     * Uses sendevent for multi-point curved swipes to create a smooth continuous gesture.
     * Falls back to simple two-point swipe if sendevent is not available.
     *
     * @param points List of points defining the swipe path, must contain at least 2 points
     * @param durationMs Total duration of the swipe in milliseconds
     * @return Result of the command execution
     *
     */
    suspend fun swipe(points: List<Point>, durationMs: Int): String = withContext(Dispatchers.IO) {
        if (points.size < 2) {
            return@withContext "Error: Swipe requires at least 2 points"
        }

        // Try sendevent for curved swipe first, fall back to simple swipe if it fails
        val sendeventResult = executeCurvedSwipe(points, durationMs)
        if (sendeventResult != null) {
            sendeventResult
        } else {
            // Fallback to simple two-point swipe
            val start = points.first()
            val end = points.last()
            executeCommand("input swipe ${start.x} ${start.y} ${end.x} ${end.y} $durationMs")
        }
    }

    /**
     * Executes a curved swipe using sendevent for smooth multi-point gesture.
     *
     * This method generates low-level touch events to create a smooth curved
     * swipe path through all provided points.
     *
     * @param points List of points defining the swipe path
     * @param durationMs Total duration of the swipe in milliseconds
     * @return Result of the command execution, or null if sendevent is not available
     */
    private fun executeCurvedSwipe(points: List<Point>, durationMs: Int): String? {
        val devicePath = getTouchDevicePath() ?: return null

        // Calculate delay between points (in microseconds for usleep)
        val delayUs = (durationMs * 1000L) / (points.size - 1)

        // Build the sendevent command sequence
        val commands = StringBuilder()

        // Touch down at first point
        val first = points.first()
        commands.appendLine("sendevent $devicePath 3 57 0") // ABS_MT_TRACKING_ID = 0
        commands.appendLine("sendevent $devicePath 3 53 ${first.x}") // ABS_MT_POSITION_X
        commands.appendLine("sendevent $devicePath 3 54 ${first.y}") // ABS_MT_POSITION_Y
        commands.appendLine("sendevent $devicePath 3 58 50") // ABS_MT_PRESSURE
        commands.appendLine("sendevent $devicePath 3 48 5") // ABS_MT_TOUCH_MAJOR
        commands.appendLine("sendevent $devicePath 0 0 0") // SYN_REPORT

        // Move through intermediate points
        for (i in 1 until points.size - 1) {
            val point = points[i]
            commands.appendLine("usleep $delayUs")
            commands.appendLine("sendevent $devicePath 3 53 ${point.x}")
            commands.appendLine("sendevent $devicePath 3 54 ${point.y}")
            commands.appendLine("sendevent $devicePath 0 0 0")
        }

        // Move to last point
        val last = points.last()
        commands.appendLine("usleep $delayUs")
        commands.appendLine("sendevent $devicePath 3 53 ${last.x}")
        commands.appendLine("sendevent $devicePath 3 54 ${last.y}")
        commands.appendLine("sendevent $devicePath 0 0 0")

        // Touch up
        commands.appendLine("sendevent $devicePath 3 57 -1") // ABS_MT_TRACKING_ID = -1 (lift)
        commands.appendLine("sendevent $devicePath 0 0 0") // SYN_REPORT

        // Execute as a shell script
        val script = commands.toString()
        val result = executeCommand("sh -c '$script'")

        // Check if sendevent worked (no permission errors)
        return if (result.contains("Permission denied") || result.contains("No such file")) {
            null
        } else {
            result
        }
    }

    /**
     * Cached touch device path for performance optimization.
     */
    private var cachedTouchDevice: String? = null

    /**
     * Gets the touch input device path.
     *
     * Attempts to find the touch input device from /dev/input/ by looking for
     * devices that support ABS_MT_POSITION events. Caches the result for
     * subsequent calls.
     *
     * @return The device path (e.g., "/dev/input/event1"), or null if not found
     */
    private fun getTouchDevicePath(): String? {
        cachedTouchDevice?.let { return it }

        // Find touch device from /dev/input/
        val geteventCmd =
            "getevent -pl 2>/dev/null | " +
                "grep -B5 'ABS_MT_POSITION' | " +
                "grep 'add device' | head -1 | awk '{print \$3}'"
        val result = executeCommand(geteventCmd)
        val device = result.trim().substringBefore("[").trim()

        if (device.startsWith("/dev/input/")) {
            cachedTouchDevice = device
            return device
        }

        // Fallback: try common device paths
        val commonPaths =
            listOf(
                "/dev/input/event1",
                "/dev/input/event2",
                "/dev/input/event0",
            )

        for (path in commonPaths) {
            val testResult = executeCommand("test -e $path && echo exists")
            if (testResult.contains("exists")) {
                cachedTouchDevice = path
                return path
            }
        }

        return null
    }

    /**
     * Presses a key by its keycode.
     *
     * Executes a key press event using the `input keyevent` shell command.
     *
     * @param keyCode The Android KeyEvent keycode (e.g., [KEYCODE_BACK], [KEYCODE_HOME])
     * @return Result of the command execution
     *
     */
    suspend fun pressKey(keyCode: Int): String = withContext(Dispatchers.IO) {
        executeCommand("input keyevent $keyCode")
    }

    /**
     * Launches an app by its package name.
     *
     * First tries to resolve the launcher activity using pm resolve-activity,
     * then launches it with am start. This is more reliable than just using -p flag.
     *
     * @param packageName The package name of the app to launch
     * @return Result of the command execution
     *
     */
    suspend fun launchApp(packageName: String): String = withContext(Dispatchers.IO) {
        // First, try to resolve the launcher activity
        val resolveCmd =
            "pm resolve-activity --brief " +
                "-a android.intent.action.MAIN " +
                "-c android.intent.category.LAUNCHER $packageName"
        val resolveResult = executeCommand(resolveCmd)
        Logger.d(TAG, "Resolve activity result: $resolveResult")

        // Parse the component name from the result (format: "package/activity")
        val componentName =
            resolveResult
                .lines()
                .firstOrNull { it.contains("/") && it.contains(packageName) }
                ?.trim()

        val result =
            if (componentName != null && componentName.isNotBlank()) {
                // Launch with specific component
                Logger.d(TAG, "Launching with component: $componentName")
                executeCommand("am start -n $componentName")
            } else {
                // Fallback to package-only launch
                Logger.d(TAG, "Launching with package only: $packageName")
                executeCommand(
                    "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $packageName",
                )
            }

        Logger.d(TAG, "Launch app result: $result")
        result
    }

    /**
     * Gets the current foreground app's package name.
     *
     * Uses dumpsys window to determine the focused window and extracts
     * the package name from the window information.
     *
     * @return The package name of the current foreground app, or empty string if not found
     *
     */
    suspend fun getCurrentApp(): String = withContext(Dispatchers.IO) {
        val result = executeCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        parseCurrentApp(result)
    }

    /**
     * Parses the dumpsys output to extract the current app package name.
     *
     * Looks for patterns in the dumpsys output that contain the package name,
     * such as mCurrentFocus and mFocusedApp entries.
     *
     * @param dumpsysOutput The output from dumpsys window command
     * @return The extracted package name, or empty string if not found
     */
    internal fun parseCurrentApp(dumpsysOutput: String): String {
        // Look for patterns like:
        // mCurrentFocus=Window{...com.example.app/com.example.app.MainActivity...}
        // mFocusedApp=ActivityRecord{...com.example.app/.MainActivity...}

        val patterns =
            listOf(
                Regex("""mCurrentFocus=Window\{[^}]*\s+([a-zA-Z0-9_.]+)/"""),
                Regex("""mFocusedApp=.*ActivityRecord\{[^}]*\s+([a-zA-Z0-9_.]+)/"""),
                Regex("""mFocusedApp=.*\s+([a-zA-Z0-9_.]+)/"""),
            )

        for (pattern in patterns) {
            val match = pattern.find(dumpsysOutput)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return ""
    }

    /**
     * Executes a shell command through the UserService.
     *
     * @param command The shell command to execute
     * @return The command output, or an error message if execution fails
     */
    private fun executeCommand(command: String): String = try {
        userService.executeCommand(command)
    } catch (e: Exception) {
        Logger.e(TAG, "Error executing command: $command", e)
        "Error executing command: ${e.message}"
    }

    /**
     * Generates the shell command for a key press action.
     *
     * Useful for testing and verification of command generation.
     *
     * @param keyCode The Android KeyEvent keycode
     * @return The shell command string
     */
    fun generateKeyPressCommand(keyCode: Int): String = "input keyevent $keyCode"

    /**
     * Generates the shell command for a long press action.
     *
     * Useful for testing and verification of command generation.
     *
     * @param x Absolute X coordinate
     * @param y Absolute Y coordinate
     * @param durationMs Duration in milliseconds
     * @return The shell command string
     */
    fun generateLongPressCommand(x: Int, y: Int, durationMs: Int): String = "input swipe $x $y $x $y $durationMs"

    /**
     * Generates the shell commands for a double tap action.
     *
     * Useful for testing and verification of command generation.
     *
     * @param x Absolute X coordinate
     * @param y Absolute Y coordinate
     * @return List of shell command strings
     */
    fun generateDoubleTapCommands(x: Int, y: Int): List<String> = listOf(
        "input tap $x $y",
        "input tap $x $y",
    )

    /**
     * Gets the interval between double tap commands in milliseconds.
     *
     * @return The double tap interval in milliseconds
     */
    fun getDoubleTapIntervalMs(): Long = DOUBLE_TAP_INTERVAL_MS

    companion object {
        private const val TAG = "DeviceExecutor"

        // Android KeyEvent keycodes

        /** Back button keycode. */
        const val KEYCODE_BACK = 4

        /** Home button keycode. */
        const val KEYCODE_HOME = 3

        /** Volume up button keycode. */
        const val KEYCODE_VOLUME_UP = 24

        /** Volume down button keycode. */
        const val KEYCODE_VOLUME_DOWN = 25

        /** Power button keycode. */
        const val KEYCODE_POWER = 26

        /** Enter key keycode. */
        const val KEYCODE_ENTER = 66

        /** Delete/backspace key keycode. */
        const val KEYCODE_DEL = 67

        /** Interval between double tap events in milliseconds. */
        const val DOUBLE_TAP_INTERVAL_MS = 100L

        /** Default duration for long press in milliseconds. */
        const val DEFAULT_LONG_PRESS_DURATION_MS = 3000
    }
}
