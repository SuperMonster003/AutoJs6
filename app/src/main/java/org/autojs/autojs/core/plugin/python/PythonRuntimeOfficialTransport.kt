package org.autojs.autojs.core.plugin.python

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.python.runtime.api.IPythonRuntimeProvider
import org.autojs.plugin.python.runtime.api.PythonRuntimeCapabilities
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeInfo
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Android adapter for the one official Python Runtime component.
 *
 * Discovery is never used to choose an arbitrary provider: the action query only proves that the
 * fixed component still declares the protocol action, and binding is always component-only with a
 * null action.
 */
internal class PythonRuntimeOfficialTransport(context: Context) {

    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    fun inspect(): InstalledProvider? {
        val pluginEnabledBeforeInspection = isPluginEnabled()
        val serviceInfo = getServiceInfo() ?: return null
        val packageInfo = getPackageInfo() ?: return null
        val applicationInfo = getApplicationInfo() ?: return null
        val trustInfo = PluginTrustManager.resolveTrustInfo(applicationContext, OFFICIAL_COMPONENT.packageName)
        val declaresRuntimeAction = queryRuntimeComponents().count { it == OFFICIAL_COMPONENT } == 1
        val sameSignerAsHost = packageManager.checkSignatures(
            applicationContext.packageName,
            OFFICIAL_COMPONENT.packageName,
        ) == PackageManager.SIGNATURE_MATCH
        return InstalledProvider(
            component = OFFICIAL_PROVIDER,
            declaredAction = if (declaresRuntimeAction) PythonRuntimeContract.RUNTIME_ACTION else "",
            exported = serviceInfo.exported,
            applicationEnabled = applicationInfo.enabled,
            serviceEnabled = serviceInfo.enabled,
            pluginEnabled = pluginEnabledBeforeInspection && isPluginEnabled(),
            requiredPermission = serviceInfo.permission,
            packageUid = applicationInfo.uid,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            lastUpdateTime = packageInfo.lastUpdateTime,
            signerSha256 = trustInfo.fingerprintsSha256,
            trustLevel = trustInfo.authorizedState.toProviderTrustLevel(),
            sameSignerAsHost = sameSignerAsHost,
        )
    }

