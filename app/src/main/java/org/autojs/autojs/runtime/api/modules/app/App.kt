package org.autojs.autojs.runtime.api.modules.app

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.PackageManager.PackageInfoFlags
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.net.toUri
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.external.receiver.BaseBroadcastReceiver
import org.autojs.autojs.ipc.LayoutInspectEventBus
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.deleteProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.AppUtils.Companion.ActivityShortForm
import org.autojs.autojs.runtime.api.AppUtils.Companion.BroadcastShortForm
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.modules.app.App.Companion.ShellAction.Companion.toFallbackShellAction
import org.autojs.autojs.runtime.api.modules.app.App.Companion.ShellAction.Companion.toRootShellAction
import org.autojs.autojs.runtime.api.modules.app.App.Companion.ShellAction.Companion.toShizukuShellAction
import org.autojs.autojs.runtime.api.modules.shell.Shell
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.getCurrentTopCallScopeOrFallback
import org.autojs.autojs.util.RhinoUtils.getDeclarationScope
import org.autojs.autojs.util.RhinoUtils.js_object_assign
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs6.BuildConfig
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import java.net.URI
import org.autojs.autojs.util.App as PresetApp

@Suppress("unused", "UNUSED_PARAMETER")
class App(scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    companion object : ArgumentGuards() {

        private const val PROTOCOL_FILE = "file://"

        private val presetPackageNames by lazy {
            PresetApp.entries.associate {
                it.alias to it.packageName
            }
        }

        private val rexWebSiteWithoutProtocol = """^(www.)?[a-z0-9]+(\.[a-z]{2,}){1,3}(#?/?[a-zA-Z0-9#]+)*/?(\?[a-zA-Z0-9-_]+=[a-zA-Z0-9-%]+&?)?$""".toRegex()

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun intent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Intent = ensureArgumentsOnlyOne(args) {
            intentRhinoWithRuntime(scriptRuntime, it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun intentRhinoWithRuntime(scriptRuntime: ScriptRuntime, o: Any?): Intent = when (o) {
            is Intent -> o
            is NativeObject -> Intent().configure(scriptRuntime, o)
            else -> throw IllegalArgumentException("Argument \"o\" ${o.jsBrief()} for app.intent must be either an Intent or a JavaScript Object")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        @Suppress("HttpUrlsUsage")
        fun startActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (obj, options) = argList
            when (obj) {
                is String -> when {
                    !options.isJsNullish() -> throw IllegalArgumentException("Method app.startActivity can only have one argument when argument[0] is String")
                    obj.contains("://") -> openUrlOnCurrentDisplay(scriptRuntime, obj)
                    rexWebSiteWithoutProtocol.matches(obj) -> openUrlOnCurrentDisplay(scriptRuntime, "http://$obj")
                    else -> runCatching {
                        startActivityWithGlobalContext(Intent(GlobalAppContext.get(), ActivityShortForm.valueOf(obj.uppercase()).classType))
                    }.getOrElse {
                        when (it) {
                            is WrappedIllegalArgumentException -> throw WrappedIllegalArgumentException("Activity short form $obj cannot be found")
                            else -> throw it
                        }
                    }
                }
                is Intent -> when {
                    options.isJsNullish() -> startActivityWithGlobalContext(obj)
                    options is NativeObject -> obj.configure(scriptRuntime, options).let { configuredIntent ->
                        when {
                            checkDualProperty(options) -> startDualActivity(scriptRuntime, args)
                            checkShizukuProperty(options) -> {
                                val tmpIntent = configuredIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                WrappedShizuku.execCommand("am start ${intentToShellRhino(scriptRuntime.topLevelScope, tmpIntent)}").throwIfError()
                            }
                            checkRootProperty(options) -> {
                                val tmpIntent = configuredIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                Shell.execCommand(scriptRuntime, arrayOf<Any>("am start ${intentToShellRhino(scriptRuntime.topLevelScope, tmpIntent)}", /* withRoot = */ true)).throwIfError()
                            }
                            else -> startActivityWithGlobalContext(configuredIntent)
                        }
                    }
                    else -> throw IllegalArgumentException("Argument[1] for app.startActivity must be a JavaScript Object when argument[0] is Intent")
                }
                is Uri, is URI -> when {
                    !options.isJsNullish() -> throw IllegalArgumentException("Method app.startActivity can only have one argument when argument[0] is URI")
                    else -> openUrlOnCurrentDisplay(scriptRuntime, obj.toString())
                }
                is NativeObject -> {
                    val opt = when {
                        options.isJsNullish() -> obj
                        options is NativeObject -> js_object_assign(js_object_assign(newNativeObject(scriptRuntime.topLevelScope), obj), options) as NativeObject
                        else -> throw IllegalArgumentException("Argument[1] for app.startActivity must be a JavaScript Object when argument[0] is JavaScript Object")
                    }
                    when {
                        checkDualProperty(opt) -> startDualActivity(scriptRuntime, args)
                        checkShizukuProperty(opt) -> {
                            val tmpIntent = intentRhinoWithRuntime(scriptRuntime, opt).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            val intentCommand = intentToShellRhino(scriptRuntime.topLevelScope, tmpIntent)
                            WrappedShizuku.execCommand("am start $intentCommand").throwIfError()
                        }
                        checkRootProperty(opt) -> {
                            val tmpIntent = intentRhinoWithRuntime(scriptRuntime, opt).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            Shell.execCommand(scriptRuntime, arrayOf<Any>("am start ${intentToShellRhino(scriptRuntime.topLevelScope, tmpIntent)}", /* withRoot = */ true)).throwIfError()
                        }
                        else -> startActivityWithGlobalContext(scriptRuntime, opt)
                    }
                }
                else -> throw IllegalArgumentException("Unknown type of argument[0]: ${obj.jsBrief()}; method: app.startActivity")
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        @Suppress("HttpUrlsUsage")
        fun startDualActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (obj, options) = argList
            when (obj) {
                is String -> when {
                    !options.isJsNullish() -> throw IllegalArgumentException("Method app.startDualActivity can only have one argument when argument[0] is String")
                    obj.contains("://") -> openDualUrlRhino(scriptRuntime, obj)
                    rexWebSiteWithoutProtocol.matches(obj) -> openDualUrlRhino(scriptRuntime, "http://$obj")
                    else -> runCatching {
                        startActivityForDualUser(scriptRuntime, Intent(GlobalAppContext.get(), ActivityShortForm.valueOf(obj.uppercase()).classType))
                    }.getOrElse {
                        when (it) {
                            is WrappedIllegalArgumentException -> throw WrappedIllegalArgumentException("Activity short form $obj cannot be found")
                            else -> throw it
                        }
                    }
                }
                is Intent -> when {
                    options.isJsNullish() -> startActivityForDualUser(scriptRuntime, obj)
                    options is NativeObject -> startActivityForDualUser(scriptRuntime, obj.configure(scriptRuntime, options))
                    else -> throw IllegalArgumentException("Argument[1] for app.startDualActivity must be a JavaScript Object when argument[0] is Intent")
                }
                is Uri, is URI -> when {
                    !options.isJsNullish() -> throw IllegalArgumentException("Method app.startDualActivity can only have one argument when argument[0] is URI")
                    else -> openDualUrlRhino(scriptRuntime, obj.toString())
                }
                is NativeObject -> {
                    val opt = when {
                        options.isJsNullish() -> obj
                        options is NativeObject -> js_object_assign(js_object_assign(newNativeObject(scriptRuntime.topLevelScope), obj), options) as NativeObject
                        else -> throw IllegalArgumentException("Argument[1] for app.startDualActivity must be a JavaScript Object when argument[0] is JavaScript Object")
                    }
                    startActivityForDualUser(scriptRuntime, opt)
                }
                else -> throw IllegalArgumentException("Unknown type of argument[0]: ${obj.jsBrief()}; method: app.startDualActivity")
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun intentToShell(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) {
            intentToShellRhino(scriptRuntime.topLevelScope, it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun intentToShellRhino(i: Any?): String {
            val scope = when (i) {
                is NativeObject -> getDeclarationScope(i)
                else -> RhinoUtils.withRhinoContext { cx -> cx.getCurrentTopCallScopeOrFallback() }
            }
            return intentToShellRhino(scope, i)
        }

        @JvmStatic
        fun intentToShellRhino(scope: VarScope, i: Any?): String {
            if (i is Intent) return intentToShellRhino(scope, newNativeObject(scope).apply {
                defineProp("packageName", i.component?.packageName ?: i.`package`)
                defineProp("className", i.component?.className)
                defineProp("extras", i.extras?.let { extras ->
                    @Suppress("DEPRECATION")
                    newNativeObject(scope).also { tmp ->
                        extras.keySet().forEach { key ->
                            tmp.put(key, tmp, extras[key])
                        }
                    }
                })
                defineProp("category", i.categories?.toList())
                defineProp("action", i.action)
                defineProp("flags", i.flags)
                defineProp("type", i.type)
                defineProp("data", i.data)
                defineProp("root", i.extras?.getBoolean("root") ?: false)
            })

            require(i is NativeObject) {
                "Argument ${i.jsBrief()} for intentToShell() must be a JavaScript Object or an Intent"
            }

            data class CommandBody(val body: String, val isQuote: Boolean = false)

            class CommandBuilder {

                var cmd = ""

                fun append(cmdOptions: String, cmdBody: CommandBody) {
                    val parsed = if (cmdBody.isQuote) quote(cmdBody.body) else cmdBody.body
                    cmd += " -$cmdOptions $parsed"
                }

                fun append(cmdOptions: String, cmdBodies: Array<CommandBody>) {
                    val parsed = cmdBodies.joinToString(" ") {
                        if (it.isQuote) quote(it.body) else it.body
                    }
                    cmd += " -$cmdOptions $parsed"
                }

                fun quote(str: String) = "'${str.replace("\'", "\\\'")}'"

                fun parseType(type: Any?) = when (type) {
                    is Boolean -> "z"
                    is Number -> when {
                        type.isJsNullish() -> throw WrappedIllegalArgumentException("Null or undefined values are not supported to convert into shell command")
                        type.toDouble().isInfinite() -> throw WrappedIllegalArgumentException("Infinite values are not supported to convert into shell command")
                        type.toDouble().isNaN() -> throw WrappedIllegalArgumentException("NaN values are not supported to convert into shell command")
                        type.toDouble() % 1 != 0.0 -> "f"
                        type.toDouble() in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() -> "i"
                        else -> "l"
                    }
                    else -> throw RuntimeException("Unknown type: $type")
                }
            }

            return object {

                private val mCommand = CommandBuilder()

                fun getResult(): String {
                    parseNames()
                    parseExtras()
                    parseCategory()
                    parseAction()
                    parseFlags()
                    parseType()
                    parseData()
                    return mCommand.cmd.trim()
                }

                private fun parseNames() {
                    if (i.prop("packageName").isJsNullish() || i.prop("className").isJsNullish()) return
                    val body = "${i.prop("packageName")}/${i.prop("className")}"
                    mCommand.append("n", CommandBody(body, isQuote = true))
                }

                private fun parseExtras() {
                    val extras = i.prop("extras").takeUnless { it.isJsNullish() } ?: return
                    if (extras !is NativeObject) throw RuntimeException("Property \"extras\" of an intent must be a JavaScript Object")
                    extras.entries.forEach { pair ->
                        val (key, value) = pair
                        key as? String ?: throw ShouldNeverHappenException()
                        when (value) {
                            is String -> mCommand.append(
                                "-es", arrayOf(
                                    CommandBody(key, isQuote = true),
                                    CommandBody(value, isQuote = true),
                                )
                            )
                            is Iterable<*> -> when (val first = value.firstOrNull() ?: throw Error("Empty array: $key")) {
                                is String -> mCommand.append(
                                    "-esa", arrayOf(
                                        CommandBody(key, isQuote = true),
                                        CommandBody(value.joinToString(",") { mCommand.quote(it as String) }),
                                    )
                                )
                                else -> mCommand.append(
                                    "-e${mCommand.parseType(first)}a", arrayOf(
                                        CommandBody(key, isQuote = true),
                                        CommandBody(Context.toString(value)),
                                    )
                                )
                            }
                            else -> mCommand.append(
                                "-e${mCommand.parseType(value)}", arrayOf(
                                    CommandBody(key, isQuote = true),
                                    CommandBody(Context.toString(value)),
                                )
                            )
                        }
                    }
                }

                private fun parseCategory() {
                    val category = i.prop("category").takeUnless { it.isJsNullish() } ?: return
                    when (category) {
                        is Iterable<*> -> category.forEach {
                            mCommand.append("c", CommandBody(Context.toString(it)))
                        }
                        else -> mCommand.append("c", CommandBody(Context.toString(category)))
                    }
                }

                private fun parseAction() {
                    val action = i.prop("action").takeUnless { it.isJsNullish() } ?: return
                    mCommand.append("a", CommandBody(parseIntentAction(action), isQuote = true))
                }

                private fun parseFlags() {
                    val flags = i.prop("flags").takeUnless { it.isJsNullish() } ?: return
                    mCommand.append("f", CommandBody(parseIntentFlags(flags).toString()))
                }

                private fun parseType() {
                    val type = i.prop("type").takeUnless { it.isJsNullish() } ?: return
                    mCommand.append("t", CommandBody(Context.toString(type)))
                }

                private fun parseData() {
                    val data = i.prop("data").takeUnless { it.isJsNullish() } ?: return
                    mCommand.append("d", CommandBody(Context.toString(data)))
                }
            }.getResult()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun startService(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { o ->
            when {
                o is NativeObject && checkRootProperty(o) -> {
                    @Suppress("SpellCheckingInspection")
                    Shell.execCommand(scriptRuntime, arrayOf<Any>(/* cmd = */ "am startservice ${intentToShellRhino(scriptRuntime.topLevelScope, o)}", /* withRoot = */ true))
                }
                else -> GlobalAppContext.get().startService(intentRhinoWithRuntime(scriptRuntime, o))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sendEmail(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { options ->
            val optionsMap = options as? NativeObject ?: newNativeObject(scriptRuntime.topLevelScope)

            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
            }

            optionsMap.prop("email").takeUnless { it.isJsNullish() }?.let {
                emailIntent.putExtra(Intent.EXTRA_EMAIL, toStringArray(it))
            }
            optionsMap.prop("cc").takeUnless { it.isJsNullish() }?.let {
                emailIntent.putExtra(Intent.EXTRA_CC, toStringArray(it))
            }
            optionsMap.prop("bcc").takeUnless { it.isJsNullish() }?.let {
                emailIntent.putExtra(Intent.EXTRA_BCC, toStringArray(it))
            }
            optionsMap.prop("subject").takeUnless { it.isJsNullish() }?.let {
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, Context.toString(it))
            }
            optionsMap.prop("text").takeUnless { it.isJsNullish() }?.let {
                emailIntent.putExtra(Intent.EXTRA_TEXT, Context.toString(it))
            }
            optionsMap.prop("attachment").takeUnless { it.isJsNullish() }?.let {
                emailIntent.putExtra(Intent.EXTRA_STREAM, parseUriRhinoWithRuntime(scriptRuntime, it))
            }

            startActivityWithGlobalContext(Intent.createChooser(emailIntent, "Send Email"))

            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sendBroadcast(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { o ->
            when (o) {
                is String -> {
                    CoroutineScope(Dispatchers.Main).launch {
                        when (BroadcastShortForm.valueOf(o.uppercase())) {
                            BroadcastShortForm.INSPECT_LAYOUT_BOUNDS -> LayoutInspectEventBus.showLayoutBounds()
                            BroadcastShortForm.LAYOUT_BOUNDS -> LayoutInspectEventBus.showLayoutBounds()
                            BroadcastShortForm.BOUNDS -> LayoutInspectEventBus.showLayoutBounds()
                            BroadcastShortForm.INSPECT_LAYOUT_HIERARCHY -> LayoutInspectEventBus.showLayoutHierarchy()
                            BroadcastShortForm.LAYOUT_HIERARCHY -> LayoutInspectEventBus.showLayoutHierarchy()
                            BroadcastShortForm.HIERARCHY -> LayoutInspectEventBus.showLayoutHierarchy()
                        }
                    }
                }
                else -> when {
                    o is NativeObject && checkRootProperty(o) -> {
                        Shell.execCommand(scriptRuntime, arrayOf<Any>("am broadcast ${intentToShellRhino(scriptRuntime.topLevelScope, o)}", /* withRoot = */ true))
                    }
                    else -> GlobalAppContext.get().sendBroadcast(intentRhinoWithRuntime(scriptRuntime, o))
                }
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sendLocalBroadcastSync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            sendLocalBroadcastSyncRhino(it)
        }

        @JvmStatic
        fun sendLocalBroadcastSyncRhino(intent: Any?) = undefined {
            require(intent is Intent?) { "Argument \"intent\" ${intent.jsBrief()} for app.sendLocalBroadcastSync must be a Intent" }
            sendLocalBroadcastSyncInternal(intent)
        }

        @JvmStatic
        fun sendLocalBroadcastSyncInternal(intent: Intent?) {

            // @Archived by SuperMonster003 on Sep 27, 2025.
            //  ! LocalBroadcastManager is deprecated.
            //  ! zh-CN: LocalBroadcastManager 已被弃用.
            //  # intent?.let { LocalBroadcastManager.getInstance(globalContext).sendBroadcastSync(it) }

            intent?.action?.let { action ->
                try {
                    // Get tasks synchronously in background thread, then execute in main thread.
                    // zh-CN: 在后台线程同步获取任务, 再回到主线程执行.
                    val mainHandler = Handler(Looper.getMainLooper())
                    Schedulers.io().scheduleDirect {
                        try {
                            val task = TimedTaskManager.getIntentTaskOfAction(action)
                                .firstOrError()
                                .blockingGet()
                            task?.let {
                                mainHandler.post {
                                    BaseBroadcastReceiver.runTask(GlobalAppContext.get(), intent, it)
                                }
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun parseUri(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Uri? = ensureArgumentsOnlyOne(args) { o ->
            parseUriRhinoWithRuntime(scriptRuntime, o)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun parseUriRhinoWithRuntime(scriptRuntime: ScriptRuntime, uri: Any?): Uri? = when (uri) {
            is String -> when {
                uri.startsWith(PROTOCOL_FILE) -> getUriForFileRhinoWithRuntime(scriptRuntime, uri)
                else -> uri.toUri()
            }
            is Uri -> uri
            is URI -> parseUriRhinoWithRuntime(scriptRuntime, uri.toString())
            else -> null
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun openUrl(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            openUrlOnCurrentDisplay(scriptRuntime, it)
        }

        /**
         * Resolves a URL on the display this runtime is bound to. Without this, `app.openUrl()`
         * (and the URL form of `app.startActivity`) always opens on the DEFAULT display and would
         * pull a virtual-screen task's app link onto the physical screen.
         *
         * zh-CN: 把 URL 打开到本运行时绑定的屏幕. 否则 `app.openUrl()` 永远落在默认屏,
         * 会把虚拟屏任务的 applink 拉到物理主屏上.
         */
        private fun openUrlOnCurrentDisplay(scriptRuntime: ScriptRuntime, url: Any?): Undefined = undefined {
            val target = coerceString(url)
            if (scriptRuntime.displayRoute.isVirtual) {
                scriptRuntime.displayRoute.launchUri(target)
            } else {
                openUrlInternal(target)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun openDualUrl(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            openDualUrlRhino(scriptRuntime, it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun openUrlRhino(url: Any?) = undefined { openUrlInternal(coerceString(url)) }

        @JvmStatic
        @RhinoFunctionBody
        fun openDualUrlRhino(scriptRuntime: ScriptRuntime, url: Any?) = undefined { openDualUrlInternal(scriptRuntime, coerceString(url)) }

        @Suppress("HttpUrlsUsage")
        fun openUrlInternal(url: String) {
            val prefix = "http://".takeUnless { url.contains("://") } ?: ""
            Intent(Intent.ACTION_VIEW)
                .setData((prefix + url).toUri())
                .startSafely(GlobalAppContext.get())
        }

        @Suppress("HttpUrlsUsage")
        private fun openDualUrlInternal(scriptRuntime: ScriptRuntime, url: String) {
            val prefix = "http://".takeUnless { url.contains("://") } ?: ""
            Intent(Intent.ACTION_VIEW)
                .setData((prefix + url).toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { startActivityForDualUser(scriptRuntime, it) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getUriForFile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Uri? = ensureArgumentsOnlyOne(args) { o ->
            o?.let { getUriForFileRhinoWithRuntime(scriptRuntime, Context.toString(it)) }
        }

        @JvmStatic
        @RhinoFunctionBody
        fun getUriForFileRhinoWithRuntime(scriptRuntime: ScriptRuntime, uri: String): Uri? {
            val path = scriptRuntime.files.path(
                when {
                    uri.startsWith(PROTOCOL_FILE) -> uri.substring(PROTOCOL_FILE.length)
                    else -> uri
                }
            ) ?: return null
            return IntentUtils.getUriOfFile(GlobalAppContext.get(), path, scriptRuntime.app.fileProviderAuthority)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getInstalledApps(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 1) {
            val flags = parsePackageManagerFlags(it.getOrNull(0), PackageManager.GET_META_DATA)
            val packageManager = GlobalAppContext.get().packageManager
            val applications = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    packageManager.getInstalledApplications(ApplicationInfoFlags.of(flags.toLong()))
                }
                else -> {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledApplications(flags)
                }
            }
            applications.map { info -> AppInfo(GlobalAppContext.get(), info) }.toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getInstalledPackages(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 1) {
            val flags = parsePackageManagerFlags(it.getOrNull(0), PackageManager.GET_META_DATA)
            val packageManager = GlobalAppContext.get().packageManager
            val packages = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    packageManager.getInstalledPackages(PackageInfoFlags.of(flags.toLong()))
                }
                else -> {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledPackages(flags)
                }
            }
            packages.onEach { info ->
                info.applicationInfo = info.applicationInfo?.let { applicationInfo ->
                    AppInfo(GlobalAppContext.get(), applicationInfo)
                }
            }.toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getApkInfo(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsLengthInRange(args, 1..2) {
            val path = scriptRuntime.files.path(coerceString(it[0])) ?: return@ensureArgumentsLengthInRange null
            val flags = parsePackageManagerFlags(it.getOrNull(1), PackageManager.GET_META_DATA)
            val packageManager = GlobalAppContext.get().packageManager
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    packageManager.getPackageArchiveInfo(path, PackageInfoFlags.of(flags.toLong()))
                }
                else -> {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageArchiveInfo(path, flags)
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getAppByAlias(scriptRuntime: ScriptRuntime, args: Array<out Any?>): PresetApp? = ensureArgumentsOnlyOne(args) { o ->
            getAppByAliasRhino(o)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launch(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean {
            return launchPackage(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchDual(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean {
            return launchDualPackage(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { o ->
            when {
                o.isJsNullish() -> false
                o is PresetApp -> launchOnCurrentDisplay(scriptRuntime, o.packageName)
                else -> launchOnCurrentDisplay(scriptRuntime, getAppByAliasRhino(o)?.packageName ?: Context.toString(o))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchDualPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { o ->
            runCatching {
                when {
                    o.isJsNullish() -> false
                    o is PresetApp -> launchDualPackageInternal(scriptRuntime, o.packageName)
                    else -> launchDualPackageInternal(scriptRuntime, getAppByAliasRhino(o)?.packageName ?: Context.toString(o))
                }
            }.getOrElse { false }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchApp(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { o ->
            when {
                o.isJsNullish() -> false
                o is PresetApp -> launchByAppName(scriptRuntime, o.getAppName())
                else -> getAppByAliasRhino(o)?.let {
                    // @Hint by SuperMonster003 on May 23, 2024.
                    //  ! Much more efficient to launch with package name.
                    //  ! zh-CN: 使用包名启动应用将会高效得多.
                    launchOnCurrentDisplay(scriptRuntime, it.packageName)
                } ?: launchByAppName(scriptRuntime, Context.toString(o))
            }
        }

        /** Launching an app must target the task's display, not the default one. */
        private fun launchOnCurrentDisplay(scriptRuntime: ScriptRuntime, packageName: String): Boolean =
            if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.launch(packageName)
            else scriptRuntime.app.launchPackage(packageName)

        private fun launchByAppName(scriptRuntime: ScriptRuntime, appName: String): Boolean {
            if (!scriptRuntime.displayRoute.isVirtual) return scriptRuntime.app.launchApp(appName)
            val packageName = scriptRuntime.app.getPackageName(appName) ?: return false
            return scriptRuntime.displayRoute.launch(packageName)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchDualApp(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { o ->
            runCatching {
                when {
                    o.isJsNullish() -> false
                    o is PresetApp -> launchDualAppInternal(scriptRuntime, o.getAppName())
                    else -> getAppByAliasRhino(o)?.let {
                        launchDualPackageInternal(scriptRuntime, it.packageName)
                    } ?: launchDualAppInternal(scriptRuntime, Context.toString(o))
                }
            }.getOrElse { false }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getPackageName(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String? = ensureArgumentsOnlyOne(args) { o ->
            when {
                o.isJsNullish() ->
                    null
                o is PresetApp ->
                    o.packageName
                else -> run {
                    val raw = Context.toString(o)
                    getAppByAliasRhino(o)?.let { return@run it.packageName }
                    scriptRuntime.app.getAppName(raw)?.let { return@run raw }
                    scriptRuntime.app.getPackageName(raw)?.let { return@run it }
                    return@run null
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getAppName(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String? = ensureArgumentsOnlyOne(args) { o ->
            when {
                o.isJsNullish() ->
                    null
                o is PresetApp ->
                    o.getAppName()
                else -> run {
                    val raw = Context.toString(o)
                    getAppByAliasRhino(o)?.let { return@run it.getAppName() }
                    scriptRuntime.app.getAppName(raw)?.let { return@run it }
                    scriptRuntime.app.getPackageName(raw)?.let { return@run raw }
                    return@run null
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchAppDetailsSettings(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { o ->
            when {
                o.isJsNullish() -> false
                o is PresetApp -> scriptRuntime.app.launchSettings(o.packageName)
                else -> getAppByAliasRhino(o)?.let {
                    scriptRuntime.app.launchSettings(it.packageName)
                } ?: scriptRuntime.app.launchSettings(Context.toString(o))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchDualAppDetailsSettings(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { o ->
            runCatching {
                when {
                    o.isJsNullish() -> false
                    o is PresetApp -> launchDualSettingsInternal(scriptRuntime, o.packageName)
                    else -> getAppByAliasRhino(o)?.let {
                        launchDualSettingsInternal(scriptRuntime, it.packageName)
                    } ?: launchDualSettingsInternal(scriptRuntime, Context.toString(o))
                }
            }.isSuccess
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun uninstall(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { o ->
            when {
                o.isJsNullish() -> Unit
                o is PresetApp -> scriptRuntime.app.uninstall(o.packageName)
                else -> getAppByAliasRhino(o)?.let {
                    scriptRuntime.app.uninstall(it.packageName)
                } ?: scriptRuntime.app.uninstall(Context.toString(o))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun uninstallDual(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { o ->
            when {
                o.isJsNullish() -> Unit
                o is PresetApp -> uninstallDualInternal(scriptRuntime, o.packageName)
                else -> getAppByAliasRhino(o)?.let {
                    uninstallDualInternal(scriptRuntime, it.packageName)
                } ?: uninstallDualInternal(scriptRuntime, Context.toString(o))
            }
            UNDEFINED
        }

        // TODO by SuperMonster003 on May 23, 2024.
        //  ! Deprecate and transfer this method to Files.kt .
        //  ! zh-CN: 弃用并将这个方法迁移到 Files.kt 文件中.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun viewFile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { path ->
            when {
                path.isJsNullish() -> false
                path !is String -> throw RuntimeException("Cannot view $path as it isn't a string")
                else -> {
                    val nicePath = scriptRuntime.files.nonNullPath(path)
                    if (!scriptRuntime.files.exists(nicePath)) {
                        throw Error("Cannot view $path as it doesn't exist")
                    }
                    if (!scriptRuntime.files.isFile(nicePath)) {
                        throw Error("Cannot view $path as it isn't a file")
                    }
                    scriptRuntime.app.viewFile(nicePath)
                }
            }
        }

        // TODO by SuperMonster003 on May 23, 2024.
        //  ! Deprecate and transfer to Files.kt .
        //  ! zh-CN: 弃用并将这个方法迁移到 Files.kt 文件中.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun editFile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { path ->
            when {
                path.isJsNullish() -> false
                path !is String -> throw RuntimeException("Cannot edit $path as it isn't a string")
                else -> {
                    val nicePath = scriptRuntime.files.nonNullPath(path)
                    if (!scriptRuntime.files.exists(nicePath)) {
                        throw Error("Cannot edit $path as it doesn't exist")
                    }
                    if (!scriptRuntime.files.isFile(nicePath)) {
                        throw Error("Cannot edit $path as it isn't a file")
                    }
                    scriptRuntime.app.editFile(nicePath)
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun kill(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 1) { argList ->
            val packageName = getPackageName(scriptRuntime, argList) ?: return@ensureArgumentsLength false
            with("am force-stop $packageName") {
                listOf(
                    toShizukuShellAction(),
                    toRootShellAction(scriptRuntime),
                    toFallbackShellAction(scriptRuntime),
                ).first { it.condition() }.execute().code == 0
            }
        }

        // @Hint by SuperMonster003 on Oct 30, 2024.
        //  ! Both kill and killDual will force stop all applications
        //  ! with the same package name simultaneously, and cannot stop them separately.
        //  ! zh-CN: kill 和 killDual 都会同时强制停止包名相同的所有应用, 无法分别停止.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun killDual(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 1) { argList ->
            val packageName = getPackageName(scriptRuntime, argList) ?: return@ensureArgumentsLength false
            val command = "am force-stop $packageName"
            runCatching { execCommandForDualUser(scriptRuntime, command) }.isSuccess
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isInstalled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { o ->
            val packageName = getPackageName(scriptRuntime, arrayOf(o)) ?: return@ensureArgumentsOnlyOne false
            scriptRuntime.app.isInstalled(packageName)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isDualInstalled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { o ->
            val packageName = getPackageName(scriptRuntime, arrayOf(o)) ?: return@ensureArgumentsOnlyOne false
            isDualInstalledInternal(scriptRuntime, packageName)
        }

        private fun isDualInstalledInternal(scriptRuntime: ScriptRuntime, packageName: String): Boolean {
            val command = "cmd package list packages %USER_ID% $packageName"
            return runCatching {
                execCommandForDualUser(scriptRuntime, command)?.let { result ->
                    result.code == 0 && result.result.contains("package:$packageName")
                } ?: false
            }.getOrElse { false }
        }

        private fun parseClassName(o: NativeObject): String {
            return Context.toString(o.prop("className")).replace(Regex("@\\{(\\w+?)\\}|@(\\w+)")) {
                val key: String = it.groups[1]?.value ?: it.groups[2]?.value ?: throw ShouldNeverHappenException()
                Context.toString(o.prop(key) ?: throw RuntimeException("Intent object doesn't have a key named $key"))
            }
        }

        private fun parseIntentUrl(scriptRuntime: ScriptRuntime, o: NativeObject): Any? {
            val url = o.prop("url")
            if (url.isJsNullish()) return null
            return if (url is NativeObject) parseUrlObject(scriptRuntime, url) else url
        }

        private fun parseIntentFlags(flags: Any?): Int {

            fun parse(o: Any?): Int =
                when (o) {
                    is String -> {
                        if (o.contains("|")) {
                            o.split(Regex("\\s*\\|\\s*")).map(::parse).fold(0) { a, b -> a or b }
                        } else {
                            Intent::class.java.fields.firstOrNull {
                                val sanitizedInputRegex = Regex.escape(o.replace(Regex("\\W"), "_"))
                                it.name.matches(Regex("(flag_)?(activity_)?$sanitizedInputRegex(_task)?", RegexOption.IGNORE_CASE))
                            }?.getInt(null)
                        }
                    }
                    is Number -> o.toInt()
                    else -> null
                } ?: throw WrappedIllegalArgumentException("Invalid flags: $o")

            var result = 0
            when (flags) {
                is List<*> -> flags.forEach { flag -> result = result or parse(flag) }
                else -> result = parse(flags)
            }
            return result
        }

        private fun parseIntentAction(action: Any?): String = when {
            action is String && "." !in action -> {
                "android.intent.action.$action"
            }
            else -> coerceString(action)
        }

        private fun parseUrlObject(scriptRuntime: ScriptRuntime, uri: NativeObject): String? {
            val src = uri.prop("src") as? String ?: return null
            val query = uri.prop("query") as? NativeObject ?: return src
            val exclude = uri.prop("exclude")
            val separator = if ("?" in src) "&" else "?"
            return when {
                exclude is Iterable<*> -> src + separator + parse(scriptRuntime, query, exclude)
                exclude is String -> src + separator + parse(scriptRuntime, query, exclude)
                exclude.isJsNullish() -> src + separator + parse(scriptRuntime, query, null)
                else -> throw WrappedIllegalArgumentException("exclude of uri for parseUrlObject() must be a string or an array")
            }
        }

        private fun parse(scriptRuntime: ScriptRuntime, query: NativeObject, exclude: String?): String {
            return parse(scriptRuntime, query, newNativeArray(scriptRuntime.topLevelScope, arrayOf(exclude)))
        }

        private fun parse(scriptRuntime: ScriptRuntime, query: NativeObject, exclude: Iterable<*>): String {
            return query.keys.joinToString("&") { key ->
                var value = query[key]
                if (value is NativeObject) {
                    value = if (key == "url") parseUrlObject(scriptRuntime, value) else parse(scriptRuntime, value, exclude)
                    /* For Alipay H5 compatibility. */
                    value = (if (key == "__webview_options__") "&" else "") + value
                }
                if (key !in exclude) {
                    value = if (value != null) RhinoUtils.encodeURI(scriptRuntime, Context.toString(value)) else ""
                }
                "$key=$value"
            }
        }

        private fun toStringArray(o: Any): Array<String> = when (o) {
            is List<*> -> Array(o.size) { i -> Context.toString(o[i]) }
            else -> arrayOf(o).let { Array(it.size) { i -> Context.toString(it[i]) } }
        }

        private fun startActivityWithGlobalContext(scriptRuntime: ScriptRuntime, o: Any?) {
            startActivityWithGlobalContext(intentRhinoWithRuntime(scriptRuntime, o))
        }

        private fun startActivityWithGlobalContext(o: Intent) {
            o.startSafely(GlobalAppContext.get())
        }

        private fun startActivityForDualUser(scriptRuntime: ScriptRuntime, o: Any?) {
            val intent = intentRhinoWithRuntime(scriptRuntime, o).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            val command = "am start ${intentToShellRhino(scriptRuntime.topLevelScope, intent)}"
            when (o) {
                is NativeObject -> execCommandForDualUser(scriptRuntime, command, o)
                else -> execCommandForDualUser(scriptRuntime, command)
            }
        }

        private fun execCommandForDualUser(
            scriptRuntime: ScriptRuntime,
            command: String,
            options: NativeObject? = null,
        ): AbstractShell.Result? {
            val userManager = GlobalAppContext.get().getSystemService(Class.forName("android.os.UserManager"))

            val getUserProfilesMethod = userManager.javaClass.getDeclaredMethod("getUserProfiles")
            val userProfiles = getUserProfilesMethod.invoke(userManager) as List<*>

            val processClass = Class.forName("android.os.Process")
            val myUserHandleMethod = processClass.getDeclaredMethod("myUserHandle")
            val processUserHandle = myUserHandleMethod.invoke(null)

            val userHandleClass = Class.forName("android.os.UserHandle")
            val getIdentifierMethod = userHandleClass.getDeclaredMethod("getIdentifier")
            val currentUserId = getIdentifierMethod.invoke(processUserHandle) as Int

            var lastResult: AbstractShell.Result? = null

            @Suppress("LocalVariableName")
            for (userHandle in userProfiles) {
                val uid = getIdentifierMethod.invoke(userHandle) as Int
                if (uid == currentUserId) continue

                val cmd = when {
                    command.contains("%USER_ID%") -> command.replace("%USER_ID%", "--user $uid")
                    Regex("""\s--user\s+\d+""").containsMatchIn(command) -> command
                    else -> "$command --user $uid"
                }

                val shizuku = cmd.toShizukuShellAction()
                val root = cmd.toRootShellAction(scriptRuntime)
                val fallback = cmd.toFallbackShellAction(scriptRuntime)

                val SRF = listOf(shizuku, root, fallback)
                val RSF = listOf(root, shizuku, fallback)

                val actions = when (options) {
                    is NativeObject -> {
                        val preferShizuku = options.prop("shizuku").takeUnless { it.isJsNullish() }?.let { coerceBoolean(it) }
                        val preferRoot = options.prop("root").takeUnless { it.isJsNullish() }?.let { coerceBoolean(it) }
                        when (preferShizuku) {
                            null -> if (preferRoot == true) RSF else SRF
                            true -> SRF
                            else -> if (preferRoot == false) SRF else RSF
                        }
                    }
                    else -> SRF
                }

                if (actions.first { it.condition() }.execute().also { lastResult = it }.code == 0) {
                    break
                }
            }
            return lastResult?.apply { throwIfError() }
        }

        private fun getAppByAliasRhino(o: Any?): PresetApp? {
            return o?.let { PresetApp.getAppByAlias(Context.toString(it)) }
        }

        private fun parsePackageManagerFlags(options: Any?, defaultFlags: Int): Int {
            if (options.isJsNullish()) return defaultFlags
            require(options is NativeObject) {
                "Package manager options ${options.jsBrief()} must be a JavaScript Object"
            }
            return listOf("get", "match").fold(defaultFlags) { flags, type ->
                val names = options.prop(type)
                if (names.isJsNullish()) {
                    flags
                } else {
                    require(names is NativeArray) {
                        "Package manager option \"$type\" ${names.jsBrief()} must be a JavaScript Array"
                    }
                    names.fold(flags) { result, name ->
                        val fieldName = "${type}_${coerceString(name)}".uppercase()
                        val value = runCatching {
                            PackageManager::class.java.getField(fieldName).getInt(null)
                        }.getOrElse {
                            throw WrappedIllegalArgumentException("Unknown PackageManager flag \"$fieldName\"")
                        }
                        result or value
                    }
                }
            }
        }

        private fun launchDualPackageInternal(scriptRuntime: ScriptRuntime, o: String): Boolean {
            val nicePackageName = getAppByAlias(scriptRuntime, arrayOf(o))?.packageName ?: (/* packageName = */ o)
            val intent = GlobalAppContext.get().packageManager.getLaunchIntentForPackage(nicePackageName) ?: return false
            startActivityForDualUser(scriptRuntime, intent)
            return true
        }

        private fun launchDualAppInternal(scriptRuntime: ScriptRuntime, packageName: String): Boolean {
            return getPackageName(scriptRuntime, arrayOf(packageName))?.let { launchDualPackageInternal(scriptRuntime, it) } ?: false
        }

        private fun launchDualSettingsInternal(scriptRuntime: ScriptRuntime, packageName: String) {
            startActivityForDualUser(scriptRuntime, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            })
        }

        private fun uninstallDualInternal(scriptRuntime: ScriptRuntime, packageName: String) {
            startActivityForDualUser(scriptRuntime, Intent(Intent.ACTION_DELETE).apply {
                data = "package:$packageName".toUri()
            })
        }

        private fun checkDualProperty(o: NativeObject) = o.inquire("dual", ::coerceBoolean, false)

        private fun checkShizukuProperty(o: NativeObject) = o.inquire("shizuku", ::coerceBoolean, false) && WrappedShizuku.isOperational()

        private fun checkRootProperty(o: NativeObject) = o.inquire("root", ::coerceBoolean, false) && RootUtils.isRootAvailable()

        private fun Intent.configure(scriptRuntime: ScriptRuntime, o: NativeObject): Intent {
            val intent = this

            val urlRaw = o.prop("url")
            val packageRaw = o.prop("package")
            var packageNameRaw = o.prop("packageName")
            val classNameRaw = o.prop("className")
            val extrasRaw = o.prop("extras")
            val categoryRaw = o.prop("category")
            val actionRaw = o.prop("action")
            val flagsRaw = o.prop("flags")
            val typeRaw = o.prop("type")

            if (!urlRaw.isJsNullish()) {
                o.defineProp("data", parseIntentUrl(scriptRuntime, o))
            }
            val dataRaw = o.prop("data")

            if (!packageRaw.isJsNullish()) {
                if (packageNameRaw.isJsNullish()) {
                    o.defineProp("packageName", packageRaw).also {
                        packageNameRaw = packageRaw
                    }
                }
            }
            o.deleteProp("package")

            if (!packageNameRaw.isJsNullish()) {
                val packageName = when (val raw = packageNameRaw) {
                    is PresetApp -> raw.packageName
                    else -> when (val packageNameStr = coerceString(raw)) {
                        in presetPackageNames -> presetPackageNames[packageNameStr]!!
                        else -> packageNameStr
                    }
                }
                if (packageName != packageNameRaw) {
                    o.defineProp("packageName", packageName).also {
                        @Suppress("AssignedValueIsNeverRead")
                        packageNameRaw = packageName
                    }
                }
                if (!classNameRaw.isJsNullish()) {
                    intent.setClassName(packageName, parseClassName(o))
                } else {
                    // @Hint by SuperMonster003 on Jun 23, 2020.
                    //  ! the Intent can only match the components
                    //  ! in the given application package with setPackage().
                    //  ! Otherwise, if there's more than one app that can handle the intent,
                    //  ! the system presents the user with a dialog to pick which app to use.
                    //  ! zh-CN:
                    //  ! Intent 只能匹配通过 setPackage() 设置的给定应用包中的组件.
                    //  ! 否则, 如果有多个应用可以处理该 Intent,
                    //  ! 系统会向用户展示一个对话框, 让用户选择要使用哪个应用.
                    intent.setPackage(packageName)
                }
            }

            if (extrasRaw is NativeObject) {
                extrasRaw.entries.forEach { entry ->
                    val (key, value) = entry
                    RhinoUtils.putExtraForIntent(intent, key, value)
                }
            }

            if (!categoryRaw.isJsNullish()) {
                when (categoryRaw) {
                    is Iterable<*> -> categoryRaw.forEach { cat ->
                        intent.addCategory(Context.toString(cat))
                    }
                    else -> intent.addCategory(Context.toString(categoryRaw))
                }
            }

            if (!actionRaw.isJsNullish()) {
                intent.setAction(parseIntentAction(actionRaw))
            }

            if (!flagsRaw.isJsNullish()) {
                intent.setFlags(parseIntentFlags(flagsRaw))
            }

            if (!typeRaw.isJsNullish()) {
                if (!dataRaw.isJsNullish()) {
                    intent.setDataAndType(parseUriRhinoWithRuntime(scriptRuntime, dataRaw), Context.toString(typeRaw))
                } else {
                    intent.setType(Context.toString(typeRaw))
                }
            } else {
                if (!dataRaw.isJsNullish()) {
                    intent.setData(parseUriRhinoWithRuntime(scriptRuntime, dataRaw))
                }
            }

            return intent
        }

        private data class ShellAction(val cmd: String, val condition: () -> Boolean, private val action: (cmd: String) -> AbstractShell.Result) {

            fun execute() = action(cmd)

            companion object {
                fun String.toShizukuShellAction() = ShellAction(this, { WrappedShizuku.isOperational() }, { WrappedShizuku.execCommand(it) })
                fun String.toRootShellAction(scriptRuntime: ScriptRuntime) = ShellAction(this, { RootUtils.isRootAvailable() }, { Shell.execCommand(scriptRuntime, arrayOf<Any>(it, /* withRoot = */ true)) })
                fun String.toFallbackShellAction(scriptRuntime: ScriptRuntime) = ShellAction(this, { true }, { Shell.execCommand(scriptRuntime, arrayOf<Any>(it, /* withRoot = */ false)) })
            }

        }

    }

}
