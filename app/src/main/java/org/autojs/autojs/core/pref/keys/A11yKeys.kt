package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.A11Y
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Settings > Accessibility service (docs/settings/ROADMAP.md P2.4, appendix A.1 `a11y`): the three ways to enable the
 * service automatically, stable mode and gesture observing. The switch mirroring the service state and the key
 * observing key are states (P4.2, appendix C Q12).
 *
 * zh-CN: 设置 > 无障碍服务 (ROADMAP P2.4, 附录 A.1 `a11y`): 三种自动启用服务的方式, 稳定模式, 手势观察. 服务状态的镜像开关与
 * "按键观察" 键为状态 (P4.2, 附录 C Q12).
 */
object A11yKeys : SettingsDomain {

    @JvmField
    val ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS = SettingKey.Bool(
        R.string.key_enable_a11y_service_with_root_access, A11Y,
        boolResource(R.bool.pref_enable_a11y_service_with_root_access, "pref_enable_a11y_service_with_root_access"),
    )

    @JvmField
    val ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS = SettingKey.Bool(
        R.string.key_enable_a11y_service_with_secure_settings, A11Y,
        boolResource(R.bool.pref_enable_a11y_service_with_secure_settings, "pref_enable_a11y_service_with_secure_settings"),
    )

    @JvmField
    val ENABLE_A11Y_SERVICE_WITH_SHIZUKU = SettingKey.Bool(
        R.string.key_enable_a11y_service_with_shizuku, A11Y,
        boolResource(R.bool.pref_enable_a11y_service_with_shizuku, "pref_enable_a11y_service_with_shizuku"),
    )

    @JvmField
    val STABLE_MODE = SettingKey.Bool(R.string.key_stable_mode, A11Y, boolResource(R.bool.pref_stable_mode, "pref_stable_mode"))

    @JvmField
    val GESTURE_OBSERVING = SettingKey.Bool(R.string.key_gesture_observing, A11Y, boolResource(R.bool.pref_gesture_observing, "pref_gesture_observing"))

    override val keys: List<PrefKey<*>> = listOf(
        ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS, ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS, ENABLE_A11Y_SERVICE_WITH_SHIZUKU,
        STABLE_MODE, GESTURE_OBSERVING,
    )
}
