package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.widget.SearchViewItem
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardDialog
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setTitlesTextColorByThemeColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPluginCenterBinding

@SuppressLint("NotifyDataSetChanged")
class PluginCenterActivity : BaseActivity() {

    private lateinit var binding: ActivityPluginCenterBinding

    private var searchViewItem: SearchViewItem? = null
    private var initialQuery: String? = null
    private var sourceScope: PluginIndexSource? = null
    private var variantDisplayMode: PluginVariantDisplayMode = PluginVariantDisplayMode.STANDARD

    private val pickApkLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            PluginInstaller.installFromFileUriWithPrompt(this@PluginCenterActivity, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        org.autojs.autojs.ui.settings.ThreeStoveAgentGuide(this)

        variantDisplayMode = intent.getStringExtra(EXTRA_VARIANT_DISPLAY_MODE)
            ?.runCatching { PluginVariantDisplayMode.valueOf(this) }
            ?.getOrNull()
            ?: PluginVariantDisplayMode.STANDARD
        initialQuery = intent.getStringExtra(EXTRA_INITIAL_QUERY)?.takeIf { it.isNotBlank() }
        sourceScope = intent.getStringExtra(EXTRA_SOURCE_SCOPE)
            ?.runCatching { PluginIndexSource.valueOf(this) }
            ?.getOrNull()
        if (variantDisplayMode == PluginVariantDisplayMode.ADDITIONAL_ONLY) {
            sourceScope = PluginIndexSource.OFFICIAL
        }

        binding = ActivityPluginCenterBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragment_plugin_center,
                PluginCenterFragment.newInstance(sourceScope, variantDisplayMode, initialQuery),
            )
            .commit()

        val titleRes = when {
            variantDisplayMode == PluginVariantDisplayMode.ADDITIONAL_ONLY -> R.string.text_plugin_additional_variants
            sourceScope == PluginIndexSource.OFFICIAL -> R.string.text_plugin_library_official
            sourceScope == PluginIndexSource.THIRD_PARTY -> R.string.text_plugin_library_third_party
            else -> R.string.text_plugin_center
        }

        setToolbarAsBack(titleRes)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_plugin_center, menu)
        val scopedMode = sourceScope != null
        menu?.findItem(R.id.action_add)?.isVisible = !scopedMode
        menu?.findItem(R.id.action_filter)?.isVisible = !scopedMode
        menu?.findItem(R.id.action_global_settings)?.isVisible = !scopedMode
        menu?.findItem(R.id.action_additional_plugin_variants)?.isVisible =
            variantDisplayMode != PluginVariantDisplayMode.ADDITIONAL_ONLY &&
                    sourceScope != PluginIndexSource.THIRD_PARTY
        setUpSearchMenuItem(menu)
        setUpToolbarColors()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val center = supportFragmentManager.findFragmentById(R.id.fragment_plugin_center) as? PluginCenterFragment

        return when (item.itemId) {
            R.id.action_plugin_install_wizard -> {
                PluginInstallWizardDialog.show(this, lifecycleScope)
                true
            }
            R.id.action_install_from_official_library -> {
                launch(this, PluginIndexSource.OFFICIAL)
                true
            }
            R.id.action_install_from_third_party_library -> {
                launch(this, PluginIndexSource.THIRD_PARTY)
                true
            }
            R.id.action_install_from_local_file -> {
                PluginInstallActions.installFromLocalFile(pickApkLauncher)
                true
            }
            R.id.action_install_from_url -> {
                PluginInstallActions.showInstallFromUrlDialog(this, lifecycleScope)
                true
            }
            R.id.action_search -> {
                // Handled by SearchViewItem.
                // zh-CN: 由 SearchViewItem 处理.
                super.onOptionsItemSelected(item)
            }
            R.id.action_sort -> {
                PluginCenterDialogs(this, sourceScope).showSortDialog(center)
                true
            }
            R.id.action_filter -> {
                PluginCenterDialogs(this, sourceScope).showFilterDialog(center)
                true
            }
            R.id.action_additional_plugin_variants -> {
                launchAdditionalVariants(this)
                true
            }
            R.id.action_show_logs -> {
                PluginCenterDialogs(this, sourceScope).showProcessLogs()
                true
            }
            R.id.action_global_settings -> {
                PluginCenterSettingsActivity.launch(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setUpSearchMenuItem(menu: Menu?) {
        val m = menu ?: return
        val searchMenuItem = m.findItem(R.id.action_search) ?: return

        searchViewItem = object : SearchViewItem(this, searchMenuItem) {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                binding.toolbar.onceGlobalLayout { setUpToolbarColors() }
                return super.onMenuItemActionExpand(item)
            }
        }.apply {
            setQueryCallback(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = true.also { submitQueryToFragment(query) }
                override fun onQueryTextChange(newText: String?) = true.also { submitQueryToFragment(newText) }
            })
        }
    }

    private fun submitQueryToFragment(query: String?) {
        val center = supportFragmentManager.findFragmentById(R.id.fragment_plugin_center) as? PluginCenterFragment ?: return
        center.setQuery(query)
    }

    private fun setUpToolbarColors() {
        binding.toolbar.setMenuIconsColorByThemeColorLuminance(this)
        binding.toolbar.setNavigationIconColorByThemeColorLuminance(this)
        binding.toolbar.setTitlesTextColorByThemeColorLuminance(this)
        searchViewItem?.setColorsByThemeColorLuminance()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchViewItem = null
    }

    companion object {
        private const val EXTRA_SOURCE_SCOPE = "extra_source_scope"
        private const val EXTRA_VARIANT_DISPLAY_MODE = "extra_variant_display_mode"
        private const val EXTRA_INITIAL_QUERY = "extra_initial_query"

        @JvmOverloads
        fun launch(
            context: Context,
            sourceScope: PluginIndexSource? = null,
            initialQuery: String? = null,
        ) {
            Intent(context, PluginCenterActivity::class.java).apply {
                if (sourceScope != null) {
                    putExtra(EXTRA_SOURCE_SCOPE, sourceScope.name)
                }
                initialQuery?.takeIf { it.isNotBlank() }?.let {
                    putExtra(EXTRA_INITIAL_QUERY, it)
                }
            }.startSafely(context)
        }

        fun launchAdditionalVariants(context: Context) {
            Intent(context, PluginCenterActivity::class.java)
                .putExtra(EXTRA_SOURCE_SCOPE, PluginIndexSource.OFFICIAL.name)
                .putExtra(EXTRA_VARIANT_DISPLAY_MODE, PluginVariantDisplayMode.ADDITIONAL_ONLY.name)
                .startSafely(context)
        }
    }
}
