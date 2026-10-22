package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.toggleContentViewByItems
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R

class PluginCenterSettingsFragment : PreferenceFragmentCompat() {

    private var autoMountManagePreference: MaterialPreference? = null
    private var hiddenPluginManagePreference: MaterialPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_plugin_center_preferences, rootKey)
        findPreference<MaterialPreference>("agent_runtime_access")?.setOnPreferenceClickListener {
            startActivity(android.content.Intent(requireContext(), org.autojs.autojs.engine.RuntimeAgentAccessManagementActivity::class.java)); true
        }
        autoMountManagePreference = findPreference(KEY_PLUGIN_CENTER_AUTO_MOUNT_MANAGEMENT)
        hiddenPluginManagePreference = findPreference(KEY_PLUGIN_CENTER_HIDDEN_PLUGIN_MANAGEMENT)
        autoMountManagePreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showAutoMountManagementDialog()
            true
        }
        hiddenPluginManagePreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showHiddenPluginManagementDialog()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.excludePaddingClippableViewFromBottomNavigationBar()
        refreshAutoMountSummary()
        refreshHiddenPluginSummary()
    }

    override fun onResume() {
        super.onResume()
        refreshAutoMountSummary()
        refreshHiddenPluginSummary()
    }

    private fun refreshAutoMountSummary() {
        val count = PluginMountStore.listEnabledConfigs().size
        autoMountManagePreference?.summary = resources.getQuantityString(R.plurals.text_items_total_sum_with_colon, count, count)
    }

    private fun refreshHiddenPluginSummary() {
        val count = PluginVisibilityStore.hiddenCount(requireContext())
        hiddenPluginManagePreference?.summary = resources.getQuantityString(R.plurals.text_items_total_sum_with_colon, count, count)
    }

    private fun showAutoMountManagementDialog() {
        val context = requireContext()
        val enabledConfigs = PluginMountStore.listEnabledConfigs()

        val enabledConfigSummaries = enabledConfigs.map { config -> config.toSummary(context) }

        MaterialDialog.Builder(context)
            .title(R.string.text_plugin_auto_mount_manage)
            .content(R.string.text_no_auto_mount_plugins)
            .items(enabledConfigSummaries)
            .itemsCallback { d, _, _, text ->
                getConfigFromSummary(context, text)?.let {
                    showAutoMountPluginActions(it, d)
                }
            }
            .widgetThemeColor()
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .onPositive { d, _ -> d.dismiss() }
            .autoDismiss(false)
            .build()
            .apply {
                toggleContentViewByItems()
            }
            .showAdaptive()
    }

    private fun showHiddenPluginManagementDialog() {
        val context = requireContext()
        val hiddenPlugins = PluginVisibilityStore.listHidden(context)
        val summaries = hiddenPlugins.map { it.toSummary(context) }

        MaterialDialog.Builder(context)
            .title(R.string.text_plugin_hidden_manage)
            .content(R.string.text_no_hidden_plugins)
            .items(summaries)
            .itemsCallback { d, _, which, _ ->
                hiddenPlugins.getOrNull(which)?.let {
                    showHiddenPluginActions(it, d)
                }
            }
            .widgetThemeColor()
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .onPositive { d, _ -> d.dismiss() }
            .autoDismiss(false)
            .build()
            .apply {
                toggleContentViewByItems()
            }
            .showAdaptive()
    }

    private fun PluginMountStore.MountConfig.toSummary(context: Context): String {
        val config = this
        return listOf(
            context.getString(R.string.text_property_colon_value, context.getString(R.string.text_name), resolvePluginDisplayName(config.packageName)),
            context.getString(R.string.text_property_colon_value, context.getString(R.string.text_package_name), config.packageName),
            context.getString(R.string.text_property_colon_value, context.getString(R.string.text_mount_path), resolveMountPath(config)),
        ).joinToString("\n")
    }

    private fun PluginVisibilityStore.HiddenPlugin.toSummary(context: Context): String {
        val displayName = title?.takeIf { it.isNotBlank() } ?: resolvePluginDisplayName(packageName)
        val sourceText = when (source) {
            PluginIndexSource.OFFICIAL -> context.getString(R.string.text_plugin_library_official)
            PluginIndexSource.THIRD_PARTY -> context.getString(R.string.text_plugin_library_third_party)
            null -> context.getString(R.string.text_unknown)
        }
        return listOf(
            context.getString(R.string.text_property_colon_value, context.getString(R.string.text_name), displayName),
            context.getString(R.string.text_property_colon_value, context.getString(R.string.text_package_name), packageName),
            context.getString(R.string.text_property_colon_value, context.getString(R.string.text_source), sourceText),
        ).joinToString("\n")
    }

    private fun getConfigFromSummary(context: Context, summary: CharSequence): PluginMountStore.MountConfig? {
        val lines = summary.split("\n")
        val packageName = lines.getOrNull(1)?.substringAfter(context.getString(R.string.symbol_colon_with_blank))?.trim() ?: return null
        return PluginMountStore.getMountConfig(packageName)
    }

    private fun showAutoMountPluginActions(config: PluginMountStore.MountConfig, parentDialog: MaterialDialog? = null) {
        val context = requireContext()
        val displayName = resolvePluginDisplayName(config.packageName)
        val content = config.toSummary(context)

        MaterialDialog.Builder(context)
            .title(R.string.text_plugin_auto_mount_manage)
            .content(content)
            .neutralText(R.string.dialog_button_disable)
            .neutralColorRes(R.color.dialog_button_caution)
            .onNeutral { d, _ ->
                PluginMountStore.setEnabled(config.packageName, false)
                refreshAutoMountSummary()
                parentDialog?.items?.let {
                    it.remove(content)
                    parentDialog.notifyItemsChanged()
                    parentDialog.toggleContentViewByItems()
                }
                d.dismiss()
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .positiveText(R.string.text_settings)
            .positiveColorRes(R.color.dialog_button_advanced_settings)
            .onPositive { _, _ ->
                PluginSettingsActivity.launch(
                    context = context,
                    packageName = config.packageName,
                    title = displayName,
                    source = PluginIndexSource.THIRD_PARTY,
                )
            }
            .autoDismiss(false)
            .show()
    }

    private fun showHiddenPluginActions(
        hiddenPlugin: PluginVisibilityStore.HiddenPlugin,
        parentDialog: MaterialDialog? = null,
    ) {
        val context = requireContext()
        val displayName = hiddenPlugin.title?.takeIf { it.isNotBlank() }
            ?: resolvePluginDisplayName(hiddenPlugin.packageName)
        val content = hiddenPlugin.toSummary(context)

        MaterialDialog.Builder(context)
            .title(R.string.text_plugin_hidden_manage)
            .content(content)
            .neutralText(R.string.text_show)
            .neutralColorRes(R.color.dialog_button_attraction)
            .onNeutral { d, _ ->
                PluginVisibilityStore.show(context, hiddenPlugin.packageName)
                refreshHiddenPluginSummary()
                parentDialog?.items?.let { items ->
                    items.remove(content)
                    parentDialog.notifyItemsChanged()
                    parentDialog.toggleContentViewByItems()
                }
                d.dismiss()
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .positiveText(R.string.text_settings)
            .positiveColorRes(R.color.dialog_button_advanced_settings)
            .onPositive { _, _ ->
                PluginSettingsActivity.launch(
                    context = context,
                    packageName = hiddenPlugin.packageName,
                    title = displayName,
                    source = hiddenPlugin.source ?: PluginIndexSource.OFFICIAL,
                )
            }
            .autoDismiss(false)
            .show()
    }

    private fun resolveMountPath(config: PluginMountStore.MountConfig): String {
        val objectName = config.normalizedObjectName ?: PluginMountStore.defaultObjectName(config.packageName)
        val mountPoint = config.normalizedMountPoint
        return if (mountPoint.isNullOrBlank()) {
            "global.$objectName"
        } else {
            "$mountPoint.$objectName"
        }
    }

    private fun resolvePluginDisplayName(packageName: String): String {
        val pm = requireContext().packageManager
        return runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrNull().orEmpty().ifBlank { packageName }
    }

    companion object {
        private const val KEY_PLUGIN_CENTER_AUTO_MOUNT_MANAGEMENT = "plugin_center_auto_mount_management"
        private const val KEY_PLUGIN_CENTER_HIDDEN_PLUGIN_MANAGEMENT = "plugin_center_hidden_plugin_management"
    }
}
