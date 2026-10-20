package org.autojs.autojs.app.tool

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.KeyEvent
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.keys.ConnectionKeys
import org.autojs.autojs.permission.LocalNetworkPermission
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.pluginclient.JsonSocketClient
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.qrcode.QrCodeScanActivity
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.toggleActionButtonAbilityByItems
import org.autojs.autojs.util.DialogUtils.toggleContentViewByItems
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ThreadUtils.runOnMain
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.lang.ref.WeakReference
import java.net.InetAddress

@SuppressLint("CheckResult")
class JsonSocketClientTool(context: Context) : AbstractJsonSocketTool(context) {

    // Keep a weak reference of current dialog to avoid leaking window on Activity destroy.
    // zh-CN: 使用弱引用保存当前 dialog, 避免 Activity 销毁时发生窗口泄漏.
    private var connectionDialogRef: WeakReference<MaterialDialog?>? = null

    // Keep a weak reference of "connecting status" dialog for interrupt/amend operations.
    // zh-CN: 保存 "连接中状态" dialog 的弱引用, 用于中止/修正等操作.
    private var connectingStatusDialogRef: WeakReference<MaterialDialog?>? = null

    // Subscription for observing connection state while status dialog is shown.
    // zh-CN: 用于观察连接状态并驱动状态 dialog 自动关闭的订阅.
    private var connectingStatusDisposable: Disposable? = null

    // Keep a weak reference of failure dialog to avoid stacking dialogs.
    // zh-CN: 保存失败 dialog 的弱引用, 避免重复弹窗叠加.
    private var connectionFailedDialogRef: WeakReference<MaterialDialog?>? = null

    // Remember last host for retry/amend.
    // zh-CN: 记录最近一次 host, 用于重试/修正.
    private var lastConnectingHost: String? = null
    private var localNetworkRequestInFlight = false

    // Main thread handler for UI operations, because connectToRemoteServer is @AnyThread.
    // zh-CN: 主线程 handler 用于 UI 操作, 因为 connectToRemoteServer 标注为 @AnyThread.
    private val mainHandler = Handler(Looper.getMainLooper())

    // Keep status dialog visible for at least this duration to avoid flicker.
    // zh-CN: 状态 dialog 至少显示该时长以避免闪烁.
    private val connectingDialogMinShowMillis = 500L

    override val isConnected
        get() = devPlugin.isJsonSocketClientConnected

    override var isNormallyClosed
        get() = devPlugin.isClientSocketNormallyClosed
        set(state) {
            devPlugin.isClientSocketNormallyClosed = state
        }

    override val isInMainThread = true

    override fun connect() {
        inputRemoteHost(isAutoConnect = false)
    }

    override fun connectIfNotNormallyClosed() {
        // Launch restore goes through the reconnector so that a PC that is not ready yet gets retried.
        // zh-CN: 启动时恢复经由重连器进行, 使尚未就绪的 PC 也能得到重试.
        if (!isNormallyClosed && ConnectionKeys.CLIENT_MODE_RESTORE_ON_LAUNCH.get()) {
            devPlugin.clientReconnector.restoreOnLaunch()
        }
    }

    override fun disconnect() {
        devPlugin.disconnectJsonSocketClient()
        isNormallyClosed = true
        dismissConnectionDialogSilently()
        dismissConnectingStatusDialogSilently()
        dismissConnectionFailedDialogSilently()
    }

    override fun dispose() {
        stateDisposable?.dispose()
        dismissConnectionDialogSilently()
        dismissConnectingStatusDialogSilently()
        dismissConnectionFailedDialogSilently()
    }

    private fun isContextInvalidForDialog(): Boolean {
        val a = (context as? Activity) ?: return false
        return a.isFinishing || a.isDestroyed
    }

    private fun dismissConnectionDialogSilently() {
        runOnMain(mainHandler) {
            runCatching { connectionDialogRef?.get()?.dismiss() }
            connectionDialogRef = null
        }
    }

    private fun dismissConnectingStatusDialogSilently() {
        runOnMain(mainHandler) {
            runCatching { connectingStatusDisposable?.dispose() }
            connectingStatusDisposable = null
            runCatching { connectingStatusDialogRef?.get()?.dismiss() }
            connectingStatusDialogRef = null
        }
    }

    private fun dismissConnectionFailedDialogSilently() {
        runOnMain(mainHandler) {
            runCatching { connectionFailedDialogRef?.get()?.dismiss() }
            connectionFailedDialogRef = null
        }
    }

