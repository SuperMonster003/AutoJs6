package org.autojs.autojs.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.os.Build
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import androidx.preference.PreferenceViewHolder
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.LongClickablePreferenceLike
import org.autojs.autojs.ui.explorer.ExplorerView
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/8/4.
 * Modified by SuperMonster003 as of Sep 10, 2022.
 * Transformed by SuperMonster003 on Oct 19, 2022.
 */
object DialogUtils {

    @JvmStatic
    fun <T : MaterialDialog> showDialog(dialog: T): T {
        val context = dialog.context
        if (!isActivityContext(context)) {
            val window = dialog.window
            val type = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                true -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else -> {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
            }
            window?.setType(type)
        }
        if (Looper.getMainLooper() == Looper.myLooper()) {
            dialog.show()
        } else {
            GlobalAppContext.post { dialog.show() }
        }
        return dialog
    }

    @JvmStatic
    fun <T : MaterialDialog> fixCheckBoxGravity(dialog: T): T = dialog.also {
        it.view.findViewById<CheckBox>(com.afollestad.materialdialogs.R.id.md_promptCheckbox)?.gravity = Gravity.CENTER_VERTICAL
    }

    @JvmStatic
    fun isActivityContext(context: Context?): Boolean {
        return context is Activity || context is ContextWrapper && isActivityContext(context.baseContext)
    }

    fun toggleContentViewByItems(dialog: MaterialDialog) {
        dialog.contentView?.visibility = when {
            dialog.items.isNullOrEmpty() -> View.VISIBLE
            else -> View.GONE
        }
    }

    @JvmStatic
    fun adaptToExplorer(dialog: MaterialDialog, explorerView: ExplorerView): MaterialDialog {
        val time = object : Any() {
            var lastPressed: Long = 0
            val minPressInterval: Long = 1000
        }
        dialog.setOnKeyListener { dialogInterface: DialogInterface, keyCode: Int, event: KeyEvent ->
            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                if (explorerView.canGoBack()) {
                    explorerView.goBack()
                    return@setOnKeyListener true
                }
                if (System.currentTimeMillis() - time.lastPressed < time.minPressInterval) {
                    dialogInterface.dismiss()
                } else {
                    time.lastPressed = System.currentTimeMillis()
                    showSnack(explorerView, R.string.text_press_again_to_dismiss_dialog)
                }
            }
            false
        }
        return dialog
    }

    fun applyLongClickability(preference: LongClickablePreferenceLike, holder: PreferenceViewHolder) {
        holder.itemView.setOnLongClickListener {
            preference.longClickPrompt?.let { content ->
                MaterialDialog.Builder(preference.prefContext)
                    .title(preference.prefTitle ?: preference.prefContext.getString(R.string.text_prompt))
                    .content(content)
                    .apply {
                        preference.longClickPromptMore?.let { contentMore ->
                            neutralText(R.string.dialog_button_more)
                            neutralColorRes(R.color.dialog_button_hint)
                            onNeutral { _, _ ->
                                MaterialDialog.Builder(context)
                                    .title(context.getString(R.string.dialog_button_more))
                                    .content(contentMore)
                                    .positiveText(R.string.dialog_button_dismiss)
                                    .build()
                                    .also { preference.longClickPromptMoreDialogHandler(it) }
                                    .show()
                            }
                        }
                    }
                    .positiveText(R.string.dialog_button_dismiss)
                    .onPositive { dialog, _ -> dialog.dismiss() }
                    .autoDismiss(false)
                    .build()
                    .also { preference.longClickPromptDialogHandler(it) }
                    .show()
                true
            } ?: false
        }
    }

}