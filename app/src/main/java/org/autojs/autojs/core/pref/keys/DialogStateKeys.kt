package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.core.pref.StateKey
import org.autojs.autojs6.R

/**
 * "Do not show again" states of `NotAskAgainDialog` (docs/settings/ROADMAP.md P4.1, T2): true while the dialog is still
 * shown (the default), false once the user ticked "do not show again". The names are those the callers used (R1): the
 * `key_dialog_*` resources and `key_version_history_restore_does_not_auto_save_to_disk` (appendix C Q13 (a)), the
 * literal names of the plugin center prompts, the editor and the about page, and two families whose run-time part is
 * the key of a list preference (`MaterialListPreference`) or the localized title of a drawer switch
 * (`DrawerMenuToggleableItem`, appendix C Q22). [TIMED_TASK_EXACT_ALARM_PROMPT] is named by
 * `key_timed_task_backend_alarm`, which is also a candidate value of [TimedTaskKeys.TIMED_TASK_BACKEND]: the same
 * string serves as the value of one key and as the name of another. The three dialogs of appendix C Q12 are no longer
 * shown anywhere and are registered only.
 *
 * Dialogs that name no key get one made from the stack trace of the caller; such names change with the code, so they
 * cannot be declared and fall into the `other` category at run time (P4.3, appendix C Q23); [forName] gives the key of
 * such a name.
 *
 * zh-CN: `NotAskAgainDialog` 的 "不再提示" 状态 (ROADMAP P4.1, T2): 对话框仍会显示时为 true (默认值), 用户勾选 "不再提示" 后为
 * false. 名称沿用调用方 (R1): `key_dialog_*` 资源与 `key_version_history_restore_does_not_auto_save_to_disk` (附录 C Q13 (a)),
 * 插件中心, 编辑器与关于页的字面量名称, 以及运行时部分为列表偏好的键 (`MaterialListPreference`) 或抽屉开关本地化标题
 * (`DrawerMenuToggleableItem`, 附录 C Q22) 的两个键族. [TIMED_TASK_EXACT_ALARM_PROMPT] 的名称为
 * `key_timed_task_backend_alarm`, 它同时是 [TimedTaskKeys.TIMED_TASK_BACKEND] 的候选值: 同一字符串既是一个键的值, 又是另一个
 * 键的名称. 附录 C Q12 的 3 个对话框已不再显示, 只登记. 未指定键的对话框以调用栈生成键名, 名称随代码变化, 无法声明, 运行时
 * 归入 `other` 分类 (P4.3, 附录 C Q23); [forName] 给出这类名称的键.
 */
object DialogStateKeys : SettingsDomain {

    private fun prompt(keyRes: Int) = StateKey.bool(keyRes, default = SettingDefault.of(true))

    /** Sending the log to the PC: the entries exceed the size that can be sent (`ConsoleImpl`). */
    @JvmField
    val DIALOG_NUM_OF_LOG_ENTRIES_EXCEEDS_LIMIT_FOR_SENDING = prompt(R.string.key_dialog_num_of_log_entries_exceeds_limit_for_sending)

    /** Building an APK: the selected ABI is not part of this AutoJs6 (`BuildActivity`). */
    @JvmField
    val DIALOG_SELECTED_ABI_IS_UNAVAILABLE = prompt(R.string.key_dialog_selected_abi_is_unavailable)

    /** Launcher icon: a transparent background may not take effect, and shortcuts may be lost (`LauncherIconPreference`). */
    @JvmField
    val DIALOG_TRANSPARENT_BACKGROUND_LAUNCHER_ICON = prompt(R.string.key_dialog_transparent_background_launcher_icon)

    /** Restoring a version into the editor does not save it to the disk (`VersionHistoryController`, appendix C Q13 (a)). */
    @JvmField
    val VERSION_HISTORY_RESTORE_DOES_NOT_AUTO_SAVE_TO_DISK = prompt(R.string.key_version_history_restore_does_not_auto_save_to_disk)

    /** Exact alarms are not allowed (`ExactAlarmPermissionHelper`); named by the candidate value `key_timed_task_backend_alarm`. */
    @JvmField
    val TIMED_TASK_EXACT_ALARM_PROMPT = prompt(R.string.key_timed_task_backend_alarm)

    /** Appendix C Q12: no longer shown (registered only). */
    @JvmField
    val DIALOG_FOREGROUND_SVC = prompt(R.string.key_dialog_foreground_svc)

