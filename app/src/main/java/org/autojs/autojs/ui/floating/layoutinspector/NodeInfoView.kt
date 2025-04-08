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
import org.autojs.autojs.extension.NumberExtensions.jsString
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.NodeInfoViewHeaderBinding
import org.autojs.autojs6.databinding.NodeInfoViewItemBinding
import org.opencv.core.Point
import java.lang.reflect.Field
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
class NodeInfoView : RecyclerView {

    // TODO by 抠脚本人 on Jul 12, 2023.
    //  ! 调整数据结构, 对话框关闭后根据已勾选的属性, 生成选择器.
    //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
    //  ! Adjust the data structure.
    //  ! When the dialog is dismissed, generate selectors based on the selected attributes.
    private val data = Array(FIELDS.size + 1) { Array(2) { "" } }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    init {
        initData()
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
        for (i in FIELDS.indices) {
            try {
                data[i + 1][1] = when (val value = FIELDS[i].get(nodeInfo)) {
                    is List<*> -> {
                        when (FIELDS[i].name) {
                            "actionNames" -> value.joinToString("\n") {
                                it.toString().replace("^ACTION_".toRegex(), "")
                            }
                            else -> value.joinToString("\n")
                        }
                    }
                    else -> {
                        when (FIELDS[i].name) {
                            "bounds" -> (value as? Rect)?.let { "[ ${it.left}, ${it.top}, ${it.right}, ${it.bottom} ]" } ?: value?.toString() ?: ""
                            "center" -> (value as? Point)?.let { "[ ${it.x.jsString}, ${it.y.jsString} ]" } ?: value?.toString() ?: ""
                            else -> value?.toString() ?: ""
                        }
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        adapter!!.notifyDataSetChanged()
    }

    private fun initData() {
        data[0][0] = resources.getString(R.string.text_attribute)
        data[0][1] = resources.getString(R.string.text_value)
        for (i in 1 until data.size) {
            data[i][0] = FIELD_NAMES[i - 1]
            data[i][1] = ""
        }
    }

    fun getCheckedDate(): Array<String> {
        // TODO by 抠脚本人 on Jul 12, 2023.
        //  ! 数据增加 checked 属性, 区分已选中项目.
        //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
        //  ! Add the checked attribute to the data to distinguish the selected items.
        val checkedArr = data.filter { it[0] == "id" || it[0] == "text" }
        return Array(checkedArr.size) {
            dataToFx(checkedArr[it])
        }
    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {

        val mViewTypeHeader = 0
        val mViewTypeItem = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
            mViewTypeHeader -> NodeInfoViewHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            else -> NodeInfoViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }.let { ViewHolder(it) }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.apply {
                data[position].let {
                    // attrChecked.isChecked = false
                    attrName.text = it[0]
                    attrValue.text = it[1]
                }
            }
        }

        override fun getItemCount(): Int = data.size

        override fun getItemViewType(position: Int): Int = if (position == 0) mViewTypeHeader else mViewTypeItem

    }

    private inner class ViewHolder(itemViewBinding: ViewBinding) : RecyclerView.ViewHolder(itemViewBinding.root) {

        val attrName: TextView
        val attrValue: TextView

        init {
            itemViewBinding.root.setOnClickListener {
                bindingAdapterPosition.takeIf { it in 1.until(data.size) }?.let { i ->
                    ClipboardUtils.setClip(context, dataToFx(this@NodeInfoView.data[i]))
                    ViewUtils.showSnack(this@NodeInfoView, R.string.text_already_copied_to_clip)
                }
            }
            when (itemViewBinding) {
                is NodeInfoViewHeaderBinding -> {
                    attrName = itemViewBinding.name
                    attrValue = itemViewBinding.value
                }
                is NodeInfoViewItemBinding -> {
                    attrName = itemViewBinding.name
                    attrValue = itemViewBinding.value
                }
                else -> throw IllegalArgumentException("Unknown binding: $itemViewBinding")
            }
        }

    }

    private fun dataToFx(data: Array<String>): String {
        val attr = data[0]
        var value = data[1]
        when (attr) {
            "className" -> value = value.replace("^android\\.widget\\.".toRegex(), "")
            "actionNames" -> return "action(${value.split("\n").joinToString(", ") { "'$it'" }})"
            "bounds" -> return "$attr(${value.replace("[^\\d,]".toRegex(), "").replace(",", ", ")})"
            "center" -> {
                val (x, y) = value.split(Regex(",\\s*")).map {
                    it.replace(Regex("[^\\d.]+"), "").toDouble()
                }
                var result = "centerX(%X%).centerY(%Y%)"

                result = if (x == x.toLong().toDouble()) {
                    result.replace("%X%", x.toLong().toString())
                } else {
                    result.replace("%X%", "${floor(x).jsString}, ${ceil(x).jsString}")
                }

                result = if (y == y.toLong().toDouble()) {
                    result.replace("%Y%", y.toLong().toString())
                } else {
                    result.replace("%Y%", "${floor(y).jsString}, ${ceil(y).jsString}")
                }

                return result
            }
        }
        return when (NodeInfo::class.java.getDeclaredField(attr).type) {
            java.lang.String::class.java -> "$attr('$value')"
            else -> "$attr($value)"
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