package org.autojs.autojs.ui.main.drawer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.tool.FloatingButtonTool
import org.autojs.autojs.app.tool.JsonSocketClientTool
import org.autojs.autojs.app.tool.JsonSocketServerTool
import org.autojs.autojs.app.tool.McpServerTool
import org.autojs.autojs.core.plugin.mcp.McpServerUiPolicy
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.core.pref.keys.HistoryKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.ui.settings.McpServerSettingsLauncher
import kotlinx.coroutines.launch
import org.autojs.autojs.app.tool.PointerLocationTool
import org.autojs.autojs.app.tool.ScreenColorPickerTool
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.screencolorpicker.ScreenColorPickerPluginStateMonitor
import org.autojs.autojs.core.plugin.terminal.TerminalLauncher
import org.autojs.autojs.core.plugin.terminal.TerminalPluginState
import org.autojs.autojs.core.plugin.terminal.TerminalPluginStateMonitor
import org.autojs.autojs.core.pref.PrefRx
import org.autojs.autojs.external.foreground.AppForegroundService
import org.autojs.autojs.permission.AllFilesAccessPermission
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.permission.IgnoreBatteryOptimizationsPermission
import org.autojs.autojs.permission.MediaProjectionPermission
import org.autojs.autojs.permission.LocalNetworkPermission
import org.autojs.autojs.permission.PostNotificationsPermission
import org.autojs.autojs.permission.ShizukuPermission
import org.autojs.autojs.permission.UsageStatsPermission
import org.autojs.autojs.permission.VivoBackgroundPopupPermission
import org.autojs.autojs.permission.WriteSecureSettingsPermission
import org.autojs.autojs.permission.WriteSystemSettingsPermission
import org.autojs.autojs.permission.XiaomiBackgroundPopupPermission
import org.autojs.autojs.pluginclient.DevPluginClientReconnector
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.pluginclient.JsonSocketClient
import org.autojs.autojs.pluginclient.JsonSocketServer
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.service.AccessibilityServiceItemHelper
import org.autojs.autojs.service.ForegroundService
import org.autojs.autojs.service.NotificationService
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.storage.history.HistoryRetentionPolicy
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.app.ColorSelectBaseActivity
import org.autojs.autojs.ui.accessibility.A11yServiceManagerDialog
import org.autojs.autojs.ui.devplugin.ClientModeManagerDialog
import org.autojs.autojs.ui.devplugin.ServerModeManagerDialog
import org.autojs.autojs.ui.floating.CircularMenu
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.fragment.BindingDelegates.viewBinding
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.autojs.autojs.ui.settings.TrashAndVersionHistorySettingsActivity
import org.autojs.autojs.ui.storage.TrashActivity
import org.autojs.autojs.ui.storage.VersionHistoryActivity
import org.autojs.autojs.ui.wizard.SetupWizards
import org.autojs.autojs.util.ActivityLaunchOptions
import org.autojs.autojs.util.IntentUtils.App.exit
import org.autojs.autojs.util.IntentUtils.App.restart
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs.util.NotificationUtils
import org.autojs.autojs.util.RomUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.MODE
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FragmentDrawerBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerContract
import rikka.shizuku.Shizuku
import java.lang.ref.WeakReference
import java.util.Locale
import org.autojs.autojs.util.App as UtilApp

/**
 * Created by Stardust on Jan 30, 2017.
 * Transformed by SuperMonster003 on Sep 19, 2022.
 * Modified by SuperMonster003 as of Jan 9, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 5, 2026.
 */
open class DrawerFragment : Fragment() {

    private val binding by viewBinding(FragmentDrawerBinding::bind)

    /**
     * Rx disposables for drawer subtitle observing.
     * zh-CN: 抽屉子标题监听的 Rx 订阅管理器.
     */
    private val drawerStatsDisposables: CompositeDisposable = CompositeDisposable()

    private var privateDrawerMenu: RecyclerView? = null

    // The Fragment stays attached after its view is destroyed, including during onDestroy cleanup.
    // zh-CN: Fragment 的视图销毁后仍保持附着, 包括 onDestroy 清理阶段.
    private val mContext: Context
        get() = requireContext()

    private val mActivity: MainActivity
        get() = requireActivity() as MainActivity

    private var mDrawerMenu
        set(value) {
            privateDrawerMenu = value
        }
        get() = privateDrawerMenu!!

    private lateinit var mAccessibilityServiceItem: DrawerMenuToggleableItem
    private lateinit var mForegroundServiceItem: DrawerMenuToggleableItem
    private lateinit var mFloatingButtonItem: DrawerMenuToggleableItem
    private lateinit var mPointerLocationItem: DrawerMenuToggleableItem
    private lateinit var mScreenColorPickerItem: DrawerMenuToggleableItem
    private lateinit var mTerminalItem: DrawerMenuToggleableItem
    private lateinit var mClientModeItem: DrawerMenuDisposableItem
    private lateinit var mServerModeItem: DrawerMenuDisposableItem
    private lateinit var mMcpServerItem: DrawerMenuDisposableItem
    private lateinit var mNotificationPostItem: DrawerMenuToggleableItem
    private lateinit var mLocalNetworkPermissionItem: DrawerMenuToggleableItem
    private lateinit var mNotificationAccessItem: DrawerMenuToggleableItem
    private lateinit var mAllFilesAccessPermissionItem: DrawerMenuToggleableItem
    private lateinit var mUsageStatsPermissionItem: DrawerMenuToggleableItem
    private lateinit var mIgnoreBatteryOptimizationsItem: DrawerMenuToggleableItem
    private lateinit var mDisplayOverOtherAppsItem: DrawerMenuToggleableItem
    private lateinit var mXiaomiBackgroundPopupPermissionItem: DrawerMenuToggleableItem
    private lateinit var mVivoBackgroundPopupPermissionItem: DrawerMenuToggleableItem
    private lateinit var mWriteSystemSettingsItem: DrawerMenuToggleableItem
    private lateinit var mWriteSecuritySettingsItem: DrawerMenuToggleableItem
    private lateinit var mProjectMediaAccessItem: DrawerMenuToggleableItem
    private lateinit var mShizukuAccessItem: DrawerMenuToggleableItem
    private lateinit var mNightModeItem: DrawerMenuToggleableItem
    private lateinit var mAutoNightModeItem: DrawerMenuToggleableItem
    private lateinit var mKeepScreenOnWhenInForegroundItem: DrawerMenuToggleableItem
    private lateinit var mThemeColorItem: DrawerMenuShortcutItem
    private lateinit var mTrashItem: DrawerMenuShortcutItem
    private lateinit var mVersionHistoryItem: DrawerMenuShortcutItem
    private lateinit var mAboutAppAndDevItem: DrawerMenuShortcutItem