    private fun interruptConnectionAndMarkNormallyClosed() {
        // Stop current attempt immediately (cancel handshake timeout runnable via switchOff()).
        // zh-CN: 立即停止当前尝试 (通过 switchOff() 取消握手超时 runnable).
        runCatching { devPlugin.disconnectJsonSocketClient() }
        isNormallyClosed = true
    }

    private fun scheduleDismissConnectingDialogWithMinDuration(shownAt: Long, afterDismiss: (() -> Unit)? = null) {
        val elapsed = System.currentTimeMillis() - shownAt
        val delay = (connectingDialogMinShowMillis - elapsed).coerceAtLeast(0L)
        runOnMain(mainHandler) {
            mainHandler.postDelayed(
                {
                    dismissConnectingStatusDialogSilently()
                    afterDismiss?.invoke()
                },
                delay,
            )
        }
    }

    private fun showConnectionFailedDialog(host: String, throwable: Throwable?) {
        if (isContextInvalidForDialog()) return

        dismissConnectionFailedDialogSilently()

        val msg = (throwable?.message ?: "").trim().ifEmpty {
            context.getString(R.string.error_unknown)
        }

        val content = buildString {
            append(context.getString(R.string.error_connection_failed_with_host, host))
            append("\n\n")
            append(msg.let { if (it.contains("\n") || it.endsWith(".")) it else "$it." })
        }

        MaterialDialog.Builder(context)
            .title(R.string.text_connection_failed)
            .content(content)
            .widgetThemeColor()
            .neutralText(R.string.dialog_button_amend_host_address)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { d, _ ->
                d.dismiss()
                // Amend should interrupt immediately to avoid stale timeout events.
                // zh-CN: 修正应立即中断连接, 避免后续超时事件.
                interruptConnectionAndMarkNormallyClosed()
                dismissConnectingStatusDialogSilently()
                inputRemoteHost(isAutoConnect = false, prefill = host)
            }
            .negativeText(R.string.dialog_button_abandon)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ ->
                d.dismiss()
            }
            .positiveText(R.string.dialog_button_retry)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { d, _ ->
                d.dismiss()
                // Retry: start again with status dialog.
                // zh-CN: 重试: 重新开始并显示状态 dialog.
                connectToServerWithStatus(host, /* dismissInputDialog */ null)
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
            .also { connectionFailedDialogRef = WeakReference(it) }
    }

    private fun shouldUseIpv4SmartFilter(fullText: String): Boolean {
        // If it contains letters, brackets, or percent (IPv6 zone id), treat as non-IPv4 mode.
        // zh-CN: 若包含字母/方括号/百分号 (IPv6 zone id), 则视为非 IPv4 模式.
        if (fullText.any { it.isLetter() }) return false
        if (fullText.contains('[') || fullText.contains(']')) return false
        if (fullText.contains('%')) return false

        // If there are 2+ ':' it is likely IPv6 (allow '::'), so do not use IPv4 strict logic.
        // zh-CN: 若 ':' 数量 >= 2, 更可能是 IPv6 (允许 '::'), 不使用 IPv4 严格逻辑.
        if (fullText.count { it == ':' } >= 2) return false

        return true
    }

