package org.autojs.autojs.ui.terminal

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.terminal.TerminalSessionManager
import org.autojs.autojs.ui.devplugin.ConnectionManagerDialog
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R

/** Session controls share the layout and section behavior of the connection managers. */
class TerminalManagerDialog private constructor(
    context: Context,
    private val onTextSizeChanged: () -> Unit,
) : ConnectionManagerDialog(
    context,
    R.string.text_terminal_manager,
    R.string.key_terminal_manager_status_level,
    R.string.key_terminal_manager_control_level,
    R.string.key_terminal_manager_settings_collapsed,
    R.string.key_terminal_manager_sessions_level,
) {
    private val newSession = addAction(Action(R.string.text_terminal_new_session, R.drawable.ic_add_white_48dp, header = true) {
        dialog.dismiss()
        TerminalActivity.launchNew(context)
    })
    private val closeAll = addAction(Action(R.string.text_terminal_close_all_sessions, R.drawable.ic_stop_button_fill, header = true) {
        TerminalSessionManager.closeAll()
        refresh()
    })
    private val sessionListener: () -> Unit = { handler.post { if (dialog.isShowing) refresh() } }

    init {
        configureClientsSection(R.string.text_terminal_sessions, R.string.text_terminal_no_sessions, R.drawable.ic_terminal_black_48dp)
        addSetting(R.string.text_text_size, R.drawable.ic_text_fields_black_48dp) {
            TerminalSettingsDialogs.showTextSize(context) { onTextSizeChanged() }
        }
        addSetting(R.string.text_terminal_npm_registry, R.drawable.ic_settings_black_48dp) {
            TerminalSettingsDialogs.showNpmRegistry(context)
        }
        addSwitch(R.string.text_terminal_npm_ignore_scripts, R.string.summary_terminal_npm_ignore_scripts, R.string.key_terminal_npm_ignore_scripts)
    }

    override fun onShown() {
        super.onShown()
        TerminalSessionManager.addListener(sessionListener)
    }

    override fun onDismissed() {
        TerminalSessionManager.removeListener(sessionListener)
        super.onDismissed()
    }

    override fun refresh() {
        val sessions = TerminalSessionManager.activeSessions
        val status = getString(if (sessions.isEmpty()) R.string.text_stopped else R.string.text_terminal_running)
        setStatus(listOf(
            StatusRow(getString(R.string.text_label_state), status),
            StatusRow(getString(R.string.text_terminal_sessions), sessions.size.toString()),
        ), status)
        val now = System.currentTimeMillis()
        setClients(sessions.map { session ->
            val directory = session.pty.currentDirectory()?.path ?: session.initialDirectory
            ClientRow(R.drawable.ic_terminal_black_48dp, getString(R.string.text_terminal_session_title, session.id),
                "${session.paths.toTildePath(directory)}\nPID: ${session.pty.pid} | ${formatElapsed(now - session.createdAt)}") {
                showSession(session.id)
            }
        })
        setActionVisible(newSession, true)
        setActionVisible(closeAll, sessions.isNotEmpty())
    }

    private fun showSession(id: String) {
        val session = TerminalSessionManager.get(id)?.takeIf { it.pty.isProcessAlive } ?: return refresh()
        val directory = session.pty.currentDirectory()?.path ?: session.initialDirectory
        val info = "${getString(R.string.text_working_directory)}: $directory\nPID: ${session.pty.pid}\n" +
            "${getString(R.string.text_uptime)}: ${formatElapsed(System.currentTimeMillis() - session.createdAt)}"
        MaterialDialog.Builder(context)
            .title(getString(R.string.text_terminal_session_title, id))
            .content(info)
            .neutralText(R.string.dialog_button_copy)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ -> copyText(info) }
            .negativeText(R.string.text_terminal_close_session)
            .negativeColorRes(R.color.dialog_button_warn)
            .onNegative { _, _ -> TerminalSessionManager.close(id); refresh() }
            .positiveText(R.string.text_open)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ -> dialog.dismiss(); TerminalActivity.launchSession(context, id) }
            .widgetThemeColor()
            .show()
    }

    companion object {
        fun show(context: Context, onTextSizeChanged: () -> Unit = {}): MaterialDialog =
            TerminalManagerDialog(context, onTextSizeChanged).show()
    }
}
