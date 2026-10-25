package org.autojs.autojs.runtime.api.modules.console

import android.util.Log
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.ConsoleImpl.Companion.DEFAULT_EXIT_ON_CLOSE_TIMEOUT
import org.autojs.autojs.core.console.log.Level
import org.autojs.autojs.core.console.log.LogConfigurator
import org.autojs.autojs.core.console.log.LogManager
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.ScriptableExtensions.hasProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.s13n.S13n
import org.autojs.autojs.runtime.api.modules.util.Util
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.ConsoleUtils
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.runtime.api.modules.flow.FlowJsErrors
import org.autojs.autojs.util.RhinoUtils.coerceFloatNumber
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceObject
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.coerceStringUppercase
import org.autojs.autojs.util.RhinoUtils.getDeclarationScope
import org.autojs.autojs.util.RhinoUtils.js_eval
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.autojs.autojs.util.StringUtils.uppercaseFirstChar
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeError
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

@Suppress("unused", "UNUSED_PARAMETER")
class Console(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    private val mRtConsole = scriptRuntime.console
    private val mTopLevelScope = scriptRuntime.topLevelScope

    private val mCaptureStack: BaseFunction = newBaseFunction(mTopLevelScope, FUNC_NAME_TRACE, { argList ->
        val (messageArg, level) = argList
        val message = "${Util.formatRhino(messageArg)}\n${getStackTrace()}"
        when (level) {
            is String -> when (level.uppercase()) {
                "VERBOSE" -> mRtConsole.verbose(message)
                "DEBUG" -> mRtConsole.log(message)
                "INFO" -> mRtConsole.info(message)
                "WARN" -> mRtConsole.warn(message)
                "ERROR" -> mRtConsole.error(message)
                else -> null
            }
            is Number -> when (coerceIntNumber(level, -1)) {
                Log.VERBOSE -> mRtConsole.verbose(message)
                Log.DEBUG -> mRtConsole.log(message)
                Log.INFO -> mRtConsole.info(message)
                Log.WARN -> mRtConsole.warn(message)
                Log.ERROR -> mRtConsole.error(message)
                else -> null
            }
            else -> null
        } ?: mRtConsole.log(message)
    }, NOT_CONSTRUCTABLE)

    private fun getStackTrace() = withRhinoContext(scriptRuntime) { cx ->
        newNativeObject(scriptRuntime.topLevelScope).also { o ->
            val globalErrorObject = mTopLevelScope.prop(NativeError.ERROR_TAG) as ScriptableObject
            NativeError.js_captureStackTrace(
                cx,
                mCaptureStack,
                globalErrorObject,
                arrayOf(o, mCaptureStack),
                // @Hint by SuperMonster003 on Jul 26, 2024.
                //  ! This is tricky to append an extra controlling argument.
                //  ! However, I don't have a better idea so far.
                //  ! zh-CN:
                //  ! 这样附加一个额外控制参数, 存在一定的技巧性, 但目前尚未想到更好的方法.
                true,
            )
        }.prop(NativeError.STACK_TAG)
    }

    companion object : ArgumentGuards() {

        private const val FUNC_NAME_TRACE = "trace"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun show(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsAtMost(args, 1) {
            when (it.size) {
                1 -> scriptRuntime.console.show(Context.toBoolean(it[0]))
                0 -> scriptRuntime.console.show()
            }
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun hide(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsIsEmpty(args) {
            scriptRuntime.console.hide()
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun reset(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsIsEmpty(args) {
            scriptRuntime.console.reset()
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clear(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsIsEmpty(args) {
            scriptRuntime.console.clear()
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun expand(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsIsEmpty(args) {
            scriptRuntime.console.expand()
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun collapse(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsIsEmpty(args) {
            scriptRuntime.console.collapse()
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun assert(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsLengthInRange(args, 1..2) {
            val (value, messageArg) = it
            val message = coerceString(messageArg, AssertionError::class.java.name)
            val result = evaluateAssertionValue(scriptRuntime, value)
            scriptRuntime.console.assertTrue(result, message)
            scriptRuntime.consoleProxyObject
        }

        internal fun evaluateAssertionValue(scriptRuntime: ScriptRuntime, value: Any?): Boolean = Context.toBoolean(
            when (value) {
                is Callable -> callFunction(
                    scriptRuntime,
                    value,
                    scriptRuntime.topLevelScope,
                    Undefined.SCRIPTABLE_UNDEFINED,
                    emptyArray(),
                )
                else -> value
            },
        )

        /**
         * Reads a line from the console input bar and evaluates it as JavaScript (`console.input("x = ") + 1`
         * yields a number when the user types `41`). Optional arguments are printed first like `console.log`.
         * zh-CN: 从控制台输入栏读取一行并作为 JavaScript 求值 (用户输入 `41` 时 `console.input("x = ") + 1` 得到数字).
         * 可选参数会像 `console.log` 一样先行打印.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun input(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? {
            val raw = rawInput(scriptRuntime, args)
            return js_eval(scriptRuntime.topLevelScope, raw)
        }

        /**
         * Reads a line from the console input bar as a plain string, blocking the script thread until the user
         * submits one. The floating console shows itself when nothing else displays this console.
         * zh-CN: 从控制台输入栏读取一行纯文本, 阻塞脚本线程直到用户提交. 若没有其他视图显示本控制台, 浮动控制台会自动显示.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun rawInput(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String {
            if (args.isNotEmpty()) {
                scriptRuntime.console.log(Util.formatRhino(*args))
            }
            return scriptRuntime.console.rawInput()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun log(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            return scriptRuntime.console.log(Util.formatRhino(*args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun verbose(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            return scriptRuntime.console.verbose(Util.formatRhino(*args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun info(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            return scriptRuntime.console.info(Util.formatRhino(*args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun warn(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            return scriptRuntime.console.warn(Util.formatRhino(*args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun error(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            return scriptRuntime.console.error(FlowJsErrors.formatConsole(args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun print(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            return scriptRuntime.console.print(Log.DEBUG, Util.formatRhino(*args))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun time(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) {
            when (it.size) {
                1 -> scriptRuntime.consoleTimeTable.save(
                    when {
                        it[0].isJsNullish() -> null
                        else -> Context.toString(it[0])
                    }
                )
                0 -> scriptRuntime.consoleTimeTable.save()
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun timeEnd(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) {
            when (it.size) {
                1 -> scriptRuntime.consoleTimeTable.print(
                    when {
                        it[0].isJsNullish() -> null
                        else -> Context.toString(it[0])
                    }
                )
                0 -> scriptRuntime.consoleTimeTable.print()
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun build(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ConsoleImpl.Configurator = ensureArgumentsAtMost(args, 1) {
            val (options) = it
            val configurator = scriptRuntime.console.configurator
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            opt.entries.forEach { entry ->
                val (keyArg, value) = entry
                val key = coerceString(keyArg)

                val fName = listOf(
                    when {
                        Regex("^set[A-Z].*").containsMatchIn(key) -> key
                        else -> "set${key.uppercaseFirstChar()}"
                    },
                    when {
                        Regex("^is[A-Z].*").containsMatchIn(key) -> key
                        else -> "is${key.uppercaseFirstChar()}"
                    },
                ).find { name ->
                    configurator::class.java.methods.any { method -> method.name == name }
                } ?: throw WrappedIllegalArgumentException("\$Unknown key \"$key\" of options for builder.build")

                val console = scriptRuntime.consoleProxyObject
                invokeConfiguratorFunction(scriptRuntime, console, fName, value)
            }

            configurator
        }

        internal fun invokeConfiguratorFunction(
            scriptRuntime: ScriptRuntime,
            console: Scriptable,
            fName: String,
            value: Any?,
        ) {
            val func = console.prop(fName)
            require(func is Callable) {
                "console.$fName must be a JavaScript Function instead of a ${func.jsBrief()}"
            }
            val args = if (value is NativeArray) value.toTypedArray() else arrayOf(value)
            callFunction(scriptRuntime, func, getDeclarationScope(console), console, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setSize(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsLength(args, 2) {
            val (wArg, hArg) = it
            val w = DisplayUtils.toRoundDoubleX(coerceNumber(wArg, -1))
            val h = DisplayUtils.toRoundDoubleY(coerceNumber(hArg, -1))
            scriptRuntime.console.setSize(w, h)
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setPosition(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsLength(args, 2) {
            val (xArg, yArg) = it
            val x = DisplayUtils.toRoundDoubleX(coerceNumber(xArg, 0))
            val y = DisplayUtils.toRoundDoubleY(coerceNumber(yArg, 0))
            scriptRuntime.console.setPosition(x, y)
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTitle(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTitle(coerceString(it, ""))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTitleTextSize(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTitleTextSize(coerceFloatNumber(it))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTitleTextColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTitleTextColor(S13n.color(arrayOf(it)))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTitleBackgroundColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTitleBackgroundColor(S13n.color(arrayOf(it)))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTitleBackgroundTint(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTitleBackgroundTint(it?.let { S13n.color(arrayOf(it)) })
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTitleBackgroundAlpha(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTitleBackgroundAlpha(coerceNumber(it, 1.0))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTitleIconsTint(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTitleIconsTint(it?.let { S13n.color(arrayOf(it)) })
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setContentTextSize(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setContentTextSize(coerceFloatNumber(it))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setContentTextColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsAtMost(args, 6) { argList ->
            when (argList.size) {
                1 -> when (val o = argList[0]) {
                    is NativeArray -> {
                        val tmp = Array(6) { index ->
                            if (index < o.size) S13n.color(arrayOf(o[index])) else null
                        }
                        scriptRuntime.console.setContentTextColors(tmp)
                    }
                    is NativeObject -> {
                        val tmp = Array<Int?>(6) { null }
                        listOf("verbose", "log", "info", "warn", "error", "assert").forEachIndexed { index, key ->
                            if (o.hasProp(key)) {
                                tmp[index] = S13n.color(arrayOf(o.prop(key)))
                            }
                        }
                        scriptRuntime.console.setContentTextColors(tmp)
                    }
                    else -> scriptRuntime.console.setContentTextColors(Array(6) { S13n.color(arrayOf(o)) })
                }
                else -> {
                    scriptRuntime.console.setContentTextColors(argList.map { S13n.color(arrayOf(it)) }.toTypedArray())
                }
            }
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setContentTextColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = setContentTextColor(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setContentBackgroundColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setContentBackgroundColor(S13n.color(arrayOf(it)))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setContentBackgroundTint(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setContentBackgroundTint(it?.let { S13n.color(arrayOf(it)) })
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setContentBackgroundAlpha(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setContentBackgroundAlpha(coerceNumber(it, 1.0))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTextSize(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTextSize(coerceFloatNumber(it))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTextColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTextColor(S13n.color(arrayOf(it)))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setBackgroundColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setBackgroundColor(S13n.color(arrayOf(it)))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setBackgroundTint(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setBackgroundTint(S13n.color(arrayOf(it)))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setBackgroundAlpha(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setBackgroundAlpha(coerceNumber(it, 1.0))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setExitOnClose(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsAtMost(args, 1) {
            when (it.size) {
                1 -> when (it[0]) {
                    is Number -> scriptRuntime.console.setExitOnClose(coerceLongNumber(it[0], DEFAULT_EXIT_ON_CLOSE_TIMEOUT))
                    else -> scriptRuntime.console.setExitOnClose(coerceBoolean(it[0], true))
                }
                0 -> scriptRuntime.console.setExitOnClose()
            }
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTouchable(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsAtMost(args, 1) {
            when (it.size) {
                1 -> scriptRuntime.console.setTouchable(coerceBoolean(it[0], true))
                0 -> scriptRuntime.console.setTouchable()
            }
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTimeVisible(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsAtMost(args, 1) {
            scriptRuntime.console.setTimeVisible(if (it.isEmpty()) true else coerceBoolean(it[0], true))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTimeFormat(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsAtMost(args, 1) {
            scriptRuntime.console.setTimeFormat(if (it.isEmpty() || it[0].isJsNullish()) null else coerceString(it[0]))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setColorful(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsAtMost(args, 1) {
            scriptRuntime.console.setColorful(if (it.isEmpty()) true else coerceBoolean(it[0], true))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setAvoidStatusBar(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsAtMost(args, 1) {
            scriptRuntime.console.setAvoidStatusBar(if (it.isEmpty()) true else coerceBoolean(it[0], true))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setInputVisible(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsAtMost(args, 1) {
            scriptRuntime.console.setInputVisible(if (it.isEmpty()) true else coerceBoolean(it[0], true))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setGlobalLogConfig(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { config ->
            require(config is NativeObject) { "Argument for console.${::setGlobalLogConfig.name} must be a JavaScript Object" }
            LogConfigurator().apply {
                fileName = scriptRuntime.files.nonNullPath(config.inquire("file", ::coerceString, "android-log4j.log"))
                filePattern = config.inquire("filePattern", ::coerceString, "%m%n")
                maxFileSize = config.inquire("maxFileSize", ::coerceLongNumber, 512 * 1024)
                maxBackupSize = config.inquire("maxBackupSize", ::coerceIntNumber, 5)
                isUseFileAppender = true
                isImmediateFlush = config.inquire("immediateFlush", ::coerceBoolean, true)
                isResetConfiguration = config.inquire("resetConfiguration", ::coerceBoolean, true)
                rootLevel = Level.valueOf(config.inquire("rootLevel", ::coerceStringUppercase, "ALL"))
            }.configure()
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun resetGlobalLogConfig(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            undefined { LogManager.getLoggerRepository().resetConfiguration() }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launch(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            undefined { ConsoleUtils.launch() }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun printAllStackTrace(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            undefined { scriptRuntime.console.printAllStackTrace(S13n.throwable(arrayOf(it))) }
        }

    }

}
