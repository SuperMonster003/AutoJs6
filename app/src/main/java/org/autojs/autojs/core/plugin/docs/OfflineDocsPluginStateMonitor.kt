package org.autojs.autojs.core.plugin.docs

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
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Application-scoped source of truth for Offline Documentation plugin availability. */
object OfflineDocsPluginStateMonitor {

    private const val TAG = "OfflineDocsStateMonitor"

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
        REFRESHED,
    }

    data class State(
        val status: Status,
        val packageName: String = OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME,
        val packageVersionCode: Long? = null,
        val packageLastUpdateTime: Long? = null,
        val contentVersion: String? = null,
        val contentSha256: String? = null,
        val errorCode: OfflineDocsPluginLoadError? = null,
        val eligibilityIdentity: String = "",
    ) {
        val available: Boolean
            get() = status == Status.AVAILABLE

        val packageIdentity: String?
            get() = packageVersionCode?.let { version ->
                "$packageName@$version:${packageLastUpdateTime ?: -1L}:${contentSha256.orEmpty()}"
            }
    }

    data class Change(
        val id: Long,
        val previous: State?,
        val current: State,
        val cause: Cause,
    ) {
        val requiresContentReload: Boolean
            get() = current.available && previous != null && (
                    !previous.available ||
                            cause == Cause.PACKAGE_REPLACED ||
                            cause == Cause.PACKAGE_DATA_CLEARED ||
                            previous.packageIdentity != current.packageIdentity
                    )

        val requiresContentRelease: Boolean
            get() = previous?.available == true && (
                    !current.available ||
                            cause == Cause.PACKAGE_REPLACED ||
                            cause == Cause.PACKAGE_DATA_CLEARED ||
                            previous.packageIdentity != current.packageIdentity
                    )
    }

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(Change) -> Unit>()
    private val nextChangeId = AtomicLong(0L)
    private val inspectionRequestGate = OfflineDocsInspectionRequestGate()
    private val inspectionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoJs6-OfflineDocs-Verification")
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var state: State? = null

    @Volatile
    private var change: Change? = null

    @Volatile
    private var loadedPluginSnapshot: LoadedPluginSnapshot? = null

    private val pluginStateChangedListener: (String) -> Unit = { packageName ->
        if (packageName == OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME) {
            refresh(Cause.PLUGIN_STATE_CHANGED)
        }
    }

    private val packageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            if (packageName != OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME) return
            OfflineDocsPluginHost.invalidateVerificationCache()
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (OfflineDocsPluginPackageEventPolicy.shouldIgnore(intent.action, replacing)) return
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
                Log.e(TAG, "Unable to register the Offline Documentation plugin monitor.", error)
            }
            return State(status = Status.LOADING).also { initial ->
                state = initial
                change = Change(
                    id = nextChangeId.incrementAndGet(),
                    previous = null,
                    current = initial,
                    cause = Cause.INITIALIZED,
                )
                scheduleInspection(applicationContext, Cause.INITIALIZED)
            }
        }
    }

    fun current(context: Context): State = state ?: initialize(context)

    /**
     * Returns only a plugin that has already completed discovery, trust checks, inventory parsing,
     * and full content verification on the background inspection thread.
     */
    fun loadedPlugin(context: Context): OfflineDocsPluginHost.LoadedPlugin? {
        val currentState = current(context)
        return loadedPluginSnapshot
            ?.takeIf { snapshot -> snapshot.state == currentState && currentState.available }
            ?.plugin
    }

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
        if (cause != Cause.REFRESHED && cause != Cause.INITIALIZED) {
            loadedPluginSnapshot = null
            if (Looper.myLooper() == Looper.getMainLooper()) {
                publishInvalidation(cause)
            } else {
                mainHandler.post { publishInvalidation(cause) }
            }
        }
        scheduleInspection(context, cause)
    }

    private fun publishInvalidation(cause: Cause) {
        val previous = state ?: return
        if (previous.status == Status.LOADING) return
        val loading = previous.copy(
            status = Status.LOADING,
            errorCode = null,
        )
        state = loading
        val event = Change(
            id = nextChangeId.incrementAndGet(),
            previous = previous,
            current = loading,
            cause = cause,
        )
        change = event
        listeners.forEach { listener -> listener(event) }
    }

    private fun scheduleInspection(context: Context, cause: Cause) {
        val request = inspectionRequestGate.newRequest(cause)
        inspectionExecutor.execute {
            if (!inspectionRequestGate.isLatest(request)) return@execute
            val startedAt = SystemClock.elapsedRealtime()
            val inspection = inspectSafely(context)
            val elapsedMillis = SystemClock.elapsedRealtime() - startedAt
            Log.i(
                TAG,
                "Offline Documentation plugin inspection and content verification completed " +
                        "in $elapsedMillis ms on ${Thread.currentThread().name}.",
            )
            if (!inspectionRequestGate.isLatest(request)) return@execute
            mainHandler.post {
                publishInspection(request, inspection)
            }
        }
    }

    private fun publishInspection(
        request: OfflineDocsInspectionRequestGate.Request,
        inspection: Inspection,
    ) {
        val cause = inspectionRequestGate.resolveCauseForPublication(request) ?: return
        val previous = state
        val current = inspection.state
        val previousLoadedPlugin = loadedPluginSnapshot?.plugin
        val forcePublish = cause == Cause.PACKAGE_REPLACED ||
                cause == Cause.PACKAGE_DATA_CLEARED ||
                (previous?.available == true &&
                        previousLoadedPlugin == null &&
                        inspection.loadedPlugin != null)
        state = current
        loadedPluginSnapshot = inspection.loadedPlugin?.let { plugin ->
            LoadedPluginSnapshot(current, plugin)
        }
        if (!forcePublish && previous == current) return
        val event = Change(
            id = nextChangeId.incrementAndGet(),
            previous = previous,
            current = current,
            cause = cause,
        )
        change = event
        listeners.forEach { listener -> listener(event) }
    }

    private fun inspectSafely(context: Context): Inspection = runCatching {
        inspect(context)
    }.getOrElse { error ->
        Log.e(TAG, "Unable to inspect the Offline Documentation plugin.", error)
        Inspection(
            state = State(
                status = Status.INELIGIBLE,
                errorCode = (error as? OfflineDocsPluginLoadException)?.code
                    ?: OfflineDocsPluginLoadError.DISCOVERY_FAILED,
                eligibilityIdentity = "inspection=${error.javaClass.name}:${error.message.orEmpty()}",
            ),
        )
    }

    private fun inspect(context: Context): Inspection {
        val discovered = OfflineDocsPluginHost.discover(context)
        val available = discovered
            .asSequence()
            .filter { it.error == null }
            .mapNotNull(OfflineDocsPluginHost.Discovered::candidate)
            .maxWithOrNull(
                compareBy<OfflineDocsPluginHost.Candidate> { it.packageVersionCode }
                    .thenBy { it.packageLastUpdateTime },
            )
        if (available != null) {
            return try {
                val loadedPlugin = OfflineDocsPluginHost.load(context)
                Inspection(
                    state = loadedPlugin.candidate.toState(Status.AVAILABLE, errorCode = null),
                    loadedPlugin = loadedPlugin,
                )
            } catch (error: OfflineDocsPluginLoadException) {
                Inspection(available.toState(Status.INELIGIBLE, error.code))
            }
        }

        val rejected = discovered.firstOrNull()
        val rejectedCandidate = rejected?.candidate
        val rejectedError = rejected?.error
        if (rejectedCandidate != null || rejectedError != null) {
            val status = if (rejectedError?.code == OfflineDocsPluginLoadError.PLUGIN_DISABLED) {
                Status.DISABLED
            } else {
                Status.INELIGIBLE
            }
            return Inspection(
                rejectedCandidate?.toState(status, rejectedError?.code)
                    ?: State(status = status, errorCode = rejectedError?.code),
            )
        }

        val packageName = OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME
        val applicationInfo = getApplicationInfoOrNull(context.packageManager, packageName)
            ?: return Inspection(State(status = Status.NOT_INSTALLED))
        val enabled = applicationInfo.enabled && PluginEnableStore.isEnabled(context, packageName, true)
        return Inspection(
            State(
                status = if (enabled) Status.INELIGIBLE else Status.DISABLED,
                eligibilityIdentity = "appEnabled=${applicationInfo.enabled};hostEnabled=$enabled",
            ),
        )
    }

    private fun OfflineDocsPluginHost.Candidate.toState(
        status: Status,
        errorCode: OfflineDocsPluginLoadError?,
    ): State = State(
        status = status,
        packageName = serviceInfo.packageName,
        packageVersionCode = packageVersionCode,
        packageLastUpdateTime = packageLastUpdateTime,
        contentVersion = contentVersion,
        contentSha256 = contentSha256,
        errorCode = errorCode,
        eligibilityIdentity = buildString {
            append("enabled=").append(enabled)
            append(";authorized=").append(authorizedState.name)
            append(";sameSigner=").append(sameSignerAsHost)
            append(";contract=").append(contractVersion)
            append(";contentVersion=").append(contentVersion)
            append(";contentSha256=").append(contentSha256)
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

    private data class Inspection(
        val state: State,
        val loadedPlugin: OfflineDocsPluginHost.LoadedPlugin? = null,
    )

    private data class LoadedPluginSnapshot(
        val state: State,
        val plugin: OfflineDocsPluginHost.LoadedPlugin,
    )
}

internal object OfflineDocsPluginPackageEventPolicy {

    fun shouldIgnore(action: String?, replacing: Boolean): Boolean =
        replacing && (action == Intent.ACTION_PACKAGE_REMOVED || action == Intent.ACTION_PACKAGE_ADDED)
}

/**
 * Coordinates asynchronous inspections without allowing a superseded package snapshot to be
 * published after a newer request. A replacement/data-clear cause survives a following manual
 * refresh so that an identity-stable package replacement still rebuilds the WebView asset loader.
 */
internal class OfflineDocsInspectionRequestGate {

    data class Request(
        val id: Long,
        val cause: OfflineDocsPluginStateMonitor.Cause,
    )

    private val lock = Any()
    private var latestRequestId = 0L
    private var lastPublishedRequestId = 0L
    private var pendingForcedRequest: Request? = null

    fun newRequest(cause: OfflineDocsPluginStateMonitor.Cause): Request = synchronized(lock) {
        Request(++latestRequestId, cause).also { request ->
            if (cause.requiresForcedPublication) {
                pendingForcedRequest = request
            }
        }
    }

    fun isLatest(request: Request): Boolean = synchronized(lock) {
        request.id == latestRequestId
    }

    fun resolveCauseForPublication(
        request: Request,
    ): OfflineDocsPluginStateMonitor.Cause? = synchronized(lock) {
        if (request.id != latestRequestId) return@synchronized null
        val forcedRequest = pendingForcedRequest?.takeIf { forced ->
            forced.id > lastPublishedRequestId && forced.id <= request.id
        }
        lastPublishedRequestId = request.id
        if (forcedRequest != null) pendingForcedRequest = null
        forcedRequest?.cause ?: request.cause
    }

    private val OfflineDocsPluginStateMonitor.Cause.requiresForcedPublication: Boolean
        get() = this == OfflineDocsPluginStateMonitor.Cause.PACKAGE_REPLACED ||
                this == OfflineDocsPluginStateMonitor.Cause.PACKAGE_DATA_CLEARED
}
