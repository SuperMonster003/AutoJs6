package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import org.autojs.autojs.core.plugin.editor.AceEditorPluginStateMonitor
import org.autojs.autojs.ui.settings.AceEditorPluginUi
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils.setToolbarAsBack
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPluginSettingsBinding
import org.autojs.plugin.editor.api.EditorPluginContract

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 22, 2026.
 */
class PluginSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityPluginSettingsBinding
    private var lastAcePluginState: AceEditorPluginStateMonitor.State? = null
    private val acePluginStateListener: (AceEditorPluginStateMonitor.Change) -> Unit = { change ->
        val wasAvailable = lastAcePluginState?.available == true || change.previous?.available == true
        val wasRemoved = change.cause == AceEditorPluginStateMonitor.Cause.PACKAGE_REMOVED
        lastAcePluginState = change.current
        if (
            isAcePluginSettings && !change.current.available && (wasAvailable || wasRemoved) &&
            !isFinishing && !isDestroyed
        ) {
            AceEditorPluginUi.showUnavailableToastOnce(this, change)
            finish()
        }
    }

    private val isAcePluginSettings: Boolean
        get() = intent.getStringExtra(EXTRA_PACKAGE_NAME) == EditorPluginContract.DEFAULT_PACKAGE_NAME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginSettingsBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        val title = intent.getStringExtra(EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        setToolbarAsBack(this, title)
        binding.toolbar.setNavigationOnClickListener { requestClose() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requestClose()
            }
        })

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_plugin_settings, PluginSettingsFragment.newInstance(intent.extras ?: Bundle()))
                .disallowAddToBackStack()
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        requestClose()
        return true
    }

    override fun onStart() {
        super.onStart()
        if (!isAcePluginSettings) return
        val current = AceEditorPluginStateMonitor.current(this)
        lastAcePluginState = current
        if (!current.available) {
            AceEditorPluginUi.showUnavailableToastOnce(
                this,
                AceEditorPluginStateMonitor.latestChange(this),
            )
            finish()
        } else {
            AceEditorPluginStateMonitor.addListener(this, acePluginStateListener)
        }
    }

    override fun onStop() {
        if (isAcePluginSettings) {
            AceEditorPluginStateMonitor.removeListener(acePluginStateListener)
        }
        super.onStop()
    }

    private fun requestClose() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_plugin_settings) as? PluginSettingsFragment
        if (fragment == null) {
            finish()
            return
        }
        fragment.requestClose {
            finish()
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_INSTUCTION_HARD_CODED = "extra_instruction_hard_coded"
        const val EXTRA_INSTRUCTION_MARKDOWN_URL = "extra_instruction_markdown_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_ENGINE = "extra_engine"
        const val EXTRA_VARIANT = "extra_variant"
        const val EXTRA_ENGINE_ID = "extra_engine_id"
        const val EXTRA_IS_INSTALLED = "extra_is_installed"
        const val EXTRA_ENGINE_INSTALLED_COUNT = "extra_engine_installed_count"

        fun launch(context: Context, item: PluginCenterItem, engineInstalledCount: Int = 0) {
            Intent(context, PluginSettingsActivity::class.java)
                .putExtra(EXTRA_PACKAGE_NAME, item.packageName)
                .putExtra(EXTRA_INSTUCTION_HARD_CODED, item.instructionHardCoded)
                .putExtra(EXTRA_INSTRUCTION_MARKDOWN_URL, item.instructionMarkdownUrl)
                .putExtra(EXTRA_TITLE, item.title)
                .putExtra(EXTRA_SOURCE, item.source.name)
                .putExtra(EXTRA_ENGINE, item.engine)
                .putExtra(EXTRA_VARIANT, item.variant)
                .putExtra(EXTRA_ENGINE_ID, item.engineId)
                .putExtra(EXTRA_IS_INSTALLED, item.isInstalled)
                .putExtra(EXTRA_ENGINE_INSTALLED_COUNT, engineInstalledCount)
                .startSafely(context)
        }

        fun launch(
            context: Context,
            packageName: String,
            title: String,
            source: PluginIndexSource,
        ) {
            Intent(context, PluginSettingsActivity::class.java)
                .putExtra(EXTRA_PACKAGE_NAME, packageName)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SOURCE, source.name)
                .putExtra(EXTRA_IS_INSTALLED, true)
                .putExtra(EXTRA_ENGINE_INSTALLED_COUNT, 0)
                .startSafely(context)
        }
    }
}
