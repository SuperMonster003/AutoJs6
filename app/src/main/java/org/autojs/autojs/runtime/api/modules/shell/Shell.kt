package org.autojs.autojs.runtime.api.modules.shell

import android.util.Log
import android.view.KeyEvent
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component4
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component5
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.modules.app.App
import org.autojs.autojs.runtime.api.shell.ShellLauncher
import org.autojs.autojs.runtime.api.shell.ShellPipes
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import java.util.Collections
import java.util.WeakHashMap

@Suppress("unused", "FunctionName", "UNUSED_PARAMETER")
class Shell(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    fun invoke(vararg args: Any?): AbstractShell.Result = execCommand(scriptRuntime, args)

    companion object : ArgumentGuards() {

        private const val TAG = "Shell"
        private const val KEY_CODE_LABEL_PREFIX = "KEYCODE_"

        private val DEFAULT_OPTION_KEYS = setOf("root", "adb")

        /**
         * Per-script state of `$shell.setDefaultOptions` (Auto.js Pro compatible). `null` means "not set".
         * zh-CN: `$shell.setDefaultOptions` 的每脚本状态 (兼容 Auto.js Pro). `null` 表示未设置.
         */
        private class RuntimeDefaults {
            @Volatile
            var root: Boolean? = null

            @Volatile
            var adb: Boolean? = null

            val adbShell: AbstractShell by lazy { ShizukuCommandShell() }
        }

        private val runtimeDefaults: MutableMap<ScriptRuntime, RuntimeDefaults> = Collections.synchronizedMap(WeakHashMap())

        private fun defaultsOf(scriptRuntime: ScriptRuntime): RuntimeDefaults? = runtimeDefaults[scriptRuntime]

        /**
         * Runs each compatibility command (Tap, Swipe, KeyCode, Text, Screencap) as one Shizuku command; used after
         * `setDefaultOptions({ adb: true })`, as Auto.js Pro does.
         * zh-CN: 把每条兼容命令 (Tap, Swipe, KeyCode, Text, Screencap) 作为一条 Shizuku 命令执行; 在 `setDefaultOptions({ adb: true })` 之后使用,
         * 与 Auto.js Pro 相同.
         */
        private class ShizukuCommandShell : AbstractShell(ScriptRuntime.applicationContext, false, false) {
            override fun init(initialCommand: String?) = Unit

            override fun exec(command: String) {
                val result = WrappedShizuku.execCommand(command)
                if (result.code != 0) Log.w(TAG, "Shizuku command \"$command\" failed: $result")
            }

            override fun exit() = Unit

            override fun exitAndWaitFor() = Unit
        }

        /** The shell behind the global compatibility functions. zh-CN: 全局兼容函数背后的 shell. */
        private fun compatShell(scriptRuntime: ScriptRuntime): AbstractShell =
            defaultsOf(scriptRuntime)?.takeIf { it.adb == true }?.adbShell ?: scriptRuntime.rootShell

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun execCommand(scriptRuntime: ScriptRuntime, args: Array<out Any?>): AbstractShell.Result = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val defaults = defaultsOf(scriptRuntime)
            val commandData = getCommandData(argList, defaults?.root, defaults?.adb)
            when {
                commandData.withAdb -> scriptRuntime.shizuku.execCommand(
                    when (commandData.withRoot) {
                        1 -> "su -c " + ShellLauncher.quote(commandData.command)
                        else -> commandData.command
                    }
                )
                else -> scriptRuntime.shell(commandData.command, commandData.withRoot)
            }
        }

        /**
         * `shell.spawn(command, options?)`: runs [command] as a process with streams, see [ShellProcess]. Options:
         * a boolean (`root`) or `{ root?, adb?, env?, cwd? }`; absent keys follow `setDefaultOptions`.
         * zh-CN: `shell.spawn(command, options?)`: 以带流的进程运行命令, 见 [ShellProcess]. 选项: 布尔值 (`root`) 或 `{ root?, adb?, env?, cwd? }`;
         * 缺少的键沿用 `setDefaultOptions`.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun spawn(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ShellProcess = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (command, options) = argList
            spawnRhino(scriptRuntime, command, options, forceAdb = false)
        }

        internal fun spawnRhino(scriptRuntime: ScriptRuntime, command: Any?, options: Any?, forceAdb: Boolean): ShellProcess {
            val commandLine = when (command) {
                is CharSequence -> command.toString()
                is NativeArray -> command.joinToString("\n") { coerceString(it) }
                else -> throw IllegalArgumentException("Argument command must be a string or an array of strings instead of ${command.jsBrief()}")
            }
            require(commandLine.isNotBlank()) { "Argument command must not be blank" }
            // shizuku.spawn keeps out of the shell module's setDefaultOptions, like shizuku() does.
            // zh-CN: shizuku.spawn 与 shizuku() 一样, 不受 shell 模块 setDefaultOptions 的影响.
            val base = when {
                forceAdb -> ShellLauncher.Options(adb = true)
                else -> defaultsOf(scriptRuntime).let { ShellLauncher.Options(root = it?.root ?: false, adb = it?.adb ?: false) }
            }
            val parsed = when (options) {
                null, is Undefined -> base
                is Boolean -> base.copy(root = options)
                is Map<*, *> -> ShellPipes.optionsOf(options, scriptRuntime, base)
                else -> throw IllegalArgumentException("Argument options must be a boolean or an object instead of ${options.jsBrief()}")
            }
            require(parsed.cmd == null) { "Shell option \"cmd\" applies to new Shell(options) only" }
            return ShellProcess.start(scriptRuntime, commandLine, if (forceAdb) parsed.copy(adb = true) else parsed)
        }

        /**
         * `$shell.setDefaultOptions({ root?, adb? })` (Auto.js Pro compatible): defaults of `shell()`, `shell.spawn()` and
         * (with `adb`) of the compatibility functions such as Tap, for this script; null or undefined clears them.
         * zh-CN: `$shell.setDefaultOptions({ root?, adb? })` (兼容 Auto.js Pro): 本脚本中 `shell()`, `shell.spawn()` 以及 (指定 `adb` 时) Tap 等兼容函数的
         * 默认选项; 传入 null 或 undefined 时清除.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setDefaultOptions(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { options ->
            when {
                options.isJsNullish() -> runtimeDefaults.remove(scriptRuntime)
                options is Map<*, *> -> {
                    options.keys.map { coerceString(it) }.firstOrNull { it !in DEFAULT_OPTION_KEYS }?.let { unknown ->
                        throw IllegalArgumentException("Unknown default shell option \"$unknown\", expected one of ${DEFAULT_OPTION_KEYS.joinToString(", ")}")
                    }
                    synchronized(runtimeDefaults) { runtimeDefaults.getOrPut(scriptRuntime) { RuntimeDefaults() } }.apply {
                        root = options["root"].takeUnless { it.isJsNullish() }?.let { coerceBoolean(it) }
                        adb = options["adb"].takeUnless { it.isJsNullish() }?.let { coerceBoolean(it) }
                    }
                }
                else -> throw IllegalArgumentException("Argument options must be an object instead of ${options.jsBrief()}")
            }
            UNDEFINED
        }

        /**
         * `$shell.checkAccess("root" | "adb")` (Auto.js Pro compatible). "root" runs `id` through su and looks for uid 0
         * (su may ask for the grant); "adb" means Shizuku is running and grants AutoJs6 access.
         * zh-CN: `$shell.checkAccess("root" | "adb")` (兼容 Auto.js Pro). "root" 经 su 执行 `id` 并检查 uid 是否为 0 (su 可能请求授权); "adb" 表示 Shizuku
         * 正在运行且已授权 AutoJs6.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun checkAccess(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { type ->
            when (val name = coerceString(type)) {
                "root" -> ProcessShell.execCommand("id", true).let { it.code == 0 && it.result.contains("uid=0") }
                "adb" -> WrappedShizuku.isOperational()
                else -> throw IllegalArgumentException("Unknown access type \"$name\", expected \"root\" or \"adb\"")
            }
        }

        /**
         * `$shell.isRootAvailable()` (Auto.js Pro compatible): whether the device has root, not whether it is granted.
         * zh-CN: `$shell.isRootAvailable()` (兼容 Auto.js Pro): 设备是否具备 root, 而非是否已授权.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isRootAvailable(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            RootUtils.isRootAvailable()
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
            App.intentToShellRhino(scriptRuntime.topLevelScope, it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun kill(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { app ->
            if (!RootUtils.isRootAvailable()) return@ensureArgumentsOnlyOne false
            when (val packageName = App.getPackageName(scriptRuntime, arrayOf(app))) {
                null -> false
                else -> execCommand(scriptRuntime, arrayOf<Any>("am force-stop $packageName", true)).code == 0
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            currentPackageRhino()
        }

        @JvmStatic
        fun currentPackageRhino(): String = currentComponentRhino().substringBefore("/")

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            currentActivityRhino()
        }

        @JvmStatic
        fun currentActivityRhino(): String {
            val component = currentComponentRhino()
            val className = component.substringAfterLast("/")
            return when {
                className.startsWith(".") -> component
                else -> className
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentComponent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            currentComponentRhino()
        }

        @JvmStatic
        fun currentComponentRhino(): String {
            if (!RootUtils.isRootAvailable()) return ""
            try {
                val process = Runtime.getRuntime().exec("su -c dumpsys activity activities")
                process.inputStream.bufferedReader().useLines { lines ->
                    val resumedActivityLine = lines.find {
                        it.contains("Resumed:") || it.contains("ResumedActivity")
                    } ?: return@useLines

                    Log.d(TAG, "Found Resumed Activity: $resumedActivityLine")

                    val activityPart = resumedActivityLine.split("\\s+".toRegex()).firstOrNull { part ->
                        part.contains("/")
                    } ?: return@useLines

                    Log.d(TAG, "current activity part: $activityPart")

                    return activityPart.replace("\\W+$".toRegex(), "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading current component", e)
            }
            return ""
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
            undefined {
                val value = coerceString(it)
                if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.text(value)
                else compatShell(scriptRuntime).Text(value)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Input(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            undefined {
                val value = coerceString(it)
                if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.text(value)
                else compatShell(scriptRuntime).Text(value)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Tap(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLength(args, 2) {
            val (x, y) = it
            undefined {
                val px = coerceIntNumber(x)
                val py = coerceIntNumber(y)
                if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.tap(px, py)
                else compatShell(scriptRuntime).Tap(px, py)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Screencap(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            undefined {
                val path = coerceString(it)
                if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.captureToFile(path)
                else compatShell(scriptRuntime).Screencap(path)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun KeyCode(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { keyCode ->
            val raw = coerceString(keyCode)
            val codeNumber = parseKeyCodeNumber(raw)
            require(codeNumber != KeyEvent.KEYCODE_UNKNOWN) {
                GlobalAppContext.get().getString(R.string.error_unknown_key_code, raw)
            }
            if (scriptRuntime.displayRoute.isVirtual) {
                scriptRuntime.displayRoute.key(codeNumber)
                return@ensureArgumentsOnlyOne UNDEFINED
            }
            val defaults = defaultsOf(scriptRuntime)
            when {
                defaults?.adb == true -> defaults.adbShell.KeyCode(codeNumber)
                WrappedShizuku.isOperational() -> WrappedShizuku.execCommand("input keyevent $codeNumber")
                else -> scriptRuntime.rootShell.KeyCode(codeNumber)
            }
            return@ensureArgumentsOnlyOne UNDEFINED
        }

        private fun parseKeyCodeNumber(raw: String): Int {
            val upperCased = raw.uppercase()
            val codeNumber = when {
                raw.startsWith(KEY_CODE_LABEL_PREFIX) -> KeyEvent.keyCodeFromString(upperCased)
                raw.startsWith("0x", true) -> upperCased.substring(2).toInt(16)
                else -> {
                    val firstTry = KeyEvent.keyCodeFromString(upperCased)
                    if (firstTry != KeyEvent.KEYCODE_UNKNOWN) {
                        firstTry
                    } else {
                        KeyEvent.keyCodeFromString(KEY_CODE_LABEL_PREFIX + upperCased)
                    }
                }
            }
            return codeNumber
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun SetScreenMetrics(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLength(args, 2) {
            val (w, h) = it
            undefined { compatShell(scriptRuntime).SetScreenMetrics(coerceIntNumber(w), coerceIntNumber(h)) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun Swipe(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 4..5) {
            val (x1, y1, x2, y2, duration) = it
            undefined {
                val px1 = coerceIntNumber(x1)
                val py1 = coerceIntNumber(y1)
                val px2 = coerceIntNumber(x2)
                val py2 = coerceIntNumber(y2)
                if (scriptRuntime.displayRoute.isVirtual) {
                    val ms = if (duration.isJsNullish()) 300 else coerceIntNumber(duration)
                    scriptRuntime.displayRoute.swipe(px1, py1, px2, py2, ms.coerceAtLeast(1))
                } else when {
                    duration.isJsNullish() -> compatShell(scriptRuntime).Swipe(px1, py1, px2, py2)
                    else -> compatShell(scriptRuntime).Swipe(px1, py1, px2, py2, coerceIntNumber(duration))
                }
            }
        }

        /**
         * Reads `(command, arguments?, withRoot?)`. `root` and `adb` given neither as arguments nor as `withRoot` fall
         * back to [defaultRoot] and [defaultAdb] (`setDefaultOptions`), then to false.
         * zh-CN: 读取 `(command, arguments?, withRoot?)`. 既未在 arguments 中也未经 `withRoot` 给出的 `root` 与 `adb` 依次采用 [defaultRoot] 与
         * [defaultAdb] (`setDefaultOptions`), 再退回 false.
         */
        @JvmOverloads
        fun getCommandData(argList: Array<Any?>, defaultRoot: Boolean? = null, defaultAdb: Boolean? = null): CommandData {
            val (arg0, arg1, arg2) = argList
            val cmdBody = when (arg0) {
                is String -> arg0
                is NativeArray -> arg0.joinToString("\n") { coerceString(it) }
                else -> throw IllegalStateException(
                    "Argument cmd must be a string or JavaScript Array instead of ${arg0.jsBrief()}"
                )
            }
            val explicitRoot = arg2.takeUnless { it.isJsNullish() }?.let { coerceBoolean(it) }
            val argsData: CommandArgumentsData = when (argList.size) {
                3 -> when (arg1) {
                    is String -> parseArguments(arg1, explicitRoot)
                    is NativeObject -> parseArguments(arg1, explicitRoot)
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
            val niceIsRoot = if (argsData.withRoot ?: defaultRoot ?: false) 1 else 0
            val commandData = CommandData(niceCommand, niceIsRoot, argsData.withAdb ?: defaultAdb ?: false)
            return commandData
        }

        private fun parseArguments(o: Map<*, *>, withRoot: Boolean? = null): CommandArgumentsData {
            val command = StringBuilder()

            var tmpWithRoot: Boolean? = null
            var tmpWithExit = false
            var tmpWithAdb: Boolean? = null

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
                    // Auto.js Pro's option: run through Shizuku. An argument named "adb" can still be written as "--adb".
                    // zh-CN: Auto.js Pro 的选项: 经 Shizuku 执行. 名为 "adb" 的参数仍可写作 "--adb".
                    "adb" -> {
                        tmpWithAdb = "$value" != "false"
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
            val niceWithRoot = when {
                tmpWithRoot == null && withRoot == null -> null
                else -> tmpWithRoot == true || withRoot == true
            }
            return CommandArgumentsData(command.toString().trim(), niceWithRoot, tmpWithExit, tmpWithAdb)
        }

        private fun parseArguments(s: String, withRoot: Boolean? = null): CommandArgumentsData {
            return s.split(Regex("\\s*\\|\\s*")).map {
                if (it.contains("=")) {
                    val (key, value) = it.split(Regex("\\s*=\\s*"))
                    key to value
                } else {
                    it to true
                }
            }.let { parseArguments(it.toMap(), withRoot) }
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

        /** `withRoot` and `withAdb` are null when not given. zh-CN: 未给出时 `withRoot` 与 `withAdb` 为 null. */
        data class CommandArgumentsData(val arguments: String = "", val withRoot: Boolean? = null, val withExit: Boolean = false, val withAdb: Boolean? = null)

        data class CommandData(val command: String, val withRoot: Int = 0, val withAdb: Boolean = false)
    }
}