    private lateinit var mA11yTool: AccessibilityTool
    private lateinit var mScreenColorPickerTool: ScreenColorPickerTool

    // Held weakly so that it can be dismissed with the Activity (its 1 s refresh timer and registry
    // listener stop with the dismiss) without keeping a finished dialog alive.
    // zh-CN: 弱引用持有, 以便随 Activity 一起关闭 (其 1 s 刷新定时器与注册表监听者随 dismiss 停止), 且不延长已关闭对话框的生命周期.
    private var mA11yServiceManagerDialog: WeakReference<MaterialDialog>? = null

    // The VSCode connection managers (client / server mode), held the same way.
    // zh-CN: VSCode 连接管理器 (客户端 / 服务端模式), 以同样方式持有.
    private var mClientModeManagerDialog: WeakReference<MaterialDialog>? = null
    // The terminal plugin state monitor publishes on the main thread (plugin roadmap D26).
    // zh-CN: 终端插件状态监听器在主线程发布 (插件路线图 D26).
    private val terminalPluginStateListener: (TerminalPluginStateMonitor.Change) -> Unit = { _ ->
        if (view != null && ::mTerminalItem.isInitialized) mTerminalItem.sync()
    }
    private var mServerModeManagerDialog: WeakReference<MaterialDialog>? = null