    /** Appendix C Q12: no longer shown (registered only). */
    @JvmField
    val DIALOG_LOG_ENTRIES_EXPORTED = prompt(R.string.key_dialog_log_entries_exported)

    /** Appendix C Q12: only in code that is commented out (`LayoutFloatyWindow`), registered only. */
    @JvmField
    val DIALOG_LAYOUT_INSPECTOR_EXIT_TIPS = prompt(R.string.key_dialog_layout_inspector_exit_tips)

    /** Downloading a plugin whose ABIs do not match the device (`PluginAbiCompatibilityPrompt`). */
    @JvmField
    val PLUGIN_ABI_MISMATCH_DOWNLOAD_PROMPT = StateKey.bool(name = "key_\$_plugin_abi_mismatch_download_prompt", default = SettingDefault.of(true))

    /** Enabling a plugin whose ABIs do not match the device (`PluginAbiCompatibilityPrompt`). */
    @JvmField
    val PLUGIN_ABI_MISMATCH_ENABLE_PROMPT = StateKey.bool(name = "key_\$_plugin_abi_mismatch_enable_prompt", default = SettingDefault.of(true))

    /** Hiding a plugin in the plugin center (`PluginSettingsFragment`). */
    @JvmField
    val PLUGIN_HIDE_CONFIRM_PROMPT = StateKey.bool(name = "key_\$_plugin_hide_confirm_prompt", default = SettingDefault.of(true))

    /** The editor's "launch debugger" menu item: a long click on "run" also starts the debugger (`EditorMenu`). */
    @JvmField
    val EDITOR_DEBUG_LONG_CLICK_HINT = StateKey.bool(name = "editor.debug.long_click_hint", default = SettingDefault.of(true))

    /** About page > feedback: the GitHub issue page is about to open (the simple class name of `AboutActivity` with a suffix). */
    @JvmField
    val ABOUT_START_FEEDBACK_ACTIVITY = StateKey.bool(name = "AboutActivity.start_feedback_activity", default = SettingDefault.of(true))

    /** The confirmation of a list preference, followed by the key of that preference (`MaterialListPreference.showPrompt`). */
    @JvmField
    val LIST_PREFERENCE_PROMPT = KeyFamily("prompt_\$_") { name ->
        StateKey.bool(name = name, default = SettingDefault.of(true))
    }

    /** The prompt of a drawer switch, followed by the title of that switch in the current language (`DrawerMenuToggleableItem.getPrompt`). */
    @JvmField
    val DRAWER_TOGGLE_PROMPT = KeyFamily("DrawerMenuToggleableItem\$") { name ->
        StateKey.bool(name = name, default = SettingDefault.of(true))
    }

    /**
     * The key of a name no declaration names: the names made from a stack trace, and the deprecated string constructor
     * of `NotAskAgainDialog`. Reads and writes are the same as with a declared key of that name.
     * zh-CN: 未被声明的名称对应的键: 以调用栈生成的名称, 以及 `NotAskAgainDialog` 已弃用的字符串构造. 读写与同名的声明键相同.
     */
    @JvmStatic
    fun forName(name: String): StateKey<Boolean> = StateKey.bool(name = name, default = SettingDefault.of(true))

    override val keys: List<PrefKey<*>> = listOf(
        DIALOG_NUM_OF_LOG_ENTRIES_EXCEEDS_LIMIT_FOR_SENDING,
        DIALOG_SELECTED_ABI_IS_UNAVAILABLE,
        DIALOG_TRANSPARENT_BACKGROUND_LAUNCHER_ICON,
        VERSION_HISTORY_RESTORE_DOES_NOT_AUTO_SAVE_TO_DISK,
        TIMED_TASK_EXACT_ALARM_PROMPT,
        DIALOG_FOREGROUND_SVC,
        DIALOG_LOG_ENTRIES_EXPORTED,
        DIALOG_LAYOUT_INSPECTOR_EXIT_TIPS,
        PLUGIN_ABI_MISMATCH_DOWNLOAD_PROMPT,
        PLUGIN_ABI_MISMATCH_ENABLE_PROMPT,
        PLUGIN_HIDE_CONFIRM_PROMPT,
        EDITOR_DEBUG_LONG_CLICK_HINT,
        ABOUT_START_FEEDBACK_ACTIVITY,
    )

    override val families: List<KeyFamily<*, *>> = listOf(LIST_PREFERENCE_PROMPT, DRAWER_TOGGLE_PROMPT)
}
