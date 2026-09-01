package org.autojs.autojs.core.plugin.screencolorpicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerContract
import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerStates
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Application-scoped, asynchronously refreshed cache for the drawer tool. */
object ScreenColorPickerPluginStateMonitor {

    private const val TAG = "ScreenColorPickerState"
    private const val TRANSITION_POLL_INTERVAL_MILLIS = 350L
    private const val TRANSITION_POLL_TIMEOUT_MILLIS = 20_000L

    enum class Status {
        LOADING,
        AVAILABLE,
        NOT_INSTALLED,
        DISABLED,
        INELIGIBLE,
    }

    enum class Cause {
        INITIALIZED,
        PLUGIN_STATE_CHANGED,
        PACKAGE_ADDED,
        PACKAGE_REMOVED,
        PACKAGE_REPLACED,
        PACKAGE_CHANGED,
        PACKAGE_DATA_CLEARED,
        USER_ACTION,
        REFRESHED,
    }

    data class State(
        val status: Status,
        val enabledInPluginCenter: Boolean,
        val runtimeState: Int = ScreenColorPickerStates.INACTIVE,
        val errorMessage: String? = null,
    ) {
        val installed: Boolean
            get() = status != Status.LOADING && status != Status.NOT_INSTALLED

        val available: Boolean
            get() = status == Status.AVAILABLE

        val visibleInDrawer: Boolean
            get() = enabledInPluginCenter &&
                    (status == Status.AVAILABLE || status == Status.INELIGIBLE)

        val active: Boolean
            get() = available && ScreenColorPickerStates.isOn(runtimeState)

        val transitioning: Boolean
            get() = available && ScreenColorPickerStates.isTransitioning(runtimeState)
    }

    data class Change(
        val previous: State?,
        val current: State,
        val cause: Cause,
    )

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(Change) -> Unit>()
    private val inspectionId = AtomicLong(0L)
    private val inspectionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoJs6-ScreenColorPicker-State")
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var state: State? = null

    /** Main-thread only deadline for confirming a user-visible transitional state. */
    private var transitionPollDeadline = 0L

    private val transitionPollRunnable = Runnable {
        refresh(Cause.REFRESHED)
    }

    private val pluginStateChangedListener: (String) -> Unit = { packageName ->
        if (ScreenColorPickerPluginStateChangePolicy.shouldRefresh(packageName)) {
            refreshAfterPluginStateChange()
        }
    }

