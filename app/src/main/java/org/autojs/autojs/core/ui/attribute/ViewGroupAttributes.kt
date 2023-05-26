package org.autojs.autojs.core.ui.attribute

import android.animation.LayoutTransition
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.ValueMapper

open class ViewGroupAttributes(resourceParser: ResourceParser, view: View) : ViewAttributes(resourceParser, view) {

    override val view = super.view as ViewGroup

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("addStatesFromChildren") { view.setAddStatesFromChildren(it.toBoolean()) }
        registerAttr("animateLayoutChanges") { view.layoutTransition = if (it == "true") LayoutTransition() else null }
        registerAttr("clipChildren") { view.clipChildren = it.toBoolean() }
        registerAttr("clipToPadding") { view.clipToPadding = it.toBoolean() }
        registerAttr("descendantFocusability") { view.descendantFocusability = DESCENDANT_FOCUSABILITY[it] }
        registerAttr("layoutMode") { view.layoutMode = LAYOUT_MODES[it] }
        registerAttrs(arrayOf("isMotionEventSplittingEnabled", "motionEventSplittingEnabled", "enableMotionEventSplitting", "splitMotionEvents")) { view.isMotionEventSplittingEnabled = it.toBoolean() }

        @Suppress("DEPRECATION")
        registerAttr("persistentDrawingCache") { view.persistentDrawingCache = PERSISTENT_DRAWING_CACHE[it] }

        registerAttrUnsupported(
            arrayOf(
                "layoutAnimation",
            )
        )
    }

    companion object {

        @Suppress("DEPRECATION")
        private val PERSISTENT_DRAWING_CACHE = ValueMapper<Int>("persistentDrawingCache")
            .map("all", ViewGroup.PERSISTENT_ALL_CACHES)
            .map("animation", ViewGroup.PERSISTENT_ANIMATION_CACHE)
            .map("none", ViewGroup.PERSISTENT_NO_CACHE)
            .map("scrolling", ViewGroup.PERSISTENT_SCROLLING_CACHE)

        private val LAYOUT_MODES = ValueMapper<Int>("layoutMode")
            .map("clipBounds", ViewGroup.LAYOUT_MODE_CLIP_BOUNDS)
            .map("opticalBounds", ViewGroup.LAYOUT_MODE_OPTICAL_BOUNDS)

        private val DESCENDANT_FOCUSABILITY = ValueMapper<Int>("descendantFocusability")
            .map("afterDescendants", ViewGroup.FOCUS_AFTER_DESCENDANTS)
            .map("beforeDescendants", ViewGroup.FOCUS_BEFORE_DESCENDANTS)
            .map("blocksDescendants", ViewGroup.FOCUS_BLOCK_DESCENDANTS)

    }

}