    fun bind(
        timeoutMillis: Long,
        cancelled: () -> Boolean,
        onConnectionLost: (Throwable) -> Unit,
        abortFailure: () -> Throwable? = { null },
        important: Boolean = false,
    ): Binding {
        require(timeoutMillis > 0L) { "Python Runtime bind timeout must be positive" }
        requirePluginEnabled()
        abortFailure()?.let { throw it }
        val binding = Binding(applicationContext, onConnectionLost)
        val didBind = try {
            PluginAutoWake.bindServiceWaking(
                applicationContext,
                Intent().setComponent(OFFICIAL_COMPONENT),
                binding,
                Context.BIND_AUTO_CREATE or if (important) Context.BIND_IMPORTANT else 0,
            )
        } catch (error: SecurityException) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.PROVIDER_UNTRUSTED,
                "Official Python Runtime binding was denied",
                error,
            )
        } catch (error: RuntimeException) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.BIND_FAILED,
                "Official Python Runtime binding failed",
                error,
            )
        }
        if (!didBind) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.BIND_FAILED,
                "Official Python Runtime did not accept the explicit binding",
            )
        }
        binding.markBound()
        try {
            requirePluginEnabled()
            abortFailure()?.let { throw it }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (true) {
                requirePluginEnabled()
                abortFailure()?.let { throw it }
                if (cancelled()) {
                    throw InterruptedException("Python Runtime binding was cancelled")
                }
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) {
                    throw PythonRuntimeHostException(
                        PythonRuntimeHostError.BIND_TIMEOUT,
                        "Official Python Runtime binding timed out",
                    )
                }
                if (binding.await(minOf(TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1L), POLL_MILLIS))) {
                    requirePluginEnabled()
                    abortFailure()?.let { throw it }
                    return binding.requireConnected()
                }
            }
        } catch (error: Throwable) {
            // Ownership transfers to the caller only on the successful return above.
            binding.close()
            throw error
        }
    }

    internal fun isPluginEnabled(): Boolean = PluginEnableStore.isEnabled(
        applicationContext,
        OFFICIAL_COMPONENT.packageName,
        defaultEnabled = true,
    )

    private fun requirePluginEnabled() {
        if (!isPluginEnabled()) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.PLUGIN_DISABLED,
                "Official Python Runtime plugin is disabled in Plugin Center",
            )
        }
    }

    private fun getServiceInfo(): ServiceInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getServiceInfo(
                OFFICIAL_COMPONENT,
                PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getServiceInfo(OFFICIAL_COMPONENT, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun getPackageInfo(): PackageInfo? = try {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES.toLong()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                OFFICIAL_COMPONENT.packageName,
                PackageManager.PackageInfoFlags.of(flags),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(OFFICIAL_COMPONENT.packageName, flags.toInt())
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun getApplicationInfo(): ApplicationInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                OFFICIAL_COMPONENT.packageName,
                PackageManager.ApplicationInfoFlags.of(
                    PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(
                OFFICIAL_COMPONENT.packageName,
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun queryRuntimeComponents(): List<ComponentName> {
        val intent = Intent(PythonRuntimeBindingPlanner.discovery().action).setPackage(OFFICIAL_COMPONENT.packageName)
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, flags.toInt())
        }
        return resolved.mapNotNull { it.serviceInfo }
            .map { ComponentName(it.packageName, it.name) }
    }

    internal class Binding(
        private val context: Context,
        private val onConnectionLost: (Throwable) -> Unit,
    ) : ServiceConnection, Closeable {

        private val connected = CountDownLatch(1)
        private val bound = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)
        private val provider = AtomicReference<IPythonRuntimeProvider?>()
        private val binder = AtomicReference<IBinder?>()
        private val failure = AtomicReference<Throwable?>()
        private val processExit = PythonRuntimeProcessExitSignal()
        private val deathRecipient = IBinder.DeathRecipient {
            fail(PythonRuntimeHostException(PythonRuntimeHostError.EXECUTION_LOST, "Python Runtime Binder died"))
        }

        val intentAction: String? = null

        internal fun markBound() {
            bound.set(true)
            unbindIfClosed()
        }

        internal fun await(timeoutMillis: Long): Boolean =
            connected.await(timeoutMillis, TimeUnit.MILLISECONDS)

        internal fun requireConnected(): Binding {
            failure.get()?.let { throw it.asHostException() }
            if (provider.get() == null) {
                throw PythonRuntimeHostException(
                    PythonRuntimeHostError.BIND_FAILED,
                    "Official Python Runtime returned no Binder",
                )
            }
            return this
        }

        fun provider(): IPythonRuntimeProvider = provider.get()
            ?: throw PythonRuntimeHostException(
                PythonRuntimeHostError.BIND_FAILED,
                "Official Python Runtime is not connected",
            )

        internal fun awaitRemoteProcessExit(timeoutMillis: Long): Boolean =
            processExit.awaitUninterruptibly(timeoutMillis)

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) return
            if (name != OFFICIAL_COMPONENT) {
                fail(
                    PythonRuntimeHostException(
                        PythonRuntimeHostError.PROVIDER_IDENTITY_CHANGED,
                        "Python Runtime connected from an unexpected component",
                    ),
                )
                return
            }
            if (!binder.compareAndSet(null, service)) {
                fail(
                    PythonRuntimeHostException(
                        PythonRuntimeHostError.CONTRACT_VIOLATION,
                        "Python Runtime delivered more than one Binder",
                    ),
                )
                return
            }
            try {
                service.linkToDeath(deathRecipient, 0)
                provider.set(IPythonRuntimeProvider.Stub.asInterface(service))
                connected.countDown()
            } catch (error: Exception) {
                fail(error)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            fail(PythonRuntimeHostException(PythonRuntimeHostError.EXECUTION_LOST, "Python Runtime disconnected"))
        }

        override fun onBindingDied(name: ComponentName) {
            fail(PythonRuntimeHostException(PythonRuntimeHostError.EXECUTION_LOST, "Python Runtime binding died"))
        }

        override fun onNullBinding(name: ComponentName) {
            fail(PythonRuntimeHostException(PythonRuntimeHostError.BIND_FAILED, "Python Runtime returned a null binding"))
        }

        private fun fail(error: Throwable) {
            if (closed.get()) return
            processExit.markExited()
            if (failure.compareAndSet(null, error)) {
                connected.countDown()
                onConnectionLost(error)
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            processExit.markExited()
            binder.getAndSet(null)?.let { value -> runCatching { value.unlinkToDeath(deathRecipient, 0) } }
            provider.set(null)
            unbindIfClosed()
            connected.countDown()
        }

        private fun unbindIfClosed() {
            if (closed.get() && bound.compareAndSet(true, false)) {
                runCatching { context.unbindService(this) }
            }
        }
    }

    internal data class InstalledProvider(
        val component: PythonRuntimeProviderComponent,
        val declaredAction: String,
        val exported: Boolean,
        val applicationEnabled: Boolean,
        val serviceEnabled: Boolean,
        val pluginEnabled: Boolean,
        val requiredPermission: String?,
        val packageUid: Int,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val signerSha256: List<String>,
        val trustLevel: PythonRuntimeProviderTrustLevel,
        val sameSignerAsHost: Boolean,
    ) {
        fun toCandidate(
            runtimeInfo: PythonRuntimeInfo,
            capabilities: PythonRuntimeCapabilities,
        ): PythonRuntimeProviderCandidate = PythonRuntimeProviderCandidate(
            component = component,
            declaredAction = declaredAction,
            exported = exported,
            applicationEnabled = applicationEnabled && pluginEnabled,
            serviceEnabled = serviceEnabled,
            requiredPermission = requiredPermission,
            packageUid = packageUid,
            versionCode = versionCode,
            lastUpdateTime = lastUpdateTime,
            signerSha256 = signerSha256,
            trustLevel = trustLevel,
            sameSignerAsHost = sameSignerAsHost,
            runtimeInfo = runtimeInfo,
            capabilities = capabilities,
        )
    }

    companion object {
        const val OFFICIAL_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val OFFICIAL_SERVICE = "$OFFICIAL_PACKAGE.service.PythonRuntimePluginService"
        private const val POLL_MILLIS = 50L

        val OFFICIAL_COMPONENT = ComponentName(OFFICIAL_PACKAGE, OFFICIAL_SERVICE)
        val OFFICIAL_PROVIDER = PythonRuntimeProviderComponent(OFFICIAL_PACKAGE, OFFICIAL_SERVICE)
    }
}

private fun PluginAuthorizedState.toProviderTrustLevel(): PythonRuntimeProviderTrustLevel = when (this) {
    PluginAuthorizedState.OFFICIAL -> PythonRuntimeProviderTrustLevel.OFFICIAL
    PluginAuthorizedState.TRUSTED,
    PluginAuthorizedState.USER_GRANTED,
    -> PythonRuntimeProviderTrustLevel.USER_APPROVED
    PluginAuthorizedState.REQUIRED -> PythonRuntimeProviderTrustLevel.UNTRUSTED
}

private fun Throwable.asHostException(): PythonRuntimeHostException =
    this as? PythonRuntimeHostException
        ?: PythonRuntimeHostException(PythonRuntimeHostError.BIND_FAILED, "Python Runtime binding failed", this)
