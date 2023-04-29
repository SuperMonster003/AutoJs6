package org.autojs.autojs.app.tool

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputFilter
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.Observers
import org.autojs.autojs6.R

class JsonSocketClientTool(context: Context) : AbstractJsonSocketTool(context) {

    override val isConnected
        get() = devPlugin.isJsonSocketClientConnected

    override val isInMainThread = true

    override fun connect() = inputRemoteHost()

    override fun disconnect() = devPlugin.disconnectJsonSocketClient()

    override fun dispose() {
        stateDisposable?.dispose()
    }

    @SuppressLint("CheckResult")
    private fun inputRemoteHost() {
        val host = Pref.getServerAddress()
        MaterialDialog.Builder(context)
            .title(R.string.text_pc_server_address)
            .input(context.getString(R.string.text_pc_server_address), host) { _, input ->
                Pref.setServerAddress(input.toString())
                devPlugin
                    .connectToRemoteServer(input.toString())
                    .subscribe(Observers.emptyConsumer(), onConnectionException)
            }
            .neutralText(R.string.text_help)
            .negativeText(R.string.text_back)
            .onNeutral { _, _ -> IntentUtils.browse(context, context.getString(R.string.url_github_autojs6_vscode_extension_usage)) }
            .dismissListener(onConnectionDialogDismissed)
            .show()
            .also { dialog: MaterialDialog ->
                dialog.inputEditText!!.filters += InputFilter { source, start, end, dest, dstart, dend ->
                    val rexDot = "[, .，。]"
                    val rexNum = "\\d{1,3}"
                    val rexIp = Regex("^${rexNum}(${rexDot}(${rexNum}(${rexDot}(${rexNum}(${rexDot}(${rexNum})?)?)?)?)?)?")
                    if (end > start) {
                        val fullText = dest.substring(0, dstart) +
                                       source.subSequence(start, end) +
                                       dest.substring(dend)
                        if (!fullText.contains(rexIp)) {
                            return@InputFilter ""
                        }
                        fullText.split(rexDot.toRegex()).dropLastWhile { it.isEmpty() }.forEach { s ->
                            if (Integer.valueOf(s) > 255) {
                                return@InputFilter ""
                            }
                        }
                    }
                    return@InputFilter source.replace(rexDot.toRegex(), ".")
                }
            }
    }

}