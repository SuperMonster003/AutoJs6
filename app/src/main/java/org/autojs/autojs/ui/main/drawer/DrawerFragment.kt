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
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.pref.Pref
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
import org.autojs.autojs.service.AccessibilityService
import org.autojs.autojs.service.ForegroundService
import org.autojs.autojs.service.NotificationService
import org.autojs.autojs.theme.app.ColorSelectBaseActivity
import org.autojs.autojs.ui.floating.CircularMenu
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.fragment.BindingDelegates.viewBinding
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.MODE
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FragmentDrawerBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.lang.ref.WeakReference

/**
 * Created by Stardust on Jan 30, 2017.
 * Modified by SuperMonster003 as of Nov 16, 2021.
 * Transformed by SuperMonster003 on Sep 19, 2022.
 */
open class DrawerFragment : Fragment() {

    private val binding by viewBinding(FragmentDrawerBinding::bind)

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

    private lateinit var mA11yTool: AccessibilityTool

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = requireContext()

        mActivity = requireActivity() as MainActivity

        mA11yTool = AccessibilityTool(mContext)

        mAccessibilityServiceItem = DrawerMenuToggleableItem(
            object : AccessibilityService(mContext) {

                override fun refreshSubtitle(aimState: Boolean) {
                    val oldSubtitle = mAccessibilityServiceItem.subtitle
                    if (aimState) {
                        if (mA11yTool.serviceExists() && !mA11yTool.isServiceRunning()) {
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
            R.drawable.ic_accessibility_black_48dp,
            R.string.text_a11y_service,
            DrawerMenuItem.DEFAULT_DIALOG_CONTENT,
            R.string.key_a11y_service,
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
            R.drawable.ic_robot_64,
            R.string.text_floating_button,
            DrawerMenuItem.DEFAULT_DIALOG_CONTENT,
            R.string.key_floating_menu_shown,
        )

        JsonSocketClientTool(mContext).apply {
            mClientModeItem = DrawerMenuDisposableItem(this, R.drawable.ic_computer_black_48dp, R.string.text_client_mode).also {
                setClientModeItem(it)
            }
            setStateDisposable(
                JsonSocketClient.cxnState
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        if (it.state == DevPluginService.State.DISCONNECTED) {
                            mClientModeItem.subtitle = null
                        }
                        consumeJsonSocketItemState(mClientModeItem, it)
                    })
            setOnConnectionException { e: Throwable ->
                mClientModeItem.setCheckedIfNeeded(false)
                ViewUtils.showToast(context, getString(R.string.error_connect_to_remote, e.message), true)
            }
            setOnConnectionDialogDismissed { mClientModeItem.setCheckedIfNeeded(false) }
            connectIfNotNormallyClosed()
        }

        JsonSocketServerTool(mContext).apply {
            setStateDisposable(
                JsonSocketServer.cxnState
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { state: DevPluginService.State ->
                        mServerModeItem.subtitle = takeIf { state.state == DevPluginService.State.CONNECTED }?.let {
                            NetworkUtils.getIpAddress()
                        }
                        consumeJsonSocketItemState(mServerModeItem, state)
                    })
            setOnConnectionException { e: Throwable ->
                mServerModeItem.setCheckedIfNeeded(false)
                ViewUtils.showToast(context, getString(R.string.error_enable_server, e.message), true)
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
            R.drawable.ic_automatic_brightness,
            R.string.text_auto_night_mode,
            DrawerMenuItem.DEFAULT_DIALOG_CONTENT,
            R.string.key_auto_night_mode_enabled,
        ).apply { isHidden = !ViewUtils.AutoNightMode.isFunctional() }

        mNightModeItem = DrawerMenuToggleableItem(
            object : DrawerMenuItemCustomHelper(mContext) {
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
            R.drawable.ic_night_mode,
            R.string.text_night_mode,
            DrawerMenuItem.DEFAULT_DIALOG_CONTENT,
            R.string.key_night_mode_enabled,
        )

        mKeepScreenOnWhenInForegroundItem = DrawerMenuToggleableItem(
            object : DrawerMenuItemCustomHelper(mContext) {
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
            R.drawable.ic_lightbulb_outline_black_48dp,
            R.string.text_keep_screen_on_when_in_foreground,
            DrawerMenuItem.DEFAULT_DIALOG_CONTENT,
        )

        mThemeColorItem = DrawerMenuShortcutItem(R.drawable.ic_personalize, R.string.text_theme_color)
            .setAction(Runnable { ColorSelectBaseActivity.startActivity(mContext) })
            .apply { subtitle = ColorSelectBaseActivity.getCurrentColorSummary(mContext) }

        mAboutAppAndDevItem = DrawerMenuShortcutItem(R.drawable.ic_about, R.string.text_about_app_and_developer)
            .setAction(Runnable { AboutActivity.startActivity(mContext) })
            .apply { subtitle = BuildConfig.VERSION_NAME }
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
    }

    private fun setupListeners() {
        binding.settings.setOnClickListener { view ->
            startActivity(
                Intent(view.context, PreferencesActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        binding.restart.setOnClickListener { mActivity.rebirth() }
        binding.exit.setOnClickListener { mActivity.exitCompletely() }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        syncMenuItemStates()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        mClientModeItem.dispose()
        mServerModeItem.dispose()
        // mActivity.unregisterReceiver(mReceiver)
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
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun initMenuItemStates() = listOf(
        mFloatingWindowItem,
        mForegroundServiceItem,
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
            interface AccessibilityServiceStateChangedEvent
        }

    }

}