package org.autojs.autojs.ui.floating.layoutinspector

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.yqritc.recyclerviewflexibledivider.HorizontalDividerItemDecoration
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.rhino.extension.NumberExtensions.jsString
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.NodeInfoViewHeaderBinding
import org.autojs.autojs6.databinding.NodeInfoViewItemBinding
import org.opencv.core.Point
import java.lang.reflect.Field

/**
 * Attribute table of one node. A tap copies the attribute as a selector call; a long press enters
 * selection mode, where several attributes are ticked and turned into one chained selector
 * (`id('x').text('y')`) or one object selector (`{ id: 'x', text: 'y' }`) by the hosting dialog.
 * zh-CN: 单个节点的属性表. 单击把该属性复制为选择器调用; 长按进入多选模式, 勾选若干属性后由宿主对话框合并为链式选择器
 * (`id('x').text('y')`) 或对象选择器 (`{ id: 'x', text: 'y' }`).
 *
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Sep 14, 2026.
 */
class NodeInfoView : RecyclerView {

    class Row(val name: String, var value: String = "", var checked: Boolean = false)

    private val mHeader: Row
    private val mRows: List<Row> = FIELD_NAMES.map { Row(it) }

    /** Whether check boxes are shown and taps toggle them. zh-CN: 是否显示复选框且单击切换勾选. */
    var isSelectionMode = false
        private set

    var onSelectionModeChanged: ((Boolean) -> Unit)? = null

    val checkedRows: List<Row>
        get() = mRows.filter { it.checked }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    init {
        mHeader = Row(resources.getString(R.string.text_attribute), resources.getString(R.string.text_value))
        adapter = Adapter()
        layoutManager = LinearLayoutManager(context)
        addItemDecoration(
            HorizontalDividerItemDecoration.Builder(context)
                .color(context.getColor(R.color.layout_node_info_view_decoration_line))
                .size(context.resources.getInteger(R.integer.layout_node_info_view_decoration_line))
                .build()
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setNodeInfo(nodeInfo: NodeInfo) {
        mRows.forEachIndexed { i, row ->
            row.value = formatValue(FIELDS[i], nodeInfo)
            row.checked = false
        }
        setSelectionMode(false)
        adapter!!.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun deselectAll() {
        mRows.forEach { it.checked = false }
        setSelectionMode(false)
        adapter!!.notifyDataSetChanged()
    }

    /** `id('x').text('y').clickable(true)` from the ticked rows. zh-CN: 由勾选行生成 `id('x').text('y').clickable(true)`. */
    fun buildChainedSelector(): String = NodeSelectorSnippets.chainedSelector(checkedRows.map { it.name to it.value })

    /**
     * `{ id: 'x', bounds: [l, t, r, b], ... }` from the ticked rows, in the object form accepted by
     * `pickup()` / `detect()`.
     * zh-CN: 由勾选行生成 `pickup()` / `detect()` 接受的对象形式 `{ id: 'x', bounds: [l, t, r, b], ... }`.
     */
    fun buildObjectSelector(): String = NodeSelectorSnippets.objectSelector(checkedRows.map { it.name to it.value })

    @SuppressLint("NotifyDataSetChanged")
    private fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode == enabled) return
        isSelectionMode = enabled
        adapter!!.notifyDataSetChanged()
        onSelectionModeChanged?.invoke(enabled)
    }

    private fun toggle(row: Row, position: Int) {
        row.checked = !row.checked
        when {
            row.checked -> setSelectionMode(true)
            checkedRows.isEmpty() -> setSelectionMode(false)
        }
        adapter!!.notifyItemChanged(position)
    }

    private fun copySingle(row: Row) {
        ClipboardUtils.setClip(context, NodeSelectorSnippets.selectorCall(row.name, row.value))
        ViewUtils.showSnack(this, R.string.text_already_copied_to_clip)
    }

    private fun formatValue(field: Field, nodeInfo: NodeInfo): String = try {
        when (val value = field.get(nodeInfo)) {
            is List<*> -> when (field.name) {
                "actionNames" -> value.joinToString("\n") { it.toString().replace("^ACTION_".toRegex(), "") }
                else -> value.joinToString("\n")
            }
            else -> when (field.name) {
                "bounds" -> (value as? Rect)?.let { "[ ${it.left}, ${it.top}, ${it.right}, ${it.bottom} ]" } ?: value?.toString() ?: ""
                "center" -> (value as? Point)?.let { "[ ${it.x.jsString}, ${it.y.jsString} ]" } ?: value?.toString() ?: ""
                else -> value?.toString() ?: ""
            }
        }
    } catch (e: Exception) {
        throw RuntimeException(e)
    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {

        val mViewTypeHeader = 0
        val mViewTypeItem = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
            mViewTypeHeader -> NodeInfoViewHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            else -> NodeInfoViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }.let { ViewHolder(it) }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val row = if (position == 0) mHeader else mRows[position - 1]
            holder.attrName.text = row.name
            holder.attrValue.text = row.value
            holder.check?.apply {
                visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                isChecked = row.checked
            }
        }

        override fun getItemCount(): Int = mRows.size + 1

        override fun getItemViewType(position: Int): Int = if (position == 0) mViewTypeHeader else mViewTypeItem

    }

    private inner class ViewHolder(itemViewBinding: ViewBinding) : RecyclerView.ViewHolder(itemViewBinding.root) {

        val attrName: TextView
        val attrValue: TextView
        val check: CheckBox?

        private fun rowAt(): Pair<Row, Int>? = bindingAdapterPosition
            .takeIf { it in 1..mRows.size }
            ?.let { mRows[it - 1] to it }

        init {
            when (itemViewBinding) {
                is NodeInfoViewHeaderBinding -> {
                    attrName = itemViewBinding.name
                    attrValue = itemViewBinding.value
                    check = null
                }
                is NodeInfoViewItemBinding -> {
                    attrName = itemViewBinding.name
                    attrValue = itemViewBinding.value
                    check = itemViewBinding.check
                    itemViewBinding.root.setOnClickListener {
                        rowAt()?.let { (row, position) ->
                            if (isSelectionMode) toggle(row, position) else copySingle(row)
                        }
                    }
                    itemViewBinding.root.setOnLongClickListener {
                        rowAt()?.let { (row, position) ->
                            if (row.checked) setSelectionMode(true) else toggle(row, position)
                            true
                        } ?: false
                    }
                }
                else -> throw IllegalArgumentException("Unknown binding: $itemViewBinding")
            }
        }

    }

    companion object {

        private val FIELD_NAMES = arrayOf(
            // Common
            "packageName", "id", "fullId", "idHex",
            "desc", "text",
            "bounds", "center", "className",
            "clickable", "longClickable", "scrollable",
            "indexInParent", "childCount", "depth",

            // Regular
            "checked", "enabled", "editable", "focusable", "checkable",
            "selected", "dismissable", "visibleToUser",

            // Rare
            "contextClickable", "focused", "accessibilityFocused",
            "rowCount", "columnCount", "row", "column", "rowSpan", "columnSpan",
            "drawingOrder",

            // Last
            "actionNames",
        )

        private val FIELDS = Array<Field>(FIELD_NAMES.size) {
            NodeInfo::class.java.getDeclaredField(FIELD_NAMES[it]).apply { isAccessible = true }
        }

    }

}
