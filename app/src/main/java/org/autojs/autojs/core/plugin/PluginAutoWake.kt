package org.autojs.autojs.core.plugin

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.center.PluginWakeManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Wakes plugins whose service binding the system refused, so that users never need the Plugin Center "Activate"
 * action. ColorOS refuses to bind a plugin whenever its process is not running (not only before the first run);
 * starting the plugin's wake activity (no display, it finishes at once) lets the next binding succeed, usually after
 * about 300 ms. Bind sites throw [PluginBindRefusedException] when `bindService` returns false and wrap the binding in
 * [retryRefusedBind] (coroutines) or [retryRefusedBindBlocking] (worker threads).
 *
 * A plugin is woken when it is enabled in the Plugin Center (or only suspended because it could not be reached) and
 * authorized; [UserRequest] lifts that condition for a call the user asked for, such as switching a plugin on, and
 * [Off] keeps a call from waking at all (the Plugin Center list, which would otherwise start every plugin at once).
 * Starting the wake activity needs AutoJs6 in the foreground or the "display over other apps" permission; otherwise
 * the system ignores it and the call fails as before. [PluginWakePolicy] backs off after wakes that did not help.
 *
 * zh-CN: 唤醒系统拒绝绑定其服务的插件, 使用户不再需要插件中心的 "激活" 操作. ColorOS 在插件进程未运行时一律拒绝绑定 (不只是首次运行前);
 * 启动插件的唤醒 Activity (无界面, 立即结束) 后, 下一次绑定通常在约 300 ms 后即可成功. 各绑定点在 `bindService` 返回 false 时抛出
 * [PluginBindRefusedException], 并用 [retryRefusedBind] (协程) 或 [retryRefusedBindBlocking] (工作线程) 包裹绑定. 插件在插件中心已启用
 * (或只是因无法连接而被挂起) 且已授权时才会被唤醒; [UserRequest] 为用户主动发起的调用 (例如打开插件开关) 取消该条件, [Off] 让调用
 * 完全不唤醒 (插件中心列表, 否则会一次启动全部插件). 启动唤醒 Activity 需要 AutoJs6 位于前台或拥有 "显示在其他应用上层" 权限,
 * 否则系统会忽略, 调用照旧失败. [PluginWakePolicy] 在唤醒无效后退避.
 */
object PluginAutoWake {

    private const val TAG = "PluginAutoWake"

    private val policy = PluginWakePolicy()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val blockingLocks = ConcurrentHashMap<String, Any>()

    private val wakeCounts = ConcurrentHashMap<String, AtomicInteger>()

    /** Wake activities started for [packageName] so far; device tests read it. zh-CN: 已为该包启动的唤醒次数; 供设备测试读取. */
    @VisibleForTesting
    internal fun wakesStartedFor(packageName: String): Int = wakeCounts[packageName]?.get() ?: 0

    enum class Mode { ON_DEMAND, USER_REQUEST, OFF }

