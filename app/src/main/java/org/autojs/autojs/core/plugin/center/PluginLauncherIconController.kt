package org.autojs.autojs.core.plugin.center

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs6.R
import org.json.JSONObject
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean

/** Per-plugin launcher preference. Stored component overrides preserve the chosen icon variant. */
object PluginLauncherIconController {
    private const val TAG = "PluginLauncherIcons"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val initialized = AtomicBoolean(false)
    private val pending = mutableMapOf<String, Job>()

    data class Result(val success: Boolean, val message: String? = null)
    private data class Identity(val uid: Int, val firstInstallTime: Long)
    private data class Saved(val show: Boolean, val states: Map<String, Int>, val identity: Identity)
    private class PermissionRequired : IllegalStateException()

    /** The choice of [packageName] in the named store `plugin_launcher_icons` ([PluginCenterKeys.LAUNCHER_ICON], P3.1). */
    private fun choice(packageName: String) = PluginCenterKeys.LAUNCHER_ICON.key(packageName)

    @Suppress("DEPRECATION")
    private fun identity(context: Context, packageName: String): Identity {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        return Identity(requireNotNull(info.applicationInfo).uid, info.firstInstallTime)
    }

    private fun saved(context: Context, packageName: String): Saved? = runCatching {
        val json = choice(packageName).storedText()?.let(::JSONObject) ?: return@runCatching null
        val id = Identity(json.getInt("uid"), json.getLong("firstInstallTime"))
        if (id != identity(context, packageName)) return@runCatching null
        val states = json.getJSONObject("states")
        Saved(json.getBoolean("show"), states.keys().asSequence().associateWith { states.getInt(it) }, id)
    }.getOrNull()

    fun isShown(context: Context, packageName: String): Boolean = saved(context, packageName)?.show ?: true

