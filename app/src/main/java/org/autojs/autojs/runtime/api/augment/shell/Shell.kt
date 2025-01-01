package org.autojs.autojs.runtime.api.augment.shell

import android.util.Log
import android.view.KeyEvent
import androidx.annotation.IntRange
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.app.App
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs.util.RootUtils
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined

@Suppress("unused", "FunctionName", "UNUSED_PARAMETER")
class Shell(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::execCommand.name,
        ::getCommand.name,
        ::fromIntent.name,
        ::kill.name,
        ::currentPackage.name,
        ::currentActivity.name,
        ::currentComponent.name,
    )

    override val globalAssignmentFunctions = listOf(
        ::Menu.name,
        ::Home.name,
        ::Back.name,
        ::Up.name,
        ::Down.name,
        ::Left.name,
        ::Right.name,
        ::OK.name,
        ::VolumeUp.name,
        ::VolumeDown.name,
        ::Power.name,
        ::Camera.name,
        ::Text.name,
        ::Input.name,
        ::Tap.name,
        ::Screencap.name,
        ::KeyCode.name,
        ::SetScreenMetrics.name,
        ::Swipe.name,
    )

    override fun invoke(vararg args: Any?): AbstractShell.Result = execCommand(scriptRuntime, args)

    companion object : FlexibleArray() {

        private val TAG = Shell::class.java.simpleName

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun execCommand(scriptRuntime: ScriptRuntime, args: Array<out Any?>): AbstractShell.Result = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val commandData = getCommandData(argList)
            scriptRuntime.shell(commandData.command, commandData.withRoot)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getCommand(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val commandData = getCommandData(argList)
            val command = commandData.command
            if (commandData.withRoot == 1) "su\n$command" else command
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun fromIntent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) {
            App.intentToShellRhino(it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun kill(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { app ->
            if (!RootUtils.isRootAvailable()) return@ensureArgumentsOnlyOne false
            when (val packageName = App.getPackageName(scriptRuntime, arrayOf(app))) {
                null -> false
                else -> execCommand(scriptRuntime, arrayOf("am force-stop $packageName", true)).code == 0
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            currentComponent(scriptRuntime, args).substringBefore("/")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            val component = currentComponent(scriptRuntime, args)
            val className = component.substringAfterLast("/")
            when {
                className.startsWith(".") -> component
                else -> className
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentComponent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            if (!RootUtils.isRootAvailable()) return@ensureArgumentsIsEmpty ""
            try {
                val process = Runtime.getRuntime().exec("su -c dumpsys activity activities")
                process.inputStream.bufferedReader().useLines { lines ->
                    val resumedActivityLine = lines.find {
                        it.contains("Resumed:") || it.contains("ResumedActivity")
                    }
                    resumedActivityLine?.let { line ->
                        Log.d(TAG, "Found Resumed Activity: $line")
                        line.split("\\s+".toRegex()).firstOrNull { part ->
                            part.contains("/")
                        }?.let { part ->
                            Log.d(TAG, "current activity part: $part")
                            return@ensureArgumentsIsEmpty part.replace("\\W+$".toRegex(), "")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading current component", e)
            }
            return@ensureArgumentsIsEmpty ""
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Menu(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_MENU))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Home(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_HOME))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Back(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_BACK))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Up(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_DPAD_UP))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Down(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_DPAD_DOWN))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Left(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_DPAD_LEFT))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Right(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_DPAD_RIGHT))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun OK(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_DPAD_CENTER))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun VolumeUp(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_VOLUME_UP))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun VolumeDown(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_VOLUME_DOWN))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Power(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_POWER))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Camera(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            KeyCode(scriptRuntime, arrayOf(KeyEvent.KEYCODE_CAMERA))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Text(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            undefined { scriptRuntime.rootShell.Text(coerceString(it)) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Input(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            undefined { scriptRuntime.rootShell.Text(coerceString(it)) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Tap(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLength(args, 2) {
            val (x, y) = it
            undefined { scriptRuntime.rootShell.Tap(coerceIntNumber(x), coerceIntNumber(y)) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Screencap(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            undefined { scriptRuntime.rootShell.Screencap(coerceString(it)) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun KeyCode(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { keyCode ->
            undefined { scriptRuntime.rootShell.KeyCode(coerceString(keyCode)) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun SetScreenMetrics(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLength(args, 2) {
            val (w, h) = it
            undefined { scriptRuntime.rootShell.SetScreenMetrics(coerceIntNumber(w), coerceIntNumber(h)) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Swipe(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 4..5) {
            val (x1, y1, x2, y2, duration) = it
            undefined {
                when {
                    duration.isJsNullish() -> scriptRuntime.rootShell.Swipe(
                        coerceIntNumber(x1),
                        coerceIntNumber(y1),
                        coerceIntNumber(x2),
                        coerceIntNumber(y2),
                    )
                    else -> scriptRuntime.rootShell.Swipe(
                        coerceIntNumber(x1),
                        coerceIntNumber(y1),
                        coerceIntNumber(x2),
                        coerceIntNumber(y2),
                        coerceIntNumber(duration),
                    )
                }
            }
        }

        fun getCommandData(argList: Array<Any?>): CommandData {
            val (arg0, arg1, arg2) = argList
            val cmdBody = when (arg0) {
                is String -> arg0
                is NativeArray -> arg0.joinToString("\n") { coerceString(it) }
                else -> throw IllegalStateException(
                    "Argument cmd must be a string or JavaScript Array instead of ${arg0.jsBrief()}"
                )
            }
            val argsData: CommandArgumentsData = when (argList.size) {
                3 -> when (arg1) {
                    is String -> parseArguments(arg1, coerceBoolean(arg2, false))
                    is NativeObject -> parseArguments(arg1, coerceBoolean(arg2, false))
                    else -> throw IllegalStateException(
                        "Argument args must be a string or JavaScript object instead of ${arg1.jsBrief()}"
                    )
                }
                2 -> when (arg1) {
                    is Number, is Boolean -> CommandArgumentsData(withRoot = coerceBoolean(arg1, false))
                    is NativeObject -> parseArguments(arg1)
                    is String -> parseArguments(arg1)
                    else -> listOf(
                        "Argument[1] must be",
                        "either a string or JavaScript object as \"arguments\"",
                        "or a boolean or number as \"withRoot\"",
                        "instead of ${arg1.jsBrief()}",
                    ).joinToString("\u0020").let { throw IllegalArgumentException(it) }
                }
                1 -> CommandArgumentsData()
                else -> throw ShouldNeverHappenException()
            }
            var niceCommand = when (val cmdArgs = argsData.arguments) {
                "" -> cmdBody
                else -> "$cmdBody $cmdArgs"
            }
            if (argsData.withExit) {
                niceCommand = listOf(niceCommand, "exit", "kill $$").joinToString("\n")
            }
            val niceIsRoot = if (argsData.withRoot) 1 else 0
            val commandData = CommandData(niceCommand, niceIsRoot)
            return commandData
        }

        private fun parseArguments(o: Map<*, *>, withRoot: Boolean? = null): CommandArgumentsData {
            val command = StringBuilder()

            var tmpWithRoot = false
            var tmpWithExit = false

            for ((keyArg, value) in o) {
                val key = coerceString(keyArg)
                when (key) {
                    "root" -> {
                        tmpWithRoot = "$value" != "false"
                        continue
                    }
                    "exit" -> {
                        tmpWithExit = "$value" != "false"
                        continue
                    }
                }

                // Determine if key is in short form or long form
                val formattedKey = when {
                    key.startsWith("--") -> "--" + toKebabCase(key.removePrefix("--"))
                    key.startsWith("-") -> key // Keep as is if it starts with a single "-"
                    key.length in 1..2 -> "-$key" // Short form
                    else -> "--" + toKebabCase(key) // Long form
                }

                // Check if argument needs to be quoted
                val formattedValue = when {
                    value is String && (value.contains(" ") || value.startsWith("-")) -> {
                        "\"$value\""
                    }
                    else -> coerceString(value, "")
                }

                when {
                    value is Boolean && value -> command.append("$formattedKey ")
                    else -> command.append("$formattedKey $formattedValue ")
                }
            }
            return CommandArgumentsData(command.toString().trim(), tmpWithRoot || withRoot == true, tmpWithExit)
        }

        private fun parseArguments(s: String, withRoot: Boolean? = null): CommandArgumentsData {
            val str = if (withRoot == true) "$s|root" else s
            return str.split(Regex("\\s*\\|\\s*")).map {
                if (it.contains("=")) {
                    val (key, value) = it.split(Regex("\\s*=\\s*"))
                    key to value
                } else {
                    it to true
                }
            }.let { parseArguments(it.toMap()) }
        }

        private fun toKebabCase(input: String) = input
            .replace(Regex("([a-z])([A-Z])"), "$1-$2") // handle camelCase to kebab-case
            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1-$2") // handle parseHTML as parse-html
            .replace(Regex("([a-z])([0-9])"), "$1-$2") // handle letters followed by numbers
            .lowercase()

        private fun completeActivityNames(name: String): String {
            val parts = name.split("/")
            if (parts.size == 2) {
                val (packageName, activityName) = parts
                if (activityName.startsWith(".")) {
                    return "$packageName/$packageName$activityName"
                }
            }
            return name
        }

        data class CommandArgumentsData(val arguments: String = "", val withRoot: Boolean = false, val withExit: Boolean = false)

        data class CommandData(val command: String, @IntRange(0, 1) val withRoot: Int = 0)

    }

}
