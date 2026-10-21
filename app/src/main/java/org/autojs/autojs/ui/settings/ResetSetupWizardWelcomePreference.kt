package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Clears the "welcome answered" flag, so the home screen offers the setup wizards again on the
 * next launch.
 *
 * zh-CN: 清除 "欢迎提示已处理" 标记, 使主页在下次启动时再次提供设置向导.
 */
class ResetSetupWizardWelcomePreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        MiscStateKeys.SETUP_WIZARD_WELCOME_DONE.remove()
        ViewUtils.showToast(context, R.string.text_setup_wizard_welcome_reset_done)
        super.onClick()
    }

}
