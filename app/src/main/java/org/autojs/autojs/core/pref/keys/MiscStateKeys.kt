package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.CacheKey
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.core.pref.StateKey

/**
 * Remembered states and caches of the default preferences that the former direct readers of
 * `PreferenceManager.getDefaultSharedPreferences` kept (docs/settings/ROADMAP.md P2.6, T2): the last input of the find
 * / replace and code evaluation dialogs, and the touch device the root automator found. Their names come from those
 * readers and do not change (R1; two of them are class names with a suffix, checked by MiscStateKeysTest). P4.2 adds
 * the remaining states of the default preferences here.
 *
 * zh-CN: 原先直接调用 `PreferenceManager.getDefaultSharedPreferences` 的读取方保存的记忆型状态与缓存 (ROADMAP P2.6, T2): 查找 /
 * 替换与代码求值对话框的上次输入, Root 自动化找到的触摸设备. 名称沿用这些读取方, 不变 (R1; 其中两个是类名加后缀, 由
 * MiscStateKeysTest 核对). P4.2 把默认偏好中其余的状态也加在这里.
 */
object MiscStateKeys : SettingsDomain {

    /** The search text of the editor's find / replace dialog (the name is three dots, as `FindOrReplaceDialogBuilder` wrote it). */
    @JvmField
    val FIND_OR_REPLACE_KEYWORDS = StateKey.str(name = "...", default = SettingDefault.of(""))

    @JvmField
    val FIND_OR_REPLACE_USING_REGEX = StateKey.bool(name = "find_or_replace_using_regex", default = SettingDefault.of(false))

    /** The last code of the code evaluation dialog (`CodeEvaluateDialogBuilder`). */
    @JvmField
    val CODE_EVALUATE_CODE = StateKey.str(name = "org.autojs.autojs.ui.edit.debug.CodeEvaluateDialogBuilder.code", default = SettingDefault.of(""))

    /** The input device id the root automator detected (`RootAutomatorEngine`); -1 until detected. */
    @JvmField
    val ROOT_AUTOMATOR_TOUCH_DEVICE = CacheKey.int(name = "org.autojs.autojs.engine.RootAutomatorEngine.touch_device", default = SettingDefault.of(-1))

    override val keys: List<PrefKey<*>> = listOf(
        FIND_OR_REPLACE_KEYWORDS, FIND_OR_REPLACE_USING_REGEX, CODE_EVALUATE_CODE, ROOT_AUTOMATOR_TOUCH_DEVICE,
    )
}
