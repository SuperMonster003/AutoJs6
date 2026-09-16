package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.MaterialPreference

/**
 * A retention limit row; the preference key selects its spec from [RetentionLimitSettings].
 * zh-CN: 单个保留策略条目; 偏好 key 决定其在 [RetentionLimitSettings] 中的规格.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
class RetentionLimitPreference : MaterialPreference {

    private var dialog: MaterialDialog? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    init {
        summaryProvider = SummaryProvider<RetentionLimitPreference> { pref ->
            val spec = pref.spec() ?: return@SummaryProvider null
            RetentionLimitSettings.formatValue(prefContext, spec, RetentionLimitSettings.read(spec))
        }
    }

    override fun onClick() {
        run {
            if (dialog?.isShowing == true) {
                return@run
            }
            // A key without a spec is a misconfigured XML entry; nothing sensible to edit.
            // zh-CN: key 没有对应规格属于 XML 配置错误, 无可编辑内容.
            val spec = spec() ?: return@run
            dialog = RetentionLimitDialogBuilder(prefContext, spec) {
                notifyChanged()
            }.show()
        }
        super.onClick()
    }

    override fun onDetached() {
        dialog?.dismiss()
        dialog = null
        super.onDetached()
    }

    private fun spec(): RetentionLimitSettings.Spec? = RetentionLimitSettings.specForKey(prefContext, key)
}
