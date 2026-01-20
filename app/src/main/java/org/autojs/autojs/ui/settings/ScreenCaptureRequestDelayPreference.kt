package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Jan 19, 2026.
 */
class ScreenCaptureRequestDelayPreference : MaterialPreference {

    private var dialog: MaterialDialog? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    init {
        summaryProvider = SummaryProvider<ScreenCaptureRequestDelayPreference> {
            prefContext.getString(
                R.string.text_property_colon_value_unit,
                prefContext.getString(R.string.text_delay_time),
                Pref.screenCaptureRequestDelay,
                "ms",
            )
        }
    }

    override fun onClick() {
        run {
            if (dialog?.isShowing == true) {
                return@run
            }
            dialog = ScreenCaptureRequestDelayDialogBuilder(prefContext) {
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

}