    /** How the calls in a coroutine may wake plugins. zh-CN: 协程中的调用可以如何唤醒插件. */
    class Scope(val mode: Mode) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Scope>
    }

    /** The user asked for the call: wake even a plugin that is not enabled yet. zh-CN: 用户主动发起: 未启用的插件也唤醒. */
    val UserRequest: CoroutineContext = Scope(Mode.USER_REQUEST)

    /** Never wake. zh-CN: 从不唤醒. */
    val Off: CoroutineContext = Scope(Mode.OFF)

    /**
     * Whether [packageName] is enabled in the Plugin Center (or only suspended because it was unreachable) and
     * authorized, the condition for waking it on demand.
     * zh-CN: [packageName] 是否在插件中心已启用 (或只是因无法连接而被挂起) 且已授权, 即按需唤醒的条件.
     */
    fun isEligible(context: Context, packageName: String, defaultEnabled: Boolean = true): Boolean = runCatching {
        (PluginEnableStore.isEnabled(context, packageName, defaultEnabled) || PluginEnableStore.isSuspended(context, packageName)) &&
                PluginTrustManager.isAuthorized(context, packageName)
    }.getOrDefault(false)

    /**
     * Runs [bind]; when the system refuses the binding (a [PluginBindRefusedException], also as a cause) and the
     * plugin may be woken, wakes it and runs [bind] again after each pause of [PluginWakePolicy.RETRY_DELAYS_MS] until
     * it succeeds. Concurrent calls for one package share one wake.
     * zh-CN: 执行 [bind]; 系统拒绝绑定 ([PluginBindRefusedException], 也可作为原因) 且插件可以唤醒时, 唤醒插件并在
     * [PluginWakePolicy.RETRY_DELAYS_MS] 的每次等待后再次执行 [bind], 直至成功. 同一包的并发调用共用一次唤醒.
     */
    suspend fun <T> retryRefusedBind(
        context: Context,
        packageName: String,
        defaultEnabled: Boolean = true,
        bind: suspend () -> T,
    ): T {
        try {
            return bind()
        } catch (refused: Throwable) {
            if (!refused.isPluginBindRefused()) throw refused
            val mode = currentCoroutineContext()[Scope]?.mode ?: Mode.ON_DEMAND
            if (!mayWake(context, packageName, defaultEnabled, mode)) throw refused
            return locks.getOrPut(packageName) { Mutex() }.withLock {
                // A call that waited here may find the plugin already awake. zh-CN: 在此等待的调用可能发现插件已被唤醒.
                try {
                    return@withLock bind()
                } catch (again: Throwable) {
                    if (!again.isPluginBindRefused()) throw again
                }
                if (mode != Mode.USER_REQUEST && !policy.mayWake(packageName)) throw refused
                if (!wake(context, packageName)) throw refused
                var last = refused
                for (pause in PluginWakePolicy.RETRY_DELAYS_MS) {
                    delay(pause)
                    try {
                        return@withLock bind().also { policy.onWakeHelped(packageName) }
                    } catch (again: Throwable) {
                        if (!again.isPluginBindRefused()) throw again
                        last = again
                    }
                }
                gaveUp(packageName)
                throw last
            }
        }
    }

    /**
     * [retryRefusedBind] for code that binds on a worker thread and waits by blocking it; on the main thread it never
     * wakes.
     * zh-CN: 供在工作线程上绑定并以阻塞方式等待的代码使用的 [retryRefusedBind]; 在主线程上从不唤醒.
     */
    fun <T> retryRefusedBindBlocking(
        context: Context,
        packageName: String,
        defaultEnabled: Boolean = true,
        bind: () -> T,
    ): T {
        try {
            return bind()
        } catch (refused: Throwable) {
            if (!refused.isPluginBindRefused()) throw refused
            if (Looper.myLooper() == Looper.getMainLooper()) throw refused
            if (!mayWake(context, packageName, defaultEnabled, Mode.ON_DEMAND)) throw refused
            synchronized(blockingLocks.getOrPut(packageName) { Any() }) {
                try {
                    return bind()
                } catch (again: Throwable) {
                    if (!again.isPluginBindRefused()) throw again
                }
                if (!policy.mayWake(packageName) || !wake(context, packageName)) throw refused
                var last = refused
                for (pause in PluginWakePolicy.RETRY_DELAYS_MS) {
                    Thread.sleep(pause)
                    try {
                        return bind().also { policy.onWakeHelped(packageName) }
                    } catch (again: Throwable) {
                        if (!again.isPluginBindRefused()) throw again
                        last = again
                    }
                }
                gaveUp(packageName)
                throw last
            }
        }
    }

    /**
     * `bindService` for code that waits on a worker thread: when the system refuses to bind [intent] (an explicit
     * component of a plugin), wakes the plugin and binds again (see [retryRefusedBindBlocking]); on the main thread it
     * only binds once. Returns whether the binding was registered, like `bindService`.
     * zh-CN: 供在工作线程上等待的代码使用的 `bindService`: 系统拒绝绑定 [intent] (插件的显式组件) 时唤醒插件并重新绑定 (见
     * [retryRefusedBindBlocking]); 在主线程上只绑定一次. 与 `bindService` 一样返回绑定是否已登记.
     */
    fun bindServiceWaking(context: Context, intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
        val packageName = intent.component?.packageName ?: intent.`package` ?: return context.bindService(intent, connection, flags)
        return try {
            retryRefusedBindBlocking(context, packageName) {
                if (!context.bindService(intent, connection, flags)) {
                    throw PluginBindRefusedException(packageName, "bindService failed: ${intent.component ?: packageName}")
                }
                true
            }
        } catch (_: PluginBindRefusedException) {
            false
        }
    }

    private fun mayWake(context: Context, packageName: String, defaultEnabled: Boolean, mode: Mode): Boolean = when (mode) {
        Mode.OFF -> false
        Mode.USER_REQUEST -> true
        Mode.ON_DEMAND -> policy.mayWake(packageName) && isEligible(context, packageName, defaultEnabled)
    }

    private fun wake(context: Context, packageName: String): Boolean {
        val started = PluginWakeManager.tryAutoWakeIfNeeded(context.applicationContext, packageName)
        if (started) {
            wakeCounts.getOrPut(packageName) { AtomicInteger() }.incrementAndGet()
            Log.i(TAG, "woke $packageName after a refused binding")
        } else {
            policy.onWakeFailed(packageName)
            Log.w(TAG, "could not wake $packageName after a refused binding")
        }
        return started
    }

    private fun gaveUp(packageName: String) {
        policy.onWakeFailed(packageName)
        Log.w(TAG, "binding $packageName was still refused after the wake")
    }
}
