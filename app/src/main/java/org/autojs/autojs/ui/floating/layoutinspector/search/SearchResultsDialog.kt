package org.autojs.autojs.ui.floating.layoutinspector.search

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.ui.floating.layoutinspector.InspectorDialogBuilder
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.inspector.InspectorNode
import org.autojs.autojs.core.accessibility.inspector.SelectorExport
import org.autojs.autojs.core.automator.diagnostics.SelectorExplainer
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyView
import org.autojs.autojs.ui.floating.layoutinspector.InspectorTheme
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.LayoutInspectorResultItemBinding

/**
 * The flat list of the current matches (roadmap P2.1, D1): one row per match with the node summary
 * (hit substring highlighted, the current match in the theme color) over its path, depth and bounds.
 * Tapping a row makes it the current match and lets the window reveal it; long-pressing opens the
 * node info dialog. With no match the dialog explains why: one filter means nothing hit the
 * keyword, several filters get the per-filter tally of [SelectorExplainer].
 *
 * zh-CN: 当前匹配项的扁平列表 (Roadmap P2.1, D1): 每行为节点摘要 (命中子串高亮, 当前匹配项用主题色), 下方为路径, 深度与边界.
 * 点击某行使其成为当前匹配项并由窗口展示; 长按打开节点信息对话框. 零匹配时解释原因: 单个过滤器即 "没有控件命中关键词",
 * 多个过滤器则给出 [SelectorExplainer] 的逐过滤器计数.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class SearchResultsDialog(
    private val context: Context,
    private val session: InspectorSearchSession,
    private val host: InspectorSearchBar.Host,
) {

    private var dialog: MaterialDialog? = null

    private val colors = InspectorTheme(context)

    fun show() {
        val matches = session.match.matches.map { it.info }
        val builder = InspectorDialogBuilder(context)
            .title(context.getString(R.string.text_inspector_matches_title, matches.size))
            .positiveText(R.string.dialog_button_dismiss)
            // "Copy as selector" (roadmap P4.2, D8) and "Copy as pickup" (selector P3.5) live here since the bar has no overflow menu.
            // zh-CN: "复制为选择器" (Roadmap P4.2, D8) 与 "复制为 pickup" (selector P3.5) 放在这里, 因为搜索栏没有溢出菜单.
            .neutralText(R.string.text_inspector_copy_as_selector)
            .onNeutral { _, _ -> SelectorClipboard.copy(context, session) { it.error?.toString().orEmpty() } }
            .negativeText(R.string.text_inspector_copy_as_pickup)
            .onNegative { _, _ -> SelectorClipboard.copy(context, session, SelectorExport.Form.PICKUP) { it.error?.toString().orEmpty() } }
        var list: RecyclerView? = null
        if (matches.isEmpty()) {
            builder.content(explain())
        } else {
            list = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = Adapter(matches)
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            builder.customView(list, false)
        }
        val dialog = builder.build()
        dialog.window!!.setType(FloatyWindowManger.getWindowType())
        this.dialog = dialog
        dialog.show()
        val current = session.currentIndex
        if (list != null && current > 0) {
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(current, 0)
        }
    }

    private fun explain(): CharSequence {
        val filters = session.filters
        if (filters.size <= 1) return context.getString(R.string.text_inspector_no_node_matches_keyword)
        val explanation = SelectorExplainer.explain(listOf(InspectorNode(session.root)), filters)
        return explanation.steps.joinToString("\n") { step ->
            context.getString(R.string.text_inspector_filter_tally, step.filter, step.matched, step.cumulative)
        }
    }

    private fun highlight(summary: String): CharSequence {
        val range = session.highlightRange(summary) ?: return summary
        return SpannableString(summary).apply {
            setSpan(BackgroundColorSpan(colors.matchBackground), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(colors.matchText), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * `FrameLayout > LinearLayout > Button` from the simplified class names of the ancestors and
     * the node; long paths keep the first and last [PATH_EDGE] segments around an ellipsis.
     * zh-CN: 由祖先与节点的简化类名拼成 `FrameLayout > LinearLayout > Button`; 过长时只保留首尾各 [PATH_EDGE] 段, 中间省略.
     */
    private fun describePath(node: NodeInfo): String {
        val names = ArrayList<String>()
        var cursor: NodeInfo? = node
        while (cursor != null) {
            names.add(LayoutHierarchyView.simplifyClassName(cursor.className).ifEmpty { "?" })
            cursor = cursor.parent
        }
        names.reverse()
        val shown = if (names.size > PATH_EDGE * 2 + 1) names.take(PATH_EDGE) + ELLIPSIS + names.takeLast(PATH_EDGE) else names
        return shown.joinToString(" > ")
    }

    /** The path on the first line, depth and bounds on the second. zh-CN: 第一行为路径, 第二行为深度与边界. */
    private fun describeDetails(node: NodeInfo): String {
        return "${describePath(node)}\ndepth ${node.depth}  |  ${node.boundsInScreen.toShortString()}"
    }

    private inner class Adapter(private val nodes: List<NodeInfo>) : RecyclerView.Adapter<Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInspectorResultItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount() = nodes.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val node = nodes[position]
            val isCurrent = node === session.currentMatch
            holder.binding.title.text = highlight(LayoutHierarchyView.summarize(node))
            holder.binding.title.setTextColor(if (isCurrent) colors.accent else colors.text)
            holder.binding.subtitle.text = describeDetails(node)
            holder.binding.subtitle.setTextColor(colors.secondaryText)
            holder.binding.root.setOnClickListener {
                session.setCurrentMatch(node)
                dialog?.dismiss()
                host.onCurrentMatchChanged(node)
            }
            holder.binding.root.setOnLongClickListener {
                dialog?.dismiss()
                host.onShowNodeInfo(node)
                true
            }
        }

    }

    private class Holder(val binding: LayoutInspectorResultItemBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {

        private const val PATH_EDGE = 2

        private const val ELLIPSIS = "…"

    }

}
