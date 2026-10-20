package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.pref.keys.LayoutInspectorKeys
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/** Node attributes with a stable selection layout and a remembered selector copy format. */
class NodeInfoDialog(private val context: Context) {

    enum class CopyMode(val label: Int) {
        ASK(R.string.text_inspector_copy_always_ask),
        OBJECT(R.string.text_copy_object_selector),
        CHAINED(R.string.text_copy_chained_selector);

        companion object {
            fun load(): CopyMode = entries.firstOrNull {
                it.name == LayoutInspectorKeys.LAYOUT_INSPECTOR_NODE_COPY_MODE.storedOrNull()
            } ?: ASK
        }

        fun save() = LayoutInspectorKeys.LAYOUT_INSPECTOR_NODE_COPY_MODE.set(name)
    }

    private val info = NodeInfoView(context).apply {
        onSelectionChanged = { updateButtons() }
    }

    private val dialog: MaterialDialog = InspectorDialogBuilder(context)
        .customView(info, false)
        .autoDismiss(false)
        .positiveText(R.string.dialog_button_dismiss)
        .onPositive { _, _ -> if (info.isSelectionMode) copy() else dialogDismiss() }
        .onNegative { _, _ -> info.deselectAll() }
        .onNeutral { _, _ -> showSettings() }
        .build()
        .also { it.window!!.setType(FloatyWindowManger.getWindowType()) }

    fun show(node: NodeInfo) {
        info.setNodeInfo(node)
        updateButtons()
        dialog.show()
        if (!hintShown) {
            hintShown = true
            ViewUtils.showToast(context, R.string.text_long_press_to_select_multiple_attributes, true)
        }
    }

    private fun dialogDismiss() = dialog.dismiss()

    private fun updateButtons() {
        dialog.setActionButton(DialogAction.POSITIVE, if (info.isSelectionMode) R.string.dialog_button_copy else R.string.dialog_button_dismiss)
        dialog.setActionButton(DialogAction.NEGATIVE, if (info.isSelectionMode) context.getString(R.string.dialog_button_back) else null)
        dialog.setActionButton(DialogAction.NEUTRAL, if (info.isSelectionMode) context.getString(R.string.text_settings) else null)
        dialog.getActionButton(DialogAction.POSITIVE).isEnabled = !info.isSelectionMode || info.checkedRows.isNotEmpty()
    }

    private fun showSettings() {
        InspectorDialogBuilder(context)
            .title(R.string.text_inspector_copy_button_behavior)
            .items(CopyMode.entries.map { context.getString(it.label) })
            .itemsCallbackSingleChoice(CopyMode.load().ordinal) { _, _, position, _ ->
                CopyMode.entries[position].save()
                true
            }
            .positiveText(R.string.dialog_button_confirm)
            .negativeText(R.string.dialog_button_back)
            .build()
            .also { it.window!!.setType(FloatyWindowManger.getWindowType()) }
            .show()
    }

    private fun copy() {
        if (info.checkedRows.isEmpty()) return
        when (val mode = CopyMode.load()) {
            CopyMode.OBJECT, CopyMode.CHAINED -> copy(mode)
            CopyMode.ASK -> InspectorDialogBuilder(context)
                .title(R.string.text_copy)
                .items(context.getString(CopyMode.OBJECT.label), context.getString(CopyMode.CHAINED.label))
                .itemsCallback { _, _, position, _ -> copy(if (position == 0) CopyMode.OBJECT else CopyMode.CHAINED) }
                .negativeText(R.string.dialog_button_back)
                .build()
                .also { it.window!!.setType(FloatyWindowManger.getWindowType()) }
                .show()
        }
    }

    private fun copy(mode: CopyMode) {
        ClipboardUtils.setClip(context, if (mode == CopyMode.OBJECT) info.buildObjectSelector() else info.buildChainedSelector())
        ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
        dialog.dismiss()
    }

    companion object {
        private var hintShown = false
    }
}
