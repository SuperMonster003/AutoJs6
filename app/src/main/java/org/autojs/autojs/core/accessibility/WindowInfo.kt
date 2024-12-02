package org.autojs.autojs.core.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.extension.ArrayExtensions.toHashCode
import org.autojs.autojs.util.ObjectUtils
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on May 18, 2024.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on May 18, 2024.
class WindowInfo(val root: NodeInfo?, val title: CharSequence?, layer: Int, val type: Int) {

    @JvmField
    val packageName: String? = root?.packageName

    @JvmField
    val rootClassName: String? = root?.className

    val order = layer + 1

    operator fun component1() = root

    operator fun component2() = title

    operator fun component3() = order

    operator fun component4() = type

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is WindowInfo -> false
        !ObjectUtils.isEqual(root, other.root) -> false
        order != other.order -> false
        type != other.type -> false
        else -> ObjectUtils.isEqual(title, other.title)
    }

    override fun hashCode(): Int {
        // @Hint by SuperMonster003 on Oct 25, 2024.
        //  ! We've got magic. :)
        //  # var result = root.hashCode()
        //  # result = result * 31 + title.hashCode()
        //  # result = result * 31 + order
        //  # result = result * 31 + type
        //  # return result
        return listOf(root, title, order, type).toHashCode()
    }

    override fun toString() = "WindowInfo(root=$root, title=$title, order=$order, type=$type)"

    fun copy(root: NodeInfo, title: CharSequence?, layer: Int, type: Int) = WindowInfo(root, title, layer, type)

    companion object {

        fun create(context: Context, window: AccessibilityWindowInfo): WindowInfo {
            val root = window.root?.let { NodeInfo.capture(context, it) }
            return WindowInfo(root, window.title, window.layer, window.type)
        }

        internal fun parseWindowType(context: Context, type: Int) = when (type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> context.getString(R.string.text_captured_window_info_type_of_application)
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> context.getString(R.string.text_captured_window_info_type_of_input_method)
            AccessibilityWindowInfo.TYPE_SYSTEM -> context.getString(R.string.text_captured_window_info_type_of_system)
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> context.getString(R.string.text_captured_window_info_type_of_accessibility_overlay)
            AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> context.getString(R.string.text_captured_window_info_type_of_split_screen_divider)
            AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY -> context.getString(R.string.text_captured_window_info_type_of_magnification_overlay)
            else -> null
        }

        internal class WindowInfoRootNodeDataItem(label: String, rawValue: CharSequence?, defaultValue: CharSequence) : WindowInfoDataItem(label, rawValue, defaultValue) {
            override val value = super.value.replaceFirst(Regex("^android\\.widget\\."), "")
        }

        internal class WindowInfoOrderDataItem(label: String, val rawValue: Int) : WindowInfoDataItem(label, "$rawValue", str(R.string.symbol_question_mark)) {
            override val value = "# ${super.value}"
        }

        internal open class WindowInfoDataItem(val label: String, rawValue: CharSequence?, defaultValue: CharSequence = str(R.string.symbol_question_mark)) {
            open val value: String = rawValue?.toString() ?: "[ $defaultValue ]"
        }

        internal data class WindowInfoDataSummary(
            val window: WindowInfo,
            val title: WindowInfoDataItem,
            val order: WindowInfoOrderDataItem,
            val type: WindowInfoDataItem,
            val packageName: WindowInfoDataItem,
            val rootNode: WindowInfoRootNodeDataItem,
        )

    }

}

