package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.util.Linkify
import android.util.AttributeSet
import androidx.preference.Preference.SummaryProvider
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.util.regex.Pattern

/**
 * Created by SuperMonster003 on Sep 25, 2022.
 */
class AboutAppAndDeveloperPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    init {
        summaryProvider = SummaryProvider<AboutAppAndDeveloperPreference> { "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" }
    }

    override fun longClickPromptDialogHandler(d: MaterialDialog?) {
        super.longClickPromptDialogHandler(d)
        d?.contentView?.let {
            it.setTextIsSelectable(true)
            it.linksClickable = true
            Linkify.addLinks(it, Pattern.compile("\\d{8,}"), null)
        }
        d?.setActionButton(DialogAction.NEUTRAL, R.string.dialog_button_join_group)
        d?.getActionButton(DialogAction.NEUTRAL)!!.apply {
            setTextColor(context.getColor(R.color.dialog_button_hint))
            setOnClickListener {
                try {
                    Intent().apply {
                        @Suppress("SpellCheckingInspection")
                        data = Uri.parse(
                            "mqqopensdkapi://bizAgent/qm/qr" +
                            "?" + "url" + "=" + "http%3A%2F%2Fqm.qq.com" +
                            "%2F" + "cgi-bin" + "%2F" + "qm" + "%2F" + "qr" +
                            "%3F" + "from" + "%3D" + "app" +
                            "%26" + "p" + "%3D" + "android" +
                            "%26" + "jump_from" + "%3D" + "webapi" +
                            "%26" + "k" + "%3D" + "6BH7HuJj29dwE0AIcUuxtAlK6NWlefmH"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }.let { context.startActivity(it) }
                } catch (e: Exception) {
                    context.getString(
                        R.string.error_app_not_installed_with_name,
                        context.getString(R.string.app_name_qq),
                    ).let { msg -> ViewUtils.showToast(context, msg, true) }
                }
            }
        }
    }

    override fun onClick() {
        AboutActivity.startActivity(prefContext)
        super.onClick()
    }

}
