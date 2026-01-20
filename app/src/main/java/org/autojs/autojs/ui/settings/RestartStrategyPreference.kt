package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs6.R

class RestartStrategyPreference : MaterialListPreference {

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr, 0)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) :
            super(context, attrs, 0, 0)

    @Suppress("unused")
    constructor(context: Context) :
            super(context, null, 0, 0)

    @Suppress("unused")
    override fun getBuilder(): MaterialDialog.Builder = super.getBuilder().also { builder ->
        builder.configureNeutralButton()
        builder.itemsCallbackSingleChoice(itemPrefIndex ?: itemDefaultIndex) { d, _, _, _ ->
            // @Caution by SuperMonster003 on Dec 29, 2025.
            //  ! Instance d equals getDialog() return value when the dialog is first shown,
            //  ! but after the dialog is closed and shown again, their values will be different.
            //  ! The behavior is that d always keeps the old object reference,
            //  ! while getDialog() returns a new object each time.
            //  ! zh-CN:
            //  ! 实例 d 与 getDialog() 返回值在首次显示对话框时相等, 但对话框关闭后再次显示后, 它们的值将不相同.
            //  ! 表现为 d 总是保持旧的对象引用, 而 getDialog() 每次返回新对象.
            getDialog().configureNeutralButton()
            return@itemsCallbackSingleChoice true
        }
        builder.alwaysCallSingleChoiceCallback()
        builder.onPositive { d, _ ->
            val which = d.selectedIndex
            if (itemPrefIndex != which) {
                itemPrefIndex = which
                showPrompt() ?: onChangeConfirmed(d)
            } else {
                d.dismiss()
            }
        }
    }

    override fun useDefaultOptionMenuItemSpecOnClickListener(dialog: MaterialDialog) {
        dialog.selectedIndex = itemDefaultIndex
        dialog.configureNeutralButton()
    }

    private fun MaterialDialog.Builder.configureNeutralButton() {
        val builder = this.apply {
            neutralColorRes(R.color.dialog_button_advanced_settings)
        }
        when (entry) {
            context.getString(R.string.entry_restart_strategy_scheduled) -> {
                builder.neutralText(R.string.dialog_button_advanced_settings)
                builder.onNeutral { _, _ ->
                    ScheduledRestartSettingsDialogBuilder(context).build().show()
                }
            }
            context.getString(R.string.entry_restart_strategy_quick) -> {
                builder.neutralText("")
                builder.onNeutral { _, _ ->
                    /* Nothing to do. */
                }
            }
        }
    }

    private fun MaterialDialog.configureNeutralButton() {
        val dialog = this
        val neutral = dialog.getActionButton(DialogAction.NEUTRAL)
        when (dialog.items?.get(dialog.selectedIndex)?.toString()) {
            context.getString(R.string.entry_restart_strategy_scheduled) -> {
                neutral.text = context.getString(R.string.dialog_button_advanced_settings)
                neutral.setOnClickListener {
                    ScheduledRestartSettingsDialogBuilder(context).build().show()
                }
            }
            context.getString(R.string.entry_restart_strategy_quick) -> {
                neutral.text = ""
                neutral.setOnClickListener(null)
            }
            else -> null
        }?.let {
            neutral.requestLayout()
            neutral.invalidate()
        }
    }

}