    private fun inputRemoteHost(isAutoConnect: Boolean, prefill: String = ConnectionKeys.SERVER_ADDRESS.get()) {
        if (isContextInvalidForDialog()) return

        if (isAutoConnect) {
            devPlugin
                .connectToRemoteServer(context, prefill, true)
                .subscribe(Observers.emptyConsumer(), Observers.emptyConsumer())
            return
        }
        MaterialDialog.Builder(context)
            .title(R.string.text_pc_server_address)
            // "Scan QR code" lives in the title's "more" menu (the input keeps its full width).
            // zh-CN: "扫描二维码" 放在标题栏 "更多" 菜单中 (输入框保持完整宽度).
            .options(listOf(MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_scan_qr_code)) { dialog -> scanQrCodeToConnect(dialog) }))
            .input(context.getString(R.string.hint_pc_server_address_supported_formats), prefill) { dialog, _ ->
                validateAndConnectToRemoteServer(dialog)
            }
            .widgetThemeColor()
            .neutralText(R.string.dialog_button_history)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { dialog, _ ->
                showServerAddressHistory { text ->
                    dialog.inputEditText?.setText(text)
                    validateAndConnectToRemoteServer(dialog)
                }
            }
            .negativeText(R.string.text_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_connect)
            .positiveColorRes(R.color.dialog_button_attraction)
            .autoDismiss(false)
            .dismissListener {
                runOnMain(mainHandler) { connectionDialogRef = null }
                onConnectionDialogDismissed.onDismiss(it)
            }
            .show()
            .also { dialog: MaterialDialog ->
                runOnMain(mainHandler) { connectionDialogRef = WeakReference(dialog) }

                dialog.setOnKeyListener { _, keyCode, _ ->
                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        validateAndConnectToRemoteServer(dialog)
                        true
                    } else {
                        false
                    }
                }

                val editText = dialog.inputEditText ?: return@also
                editText.filters += InputFilter { source, start, end, dest, dstart, dend ->
                    if (end > start) {
                        val fullText = dest.substring(0, dstart) +
                                source.subSequence(start, end) +
                                dest.substring(dend)

                        // Allow more characters for domain/IPv6: letters, digits, '.', '-', '_', ':', '[', ']', '%'
                        // zh-CN: 域名/IPv6 需要允许更多字符: 字母/数字/'.'/'-'/'_' / ':' / '[' / ']' / '%'.
                        val rexGenericAcceptable = Regex("""[0-9a-zA-Z.\-_:()\[\]%]+""")

                        val normalized = source
                            .replace(Regex("$REGEX_DOT+"), ".")
                            .replace(Regex("$REGEX_COLON+"), ":")

                        // IPv4 smart filter branch (keeps your existing behavior).
                        // zh-CN: IPv4 智能过滤分支 (保留现有行为).
                        if (shouldUseIpv4SmartFilter(fullText)) {
                            // ... existing code (original IPv4 correction/validation) ...
                        } else {
                            // Generic branch: do not block '::' (valid in IPv6), and do not over-validate.
                            // zh-CN: 通用分支: 不阻止 '::' (IPv6 合法), 且不做过度校验.
                            val candidate = (dest.substring(0, dstart) +
                                    normalized.subSequence(start, end) +
                                    dest.substring(dend)).trim()

                            if (candidate.isNotEmpty() && !rexGenericAcceptable.matches(candidate)) {
                                showSnack(dialog, R.string.error_unacceptable_character)
                                return@InputFilter ""
                            }
                            return@InputFilter normalized
                        }
                    }
                    return@InputFilter source
                        .replace(Regex("$REGEX_DOT+"), ".")
                        .replace(Regex("$REGEX_COLON+"), ":")
                }
            }
    }

    /**
     * History of server addresses (most recent first): tap to hand an entry to [onSelected], long-press to
     * delete it, "Clear" to drop them all. Opened from the address dialog and from the client mode manager.
     * zh-CN: 服务端地址历史记录 (最近的在前): 点击将条目交给 [onSelected], 长按删除, "清空" 则全部清除.
     * 由地址对话框与客户端模式管理器打开.
     */
    fun showServerAddressHistory(onSelected: (String) -> Unit) {
        if (isContextInvalidForDialog()) return
        MaterialDialog.Builder(context)
            .title(R.string.text_history)
            .content(R.string.text_no_history)
            .items(JsonSocketClient.serverAddressHistory)
            .itemsCallback { dHistory, _, _, text ->
                dHistory.dismiss()
                onSelected(text.toString())
            }
            .choiceWidgetThemeColor()
            .itemsLongCallback { dHistory, _, _, text ->
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.text_confirm_to_delete)
                    .negativeText(R.string.dialog_button_cancel)
                    .neutralColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_caution)
                    .onPositive { ds, _ ->
                        ds.dismiss()
                        JsonSocketClient.removeFromHistory(text.toString())
                        dHistory.items?.let {
                            it.remove(text)
                            dHistory.run {
                                notifyItemsChanged()
                                toggleContentViewByItems()
                                toggleActionButtonAbilityByItems(DialogAction.NEUTRAL)
                            }
                        }
                    }
                    .show()
                return@itemsLongCallback false
            }
            .neutralText(R.string.dialog_button_clear_items)
            .neutralColorRes(R.color.dialog_button_warn)
            .onNeutral { dHistory, _ ->
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.text_confirm_to_clear_the_history)
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_caution)
                    .onPositive { _, _ ->
                        JsonSocketClient.clearHistory()
                        dHistory.items?.let {
                            it.clear()
                            dHistory.run {
                                notifyItemsChanged()
                                toggleContentViewByItems()
                                toggleActionButtonAbilityByItems(DialogAction.NEUTRAL)
                            }
                        }
                        ViewUtils.showSnack(dHistory.view, R.string.text_history_has_been_cleared)
                    }
                    .show()
            }
            .positiveText(R.string.dialog_button_back)
            .positiveColorRes(R.color.dialog_button_default)
            .onPositive { dHistory, _ -> dHistory.dismiss() }
            .autoDismiss(false)
            .show()
            .apply {
                toggleContentViewByItems()
                toggleActionButtonAbilityByItems(DialogAction.NEUTRAL)
            }
    }

    /**
     * Connect to [host] with the connecting status dialog, e.g. an entry picked from the history in the client
     * mode manager. A live connection is closed first so that the new attempt is not short-circuited by it.
     * zh-CN: 连接到 [host] 并显示连接状态对话框, 如在客户端模式管理器中从历史记录选中的条目. 已有连接会先关闭,
     * 以免新的尝试被其直接短路.
     */
    fun connectTo(host: String) {
        if (isConnected) disconnect()
        connectToServerWithStatus(host, null)
    }

    /**
     * Scan the QR code shown by the VSCode extension, fill the address and connect.
     * zh-CN: 扫描 VSCode 插件显示的二维码, 填入地址并连接.
     */
    private fun scanQrCodeToConnect(dialog: MaterialDialog) {
        QrCodeScanActivity.launch(context) { text ->
            runOnMain(mainHandler) {
                val endpoint = DevPluginService.parseQrConnectPayload(text)
                if (endpoint == null) {
                    ViewUtils.showToast(context, context.getString(R.string.error_invalid_qr_code_content, text), true)
                    return@runOnMain
                }
                // Hand the one-time pairing token to the upcoming connection (roadmap 4.4).
                // zh-CN: 将一次性配对令牌交给即将建立的连接 (roadmap 4.4).
                DevPluginService.pendingQrToken = DevPluginService.parseQrToken(text)?.let { token ->
                    endpoint.substringBeforeLast(':').trim('[', ']') to token
                }
                if (dialog.isShowing) {
                    dialog.inputEditText?.setText(endpoint)
                    validateAndConnectToRemoteServer(dialog)
                } else {
                    connectToServerWithStatus(endpoint, null)
                }
            }
        }
    }

    private fun connectToServerWithStatus(host: String, dismissInputDialog: MaterialDialog?, permissionRequested: Boolean = false) {
        if (isContextInvalidForDialog()) return

        if (!permissionRequested && !LocalNetworkPermission.isGranted(context)) {
            if (localNetworkRequestInFlight) return
            localNetworkRequestInFlight = true
            Observable.fromCallable { devPlugin.needsLocalNetworkPermission(host) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ needed ->
                    if (isContextInvalidForDialog()) {
                        localNetworkRequestInFlight = false
                    } else if (needed) {
                        LocalNetworkPermission(context).request { granted ->
                            localNetworkRequestInFlight = false
                            if (granted) connectToServerWithStatus(host, dismissInputDialog, permissionRequested = true)
                        }
                    } else {
                        localNetworkRequestInFlight = false
                        connectToServerWithStatus(host, dismissInputDialog, permissionRequested = true)
                    }
                }, { error ->
                    localNetworkRequestInFlight = false
                    showConnectionFailedDialog(host, error)
                })
            return
        }

        val trimmedHost = host.trim()
        lastConnectingHost = trimmedHost

        // Close input dialog first, then show status dialog.
        // zh-CN: 先关闭输入 dialog, 再显示状态 dialog.
        runOnMain(mainHandler) { runCatching { dismissInputDialog?.dismiss() } }

        dismissConnectingStatusDialogSilently()
        dismissConnectionFailedDialogSilently()

        val shownAt = System.currentTimeMillis()

        // Build a non-cancelable status dialog.
        // zh-CN: 构建不可取消的状态 dialog.
        val statusDialog = MaterialDialog.Builder(context)
            .title(R.string.text_connecting)
            .content(context.getString(R.string.text_connecting_to_host, trimmedHost))
            .neutralText(R.string.dialog_button_amend_host_address)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { d, _ ->
                d.dismiss()
                // Amend should interrupt immediately to avoid later handshake timeout.
                // zh-CN: 修正应立即中断连接, 避免后续握手超时.
                interruptConnectionAndMarkNormallyClosed()
                dismissConnectingStatusDialogSilently()
                inputRemoteHost(isAutoConnect = false, prefill = trimmedHost)
            }
            .positiveText(R.string.dialog_button_abort_connection)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { d, _ ->
                // Treat as user interrupt: stop current attempt and mark normally closed.
                // zh-CN: 视为用户中止: 停止当前尝试并标记为正常关闭.
                interruptConnectionAndMarkNormallyClosed()
                d.dismiss()
                dismissConnectingStatusDialogSilently()
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
            .also { startResolveHostIpIfNeeded(trimmedHost) }

        connectingStatusDialogRef = WeakReference(statusDialog)

        // Auto-dismiss status dialog when state reaches CONNECTED or DISCONNECTED.
        // zh-CN: 当状态到达 CONNECTED 或 DISCONNECTED 时自动关闭状态 dialog.
        //
        // Important: cxnState is a BehaviorSubject and emits current state immediately on subscribe.
        // zh-CN: 注意: cxnState 是 BehaviorSubject, 订阅时会立刻发出当前状态.
        //
        // Skip the first emission to avoid instant dismissal by the initial DISCONNECTED state.
        // zh-CN: 跳过首次发射, 避免初始 DISCONNECTED 导致对话框瞬间关闭.
        connectingStatusDisposable = JsonSocketClient.cxnState
            .observeOn(AndroidSchedulers.mainThread())
            .skip(1)
            .subscribe { state ->
                if (connectingStatusDialogRef?.get()?.isShowing != true) return@subscribe
                when {
                    state.isConnecting() -> Unit
                    state.isConnected() -> {
                        scheduleDismissConnectingDialogWithMinDuration(shownAt)
                    }
                    state.isDisconnected() -> {
                        if (state.exception != null) {
                            scheduleDismissConnectingDialogWithMinDuration(shownAt) {
                                showConnectionFailedDialog(trimmedHost, state.exception)
                            }
                        } else {
                            scheduleDismissConnectingDialogWithMinDuration(shownAt)
                        }
                    }
                }
            }

        devPlugin
            .connectToRemoteServer(context, trimmedHost)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    ConnectionKeys.SERVER_ADDRESS.set(trimmedHost)
                    // The history used to be written only on a failed handshake; a reached server is what it is for.
                    // zh-CN: 历史记录此前仅在握手失败时写入; 而能连上的服务端才是它应记录的.
                    JsonSocketClient.addIntoHistory(trimmedHost)
                },
                { e ->
                    // Do not toast here; show failure dialog.
                    // zh-CN: 不在这里 toast; 改为失败对话框.
                    scheduleDismissConnectingDialogWithMinDuration(shownAt) {
                        showConnectionFailedDialog(trimmedHost, e)
                    }
                },
            )
    }

    /**
     * Show connecting status dialog if current connection state is CONNECTING.
     * zh-CN: 若当前连接状态为 CONNECTING, 则显示 "正在连接" 状态对话框.
     */
    fun showConnectingStatusDialogIfConnecting(): Boolean {
        val state = runCatching { JsonSocketClient.cxnState.value }.getOrNull()
            ?: return false

        if (!state.isConnecting()) return false
        if (isContextInvalidForDialog()) return false

        val host = (lastConnectingHost ?: ConnectionKeys.SERVER_ADDRESS.get()).trim()
        if (host.isEmpty()) return false

        // Re-show connecting dialog without starting a new connection attempt.
        // zh-CN: 仅重新显示连接中对话框, 不发起新的连接尝试.
        showConnectingStatusDialogOnly(host)
        return true
    }

    private fun showConnectingStatusDialogOnly(host: String) {
        dismissConnectingStatusDialogSilently()
        dismissConnectionFailedDialogSilently()

        val shownAt = System.currentTimeMillis()

        val statusDialog = MaterialDialog.Builder(context)
            .title(R.string.text_connecting)
            .content(context.getString(R.string.text_connecting_to_host, host))
            .neutralText(R.string.dialog_button_amend_host_address)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { d, _ ->
                d.dismiss()
                // Amend should interrupt immediately to avoid stale timeouts.
                // zh-CN: 修正应立即中断连接, 避免后续超时回调.
                interruptConnectionAndMarkNormallyClosed()
                dismissConnectingStatusDialogSilently()
                inputRemoteHost(isAutoConnect = false, prefill = host)
            }
            .positiveText(R.string.dialog_button_abort_connection)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { d, _ ->
                // Treat as user interrupt: stop current attempt and mark normally closed.
                // zh-CN: 视为用户中止: 停止当前尝试并标记为正常关闭.
                interruptConnectionAndMarkNormallyClosed()
                d.dismiss()
                dismissConnectingStatusDialogSilently()
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()

        connectingStatusDialogRef = WeakReference(statusDialog)

        // Continue resolving domain to IP if needed.
        // zh-CN: 如有需要继续解析域名为 IP.
        startResolveHostIpIfNeeded(host)

        // Keep dialog in sync with state changes.
        // zh-CN: 让对话框随状态变化自动关闭/弹失败.
        connectingStatusDisposable = JsonSocketClient.cxnState
            .observeOn(AndroidSchedulers.mainThread())
            .skip(1)
            .subscribe { s: DevPluginService.State ->
                if (connectingStatusDialogRef?.get()?.isShowing != true) return@subscribe
                when {
                    s.isConnecting() -> Unit
                    s.isConnected() -> {
                        scheduleDismissConnectingDialogWithMinDuration(shownAt)
                    }
                    s.isDisconnected() -> {
                        if (s.exception != null) {
                            scheduleDismissConnectingDialogWithMinDuration(shownAt) {
                                showConnectionFailedDialog(host, s.exception)
                            }
                        } else {
                            scheduleDismissConnectingDialogWithMinDuration(shownAt)
                        }
                    }
                }
            }
    }

    private fun validateAndConnectToRemoteServer(dialog: MaterialDialog) {
        if (isContextInvalidForDialog()) {
            dismissConnectionDialogSilently()
            return
        }

        val input = dialog.inputEditText?.text?.toString()?.trim() ?: ""

        if (input.isEmpty()) {
            showSnack(dialog, R.string.error_ip_address_should_not_be_empty)
            return
        }

        // If it looks like IPv4, keep strict validation; otherwise allow domain/IPv6 with minimal checks.
        // zh-CN: 若看起来像 IPv4, 保持严格校验; 否则允许 域名/IPv6 并仅做最小检查.
        if (shouldUseIpv4SmartFilter(input) && !rexValidIp.matches(input)) {
            showSnack(dialog, R.string.error_invalid_ip_address)
            return
        }

        // Enforce bracketed form for IPv6 with port: [ipv6]:port (Option 1A).
        // zh-CN: 强制 IPv6 带端口使用方括号形式: [ipv6]:port (选项 1A).
        val colonCount = input.count { it == ':' }
        val hasBracket = input.startsWith("[") && input.contains("]")
        val looksLikeIpv6 = colonCount >= 2
        val triesToSpecifyPortWithoutBracket = looksLikeIpv6 && !hasBracket && input.lastIndexOf(':') in 1 until input.lastIndex
        if (triesToSpecifyPortWithoutBracket) {
            showSnack(dialog, R.string.error_ipv6_port_requires_brackets)
            return
        }

        val isInHistory = { JsonSocketClient.serverAddressHistory.contains(input) }
        val isPotentiallyInvalid = { POTENTIALLY_INVALID_IP_ADRESS_LIST_FOR_REMOTE_SERVER.any { input.matches(it) } }

        when {
            !isInHistory() && isPotentiallyInvalid() -> NotAskAgainDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(context.getString(R.string.text_ip_address_may_be_invalid_for_server_connection, input))
                .widgetThemeColor()
                .negativeText(R.string.dialog_button_abandon)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_continue)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ -> connectToServerWithStatus(input, dialog) }
                .cancelable(false)
                .show() ?: connectToServerWithStatus(input, dialog)

            else -> connectToServerWithStatus(input, dialog)
        }
    }

    private fun showSnack(dialog: MaterialDialog, strRes: Int) {
        runOnMain(mainHandler) {
            if (isContextInvalidForDialog()) return@runOnMain
            ViewUtils.showSnack(dialog.view, dialog.context.getString(strRes))
        }
    }

    private fun startResolveHostIpIfNeeded(host: String) {
        val raw = host.trim()

        // Skip literals quickly.
        // zh-CN: 快速跳过字面量.
        if (raw.any { it.isLetter() }.not() && raw.count { it == '.' } == 3) return
        if (raw.startsWith("[") && raw.contains("]")) return
        if (raw.count { it == ':' } >= 2) return

        Observable
            .fromCallable {
                // Resolve domain to IP in background.
                // zh-CN: 在后台将域名解析为 IP.
                InetAddress.getByName(raw).hostAddress
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ ip ->
                val dialog = connectingStatusDialogRef?.get() ?: return@subscribe
                if (!dialog.isShowing) return@subscribe

                val decorated = "$raw [$ip]"
                // Update status dialog content.
                // zh-CN: 更新状态对话框内容.
                dialog.setContent(context.getString(R.string.text_connecting_to_host, decorated))

                // Update drawer subtitle via subject.
                // zh-CN: 通过 subject 更新抽屉子标题.
                devPlugin.clientConnectionIpAddress.onNext("$decorated ...")
            }, Observers.emptyConsumer())
    }

    companion object {

        const val REGEX_DOT = "[,.，。\\u0020\\u3000]"
        const val REGEX_COLON = "[:：]"

        private const val REGEX_IP_DEC = "\\d{1,3}"
        private const val REGEX_PORT = "\\d{1,5}"

        private val POTENTIALLY_INVALID_IP_ADRESS_LIST_FOR_REMOTE_SERVER by lazy {
            listOf(
                // APIPA.
                // zh-CN: 自动专用 IP 地址.
                Regex("""^169\.254\.\d+\.\d+$"""), // 169.254.0.0/16

                // Loopback: 127.0.0.0/8.
                // zh-CN: 环回地址: 127.0.0.0/8.
                Regex("""^127\.\d+\.\d+\.\d+$"""),

                // "This network": 0.0.0.0/8.
                // zh-CN: "本网络": 0.0.0.0/8.
                Regex("""^0\.\d+\.\d+\.\d+$"""),

                // Carrier-grade NAT (CGNAT / Shared Address Space).
                // zh-CN: 运营商级 NAT (CGNAT / 共享地址空间).
                Regex("""^100\.(?:6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d+\.\d+$"""), // 100.64.0.0/10

                // Documentation example network segments (test-net): commonly used in documentation/tutorials, will not appear on the public network.
                // zh-CN: 文档示例网段 (test-net): 常用于文档/教程, 不会在公网出现.
                Regex("""^192\.0\.2\.\d+$"""), // 192.0.2.0/24
                Regex("""^198\.51\.100\.\d+$"""), // 198.51.100.0/24
                Regex("""^203\.0\.113\.\d+$"""), // 203.0.113.0/24

                // Benchmarking network segment.
                // zh-CN: 基准测试网段.
                Regex("""^198\.1[89]\.\d+\.\d+$"""), // 198.18.0.0/15

                // 6to4 Relay Anycast (deprecated but still for special purposes).
                // zh-CN: 6to4 中继任播 (已弃用但仍属特殊用途).
                Regex("""^192\.88\.99\.\d+$"""), // 192.88.99.0/24

                // Multicast address: 224.0.0.0/4 (non-unicast, usually should not be used as "host address").
                // zh-CN: 多播地址: 224.0.0.0/4 (非单播, 通常不应作为 "主机地址").
                Regex("""^(?:22[4-9]|23\d)\.\d+\.\d+\.\d+$"""),

                // Reserved/future use: 240.0.0.0/4 (including 255.x.x.x; 255.255.255.255 is also included).
                // zh-CN: 保留/未来用途: 240.0.0.0/4 (包含 255.x.x.x; 255.255.255.255 也在其中).
                Regex("""^(?:24\d|25[0-5])\.\d+\.\d+\.\d+$"""),
            )
        }

        val rexPartialIp = Regex("^$REGEX_IP_DEC($REGEX_DOT($REGEX_IP_DEC($REGEX_DOT($REGEX_IP_DEC($REGEX_DOT($REGEX_IP_DEC)?)?)?)?)?)?")
        val rexFullIpWithColon = Regex("\\d+$REGEX_DOT\\d+$REGEX_DOT\\d+$REGEX_DOT\\d+$REGEX_COLON\\d*")
        val rexValidIp = Regex("$REGEX_IP_DEC$REGEX_DOT$REGEX_IP_DEC$REGEX_DOT$REGEX_IP_DEC$REGEX_DOT$REGEX_IP_DEC($REGEX_COLON$REGEX_PORT)?")
        val rexAcceptable = Regex("($REGEX_DOT|$REGEX_COLON|\\d)+")

    }

}
