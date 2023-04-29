package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on 2017/3/9.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
class BooleanFilter(private val mBooleanSupplier: BooleanSupplier, private val mExceptedValue: Boolean? = null) : Filter {

    interface BooleanSupplier {
        operator fun get(node: UiObject): Boolean
    }

    override fun filter(node: UiObject) = mBooleanSupplier[node] == (mExceptedValue ?: DEFAULT)

    override fun toString() = "$mBooleanSupplier(${mExceptedValue ?: ""})"

    companion object {

        const val DEFAULT = true

        private val cache = HashMap<BooleanSupplier, Array<BooleanFilter>>()

        operator fun get(supplier: BooleanSupplier, b: Boolean): BooleanFilter {
            val booleanFilters: Array<BooleanFilter> = cache[supplier] ?: Array(2) {
                BooleanFilter(supplier, it != 0)
            }.also { cache[supplier] = it }
            return booleanFilters[if (b) 1 else 0]
        }

        val CHECKABLE = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isCheckable
            override fun toString() = "checkable"
        }

        val CHECKED = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isChecked
            override fun toString() = "checked"
        }

        val FOCUSABLE = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isFocusable
            override fun toString() = "focusable"
        }

        val FOCUSED = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isFocused
            override fun toString() = "focused"
        }

        val VISIBLE_TO_USER = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isVisibleToUser
            override fun toString() = "visibleToUser"
        }

        val ACCESSIBILITY_FOCUSED = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isAccessibilityFocused
            override fun toString() = "accessibilityFocused"
        }

        val SELECTED = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isSelected
            override fun toString() = "selected"
        }

        val CLICKABLE = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isClickable
            override fun toString() = "clickable"
        }

        val LONG_CLICKABLE = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isLongClickable
            override fun toString() = "longClickable"
        }

        val ENABLED = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isEnabled
            override fun toString() = "enabled"
        }

        val PASSWORD = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isPassword
            override fun toString() = "password"
        }

        val SCROLLABLE = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isScrollable
            override fun toString() = "scrollable"
        }

        val EDITABLE = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isEditable
            override fun toString() = "editable"
        }

        val CONTENT_INVALID = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isContentInvalid
            override fun toString() = "contentInvalid"
        }

        val CONTEXT_CLICKABLE = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isContextClickable
            override fun toString() = "checkable"
        }

        val MULTI_LINE = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isMultiLine
            override fun toString() = "multiLine"
        }

        val DISMISSABLE = object : BooleanSupplier {
            override fun get(node: UiObject) = node.isDismissable
            override fun toString() = "dismissable"
        }

        val HAS_CHILDREN = object : BooleanSupplier {
            override fun get(node: UiObject) = node.hasChildren()
            override fun toString() = "hasChildren"
        }

    }

}
