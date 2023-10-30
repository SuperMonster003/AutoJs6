package org.autojs.autojs.ui.main.drawer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.app.tool.FloatingButtonTool
import org.autojs.autojs.app.tool.JsonSocketClientTool
import org.autojs.autojs.app.tool.JsonSocketServerTool
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.permission.IgnoreBatteryOptimizationsPermission
import org.autojs.autojs.permission.MediaProjectionPermission
import org.autojs.autojs.permission.PostNotificationPermission
import org.autojs.autojs.permission.ShizukuPermission
import org.autojs.autojs.permission.UsageStatsPermission
import org.autojs.autojs.permission.WriteSecureSettingsPermission
import org.autojs.autojs.permission.WriteSystemSettingsPermission
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.pluginclient.JsonSocketClient
import org.autojs.autojs.pluginclient.JsonSocketServer
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.service.AccessibilityService
import org.autojs.autojs.service.ForegroundService
import org.autojs.autojs.service.NotificationService
import org.autojs.autojs.theme.app.ColorSelectActivity
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.MODE
import org.autojs.autojs.util.ViewUtils.isNightModeYes
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FragmentDrawerBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Created by Stardust on Jan 30, 2017.
 * Modified by SuperMonster003 as of Nov 16, 2021.
 * Transformed by SuperMonster003 on Sep 19, 2022.
 */
open class DrawerFragment : Fragment() {

    private lateinit var binding: FragmentDrawerBinding

    private lateinit var mDrawerMenu: RecyclerView
    private lateinit var mContext: Context
    private lateinit var mActivity: MainActivity

    private lateinit var mAccessibilityServiceItem: DrawerMenuToggleableItem
    private lateinit var mForegroundServiceItem: DrawerMenuToggleableItem
    private lateinit var mFloatingWindowItem: DrawerMenuToggleableItem
    private lateinit var mClientModeItem: DrawerMenuDisposableItem
    private lateinit var mServerModeItem: DrawerMenuDisposableItem
    private lateinit var mNotificationPostItem: DrawerMenuToggleableItem
    private lateinit var mNotificationAccessItem: DrawerMenuToggleableItem
    private lateinit var mUsageStatsPermissionItem: DrawerMenuToggleableItem
    private lateinit var mIgnoreBatteryOptimizationsItem: DrawerMenuToggleableItem
    private lateinit var mDisplayOverOtherAppsItem: DrawerMenuToggleableItem
    private lateinit var mWriteSystemSettingsItem: DrawerMenuToggleableItem
    private lateinit var mWriteSecuritySettingsItem: DrawerMenuToggleableItem
    private lateinit var mProjectMediaAccessItem: DrawerMenuToggleableItem
    private lateinit var mShizukuAccessItem: DrawerMenuToggleableItem
    private lateinit var mNightModeItem: DrawerMenuToggleableItem
    private lateinit var mAutoNightModeItem: DrawerMenuToggleableItem
    private lateinit var mKeepScreenOnWhenInForegroundItem: DrawerMenuToggleableItem
    private lateinit var mThemeColorItem: DrawerMenuShortcutItem
    private lateinit var mAboutAppAndDevItem: DrawerMenuShortcutItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EventBus.getDefault().register(this)

        mContext = requireContext()

        mActivity = (requireActivity() as MainActivity)

        mAccessibilityServiceItem = DrawerMenuToggleableItem(
            AccessibilityService(mContext),
            R.drawable.ic_accessibility_black_48dp,
            R.string.text_a11y_service,
        )

        mForegroundServiceItem = DrawerMenuToggleableItem(
            ForegroundService(mContext),
            R.drawable.ic_service_green,
            R.string.text_foreground_service,
            R.string.text_foreground_service_description,
            R.string.key_foreground_service,
        )