    private val screenColorPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        if (::mScreenColorPickerTool.isInitialized) {
            mScreenColorPickerTool.refreshAfterActivityResult()
        }
    }

    private val screenColorPickerStateListener:
        (ScreenColorPickerPluginStateMonitor.Change) -> Unit = { change ->
            if (::mScreenColorPickerItem.isInitialized) {
                applyScreenColorPickerState(change.current)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EventBus.getDefault().register(this)

        @SuppressLint("NotifyDataSetChanged")
        ThemeChangeNotifier.themeChanged.observe(this) {
            updateThemeColorSubtitle()
        }

        mA11yTool = AccessibilityTool(mContext)

        mAccessibilityServiceItem = DrawerMenuToggleableItem(
            helper = object : AccessibilityServiceItemHelper(mContext) {

                // The switch shows the running state, so a malfunctioning service (enabled in system
                // settings but not bound) is off; the subtitle is what tells it apart from a stopped one.
                // zh-CN: 开关显示运行状态, 因此故障 (系统设置中已启用但未绑定) 的服务为关闭; 副标题用于区分它与已停止的服务.
                override fun refreshSubtitle(aimState: Boolean) {
                    val oldSubtitle = mAccessibilityServiceItem.subtitle
                    mAccessibilityServiceItem.subtitle = when {
                        mA11yTool.isMalfunctioning() -> context.getString(R.string.text_malfunctioning)
                        else -> null
                    }
                    if (mAccessibilityServiceItem.subtitle != oldSubtitle) {
                        /* To refresh subtitle view. */
                        mAccessibilityServiceItem.isChecked = mAccessibilityServiceItem.isChecked
                    }
                    super.refreshSubtitle(aimState)
                }

            },
            icon = R.drawable.ic_accessibility_black_thicker_48dp,
            title = R.string.text_a11y_service,
            descriptionRes = R.string.description_accessibility_service,
            prefSetting = MiscStateKeys.A11Y_SERVICE,
        ).also { item ->
            item.setOnLaunchManagerListener { d ->
                // The description dialog is closed first, the manager takes its place.
                // zh-CN: 先关闭说明对话框, 由管理器取而代之.
                d?.dismiss()
                showA11yServiceManager()
            }
            item.setOnLaunchSettingsListener {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .startSafely(mContext)
            }
        }

        mForegroundServiceItem = DrawerMenuToggleableItem(
            helper = ForegroundService(mContext),
            icon = R.drawable.ic_service_green,
            title = R.string.text_foreground_service,
            descriptionRes = R.string.description_foreground_service,
            prefSetting = GeneralKeys.FOREGROUND_SERVICE,
        ).setOnLaunchSettingsListener {
            NotificationUtils.launchChannelSettings(mContext, AppForegroundService::class.java)
        }

        mFloatingButtonItem = DrawerMenuToggleableItem(
            helper = object : FloatingButtonTool(mContext) {
                override fun toggle(aimState: Boolean): Boolean = runCatching {
                    // @BeforeSuper
                    if (!aimState /* is to switch off */) {
                        FloatyWindowManger.getCircularMenu()?.let { circularMenu ->
                            if (circularMenu.isRecording) {
                                circularMenu.stopRecord()
                            }
                        }
                    }

                    super.toggle(aimState)

                    // @AfterSuper
                    // if (aimState /* is to switch on */) {
                    //     if (!mAccessibilityServiceItem.isChecked) {
                    //         mAccessibilityServiceItem.syncDelay()
                    //     }
                    // }
                }.isSuccess
            },
            icon = R.drawable.ic_robot_thicker_64,
            title = R.string.text_floating_button,
            descriptionRes = R.string.description_floating_button,
            prefSetting = MiscStateKeys.FLOATING_MENU_SHOWN,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                val helper = DisplayOverOtherAppsPermission(mContext)
                helper.config()
            }
        }

        mPointerLocationItem = DrawerMenuToggleableItem(
            helper = PointerLocationTool(mContext),
            icon = R.drawable.ic_control_point_bigger_black_48dp,
            title = R.string.text_pointer_location,
            descriptionRes = R.string.description_pointer_location,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                val helper = item.getHelper() as PointerLocationTool
                helper.config()
            }
        }

        mScreenColorPickerTool = ScreenColorPickerTool(mContext) { pendingIntent ->
            screenColorPickerLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                ActivityLaunchOptions.forVisibleSender(),
            )
        }
        mScreenColorPickerItem = DrawerMenuToggleableItem(
            helper = mScreenColorPickerTool,
            icon = R.drawable.ic_eyedropper_black_48dp,
            title = R.string.text_screen_color_picker,
            descriptionRes = R.string.description_screen_color_picker,
            onTitleContainerClickListener = { neutralText(R.string.text_settings) },
        ).also { item ->
            item.isHidden = true
            item.setOnLaunchManagerListener {
                mContext.packageManager.getLaunchIntentForPackage(ScreenColorPickerContract.DEFAULT_PACKAGE_NAME)
                    ?.startSafely(mContext)
            }
        }
        ScreenColorPickerPluginStateMonitor.addListener(
            mContext,
            screenColorPickerStateListener,
        )
        applyScreenColorPickerState(
            ScreenColorPickerPluginStateMonitor.current(mContext),
        )

        JsonSocketClientTool(mActivity).apply {
            val devPluginService = AutoJs.instance.devPluginService

            val drawerItem = DrawerMenuDisposableItem(
                helper = this,
                icon = R.drawable.ic_computer_black_48dp,
                title = R.string.text_client_mode,
                descriptionRes = R.string.description_client_mode,
            ) { helper ->
                // If connecting, show status dialog so user can interrupt.
                // zh-CN: 若正在连接, 显示状态对话框以便用户中止连接.
                (helper as? JsonSocketClientTool)?.showConnectingStatusDialogIfConnecting() == true
            }.also { mClientModeItem = it }

            drawerItem.setOnLaunchManagerListener { d ->
                // The description dialog is closed first, the manager takes its place.
                // zh-CN: 先关闭说明对话框, 由管理器取而代之.
                d?.dismiss()
                showClientModeManager(this@apply)
            }

            val disposable = Observable
                .combineLatest(
                    JsonSocketClient.cxnState,
                    devPluginService.clientConnectionIpAddress,
                    devPluginService.clientReconnector.status,
                ) { state: DevPluginService.State, ip: String, reconnect: DevPluginClientReconnector.Status ->
                    Triple(state, ip, reconnect)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (state, ip, reconnect) ->
                    // While a reconnect session waits for its next attempt the entry stays busy and says so.
                    // zh-CN: 重连会话等待下一次尝试期间, 条目保持忙碌并予以说明.
                    val isReconnecting = state.isDisconnected() && reconnect.isActive
                    drawerItem.subtitle = when {
                        isReconnecting -> "${reconnect.host ?: ip}  [ ${mContext.getString(R.string.text_reconnecting_with_attempts, reconnect.attempts)} ]"
                        state.isDisconnected() || state.exception != null -> null
                        else -> ip
                    }
                    drawerItem.setCheckedIfNeeded(state.isConnected())
                    drawerItem.isProgress = state.isConnecting() || isReconnecting
                }
            setStateDisposable(disposable)

            setOnConnectionException { _: Throwable ->
                drawerItem.setCheckedIfNeeded(false)
            }

            setOnConnectionDialogDismissed {
                drawerItem.setCheckedIfNeeded(false)
            }

            connectIfNotNormallyClosed()
        }

        JsonSocketServerTool(mActivity).apply {
            val devPluginService = AutoJs.instance.devPluginService

            val drawerItem = DrawerMenuDisposableItem(
                helper = this,
                icon = R.drawable.ic_smartphone_black_48dp,
                title = R.string.text_server_mode,
                descriptionRes = R.string.description_server_mode,
            ).also { item ->
                item.setOnLaunchManagerListener { d ->
                    d?.dismiss()
                    showServerModeManager(this@apply)
                }
                mServerModeItem = item
            }

            val disposable = Observable
                .combineLatest(
                    JsonSocketServer.cxnState,
                    devPluginService.serverConnectionCount,
                ) { state: DevPluginService.State, count: Int ->
                    Pair(state, count)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (state, count) ->
                    drawerItem.subtitle = when {
                        state.isDisconnected() -> null
                        else -> NetworkUtils.getIpAddress().let { ip ->
                            when {
                                count > 0 -> "$ip  [ ${this.context.getString(R.string.text_connected_count_with_colon, count)} ]"
                                else -> ip
                            }
                        }
                    }
                    drawerItem.setCheckedIfNeeded(!state.isDisconnected())
                    drawerItem.isProgress = state.isConnecting()
                    state.exception?.let { e ->
                        ViewUtils.showToast(mContext, e.message)
                    }
                }
            setStateDisposable(disposable)

            setOnConnectionException { e: Throwable ->
                drawerItem.setCheckedIfNeeded(false)
                ViewUtils.showToast(context, getString(R.string.error_enable_server, e.message), true)
            }
            setOnConnectionDialogDismissed { drawerItem.setCheckedIfNeeded(false) }
            connectIfNotNormallyClosed()
        }

        McpServerTool(mActivity).apply {
            val tool = this
            mMcpServerItem = DrawerMenuDisposableItem(
                helper = tool,
                icon = R.drawable.ic_mcp_black_48dp,
                title = R.string.text_mcp_server,
                descriptionRes = R.string.description_mcp_server,
            ) {
                neutralText(R.string.text_settings)
            }
            mMcpServerItem.setOnLaunchManagerListener { d -> d?.dismiss(); McpServerSettingsLauncher.launch(mActivity) }
            lifecycleScope.launch {
                state.collect { value ->
                    mMcpServerItem.subtitle = McpServerTool.subtitle(mContext, value)
                    mMcpServerItem.setCheckedIfNeeded(McpServerUiPolicy.checked(value.state))
                    mMcpServerItem.isProgress = McpServerUiPolicy.progress(value.state)
                }
            }
            connectIfNotNormallyClosed()
        }

        mLocalNetworkPermissionItem = DrawerMenuToggleableItem(
            helper = LocalNetworkPermission(mContext),
            icon = R.drawable.ic_lan_black_48dp,
            title = R.string.text_local_network_permission,
            descriptionRes = R.string.description_local_network_permission,
        ).also { item ->
            item.isHidden = !LocalNetworkPermission.isRequired(mContext)
            item.setOnLaunchSettingsListener { (item.getHelper() as LocalNetworkPermission).config() }
        }

        mNotificationPostItem = DrawerMenuToggleableItem(
            helper = PostNotificationsPermission(mContext),
            icon = R.drawable.ic_ali_notification,
            title = R.string.text_post_notifications_permission,
            descriptionRes = R.string.description_post_notifications,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                val helper = item.getHelper() as PostNotificationsPermission
                helper.config()
            }
        }

        mNotificationAccessItem = DrawerMenuToggleableItem(
            helper = NotificationService(mContext),
            icon = R.drawable.ic_ali_notification,
            title = R.string.text_notification_access_permission,
            descriptionRes = R.string.description_notification_access,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                val helper = item.getHelper() as NotificationService
                helper.config()
            }
        }

        mAllFilesAccessPermissionItem = DrawerMenuToggleableItem(
            helper = AllFilesAccessPermission(mContext),
            icon = R.drawable.ic_database_black_48dp,
            title = R.string.text_all_files_access,
            descriptionRes = R.string.description_all_files_access,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                val helper = item.getHelper() as AllFilesAccessPermission
                helper.config()
            }
        }

        mUsageStatsPermissionItem = DrawerMenuToggleableItem(
            helper = UsageStatsPermission(mContext),
            icon = R.drawable.ic_assessment_black_48dp,
            title = R.string.text_usage_stats_permission,
            descriptionRes = R.string.description_usage_stats_access,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                val helper = item.getHelper() as UsageStatsPermission
                helper.config()
            }
        }

        mIgnoreBatteryOptimizationsItem = DrawerMenuToggleableItem(
            helper = IgnoreBatteryOptimizationsPermission(mContext),
            icon = R.drawable.ic_battery_std_black_48dp,
            title = R.string.text_ignore_battery_optimizations,
            descriptionRes = R.string.description_ignore_battery_optimizations,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .startSafely(mContext)
            }
        }

        mDisplayOverOtherAppsItem = DrawerMenuToggleableItem(
            helper = DisplayOverOtherAppsPermission(mContext),
            icon = R.drawable.ic_layers_black_48dp,
            title = R.string.text_display_over_other_app,
            descriptionRes = R.string.description_display_over_other_app,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                val helper = item.getHelper() as DisplayOverOtherAppsPermission
                helper.config()
            }
        }

        mXiaomiBackgroundPopupPermissionItem = DrawerMenuToggleableItem(
            helper = XiaomiBackgroundPopupPermission(mContext),
            icon = R.drawable.ic_layers_black_48dp,
            title = R.string.text_xiaomi_background_popup_permission,
            descriptionRes = R.string.description_background_popup_permission,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                val helper = item.getHelper() as XiaomiBackgroundPopupPermission
                helper.config()
            }
            item.isHidden = !RomUtils.isMiui()
        }

        mVivoBackgroundPopupPermissionItem = DrawerMenuToggleableItem(
            helper = VivoBackgroundPopupPermission(mContext),
            icon = R.drawable.ic_layers_black_48dp,
            title = R.string.text_vivo_background_popup_permission,
            descriptionRes = R.string.description_background_popup_permission,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                val helper = item.getHelper() as VivoBackgroundPopupPermission
                helper.config()
            }
            item.isHidden = !RomUtils.isVivo()
        }

        mWriteSystemSettingsItem = DrawerMenuToggleableItem(
            helper = WriteSystemSettingsPermission(mContext),
            icon = R.drawable.ic_settings_black_48dp,
            title = R.string.text_write_system_settings,
            descriptionRes = R.string.description_write_system_settings,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                val helper = item.getHelper() as WriteSystemSettingsPermission
                helper.config()
            }
        }

        mWriteSecuritySettingsItem = DrawerMenuToggleableItem(
            helper = WriteSecureSettingsPermission(mContext),
            icon = R.drawable.ic_security_black_48dp,
            title = R.string.text_write_secure_settings,
            descriptionRes = R.string.description_write_secure_settings,
        )

        mProjectMediaAccessItem = DrawerMenuToggleableItem(
            helper = MediaProjectionPermission(mContext),
            icon = R.drawable.ic_cast_connected_black_48dp,
            title = R.string.text_project_media_access,
            descriptionRes = R.string.description_project_media_access,
        )

        mShizukuAccessItem = DrawerMenuToggleableItem(
            helper = ShizukuPermission(mContext),
            icon = R.drawable.ic_app_shizuku_representative,
            title = R.string.text_shizuku_access,
            descriptionRes = R.string.description_shizuku_access,
        ) {
            positiveText(UtilApp.SHIZUKU.getAppName())
            positiveTextAllCaps(false)
            positiveColorRes(R.color.dialog_button_advanced_settings)
            onPositive { d, _ ->
                when {
                    !WrappedShizuku.isInstalled(mContext) -> {
                        ViewUtils.showSnack(d.view, R.string.error_shizuku_is_not_installed)
                    }
                    Shizuku.isPreV11() -> {
                        ViewUtils.showSnack(d.view, R.string.error_shizuku_version_is_not_supported)
                    }
                    else -> WrappedShizuku.getLaunchIntent(mContext)
                        ?.startSafely(mContext)
                        ?: ViewUtils.showSnack(d.view, R.string.error_failed_to_revoke_shizuku_access)
                }
            }
        }

        mAutoNightModeItem = DrawerMenuToggleableItem(
            helper = object : DrawerMenuItemCustomHelper(mContext) {
                override fun toggle(): Boolean = runCatching {
                    val isTurningOn = !isActive
                    val isNightModeYes = ViewUtils.isNightModeYes(resources.configuration)
                    val mode = when {
                        isTurningOn -> MODE.FOLLOW
                        isNightModeYes -> MODE.NIGHT
                        else -> MODE.DAY
                    }
                    ViewUtils.setDefaultNightMode(mode)
                    if (isTurningOn) {
                        ViewUtils.isNightModeEnabled = ViewUtils.isSystemDarkModeEnabled(mContext)
                        AppearanceKeys.NIGHT_MODE.set(MODE.FOLLOW.key)
                    } else {
                        when (isNightModeYes) {
                            true -> MODE.NIGHT.key
                            else -> MODE.DAY.key
                        }.let { AppearanceKeys.NIGHT_MODE.set(it) }
                    }
                }.isSuccess

                override val isActive
                    get() = ViewUtils.isAutoNightModeEnabled

                override val isInMainThread = true
            },
            icon = R.drawable.ic_automatic_brightness_thicker,
            title = R.string.text_auto_night_mode,
            descriptionRes = R.string.description_auto_night_mode,
            prefSetting = AppearanceKeys.AUTO_NIGHT_MODE_ENABLED,
        ).also { item ->
            item.setOnLaunchSettingsListener {
                Intent(Settings.ACTION_DISPLAY_SETTINGS)
                    .startSafely(mContext)
            }
            item.isHidden = !ViewUtils.AutoNightMode.isFunctional()
        }

        mNightModeItem = DrawerMenuToggleableItem(
            helper = object : DrawerMenuItemCustomHelper(mContext) {
                override fun toggle(): Boolean = runCatching {
                    if (!mAutoNightModeItem.isHidden) {
                        ViewUtils.isAutoNightModeEnabled = false
                    }
                    when (/* isTurningOn */ !isActive) {
                        true -> {
                            ViewUtils.setDefaultNightMode(MODE.NIGHT)
                            ViewUtils.isNightModeEnabled = true
                            AppearanceKeys.NIGHT_MODE.set(MODE.NIGHT.key)
                        }
                        else -> {
                            ViewUtils.setDefaultNightMode(MODE.DAY)
                            ViewUtils.isNightModeEnabled = false
                            AppearanceKeys.NIGHT_MODE.set(MODE.DAY.key)
                        }
                    }
                }.isSuccess

                override val isActive
                    get() = ViewUtils.isNightModeEnabled

                override val isInMainThread = true
            },
            icon = R.drawable.ic_night_mode_thicker,
            title = R.string.text_night_mode,
            descriptionRes = R.string.description_night_mode,
            prefSetting = AppearanceKeys.NIGHT_MODE_ENABLED,
        ).setOnLaunchSettingsListener {
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
                .startSafely(mContext)
        }

        mKeepScreenOnWhenInForegroundItem = DrawerMenuToggleableItem(
            helper = object : DrawerMenuItemCustomHelper(mContext) {
                override val isActive: Boolean
                    get() = ViewUtils.isKeepScreenOnWhenInForegroundEnabled

                override fun toggle(): Boolean = runCatching {
                    when (/* isTurningOn */ !isActive) {
                        true -> ViewUtils.setKeepScreenOnWhenInForegroundFromLastEnabledState()
                        else -> ViewUtils.setKeepScreenOnWhenInForegroundDisabled()
                    }
                    refreshSubtitle(!isActive)
                }.isSuccess

                override fun refreshSubtitle(aimState: Boolean) {
                    val oldSubtitle = mKeepScreenOnWhenInForegroundItem.subtitle
                    val aimSubtitle = if (ViewUtils.isKeepScreenOnWhenInForegroundDisabled) null else {
                        val i = resources.getStringArray(R.array.keys_keep_screen_on_when_in_foreground).indexOf(AppearanceKeys.keepScreenOnWhenInForegroundKey)
                        resources.getStringArray(R.array.values_keep_screen_on_when_in_foreground)[i]
                    }
                    if (mKeepScreenOnWhenInForegroundItem.subtitle != aimSubtitle) {
                        mKeepScreenOnWhenInForegroundItem.subtitle = aimSubtitle
                        if (mKeepScreenOnWhenInForegroundItem.subtitle != oldSubtitle) {
                            /* To refresh subtitle view. */
                            mKeepScreenOnWhenInForegroundItem.isChecked = mKeepScreenOnWhenInForegroundItem.isChecked
                        }
                    }
                    super.refreshSubtitle(aimState)
                }
            },
            icon = R.drawable.ic_lightbulb_outline_black_thicker_48dp,
            title = R.string.text_keep_screen_on_when_in_foreground,
            descriptionRes = R.string.description_keep_screen_on_when_in_foreground,
        )

        // The terminal lives in the 3-Shell Terminal plugin (plugin roadmap D3 / D25 / D26): the switch
        // mirrors the plugin's session count while the plugin is available, and the row degrades to a
        // plain entry whose tap, title and manager actions all route through TerminalLauncher otherwise.
        // zh-CN: 终端由 3-Shell Terminal 插件提供 (插件路线图 D3 / D25 / D26): 插件可用时开关镜像其会话数,
        // 否则该行退化为普通条目, 点击 / 标题 / 管理器动作全部经 TerminalLauncher 路由.
        mTerminalItem = DrawerMenuToggleableItem(
            helper = object : DrawerMenuItemCustomHelper(mContext) {
                override val isInMainThread = true
                override val isActive get() = TerminalPluginStateMonitor.current(context).sessionCount > 0
                override fun toggle(): Boolean {
                    if (isActive) TerminalLauncher.closeAll(context) else TerminalLauncher.openNew(context)
                    return true
                }
                override fun toggle(aimState: Boolean): Boolean {
                    if (!TerminalPluginStateMonitor.current(context).available) {
                        TerminalLauncher.open(context)
                        return true
                    }
                    return super.toggle(aimState)
                }
                override fun refreshSubtitle(aimState: Boolean) {
                    val state = TerminalPluginStateMonitor.current(context)
                    val subtitle = when (state.status) {
                        TerminalPluginState.AVAILABLE -> state.sessionCount.takeIf { it > 0 }?.let { context.getString(R.string.text_terminal_running_sessions, it) }
                        TerminalPluginState.NOT_INSTALLED -> context.getString(R.string.text_terminal_plugin_required)
                        TerminalPluginState.DISABLED -> context.getString(R.string.text_terminal_plugin_disabled)
                        TerminalPluginState.INCOMPATIBLE -> context.getString(R.string.text_terminal_plugin_incompatible)
                        TerminalPluginState.UNAVAILABLE -> context.getString(R.string.text_terminal_plugin_unavailable)
                    }
                    if (mTerminalItem.subtitle != subtitle || mTerminalItem.isSwitchEnabled != state.available) {
                        mTerminalItem.subtitle = subtitle
                        mTerminalItem.isSwitchEnabled = state.available
                        mTerminalItem.isChecked = mTerminalItem.isChecked
                    }
                }
            },
            icon = R.drawable.ic_terminal_black_48dp,
            title = R.string.text_terminal,
            descriptionRes = R.string.description_terminal,
            onTitleContainerClickListener = {
                positiveText(R.string.text_terminal_new_session)
                positiveColorRes(R.color.dialog_button_attraction)
                onPositive { d, _ -> d.dismiss(); TerminalLauncher.openNew(mContext) }
            },
        ).also { item ->
            item.setOnLaunchManagerListener { d ->
                d?.dismiss()
                TerminalLauncher.openManager(mContext)
            }
        }
        TerminalPluginStateMonitor.addListener(mContext, terminalPluginStateListener)

        mThemeColorItem = DrawerMenuShortcutItem(
            icon = R.drawable.ic_personalize_thicker,
            title = R.string.text_theme_color,
        ).apply {
            setAction { ColorSelectBaseActivity.launch(mContext) }
            subtitle = ColorSelectBaseActivity.getCurrentColorSummary(mContext)
        }

        mTrashItem = DrawerMenuShortcutItem(
            icon = R.drawable.ic_recycle_bin,
            title = R.string.text_trash,
        ).apply {
            setAction { TrashActivity.launch(mContext) }
            setLongClickAction { TrashAndVersionHistorySettingsActivity.launch(mContext) }
        }

        mVersionHistoryItem = DrawerMenuShortcutItem(
            icon = R.drawable.ic_version_history,
            title = R.string.text_version_history,
        ).apply {
            setAction { VersionHistoryActivity.launch(mContext) }
            setLongClickAction { TrashAndVersionHistorySettingsActivity.launch(mContext) }
        }

        mAboutAppAndDevItem = DrawerMenuShortcutItem(
            icon = R.drawable.ic_about,
            title = R.string.text_about_app_and_developer,
        ).apply {
            setAction { AboutActivity.launch(mContext) }
            subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentDrawerBinding.inflate(inflater, container, false).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mDrawerMenu = binding.drawerMenu
        initMenuItems()
        initMenuItemStates()
        setupListeners()

        // Start observing stats for drawer subtitles.
        // zh-CN: 开始监听抽屉子标题所需的统计信息.
        observeTrashAndHistoryDrawerSubtitles()
    }

    private fun observeTrashAndHistoryDrawerSubtitles() {
        val appCtx = mContext.applicationContext
        val dao = HistoryDatabase.getInstance(appCtx).historyDao()

        // Trash: observe DB stats + observe limit preference.
        // zh-CN: 回收站: 监听 DB 统计 + 监听容量上限偏好.
        val trashLimitFlow = PrefRx.observeLong(HistoryKeys.TRASH_MAX_TOTAL_BYTES)

        val trashDisposable = Observable
            .combineLatest(
                dao.observeTrashStats().toObservable(),
                trashLimitFlow.toObservable(),
            ) { stats, limitBytes ->
                val count = stats.count.coerceAtLeast(0L)
                val totalBytes = (stats.totalBytes ?: 0L).coerceAtLeast(0L)
                val sizeText = PFiles.formatSizeWithUnit(totalBytes)

                // An unlimited size limit (zero or negative) has no meaningful percentage.
                // zh-CN: 容量上限为不限制 (0 或负数) 时, 百分比没有意义.
                if (HistoryRetentionPolicy.isUnlimited(limitBytes)) {
                    "$count | $sizeText"
                } else {
                    val ratio = (totalBytes.toDouble() / limitBytes.toDouble()).coerceAtLeast(0.0)
                    "$count | $sizeText | ${String.format(Locale.getDefault(), "%.1f%%", ratio * 100.0)}"
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { subtitle ->
                mTrashItem.subtitle = subtitle
                (mDrawerMenu.adapter as? DrawerMenuAdapter)?.notifyItemChanged(mTrashItem)
            }

        drawerStatsDisposables.add(trashDisposable)

        // Version history: observe DB stats + observe limit preference.
        // zh-CN: 版本历史: 监听 DB 统计 + 监听容量上限偏好.
        val historyLimitFlow = PrefRx.observeLong(HistoryKeys.HISTORY_MAX_TOTAL_BYTES)

        val historyDisposable = Observable
            .combineLatest(
                dao.observeVersionHistoryStats().toObservable(),
                historyLimitFlow.toObservable(),
            ) { stats, limitBytes ->
                val count = stats.fileCount.coerceAtLeast(0L)
                val totalBytes = (stats.totalBytes ?: 0L).coerceAtLeast(0L)
                val sizeText = PFiles.formatSizeWithUnit(totalBytes)

                // An unlimited size limit (zero or negative) has no meaningful percentage.
                // zh-CN: 容量上限为不限制 (0 或负数) 时, 百分比没有意义.
                if (HistoryRetentionPolicy.isUnlimited(limitBytes)) {
                    "$count | $sizeText"
                } else {
                    val ratio = (totalBytes.toDouble() / limitBytes.toDouble()).coerceAtLeast(0.0)
                    "$count | $sizeText | ${String.format(Locale.getDefault(), "%.1f%%", ratio * 100.0)}"
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { subtitle ->
                mVersionHistoryItem.subtitle = subtitle
                (mDrawerMenu.adapter as? DrawerMenuAdapter)?.notifyItemChanged(mVersionHistoryItem)
            }

        drawerStatsDisposables.add(historyDisposable)
    }

    private fun setupListeners() {
        binding.settings.setOnClickListener { PreferencesActivity.launch(it.context) }
        binding.pluginCenter.setOnClickListener { PluginCenterActivity.launch(it.context) }
        binding.restart.setOnClickListener { restart(mActivity, mActivity::beforeExit) }
        binding.exit.setOnClickListener { exit(mActivity, mActivity::beforeExit) }

        // Consume touches on drawer blank area to prevent "press state" leaking to RecyclerView items.
        // zh-CN: 消费抽屉空白区域触摸, 防止按压状态影响 RecyclerView item 的 ripple/pressed 表现.
        binding.drawerMenuContainer.setOnTouchListener { v, ev ->
            val rv = binding.drawerMenu
            val buttonsRow = binding.settings.parent as? View

            fun hitTest(target: View?): Boolean {
                if (target == null) return false
                val loc = IntArray(2)
                target.getLocationOnScreen(loc)
                val left = loc[0]
                val top = loc[1]
                val right = left + target.width
                val bottom = top + target.height
                val x = ev.rawX.toInt()
                val y = ev.rawY.toInt()
                return x in left until right && y in top until bottom
            }

            val onRecycler = hitTest(rv)
            val onButtons = hitTest(buttonsRow)

            if (onRecycler || onButtons) {
                false
            } else {
                // Keep container from staying pressed.
                // zh-CN: 避免容器保持 pressed 状态.
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    v.isPressed = false
                }
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mScreenColorPickerTool.refresh()
        TerminalPluginStateMonitor.refresh()
        syncMenuItemStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        mClientModeItem.dispose()
        mServerModeItem.dispose()
        mMcpServerItem.dispose()

        dismissA11yServiceManager()
        dismissConnectionManagers()
        TerminalPluginStateMonitor.removeListener(terminalPluginStateListener)
        TerminalPluginStateMonitor.setVisible(mContext, false)

        // mActivity.unregisterReceiver(mReceiver)

        ScreenColorPickerPluginStateMonitor.removeListener(screenColorPickerStateListener)

        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        // These subscriptions access the RecyclerView and restart in onViewCreated.
        // zh-CN: 这些订阅会访问 RecyclerView, 并在 onViewCreated 中重新建立.
        drawerStatsDisposables.clear()
        super.onDestroyView()
        privateDrawerMenu = null
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe
    fun onCircularMenuStateChange(event: CircularMenu.StateChangeEvent) {
        // mFloatingWindowItem.toggle(event.currentState != CircularMenu.STATE_CLOSED)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @Subscribe
    fun onPointerLocationStateChange(event: PointerLocationTool.Companion.StateChangedEvent) {
        mPointerLocationItem.sync()
    }

    @Subscribe
    @Suppress("unused", "UNUSED_PARAMETER")
    fun onDrawerOpened(event: Event.OnDrawerOpened) {
        // ViewCompat.getWindowInsetsController(mActivity.window.decorView)?.hide(WindowInsets.Type.systemBars())
        mScreenColorPickerTool.refresh()
        TerminalPluginStateMonitor.setVisible(mContext, true)
        syncMenuItemStates()
    }

    @Subscribe
    @Suppress("unused", "UNUSED_PARAMETER")
    fun onDrawerClosed(event: Event.OnDrawerClosed) {
        // ViewCompat.getWindowInsetsController(mActivity.window.decorView)?.show(WindowInsets.Type.systemBars())
        TerminalPluginStateMonitor.setVisible(mContext, false)
    }

    @Subscribe
    @Suppress("unused", "UNUSED_PARAMETER")
    fun onAccessibilityServiceStateChanged(event: Event.AccessibilityServiceStateChangedEvent) {
        mAccessibilityServiceItem.sync()
    }

    @Subscribe
    @Suppress("unused", "UNUSED_PARAMETER")
    fun onThemeColorLayoutSwitched(event: Event.ThemeColorLayoutSwitchedEvent) {
        updateThemeColorSubtitle()
    }

    private fun updateThemeColorSubtitle() {
        mThemeColorItem.subtitle = ColorSelectBaseActivity.getCurrentColorSummary(mContext)
        (privateDrawerMenu?.adapter as? DrawerMenuAdapter)?.notifyItemChanged(mThemeColorItem)
    }

    private fun showA11yServiceManager() {
        dismissA11yServiceManager()
        // The context is captured here, once; the dialog never reads the fragment's weak context later.
        // zh-CN: 上下文在此一次性捕获; 对话框之后不再读取 Fragment 的弱引用上下文.
        val dialog = A11yServiceManagerDialog.show(mContext, mA11yTool) {
            if (::mAccessibilityServiceItem.isInitialized) mAccessibilityServiceItem.sync()
        }
        mA11yServiceManagerDialog = WeakReference(dialog)
    }

    private fun dismissA11yServiceManager() {
        mA11yServiceManagerDialog?.get()?.let { dialog ->
            if (dialog.isShowing) runCatching { dialog.dismiss() }
        }
        mA11yServiceManagerDialog = null
    }

    private fun showClientModeManager(tool: JsonSocketClientTool) {
        dismissConnectionManagers()
        mClientModeManagerDialog = WeakReference(ClientModeManagerDialog.show(mContext, tool))
    }

    private fun showServerModeManager(tool: JsonSocketServerTool) {
        dismissConnectionManagers()
        mServerModeManagerDialog = WeakReference(ServerModeManagerDialog.show(mContext, tool))
    }

    private fun dismissConnectionManagers() {
        listOf(mClientModeManagerDialog, mServerModeManagerDialog).forEach { ref ->
            ref?.get()?.let { dialog -> if (dialog.isShowing) runCatching { dialog.dismiss() } }
        }
        mClientModeManagerDialog = null
        mServerModeManagerDialog = null
    }

    private fun initMenuItems() {
        val items = listOf(
            DrawerMenuGroup(R.string.text_service),
            mAccessibilityServiceItem,
            mForegroundServiceItem,
            DrawerMenuGroup(R.string.text_tools),
            mFloatingButtonItem,
            mPointerLocationItem,
            mScreenColorPickerItem,
            mTerminalItem,
            DrawerMenuGroup(R.string.text_remote_connections),
            mClientModeItem,
            mServerModeItem,
            mMcpServerItem,
            DrawerMenuGroup(R.string.text_permissions, R.drawable.ic_plugin_install_wizard, R.string.text_permission_wizard) {
                SetupWizards.showPermissionWizard(mActivity)
            },
            mNotificationPostItem,
            mNotificationAccessItem,
            mLocalNetworkPermissionItem,
            mAllFilesAccessPermissionItem,
            mUsageStatsPermissionItem,
            mIgnoreBatteryOptimizationsItem,
            mDisplayOverOtherAppsItem,
            mXiaomiBackgroundPopupPermissionItem,
            mVivoBackgroundPopupPermissionItem,
            mWriteSystemSettingsItem,
            mWriteSecuritySettingsItem,
            mProjectMediaAccessItem,
            mShizukuAccessItem,
            DrawerMenuGroup(R.string.text_appearance),
            mAutoNightModeItem,
            mNightModeItem,
            mKeepScreenOnWhenInForegroundItem,
            mThemeColorItem,
            DrawerMenuGroup(R.string.text_file),
            mTrashItem,
            mVersionHistoryItem,
            DrawerMenuGroup(R.string.text_about),
            mAboutAppAndDevItem,
        )

        val drawerMenuAdapter = DrawerMenuAdapter(items.filterNot { it.isHidden })

        // Wire item change notifications to this fragment's adapter instance.
        // zh-CN: 将 item 的刷新通知绑定到当前 Fragment 的 adapter 实例.
        items.filterIsInstance<DrawerMenuToggleableItem>().forEach { item ->
            item.setOnNotifyItemChangedListener { changedItem ->
                drawerMenuAdapter.notifyItemChanged(changedItem)
            }
        }

        mDrawerMenu.apply {
            adapter = drawerMenuAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun initMenuItemStates() = listOf(
        mFloatingButtonItem,
        mForegroundServiceItem,
    ).forEach { it.selfActive() }

    private fun syncMenuItemStates() = listOf(
        mAccessibilityServiceItem,
        mForegroundServiceItem,
        mFloatingButtonItem,
        mPointerLocationItem,
        mScreenColorPickerItem,
        mClientModeItem,
        mServerModeItem,
        mMcpServerItem,
        mNotificationPostItem,
        mNotificationAccessItem,
        mLocalNetworkPermissionItem,
        mAllFilesAccessPermissionItem,
        mUsageStatsPermissionItem,
        mIgnoreBatteryOptimizationsItem,
        mDisplayOverOtherAppsItem,
        mXiaomiBackgroundPopupPermissionItem,
        mVivoBackgroundPopupPermissionItem,
        mWriteSystemSettingsItem,
        mWriteSecuritySettingsItem,
        mProjectMediaAccessItem,
        mShizukuAccessItem,
        mKeepScreenOnWhenInForegroundItem,
    ).forEach { it.sync() }

    private fun applyScreenColorPickerState(
        state: ScreenColorPickerPluginStateMonitor.State,
    ) {
        val shouldHide = !state.visibleInDrawer
        val visibilityChanged = mScreenColorPickerItem.isHidden != shouldHide
        mScreenColorPickerItem.isHidden = shouldHide

        if (privateDrawerMenu != null && visibilityChanged) {
            initMenuItems()
        }
        if (shouldHide) return

        mScreenColorPickerItem.subtitle = when (state.status) {
            ScreenColorPickerPluginStateMonitor.Status.DISABLED -> getString(R.string.text_disabled)
            ScreenColorPickerPluginStateMonitor.Status.INELIGIBLE -> getString(R.string.text_unavailable)
            else -> null
        }
        mScreenColorPickerItem.setCheckedIfNeeded(state.active)
        mScreenColorPickerItem.isProgress = state.transitioning
        (privateDrawerMenu?.adapter as? DrawerMenuAdapter)
            ?.notifyItemChanged(mScreenColorPickerItem)
    }

    companion object {

        class Event {
            interface OnDrawerOpened
            interface OnDrawerClosed
            interface AccessibilityServiceStateChangedEvent
            interface ThemeColorLayoutSwitchedEvent
        }

    }

}
