package org.autojs.autojs.runtime.api.modules.console

import android.util.Log
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.log.Level
import org.autojs.autojs.core.console.log.LogConfigurator
import org.autojs.autojs.core.console.log.LogManager
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.colors.ColorNativeObject
import org.autojs.autojs.runtime.api.augment.console.Console
import org.autojs.autojs.runtime.api.augment.flow.FlowJsErrors
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.ScriptProxyGet
import org.autojs.autojs.runtime.api.modules.ScriptProxyObject
import org.autojs.autojs.runtime.api.modules.ScriptProxySet
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.util.Inspector
import org.autojs.autojs.runtime.api.modules.util.UtilModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ConsoleUtils
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeError
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper

/**
 * The `console` module on the new script module layer (roadmap P3.2), a proxy module as the current
 * `augment/console/Console` (`AugmentableProxy.proxying`): the members in the current order, the runtime's
 * `ConsoleImpl` as prototype (so `isShowing`, `setGravity` and the other console methods stay reachable), a read of
 * `name` trying `name`, `getName` and `isName`, and a write of `name` calling `setName` with the value (an array
 * spread). The console itself, `console.assert`'s evaluation and `console.build`'s calls stay with the runtime's
 * `ConsoleImpl` and the current module's companion (to be moved out before P5.2).
 *
 * Messages are formatted with the new `util.format` (decision b of P3.2): `console.log(o)` prints what `util.inspect(o)`
 * returns, a first argument of string type is the format string of `util.format`; the differences to the current
 * module's `Util.formatRhino` are listed in appendix E. Arguments follow the real signatures (T4): wrong types are
 * TypeErrors, unusable values (an unknown level word, a fraction for a timeout, NaN for a size) RangeErrors, surplus
 * arguments TypeErrors (appendix C Q7). Colors are the omni colors of `s13n.color`; the `Color` object of the colors
 * module is still the `augment` package's `ColorNativeObject` (Q20).
 *
 * zh-CN: 新脚本模块层上的 console 模块 (P3.2), 与现有 augment/console/Console (AugmentableProxy.proxying) 一样是代理模块: 成员顺序同现有模块, 原型为运行时的
 * ConsoleImpl (isShowing, setGravity 等控制台方法仍可访问), 读取 name 时依次尝试 name, getName 与 isName, 写入 name 时以该值调用 setName (数组展开). 控制台本身,
 * console.assert 的求值与 console.build 的调用仍由运行时的 ConsoleImpl 与现有模块的伴生对象完成 (P5.2 前迁出). 消息用新的 util.format 格式化 (P3.2 决定 b):
 * console.log(o) 打印 util.inspect(o) 的结果, 第一个参数为字符串类型时是 util.format 的格式串; 与现有模块 Util.formatRhino 的差异列在附录 E. 参数按真实签名 (T4):
 * 类型不符为 TypeError, 不可用的值 (未知的等级单词, 小数超时, 尺寸为 NaN) 为 RangeError, 多余参数为 TypeError (附录 C Q7). 颜色为 s13n.color 的通用颜色; colors 模块的
 * Color 对象仍是 augment 包的 ColorNativeObject (Q20).
 */
@ScriptModule(name = "console", proxy = true)
class ConsoleModule(private val runtime: ScriptRuntime) {

    private val util = UtilModule(null)

    private val console: ConsoleImpl get() = runtime.console

    /** The module object the setters return (the runtime's `consoleProxyObject`). */
    private val self: ProxyObject get() = runtime.consoleProxyObject

    @ScriptPrototype
    fun prototype(): Any = runtime.console

    /**
     * Prints [message] with the stack of the calling script at [level]: a documented word (`'verbose'`, `'debug'`,
     * `'info'`, `'warn'`, `'error'`, any case) or the `android.util.Log` constant (2 to 6); nullish is `'debug'`. The
     * current module printed an unknown level as `'debug'`.
     */
    @ScriptFunction
    fun trace(cx: Context, scope: VarScope, message: Any? = Undefined.instance, level: Any? = null) {
        val priority = traceLevel(level)
        val text = "${util.format(cx, scope, message)}\n${stackTrace(cx, scope)}"
        when (priority) {
            Log.VERBOSE -> console.verbose(text)
            Log.INFO -> console.info(text)
            Log.WARN -> console.warn(text)
            Log.ERROR -> console.error(text)
            else -> console.log(text)
        }
    }

