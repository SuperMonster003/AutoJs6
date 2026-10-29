package org.autojs.autojs.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.installer.InstallerError
import org.autojs.autojs.core.plugin.installer.InstallerJson
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.autojs.core.plugin.installer.InstallerSessionListener
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.installer.InstallerScriptArguments
import org.autojs.autojs.runtime.api.installer.InstallerScriptOptions
import org.autojs.autojs.runtime.api.installer.InstallerScriptValues
import org.autojs.autojs.runtime.api.installer.InstallerService
import org.autojs.autojs.runtime.api.installer.InstallerOperation
import org.autojs.autojs.runtime.api.modules.memory.Memory
import org.autojs.autojs.runtime.api.modules.settings.Settings as ScriptSettings
import org.autojs.autojs.runtime.api.modules.settings.SettingsScriptExport
import org.autojs.autojs.runtime.api.modules.sysprops.SystemProperties
import org.autojs.autojs.runtime.memory.MemoryDiagnostics
import org.autojs.autojs.runtime.memory.MemoryDiagnosticsCore
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.NotificationUtils
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Host side of the Rhino system information family for the JS runtimes (api-coverage P3.1b): `autojs`, `settings`,
 * `sysprops`, `memory`, `installer`, the notice extras (`notice`) and `powerManager` (`power_manager`), JSON in and out,
 * with the same host calls the Rhino modules make. The Node bridge routes these modules here (one bridge per execution,
 * closed with it); the capabilities are in [capabilities] (Q133 (b): one capability per module, `.control` / `.write` /
 * `.dump` / `.mutate` for the calls that change state). Installer calls use an [InstallerService] owned by this bridge.
 *
 * zh-CN: JS 运行时的 Rhino 系统信息族宿主侧 (api-coverage P3.1b): autojs, settings, sysprops, memory, installer, notice 附加函数与 powerManager,
 * JSON 进出, 宿主调用与 Rhino 模块相同. Node 桥把这些模块路由到这里 (每次执行一个, 随执行关闭); 所需能力见 [capabilities] (Q133 (b): 每个模块一个能力,
 * 改状态的调用另需 .control / .write / .dump / .mutate). installer 调用使用本对象持有的 [InstallerService].
 */