    @Suppress("DEPRECATION")
    private fun components(context: Context, packageName: String, includeDisabled: Boolean): List<ComponentName> {
        val flags = if (includeDisabled) PackageManager.MATCH_DISABLED_COMPONENTS else 0
        return context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName), flags,
        ).mapNotNull { it.activityInfo?.let { info -> ComponentName(info.packageName, info.name) } }
            .filter { it.packageName == packageName }.distinct()
    }

    suspend fun setShown(context: Context, packageName: String, show: Boolean): Result =
        setShown(context, packageName, show) { command -> execute(context, command) }

    internal suspend fun setShown(
        context: Context,
        packageName: String,
        show: Boolean,
        onlyIfHidden: Boolean = false,
        executeCommand: suspend (String) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val id = identity(context, packageName)
                val previous = saved(context, packageName)
                if (onlyIfHidden && previous?.show != false) return@withLock Result(true)
                val all = components(context, packageName, includeDisabled = true)
                val visible = components(context, packageName, includeDisabled = false)
                val current = all.associate { it.className to context.packageManager.getComponentEnabledSetting(it) }
                val desired = PluginLauncherIconPolicy.plan(show, current, previous?.states, visible.isNotEmpty())
                val changes = desired.filter { (name, state) -> current[name] != state }
                // Preserve the last visible variant, including external changes made by the plugin.
                val restore = if (visible.isNotEmpty()) current else previous?.states ?: current.mapValues { PluginLauncherIconPolicy.DEFAULT }
                // Leaving Settings must not cancel the preference write after components changed.
                withContext(NonCancellable) {
                    if (changes.isNotEmpty()) executeCommand(command(context, packageName, changes))
                    check(desired.all { (name, state) ->
                        context.packageManager.getComponentEnabledSetting(ComponentName(packageName, name)) == state
                    }) { context.getString(R.string.error_plugin_launcher_icon_not_applied) }
                    val json = JSONObject().put("show", show).put("uid", id.uid).put("firstInstallTime", id.firstInstallTime)
                        .put("states", JSONObject(restore))
                    choice(packageName).setStoredText(json.toString(), commit = true)
                }
                Result(true)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Log.w(TAG, "Unable to change launcher visibility for $packageName", failure)
                Result(false, context.getString(
                    if (failure is PermissionRequired) R.string.error_plugin_launcher_icon_permission
                    else R.string.error_plugin_launcher_icon_not_applied,
                ))
            }
        }
    }

    internal fun command(context: Context, packageName: String, states: Map<String, Int>): String {
        val payload = Base64.encodeToString(JSONObject(states).toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        val quote = PluginLauncherIconPolicy::shellQuote
        // Android UID ranges allocate 100000 UIDs per user; never modify another user's icons.
        val userId = Process.myUid() / 100000
        return "CLASSPATH=${quote(context.applicationInfo.sourceDir)} /system/bin/app_process /system/bin " +
                "${PluginLauncherComponentCommand::class.java.name} ${quote(packageName)} $userId ${quote(payload)}"
    }

    private suspend fun execute(context: Context, command: String) {
        if (WrappedShizuku.isOperational()) {
            try {
                val result = WrappedShizuku.execCommand(context, command)
                if (result.code == 0 && result.result.contains("AUTOJS_LAUNCHER_OK")) return
                // ADB-mode Shizuku is subject to Android's shell restrictions on ordinary APKs.
                // Try the separately authorized Root channel before reporting permission failure.
                Log.w(TAG, "Shizuku could not change launcher components: ${result.error}")
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Log.w(TAG, "Shizuku launcher update failed; trying Root", failure)
            }
        }
        if (!RootUtils.isRootAvailable()) throw PermissionRequired()
        val output = executeRoot(command)
        if (!output.contains("AUTOJS_LAUNCHER_OK")) throw PermissionRequired()
    }

    /** Bound both the Root authorization wait and helper execution; drain both streams concurrently. */
    private suspend fun executeRoot(command: String): String = runInterruptible(Dispatchers.IO) {
        val process = ProcessShell.getRootProcess()
        fun reader(stream: java.io.InputStream): FutureTask<String> = FutureTask {
            stream.bufferedReader().use { input ->
                val result = StringBuilder()
                input.forEachLine { if (result.length < 16384) result.appendLine(it) }
                result.toString()
            }
        }.also { task -> Thread(task, "plugin-launcher-output").apply { isDaemon = true; start() } }
        val stdout = reader(process.inputStream)
        val stderr = reader(process.errorStream)
        try {
            process.outputStream.bufferedWriter().use { it.write("$command\nexit\n"); it.flush() }
            val deadline = SystemClock.elapsedRealtime() + 20_000L
            while (true) {
                val exit = runCatching { process.exitValue() }.getOrNull()
                if (exit != null) break
                check(SystemClock.elapsedRealtime() < deadline) { "Root authorization or launcher update timed out" }
                Thread.sleep(50)
            }
            stdout.get(1, java.util.concurrent.TimeUnit.SECONDS) + stderr.get(1, java.util.concurrent.TimeUnit.SECONDS)
        } finally {
            process.destroy()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            stdout.cancel(true)
            stderr.cancel(true)
        }
    }

    /** Reapply an explicitly hidden icon if a package update or its own icon picker re-enables it. */
    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val app = context.applicationContext
        fun schedule(packageName: String) {
            if (saved(app, packageName)?.show != false) return
            synchronized(pending) {
                if (pending[packageName]?.isActive == true) return
                val job = scope.launch {
                    try {
                        delay(750)
                        if (saved(app, packageName)?.show == false && components(app, packageName, false).isNotEmpty()) {
                            setShown(app, packageName, false, onlyIfHidden = true) { command -> execute(app, command) }
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Exception) {
                        Log.w(TAG, "Unable to reconcile launcher visibility for $packageName", failure)
                    }
                }
                pending[packageName] = job
                job.invokeOnCompletion {
                    synchronized(pending) { if (pending[packageName] === job) pending.remove(packageName) }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val name = intent.data?.schemeSpecificPart?.takeIf { it.isNotEmpty() } ?: return
                if (intent.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    choice(name).remove()
                } else schedule(name)
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { PluginCenterKeys.LAUNCHER_ICON.storedNames().forEach(::schedule) }
    }
}