    @ScriptFunction
    fun show(isReset: Any? = null): ProxyObject {
        if (ScriptArgs.isNullish(isReset)) console.show() else console.show(ScriptArgs.boolean(isReset, "console.show", "isReset"))
        return self
    }

    @ScriptFunction
    fun hide(): ProxyObject = self.also { console.hide() }

    @ScriptFunction
    fun reset(): ProxyObject = self.also { console.reset() }

    @ScriptFunction
    fun clear(): ProxyObject = self.also { console.clear() }

    @ScriptFunction
    fun expand(): ProxyObject = self.also { console.expand() }

    @ScriptFunction
    fun collapse(): ProxyObject = self.also { console.collapse() }

    /** Asserts [value] (a function is called and its result asserted, as in the current module). */
    @ScriptFunction
    fun assert(value: Any?, message: Any? = null): ProxyObject {
        val text = if (ScriptArgs.isNullish(message)) AssertionError::class.java.name else ScriptArgs.string(message, "console.assert", "message")
        console.assertTrue(Console.evaluateAssertionValue(runtime, value), text)
        return self
    }

    /** Reads a line as [rawInput] does and evaluates it as JavaScript. */
    @ScriptFunction
    fun input(cx: Context, scope: VarScope, vararg data: Any?): Any? = RhinoUtils.js_eval(runtime.topLevelScope, rawInput(cx, scope, *data))

