package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.CacheKey
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.RUNNING
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingResources
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs6.R

/**
 * Settings > Running scripts (docs/settings/ROADMAP.md P2.4, appendix A.1 `running`): stopping scripts with the volume
 * key, guard mode, root mode, the delay before a screen capture request and the custom root manager apps (picked in
 * the root manager apps dialog). The list of root manager apps found on the device is a cache (appendix A.2).
 *
 * zh-CN: 设置 > 脚本运行 (ROADMAP P2.4, 附录 A.1 `running`): 音量键停止脚本, 守护模式, Root 模式, 截图请求前的延迟, 自定义 Root
 * 管理器应用 (在 Root 管理器应用对话框中选择). 本机找到的 Root 管理器应用列表是缓存 (附录 A.2).
 */
object RunningKeys : SettingsDomain {

    private val ROOT_MODE_VALUES = intArrayOf(
        R.string.key_root_mode_auto_detect,
        R.string.key_root_mode_force_root,
        R.string.key_root_mode_force_non_root,
    )

    @JvmField
    val USE_VOLUME_CONTROL_RUNNING = SettingKey.Bool(
        R.string.key_use_volume_control_running, RUNNING,
        boolResource(R.bool.pref_use_volume_control_running, "pref_use_volume_control_running"),
    )

    @JvmField
    val GUARD_MODE = SettingKey.Bool(R.string.key_guard_mode, RUNNING, boolResource(R.bool.pref_guard_mode, "pref_guard_mode"))

    /** Read through `RootUtils.RootMode.getRootMode` (`Pref.rootMode`). */
    @JvmField
    val ROOT_MODE = SettingKey.Enum(
        R.string.key_root_mode, RUNNING, keyChoices(*ROOT_MODE_VALUES),
        keyResource(R.string.default_key_root_mode, "default_key_root_mode"),
    )

    /** Milliseconds, kept within the minimum and maximum resources when read ([screenCaptureRequestDelay]). */
    @JvmField
    val SCREEN_CAPTURE_REQUEST_DELAY = SettingKey.Int(
        R.string.key_screen_capture_request_delay, RUNNING,
        intResource(R.integer.screen_capture_request_delay_default_value, "screen_capture_request_delay_default_value"),
    )

    /** Package names added in the root manager apps dialog. */
    @JvmField
    val ROOT_MANAGER_APPS_CUSTOM = SettingKey.StrSet(R.string.key_root_manager_apps_custom, RUNNING, SettingDefault.of(emptySet()))

    /** JSON of the root manager apps found on the device; rebuilt by the dialog, never exported (appendix A.2). */
    @JvmField
    val ROOT_MANAGER_APPS_CACHE = CacheKey.str(R.string.key_root_manager_apps_cache, SettingDefault.None)

    override val keys: List<PrefKey<*>> = listOf(
        USE_VOLUME_CONTROL_RUNNING, GUARD_MODE, ROOT_MODE, SCREEN_CAPTURE_REQUEST_DELAY, ROOT_MANAGER_APPS_CUSTOM, ROOT_MANAGER_APPS_CACHE,
    )

    override val names: List<RegisteredName> = valueConstants(*ROOT_MODE_VALUES)

    /**
     * The root mode of the settings (`Pref.rootMode`; `RootUtils` may override it for the current run). The stored
     * key goes to `RootMode.getRootMode` as it is, as before (R2).
     * zh-CN: 设置中的 Root 模式 (`Pref.rootMode`; `RootUtils` 可在本次运行中覆盖). 存储的键原样交给 `RootMode.getRootMode` (R2).
     */
    @JvmStatic
    var rootMode: RootUtils.RootMode
        get() = RootUtils.RootMode.getRootMode(ROOT_MODE.storedKeyOrDefault())
        set(mode) = ROOT_MODE.set(mode.key)

    /**
     * The delay before a screen capture request, coerced into the minimum and maximum resources
     * (`Pref.screenCaptureRequestDelay`); 350 ms covers the fade-out of the permission dialog on most ROMs.
     * zh-CN: 截图请求前的延迟, 限制在最小值与最大值资源之间; 默认 350 ms 通常可覆盖多数 ROM 授权弹窗的渐隐动画.
     */
    @JvmStatic
    val screenCaptureRequestDelay: Int
        get() = SCREEN_CAPTURE_REQUEST_DELAY.get().coerceIn(
            SettingResources.int(R.integer.screen_capture_request_delay_min_value),
            SettingResources.int(R.integer.screen_capture_request_delay_max_value),
        )
}
