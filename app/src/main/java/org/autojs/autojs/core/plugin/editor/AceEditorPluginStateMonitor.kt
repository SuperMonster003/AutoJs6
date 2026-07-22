package org.autojs.autojs.core.plugin.editor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.plugin.editor.api.EditorPluginContract
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/** Application-scoped source of truth for the external Ace editor plugin state. */
object AceEditorPluginStateMonitor {

    private const val TAG = "AcePluginStateMonitor"

    enum class Status {
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
        REFRESHED,
    }

    data class State(
        val status: Status,
        val packageName: String = EditorPluginContract.DEFAULT_PACKAGE_NAME,
        val packageVersionCode: Long? = null,
        val packageLastUpdateTime: Long? = null,
        val errorCode: EditorPluginLoadError? = null,
        val eligibilityIdentity: String = "",
    ) {
        val available: Boolean
            get() = status == Status.AVAILABLE

        val packageIdentity: String?
            get() = packageVersionCode?.let { version ->
                "$packageName@$version:${packageLastUpdateTime ?: -1L}"
            }
    }

    data class Change(
        val id: Long,
        val previous: State?,
        val current: State,
        val cause: Cause,
    ) {
        val requiresSessionReload: Boolean
            get() = current.available && previous != null && (
                previous.available != true ||
                    cause == Cause.PACKAGE_REPLACED ||
                    cause == Cause.PACKAGE_DATA_CLEARED ||
                    previous.packageIdentity != current.packageIdentity
                )
    }

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(Change) -> Unit>()
    private val nextChangeId = AtomicLong(0L)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var state: State? = null

    @Volatile
    private var change: Change? = null

    private val pluginStateChangedListener: (String) -> Unit = { packageName ->
        if (packageName == EditorPluginContract.DEFAULT_PACKAGE_NAME) {
            refresh(Cause.PLUGIN_STATE_CHANGED)
        }
    }

    private val packageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            if (packageName != EditorPluginContract.DEFAULT_PACKAGE_NAME) return
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (AceEditorPluginPackageEventPolicy.shouldIgnore(intent.action, replacing)) return
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

    fun initialize(context: Context): State {
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
                    applicationContext.registerReceiver(packageChangedReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    applicationContext.registerReceiver(packageChangedReceiver, filter)
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to register the Ace plugin package monitor.", error)
            }
            return inspectSafely(applicationContext).also { initial ->
                state = initial
                change = Change(
                    id = nextChangeId.incrementAndGet(),
                    previous = null,
                    current = initial,
                    cause = Cause.INITIALIZED,
                )
            }
        }
    }

    fun current(context: Context): State = state ?: initialize(context)

    fun latestChange(context: Context): Change {
        initialize(context)
        return checkNotNull(change)
    }

    fun addListener(context: Context, listener: (Change) -> Unit) {
        initialize(context)
        listeners += listener
    }

    fun removeListener(listener: (Change) -> Unit) {
        listeners -= listener
    }

    fun refresh(cause: Cause = Cause.REFRESHED) {
        val context = appContext ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshOnMain(context, cause)
        } else {
            mainHandler.post { refreshOnMain(context, cause) }
        }
    }

    private fun refreshOnMain(context: Context, cause: Cause) {
        val previous = state
        val current = inspectSafely(context)
        val forcePublish = cause == Cause.PACKAGE_REPLACED || cause == Cause.PACKAGE_DATA_CLEARED
        if (!forcePublish && previous == current) return
        state = current
        val event = Change(
            id = nextChangeId.incrementAndGet(),
            previous = previous,
            current = current,
            cause = cause,
        )
        change = event
        listeners.forEach { listener -> listener(event) }
    }

    private fun inspectSafely(context: Context): State = runCatching {
        inspect(context)
    }.getOrElse { error ->
        Log.e(TAG, "Unable to inspect the Ace editor plugin.", error)
        State(
            status = Status.INELIGIBLE,
            errorCode = EditorPluginLoadError.DISCOVERY_FAILED,
            eligibilityIdentity = "inspection=${error.javaClass.name}:${error.message.orEmpty()}",
        )
    }

    private fun inspect(context: Context): State {
        val packageName = EditorPluginContract.DEFAULT_PACKAGE_NAME
        val discovered = AceEditorPluginHost.discover(context, packageName)
        val available = discovered
            .mapNotNull { item -> item.candidate?.takeIf { item.error == null } }
            .maxByOrNull { it.packageVersionCode }
        if (available != null) {
            return available.toState(Status.AVAILABLE, errorCode = null)
        }

        val rejected = discovered.firstOrNull()
        val rejectedCandidate = rejected?.candidate
        val rejectedError = rejected?.error
        if (rejectedCandidate != null || rejectedError != null) {
            val status = if (rejectedError?.code == EditorPluginLoadError.PLUGIN_DISABLED) {
                Status.DISABLED
            } else {
                Status.INELIGIBLE
            }
            return rejectedCandidate?.toState(status, rejectedError?.code)
                ?: State(status = status, errorCode = rejectedError?.code)
        }

        val applicationInfo = getApplicationInfoOrNull(context.packageManager, packageName)
            ?: return State(status = Status.NOT_INSTALLED)
        return State(
            status = if (applicationInfo.enabled) Status.INELIGIBLE else Status.DISABLED,
            eligibilityIdentity = "appEnabled=${applicationInfo.enabled}",
        )
    }

    private fun AceEditorPluginHost.Candidate.toState(
        status: Status,
        errorCode: EditorPluginLoadError?,
    ): State = State(
        status = status,
        packageName = serviceInfo.packageName,
        packageVersionCode = packageVersionCode,
        packageLastUpdateTime = packageLastUpdateTime,
        errorCode = errorCode,
        eligibilityIdentity = buildString {
            append("enabled=").append(enabled)
            append(";authorized=").append(authorizedState.name)
            append(";sameSigner=").append(sameSignerAsHost)
            append(";contract=").append(contractVersion)
            append(";minHost=").append(requiresHostVersion)
            append(";maxHost=").append(maxHostVersion)
        },
    )

    private fun getApplicationInfoOrNull(
        packageManager: PackageManager,
        packageName: String,
    ): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
    }.getOrNull()
}

internal object AceEditorPluginPackageEventPolicy {

    fun shouldIgnore(action: String?, replacing: Boolean): Boolean =
        replacing && (action == Intent.ACTION_PACKAGE_REMOVED || action == Intent.ACTION_PACKAGE_ADDED)
}
