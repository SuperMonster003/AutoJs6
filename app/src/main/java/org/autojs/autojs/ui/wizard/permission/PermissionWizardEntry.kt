package org.autojs.autojs.ui.wizard.permission

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.autojs.autojs6.R

/**
 * The permissions of the home drawer, in drawer order, with the built-in profile markers of
 * the permission wizard: "Standard" holds all files access, post notifications, ignoring
 * battery optimizations and displaying over other apps; "Minimal" holds all files access.
 *
 * zh-CN: 主页抽屉中的各项权限 (按抽屉顺序) 及其在权限申请向导内置配置中的标记:
 * "标准" 包含所有文件访问, 发布通知, 忽略电池优化及显示在其他应用上层; "最小" 包含所有文件访问.
 */
enum class PermissionWizardEntry(
    val id: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    @StringRes val descriptionRes: Int,
    val inStandard: Boolean = false,
    val inMinimal: Boolean = false,
) {
    POST_NOTIFICATIONS(
        "post_notifications", R.string.text_post_notifications_permission,
        R.drawable.ic_ali_notification, R.string.description_post_notifications, inStandard = true,
    ),
    NOTIFICATION_ACCESS(
        "notification_access", R.string.text_notification_access_permission,
        R.drawable.ic_ali_notification, R.string.description_notification_access,
    ),
    LOCAL_NETWORK(
        "local_network", R.string.text_local_network_permission,
        R.drawable.ic_lan_black_48dp, R.string.description_local_network_permission,
    ),
    ALL_FILES_ACCESS(
        "all_files_access", R.string.text_all_files_access,
        R.drawable.ic_database_black_48dp, R.string.description_all_files_access, inStandard = true, inMinimal = true,
    ),
    USAGE_STATS(
        "usage_stats", R.string.text_usage_stats_permission,
        R.drawable.ic_assessment_black_48dp, R.string.description_usage_stats_access,
    ),
    IGNORE_BATTERY_OPTIMIZATIONS(
        "ignore_battery_optimizations", R.string.text_ignore_battery_optimizations,
        R.drawable.ic_battery_std_black_48dp, R.string.description_ignore_battery_optimizations, inStandard = true,
    ),
    DISPLAY_OVER_OTHER_APPS(
        "display_over_other_apps", R.string.text_display_over_other_app,
        R.drawable.ic_layers_black_48dp, R.string.description_display_over_other_app, inStandard = true,
    ),
    XIAOMI_BACKGROUND_POPUP(
        "xiaomi_background_popup", R.string.text_xiaomi_background_popup_permission,
        R.drawable.ic_layers_black_48dp, R.string.description_background_popup_permission,
    ),
    VIVO_BACKGROUND_POPUP(
        "vivo_background_popup", R.string.text_vivo_background_popup_permission,
        R.drawable.ic_layers_black_48dp, R.string.description_background_popup_permission,
    ),
    WRITE_SYSTEM_SETTINGS(
        "write_system_settings", R.string.text_write_system_settings,
        R.drawable.ic_settings_black_48dp, R.string.description_write_system_settings,
    ),
    WRITE_SECURE_SETTINGS(
        "write_secure_settings", R.string.text_write_secure_settings,
        R.drawable.ic_security_black_48dp, R.string.description_write_secure_settings,
    ),
    PROJECT_MEDIA(
        "project_media", R.string.text_project_media_access,
        R.drawable.ic_cast_connected_black_48dp, R.string.description_project_media_access,
    ),
    SHIZUKU(
        "shizuku", R.string.text_shizuku_access,
        R.drawable.ic_app_shizuku_representative, R.string.description_shizuku_access,
    ),
    ;

    companion object {

        fun fromId(id: String): PermissionWizardEntry? = entries.firstOrNull { it.id == id }

    }

}
