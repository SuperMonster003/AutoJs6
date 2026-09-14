package org.autojs.autojs.ui.terminal

import android.content.Context
import android.net.Uri
import android.text.InputType
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.terminal.NpmProjectScripts
import org.autojs.autojs.core.terminal.ShellQuoting
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File

/**
 * Menu-driven npm helpers that type ready-made commands into the terminal.
 *
 * @param typeCommand   writes one command line (without the trailing newline) and executes it
 * @param currentDirectory the shell's current working directory, when known
 *
 * zh-CN: 由菜单驱动的 npm 辅助动作, 向终端键入现成的命令.
 */
class TerminalNpmActions(
    private val context: Context,
    private val typeCommand: (String) -> Unit,
    private val currentDirectory: () -> File?,
) {

    fun npmInit() = typeCommand("npm init")

    fun npmInstall() = typeCommand("npm install")

    fun npmInstallPackage() {
        MaterialDialog.Builder(context)
            .title(R.string.text_terminal_npm_install_package)
            .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            .input(context.getString(R.string.text_terminal_package_name_hint), "", false) { dialog, input ->
                val packages = input.toString().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (packages.isNotEmpty()) {
                    typeCommand("npm install " + ShellQuoting.join(packages))
                }
                dialog.dismiss()
            }
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .widgetThemeColor()
            .show()
    }

    fun npmRunScript() {
        val directory = currentDirectory()
        val scripts = directory?.let(NpmProjectScripts::read)
        if (scripts == null) {
            ViewUtils.showToast(context, R.string.text_terminal_no_package_json)
            return
        }
        if (scripts.isEmpty()) {
            ViewUtils.showToast(context, R.string.text_terminal_no_npm_scripts)
            return
        }
        MaterialDialog.Builder(context)
            .title(R.string.text_terminal_npm_run_script)
            .items(scripts.map { "${it.name}\n${it.command}" })
            .itemsCallback { dialog, _, which, _ ->
                typeCommand("npm run " + ShellQuoting.quote(scripts[which].name))
                dialog.dismiss()
            }
            .negativeText(R.string.dialog_button_cancel)
            .widgetThemeColor()
            .show()
    }

    fun otherPackageManagers() {
        val commands = listOf("yarn --version", "pnpm --version")
        MaterialDialog.Builder(context)
            .title(R.string.text_terminal_other_package_managers)
            .content(R.string.text_terminal_other_package_managers_content)
            .items(commands)
            .itemsCallback { dialog, _, which, _ ->
                typeCommand(commands[which])
                dialog.dismiss()
            }
            .negativeText(R.string.dialog_button_cancel)
            .widgetThemeColor()
            .show()
    }

    fun searchNpm() {
        MaterialDialog.Builder(context)
            .title(R.string.text_terminal_search_npm)
            .inputType(InputType.TYPE_CLASS_TEXT)
            .input(context.getString(R.string.text_terminal_package_name_hint), "", true) { dialog, input ->
                val query = input.toString().trim()
                val url = if (query.isEmpty()) NPM_HOME else NPM_SEARCH + Uri.encode(query)
                IntentUtils.browse(context, url)
                dialog.dismiss()
            }
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .widgetThemeColor()
            .show()
    }

    companion object {
        private const val NPM_HOME = "https://www.npmjs.com/"
        private const val NPM_SEARCH = "https://www.npmjs.com/search?q="
    }

}