    /** Prints [data] (when given) as [log] does, then reads a line from the console input bar. */
    @ScriptFunction
    fun rawInput(cx: Context, scope: VarScope, vararg data: Any?): String {
        if (data.isNotEmpty()) console.log(util.format(cx, scope, *data))
        return console.rawInput()
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun log(cx: Context, scope: VarScope, vararg data: Any?) {
        console.log(util.format(cx, scope, *data))
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun verbose(cx: Context, scope: VarScope, vararg data: Any?) {
        console.verbose(util.format(cx, scope, *data))
    }

    @ScriptFunction
    fun info(cx: Context, scope: VarScope, vararg data: Any?) {
        console.info(util.format(cx, scope, *data))
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun warn(cx: Context, scope: VarScope, vararg data: Any?) {
        console.warn(util.format(cx, scope, *data))
    }

    /** As [log], with a flow error (an object with a `flowStack`) printed as its stack, as in the current module. */
    @ScriptFunction
    fun error(cx: Context, scope: VarScope, vararg data: Any?) {
        console.error(errorText(cx, scope, data))
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun print(cx: Context, scope: VarScope, vararg data: Any?) {
        console.print(Log.DEBUG, util.format(cx, scope, *data))
    }

    @ScriptFunction
    fun time(label: Any? = null) = runtime.consoleTimeTable.save(ScriptArgs.stringOrNull(label, "console.time", "label"))

    @ScriptFunction
    fun timeEnd(label: Any? = null) = runtime.consoleTimeTable.print(ScriptArgs.stringOrNull(label, "console.timeEnd", "label"))

    /**
     * Applies [options] (a `ConsoleBuildOptions` object: each key names a setter of the console configurator, as `size`
     * names `setSize`) by calling the console's setter with the value, as the current module does, and returns the
     * configurator. An unknown key is a RangeError (the current module threw IllegalArgumentException).
     */
    @ScriptFunction
    fun build(options: Any? = null): ConsoleImpl.Configurator {
        val function = "console.build"
        val configurator = console.configurator
        if (ScriptArgs.isNullish(options)) return configurator
        if (options !is NativeObject) throw GlobalValues.typeError(function, "options", "ConsoleBuildOptions", options)
        options.entries.forEach { (id, value) ->
            val key = RhinoUtils.coerceString(id)
            val name = configuratorFunction(key) ?: throw GlobalValues.rangeError(function, "options key", "an option of ConsoleBuildOptions", key)
            Console.invokeConfiguratorFunction(runtime, self, name, value)
        }
        return configurator
    }

    @ScriptFunction
    fun setSize(width: Any?, height: Any?): ProxyObject {
        val function = "console.setSize"
        console.setSize(DisplayUtils.toRoundDoubleX(finite(function, "width", width)), DisplayUtils.toRoundDoubleY(finite(function, "height", height)))
        return self
    }

    @ScriptFunction
    fun setPosition(x: Any?, y: Any?): ProxyObject {
        val function = "console.setPosition"
        console.setPosition(DisplayUtils.toRoundDoubleX(finite(function, "x", x)), DisplayUtils.toRoundDoubleY(finite(function, "y", y)))
        return self
    }

    /** Sets the title; nullish is an empty title, as in the current module. */
    @ScriptFunction
    fun setTitle(title: Any?): ProxyObject = self.also {
        console.setTitle(if (ScriptArgs.isNullish(title)) "" else ScriptArgs.string(title, "console.setTitle", "title"))
    }

    @ScriptFunction
    fun setTitleTextSize(size: Any?): ProxyObject = self.also { console.setTitleTextSize(textSize("console.setTitleTextSize", size)) }

    @ScriptFunction
    fun setTitleTextColor(color: Any?): ProxyObject = self.also { console.setTitleTextColor(colorOf("console.setTitleTextColor", "color", color)) }

    @ScriptFunction
    fun setTitleBackgroundColor(color: Any?): ProxyObject = self.also { console.setTitleBackgroundColor(colorOf("console.setTitleBackgroundColor", "color", color)) }

    @ScriptFunction
    fun setTitleBackgroundTint(tint: Any?): ProxyObject = self.also { console.setTitleBackgroundTint(tintOf("console.setTitleBackgroundTint", tint)) }

    @ScriptFunction
    fun setTitleBackgroundAlpha(alpha: Any?): ProxyObject = self.also { console.setTitleBackgroundAlpha(alphaOf("console.setTitleBackgroundAlpha", alpha)) }

    @ScriptFunction
    fun setTitleIconsTint(tint: Any?): ProxyObject = self.also { console.setTitleIconsTint(tintOf("console.setTitleIconsTint", tint)) }

    @ScriptFunction
    fun setContentTextSize(size: Any?): ProxyObject = self.also { console.setContentTextSize(textSize("console.setContentTextSize", size)) }

    /**
     * Sets the log text colors by level (`verbose`, `log`, `info`, `warn`, `error`, `assert`): from a color map, from an
     * array (the first six elements, a nullish element or a missing one leaving the console's color), from one color for
     * all levels, or from 2 to 6 colors in level order; without colors the console gets an empty color list.
     */
    @ScriptFunction
    fun setContentTextColor(vararg colors: Any?): ProxyObject = self.also { console.setContentTextColors(contentTextColors("console.setContentTextColor", colors)) }

    @ScriptFunction
    fun setContentTextColors(vararg colors: Any?): ProxyObject = self.also { console.setContentTextColors(contentTextColors("console.setContentTextColors", colors)) }

    @ScriptFunction
    fun setContentBackgroundColor(color: Any?): ProxyObject = self.also { console.setContentBackgroundColor(colorOf("console.setContentBackgroundColor", "color", color)) }

    @ScriptFunction
    fun setContentBackgroundTint(tint: Any?): ProxyObject = self.also { console.setContentBackgroundTint(tintOf("console.setContentBackgroundTint", tint)) }

    @ScriptFunction
    fun setContentBackgroundAlpha(alpha: Any?): ProxyObject = self.also { console.setContentBackgroundAlpha(alphaOf("console.setContentBackgroundAlpha", alpha)) }

    @ScriptFunction
    fun setTextSize(size: Any?): ProxyObject = self.also { console.setTextSize(textSize("console.setTextSize", size)) }

    @ScriptFunction
    fun setTextColor(color: Any?): ProxyObject = self.also { console.setTextColor(colorOf("console.setTextColor", "color", color)) }

    @ScriptFunction
    fun setBackgroundColor(color: Any?): ProxyObject = self.also { console.setBackgroundColor(colorOf("console.setBackgroundColor", "color", color)) }

    /** Sets the background tint; nullish clears it, as for the other tints (the current module refused nullish here). */
    @ScriptFunction
    fun setBackgroundTint(tint: Any?): ProxyObject = self.also { console.setBackgroundTint(tintOf("console.setBackgroundTint", tint)) }

    @ScriptFunction
    fun setBackgroundAlpha(alpha: Any?): ProxyObject = self.also { console.setBackgroundAlpha(alphaOf("console.setBackgroundAlpha", alpha)) }

    /** Closes the window when the script ends: [exitOnClose] is a boolean or a delay in milliseconds (an integer). */
    @ScriptFunction
    fun setExitOnClose(exitOnClose: Any? = null): ProxyObject {
        val function = "console.setExitOnClose"
        when {
            ScriptArgs.isNullish(exitOnClose) -> console.setExitOnClose()
            ScriptArgs.isBoolean(exitOnClose) -> console.setExitOnClose(ScriptArgs.boolean(exitOnClose, function, "exitOnClose"))
            ScriptArgs.isNumber(exitOnClose) -> console.setExitOnClose(ScriptArgs.long(exitOnClose, function, "timeout"))
            else -> throw GlobalValues.typeError(function, "exitOnClose", "boolean | number", exitOnClose)
        }
        return self
    }

    @ScriptFunction
    fun setTouchable(touchable: Any? = null): ProxyObject = self.also {
        if (ScriptArgs.isNullish(touchable)) console.setTouchable() else console.setTouchable(ScriptArgs.boolean(touchable, "console.setTouchable", "touchable"))
    }

    @ScriptFunction
    fun setTimeVisible(visible: Any? = null): ProxyObject = self.also { console.setTimeVisible(flag("console.setTimeVisible", "visible", visible)) }

    @ScriptFunction
    fun setTimeFormat(pattern: Any? = null): ProxyObject = self.also { console.setTimeFormat(ScriptArgs.stringOrNull(pattern, "console.setTimeFormat", "pattern")) }

    @ScriptFunction
    fun setColorful(colorful: Any? = null): ProxyObject = self.also { console.setColorful(flag("console.setColorful", "colorful", colorful)) }

    @ScriptFunction
    fun setAvoidStatusBar(avoid: Any? = null): ProxyObject = self.also { console.setAvoidStatusBar(flag("console.setAvoidStatusBar", "avoid", avoid)) }

    @ScriptFunction
    fun setInputVisible(visible: Any? = null): ProxyObject = self.also { console.setInputVisible(flag("console.setInputVisible", "visible", visible)) }

    /**
     * Writes the logs of all scripts to a file. The documented keys of [config] are checked (`file` and `filePattern`
     * strings, `maxFileSize` and `maxBackupSize` integers, `rootLevel` a level word in any case), as are the two keys
     * the current module also reads (`immediateFlush`, `resetConfiguration`, booleans); an unknown key is a RangeError
     * (the current module ignored it).
     */
    @ScriptFunction
    fun setGlobalLogConfig(config: Any?) {
        val function = "console.setGlobalLogConfig"
        if (config !is NativeObject) throw GlobalValues.typeError(function, "config", "Object", config)
        config.keys.map { RhinoUtils.coerceString(it) }.firstOrNull { it !in LOG_CONFIG_KEYS }?.let {
            throw GlobalValues.rangeError(function, "config key", LOG_CONFIG_KEYS.joinToString(" | ") { key -> "'$key'" }, it)
        }
        fun option(key: String): Any? = config.prop(key).takeUnless { GlobalValues.isNullish(it) }
        // All options are checked before the configurator is touched.
        val file = option("file")?.let { ScriptArgs.string(it, function, "config.file") } ?: DEFAULT_LOG_FILE
        val pattern = option("filePattern")?.let { ScriptArgs.string(it, function, "config.filePattern") } ?: DEFAULT_LOG_PATTERN
        val fileSize = option("maxFileSize")?.let { ScriptArgs.long(it, function, "config.maxFileSize") } ?: DEFAULT_LOG_FILE_SIZE
        val backupSize = option("maxBackupSize")?.let { ScriptArgs.int(it, function, "config.maxBackupSize") } ?: DEFAULT_LOG_BACKUP_SIZE
        val immediateFlush = option("immediateFlush")?.let { ScriptArgs.boolean(it, function, "config.immediateFlush") } ?: true
        val resetConfiguration = option("resetConfiguration")?.let { ScriptArgs.boolean(it, function, "config.resetConfiguration") } ?: true
        val levelName = option("rootLevel")?.let { value ->
            val word = ScriptArgs.string(value, function, "config.rootLevel")
            LOG_LEVELS.find { it.equals(word, ignoreCase = true) } ?: throw GlobalValues.rangeError(function, "config.rootLevel", LOG_LEVELS.joinToString(" | ") { "'${it.lowercase()}'" }, word)
        }
        LogConfigurator().apply {
            fileName = runtime.files.nonNullPath(file)
            filePattern = pattern
            maxFileSize = fileSize
            maxBackupSize = backupSize
            isUseFileAppender = true
            isImmediateFlush = immediateFlush
            isResetConfiguration = resetConfiguration
            rootLevel = Level.valueOf(levelName ?: DEFAULT_LOG_LEVEL)
        }.configure()
    }

    @ScriptFunction
    fun resetGlobalLogConfig() {
        LogManager.getLoggerRepository().resetConfiguration()
    }

    @ScriptFunction
    fun launch() {
        ConsoleUtils.launch()
    }

    /** Prints the stack of [throwable]: a Java Throwable, an Error object or a message string. */
    @ScriptFunction
    fun printAllStackTrace(throwable: Any?) {
        console.printAllStackTrace(throwableOf(throwable))
    }

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun err(cx: Context, scope: VarScope, vararg data: Any?) = error(cx, scope, *data)

    @ScriptFunction(aliases = ["showConsole"], global = ScriptGlobal.ONLY)
    fun openConsole(isReset: Any? = null): ProxyObject = show(isReset)

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun clearConsole(): ProxyObject = clear()

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun launchConsole() = launch()

    /** A read of [name] tries `name`, `getName` and `isName` among [members], as the current module. */
    @ScriptProxyGet
    fun read(members: Scriptable, name: String): Any? {
        val capitalized = name.upperFirst()
        listOf(name, "get$capitalized", "is$capitalized").forEach { key ->
            ScriptProxyObject.member(members, key).let { if (it !== Scriptable.NOT_FOUND) return it }
        }
        return Scriptable.NOT_FOUND
    }

    /**
     * A write of [name] calls the setter `setName` (or `name` itself when it is a setter name) with [value], an array
     * spread into the arguments, as the current module; `constructor` is ignored. A name without a setter is a TypeError
     * (the current module threw IllegalArgumentException, the obsolete module a TypeError).
     */
    @ScriptProxySet
    fun write(members: Scriptable, name: String, value: Any?) {
        if (name == "constructor") return
        val setterKey = if (SETTER_NAME.matches(name)) name else "set${name.upperFirst()}"
        val setter = ScriptableObject.getProperty(members, setterKey)
        if (setter !is Callable) throw GlobalValues.typeError("console", "\"$name\"", "a property with a setter ($setterKey)", value)
        val args: Array<Any?> = if (value is NativeArray) value.toTypedArray() else arrayOf(value)
        RhinoUtils.callFunction(runtime, setter, RhinoUtils.getDeclarationScope(members), members, args)
    }

    private fun errorText(cx: Context, scope: VarScope, data: Array<out Any?>): String {
        if (data.isEmpty() || UtilValues.typeOf(data[0]) == "string") return util.format(cx, scope, *data.map(FlowJsErrors::consoleValue).toTypedArray())
        // An Error message containing "%s" is data, not a format string, as in the current module.
        val inspector = Inspector.of(cx, scope)
        return data.joinToString(" ") { value -> FlowJsErrors.consoleValue(value).let { if (it !== value) it.toString() else inspector.format(value) } }
    }

    /** The stack of the calling script, as the current module captures it (the extra `true` hides this call). */
    private fun stackTrace(cx: Context, scope: VarScope): Any? {
        val holder = RhinoUtils.newNativeObject(scope)
        NativeError.js_captureStackTrace(cx, holder, runtime.topLevelScope.prop(NativeError.ERROR_TAG) as ScriptableObject, arrayOf<Any?>(holder), true)
        return holder.prop(NativeError.STACK_TAG)
    }

    private fun traceLevel(level: Any?): Int {
        val function = "console.trace"
        return when {
            ScriptArgs.isNullish(level) -> Log.DEBUG
            ScriptArgs.isString(level) -> ScriptArgs.string(level, function, "level").let { word ->
                TRACE_LEVELS[word.lowercase()] ?: throw GlobalValues.rangeError(function, "level", TRACE_LEVELS.keys.joinToString(" | ") { "'$it'" }, word)
            }
            ScriptArgs.isNumber(level) -> ScriptArgs.int(level, function, "level").also {
                if (it !in Log.VERBOSE..Log.ERROR) throw GlobalValues.rangeError(function, "level", "${Log.VERBOSE} to ${Log.ERROR}", level)
            }
            else -> throw GlobalValues.typeError(function, "level", "string | number", level)
        }
    }

    /** The configurator setter named by a `console.build` key, as the current module looks it up. */
    private fun configuratorFunction(key: String): String? {
        val names = listOf(if (SETTER_NAME.matches(key)) key else "set${key.upperFirst()}", if (GETTER_NAME.matches(key)) key else "is${key.upperFirst()}")
        val methods = CONFIGURATOR_METHODS ?: return names.find { ScriptableObject.getProperty(self, it) is Callable }
        return names.find { it in methods }
    }

    /** The level colors (a `Color` object is one color, not a color map); all types are checked before any is converted. */
    private fun contentTextColors(function: String, colors: Array<out Any?>): Array<Int?> {
        if (colors.size > LEVEL_KEYS.size) throw ScriptArgs.arityError(function, 0, LEVEL_KEYS.size, colors.size)
        val o = colors.getOrNull(0)
        val levels: List<Pair<String, Any?>> = when {
            colors.size != 1 -> colors.mapIndexed { i, value -> "colors[$i]" to value }
            o is NativeArray -> LEVEL_KEYS.indices.map { i -> "colors[$i]" to if (i < o.size) o[i] else null }
            o is NativeObject && o !is ColorNativeObject -> {
                o.keys.map { RhinoUtils.coerceString(it) }.firstOrNull { it !in LEVEL_KEYS }?.let {
                    throw GlobalValues.rangeError(function, "colorMap key", LEVEL_KEYS.joinToString(" | ") { key -> "'$key'" }, it)
                }
                LEVEL_KEYS.map { key -> "colorMap.$key" to o.prop(key) }
            }
            else -> return colorOf(function, "color", o).let { color -> Array(LEVEL_KEYS.size) { color } }
        }
        val plain = levels.map { (param, value) -> if (GlobalValues.isNullish(value)) null else plainColor(function, param, value) }
        return Array(levels.size) { i -> plain[i]?.let { colorOf(function, levels[i].first, it) } }
    }

    private fun tintOf(function: String, value: Any?): Int? = if (ScriptArgs.isNullish(value)) null else colorOf(function, "tint", value)

    /** [value] unwrapped, when it is of an omni color type; else a TypeError. */
    private fun plainColor(function: String, param: String, value: Any?): Any = when (val v = if (value is Wrapper && value !is NativeArray) value.unwrap() else value) {
        is ColorNativeObject, is ThemeColor, is Number, is CharSequence -> v
        else -> throw GlobalValues.typeError(function, param, COLOR_TYPES, value)
    }

    /** An omni color, converted as `s13n.color` converts it. */
    private fun colorOf(function: String, param: String, value: Any?): Int = when (val v = plainColor(function, param, value)) {
        is ColorNativeObject -> v.color
        is ThemeColor -> ColorUtils.toInt(v)
        is Number -> ColorUtils.toInt(v.toLong())
        else -> v.toString().let { text ->
            try {
                ColorUtils.toInt(text)
            } catch (_: Exception) {
                throw GlobalValues.rangeError(function, param, COLOR_TYPES, text)
            }
        }
    }

    private fun finite(function: String, param: String, value: Any?): Double = ScriptArgs.double(value, function, param).also {
        if (!it.isFinite()) throw GlobalValues.rangeError(function, param, "finite number", value)
    }

    private fun textSize(function: String, value: Any?): Float = finite(function, "size", value).toFloat()

    private fun alphaOf(function: String, value: Any?): Double = finite(function, "alpha", value)

    /** A boolean option where nullish is `true`, as in the current module. */
    private fun flag(function: String, param: String, value: Any?): Boolean = ScriptArgs.booleanOrNull(value, function, param) ?: true

    private fun throwableOf(value: Any?): Throwable = when (val v = if (value is Wrapper) value.unwrap() else value) {
        is Throwable -> v
        is CharSequence -> Exception(v.toString())
        is NativeError -> listOf("rhinoException", "javaException").firstNotNullOfOrNull { name -> (ScriptableObject.getProperty(v, name) as? Wrapper)?.unwrap() as? Exception }
            ?: Exception(ScriptableObject.getProperty(v, "message").let { if (it === Scriptable.NOT_FOUND || ScriptArgs.isNullish(it)) "" else Context.toString(it) })
        else -> throw GlobalValues.typeError("console.printAllStackTrace", "throwable", "Throwable | Error | string", value)
    }

    private companion object {

        const val COLOR_TYPES = "ColorHex | ColorInt | ColorName | Color | ThemeColor"

        const val DEFAULT_LOG_FILE = "android-log4j.log"
        const val DEFAULT_LOG_PATTERN = "%m%n"
        const val DEFAULT_LOG_FILE_SIZE = 512L * 1024
        const val DEFAULT_LOG_BACKUP_SIZE = 5
        const val DEFAULT_LOG_LEVEL = "ALL"

        val SETTER_NAME = Regex("^set[A-Z].*")
        val GETTER_NAME = Regex("^is[A-Z].*")

        val TRACE_LEVELS = linkedMapOf(
            "verbose" to Log.VERBOSE,
            "debug" to Log.DEBUG,
            "info" to Log.INFO,
            "warn" to Log.WARN,
            "error" to Log.ERROR,
        )

        /** The log levels in the order of `setContentTextColor`. */
        val LEVEL_KEYS = listOf("verbose", "log", "info", "warn", "error", "assert")

        val LOG_CONFIG_KEYS = listOf("file", "maxFileSize", "maxBackupSize", "rootLevel", "filePattern", "immediateFlush", "resetConfiguration")

        val LOG_LEVELS = listOf("OFF", "FATAL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL")

        /** The method names of the console configurator; null where its class cannot be loaded (the JVM tests). */
        val CONFIGURATOR_METHODS: Set<String>? by lazy {
            try {
                ConsoleImpl.Configurator::class.java.methods.map { it.name }.toSet()
            } catch (_: LinkageError) {
                null
            }
        }

        /** The first character upper-cased, as `StringUtils.uppercaseFirstChar` (whose class needs Android to load). */
        fun String.upperFirst() = replaceFirstChar { it.uppercaseChar() }

    }

}