    private val packageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart != ScreenColorPickerContract.DEFAULT_PACKAGE_NAME) return
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (ScreenColorPickerPluginPackageEventPolicy.shouldIgnore(intent.action, replacing)) return
            val cause = when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> if (replacing) Cause.PACKAGE_REPLACED else Cause.PACKAGE_ADDED
                Intent.ACTION_PACKAGE_REMOVED -> Cause.PACKAGE_REMOVED
                Intent.ACTION_PACKAGE_REPLACED -> Cause.PACKAGE_REPLACED
                Intent.ACTION_PACKAGE_CHANGED -> Cause.PACKAGE_CHANGED
                Intent.ACTION_PACKAGE_DATA_CLEARED -> Cause.PACKAGE_DATA_CLEARED
                else -> Cause.REFRESHED
            }
            refresh(cause)
        }
    }

    fun current(context: Context): State = state ?: initialize(context)

    fun addListener(context: Context, listener: (Change) -> Unit) {
        initialize(context)
        listeners += listener
    }

    fun removeListener(listener: (Change) -> Unit) {
        listeners -= listener
        if (listeners.isEmpty()) {
            mainHandler.post(::scheduleNextTransitionPoll)
        }
    }

    fun refresh(cause: Cause = Cause.REFRESHED) {
        val context = appContext ?: return
        scheduleInspection(context, cause)
    }

    fun publishOptimisticRuntimeState(context: Context, runtimeState: Int) {
        ScreenColorPickerStates.requireKnown(runtimeState)
        initialize(context)
        // A result already read by an older inspection must not overwrite this user action.
        inspectionId.incrementAndGet()
        val publish = {
            val previous = state
            val shouldPublish = when (runtimeState) {
                ScreenColorPickerStates.STARTING ->
                    previous?.runtimeState == ScreenColorPickerStates.INACTIVE
                ScreenColorPickerStates.STOPPING ->
                    previous?.runtimeState == ScreenColorPickerStates.STARTING ||
                            previous?.runtimeState == ScreenColorPickerStates.ACTIVE
                else -> true
            }
            if (previous?.available == true && shouldPublish) {
                publish(
                    previous.copy(
                        status = Status.AVAILABLE,
                        runtimeState = runtimeState,
                        errorMessage = null,
                    ),
                    Cause.USER_ACTION,
                )
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) publish() else mainHandler.post(publish)
    }

    private fun refreshAfterPluginStateChange() {
        val context = appContext ?: return
        // Invalidate an inspection that may already have read the old enablement value.
        inspectionId.incrementAndGet()
        val updateAndInspect = {
            val previous = state
            if (previous != null) {
                val current = previous.withPluginCenterEnabled(isEnabledInPluginCenter(context))
                if (current != previous) {
                    publish(current, Cause.PLUGIN_STATE_CHANGED)
                }
            }
            scheduleInspection(context, Cause.PLUGIN_STATE_CHANGED)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) updateAndInspect() else mainHandler.post(updateAndInspect)
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
                addAction(Intent.ACTION_PACKAGE_DATA_CLEARED)
                addDataScheme("package")
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    applicationContext.registerReceiver(
                        packageChangedReceiver,
                        filter,
                        Context.RECEIVER_EXPORTED,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    applicationContext.registerReceiver(packageChangedReceiver, filter)
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to register the Screen Color Picker package monitor", error)
            }
            return State(
                status = Status.LOADING,
                enabledInPluginCenter = isEnabledInPluginCenter(applicationContext),
            ).also { initial ->
                state = initial
                scheduleInspection(applicationContext, Cause.INITIALIZED)
            }
        }
    }

    private fun scheduleInspection(context: Context, cause: Cause) {
        val requestId = inspectionId.incrementAndGet()
        inspectionExecutor.execute {
            val inspected = inspectSafely(context)
            if (inspectionId.get() != requestId) return@execute
            mainHandler.post {
                if (inspectionId.get() == requestId) {
                    publish(inspected, cause)
                    scheduleNextTransitionPoll()
                }
            }
        }
    }

    private fun inspectSafely(context: Context): State = runCatching {
        inspect(context)
    }.getOrElse { error ->
        Log.w(TAG, "Unable to inspect the Screen Color Picker plugin", error)
        State(
            status = Status.INELIGIBLE,
            enabledInPluginCenter = isEnabledInPluginCenter(context),
            errorMessage = error.message,
        )
    }

    private fun inspect(context: Context): State {
        val enabledInPluginCenter = isEnabledInPluginCenter(context)
        val applicationInfo = getApplicationInfoOrNull(context.packageManager)
            ?: return State(
                status = Status.NOT_INSTALLED,
                enabledInPluginCenter = enabledInPluginCenter,
            )
        if (!applicationInfo.enabled || !enabledInPluginCenter) {
            return State(
                status = Status.DISABLED,
                enabledInPluginCenter = enabledInPluginCenter,
            )
        }
        if (ScreenColorPickerPluginHost.queryServiceCount(context) == 0) {
            return State(
                status = Status.INELIGIBLE,
                enabledInPluginCenter = true,
                errorMessage = "Required plugin service is unavailable",
            )
        }
        return State(
            status = Status.AVAILABLE,
            enabledInPluginCenter = true,
            runtimeState = runBlocking { ScreenColorPickerPluginHost.getState(context) },
        )
    }

    private fun isEnabledInPluginCenter(context: Context): Boolean = PluginEnableStore.isEnabled(
        context,
        ScreenColorPickerContract.DEFAULT_PACKAGE_NAME,
        PluginDefaultEnabledPolicy.isEnabledByDefault(ScreenColorPickerContract.ENGINE),
    )

    private fun publish(current: State, cause: Cause) {
        val previous = state
        if (previous == current) {
            scheduleNextTransitionPoll()
            return
        }
        state = current
        val change = Change(previous, current, cause)
        listeners.forEach { listener -> listener(change) }
        when {
            !current.transitioning -> transitionPollDeadline = 0L
            cause == Cause.USER_ACTION || previous?.transitioning != true -> {
                transitionPollDeadline = SystemClock.uptimeMillis() + TRANSITION_POLL_TIMEOUT_MILLIS
            }
        }
        scheduleNextTransitionPoll()
    }

    private fun scheduleNextTransitionPoll() {
        mainHandler.removeCallbacks(transitionPollRunnable)
        val now = SystemClock.uptimeMillis()
        if (listeners.isEmpty() || state?.transitioning != true || now >= transitionPollDeadline) return
        mainHandler.postDelayed(transitionPollRunnable, TRANSITION_POLL_INTERVAL_MILLIS)
    }

    private fun getApplicationInfoOrNull(packageManager: PackageManager): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                ScreenColorPickerContract.DEFAULT_PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(
                ScreenColorPickerContract.DEFAULT_PACKAGE_NAME,
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }
    }.getOrNull()
}

internal fun ScreenColorPickerPluginStateMonitor.State.withPluginCenterEnabled(
    enabled: Boolean,
): ScreenColorPickerPluginStateMonitor.State {
    if (enabledInPluginCenter == enabled) return this
    return when {
        enabled -> copy(enabledInPluginCenter = true)
        else -> copy(
            status = if (installed) ScreenColorPickerPluginStateMonitor.Status.DISABLED else status,
            enabledInPluginCenter = false,
            runtimeState = ScreenColorPickerStates.INACTIVE,
            errorMessage = null,
        )
    }
}

internal object ScreenColorPickerPluginStateChangePolicy {
    fun shouldRefresh(packageName: String): Boolean =
        packageName == ScreenColorPickerContract.DEFAULT_PACKAGE_NAME
}

internal object ScreenColorPickerPluginPackageEventPolicy {
    fun shouldIgnore(action: String?, replacing: Boolean): Boolean =
        replacing && (action == Intent.ACTION_PACKAGE_REMOVED || action == Intent.ACTION_PACKAGE_ADDED)
}
