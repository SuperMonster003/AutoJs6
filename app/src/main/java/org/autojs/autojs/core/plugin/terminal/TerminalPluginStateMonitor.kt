package org.autojs.autojs.core.plugin.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.plugin.terminal.api.ITerminalCallback
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/**
 * Application-scoped cache of the terminal plugin's availability and session list for the drawer
 * (plugin roadmap D26). While the drawer is visible the monitor binds the plugin, registers an
 * [ITerminalCallback] and mirrors `onSessionsChanged`; once hidden the idle unbinding of
 * `AidlPluginHost` (30 s) releases the binding and the callback with it, and the next visibility
 * registers again. Package broadcasts and Plugin Center enablement changes trigger a fresh
 * inspection. Listeners are called on the main thread.
 */
object TerminalPluginStateMonitor {

    private const val TAG = "TerminalPluginState"

    enum class Cause {
        INITIALIZED,
        PLUGIN_STATE_CHANGED,
        PACKAGE_ADDED,
        PACKAGE_REMOVED,
        PACKAGE_REPLACED,
        PACKAGE_CHANGED,
        SESSIONS_CHANGED,
        SESSION_EXITED,
        VISIBILITY,
        REFRESHED,
    }

    data class State(
        val inspection: TerminalInspection?,
        val sessions: List<TerminalJson.Session>,
        val errorMessage: String? = null,
    ) {
        val status: TerminalPluginState
            get() = inspection?.state ?: TerminalPluginState.UNAVAILABLE

        val available: Boolean
            get() = status == TerminalPluginState.AVAILABLE

        val sessionCount: Int
            get() = if (available) sessions.size else 0

        val requiredHostVersion: Long
            get() = inspection?.facts?.requiredHostVersion ?: 0L

        companion object {
            val LOADING = State(null, emptyList())
        }
    }