        mFloatingWindowItem = DrawerMenuToggleableItem(
            object : FloatingButtonTool(mContext) {
                override fun toggle(aimState: Boolean) {
                    // @BeforeSuper
                    if (!aimState /* is closing */) {
                        FloatyWindowManger.getCircularMenu()?.let { circularMenu ->
                            if (circularMenu.isRecording) {
                                circularMenu.stopRecord()
                            }
                        }
                    }

                    super.toggle(aimState)

                    // @AfterSuper
                    if (isActive) {
                        if (!mAccessibilityServiceItem.isChecked) {
                            mAccessibilityServiceItem.syncDelay()
                        }
                    }
                }
            },
            R.drawable.ic_robot_64,
            R.string.text_floating_button,
            DrawerMenuItem.DEFAULT_DIALOG_CONTENT,
            R.string.key_floating_menu_shown,
        )

        JsonSocketClientTool(mContext).apply {
            mClientModeItem = DrawerMenuDisposableItem(this, R.drawable.ic_computer_black_48dp, R.string.text_client_mode).also {
                setClientModeItem(it)
            }
            setStateDisposable(JsonSocketClient.cxnState
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (it.state == DevPluginService.State.DISCONNECTED) {
                        mClientModeItem.subtitle = null
                    }
                    consumeJsonSocketItemState(mClientModeItem, it)
                })
            setOnConnectionException { e: Throwable ->
                mClientModeItem.setCheckedIfNeeded(false)
                ViewUtils.showToast(mContext, getString(R.string.error_connect_to_remote, e.message), true)
            }
            setOnConnectionDialogDismissed { mClientModeItem.setCheckedIfNeeded(false) }
            connectIfNotNormallyClosed()
        }

        JsonSocketServerTool(mContext).apply {
            setStateDisposable(JsonSocketServer.cxnState
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { state: DevPluginService.State ->
                    mServerModeItem.subtitle = takeIf { state.state == DevPluginService.State.CONNECTED }?.let {
                        NetworkUtils.getIpAddress()
                    }
                    consumeJsonSocketItemState(mServerModeItem, state)
                })
            setOnConnectionException { e: Throwable ->
                mServerModeItem.setCheckedIfNeeded(false)
                ViewUtils.showToast(mContext, getString(R.string.error_enable_server, e.message), true)
            }
            mServerModeItem = DrawerMenuDisposableItem(this, R.drawable.ic_smartphone_black_48dp, R.string.text_server_mode)
            connectIfNotNormallyClosed()
        }

        mNotificationPostItem = DrawerMenuToggleableItem(
            PostNotificationPermission(mContext),
            R.drawable.ic_ali_notification,
            R.string.text_post_notifications_permission,
        )

        mNotificationAccessItem = DrawerMenuToggleableItem(
            NotificationService(mContext),
            R.drawable.ic_ali_notification,
            R.string.text_notification_access_permission,
        )

        mUsageStatsPermissionItem = DrawerMenuToggleableItem(
            UsageStatsPermission(mContext),
            R.drawable.ic_assessment_black_48dp,
            R.string.text_usage_stats_permission,
            R.string.text_usage_stats_permission_description,
        )

        mIgnoreBatteryOptimizationsItem = DrawerMenuToggleableItem(
            IgnoreBatteryOptimizationsPermission(mContext),
            R.drawable.ic_battery_std_black_48dp,
            R.string.text_ignore_battery_optimizations,
        )

        mDisplayOverOtherAppsItem = DrawerMenuToggleableItem(
            DisplayOverOtherAppsPermission(mContext),
            R.drawable.ic_layers_black_48dp,
            R.string.text_display_over_other_app,
        )

        mWriteSystemSettingsItem = DrawerMenuToggleableItem(
            WriteSystemSettingsPermission(mContext),
            R.drawable.ic_settings_black_48dp,
            R.string.text_write_system_settings,
        )

        mWriteSecuritySettingsItem = DrawerMenuToggleableItem(
            WriteSecureSettingsPermission(mContext),
            R.drawable.ic_security_black_48dp,
            R.string.text_write_secure_settings,
            R.string.text_write_secure_settings_description,
        )

        mProjectMediaAccessItem = DrawerMenuToggleableItem(
            MediaProjectionPermission(mContext),
            R.drawable.ic_cast_connected_black_48dp,
            R.string.text_project_media_access,
            R.string.text_project_media_access_description,
        )

        mShizukuAccessItem = DrawerMenuToggleableItem(
            ShizukuPermission(mContext),
            R.drawable.ic_app_shizuku_representative,
            R.string.text_shizuku_access,
            R.string.text_shizuku_access_description,
        )

        mAutoNightModeItem = DrawerMenuToggleableItem(
            object : DrawerMenuItemCustomHelper(mContext) {
                override fun toggle() {
                    val isTurningOn = !isActive
                    val isNightModeYes = isNightModeYes(resources.configuration)
                    val mode = when {
                        isTurningOn -> MODE.FOLLOW
                        isNightModeYes -> MODE.NIGHT
                        else -> MODE.DAY
                    }
                    ViewUtils.setDefaultNightMode(mode)
                    if (isTurningOn) {
                        ViewUtils.isNightModeEnabled = isNightModeYes
                        Pref.putString(R.string.key_night_mode, MODE.FOLLOW.key)
                    } else {
                        when (isNightModeYes) {
                            true -> MODE.NIGHT.key
                            else -> MODE.DAY.key
                        }.let { Pref.putString(R.string.key_night_mode, it) }
                    }
                }

                override val isActive
                    get() = ViewUtils.isAutoNightModeEnabled

                override val isInMainThread = true
            },
            R.drawable.ic_automatic_brightness,
            R.string.text_auto_night_mode,
            DrawerMenuItem.DEFAULT_DIALOG_CONTENT,
            R.string.key_auto_night_mode_enabled,
        ).apply { isHidden = !ViewUtils.AutoNightMode.isFunctional() }

        mNightModeItem = DrawerMenuToggleableItem(
            object : DrawerMenuItemCustomHelper(mContext) {
                override fun toggle() {
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
                }

                override val isActive
                    get() = ViewUtils.isNightModeEnabled

                override val isInMainThread = true
            },
            R.drawable.ic_night_mode,
            R.string.text_night_mode,
            DrawerMenuItem.DEFAULT_DIALOG_CONTENT,
            R.string.key_night_mode_enabled,
        )

        mKeepScreenOnWhenInForegroundItem = DrawerMenuToggleableItem(
            object : DrawerMenuItemCustomHelper(mContext) {
                override val isActive: Boolean
                    get() = ViewUtils.isKeepScreenOnWhenInForegroundEnabled

                override fun toggle() {
                    when (/* isTurningOn */ !isActive) {
                        true -> ViewUtils.setKeepScreenOnWhenInForegroundFromLastEnabledState()
                        else -> ViewUtils.setKeepScreenOnWhenInForegroundDisabled()
                    }
                    refreshSubtitle()
                }

                override fun refreshSubtitle() {
                    val aimSubtitle = if (ViewUtils.isKeepScreenOnWhenInForegroundDisabled) null else {
                        val i = resources.getStringArray(R.array.keys_keep_screen_on_when_in_foreground).indexOf(Pref.keyKeepScreenOnWhenInForeground!!)
                        resources.getStringArray(R.array.values_keep_screen_on_when_in_foreground)[i]
                    }
                    if (mKeepScreenOnWhenInForegroundItem.subtitle != aimSubtitle) {
                        mKeepScreenOnWhenInForegroundItem.subtitle = aimSubtitle
                        mKeepScreenOnWhenInForegroundItem.isChecked = mKeepScreenOnWhenInForegroundItem.isChecked
                    }
                }
            },
            R.drawable.ic_lightbulb_outline_black_48dp,
            R.string.text_keep_screen_on_when_in_foreground,
            DrawerMenuItem.DEFAULT_DIALOG_CONTENT,
        )

        mThemeColorItem = DrawerMenuShortcutItem(R.drawable.ic_personalize, R.string.text_theme_color)
            .setAction(Runnable { ColorSelectActivity.startActivity(mContext) })

        mAboutAppAndDevItem = DrawerMenuShortcutItem(R.drawable.ic_about, R.string.text_about_app_and_developer)
            .setAction(Runnable { AboutActivity.startActivity(mContext) })
            .apply { subtitle = BuildConfig.VERSION_NAME }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentDrawerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mDrawerMenu = binding.drawerMenu
        initMenuItems()
        initMenuItemStates()
        syncMenuItemStates()
        setupListeners()
    }

    private fun setupListeners() {
        binding.settings.setOnClickListener { view ->
            startActivity(
                Intent(view.context, PreferencesActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        binding.restart.setOnClickListener { view -> mActivity.rebirth(view) }
        binding.exit.setOnClickListener { mActivity.exitCompletely() }
    }

    override fun onResume() {
        super.onResume()
        syncMenuItemStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        mClientModeItem.dispose()
        mServerModeItem.dispose()
        EventBus.getDefault().unregister(this)
        // mActivity.unregisterReceiver(mReceiver)
    }

    // @Subscribe
    // fun onCircularMenuStateChange(event: CircularMenu.StateChangeEvent) {
    //     mFloatingWindowItem.toggle(event.currentState != CircularMenu.STATE_CLOSED)
    // }

    @Subscribe
    @Suppress("UNUSED_PARAMETER")
    fun onDrawerOpened(event: Event.OnDrawerOpened) {
        // ViewCompat.getWindowInsetsController(mActivity.window.decorView)?.hide(WindowInsets.Type.systemBars())
        syncMenuItemStates()
    }

    @Subscribe
    @Suppress("UNUSED_PARAMETER")
    fun onDrawerClosed(event: Event.OnDrawerClosed) {
        // ViewCompat.getWindowInsetsController(mActivity.window.decorView)?.show(WindowInsets.Type.systemBars())
    }

    private fun initMenuItems() {
        drawerMenuAdapter = listOf(
            DrawerMenuGroup(R.string.text_service),
            mAccessibilityServiceItem,
            mForegroundServiceItem,
            DrawerMenuGroup(R.string.text_tools),
            mFloatingWindowItem,
            DrawerMenuGroup(R.string.text_connect_to_pc),
            mClientModeItem,
            mServerModeItem,
            DrawerMenuGroup(R.string.text_permissions),
            mNotificationPostItem,
            mNotificationAccessItem,
            mUsageStatsPermissionItem,
            mIgnoreBatteryOptimizationsItem,
            mDisplayOverOtherAppsItem,
            mWriteSystemSettingsItem,
            mWriteSecuritySettingsItem,
            mProjectMediaAccessItem,
            mShizukuAccessItem,
            DrawerMenuGroup(R.string.text_appearance),
            mAutoNightModeItem,
            mNightModeItem,
            mKeepScreenOnWhenInForegroundItem,
            mThemeColorItem,
            DrawerMenuGroup(R.string.text_about),
            mAboutAppAndDevItem,
        ).let { items -> DrawerMenuAdapter(items.filterNot { it.isHidden }) }

        mDrawerMenu.apply {
            adapter = drawerMenuAdapter
            layoutManager = LinearLayoutManager(mContext)
        }
    }

    private fun initMenuItemStates() = listOf(
        mAccessibilityServiceItem,
        mForegroundServiceItem,
        mFloatingWindowItem,
    ).forEach { it.selfActive() }

    private fun syncMenuItemStates() = listOf(
        mAccessibilityServiceItem,
        mForegroundServiceItem,
        mFloatingWindowItem,
        mClientModeItem,
        mServerModeItem,
        mNotificationPostItem,
        mNotificationAccessItem,
        mUsageStatsPermissionItem,
        mIgnoreBatteryOptimizationsItem,
        mDisplayOverOtherAppsItem,
        mWriteSystemSettingsItem,
        mWriteSecuritySettingsItem,
        mProjectMediaAccessItem,
        mShizukuAccessItem,
        mKeepScreenOnWhenInForegroundItem,
    ).forEach { it.sync() }

    private fun consumeJsonSocketItemState(item: DrawerMenuToggleableItem, state: DevPluginService.State) {
        item.setCheckedIfNeeded(state.state == DevPluginService.State.CONNECTED)
        item.isProgress = state.state == DevPluginService.State.CONNECTING
        state.exception?.let { e ->
            item.subtitle = null
            ViewUtils.showToast(mContext, e.message)
        }
    }

    companion object {

        lateinit var drawerMenuAdapter: DrawerMenuAdapter
            private set

        class Event {
            interface OnDrawerOpened
            interface OnDrawerClosed
        }

    }

}