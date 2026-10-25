package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
class BooleanFilter(private val mBooleanSupplier: BooleanSupplier, private val mExceptedValue: Boolean? = null) : Filter {

    fun interface BooleanSupplier {
        operator fun get(node: UiNode<*>): Boolean
    }

    override fun filter(node: UiNode<*>) = mBooleanSupplier[node] == (mExceptedValue ?: DEFAULT)

    override fun toString() = "$mBooleanSupplier(${mExceptedValue ?: ""})"

    override val cost get() = Filter.COST_PROPERTY

    companion object {

        const val DEFAULT = true

        val CHECKABLE = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.checkable()
            override fun toString() = "checkable"
        }

        val CHECKED = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.checked()
            override fun toString() = "checked"
        }

        val FOCUSABLE = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.focusable()
            override fun toString() = "focusable"
        }

        val FOCUSED = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.focused()
            override fun toString() = "focused"
        }

        val VISIBLE_TO_USER = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.visibleToUser()
            override fun toString() = "visibleToUser"
        }

        val ACCESSIBILITY_FOCUSED = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.accessibilityFocused()
            override fun toString() = "accessibilityFocused"
        }

        val SELECTED = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.selected()
            override fun toString() = "selected"
        }

        val CLICKABLE = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.clickable()
            override fun toString() = "clickable"
        }

        val LONG_CLICKABLE = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.longClickable()
            override fun toString() = "longClickable"
        }

        val ENABLED = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.enabled()
            override fun toString() = "enabled"
        }

        val PASSWORD = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.password()
            override fun toString() = "password"
        }

        val SCROLLABLE = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.scrollable()
            override fun toString() = "scrollable"
        }

        val EDITABLE = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.editable()
            override fun toString() = "editable"
        }

        val CONTENT_INVALID = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.contentInvalid()
            override fun toString() = "contentInvalid"
        }

        val CONTEXT_CLICKABLE = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.contextClickable()
            override fun toString() = "contextClickable"
        }

        val MULTI_LINE = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.multiLine()
            override fun toString() = "multiLine"
        }

        val DISMISSABLE = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.dismissable()
            override fun toString() = "dismissable"
        }

        val HAS_CHILDREN = object : BooleanSupplier {
            override fun get(node: UiNode<*>) = node.hasChildren()
            override fun toString() = "hasChildren"
        }

    }

}
