package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Space
import androidx.appcompat.widget.AppCompatTextView
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.autojs.autojs.core.accessibility.AdvancedProtectionNotice
import org.autojs.autojs.network.UpdateIgnoreStore
import org.autojs.autojs.network.entity.VersionInfo
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.theme.preference.ThemeColorSwitchPreference
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.util.DialogUtils.buildAndShowAdaptiveOrNull
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.toggleActionButtonAbilityByItems
import org.autojs.autojs.util.DialogUtils.toggleContentViewByItems
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.PreferenceUtils.setButtonColorVisualEnabledWith
import org.autojs.autojs.util.PreferenceUtils.setSummaryColorVisualEnabledWith
import org.autojs.autojs.util.PreferenceUtils.setTitleColorVisualEnabledWith
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogPluginMountInputBinding
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import java.util.Locale
import kotlin.math.max

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 22, 2026.
 */
class PluginSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var packageName: String
    private lateinit var title: String
    private lateinit var source: PluginIndexSource
    private var instructionHardCoded: String? = null
    private var instructionMarkdownUrl: String? = null
    private var engine: String? = null
    private var engineId: String? = null
    private var engineInstalledCount: Int = 0

    private lateinit var ignoredVersionSummaries: MutableList<String>

    private lateinit var launchPagePreference: MaterialPreference
    private lateinit var visibilityPreference: ThemeColorSwitchPreference
    private lateinit var launcherIconPreference: ThemeColorSwitchPreference
    private lateinit var autoCheckPreference: ThemeColorSwitchPreference
    private lateinit var ignoredVersionsPreference: MaterialPreference
    private lateinit var instructionPreference: MaterialPreference

    private var autoMountSwitchPreference: ThemeColorSwitchPreference? = null
    private var mountObjectPreference: MaterialPreference? = null
    private var mountPointPreference: MaterialPreference? = null
    private var mountCategory: PreferenceCategory? = null

    private var variantPreference: MaterialPreference? = null

    /** Bun capability bridge grants (plugin ROADMAP M7); only shown for the package that hosts the Bun runtime service. */
    private var bunHostInfoGrantPreference: ThemeColorSwitchPreference? = null
    private var bunUiToastGrantPreference: ThemeColorSwitchPreference? = null
    private var bunDeviceInfoGrantPreference: ThemeColorSwitchPreference? = null
    private var bunPinyinGrantPreference: ThemeColorSwitchPreference? = null
    private var bunImageFilesGrantPreference: ThemeColorSwitchPreference? = null
    private var bunScreenCaptureGrantPreference: ThemeColorSwitchPreference? = null
    private var bunScreenRecordGrantPreference: ThemeColorSwitchPreference? = null
    private var bunScreenRecordMicrophoneGrantPreference: ThemeColorSwitchPreference? = null
    private var bunScreenRecordPlaybackGrantPreference: ThemeColorSwitchPreference? = null
    private var bunImageProcessGrantPreference: ThemeColorSwitchPreference? = null
    private var bunOpenccGrantPreference: ThemeColorSwitchPreference? = null
    private var bunPinyin4jGrantPreference: ThemeColorSwitchPreference? = null
    private var bunScreenMetricsGrantPreference: ThemeColorSwitchPreference? = null
    private var bunAppControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunAccessibilityControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunAccessibilityGestureGrantPreference: ThemeColorSwitchPreference? = null
    private var bunClipboardControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunFilesControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunStorageControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunDatabaseControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunSensorsControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunShellControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunShellRootGrantPreference: ThemeColorSwitchPreference? = null
    private var bunShellShizukuGrantPreference: ThemeColorSwitchPreference? = null
    private var bunEventsObserveGrantPreference: ThemeColorSwitchPreference? = null
    private var bunEventsNotificationGrantPreference: ThemeColorSwitchPreference? = null
    private var bunEventsToastGrantPreference: ThemeColorSwitchPreference? = null
    private var bunEventsKeyGrantPreference: ThemeColorSwitchPreference? = null
    private var bunNotificationsPostGrantPreference: ThemeColorSwitchPreference? = null
    private var bunNotificationsSettingsGrantPreference: ThemeColorSwitchPreference? = null
    private var bunDeviceControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunDevicePowerGrantPreference: ThemeColorSwitchPreference? = null
    private var bunDeviceAudioGrantPreference: ThemeColorSwitchPreference? = null
    private var bunDialogsControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunAndroidObjectsGrantPreference: ThemeColorSwitchPreference? = null
    private var bunTasksControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunConsoleControlGrantPreference: ThemeColorSwitchPreference? = null
    private var bunUiLayoutGrantPreference: ThemeColorSwitchPreference? = null
    private var bunUiComposeGrantPreference: ThemeColorSwitchPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val args = requireArguments()

        packageName = args.getString(PluginSettingsActivity.EXTRA_PACKAGE_NAME).orEmpty()
        instructionHardCoded = args.getString(PluginSettingsActivity.EXTRA_INSTUCTION_HARD_CODED).orEmpty()
        instructionMarkdownUrl = args.getString(PluginSettingsActivity.EXTRA_INSTRUCTION_MARKDOWN_URL).orEmpty()
        title = args.getString(PluginSettingsActivity.EXTRA_TITLE).orEmpty().ifBlank { packageName }
        source = args.getString(PluginSettingsActivity.EXTRA_SOURCE)
            ?.runCatching { PluginIndexSource.valueOf(this) }
            ?.getOrNull()
            ?: PluginIndexSource.OFFICIAL
        engine = args.getString(PluginSettingsActivity.EXTRA_ENGINE)
        engineId = args.getString(PluginSettingsActivity.EXTRA_ENGINE_ID)
        engineInstalledCount = args.getInt(PluginSettingsActivity.EXTRA_ENGINE_INSTALLED_COUNT, 0)

        ignoredVersionSummaries = UpdateIgnoreStore.ignoredVersions(packageName)
            .sortedByDescending { it.first }
            .map {
                val (versionCode, versionName) = it
                VersionInfo(versionName, versionCode).toSummary()
            }
            .toMutableList()

        val resId = when (source) {
            PluginIndexSource.OFFICIAL -> R.xml.fragment_plugin_settings_preferences_official
            PluginIndexSource.THIRD_PARTY -> R.xml.fragment_plugin_settings_preferences_third_party
        }
        setPreferencesFromResource(resId, rootKey)

        if (PluginLocalNetworkPermission.isRequired(packageName)) {
            val launchCategory = findPreference<MaterialPreference>(KEY_PLUGIN_LAUNCH_PAGE)?.parent ?: preferenceScreen
            launchCategory.addPreference(MaterialPreference(requireContext()).apply {
                key = "plugin_local_network_permission"
                layoutResource = R.layout.preference_custom
                setTitle(R.string.text_local_network_permission)
                summary = PluginLocalNetworkPermission.summary(requireContext(), packageName)
                setOnPreferenceClickListener {
                    PluginLocalNetworkPermission.manage(requireContext(), packageName) {
                        if (isAdded) refreshLocalNetworkPermission()
                    }
                    true
                }
            })
        }

        if (packageName == "io.github.supermonster003.autojs6.plugin.three.adapt.a11y") {
            preferenceScreen.addPreference(MaterialPreference(requireContext()).apply {
                key = "accessibility_advanced_protection"
                setTitle(R.string.a11y_advanced_protection_title)
                summary = AdvancedProtectionNotice.summary(requireContext())
                setOnPreferenceClickListener {
                    MaterialDialog.Builder(requireContext())
                        .title(R.string.a11y_advanced_protection_title)
                        .content(AdvancedProtectionNotice.summary(requireContext()))
                        .positiveText(android.R.string.ok)
                        .showAdaptive()
                    true
                }
            })
        }

        if (source == PluginIndexSource.OFFICIAL && !shouldShowVariantPriority()) {
            val variantCategory = findPreference<PreferenceCategory>(KEY_CATEGORY_VARIANT_PRIORITY)
            if (variantCategory != null) {
                preferenceScreen.removePreference(variantCategory)
            }
        }

        if (source != PluginIndexSource.OFFICIAL || !hostsBunRuntimeService()) {
            findPreference<PreferenceCategory>(KEY_CATEGORY_BUN_HOST_CAPABILITIES)?.let(preferenceScreen::removePreference)
        }

        if (source == PluginIndexSource.THIRD_PARTY) {
            val enabled = PluginMountStore.isEnabled(packageName)
            findPreference<MaterialPreference>(KEY_PLUGIN_MOUNT_OBJECT_NAME)?.isVisible = enabled
            findPreference<MaterialPreference>(KEY_PLUGIN_MOUNT_POINT)?.isVisible = enabled
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.excludePaddingClippableViewFromBottomNavigationBar()
        bindPreferences()
        refreshAllSummaries()
    }

    override fun onResume() {
        super.onResume()
        refreshAllSummaries()
        findPreference<MaterialPreference>("accessibility_advanced_protection")?.summary =
            AdvancedProtectionNotice.summary(requireContext())
        refreshLocalNetworkPermission()
    }

    private fun refreshLocalNetworkPermission() {
        findPreference<MaterialPreference>("plugin_local_network_permission")?.summary =
            PluginLocalNetworkPermission.summary(requireContext(), packageName)
    }

    fun requestClose(onAllowed: () -> Unit) {
        if (source != PluginIndexSource.THIRD_PARTY || !PluginMountStore.isEnabled(packageName)) {
            onAllowed()
            return
        }
        applyMountChange(
            objectName = PluginMountStore.getObjectName(packageName),
            mountPoint = PluginMountStore.getMountPoint(packageName),
            onApply = onAllowed,
        )
    }

    private fun bindPreferences() {
        launchPagePreference = requireNotNull(findPreference(KEY_PLUGIN_LAUNCH_PAGE))
        visibilityPreference = requireNotNull(findPreference(KEY_PLUGIN_VISIBILITY))
        launcherIconPreference = requireNotNull(findPreference(KEY_PLUGIN_LAUNCHER_ICON))
        autoCheckPreference = requireNotNull(findPreference(KEY_PLUGIN_AUTO_CHECK))
        ignoredVersionsPreference = requireNotNull(findPreference(KEY_PLUGIN_IGNORED_VERSIONS))
        instructionPreference = requireNotNull(findPreference(KEY_PLUGIN_INSTRUCTION_VIEW))
        variantPreference = findPreference(KEY_PLUGIN_VARIANT_PRIORITY_MANAGE)
        bunHostInfoGrantPreference = findPreference(KEY_PLUGIN_BUN_HOST_INFO_GRANT)
        bunUiToastGrantPreference = findPreference(KEY_PLUGIN_BUN_UI_TOAST_GRANT)
        bunDeviceInfoGrantPreference = findPreference(KEY_PLUGIN_BUN_DEVICE_INFO_GRANT)
        bunPinyinGrantPreference = findPreference("plugin_bun_pinyin_grant")
        bunImageFilesGrantPreference = findPreference("plugin_bun_image_files_grant")
        bunScreenCaptureGrantPreference = findPreference("plugin_bun_screen_capture_grant")
        bunScreenRecordGrantPreference = findPreference("plugin_bun_screen_record_grant")
        bunScreenRecordMicrophoneGrantPreference = findPreference("plugin_bun_screen_record_microphone_grant")
        bunScreenRecordPlaybackGrantPreference = findPreference("plugin_bun_screen_record_playback_grant")
        bunImageProcessGrantPreference = findPreference("plugin_bun_image_process_grant")
        bunOpenccGrantPreference = findPreference("plugin_bun_opencc_grant")
        bunPinyin4jGrantPreference = findPreference("plugin_bun_pinyin4j_grant")
        bunScreenMetricsGrantPreference = findPreference("plugin_bun_screen_metrics_grant")
        bunAppControlGrantPreference = findPreference("plugin_bun_app_control_grant")
        bunAccessibilityControlGrantPreference = findPreference("plugin_bun_accessibility_control_grant")
        bunAccessibilityGestureGrantPreference = findPreference("plugin_bun_accessibility_gesture_grant")
        bunClipboardControlGrantPreference = findPreference("plugin_bun_clipboard_control_grant")
        bunFilesControlGrantPreference = findPreference("plugin_bun_files_control_grant")
        bunStorageControlGrantPreference = findPreference("plugin_bun_storage_control_grant")
        bunDatabaseControlGrantPreference = findPreference("plugin_bun_database_control_grant")
        bunSensorsControlGrantPreference = findPreference("plugin_bun_sensors_control_grant")
        bunShellControlGrantPreference = findPreference("plugin_bun_shell_control_grant")
        bunShellRootGrantPreference = findPreference("plugin_bun_shell_root_grant")
        bunShellShizukuGrantPreference = findPreference("plugin_bun_shell_shizuku_grant")
        bunEventsObserveGrantPreference = findPreference("plugin_bun_events_observe_grant")
        bunEventsNotificationGrantPreference = findPreference("plugin_bun_events_notification_grant")
        bunEventsToastGrantPreference = findPreference("plugin_bun_events_toast_grant")
        bunEventsKeyGrantPreference = findPreference("plugin_bun_events_key_grant")
        bunNotificationsPostGrantPreference = findPreference("plugin_bun_notifications_post_grant")
        bunNotificationsSettingsGrantPreference = findPreference("plugin_bun_notifications_settings_grant")
        bunDeviceControlGrantPreference = findPreference("plugin_bun_device_control_grant")
        bunDevicePowerGrantPreference = findPreference("plugin_bun_device_power_grant")
        bunDeviceAudioGrantPreference = findPreference("plugin_bun_device_audio_grant")
        bunDialogsControlGrantPreference = findPreference("plugin_bun_dialogs_control_grant")
        bunAndroidObjectsGrantPreference = findPreference("plugin_bun_android_objects_grant")
        bunTasksControlGrantPreference = findPreference("plugin_bun_tasks_control_grant")
        bunConsoleControlGrantPreference = findPreference("plugin_bun_console_control_grant")
        bunUiLayoutGrantPreference = findPreference("plugin_bun_ui_layout_grant")
        bunUiComposeGrantPreference = findPreference("plugin_bun_ui_compose_grant")

        launcherIconPreference.setOnPreferenceChangeListener { _, value ->
            val context = requireContext().applicationContext
            val show = value as? Boolean ?: true
            launcherIconPreference.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = PluginLauncherIconController.setShown(context, packageName, show)
                    if (!result.success) ViewUtils.showToast(context, result.message.orEmpty(), true)
                    if (isAdded) refreshAllSummaries()
                } finally {
                    if (isAdded) launcherIconPreference.isEnabled = true
                }
            }
            false
        }

        launchPagePreference.setOnPreferenceClickListener {
            val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                ViewUtils.showToast(requireContext(), R.string.summary_plugin_no_launch_page, true)
                return@setOnPreferenceClickListener true
            }
            startActivity(Intent(launchIntent))
            true
        }

        visibilityPreference.setOnPreferenceChangeListener { _, newValue ->
            val shouldHide = newValue as? Boolean ?: false
            if (shouldHide) {
                showHidePluginConfirmDialog()
                return@setOnPreferenceChangeListener false
            }
            PluginVisibilityStore.show(requireContext(), packageName)
            refreshVisibilitySummary()
            false
        }

        autoCheckPreference.setOnPreferenceChangeListener { _, newValue ->
            PluginUpdateCheckStore.setEnabled(packageName, newValue as? Boolean ?: true)
            true
        }

        ignoredVersionsPreference.setOnPreferenceClickListener {
            showIgnoredVersionsDialog()
            true
        }

        variantPreference?.setOnPreferenceClickListener {
            val currentEngine = engine
            if (currentEngine.isNullOrBlank()) {
                ViewUtils.showToast(requireContext(), R.string.text_unavailable, true)
            } else {
                PluginVariantPriorityActivity.launch(
                    context = requireContext(),
                    engine = currentEngine,
                    engineId = engineId,
                    title = title,
                )
            }
            true
        }

        bunHostInfoGrantPreference?.setOnPreferenceChangeListener { _, newValue ->
            // Revocation is per run: the next runScript request simply carries no snapshot keys.
            BunHostCapabilityGrants.setHostInfoGranted(requireContext(), newValue as? Boolean ?: true)
            true
        }

        bunUiToastGrantPreference?.setOnPreferenceChangeListener { _, newValue ->
            // Dynamic capabilities are revoked the same way: the next request just omits the ID from the grant list.
            BunHostCapabilityGrants.setUiToastGranted(requireContext(), newValue as? Boolean ?: true)
            true
        }

        bunDeviceInfoGrantPreference?.setOnPreferenceChangeListener { _, newValue ->
            BunHostCapabilityGrants.setDeviceInfoGranted(requireContext(), newValue as? Boolean ?: true)
            true
        }
        bunPinyinGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setTextPinyinGranted(requireContext(), value == true)
            true
        }
        bunImageFilesGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setImageFilesGranted(requireContext(), value == true)
            true
        }
        bunScreenCaptureGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setScreenCaptureGranted(requireContext(), value == true)
            true
        }
        bunScreenRecordGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setScreenRecordGranted(requireContext(), value == true)
            true
        }
        bunScreenRecordMicrophoneGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setScreenRecordMicrophoneGranted(requireContext(), value == true)
            true
        }
        bunScreenRecordPlaybackGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setScreenRecordPlaybackGranted(requireContext(), value == true)
            true
        }
        bunImageProcessGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setImageProcessGranted(requireContext(), value == true)
            true
        }
        bunOpenccGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setTextOpenccGranted(requireContext(), value == true)
            true
        }
        bunPinyin4jGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setTextPinyin4jGranted(requireContext(), value == true)
            true
        }
        bunScreenMetricsGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setScreenMetricsGranted(requireContext(), value == true)
            true
        }
        bunAppControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setAppControlGranted(requireContext(), value == true)
            true
        }
        bunAccessibilityControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setAccessibilityControlGranted(requireContext(), value == true)
            true
        }
        bunAccessibilityGestureGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setAccessibilityGestureGranted(requireContext(), value == true)
            true
        }
        bunClipboardControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setClipboardControlGranted(requireContext(), value == true)
            true
        }
        bunFilesControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setFilesControlGranted(requireContext(), value == true)
            true
        }
        bunStorageControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setStorageControlGranted(requireContext(), value == true)
            true
        }
        bunDatabaseControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setDatabaseControlGranted(requireContext(), value == true)
            true
        }
        bunSensorsControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setSensorsControlGranted(requireContext(), value == true)
            true
        }
        bunShellControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setShellControlGranted(requireContext(), value == true)
            true
        }
        bunShellRootGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setShellRootGranted(requireContext(), value == true)
            true
        }
        bunShellShizukuGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setShellShizukuGranted(requireContext(), value == true)
            true
        }
        bunEventsObserveGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setEventsObserveGranted(requireContext(), value == true)
            true
        }
        bunEventsNotificationGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setEventsNotificationGranted(requireContext(), value == true)
            true
        }
        bunEventsToastGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setEventsToastGranted(requireContext(), value == true)
            true
        }
        bunEventsKeyGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setEventsKeyGranted(requireContext(), value == true)
            true
        }
        bunNotificationsPostGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setNotificationsPostGranted(requireContext(), value == true)
            true
        }
        bunNotificationsSettingsGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setNotificationsSettingsGranted(requireContext(), value == true)
            true
        }
        bunDeviceControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setDeviceControlGranted(requireContext(), value == true)
            true
        }
        bunDevicePowerGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setDevicePowerGranted(requireContext(), value == true)
            true
        }
        bunDeviceAudioGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setDeviceAudioGranted(requireContext(), value == true)
            true
        }
        bunDialogsControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setDialogsControlGranted(requireContext(), value == true)
            true
        }
        bunAndroidObjectsGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setAndroidObjectsGranted(requireContext(), value == true)
            true
        }
        bunTasksControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setTasksControlGranted(requireContext(), value == true)
            true
        }
        bunConsoleControlGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setConsoleControlGranted(requireContext(), value == true)
            true
        }
        bunUiLayoutGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setUiLayoutGranted(requireContext(), value == true)
            true
        }
        bunUiComposeGrantPreference?.setOnPreferenceChangeListener { _, value ->
            BunHostCapabilityGrants.setUiComposeGranted(requireContext(), value == true)
            true
        }

        instructionPreference.setOnPreferenceClickListener {
            PluginInstructionActivity.launch(
                context = requireContext(),
                packageName = packageName,
                title = title,
                source = source,
                instructionHardCoded = instructionHardCoded,
                instructionMarkdownUrl = instructionMarkdownUrl,
            )
            true
        }

        if (source == PluginIndexSource.THIRD_PARTY) {
            autoMountSwitchPreference = requireNotNull(findPreference(KEY_PLUGIN_AUTO_MOUNT_ENABLED))
            mountObjectPreference = requireNotNull(findPreference(KEY_PLUGIN_MOUNT_OBJECT_NAME))
            mountPointPreference = requireNotNull(findPreference(KEY_PLUGIN_MOUNT_POINT))
            mountCategory = findPreference(KEY_CATEGORY_AUTO_MOUNT)

            autoMountSwitchPreference?.setOnPreferenceChangeListener { _, newValue ->
                onAutoMountToggle(newValue as? Boolean ?: false)
                true
            }
            mountObjectPreference?.setOnPreferenceClickListener {
                showMountObjectNameDialog()
                true
            }
            mountPointPreference?.setOnPreferenceClickListener {
                showMountPointDialog()
                true
            }
        }
    }

    private fun refreshAllSummaries() {
        val context = requireContext()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)

        val hasLaunchIntent = launchIntent != null
        launchPagePreference.setTitleColorVisualEnabledWith { hasLaunchIntent }
        launchPagePreference.setSummaryColorVisualEnabledWith { hasLaunchIntent }
        launchPagePreference.summary = when (hasLaunchIntent) {
            true -> this.getString(R.string.summary_plugin_jump_to_launch_page)
            else -> this.getString(R.string.summary_plugin_no_launch_page)
        }

        refreshVisibilitySummary()

        launcherIconPreference.isChecked = PluginLauncherIconController.isShown(context, packageName)
        launcherIconPreference.setSummary(
            if (launcherIconPreference.isChecked) R.string.summary_plugin_launcher_icon_shown
            else R.string.summary_plugin_launcher_icon_hidden,
        )

        val globalAutoCheckEnabled = PluginOnlineSettingsStore.isAutoCheckUpdatesEnabled()
        autoCheckPreference.isChecked = PluginUpdateCheckStore.isEnabled(packageName, defaultEnabled = true)
        bunHostInfoGrantPreference?.isChecked = BunHostCapabilityGrants.isHostInfoGranted(requireContext())
        bunUiToastGrantPreference?.isChecked = BunHostCapabilityGrants.isUiToastGranted(requireContext())
        bunDeviceInfoGrantPreference?.isChecked = BunHostCapabilityGrants.isDeviceInfoGranted(requireContext())
        bunPinyinGrantPreference?.isChecked = BunHostCapabilityGrants.isTextPinyinGranted(requireContext())
        bunImageFilesGrantPreference?.isChecked = BunHostCapabilityGrants.isImageFilesGranted(requireContext())
        bunScreenCaptureGrantPreference?.isChecked = BunHostCapabilityGrants.isScreenCaptureGranted(requireContext())
        bunScreenRecordGrantPreference?.isChecked = BunHostCapabilityGrants.isScreenRecordGranted(requireContext())
        bunScreenRecordMicrophoneGrantPreference?.isChecked = BunHostCapabilityGrants.isScreenRecordMicrophoneGranted(requireContext())
        bunScreenRecordPlaybackGrantPreference?.isChecked = BunHostCapabilityGrants.isScreenRecordPlaybackGranted(requireContext())
        bunImageProcessGrantPreference?.isChecked = BunHostCapabilityGrants.isImageProcessGranted(requireContext())
        bunOpenccGrantPreference?.isChecked = BunHostCapabilityGrants.isTextOpenccGranted(requireContext())
        bunPinyin4jGrantPreference?.isChecked = BunHostCapabilityGrants.isTextPinyin4jGranted(requireContext())
        bunScreenMetricsGrantPreference?.isChecked = BunHostCapabilityGrants.isScreenMetricsGranted(requireContext())
        bunAppControlGrantPreference?.isChecked = BunHostCapabilityGrants.isAppControlGranted(requireContext())
        bunAccessibilityControlGrantPreference?.isChecked = BunHostCapabilityGrants.isAccessibilityControlGranted(requireContext())
        bunAccessibilityGestureGrantPreference?.isChecked = BunHostCapabilityGrants.isAccessibilityGestureGranted(requireContext())
        bunClipboardControlGrantPreference?.isChecked = BunHostCapabilityGrants.isClipboardControlGranted(requireContext())
        bunFilesControlGrantPreference?.isChecked = BunHostCapabilityGrants.isFilesControlGranted(requireContext())
        bunStorageControlGrantPreference?.isChecked = BunHostCapabilityGrants.isStorageControlGranted(requireContext())
        bunDatabaseControlGrantPreference?.isChecked = BunHostCapabilityGrants.isDatabaseControlGranted(requireContext())
        bunSensorsControlGrantPreference?.isChecked = BunHostCapabilityGrants.isSensorsControlGranted(requireContext())
        bunShellControlGrantPreference?.isChecked = BunHostCapabilityGrants.isShellControlGranted(requireContext())
        bunShellRootGrantPreference?.isChecked = BunHostCapabilityGrants.isShellRootGranted(requireContext())
        bunShellShizukuGrantPreference?.isChecked = BunHostCapabilityGrants.isShellShizukuGranted(requireContext())
        bunEventsObserveGrantPreference?.isChecked = BunHostCapabilityGrants.isEventsObserveGranted(requireContext())
        bunEventsNotificationGrantPreference?.isChecked = BunHostCapabilityGrants.isEventsNotificationGranted(requireContext())
        bunEventsToastGrantPreference?.isChecked = BunHostCapabilityGrants.isEventsToastGranted(requireContext())
        bunEventsKeyGrantPreference?.isChecked = BunHostCapabilityGrants.isEventsKeyGranted(requireContext())
        bunNotificationsPostGrantPreference?.isChecked = BunHostCapabilityGrants.isNotificationsPostGranted(requireContext())
        bunNotificationsSettingsGrantPreference?.isChecked = BunHostCapabilityGrants.isNotificationsSettingsGranted(requireContext())
        bunDeviceControlGrantPreference?.isChecked = BunHostCapabilityGrants.isDeviceControlGranted(requireContext())
        bunDevicePowerGrantPreference?.isChecked = BunHostCapabilityGrants.isDevicePowerGranted(requireContext())
        bunDeviceAudioGrantPreference?.isChecked = BunHostCapabilityGrants.isDeviceAudioGranted(requireContext())
        bunDialogsControlGrantPreference?.isChecked = BunHostCapabilityGrants.isDialogsControlGranted(requireContext())
        bunAndroidObjectsGrantPreference?.isChecked = BunHostCapabilityGrants.isAndroidObjectsGranted(requireContext())
        bunTasksControlGrantPreference?.isChecked = BunHostCapabilityGrants.isTasksControlGranted(requireContext())
        bunConsoleControlGrantPreference?.isChecked = BunHostCapabilityGrants.isConsoleControlGranted(requireContext())
        bunUiLayoutGrantPreference?.isChecked = BunHostCapabilityGrants.isUiLayoutGranted(requireContext())
        bunUiComposeGrantPreference?.isChecked = BunHostCapabilityGrants.isUiComposeGranted(requireContext())
        autoCheckPreference.isEnabled = globalAutoCheckEnabled
        autoCheckPreference.setTitleColorVisualEnabledWith { globalAutoCheckEnabled }
        autoCheckPreference.setSummaryColorVisualEnabledWith { globalAutoCheckEnabled }
        autoCheckPreference.setButtonColorVisualEnabledWith { globalAutoCheckEnabled }
        autoCheckPreference.summary = when (globalAutoCheckEnabled) {
            true -> getString(R.string.summary_plugin_auto_check_updates_per_plugin)
            else -> getString(R.string.summary_plugin_auto_check_updates_disabled_by_global)
        }

        refreshIgnoredVersionSummary()
        refreshMountSection()
        refreshVariantSummary()
    }

    private fun refreshVisibilitySummary() {
        val hidden = PluginVisibilityStore.isHidden(requireContext(), packageName)
        visibilityPreference.isChecked = hidden
        visibilityPreference.summary = getString(
            if (hidden) R.string.summary_plugin_visibility_hidden else R.string.summary_plugin_visibility_shown,
        )
    }

    private fun showHidePluginConfirmDialog() {
        val context = requireContext()
        val hideAction = {
            PluginVisibilityStore.hide(context, packageName, title, source)
            refreshVisibilitySummary()
        }
        buildAndShowAdaptiveOrNull {
            NotAskAgainDialog.Builder(context, KEY_PLUGIN_HIDE_CONFIRM_PROMPT).apply {
                title(R.string.text_prompt)
                content(R.string.prompt_plugin_hide_from_center)
                widgetThemeColor()
                negativeText(R.string.dialog_button_cancel)
                negativeColorRes(R.color.dialog_button_default)
                positiveText(R.string.text_hide)
                positiveColorRes(R.color.dialog_button_caution)
                onPositive { _, _ -> hideAction() }
            }.build()
        } ?: hideAction()
    }

    private fun refreshIgnoredVersionSummary() {
        UpdateIgnoreStore.ignoredVersionCodes(packageName).let {
            ignoredVersionsPreference.summary = resources.getQuantityString(R.plurals.text_items_total_sum_with_colon, it.size, it.size)
        }
    }

    private fun refreshMountSection() {
        if (source != PluginIndexSource.THIRD_PARTY) {
            return
        }

        val config = PluginMountStore.getMountConfig(packageName)
        autoMountSwitchPreference?.isChecked = config.enabled
        mountCategory?.isVisible = true

        val defaultObject = PluginMountStore.defaultObjectName(packageName)
        val userObject = config.objectName?.trim().orEmpty()
        mountObjectPreference?.summary = userObject.ifBlank { defaultObject }

        val globalDisplay = getString(R.string.text_plugin_mount_point_global_display)
        val userMountPoint = PluginMountStore.normalizeMountPoint(config.mountPoint).orEmpty()
        mountPointPreference?.summary = userMountPoint.ifBlank { globalDisplay }

        mountObjectPreference?.isVisible = config.enabled
        mountPointPreference?.isVisible = config.enabled
    }

    private fun refreshVariantSummary() {
        variantPreference?.summary = getString(R.string.summary_plugin_variant_priority_manage)
    }

    /** True when [packageName] exports the Bun runtime service action, i.e. it is the Bun Runtime plugin. */
    private fun hostsBunRuntimeService(): Boolean {
        if (packageName.isBlank()) return false
        val packageManager = requireContext().packageManager
        val intent = Intent(BunRuntimeContract.SERVICE_ACTION).setPackage(packageName)
        val services = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentServices(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
        }.getOrDefault(emptyList())
        return services.any { it.serviceInfo?.packageName == packageName }
    }

    private fun shouldShowVariantPriority(): Boolean {
        return source == PluginIndexSource.OFFICIAL &&
                !engine.isNullOrBlank() &&
                engineInstalledCount >= 2
    }

    private fun showIgnoredVersionsDialog() {
        val context = requireContext()

        MaterialDialog.Builder(context)
            .title(R.string.text_ignored_updates)
            .options(
                listOf(
                    MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_details)) {
                        MaterialDialog.Builder(context)
                            .title(R.string.text_details)
                            .content(R.string.description_manage_ignored_updates_preference)
                            .positiveText(R.string.dialog_button_dismiss)
                            .positiveColorRes(R.color.dialog_button_default)
                            .showAdaptive()
                    },
                ),
            )
            .content(R.string.text_no_ignored_updates)
            .items(ignoredVersionSummaries)
            .itemsLongCallback { dialog, _, _, text ->
                false.also { showRemoveIgnoredVersionPrompt(context, dialog, null, text) }
            }
            .itemsCallback { dialog, _, _, text ->
                val simpleInfo = VersionInfo.parseSummary(text)
                MaterialDialog.Builder(context)
                    .title(R.string.text_version_info)
                    .content(
                        "${context.getString(R.string.text_version_name)}: ${simpleInfo.versionName}\n" +
                                "${context.getString(R.string.text_version_code)}: ${simpleInfo.versionCode}"
                    )
                    .neutralText(R.string.dialog_button_remove)
                    .neutralColorRes(R.color.dialog_button_warn)
                    .onNeutral { dVersionInfo, _ ->
                        showRemoveIgnoredVersionPrompt(context, dialog, dVersionInfo, text)
                    }
                    .positiveText(R.string.dialog_button_back)
                    .positiveColorRes(R.color.dialog_button_default)
                    .onPositive { dVersionInfo, _ -> dVersionInfo.dismiss() }
                    .autoDismiss(false)
                    .show()
            }
            .choiceWidgetThemeColor()
            .neutralText(R.string.dialog_button_clear_items)
            .neutralColorRes(R.color.dialog_button_warn)
            .onNeutral { dialogParent, _ ->
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.text_confirm_to_clear_all_items)
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_caution)
                    .onPositive { _, _ ->
                        ignoredVersionSummaries.forEach {
                            val versionInfo = VersionInfo.parseSummary(it)
                            UpdateIgnoreStore.unignoreVersion(packageName, versionInfo.versionCode)
                        }
                        dialogParent.items?.let {
                            it.clear()
                            dialogParent.notifyItemsChanged()
                            dialogParent.toggleContentViewByItems()
                            dialogParent.toggleActionButtonAbilityByItems(DialogAction.NEUTRAL)
                        }
                        refreshIgnoredVersionSummary()
                        ViewUtils.showSnack(dialogParent.view, R.string.text_all_items_cleared)
                    }
                    .showAdaptive()
            }
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .onPositive { dialog, _ -> dialog.dismiss() }
            .autoDismiss(false)
            .build()
            .also {
                it.toggleContentViewByItems()
                it.toggleActionButtonAbilityByItems(DialogAction.NEUTRAL)
            }
            .showAdaptive()
    }

    private fun showRemoveIgnoredVersionPrompt(context: Context, dialogHoldingItems: MaterialDialog, dialogPromptParent: MaterialDialog?, text: CharSequence) {
        MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(R.string.text_confirm_to_remove)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                dialogHoldingItems.items!!.remove(text)
                dialogHoldingItems.notifyItemsChanged()
                ignoredVersionSummaries -= text.toString()
                val versionInfo = VersionInfo.parseSummary(text.toString())
                UpdateIgnoreStore.unignoreVersion(packageName, versionInfo.versionCode)
                refreshIgnoredVersionSummary()
                dialogHoldingItems.toggleContentViewByItems()
                dialogPromptParent?.dismiss()
            }
            .showAdaptive()
    }

    private fun showMountObjectNameDialog() {
        val defaultValue = PluginMountStore.defaultObjectName(packageName)
        val currentValue = PluginMountStore.getObjectName(packageName).orEmpty()
        val packageSegments = packageName.split('.').map { it.trim() }.filter { it.isNotBlank() }
        val titleCasePresets = packageSegments.map { segment ->
            segment.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString()
            }
        }
        val lowerCasePresets = packageSegments.map { it.lowercase(Locale.ROOT) }

        showMountInputDialog(
            titleRes = R.string.text_plugin_mount_object_name,
            hint = defaultValue,
            initialValue = currentValue,
            primaryPresets = titleCasePresets.map { Preset(it, it) },
            secondaryPresets = lowerCasePresets.map { Preset(it, it) },
        ) { nextObjectName, dialog ->
            applyMountChange(
                objectName = nextObjectName,
                mountPoint = PluginMountStore.getMountPoint(packageName),
                onApply = {
                    PluginMountStore.setObjectName(packageName, nextObjectName)
                    refreshMountSection()
                    dialog.dismiss()
                },
            )
        }
    }

    private fun showMountPointDialog() {
        val globalDisplay = getString(R.string.text_plugin_mount_point_global_display)
        val currentValue = PluginMountStore.getMountPoint(packageName).orEmpty()
        val presets = listOf(
            Preset(globalDisplay, "global"),
            Preset("app", "app"),
            Preset("autojs", "autojs"),
            Preset("automator", "automator"),
            Preset("canvas", "canvas"),
            Preset("color", "color"),
            Preset("console", "console"),
            Preset("device", "device"),
            Preset("dialogs", "dialogs"),
            Preset("engines", "engines"),
            Preset("events", "events"),
            Preset("files", "files"),
            Preset("floaty", "floaty"),
            Preset("http", "http"),
            Preset("image", "image"),
            Preset("media", "media"),
            Preset("notice", "notice"),
            Preset("ocr", "ocr"),
            Preset("shell", "shell"),
            Preset("sqlite", "sqlite"),
            Preset("storages", "storages"),
            Preset("tasks", "tasks"),
            Preset("threads", "threads"),
            Preset("timers", "timers"),
            Preset("ui", "ui"),
            Preset("web", "web"),
        )

        showMountInputDialog(
            titleRes = R.string.text_plugin_mount_point,
            hint = globalDisplay,
            initialValue = currentValue,
            primaryPresets = presets,
        ) { nextMountPoint, dialog ->
            applyMountChange(
                objectName = PluginMountStore.getObjectName(packageName),
                mountPoint = nextMountPoint,
                onApply = {
                    PluginMountStore.setMountPoint(packageName, nextMountPoint)
                    refreshMountSection()
                    dialog.dismiss()
                },
            )
        }
    }

    private fun showMountInputDialog(
        titleRes: Int,
        hint: String,
        initialValue: String,
        primaryPresets: List<Preset>,
        secondaryPresets: List<Preset> = emptyList(),
        onConfirm: (String?, MaterialDialog) -> Unit,
    ) {
        val context = requireContext()
        val binding = DialogPluginMountInputBinding.inflate(LayoutInflater.from(context))
        val contentView = binding.root
        val input = binding.input
        val primaryGroup = binding.presetPrimary
        val secondaryGroup = binding.presetSecondary

        input.hint = hint
        input.setHintTextColor(context.getColor(R.color.day_night_alpha_30))
        input.setText(initialValue)
        input.setSelection(input.text?.length ?: 0)
        ThemeColorHelper.setThemeColorPrimary(input, true)

        bindPresetButtons(primaryGroup, primaryPresets, input)
        if (secondaryPresets.isEmpty()) {
            secondaryGroup.visibility = View.GONE
        } else {
            secondaryGroup.visibility = View.VISIBLE
            bindPresetButtons(secondaryGroup, secondaryPresets, input)
        }

        MaterialDialog.Builder(context)
            .title(titleRes)
            .customView(contentView, false)
            .widgetThemeColor()
            .neutralText(R.string.dialog_button_clear)
            .neutralColorRes(R.color.dialog_button_caution)
            .onNeutral { d, _ ->
                d.customView?.findViewById<EditText>(R.id.input)?.apply {
                    setText("")
                    setSelection(0)
                }
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { d, _ ->
                val nextValue = d.customView?.findViewById<EditText>(R.id.input)?.text?.toString()
                onConfirm(nextValue, d)
            }
            .autoDismiss(false)
            .show()
    }

    private fun bindPresetButtons(container: LinearLayout, presets: List<Preset>, input: EditText) {
        container.removeAllViews()
        val columns = when {
            presets.size <= 6 -> 3
            else -> 4
        }
        val rows = distributePresetsBySpan(presets, max(1, columns))
        val rowGap = dp(6)
        val colGap = dp(8)
        val horizontalPadding = dp(12)
        val verticalPadding = dp(6)

        rows.forEachIndexed { rowIndex, rowPresets ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            rowPresets.forEachIndexed { colIndex, preset ->
                val button = AppCompatTextView(requireContext()).apply {
                    text = preset.preset.label
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                    setTextColor(context.getColor(R.color.text_color_primary))
                    setBackgroundResource(R.drawable.bg_plugin_mount_preset_token)
                    setOnClickListener {
                        val currentText = input.text?.toString().orEmpty()
                        val next = currentText + preset.preset.value
                        input.setText(next)
                        input.setSelection(next.length)
                    }
                }
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, preset.span.toFloat()).apply {
                    if (colIndex != rowPresets.lastIndex) {
                        marginEnd = colGap
                    }
                }
                row.addView(button, lp)
            }

            val usedColumns = rowPresets.sumOf { it.span }
            repeat(max(0, columns - usedColumns)) { index ->
                val spacerLp = LinearLayout.LayoutParams(0, 0, 1f).apply {
                    if (index != columns - usedColumns - 1) {
                        marginEnd = colGap
                    }
                }
                row.addView(Space(requireContext()), spacerLp)
            }

            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (rowIndex != rows.lastIndex) {
                    bottomMargin = rowGap
                }
            }
            container.addView(row, rowLp)
        }
    }

    private fun distributePresetsBySpan(presets: List<Preset>, columns: Int): List<List<PresetWithSpan>> {
        if (presets.isEmpty()) return emptyList()
        val rows = mutableListOf<MutableList<PresetWithSpan>>()
        var currentRow = mutableListOf<PresetWithSpan>()
        var used = 0

        presets.forEach { preset ->
            val span = calculateSpan(preset.label, columns)
            if (used + span > columns && currentRow.isNotEmpty()) {
                rows += currentRow
                currentRow = mutableListOf()
                used = 0
            }
            currentRow += PresetWithSpan(preset, span)
            used += span
        }

        if (currentRow.isNotEmpty()) {
            rows += currentRow
        }
        return rows
    }

    private fun calculateSpan(label: String, columns: Int): Int {
        if (columns < 2) return 1
        val weightedLength = label.sumOf { ch -> if (ch.code <= 0x7F) 1 else 2 }
        val threshold = when {
            columns >= 4 -> 7
            columns == 3 -> 9
            else -> 11
        }
        return if (weightedLength >= threshold) 2 else 1
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun onAutoMountToggle(enabled: Boolean) {
        PluginMountStore.setEnabled(packageName, enabled)
        refreshMountSection()
    }

    private fun applyMountChange(
        objectName: String?,
        mountPoint: String?,
        onApply: () -> Unit,
    ) {
        val validation = runCatching {
            PluginMountValidator.validate(
                packageName = packageName,
                objectNameInput = objectName,
                mountPointInput = mountPoint,
            )
        }.getOrElse { error ->
            val messageRes = when (error.message) {
                "invalid_object_name" -> R.string.error_invalid_plugin_mount_object_name
                "invalid_mount_point" -> R.string.error_invalid_plugin_mount_point
                else -> R.string.text_unavailable
            }
            ViewUtils.showToast(requireContext(), messageRes, true)
            return
        }

        val conflict = validation.conflict
        if (conflict == null) {
            onApply()
            return
        }
        if (conflict.source == PluginMountValidator.ConflictSource.MOUNT_CONFIG) {
            showMountOccupiedByPluginDialog(
                fullPath = validation.fullPath,
                occupiedByPackageName = conflict.occupiedByPackageName,
            )
            return
        }

        MaterialDialog.Builder(requireContext())
            .title(R.string.text_prompt)
            .content(
                getString(
                    R.string.prompt_plugin_mount_conflict_override,
                    validation.fullPath,
                    conflict.type,
                )
            )
            .positiveText(R.string.dialog_button_continue)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { d, _ ->
                onApply()
                d.dismiss()
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .autoDismiss(false)
            .show()
    }

    private fun showMountOccupiedByPluginDialog(fullPath: String, occupiedByPackageName: String?) {
        val packageNameText = occupiedByPackageName?.takeIf { it.isNotBlank() } ?: getString(R.string.text_unavailable)
        val pluginName = occupiedByPackageName
            ?.takeIf { it.isNotBlank() }
            ?.let(::resolvePluginDisplayName)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.text_unavailable)

        MaterialDialog.Builder(requireContext())
            .title(R.string.text_prompt)
            .content(
                getString(
                    R.string.prompt_plugin_mount_occupied_by_plugin,
                    fullPath,
                    pluginName,
                    packageNameText,
                )
            )
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { d, _ -> d.dismiss() }
            .autoDismiss(false)
            .show()
    }

    private fun resolvePluginDisplayName(targetPackageName: String): String {
        val pm = requireContext().packageManager
        return runCatching {
            val appInfo = pm.getApplicationInfo(targetPackageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrNull().orEmpty().ifBlank { targetPackageName }
    }

    private data class Preset(
        val label: String,
        val value: String,
    )

    private data class PresetWithSpan(
        val preset: Preset,
        val span: Int,
    )

    companion object {
        private const val KEY_PLUGIN_LAUNCH_PAGE = "plugin_launch_page"
        private const val KEY_PLUGIN_VISIBILITY = "plugin_visibility"
        private const val KEY_PLUGIN_LAUNCHER_ICON = "plugin_launcher_icon"
        private const val KEY_PLUGIN_AUTO_CHECK = "plugin_auto_check_updates"
        private const val KEY_PLUGIN_IGNORED_VERSIONS = "plugin_ignored_versions"
        private const val KEY_CATEGORY_VARIANT_PRIORITY = "category_variant_priority"
        private const val KEY_PLUGIN_VARIANT_PRIORITY_MANAGE = "plugin_variant_priority_manage"
        private const val KEY_CATEGORY_AUTO_MOUNT = "category_auto_mount"
        private const val KEY_PLUGIN_AUTO_MOUNT_ENABLED = "plugin_auto_mount_enabled"
        private const val KEY_PLUGIN_MOUNT_OBJECT_NAME = "plugin_mount_object_name"
        private const val KEY_PLUGIN_MOUNT_POINT = "plugin_mount_point"
        private const val KEY_PLUGIN_INSTRUCTION_VIEW = "plugin_instruction_view"
        private const val KEY_CATEGORY_BUN_HOST_CAPABILITIES = "category_bun_host_capabilities"
        private const val KEY_PLUGIN_BUN_HOST_INFO_GRANT = "plugin_bun_host_info_grant"
        private const val KEY_PLUGIN_BUN_UI_TOAST_GRANT = "plugin_bun_ui_toast_grant"
        private const val KEY_PLUGIN_BUN_DEVICE_INFO_GRANT = "plugin_bun_device_info_grant"
        private const val KEY_PLUGIN_HIDE_CONFIRM_PROMPT = "key_\$_plugin_hide_confirm_prompt"

        fun newInstance(args: Bundle) = PluginSettingsFragment().apply { arguments = Bundle(args) }
    }
}
