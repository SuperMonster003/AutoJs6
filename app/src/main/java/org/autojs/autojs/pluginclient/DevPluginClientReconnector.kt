package org.autojs.autojs.pluginclient

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import org.autojs.autojs.core.pref.keys.ConnectionKeys
import java.io.IOException

/**
 * Restores the client mode connection after an unexpected disconnect (extension crash, VSCode restart,
 * network hiccup, `bye(shutdown)`). Driven by [JsonSocketClient.cxnState]: a DISCONNECTED state that
 * carries an exception schedules the next attempt with exponential backoff (2, 4, 8, 16, 30 s; 60 s once
 * the session is older than 10 minutes) until a handshake succeeds or the session is cancelled.
 *
 * It never fights the person: an explicit disconnect here or in VSCode (`bye(user)`) disarms it until a
 * new handshake succeeds, a rejected handshake stops it for good, and a failed *manual* attempt is not
 * retried either (the session must have been connected at least once, or be a launch restore).
 *
 * zh-CN: 意外断开 (插件崩溃, VSCode 重启, 网络抖动, `bye(shutdown)`) 后恢复客户端模式连接. 由 [JsonSocketClient.cxnState]
 * 驱动: 携带异常的 DISCONNECTED 状态以指数退避 (2, 4, 8, 16, 30 秒; 会话超过 10 分钟后为 60 秒) 安排下一次尝试,
 * 直至握手成功或会话被取消.
 * 它绝不违背用户意愿: 本地或 VSCode 中的显式断开 (`bye(user)`) 会将其解除武装直至新的握手成功, 握手被拒绝则彻底停止,
 * 手动连接失败也不会重试 (会话须至少成功连接过一次, 或属于启动时恢复).
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
@SuppressLint("CheckResult")
class DevPluginClientReconnector(private val service: DevPluginService) {

    /**
     * Snapshot for the drawer subtitle and the manager dialog.
     * zh-CN: 供抽屉副标题与管理器对话框使用的快照.
     */
    data class Status(
        val isActive: Boolean = false,
        /** Attempts made in the current session. zh-CN: 当前会话已尝试次数. */
        val attempts: Int = 0,
        /** Epoch millis of the pending attempt, 0 while an attempt is in flight. zh-CN: 待执行尝试的时间戳, 尝试进行中为 0. */
        val nextAttemptAt: Long = 0L,
        val lastError: String? = null,
        val host: String? = null,
    )

    val status: BehaviorSubject<Status> = BehaviorSubject.createDefault(Status())

    val isActive: Boolean
        get() = status.value?.isActive == true

    private val appContext = service.context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val retryRunnable = Runnable { attempt() }

    // True once a handshake succeeded (or a launch restore was requested) since the last explicit disconnect.
    // zh-CN: 上次显式断开之后, 握手成功过 (或请求过启动时恢复) 则为 true.
    @Volatile
    private var isArmed = false

    private var sessionStartedAt = 0L
    private var attempts = 0
    private var isAttemptInFlight = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkCallbackRegisteredAt = 0L

    init {
        JsonSocketClient.cxnState
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ onState(it) }, { Log.w(TAG, "Connection state stream failed", it) })
    }

    private fun onState(state: DevPluginService.State) {
        when {
            state.isConnected() -> {
                isArmed = true
                finish()
            }
            state.isConnecting() -> Unit
            state.isDisconnected() -> onDisconnected(state.exception)
        }
    }

    private fun onDisconnected(e: Throwable?) {
        isAttemptInFlight = false
        when {
            // A clean disconnect: the person (here or in VSCode) asked for it, nothing to restore.
            // zh-CN: 正常断开: 用户 (本地或 VSCode 中) 主动要求, 无需恢复.
            e == null -> {
                if (service.isClientSocketNormallyClosed) isArmed = false
                finish()
            }
            e is HandshakeRejectedException -> {
                isArmed = false
                finish()
            }
            !isArmed || service.isClientSocketNormallyClosed || !ConnectionKeys.CLIENT_MODE_AUTO_RECONNECT.get() -> finish()
            else -> scheduleNext(e)
        }
    }

    private fun scheduleNext(e: Throwable) {
        val host = ConnectionKeys.SERVER_ADDRESS.get().trim()
        if (host.isEmpty()) {
            finish()
            return
        }
        val now = System.currentTimeMillis()
        if (sessionStartedAt == 0L) sessionStartedAt = now
        val delay = delayFor(attempts, now - sessionStartedAt)
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, delay)
        registerNetworkCallback()
        status.onNext(Status(true, attempts, now + delay, e.message ?: e.javaClass.simpleName, host))
        Log.i(TAG, "Reconnect attempt #${attempts + 1} to $host in $delay ms (${e.message})")
    }

    private fun delayFor(attempts: Int, sessionElapsed: Long): Long = when {
        sessionElapsed >= LONG_SESSION_MILLIS -> LONG_SESSION_DELAY_MILLIS
        else -> BACKOFF_MILLIS[attempts.coerceIn(0, BACKOFF_MILLIS.lastIndex)]
    }

    private fun attempt() {
        handler.removeCallbacks(retryRunnable)
        if (!isArmed || service.isClientSocketNormallyClosed || !ConnectionKeys.CLIENT_MODE_AUTO_RECONNECT.get()) {
            finish()
            return
        }
        if (service.isJsonSocketClientConnected) {
            finish()
            return
        }
        val host = ConnectionKeys.SERVER_ADDRESS.get().trim()
        if (host.isEmpty()) {
            finish()
            return
        }
        attempts++
        isAttemptInFlight = true
        status.onNext(Status(true, attempts, 0L, status.value?.lastError, host))
        Log.i(TAG, "Reconnect attempt #$attempts to $host")

        // The application context keeps handshake problems as toasts; no Activity dialog can outlive a recreation.
        // zh-CN: 使用应用上下文, 握手问题以 toast 展示; 不会有对话框在 Activity 重建后残留.
        service.connectToRemoteServer(appContext, host, ignoreExceptions = true)
            .isEmpty
            .subscribe({ empty ->
                // Empty means the connection could not even be started (unparsable address etc.).
                // zh-CN: 为空表示连接根本无法发起 (地址无法解析等).
                if (empty) handler.post { if (isAttemptInFlight) onDisconnected(IOException("Unable to start the connection to $host")) }
            }, { error ->
                handler.post { if (isAttemptInFlight) onDisconnected(error) }
            })
    }

    /**
     * Start (or restart) a session right now, e.g. from the manager's "Reconnect now"; being explicit, it also
     * clears the "normally closed" mark left by an earlier explicit disconnect. Returns false when no server
     * address has been saved.
     * zh-CN: 立即开始 (或重新开始) 会话, 如管理器的 "立即重连"; 作为显式操作, 它同时清除此前显式断开留下的 "正常关闭" 标记.
     * 未保存服务端地址时返回 false.
     */
    fun reconnectNow(): Boolean {
        if (ConnectionKeys.SERVER_ADDRESS.get().isBlank()) return false
        if (service.isJsonSocketClientConnected || JsonSocketClient.cxnState.value?.isConnecting() == true) return true
        // An explicit request to connect overrides an earlier explicit disconnect (here or in VSCode).
        // zh-CN: 显式的连接请求覆盖此前 (本地或 VSCode 中) 的显式断开.
        service.isClientSocketNormallyClosed = false
        isArmed = true
        attempts = 0
        sessionStartedAt = 0L
        attempt()
        return true
    }

    /**
     * Launch restore, called by the drawer on every Activity creation: a no-op while a session, an attempt
     * or a connection already exists. zh-CN: 启动时恢复, 抽屉在每次 Activity 创建时调用: 已有会话, 尝试或连接时不做任何事.
     */
    fun restoreOnLaunch() {
        if (isActive) return
        reconnectNow()
    }

    /** Skip the pending delay. zh-CN: 跳过等待, 立即尝试. */
    fun retryNow() {
        if (isActive && !isAttemptInFlight) attempt()
    }

    /** Stop the current session; a later unexpected disconnect starts a new one. zh-CN: 停止当前会话; 之后的意外断开会开始新会话. */
    fun cancel() = finish()

    /** The person disconnected explicitly: stay quiet until a new handshake succeeds. zh-CN: 用户显式断开: 保持安静直至新的握手成功. */
    fun onUserDisconnect() {
        isArmed = false
        finish()
    }

    private fun finish() {
        handler.removeCallbacks(retryRunnable)
        unregisterNetworkCallback()
        attempts = 0
        sessionStartedAt = 0L
        isAttemptInFlight = false
        if (status.value != Status()) status.onNext(Status())
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null || !ConnectionKeys.CLIENT_MODE_RECONNECT_ON_NETWORK_CHANGE.get()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post { onNetworkAvailable() }
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onSuccess {
                networkCallback = callback
                networkCallbackRegisteredAt = System.currentTimeMillis()
            }
            .onFailure { Log.w(TAG, "Unable to register the network callback", it) }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { appContext.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }

    private fun onNetworkAvailable() {
        if (!isActive || isAttemptInFlight) return
        // Registration reports the current default network right away; that is not a change.
        // zh-CN: 注册后会立即报告当前默认网络, 这并非变化.
        if (System.currentTimeMillis() - networkCallbackRegisteredAt < NETWORK_CALLBACK_SETTLE_MILLIS) return
        Log.i(TAG, "Network available, retrying now")
        attempt()
    }

    companion object {

        private const val TAG = "DevPluginClientReconnector"

        private val BACKOFF_MILLIS = longArrayOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L)
        private const val LONG_SESSION_MILLIS = 10 * 60_000L
        private const val LONG_SESSION_DELAY_MILLIS = 60_000L
        private const val NETWORK_CALLBACK_SETTLE_MILLIS = 1_500L

    }

}
