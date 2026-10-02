package org.autojs.autojs.ui.floating.layoutinspector

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
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
 * selection mode, where several attributes are selected and turned into one chained selector
 * (`id('x').text('y')`) or one object selector (`{ id: 'x', text: 'y' }`) by the hosting dialog.
 * zh-CN: 单个节点的属性表. 单击把该属性复制为选择器调用; 长按进入多选模式, 选择若干属性后由宿主对话框合并为链式选择器
 * (`id('x').text('y')`) 或对象选择器 (`{ id: 'x', text: 'y' }`).
 *
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Sep 14, 2026.
 */
class NodeInfoView : RecyclerView {

    class Row(val name: String, var value: String = "", var checked: Boolean = false)

    private val mHeader: Row
    private val mRows: List<Row> = FIELD_NAMES.map { Row(it) }

    /** Taps toggle selected row backgrounds without changing the row geometry. */
    var isSelectionMode = false
        private set

    var onSelectionChanged: (() -> Unit)? = null

    private val colors by lazy { InspectorTheme(context) }

    val checkedRows: List<Row>
        get() = mRows.filter { it.checked }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    init {
        mHeader = Row(resources.getString(R.string.text_attribute), resources.getString(R.string.text_value))
        adapter = Adapter()
        layoutManager = LinearLayoutManager(context)
        itemAnimator = null
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
        isSelectionMode = false
        adapter!!.notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun deselectAll() {
        mRows.forEach { it.checked = false }
        isSelectionMode = false
        adapter!!.notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    /** `id('x').text('y').clickable(true)` from the selected rows. zh-CN: 由选中行生成 `id('x').text('y').clickable(true)`. */
    fun buildChainedSelector(): String = NodeSelectorSnippets.chainedSelector(checkedRows.map { it.name to it.value })

    /**
     * `{ id: 'x', bounds: [l, t, r, b], ... }` from the selected rows, in the object form accepted by
     * `pickup()` / `detect()`.
     * zh-CN: 由选中行生成 `pickup()` / `detect()` 接受的对象形式 `{ id: 'x', bounds: [l, t, r, b], ... }`.
     */
    fun buildObjectSelector(): String = NodeSelectorSnippets.objectSelector(checkedRows.map { it.name to it.value })

    private fun toggle(row: Row, position: Int) {
        row.checked = !row.checked
        isSelectionMode = true
        adapter!!.notifyItemChanged(position)
        onSelectionChanged?.invoke()
    }

    private fun copySingle(row: Row) {
        ClipboardUtils.setClip(context, NodeSelectorSnippets.selectorCall(row.name, row.value))
        ViewUtils.showSnack(this, R.string.text_already_copied_to_clip)
    }

    private fun formatValue(field: Field, nodeInfo: NodeInfo): String = try {
        val value: Any? = field.get(nodeInfo)
        when (value) {
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
            holder.itemView.isSelected = row.checked
            holder.itemView.setBackgroundColor(if (row.checked) colors.selectionBackground else android.graphics.Color.TRANSPARENT)
            val textColor = if (row.checked) colors.selectionText else colors.text
            holder.attrName.setTextColor(textColor)
            holder.attrValue.setTextColor(textColor)
        }

        override fun getItemCount(): Int = mRows.size + 1

        override fun getItemViewType(position: Int): Int = if (position == 0) mViewTypeHeader else mViewTypeItem

    }

    private inner class ViewHolder(itemViewBinding: ViewBinding) : RecyclerView.ViewHolder(itemViewBinding.root) {

        val attrName: TextView
        val attrValue: TextView

        private fun rowAt(): Pair<Row, Int>? = bindingAdapterPosition
            .takeIf { it in 1..mRows.size }
            ?.let { mRows[it - 1] to it }

        init {
            when (itemViewBinding) {
                is NodeInfoViewHeaderBinding -> {
                    attrName = itemViewBinding.name
                    attrValue = itemViewBinding.value
                }
                is NodeInfoViewItemBinding -> {
                    attrName = itemViewBinding.name
                    attrValue = itemViewBinding.value
                    itemViewBinding.root.setOnClickListener {
                        rowAt()?.let { (row, position) ->
                            if (isSelectionMode) toggle(row, position) else copySingle(row)
                        }
                    }
                    itemViewBinding.root.setOnLongClickListener {
                        rowAt()?.let { (row, position) ->
                            if (!row.checked) toggle(row, position)
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
