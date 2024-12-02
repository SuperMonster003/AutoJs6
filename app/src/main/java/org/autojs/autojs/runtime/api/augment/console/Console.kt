package org.autojs.autojs.runtime.api.augment.console

import android.util.Log
import de.mindpipe.android.logging.log4j.LogConfigurator
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.ConsoleImpl.Companion.DEFAULT_EXIT_ON_CLOSE_TIMEOUT
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.hasProp
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.AugmentableProxy
import org.autojs.autojs.runtime.api.augment.s13n.S13n
import org.autojs.autojs.runtime.api.augment.util.Util
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ConsoleUtils
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceFloatNumber
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceObject
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.coerceStringUppercase
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.autojs.autojs.util.StringUtils.lowercaseFirstChar
import org.autojs.autojs.util.StringUtils.uppercaseFirstChar
import org.autojs.autojs6.R
import org.mozilla.javascript.*

@Suppress("unused", "UNUSED_PARAMETER")
class Console(scriptRuntime: ScriptRuntime) : AugmentableProxy(scriptRuntime) {

    private val mRtConsole = scriptRuntime.console
    private val mTopLevelScope = scriptRuntime.topLevelScope

    private val mCaptureStack: BaseFunction = newBaseFunction(FUNC_NAME_TRACE, { argList ->
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

    override val selfAssignmentProperties = listOf(
        FUNC_NAME_TRACE to mCaptureStack,
    )

    override val selfAssignmentFunctions = listOf(
        ::show.name,
        ::hide.name,
        ::reset.name,
        ::clear.name,
        ::expand.name,
        ::collapse.name,
        ::assert.name,
        ::input.name,
        ::rawInput.name,
        ::log.name to AS_GLOBAL,
        ::verbose.name to AS_GLOBAL,
        ::info.name,
        ::warn.name to AS_GLOBAL,
        ::error.name,
        ::print.name to AS_GLOBAL,
        ::time.name,
        ::timeEnd.name,
        ::build.name,
        ::setSize.name,
        ::setPosition.name,
        ::setTitle.name,
        ::setTitleTextSize.name,
        ::setTitleTextColor.name,
        ::setTitleBackgroundColor.name,
        ::setTitleBackgroundTint.name,
        ::setTitleBackgroundAlpha.name,
        ::setTitleIconsTint.name,
        ::setContentTextSize.name,
        ::setContentTextColor.name,
        ::setContentTextColors.name,
        ::setContentBackgroundTint.name,
        ::setContentBackgroundAlpha.name,
        ::setTextSize.name,
        ::setTextColor.name,
        ::setBackgroundColor.name,
        ::setBackgroundTint.name,
        ::setBackgroundAlpha.name,
        ::setExitOnClose.name,
        ::setTouchable.name,
        ::setGlobalLogConfig.name,
        ::resetGlobalLogConfig.name,
        ::launch.name,
        ::printAllStackTrace.name,
    )

    override val globalAssignmentFunctions = listOf(
        ::error.name to "err",
        ::show.name to listOf("openConsole", "showConsole"),
        ::clear.name to "clearConsole",
        ::launch.name to "launchConsole",
    )

    private fun getStackTrace() = withRhinoContext { context ->
        newNativeObject().also { o ->
            val globalErrorObject = mTopLevelScope.prop(NativeError.ERROR_TAG) as ScriptableObject
            NativeError.js_captureStackTrace(
                context,
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

    companion object : FlexibleArray() {

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
            val result = when (value) {
                is BaseFunction -> Context.toBoolean(callFunction(scriptRuntime, value, scriptRuntime.topLevelScope, arrayOf()))
                else -> Context.toBoolean(value)
            }
            scriptRuntime.console.assertTrue(result, message)
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun input(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            val s = "${lowercaseFirstChar(Console::class.java.simpleName)}.${::input.name}"
            throw RuntimeException(globalContext.getString(R.string.error_abandoned_method, s))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun rawInput(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            val s = "${lowercaseFirstChar(Console::class.java.simpleName)}.${::rawInput.name}"
            throw RuntimeException(globalContext.getString(R.string.error_abandoned_method, s))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun log(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = unwrapArguments(args) {
            scriptRuntime.console.log(Util.formatRhino(*it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun verbose(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = unwrapArguments(args) {
            scriptRuntime.console.verbose(Util.formatRhino(*it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun info(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = unwrapArguments(args) {
            scriptRuntime.console.info(Util.formatRhino(*it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun warn(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = unwrapArguments(args) {
            scriptRuntime.console.warn(Util.formatRhino(*it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun error(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = unwrapArguments(args) {
            scriptRuntime.console.error(Util.formatRhino(*it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun print(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = unwrapArguments(args) {
            scriptRuntime.console.print(Log.DEBUG, Util.formatRhino(*it))
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
            val opt = coerceObject(options, newNativeObject())

            opt.entries.forEach { entry ->
                val (keyArg, value) = entry
                val key = coerceString(keyArg)

                val fName = listOf(
                    when {
                        Regex("^set[A-Z].*").containsMatchIn(key) -> key
                        else -> "set${uppercaseFirstChar(key)}"
                    },
                    when {
                        Regex("^is[A-Z].*").containsMatchIn(key) -> key
                        else -> "is${uppercaseFirstChar(key)}"
                    },
                ).find { name ->
                    configurator::class.java.methods.any { method -> method.name == name }
                } ?: throw WrappedIllegalArgumentException("\$Unknown key \"$key\" of options for builder.build")

                val console = scriptRuntime.consoleProxyObject
                val func = console.prop(fName)
                require(func is BaseFunction) {
                    "console.$fName must be a JavaScript Function instead of a ${func.jsBrief()}"
                }
                if (value is NativeArray) {
                    callFunction(scriptRuntime, func, console, console, value.toTypedArray())
                } else {
                    callFunction(scriptRuntime, func, console, console, arrayOf(value))
                }
            }

            configurator
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
            scriptRuntime.console.setTitleBackgroundTint(S13n.color(arrayOf(it)))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTitleBackgroundAlpha(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTitleBackgroundAlpha(ColorUtils.toUnit8(coerceNumber(it, 1.0), true))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTitleIconsTint(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setTitleIconsTint(S13n.color(arrayOf(it)))
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
                        scriptRuntime.console.setContentTextColors(o.map { S13n.color(arrayOf(it)) }.toTypedArray())
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
        fun setContentBackgroundTint(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setContentBackgroundTint(S13n.color(arrayOf(it)))
            scriptRuntime.consoleProxyObject
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setContentBackgroundAlpha(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyObject = ensureArgumentsOnlyOne(args) {
            scriptRuntime.console.setContentBackgroundAlpha(ColorUtils.toUnit8(coerceNumber(it, 1.0), true))
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
            scriptRuntime.console.setBackgroundAlpha(ColorUtils.toUnit8(coerceNumber(it, 1.0), true))
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
        fun setGlobalLogConfig(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { config ->
            require(config is NativeObject) { "Argument for console.${::setGlobalLogConfig.name} must be a JavaScript Object" }
            LogConfigurator().apply {
                fileName = scriptRuntime.files.path(config.inquire("file", ::coerceString, "android-log4j.log"))
                filePattern = config.inquire("filePattern", ::coerceString, "%m%n")
                maxFileSize = config.inquire("maxFileSize", ::coerceLongNumber, 512 * 1024)
                maxBackupSize = config.inquire("maxBackupSize", ::coerceIntNumber, 5)
                isUseFileAppender = true
                isImmediateFlush = config.inquire("immediateFlush", ::coerceBoolean, true)
                isResetConfiguration = config.inquire("resetConfiguration", ::coerceBoolean, true)
                Level::class.java.getDeclaredField(
                    config.inquire("rootLevel", ::coerceStringUppercase, "ALL")
                ).apply { isAccessible = true }.get(null)?.let {
                    rootLevel = it as Level
                }
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
