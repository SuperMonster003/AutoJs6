package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import org.autojs.autojs.theme.preference.MaterialPreference

/**
 * Settings > Config > "Manage AutoJs6 settings" (docs/settings/ROADMAP.md D4), after the shortcut backup.
 * zh-CN: 设置 > 配置 > "管理 AutoJs6 设置" (ROADMAP D4), 位于快捷方式备份之后.
 */
class SettingsTransferPreference(context: Context, attrs: AttributeSet?) : MaterialPreference(context, attrs) {
    override fun onClick() {
        context.startActivity(Intent(context, SettingsTransferActivity::class.java))
    }
}
