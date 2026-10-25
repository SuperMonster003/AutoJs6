package org.autojs.autojs.ui.floating.layoutinspector.search

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.accessibility.inspector.SelectorExport
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * "Copy as selector" (roadmap P4.2): renders the session's conditions with
 * [InspectorSearchSession.exportSelector], puts the text on the clipboard and tells the user; a
 * query that does not compile or an empty condition set is reported instead. Shared by the search
 * bar (long-press on the counter) and the results dialog (neutral button; the negative button copies
 * the [SelectorExport.Form.PICKUP] form, selector P3.5).
 * zh-CN: "复制为选择器" (Roadmap P4.2): 以 [InspectorSearchSession.exportSelector] 渲染会话条件, 写入剪贴板并提示用户; 查询无法
 * 编译或没有条件时改为提示. 搜索栏 (长按计数位) 与结果对话框 (中性按钮; 消极按钮复制 [SelectorExport.Form.PICKUP] 形式, selector P3.5) 共用.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
object SelectorClipboard {

    private const val TAG = "SelectorClipboard"

    /** @return the copied text, or null when nothing was copied. zh-CN: 返回已复制的文本, 未复制时为 null. */
    fun copy(
        context: Context,
        session: InspectorSearchSession,
        form: SelectorExport.Form = SelectorExport.Form.CHAIN,
        describeError: (InspectorSearchSession) -> CharSequence,
    ): String? {
        val rendered = session.exportSelector()
        if (rendered == null) {
            ViewUtils.showToast(context, describeError(session), true)
            return null
        }
        if (rendered.isEmpty) {
            ViewUtils.showToast(context, R.string.text_inspector_nothing_to_copy)
            return null
        }
        val text = rendered.text(form)
        ClipboardUtils.setClip(context, text)
        Log.d(TAG, "copy as ${form.name.lowercase()}: $text")
        ViewUtils.showToast(context, context.getString(R.string.text_inspector_selector_copied, text), true)
        return text
    }

}
