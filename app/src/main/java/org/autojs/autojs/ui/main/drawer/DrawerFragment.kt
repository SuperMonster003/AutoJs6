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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.tool.FloatingButtonTool
import org.autojs.autojs.app.tool.JsonSocketClientTool
import org.autojs.autojs.app.tool.JsonSocketServerTool
import org.autojs.autojs.app.tool.PointerLocationTool
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.PrefRx
import org.autojs.autojs.external.foreground.AppForegroundService
import org.autojs.autojs.permission.AllFilesAccessPermission
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.permission.IgnoreBatteryOptimizationsPermission
import org.autojs.autojs.permission.MediaProjectionPermission
import org.autojs.autojs.permission.PostNotificationsPermission
import org.autojs.autojs.permission.ShizukuPermission
import org.autojs.autojs.permission.UsageStatsPermission
import org.autojs.autojs.permission.VivoBackgroundPopupPermission
import org.autojs.autojs.permission.WriteSecureSettingsPermission
import org.autojs.autojs.permission.WriteSystemSettingsPermission
import org.autojs.autojs.permission.XiaomiBackgroundPopupPermission
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.pluginclient.JsonSocketClient
import org.autojs.autojs.pluginclient.JsonSocketServer
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.service.AccessibilityService
import org.autojs.autojs.service.ForegroundService
import org.autojs.autojs.service.NotificationService
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.storage.history.HistoryPrefs
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.app.ColorSelectBaseActivity
import org.autojs.autojs.ui.floating.CircularMenu
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.fragment.BindingDelegates.viewBinding
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.autojs.autojs.ui.storage.TrashActivity
import org.autojs.autojs.ui.storage.VersionHistoryActivity
import org.autojs.autojs.util.DisplayUtils
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
import rikka.shizuku.Shizuku
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.roundToInt
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
    private var privateContext: WeakReference<Context?>? = null
    private var privateActivity: WeakReference<MainActivity?>? = null

    private var mContext
        set(value) {
            privateContext = WeakReference(value)
        }
        get() = privateContext!!.get()!!

    private var mActivity
        set(value) {
            privateActivity = WeakReference(value)
        }
        get() = privateActivity!!.get()!!

    private var mDrawerMenu
        set(value) {
            privateDrawerMenu = value
        }
        get() = privateDrawerMenu!!

    private lateinit var mAccessibilityServiceItem: DrawerMenuToggleableItem
    private lateinit var mForegroundServiceItem: DrawerMenuToggleableItem
    private lateinit var mFloatingButtonItem: DrawerMenuToggleableItem
    private lateinit var mPointerLocationItem: DrawerMenuToggleableItem
    private lateinit var mClientModeItem: DrawerMenuDisposableItem
    private lateinit var mServerModeItem: DrawerMenuDisposableItem
    private lateinit var mNotificationPostItem: DrawerMenuToggleableItem
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EventBus.getDefault().register(this)

        @SuppressLint("NotifyDataSetChanged")
        ThemeChangeNotifier.themeChanged.observe(this) {
            updateThemeColorSubtitle()
        }

        mContext = requireContext()

        mActivity = requireActivity() as MainActivity

        mA11yTool = AccessibilityTool(mContext)

        mAccessibilityServiceItem = DrawerMenuToggleableItem(
            helper = object : AccessibilityService(mContext) {

                override fun refreshSubtitle(aimState: Boolean) {
                    val oldSubtitle = mAccessibilityServiceItem.subtitle
                    if (aimState) {
                        if (mA11yTool.isMalfunctioning()) {
                            mAccessibilityServiceItem.subtitle = context.getString(R.string.text_malfunctioning)
                        } else {
                            mAccessibilityServiceItem.subtitle = null
                        }
                    } else {
                        mAccessibilityServiceItem.subtitle = null
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
            prefKey = R.string.key_a11y_service,
        ).also { item ->
            item.setOnLaunchManagerListener { d ->
                if (d != null) {
                    ViewUtils.showSnack(d.view, R.string.text_under_development, 1_200)
                } else {
                    ViewUtils.showToast(mContext, R.string.text_under_development)
                }
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
            prefKey = R.string.key_foreground_service,
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
            prefKey = R.string.key_floating_menu_shown,
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

            val disposable = Observable
                .combineLatest(
                    JsonSocketClient.cxnState,
                    devPluginService.clientConnectionIpAddress,
                ) { state: DevPluginService.State, ip: String ->
                    Pair(state, ip)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (state, ip) ->
                    drawerItem.subtitle = when {
                        state.isDisconnected() -> null
                        else -> ip
                    }
                    drawerItem.setCheckedIfNeeded(state.isConnected())
                    drawerItem.isProgress = state.isConnecting()
                    state.exception?.let {
                        drawerItem.subtitle = null
                    }
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
                    if (d != null) {
                        ViewUtils.showSnack(d.view, R.string.text_under_development, 1_200)
                    } else {
                        ViewUtils.showToast(mContext, R.string.text_under_development)
                    }
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
            connectIfNotNormallyClosed()
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
                        Pref.putString(R.string.key_night_mode, MODE.FOLLOW.key)
                    } else {
                        when (isNightModeYes) {
                            true -> MODE.NIGHT.key
                            else -> MODE.DAY.key
                        }.let { Pref.putString(R.string.key_night_mode, it) }
                    }
                }.isSuccess

                override val isActive
                    get() = ViewUtils.isAutoNightModeEnabled

                override val isInMainThread = true
            },
            icon = R.drawable.ic_automatic_brightness_thicker,
            title = R.string.text_auto_night_mode,
            descriptionRes = R.string.description_auto_night_mode,
            prefKey = R.string.key_auto_night_mode_enabled,
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
                            Pref.putString(R.string.key_night_mode, MODE.NIGHT.key)
                        }
                        else -> {
                            ViewUtils.setDefaultNightMode(MODE.DAY)
                            Pref.putString(R.string.key_night_mode, MODE.DAY.key)
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
            prefKey = R.string.key_night_mode_enabled,
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
                        val i = resources.getStringArray(R.array.keys_keep_screen_on_when_in_foreground).indexOf(Pref.keyKeepScreenOnWhenInForeground)
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

        mThemeColorItem = DrawerMenuShortcutItem(
            icon = R.drawable.ic_personalize_thicker,
            title = R.string.text_theme_color,
        ).apply {
            setAction { ColorSelectBaseActivity.startActivity(mContext) }
            subtitle = ColorSelectBaseActivity.getCurrentColorSummary(mContext)
        }

        mTrashItem = DrawerMenuShortcutItem(
            icon = R.drawable.ic_recycle_bin,
            title = R.string.text_trash,
        ).apply {
            setAction { TrashActivity.startActivity(mContext) }
        }

        mVersionHistoryItem = DrawerMenuShortcutItem(
            icon = R.drawable.ic_version_history,
            title = R.string.text_version_history,
        ).apply {
            setAction { VersionHistoryActivity.startActivity(mContext) }
        }

        mAboutAppAndDevItem = DrawerMenuShortcutItem(
            icon = R.drawable.ic_about,
            title = R.string.text_about_app_and_developer,
        ).apply {
            setAction { AboutActivity.startActivity(mContext) }
            subtitle = BuildConfig.VERSION_NAME
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentDrawerBinding.inflate(inflater, container, false).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mDrawerMenu = binding.drawerMenu
        configureDrawerWidth()
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
        val trashLimitFlow = PrefRx.observeLong(
            keyRes = R.string.key_trash_max_total_bytes,
            defaultValue = HistoryPrefs.DEFAULT_TRASH_MAX_TOTAL_BYTES,
        )

        val trashDisposable = Observable
            .combineLatest(
                dao.observeTrashStats().toObservable(),
                trashLimitFlow.toObservable(),
            ) { stats, limitBytes ->
                val count = stats.count.coerceAtLeast(0L)
                val totalBytes = (stats.totalBytes ?: 0L).coerceAtLeast(0L)
                val limit = limitBytes.coerceAtLeast(0L)

                val sizeText = PFiles.formatSizeWithUnit(totalBytes)

                val percentText = if (limit > 0L) {
                    val ratio = (totalBytes.toDouble() / limit.toDouble()).coerceAtLeast(0.0)
                    String.format(Locale.getDefault(), "%.1f%%", ratio * 100.0)
                } else {
                    String.format(Locale.getDefault(), "%.1f%%", 0.0)
                }

                "$count | $sizeText | $percentText"
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { subtitle ->
                mTrashItem.subtitle = subtitle
                (mDrawerMenu.adapter as? DrawerMenuAdapter)?.notifyItemChanged(mTrashItem)
            }

        drawerStatsDisposables.add(trashDisposable)

        // Version history: observe DB stats + observe limit preference.
        // zh-CN: 版本历史: 监听 DB 统计 + 监听容量上限偏好.
        val historyLimitFlow = PrefRx.observeLong(
            keyRes = R.string.key_history_max_total_bytes,
            defaultValue = HistoryPrefs.DEFAULT_HISTORY_MAX_TOTAL_BYTES,
        )

        val historyDisposable = Observable
            .combineLatest(
                dao.observeVersionHistoryStats().toObservable(),
                historyLimitFlow.toObservable(),
            ) { stats, limitBytes ->
                val count = stats.fileCount.coerceAtLeast(0L)
                val totalBytes = (stats.totalBytes ?: 0L).coerceAtLeast(0L)
                val limit = limitBytes.coerceAtLeast(0L)

                val sizeText = PFiles.formatSizeWithUnit(totalBytes)

                val percentText = if (limit > 0L) {
                    val ratio = (totalBytes.toDouble() / limit.toDouble()).coerceAtLeast(0.0)
                    String.format(Locale.getDefault(), "%.1f%%", ratio * 100.0)
                } else {
                    String.format(Locale.getDefault(), "%.1f%%", 0.0)
                }

                "$count | $sizeText | $percentText"
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { subtitle ->
                mVersionHistoryItem.subtitle = subtitle
                (mDrawerMenu.adapter as? DrawerMenuAdapter)?.notifyItemChanged(mVersionHistoryItem)
            }

        drawerStatsDisposables.add(historyDisposable)
    }

    private fun configureDrawerWidth() {
        val screenWidthDp = resources.configuration.screenWidthDp
        val targetWidthPx = 288f.coerceIn(
            screenWidthDp * 0.4f,
            screenWidthDp * 0.9f,
        ).let { DisplayUtils.dpToPx(it) }.roundToInt()
        binding.drawerMenuContainer.layoutParams.width = targetWidthPx
    }

    private fun setupListeners() {
        binding.settings.setOnClickListener { PreferencesActivity.launch(it.context) }
        binding.pluginCenter.setOnClickListener { PluginCenterActivity.startActivity(it.context) }
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
        syncMenuItemStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        mClientModeItem.dispose()
        mServerModeItem.dispose()

        // mActivity.unregisterReceiver(mReceiver)

        // Dispose drawer subtitle observers.
        // zh-CN: 释放抽屉子标题监听订阅.
        drawerStatsDisposables.clear()

        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        privateDrawerMenu = null
        privateContext = null
        privateActivity = null
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
        syncMenuItemStates()
    }

    @Subscribe
    @Suppress("unused", "UNUSED_PARAMETER")
    fun onDrawerClosed(event: Event.OnDrawerClosed) {
        // ViewCompat.getWindowInsetsController(mActivity.window.decorView)?.show(WindowInsets.Type.systemBars())
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
        (mDrawerMenu.adapter as? DrawerMenuAdapter)?.notifyItemChanged(mThemeColorItem)
    }

    private fun initMenuItems() {
        val items = listOf(
            DrawerMenuGroup(R.string.text_service),
            mAccessibilityServiceItem,
            mForegroundServiceItem,
            DrawerMenuGroup(R.string.text_tools),
            mFloatingButtonItem,
            mPointerLocationItem,
            DrawerMenuGroup(R.string.text_connect_to_pc),
            mClientModeItem,
            mServerModeItem,
            DrawerMenuGroup(R.string.text_permissions),
            mNotificationPostItem,
            mNotificationAccessItem,
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
        mClientModeItem,
        mServerModeItem,
        mNotificationPostItem,
        mNotificationAccessItem,
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

    companion object {

        class Event {
            interface OnDrawerOpened
            interface OnDrawerClosed
            interface AccessibilityServiceStateChangedEvent
            interface ThemeColorLayoutSwitchedEvent
        }

    }

}