internal class RuntimeSystemBridge(
    private val context: Context,
    private val resolveFile: (String) -> File?,
) : Closeable {

    private val installerJob = SupervisorJob()
    private var installerService: InstallerService? = null
    private val installer: InstallerService
        get() = synchronized(this) { installerService ?: InstallerService(context, installerJob).also { installerService = it } }
    private val sessions = ConcurrentHashMap<String, InstallerSession>()

    fun invoke(module: String, method: String, args: JSONArray): Any? = when (module) {
        AUTOJS -> autojs(method, args)
        SETTINGS -> settings(method, args)
        SYSPROPS -> sysprops(method, args)
        MEMORY -> memory(method, args)
        INSTALLER -> installer(method, args)
        NOTICE -> notice(method, args)
        POWER_MANAGER -> powerManager(method, args)
        else -> throw IllegalArgumentException("Unknown system module: $module")
    }

    override fun close() {
        sessions.values.forEach { it.operation.cancelSilently() }
        sessions.clear()
        synchronized(this) { installerService?.close() }
        installerJob.cancel()
    }

    private fun autojs(method: String, args: JSONArray): Any? = when (method) {
        "isRootAvailable" -> RootUtils.isRootAvailable()
        "getRootMode" -> RootUtils.getRootMode().name
        "setRootMode" -> {
            RootUtils.setRootMode(RootUtils.RootMode.valueOf(args.getString(0)), args.optBoolean(1, false))
            null
        }
        "canModifySystemSettings" -> Settings.System.canWrite(context)
        "canWriteSecureSettings" -> context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        "canDisplayOverOtherApps" -> Settings.canDrawOverlays(context)
        "getLanguage" -> Language.getPrefLanguage().locale.let { locale ->
            JSONObject().put("tag", locale.toLanguageTag()).put("language", locale.language).put("country", locale.country)
                .put("variant", locale.variant).put("displayName", locale.displayName)
        }
        "screen" -> JSONObject().put("portrait", ScreenMetrics.isScreenPortrait).put("landscape", ScreenMetrics.isScreenLandscape)
            .put("rotation", ScreenMetrics.rotation).put("orientation", ScreenMetrics.orientation)
            .put("width", ScreenMetrics.deviceScreenWidth).put("height", ScreenMetrics.deviceScreenHeight)
        "themeColor" -> ThemeColorManager.currentThemeColor.let { color ->
            JSONObject().put("colorPrimary", color.colorPrimary).put("colorPrimaryDark", color.colorPrimaryDark).put("colorAccent", color.colorAccent)
        }
        "resourceId" -> context.resources.getIdentifier(args.getString(1), args.getString(0), context.packageName)
        "restart", "exit" -> {
            val scripts = args.optJSONArray(0)?.let { array -> List(array.length()) { array.getString(it) } }
            if (method == "restart") IntentUtils.App.restart(context, scriptsAfterRestart = scripts) else IntentUtils.App.exit(context, scriptsAfterRestart = scripts)
            null
        }
        else -> unknown(AUTOJS, method)
    }

    private fun settings(method: String, args: JSONArray): Any? = when (method) {
        "isEnabled" -> ScriptSettings.isEnabledFor(ScriptSettings.resolvePreferenceKey(args.getString(0)))
        "setEnabled" -> {
            org.autojs.autojs.core.pref.store.SettingsStores.default.put(ScriptSettings.resolvePreferenceKey(args.getString(0)), args.getBoolean(1))
            null
        }
        "export" -> {
            val path = if (args.isNull(0)) null else args.getString(0)
            val target = SettingsScriptExport.pathOf(path)?.let { resolveFile(it)?.path ?: throw SecurityException("settings.export path is not a usable file path.") }
            wrap(ScriptSettings.exportPlain(target, if (args.isNull(1)) null else SettingsScriptExport.categoryIds(plain(args.get(1)))))
        }
        "categories" -> wrap(ScriptSettings.categoriesPlain())
        else -> unknown(SETTINGS, method)
    }

    private fun sysprops(method: String, args: JSONArray): Any? {
        val name = args.optString(0)
        return when (method) {
            "get" -> SystemProperties.get(name, if (args.isNull(1)) null else args.getString(1))
            "getInt" -> SystemProperties.getInt(name, if (args.isNull(1)) null else args.getInt(1))
            "getBoolean" -> SystemProperties.getBoolean(name, if (args.isNull(1)) null else args.getBoolean(1))
            "getAll" -> wrap(SystemProperties.getAll())
            else -> unknown(SYSPROPS, method)
        }
    }

    private fun memory(method: String, args: JSONArray): Any? = when (method) {
        "snapshot" -> wrap(Memory.snapshotOf(MemoryDiagnostics.snapshot()))
        "collect" -> {
            val timeoutMs = if (args.isNull(0)) MemoryDiagnosticsCore.COLLECT_TIMEOUT_MS else args.getLong(0)
            require(timeoutMs in 0..Memory.MAX_COLLECT_TIMEOUT_MS) { "Argument timeout $timeoutMs for memory.collect must be a number of milliseconds from 0 to ${Memory.MAX_COLLECT_TIMEOUT_MS}" }
            MemoryDiagnostics.awaitCleared(timeoutMs) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                MemoryDiagnostics.isCleared()
            }
            wrap(Memory.snapshotOf(MemoryDiagnostics.snapshot()))
        }
        "retained" -> wrap(MemoryDiagnostics.retained().map(Memory::retainedOf))
        "dumpHeap" -> {
            check(org.autojs.autojs6.BuildConfig.DEBUG || DeveloperKeys.MEMORY_SCRIPT_HEAP_DUMP.get()) {
                context.getString(org.autojs.autojs6.R.string.error_memory_script_heap_dump_not_allowed)
            }
            val defaultName = Memory.heapDumpFileName(System.currentTimeMillis())
            val text = if (args.isNull(0)) defaultName else args.getString(0)
            require(text.isNotBlank()) { "Argument path for memory.dumpHeap must be a non-empty string" }
            val resolved = resolveFile(text) ?: throw SecurityException("memory.dumpHeap path is not a usable file path.")
            val file = if (text.endsWith('/') || resolved.isDirectory) File(resolved, defaultName) else resolved
            MemoryDiagnostics.dumpHeap(file).absolutePath
        }
        else -> unknown(MEMORY, method)
    }

    /** Installer answers `{value}` or `{error}` (the fields of [InstallerError]), so the runtime builds its InstallerError. */
    private fun installer(method: String, args: JSONArray): Any? = try {
        JSONObject().put("value", wrap(installerValue(method, args)))
    } catch (failure: InstallerPluginException) {
        JSONObject().put("error", installerError(failure.error))
    }

    private fun installerValue(method: String, args: JSONArray): Any? = when (method) {
        "isAvailable" -> blocking { installer.isAvailable() }
        "status" -> blocking { installer.status() }
        "isDefault" -> blocking { installer.isDefault() }
        "users" -> blocking { InstallerScriptValues.users(installer.users()) }
        "inspect" -> blocking { InstallerScriptValues.json(installer.inspect(InstallerScriptArguments.inspect(plain(args.get(0)), cwd()))) }
        "install" -> {
            val plan = InstallerScriptArguments.sources(plain(args.get(0)), cwd())
            val options = InstallerScriptOptions.install(json(args, 1))
            blocking { InstallerScriptValues.completion(installer.install(plan, options), plan.isBatch) }
        }
        "uninstall" -> {
            val packageName = InstallerScriptArguments.packageName(plain(args.get(0)))
            val options = InstallerScriptOptions.uninstall(json(args, 1))
            blocking { InstallerScriptValues.uninstallResult(installer.uninstall(packageName, options)) }
        }
        "setDefault" -> {
            val enabled = InstallerScriptOptions.enabled(json(args, 0))
            val options = InstallerScriptOptions.setDefault(json(args, 1))
            blocking { installer.setDefault(enabled, options.authorizer, options.mode) }
        }
        "authorizerState" -> blocking { installer.authorizerState(if (args.isNull(0)) org.autojs.plugin.installer.api.InstallerContract.AUTHORIZER_AUTO else args.getString(0)) }
        "authorizerRequest" -> {
            val timeout = if (args.isNull(1)) org.autojs.plugin.installer.api.InstallerContract.DEFAULT_USER_ACTION_TIMEOUT_MILLIS else args.getLong(1)
            blocking { installer.requestAuthorizer(args.getString(0), timeout) }
        }
        "sessionOpen" -> {
            val plan = InstallerScriptArguments.sources(plain(args.get(0)), cwd())
            val options = InstallerScriptOptions.install(json(args, 1))
            val session = InstallerSession(plan.isBatch)
            session.operation = installer.newSession(plan, options, session)
            sessions[session.operation.id] = session
            mapOf("id" to session.operation.id, "state" to session.operation.state)
        }
        "sessionStart" -> session(args).also { it.operation.start() }.operation.state
        "sessionState" -> session(args).operation.state
        "sessionDrain" -> session(args).drain()
        "sessionCancel" -> session(args).operation.cancel()
        "sessionWait" -> {
            val session = session(args)
            val timeout = if (args.isNull(1)) null else args.getLong(1)
            blocking { InstallerScriptValues.completion(session.operation.await(timeout), session.isBatch) }
        }
        "sessionClose" -> sessions.remove(args.getString(0))?.let { it.operation.close(); true } ?: false
        else -> unknown(INSTALLER, method)
    }

    private fun session(args: JSONArray) = sessions[args.getString(0)] ?: throw IllegalArgumentException("No installer session with id ${args.optString(0)}")

    private fun notice(method: String, args: JSONArray): Any? = when (method) {
        "isEnabled" -> NotificationUtils.isEnabled()
        "ensureEnabled" -> {
            val enabled = NotificationUtils.isEnabled()
            if (!enabled) NotificationUtils.launchSettings()
            enabled
        }
        "launchSettings" -> {
            NotificationUtils.launchSettings()
            null
        }
        "channelCreate" -> {
            val id = args.getString(0)
            val options = args.optJSONObject(1) ?: JSONObject()
            NotificationUtils.createNotificationChannel(
                id,
                options.optStringOrNull("name"),
                options.optStringOrNull("description"),
                options.optIntOrNull("importance"),
                options.optBooleanOrNull("enableVibration"),
                options.optJSONArray("vibrationPattern")?.let { array -> LongArray(array.length()) { array.getLong(it) } },
                options.optBooleanOrNull("enableLights"),
                options.optIntOrNull("lightColor"),
                options.optIntOrNull("lockscreenVisibility"),
            )
            id
        }
        "channelContains" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager().notificationChannels.any { it.id == args.getString(0) }
        "channelGet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notificationManager().getNotificationChannel(args.getString(0))?.let(::channelJson) else null
        "channelGetAll" -> JSONArray(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notificationManager().notificationChannels.map(::channelJson) else emptyList())
        "channelRemove" -> {
            val id = args.getString(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager().notificationChannels.any { it.id == id }) {
                notificationManager().deleteNotificationChannel(id)
                true
            } else false
        }
        else -> unknown(NOTICE, method)
    }

    private fun powerManager(method: String, args: JSONArray): Any? {
        val packageName = args.optString(if (method == "requestIgnoreBatteryOptimizations") 1 else 0).takeUnless { it.isEmpty() || it == "null" } ?: context.packageName
        val power = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return when (method) {
            "isIgnoringBatteryOptimizations" -> power.isIgnoringBatteryOptimizations(packageName)
            "requestIgnoreBatteryOptimizations" -> {
                val force = args.optBoolean(0, false)
                if (force || !power.isIgnoringBatteryOptimizations(packageName)) {
                    @android.annotation.SuppressLint("BatteryLife")
                    val intent = android.content.Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(android.net.Uri.parse("package:$packageName")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                null
            }
            else -> unknown(POWER_MANAGER, method)
        }
    }

    private fun notificationManager() = context.getSystemService(android.app.NotificationManager::class.java)

    private fun channelJson(channel: android.app.NotificationChannel): JSONObject = JSONObject().apply {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@apply
        put("id", channel.id)
        put("name", channel.name?.toString())
        put("description", channel.description)
        put("importance", channel.importance)
        put("lockscreenVisibility", channel.lockscreenVisibility)
        put("lightColor", channel.lightColor)
        put("shouldShowLights", channel.shouldShowLights())
        put("shouldVibrate", channel.shouldVibrate())
        put("vibrationPattern", channel.vibrationPattern?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
        put("canBypassDnd", channel.canBypassDnd())
        put("canShowBadge", channel.canShowBadge())
        put("group", channel.group ?: JSONObject.NULL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) put("hasUserSetImportance", channel.hasUserSetImportance())
    }

    private fun cwd(): String? = resolveFile(".")?.path

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(INSTALLER_CALL_TIMEOUT_MS) { block() } }

    private fun json(args: JSONArray, index: Int): JsonElement? =
        if (index >= args.length() || args.isNull(index)) null else JsonParser.parseString(args.get(index).let { if (it is String) JSONObject.quote(it) else it.toString() })

    private fun unknown(module: String, method: String): Nothing = throw IllegalArgumentException("Unknown $module method: $method")

    private class InstallerSession(val isBatch: Boolean) : InstallerSessionListener {
        lateinit var operation: InstallerOperation
        private val events = ArrayList<JSONObject>()

        fun drain(): JSONArray = synchronized(events) { JSONArray(events.toList()).also { events.clear() } }

        private fun add(event: JSONObject) = synchronized(events) { events.add(event) }

        override fun onStage(stage: String, detail: InstallerJson.StageDetail) {
            add(JSONObject().put("type", "stage").put("stage", stage).put("detail", JSONObject().apply {
                detail.index?.let { put("index", it) }
                detail.packageName?.let { put("packageName", it) }
            }))
        }

        override fun onProgress(progress: Float, detail: InstallerJson.ProgressDetail) {
            add(JSONObject().put("type", "progress").put("progress", progress.toDouble()).put("detail", JSONObject()
                .put("bytesWritten", detail.bytesWritten ?: 0L).put("totalBytes", detail.totalBytes ?: 0L).apply { detail.index?.let { put("index", it) } }))
        }

        override fun onCompleted(completion: InstallerJson.Completion) {
            add(JSONObject().put("type", "complete").put("result", wrap(InstallerScriptValues.completion(completion, isBatch))))
        }

        override fun onFailed(error: InstallerError) {
            add(JSONObject().put("type", "error").put("error", installerError(error)))
        }
    }

    companion object {
        const val AUTOJS = "autojs"
        const val SETTINGS = "settings"
        const val SYSPROPS = "sysprops"
        const val MEMORY = "memory"
        const val INSTALLER = "installer"
        const val NOTICE = "notice"
        const val POWER_MANAGER = "power_manager"

        private const val INSTALLER_CALL_TIMEOUT_MS = 15 * 60 * 1000L

        val methodsByModule: Map<String, Set<String>> = mapOf(
            AUTOJS to setOf(
                "isRootAvailable", "getRootMode", "setRootMode", "canModifySystemSettings", "canWriteSecureSettings", "canDisplayOverOtherApps",
                "getLanguage", "screen", "themeColor", "resourceId", "restart", "exit",
            ),
            SETTINGS to setOf("isEnabled", "setEnabled", "export", "categories"),
            SYSPROPS to setOf("get", "getInt", "getBoolean", "getAll"),
            MEMORY to setOf("snapshot", "collect", "retained", "dumpHeap"),
            INSTALLER to setOf(
                "isAvailable", "status", "isDefault", "users", "inspect", "install", "uninstall", "setDefault", "authorizerState", "authorizerRequest",
                "sessionOpen", "sessionStart", "sessionState", "sessionDrain", "sessionCancel", "sessionWait", "sessionClose",
            ),
            NOTICE to setOf("isEnabled", "ensureEnabled", "launchSettings", "channelCreate", "channelContains", "channelGet", "channelGetAll", "channelRemove"),
            POWER_MANAGER to setOf("isIgnoringBatteryOptimizations", "requestIgnoreBatteryOptimizations"),
        )

        /** Q133 (b): the module capability, plus the state-changing one where the call changes AutoJs6 or Android state. */
        fun capabilities(module: String, method: String): List<String> = when (module) {
            AUTOJS -> if (method in setOf("setRootMode", "restart", "exit")) listOf(NodeBridgePermissionManifest.AUTOJS, NodeBridgePermissionManifest.AUTOJS_CONTROL)
            else listOf(NodeBridgePermissionManifest.AUTOJS)
            SETTINGS -> when (method) {
                "setEnabled" -> listOf(NodeBridgePermissionManifest.SETTINGS, NodeBridgePermissionManifest.SETTINGS_WRITE)
                "export" -> listOf(NodeBridgePermissionManifest.SETTINGS, NodeBridgePermissionManifest.FILES_WRITE)
                else -> listOf(NodeBridgePermissionManifest.SETTINGS)
            }
            SYSPROPS -> listOf(NodeBridgePermissionManifest.SYSPROPS)
            MEMORY -> if (method == "dumpHeap") listOf(NodeBridgePermissionManifest.MEMORY, NodeBridgePermissionManifest.MEMORY_DUMP)
            else listOf(NodeBridgePermissionManifest.MEMORY)
            INSTALLER -> if (method in setOf("isAvailable", "status", "isDefault", "users", "inspect", "authorizerState")) listOf(NodeBridgePermissionManifest.INSTALLER)
            else listOf(NodeBridgePermissionManifest.INSTALLER, NodeBridgePermissionManifest.INSTALLER_MUTATE)
            NOTICE -> if (method in setOf("ensureEnabled", "launchSettings")) listOf(NodeBridgePermissionManifest.NOTIFICATIONS, NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS)
            else listOf(NodeBridgePermissionManifest.NOTIFICATIONS)
            POWER_MANAGER -> listOf(NodeBridgePermissionManifest.DEVICE, NodeBridgePermissionManifest.DEVICE_POWER)
            else -> emptyList()
        }

        private fun installerError(error: InstallerError): JSONObject = JSONObject().put("code", error.code).put("message", error.message)
            .put("retryable", error.retryable).put("status", error.status ?: JSONObject.NULL).put("systemMessage", error.systemMessage ?: JSONObject.NULL)
            .put("packageName", error.packageName ?: JSONObject.NULL).put("authorizer", error.authorizer ?: JSONObject.NULL)

        /** org.json values of plain Kotlin maps and lists (the installer result documents). */
        fun wrap(value: Any?): Any? = when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().also { o -> value.forEach { (k, v) -> o.put(k.toString(), wrap(v)) } }
            is Collection<*> -> JSONArray().also { a -> value.forEach { a.put(wrap(it)) } }
            is Array<*> -> JSONArray().also { a -> value.forEach { a.put(wrap(it)) } }
            else -> value
        }

        /** Plain Kotlin values (String, Number, Boolean, Map, List, null) of an org.json value. */
        fun plain(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
            is JSONArray -> List(value.length()) { plain(value.get(it)) }
            else -> value
        }

        private fun JSONObject.optStringOrNull(key: String) = if (isNull(key)) null else getString(key)
        private fun JSONObject.optIntOrNull(key: String) = if (isNull(key)) null else getInt(key)
        private fun JSONObject.optBooleanOrNull(key: String) = if (isNull(key)) null else getBoolean(key)
    }
}
