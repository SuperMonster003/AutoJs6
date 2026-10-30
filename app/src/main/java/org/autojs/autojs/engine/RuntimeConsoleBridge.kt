package org.autojs.autojs.engine

import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.ConsoleInputRequest
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
            "isShowing", "resetBackgroundAlpha", "resetContentBackgroundAlpha", "resetTitleBackgroundAlpha" -> 0..0
            "setAvoidStatusBar", "setColorful", "setInputVisible", "setTimeVisible", "setTouchThrough", "setTimeFormat", "configure" -> 0..1
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
            "isShowing" -> return console.isShowing
            "resetBackgroundAlpha" -> console.resetBackgroundAlpha()
            "resetContentBackgroundAlpha" -> console.resetContentBackgroundAlpha()
            "resetTitleBackgroundAlpha" -> console.resetTitleBackgroundAlpha()
            "setAvoidStatusBar" -> console.setAvoidStatusBar(bool())
            "setColorful" -> console.setColorful(bool())
            "setInputVisible" -> console.setInputVisible(bool())
            "setTimeVisible" -> console.setTimeVisible(bool())
            "setTouchThrough" -> console.setTouchThrough(bool())
            "setGravity" -> when (val gravity = first()) {
                is Number -> console.setGravity(number().toInt())
                is String -> console.setGravity(gravity)
                else -> throw IllegalArgumentException("console.setGravity requires a gravity number or name")
            }
            "setTimeFormat" -> console.setTimeFormat(first()?.let { it as? String ?: throw IllegalArgumentException("console.setTimeFormat requires a string") })
            "configure" -> {
                // Every key is checked before the first setter runs; the window is then shown as Rhino's build(options).show() does.
                // zh-CN: 先检查全部键再调用第一个 setter, 然后像 Rhino 的 build(options).show() 那样显示窗口.
                val options = first()?.let { it as? JSONObject ?: throw IllegalArgumentException("console.configure requires an options object") } ?: JSONObject()
                configureCalls(options).forEach { (setter, setterArgs) -> invoke(setter, setterArgs) }
                invoke("show", JSONArray())
            }
        }
        return null
    }

    /**
     * Queues a nonblocking line request on the execution console (the queue that stdin and Rhino's
     * `console.rawInput()` share) and shows the console window when nothing displays it yet;
     * [callback] receives the submitted line, or null when the request was cancelled (execution
     * end, console reset). Unlike Rhino, a hidden window without the overlay permission is refused
     * instead of leaving the caller waiting. api-coverage P3.3 (Q165 (a)).
     * zh-CN: 在执行控制台上排队一个非阻塞的输入请求 (与 stdin 及 Rhino 的 `console.rawInput()` 共用同一队列), 尚无视图显示控制台时显示控制台窗口;
     * [callback] 收到提交的那一行, 请求被取消 (执行结束, 控制台重置) 时收到 null. 与 Rhino 不同, 窗口未显示且没有悬浮窗权限时直接拒绝, 而不是让调用方一直等待.
     * api-coverage P3.3 (Q165 (a)).
     */
    fun requestInput(callback: (String?) -> Unit): ConsoleInputRequest {
        val console = consoleProvider()
        if (!console.isGlobal && !console.isShowing && !DisplayOverOtherAppsPermission(console.context).has()) {
            throw SecurityException("console.rawInput requires the host's draw-over-other-apps permission")
        }
        return console.requestInput { }.also { request -> request.whenComplete(callback) }
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
            // api-coverage P3.3: Rhino's remaining window controls, and console.build(options).show() as one call.
            // zh-CN: api-coverage P3.3: Rhino 其余的窗口控制, 以及合成一次调用的 console.build(options).show().
            "isShowing", "resetBackgroundAlpha", "resetContentBackgroundAlpha", "resetTitleBackgroundAlpha",
            "setAvoidStatusBar", "setColorful", "setInputVisible", "setTimeVisible", "setTouchThrough", "setGravity", "setTimeFormat",
            "configure",
        )

        /**
         * The setters `configure(options)` may call: the configurator fields of Rhino's `console.build` (api-coverage P3.3).
         * zh-CN: `configure(options)` 可以调用的 setter, 即 Rhino `console.build` 的配置器字段 (api-coverage P3.3).
         */
        val CONFIGURE_SETTERS = setOf(
            "setSize", "setPosition", "setGravity", "setTitle", "setTitleTextSize", "setTitleTextColor", "setTitleBackgroundColor",
            "setTitleBackgroundTint", "setTitleBackgroundAlpha", "setTitleIconsTint", "setContentTextSize", "setContentTextColor",
            "setContentTextColors", "setContentBackgroundColor", "setContentBackgroundTint", "setContentBackgroundAlpha", "setTextSize",
            "setTextColor", "setBackgroundColor", "setBackgroundTint", "setBackgroundAlpha", "setExitOnClose", "setTouchable",
            "setTouchThrough", "setTimeVisible", "setTimeFormat", "setColorful", "setAvoidStatusBar", "setInputVisible",
        )

        private val SETTER_KEY = Regex("^set[A-Z]")

        /**
         * Maps the keys of `configure(options)` to setter calls the way Rhino's `console.build` does: a key names its setter
         * (`title` -> `setTitle`, a `setXxx` key stays as it is) and an array value is spread into the arguments; `size` and
         * `position` also take `{width, height}` and `{x, y}`. An unknown key is refused before any setter runs (api-coverage P3.3).
         * zh-CN: 像 Rhino 的 `console.build` 那样把 `configure(options)` 的键映射为 setter 调用: 键对应其 setter (`title` -> `setTitle`, `setXxx`
         * 形式的键保持原样), 数组值展开为参数; `size` 与 `position` 还接受 `{width, height}` 与 `{x, y}`. 未知键在任何 setter 运行之前即被拒绝
         * (api-coverage P3.3).
         */
        fun configureCalls(options: JSONObject): List<Pair<String, JSONArray>> = options.keys().asSequence().toList().map { key ->
            val setter = if (SETTER_KEY.containsMatchIn(key)) key else "set" + key.replaceFirstChar { it.uppercaseChar() }
            require(setter in CONFIGURE_SETTERS) {
                "unknown option '$key'; expected one of ${CONFIGURE_SETTERS.joinToString(", ") { it.removePrefix("set").replaceFirstChar(Char::lowercaseChar) }}"
            }
            val value = options.opt(key)
            val args = when {
                value is JSONObject && setter == "setSize" -> JSONArray().put(value.opt("width") ?: JSONObject.NULL).put(value.opt("height") ?: JSONObject.NULL)
                value is JSONObject && setter == "setPosition" -> JSONArray().put(value.opt("x") ?: JSONObject.NULL).put(value.opt("y") ?: JSONObject.NULL)
                value is JSONArray -> value
                else -> JSONArray().put(value ?: JSONObject.NULL)
            }
            setter to args
        }
    }
}
