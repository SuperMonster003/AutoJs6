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
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.extension.MaterialDialogExtensions.widgetThemeColor
import org.autojs.autojs.pluginclient.JsonSocketClient
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.lang.ref.WeakReference

class JsonSocketClientTool(context: Context) : AbstractJsonSocketTool(context) {

    // Keep a weak reference of current dialog to avoid leaking window on Activity destroy.
    // zh-CN: 使用弱引用保存当前 dialog, 避免 Activity 销毁时发生窗口泄漏.
    private var connectionDialogRef: WeakReference<MaterialDialog?>? = null

    // Main thread handler for UI operations, because connectToRemoteServer is @AnyThread.
    // zh-CN: 主线程 handler 用于 UI 操作, 因为 connectToRemoteServer 标注为 @AnyThread.
    private val mainHandler = Handler(Looper.getMainLooper())

    override val isConnected
        get() = devPlugin.isJsonSocketClientConnected

    override var isNormallyClosed
        get() = devPlugin.isClientSocketNormallyClosed
        set(state) {
            devPlugin.isClientSocketNormallyClosed = state
        }

    override val isInMainThread = true

    override fun connect() {
        inputRemoteHost(isAutoConnect = !isNormallyClosed)
    }

    override fun connectIfNotNormallyClosed() {
        if (!isNormallyClosed) connect()
    }

    override fun disconnect() {
        devPlugin.jsonSocketClient?.switchOff()
        isNormallyClosed = true
        dismissConnectionDialogSilently()
    }

    override fun dispose() {
        stateDisposable?.dispose()
        dismissConnectionDialogSilently()
    }

    private fun isContextInvalidForDialog(): Boolean {
        val a = (context as? Activity) ?: return false
        return a.isFinishing || a.isDestroyed
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }

    private fun dismissConnectionDialogSilently() {
        runOnMainThread {
            runCatching {
                connectionDialogRef?.get()?.dismiss()
            }
            connectionDialogRef = null
        }
    }

