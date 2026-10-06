package org.autojs.autojs.theme.app

import android.content.Context
import androidx.annotation.StringRes
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.library.LibraryNaming
import org.autojs.autojs6.R

/**
 * Dialogs shared by the color library pages (roadmap P1.1).
 * zh-CN: 颜色库页面共用的对话框 (Roadmap P1.1).
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object ColorLibraryDialogs {

    /**
     * Asks for a name; [onConfirmed] receives the normalized, non-empty name.
     * zh-CN: 输入名称; [onConfirmed] 收到规范化后的非空名称.
     */
    fun showNameInput(
        context: Context,
        @StringRes titleRes: Int,
        prefill: String?,
        @StringRes hintRes: Int = R.string.text_color_library_name,
        allowEmpty: Boolean = false,
        onConfirmed: (String?) -> Unit,
    ) {
        MaterialDialog.Builder(context)
            .title(titleRes)
            .input(context.getString(hintRes), prefill, allowEmpty) { _, input ->
                val name = LibraryNaming.normalize(input.toString())
                if (name != null || allowEmpty) onConfirmed(name)
            }
            .inputRange(if (allowEmpty) 0 else 1, LibraryNaming.MAX_LENGTH)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    fun showConfirm(context: Context, content: String, onConfirmed: () -> Unit) {
        MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(content)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ -> onConfirmed() }
            .show()
    }

    /**
     * A plain list of actions; [actions] pairs a label with its handler.
     * zh-CN: 简单的操作列表; [actions] 为标签与对应处理函数.
     */
    fun showActions(context: Context, title: String, actions: List<Pair<String, () -> Unit>>) {
        MaterialDialog.Builder(context)
            .title(title)
            .items(actions.map { it.first })
            .itemsCallback { dialog, _, which, _ ->
                dialog.dismiss()
                actions.getOrNull(which)?.second?.invoke()
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .show()
    }

    fun showSingleChoice(context: Context, @StringRes titleRes: Int, labels: List<String>, onChosen: (Int) -> Unit) {
        MaterialDialog.Builder(context)
            .title(titleRes)
            .items(labels)
            .itemsCallback { dialog, _, which, _ ->
                dialog.dismiss()
                onChosen(which)
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .show()
    }

    /**
     * Multi-choice list; [onChosen] receives the checked indices in ascending order (never empty).
     * zh-CN: 多选列表; [onChosen] 收到按升序排列的已勾选下标 (不会为空).
     */
    fun showMultiChoice(context: Context, @StringRes titleRes: Int, labels: List<String>, onChosen: (List<Int>) -> Unit) {
        MaterialDialog.Builder(context)
            .title(titleRes)
            .items(labels)
            .itemsCallbackMultiChoice(null) { _, indices, _ ->
                indices.filterNotNull().sorted().takeIf { it.isNotEmpty() }?.let(onChosen)
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

}
