package org.autojs.autojs.engine

import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.log.Level
import org.autojs.autojs.core.console.log.LogConfigurator
import org.autojs.autojs.core.console.log.LogManager
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.runtime.api.modules.colors.Colors
import org.autojs.autojs.util.ConsoleUtils
import org.autojs.autojs.util.DisplayUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** JSON control operations on the same console that receives an execution's output. */
internal class RuntimeConsoleBridge(
    private val consoleProvider: () -> ConsoleImpl,
    private val workingDirectory: String? = null,
) {
    fun invoke(method: String, args: JSONArray): Any? {
        require(method in METHODS) { "Unknown console method: $method" }
        val values = Array<Any?>(args.length()) { args.opt(it).takeUnless { value -> value == JSONObject.NULL } }
        val expected = when (method) {
            "show", "setTitle", "setTouchable", "setExitOnClose", "setTitleBackgroundTint", "setTitleIconsTint",
            "setContentBackgroundTint", "setBackgroundTint" -> 0..1
            "setContentTextColor", "setContentTextColors" -> 0..6
            "setSize", "setPosition" -> 2..2
            "hide", "reset", "expand", "collapse", "launch", "clear", "resetGlobalLogConfig" -> 0..0
            else -> 1..1
        }
        require(values.size in expected) { "console.$method expects ${expected.first}..${expected.last} arguments" }
        val console = consoleProvider()
        fun first() = values.firstOrNull()
        fun number(index: Int = 0): Double = (values.getOrNull(index) as? Number)?.toDouble()
            ?.takeIf(Double::isFinite) ?: throw IllegalArgumentException("console.$method requires finite numbers")
        fun bool(default: Boolean = true): Boolean = first()?.let {
            it as? Boolean ?: throw IllegalArgumentException("console.$method requires a boolean")
        } ?: default
        fun color(value: Any? = first()): Int {
            require(value is String || value is Number) { "console.$method requires a color string or number" }
            return Colors.toIntRhino(value)
        }
        fun tint(): Int? = first()?.let(::color)
        when (method) {
            "show" -> {
                if (!DisplayOverOtherAppsPermission(console.context).has()) {
                    throw SecurityException("console.show requires the host's draw-over-other-apps permission")
                }
                console.show(bool(false))
            }
            "hide" -> console.hide()
            "reset" -> console.reset()
            "expand" -> console.expand()
            "collapse" -> console.collapse()
            "launch" -> check(ConsoleUtils.launch(console.context)) { "The console activity could not be opened" }
            "clear" -> console.clear()
            "setSize" -> console.setSize(DisplayUtils.toRoundDoubleX(number()), DisplayUtils.toRoundDoubleY(number(1)))
            "setPosition" -> console.setPosition(DisplayUtils.toRoundDoubleX(number()), DisplayUtils.toRoundDoubleY(number(1)))
            "setTitle" -> console.setTitle(first()?.toString().orEmpty())
            "setTitleTextSize" -> console.setTitleTextSize(number().toFloat())
            "setTitleTextColor" -> console.setTitleTextColor(color())
            "setTitleBackgroundColor" -> console.setTitleBackgroundColor(color())
            "setTitleBackgroundTint" -> console.setTitleBackgroundTint(tint())
            "setTitleBackgroundAlpha" -> console.setTitleBackgroundAlpha(number())
            "setTitleIconsTint" -> console.setTitleIconsTint(tint())
            "setContentTextSize" -> console.setContentTextSize(number().toFloat())
            "setContentTextColor", "setContentTextColors" -> {
                val input = first()
                val colors: Array<Int?> = when {
                    values.size == 1 && input is JSONArray -> {
                        require(input.length() <= 6) { "console.$method accepts at most six level colors" }
                        Array(6) { index -> input.opt(index)?.takeUnless { it == JSONObject.NULL }?.let(::color) }
                    }
                    values.size == 1 && input is JSONObject -> arrayOf("verbose", "log", "info", "warn", "error", "assert").map { key ->
                        input.opt(key)?.takeUnless { it == JSONObject.NULL }?.let(::color)
                    }.toTypedArray()
                    values.size == 1 -> Array(6) { color(input) }
                    else -> values.map { it?.let(::color) }.toTypedArray()
                }
                console.setContentTextColors(colors)
            }
            "setContentBackgroundColor" -> console.setContentBackgroundColor(color())
            "setContentBackgroundTint" -> console.setContentBackgroundTint(tint())
            "setContentBackgroundAlpha" -> console.setContentBackgroundAlpha(number())
            "setTextSize" -> console.setTextSize(number().toFloat())
            "setTextColor" -> console.setTextColor(color())
            "setBackgroundColor" -> console.setBackgroundColor(color())
            "setBackgroundTint" -> console.setBackgroundTint(tint())
            "setBackgroundAlpha" -> console.setBackgroundAlpha(number())
            "setExitOnClose" -> if (first() is Number) console.setExitOnClose(number().toLong()) else console.setExitOnClose(bool())
            "setTouchable" -> console.setTouchable(bool())
            "setGlobalLogConfig" -> configureLog(first() as? JSONObject ?: throw IllegalArgumentException("console.setGlobalLogConfig requires an object"))
            "resetGlobalLogConfig" -> LogManager.getLoggerRepository().resetConfiguration()
        }
        return null
    }

    private fun configureLog(config: JSONObject) {
        val path = File(config.optString("file", "android-log4j.log"))
        LogConfigurator().apply {
            fileName = if (path.isAbsolute) path.path else File(workingDirectory ?: consoleProvider().context.filesDir.path, path.path).path
            filePattern = config.optString("filePattern", "%m%n")
            maxFileSize = config.optLong("maxFileSize", 512 * 1024L)
            maxBackupSize = config.optInt("maxBackupSize", 5)
            require(maxFileSize > 0 && maxBackupSize >= 0) { "Log file size must be positive and backup count non-negative" }
            isUseFileAppender = true
            isImmediateFlush = config.optBoolean("immediateFlush", true)
            isResetConfiguration = config.optBoolean("resetConfiguration", true)
            rootLevel = Level.valueOf(config.optString("rootLevel", "ALL").uppercase(Locale.ROOT))
        }.configure()
    }

    companion object {
        val METHODS = setOf(
            "show", "hide", "reset", "expand", "collapse", "launch", "clear", "setSize", "setPosition", "setTitle",
            "setTitleTextSize", "setTitleTextColor", "setTitleBackgroundColor", "setTitleBackgroundTint", "setTitleBackgroundAlpha", "setTitleIconsTint",
            "setContentTextSize", "setContentTextColor", "setContentTextColors", "setContentBackgroundColor", "setContentBackgroundTint", "setContentBackgroundAlpha",
            "setTextSize", "setTextColor", "setBackgroundColor", "setBackgroundTint", "setBackgroundAlpha", "setExitOnClose", "setTouchable",
            "setGlobalLogConfig", "resetGlobalLogConfig",
        )
    }
}
