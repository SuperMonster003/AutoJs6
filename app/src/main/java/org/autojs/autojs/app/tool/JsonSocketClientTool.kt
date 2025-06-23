package org.autojs.autojs.app.tool

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputFilter
import android.view.KeyEvent
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.extension.MaterialDialogExtensions.widgetThemeColor
import org.autojs.autojs.pluginclient.JsonSocketClient
import org.autojs.autojs.ui.main.drawer.DrawerMenuDisposableItem
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class JsonSocketClientTool(context: Context) : AbstractJsonSocketTool(context) {

    private var mClientModeItem: DrawerMenuDisposableItem? = null

    override val isConnected
        get() = devPlugin.isJsonSocketClientConnected

    private var isNormallyClosed
        get() = devPlugin.isClientSocketNormallyClosed
        set(state) {
            devPlugin.isClientSocketNormallyClosed = state
        }

    override val isInMainThread = true

    override fun connect(): Boolean {
        inputRemoteHost(isAutoConnect = !isNormallyClosed)
        // @Hint by SuperMonster003 on Nov 9, 2023.
        //  ! Method with showing a dialog always returns false.
        //  ! zh-CN: 含对话框显示的方法总是返回 false 值.
        return false
    }

    internal fun connectIfNotNormallyClosed() {
        if (!isNormallyClosed) connect()
    }

    internal fun setClientModeItem(clientModeItem: DrawerMenuDisposableItem) {
        mClientModeItem = clientModeItem
    }

    override fun disconnect(): Boolean {
        mClientModeItem?.subtitle = null
        val result = runCatching {
            devPlugin.jsonSocketClient?.switchOff()
        }.isSuccess
        isNormallyClosed = true
        return result
    }

    override fun dispose() {
        stateDisposable?.dispose()
    }

    @SuppressLint("CheckResult")
    private fun inputRemoteHost(isAutoConnect: Boolean) {
        val host = Pref.getServerAddress()
        if (isAutoConnect) {
            devPlugin
                .connectToRemoteServer(context, host, mClientModeItem, true)
                .subscribe(Observers.emptyConsumer(), Observers.emptyConsumer())
            return
        }
        MaterialDialog.Builder(context)
            .title(R.string.text_pc_server_address)
            .input(context.getString(R.string.text_pc_server_address), host) { dialog, _ ->
                connectToRemoteServer(dialog)
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
                        connectToRemoteServer(dialog)
                    }
                    .itemsLongCallback { dHistories, _, _, text ->
                        false.also {
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
                        }
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
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .autoDismiss(false)
            .dismissListener(onConnectionDialogDismissed)
            .show()
            .also { dialog: MaterialDialog ->
                dialog.setOnKeyListener { _, keyCode, _ ->
                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        connectToRemoteServer(dialog)
                        true
                    } else {
                        false
                    }
                }
                dialog.inputEditText!!.filters += InputFilter { source, start, end, dest, dstart, dend ->
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
    private fun connectToRemoteServer(dialog: MaterialDialog) {
        val input = dialog.inputEditText?.text?.toString() ?: ""
        if (!rexValidIp.matches(input)) {
            if (input.isEmpty()) {
                showSnack(dialog, R.string.error_ip_address_should_not_be_empty)
            } else {
                showSnack(dialog, R.string.error_invalid_ip_address)
            }
            return
        }
        dialog.dismiss()
        devPlugin
            .connectToRemoteServer(context, input, mClientModeItem)
            .subscribe({ Pref.setServerAddress(input) }, onConnectionException)
    }

    private fun showSnack(dialog: MaterialDialog, strRes: Int) {
        ViewUtils.showSnack(dialog.view, dialog.context.getString(strRes))
    }

    private fun isRepeatedCharacter(source: CharSequence, nearest: Char, regex: String): Boolean {
        return Regex(regex).matches(nearest.toString()) && Regex(regex).matches(source)
    }

    private fun triggerRepeatedCharacter(dialog: MaterialDialog, source: CharSequence, nearest: Char): Boolean {
        if (isRepeatedCharacter(source, nearest, REGEX_DOT)) {
            showSnack(dialog, R.string.error_repeated_dot_symbol)
            return true
        }
        if (isRepeatedCharacter(source, nearest, REGEX_COLON)) {
            showSnack(dialog, R.string.error_repeated_colon_symbol)
            return true
        }
        return false
    }

    companion object {

        const val REGEX_DOT = "[,.，。\\u0020\\u3000]"
        const val REGEX_COLON = "[:：]"

        private const val REGEX_IP_DEC = "\\d{1,3}"
        private const val REGEX_PORT = "\\d{1,5}"

        val rexPartialIp = Regex("^$REGEX_IP_DEC($REGEX_DOT($REGEX_IP_DEC($REGEX_DOT($REGEX_IP_DEC($REGEX_DOT($REGEX_IP_DEC)?)?)?)?)?)?")
        val rexFullIpWithColon = Regex("\\d+$REGEX_DOT\\d+$REGEX_DOT\\d+$REGEX_DOT\\d+$REGEX_COLON\\d*")
        val rexValidIp = Regex("$REGEX_IP_DEC$REGEX_DOT$REGEX_IP_DEC$REGEX_DOT$REGEX_IP_DEC$REGEX_DOT$REGEX_IP_DEC($REGEX_COLON$REGEX_PORT)?")
        val rexAcceptable = Regex("($REGEX_DOT|$REGEX_COLON|\\d)+")

    }

}