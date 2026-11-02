package org.autojs.autojs.core.plugin.center

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R

/** The prompt of [PluginStorageAccess], a window of its own because a script may run with no screen of AutoJs6 open. zh-CN: [PluginStorageAccess] 的提示, 用独立窗口是因为脚本运行时 AutoJs6 可能没有打开任何界面. */
class PluginStorageAccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val plugin = intent.getStringExtra(EXTRA_PACKAGE)
        if (plugin == null) {
            finish()
            return
        }
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(plugin, 0)) }.getOrDefault(plugin)
        MaterialDialog.Builder(this)
            .title(R.string.text_all_files_access)
            .content(getString(R.string.plugin_storage_access_explanation, label))
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_authorize)
            .onPositive { _, _ -> PluginStorageAccess.openSettings(this, plugin) }
            .dismissListener { finish() }
            .showAdaptive()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}
