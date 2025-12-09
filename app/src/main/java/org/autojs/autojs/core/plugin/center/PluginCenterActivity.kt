package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.launch
import org.autojs.autojs.extension.MaterialDialogExtensions.widgetThemeColor
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByThemeColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPluginCenterBinding

@SuppressLint("NotifyDataSetChanged")
class PluginCenterActivity : BaseActivity() {

    private lateinit var binding: ActivityPluginCenterBinding

    private val pickApkLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            PluginInstaller.installFromFileUriWithPrompt(this@PluginCenterActivity, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPluginCenterBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_plugin_center, PluginCenterFragment())
            .commit()

        setToolbarAsBack(R.string.text_plugin_center)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_plugin_center, menu)
        setUpToolbarColors()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_install_from_local_file -> {
                pickApkLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                true
            }
            R.id.action_install_from_url -> {
                MaterialDialog.Builder(this)
                    .title(R.string.text_install_plugin_from_url)
                    .content(R.string.instruction_install_plugin_from_url)
                    .input(null, null) { d, input ->
                        val positiveButton = d.getActionButton(DialogAction.POSITIVE)
                        when {
                            input.isNullOrBlank() -> {
                                positiveButton.setOnClickListener(null)
                                positiveButton.setTextColor(d.context.getColor(R.color.dialog_button_unavailable))
                            }
                            else -> {
                                positiveButton.setOnClickListener {
                                    d.dismiss()
                                    val url = input.trim().toString()
                                    val context = this@PluginCenterActivity
                                    lifecycleScope.launch {
                                        runCatching {
                                            PluginInstaller.installFromUrlWithPrompt(context, url)
                                        }.onFailure { e ->
                                            MaterialDialog.Builder(context)
                                                .title(R.string.text_failed_to_retrieve)
                                                .content(e.message ?: e.toString())
                                                .positiveText(R.string.dialog_button_dismiss)
                                                .show()
                                        }
                                    }
                                }
                                positiveButton.setTextColor(d.context.getColor(R.color.dialog_button_attraction))
                            }
                        }
                    }
                    .alwaysCallInputCallback()
                    .widgetThemeColor()
                    .negativeText(R.string.text_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .onNegative { d, _ -> d.dismiss() }
                    .positiveText(R.string.dialog_button_retrieve)
                    .positiveColorRes(R.color.dialog_button_unavailable)
                    .autoDismiss(false)
                    .cancelable(false)
                    .show()
                true
            }
            R.id.action_search -> {
                // TODO action_search
                ViewUtils.showToast(this, R.string.text_under_development)
                true
            }
            R.id.action_sort -> {
                // TODO action_sort
                ViewUtils.showToast(this, R.string.text_under_development)
                true
            }
            R.id.action_filter -> {
                // TODO action_filter
                ViewUtils.showToast(this, R.string.text_under_development)
                true
            }
            R.id.action_global_settings -> {
                // TODO action_global_settings
                ViewUtils.showToast(this, R.string.text_under_development)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setUpToolbarColors() {
        binding.toolbar.setMenuIconsColorByThemeColorLuminance(this)
        binding.toolbar.setNavigationIconColorByThemeColorLuminance(this)
    }

    companion object {

        fun startActivity(context: Context) {
            Intent(context, PluginCenterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
        }

    }

}