    data class Change(val previous: State?, val current: State, val cause: Cause)

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(Change) -> Unit>()
    private val inspectionId = AtomicLong(0L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var state: State? = null

    @Volatile
    private var visible = false

    @Volatile
    private var callbackRegistered = false

    private val callback = object : ITerminalCallback.Stub() {
        override fun onSessionsChanged(count: Int, sessionsJson: String?) {
            val sessions = runCatching { TerminalJson.decodeSessions(sessionsJson) }
                .onFailure { Log.w(TAG, "onSessionsChanged carried an unreadable list: ${it.message}") }
                .getOrNull() ?: return
            publishSessions(sessions, Cause.SESSIONS_CHANGED)
        }

        override fun onSessionExited(sessionId: String?, exitCode: Int) {
            // The plugin follows with onSessionsChanged; refreshing covers plugins that only report the exit.
            refresh(Cause.SESSION_EXITED)
        }

        override fun onOutputOverflow(sessionId: String?, subscriptionId: String?, droppedBytes: Long) {
            // Output subscriptions belong to the script layer (P4.2); the drawer has none.
        }
    }

    private val pluginStateChangedListener: (String) -> Unit = { packageName ->
        if (packageName == ThreeShellTerminalOfficialPlugin.PACKAGE_NAME) refresh(Cause.PLUGIN_STATE_CHANGED)
    }

    private val packageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart != ThreeShellTerminalOfficialPlugin.PACKAGE_NAME) return
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (TerminalPluginPackageEventPolicy.shouldIgnore(intent.action, replacing)) return
            callbackRegistered = false
            refresh(
                when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED -> if (replacing) Cause.PACKAGE_REPLACED else Cause.PACKAGE_ADDED
                    Intent.ACTION_PACKAGE_REMOVED -> Cause.PACKAGE_REMOVED
                    Intent.ACTION_PACKAGE_REPLACED -> Cause.PACKAGE_REPLACED
                    Intent.ACTION_PACKAGE_CHANGED -> Cause.PACKAGE_CHANGED
                    else -> Cause.REFRESHED
                },
            )
        }
    }

    fun current(context: Context): State = state ?: initialize(context)

    fun addListener(context: Context, listener: (Change) -> Unit) {
        initialize(context)
        listeners += listener
    }

    fun removeListener(listener: (Change) -> Unit) {
        listeners -= listener
    }

    /**
     * Tells the monitor whether the drawer shows the terminal item. Becoming visible re-inspects
     * the package and, when the plugin is available, binds it to register the callback and read the
     * session list; becoming hidden unregisters the callback best-effort and lets the binding idle out.
     */
    fun setVisible(context: Context, visible: Boolean) {
        initialize(context)
        if (this.visible == visible) {
            if (visible) refresh(Cause.VISIBILITY)
            return
        }
        this.visible = visible
        if (visible) {
            refresh(Cause.VISIBILITY)
        } else if (callbackRegistered) {
            callbackRegistered = false
            val applicationContext = appContext ?: return
            scope.launch {
                runCatching { TerminalPluginHost.unregisterCallback(applicationContext, callback) }
                    .onFailure { Log.i(TAG, "unregisterCallback skipped: ${it.message}") }
            }
        }
    }

    fun refresh(cause: Cause = Cause.REFRESHED) {
        val context = appContext ?: return
        scheduleInspection(context, cause)
    }

    private fun initialize(context: Context): State {
        state?.let { return it }
        val applicationContext = context.applicationContext
        synchronized(lock) {
            state?.let { return it }
            appContext = applicationContext
            PluginStateChangeRegistry.addListener(pluginStateChangedListener)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Package broadcasts come from the system, which reaches a non-exported receiver.
                    applicationContext.registerReceiver(packageChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    applicationContext.registerReceiver(packageChangedReceiver, filter)
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to register the terminal plugin package monitor", error)
            }
            return State.LOADING.also { initial ->
                state = initial
                scheduleInspection(applicationContext, Cause.INITIALIZED)
            }
        }
    }

    private fun scheduleInspection(context: Context, cause: Cause) {
        val requestId = inspectionId.incrementAndGet()
        scope.launch {
            val inspected = inspect(context)
            if (inspectionId.get() != requestId) return@launch
            mainHandler.post {
                if (inspectionId.get() == requestId) publish(inspected, cause)
            }
        }
    }

    private suspend fun inspect(context: Context): State {
        val inspection = runCatching { TerminalPluginInspector.inspect(context) }.getOrElse { error ->
            Log.w(TAG, "Unable to inspect the terminal plugin", error)
            return State(TerminalInspection(TerminalPluginFacts(installed = true, bindable = false)), emptyList(), error.message)
        }
        if (!inspection.available || !visible) {
            callbackRegistered = false
            return State(inspection, emptyList())
        }
        return try {
            if (!callbackRegistered) {
                TerminalPluginHost.registerCallback(context, callback)
                callbackRegistered = true
            }
            State(inspection, TerminalPluginHost.listSessions(context))
        } catch (e: TerminalPluginException) {
            Log.w(TAG, "terminal plugin did not answer: ${e.message}")
            callbackRegistered = false
            State(TerminalInspection(inspection.facts.copy(bindable = false)), emptyList(), e.error.message)
        }
    }

    private fun publishSessions(sessions: List<TerminalJson.Session>, cause: Cause) {
        mainHandler.post {
            val previous = state ?: return@post
            if (!previous.available) return@post
            publish(previous.copy(sessions = sessions, errorMessage = null), cause)
        }
    }

    private fun publish(current: State, cause: Cause) {
        val previous = state
        if (previous == current) return
        state = current
        val change = Change(previous, current, cause)
        listeners.forEach { listener -> listener(change) }
    }
}

/** Replacement delivers REMOVED and ADDED with `EXTRA_REPLACING` before REPLACED; only the last one is worth an inspection. */
internal object TerminalPluginPackageEventPolicy {
    fun shouldIgnore(action: String?, replacing: Boolean): Boolean =
        replacing && (action == Intent.ACTION_PACKAGE_REMOVED || action == Intent.ACTION_PACKAGE_ADDED)
}