    @SuppressLint("CheckResult")
    private fun inputRemoteHost(isAutoConnect: Boolean) {
        if (isContextInvalidForDialog()) return

        val host = Pref.getServerAddress()
        if (isAutoConnect) {
            devPlugin
                .connectToRemoteServer(context, host, true)
                .subscribe(Observers.emptyConsumer(), Observers.emptyConsumer())
            return
        }
        MaterialDialog.Builder(context)
            .title(R.string.text_pc_server_address)
            .input(context.getString(R.string.text_pc_server_address), host) { dialog, _ ->
                validateAndConnectToRemoteServer(dialog)
            }
            .widgetThemeColor()
            .neutralText(R.string.dialog_button_history)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { dialog, _ ->
                MaterialDialog.Builder(context)
                    .title(R.string.text_histories)
                    .content(R.string.text_no_histories)
                    .items(JsonSocketClient.serverAddressHistories)
                    .itemsCallback { dHistories, _, _, text ->
                        dHistories.dismiss()
                        dialog.inputEditText?.setText(text)
                        validateAndConnectToRemoteServer(dialog)
                    }
                    .itemsLongCallback { dHistories, _, _, text ->
                        MaterialDialog.Builder(context)
                            .title(R.string.text_prompt)
                            .content(R.string.text_confirm_to_delete)
                            .negativeText(R.string.dialog_button_cancel)
                            .neutralColorRes(R.color.dialog_button_default)
                            .positiveText(R.string.dialog_button_confirm)
                            .positiveColorRes(R.color.dialog_button_caution)
                            .onPositive { ds, _ ->
                                ds.dismiss()
                                JsonSocketClient.removeFromHistories(text.toString())
                                dHistories.items?.let {
                                    it.remove(text)
                                    dHistories.notifyItemsChanged()
                                    DialogUtils.toggleContentViewByItems(dHistories)
                                    DialogUtils.toggleActionButtonAbilityByItems(dHistories, DialogAction.NEUTRAL)
                                }
                            }
                            .show()
                        return@itemsLongCallback false
                    }
                    .neutralText(R.string.dialog_button_clear_items)
                    .neutralColorRes(R.color.dialog_button_warn)
                    .onNeutral { dHistories, _ ->
                        MaterialDialog.Builder(context)
                            .title(R.string.text_prompt)
                            .content(R.string.text_confirm_to_clear_all_histories)
                            .negativeText(R.string.dialog_button_cancel)
                            .negativeColorRes(R.color.dialog_button_default)
                            .positiveText(R.string.dialog_button_confirm)
                            .positiveColorRes(R.color.dialog_button_caution)
                            .onPositive { _, _ ->
                                JsonSocketClient.clearAllHistories()
                                dHistories.items?.let {
                                    it.clear()
                                    dHistories.notifyItemsChanged()
                                    DialogUtils.toggleContentViewByItems(dHistories)
                                    DialogUtils.toggleActionButtonAbilityByItems(dHistories, DialogAction.NEUTRAL)
                                }
                                ViewUtils.showSnack(dHistories.view, R.string.text_all_histories_cleared)
                            }
                            .show()
                    }
                    .positiveText(R.string.dialog_button_back)
                    .positiveColorRes(R.color.dialog_button_default)
                    .onPositive { dHistories, _ -> dHistories.dismiss() }
                    .autoDismiss(false)
                    .show()
                    .also {
                        DialogUtils.toggleContentViewByItems(it)
                        DialogUtils.toggleActionButtonAbilityByItems(it, DialogAction.NEUTRAL)
                    }
            }
            .negativeText(R.string.text_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_connect)
            .positiveColorRes(R.color.dialog_button_attraction)
            .autoDismiss(false)
            .dismissListener {
                runOnMainThread { connectionDialogRef = null }
                onConnectionDialogDismissed.onDismiss(it)
            }
            .show()
            .also { dialog: MaterialDialog ->
                runOnMainThread { connectionDialogRef = WeakReference(dialog) }

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
                        if (source.length > 1) /* Take source as copied from clipboard. */ {
                            val splitTextSegments = fullText.split(Regex("$REGEX_DOT+")).dropLastWhile { it.isEmpty() }
                            val newFullText = splitTextSegments
                                .slice(0..splitTextSegments.lastIndex.coerceIn(0..4))
                                .mapIndexed { idx, part ->
                                    part + when (idx) {
                                        splitTextSegments.lastIndex -> ""
                                        in 0..2 -> "."
                                        3 -> ":"
                                        else -> ""
                                    }
                                }
                                .joinToString("")
                            return@InputFilter newFullText.slice(dstart until newFullText.length - dend)
                        }
                        if (dstart > 0) {
                            if (triggerRepeatedCharacter(dialog, source, /* prevNearest */ dest[dstart - 1])) {
                                return@InputFilter ""
                            }
                            if (dest.length > dstart && triggerRepeatedCharacter(dialog, source, /* nextNearest */ dest[dstart])) {
                                return@InputFilter ""
                            }
                            if (source.matches(Regex("[\\u0020\\u3000]"))) {
                                val prevText = dest.substring(0, dstart)
                                if (prevText.matches(Regex("(\\d+\\.){3}\\d+"))) {
                                    return@InputFilter ":"
                                }
                            }
                        }
                        if (!rexAcceptable.matches(fullText)) {
                            showSnack(dialog, R.string.error_unacceptable_character)
                            return@InputFilter ""
                        }
                        if (!fullText.contains(rexPartialIp)) {
                            showSnack(dialog, R.string.error_invalid_ip_address)
                            return@InputFilter ""
                        }
                        if (!fullText.contains(Regex(REGEX_COLON))) {
                            fullText.split(Regex(REGEX_DOT)).dropLastWhile { it.isEmpty() }.forEach { s ->
                                if (s.toIntOrNull()?.let { it <= 255 } != true) {
                                    showSnack(dialog, R.string.error_dot_decimal_notation_num_over_255)
                                    return@InputFilter ""
                                }
                            }
                        } else {
                            if (!fullText.matches(rexFullIpWithColon)) {
                                if (!dest.substring(0, dstart).contains(Regex(REGEX_COLON)) && dend == dest.length) {
                                    showSnack(dialog, R.string.error_colon_must_follow_a_valid_ip_address)
                                } else {
                                    showSnack(dialog, R.string.error_invalid_ip_address)
                                }
                                return@InputFilter ""
                            }
                            fullText.split(Regex("$REGEX_DOT|$REGEX_COLON")).dropLastWhile { it.isEmpty() }.forEachIndexed { index, s ->
                                if (index < 4 && s.toIntOrNull()?.let { it <= 255 } != true) {
                                    showSnack(dialog, R.string.error_dot_decimal_notation_num_over_255)
                                    return@InputFilter ""
                                }
                                if (index >= 4 && s.toIntOrNull()?.let { it <= 65535 } != true) {
                                    showSnack(dialog, R.string.error_port_num_over_65535)
                                    return@InputFilter ""
                                }
                            }
                        }
                    }
                    return@InputFilter source
                        .replace(Regex("$REGEX_DOT+"), ".")
                        .replace(Regex("$REGEX_COLON+"), ":")
                }
            }
    }

    @SuppressLint("CheckResult")
    private fun validateAndConnectToRemoteServer(dialog: MaterialDialog) {
        if (isContextInvalidForDialog()) {
            dismissConnectionDialogSilently()
            return
        }

        val input = dialog.inputEditText?.text?.toString() ?: ""

        val isInHistory = { JsonSocketClient.serverAddressHistories.contains(input) }
        val isPotentiallyInvalid = { POTENTIALLY_INVALID_IP_ADRESS_LIST_FOR_REMOTE_SERVER.any { input.matches(it) } }

        fun connectToServer() = devPlugin
            .connectToRemoteServer(context, input)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { Pref.setServerAddress(input) },
                onConnectionException,
            )
            .also {
                runOnMainThread {
                    runCatching { dialog.dismiss() }
                    connectionDialogRef = null
                }
            }

        if (!rexValidIp.matches(input)) {
            when (input.isEmpty()) {
                true -> showSnack(dialog, R.string.error_ip_address_should_not_be_empty)
                else -> showSnack(dialog, R.string.error_invalid_ip_address)
            }
            return
        }
        when {
            !isInHistory() && isPotentiallyInvalid() -> NotAskAgainDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(context.getString(R.string.text_ip_address_may_be_invalid_for_server_connection, input))
                .widgetThemeColor()
                .negativeText(R.string.dialog_button_quit_connecting)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_continue_connecting)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ -> connectToServer() }
                .cancelable(false)
                .show() ?: connectToServer()
            else -> connectToServer()
        }
    }

    private fun showSnack(dialog: MaterialDialog, strRes: Int) {
        runOnMainThread {
            if (isContextInvalidForDialog()) return@runOnMainThread
            ViewUtils.showSnack(dialog.view, dialog.context.getString(strRes))
        }
    }

    private fun isRepeatedCharacter(source: CharSequence, nearest: Char, regex: String): Boolean {
        return Regex(regex).matches(nearest.toString()) && Regex(regex).matches(source)
    }

    private fun triggerRepeatedCharacter(dialog: MaterialDialog, source: CharSequence, nearest: Char) = when {
        isRepeatedCharacter(source, nearest, REGEX_DOT) -> {
            showSnack(dialog, R.string.error_repeated_dot_symbol)
            true
        }
        isRepeatedCharacter(source, nearest, REGEX_COLON) -> {
            showSnack(dialog, R.string.error_repeated_colon_symbol)
            true
        }
        else -> false
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