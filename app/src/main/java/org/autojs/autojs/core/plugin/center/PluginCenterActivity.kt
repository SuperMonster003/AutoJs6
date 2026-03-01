package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.launch
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.widget.SearchViewItem
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setTitlesTextColorByThemeColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPluginCenterBinding

@SuppressLint("NotifyDataSetChanged")
class PluginCenterActivity : BaseActivity() {

    private lateinit var binding: ActivityPluginCenterBinding

    private var mSearchViewItem: SearchViewItem? = null

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
        setUpSearchMenuItem(menu)
        setUpToolbarColors()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val center = supportFragmentManager.findFragmentById(R.id.fragment_plugin_center) as? PluginCenterFragment

        return when (item.itemId) {
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
                showSortDialog(center)
                true
            }
            R.id.action_filter -> {
                showFilterDialog(center)
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

    private fun setUpSearchMenuItem(menu: Menu?) {
        val m = menu ?: return
        val searchMenuItem = m.findItem(R.id.action_search) ?: return

        mSearchViewItem = object : SearchViewItem(this, searchMenuItem) {
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

    private fun showSortDialog(center: PluginCenterFragment?) {
        if (center == null) return

        val entries = PluginCenterFragment.Sort.values()

        MaterialDialog.Builder(this)
            .title(R.string.text_sort)
            .items(entries.map { getString(it.titleRes) })
            .itemsCallbackSingleChoice(PluginSortStore.getSortOrdinal(this)) { d, _, which, _ ->
                d.dismiss()
                val selectedSort = entries[which]
                // center.setSort(selectedSort)
                PluginSortStore.setSort(this, selectedSort)
                true
            }
            .choiceWidgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    private fun showFilterDialog(center: PluginCenterFragment?) {
        if (center == null) return

        val entries = PluginCenterFragment.Filter.values()

        MaterialDialog.Builder(this)
            .title(R.string.text_filter)
            .items(entries.map { getString(it.titleRes) })
            .itemsCallbackSingleChoice(PluginFilterStore.getFilterOrdinal(this)) { d, _, which, _ ->
                d.dismiss()
                val selectedFilter = entries[which]
                // center.setFilter(selectedFilter)
                PluginFilterStore.setFilter(this, selectedFilter)
                true
            }
            .choiceWidgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    private fun setUpToolbarColors() {
        binding.toolbar.setMenuIconsColorByThemeColorLuminance(this)
        binding.toolbar.setNavigationIconColorByThemeColorLuminance(this)
        binding.toolbar.setTitlesTextColorByThemeColorLuminance(this)
        mSearchViewItem?.setColorsByThemeColorLuminance()
    }

    override fun onDestroy() {
        super.onDestroy()
        mSearchViewItem = null
    }

    companion object {

        fun startActivity(context: Context) {
            Intent(context, PluginCenterActivity::class.java)
                .startSafely(context)
        }

    }

}