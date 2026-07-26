package org.autojs.autojs.core.ui.inflater

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.graphics.ColorUtils
import org.autojs.autojs.core.ui.inflater.ScriptResourceRepository.Companion.childElements
import org.w3c.dom.Element
import java.io.File
import java.util.LinkedHashSet
import kotlin.math.roundToInt

/**
 * Inflates an uncompiled `res/color` selector into a platform [ColorStateList].
 */
internal class AndroidColorStateListInflater(
    private val resolver: AndroidResourceResolver,
) {

    private val resolvingFiles = ThreadLocal<LinkedHashSet<String>>()

    fun inflate(context: Context, file: File): ColorStateList {
        val isRootCall = resolvingFiles.get() == null
        val stack = resolvingFiles.get() ?: LinkedHashSet<String>().also(resolvingFiles::set)
        val canonicalPath = file.canonicalPath
        check(stack.size < MAX_COLOR_DEPTH) {
            "Android color reference depth exceeds $MAX_COLOR_DEPTH"
        }
        check(stack.add(canonicalPath)) {
            "Circular Android color reference: ${
                (stack.toList() + canonicalPath).joinToString(" -> ") { File(it).nameWithoutExtension }
            }"
        }
        return try {
            val root = resolver.repository.parseDocument(file).documentElement
                ?: error("Color XML has no root element: $canonicalPath")
            when (root.tagName) {
                "selector" -> inflateSelector(context, root)
                "color" -> ColorStateList.valueOf(
                    resolver.parseColor(context, root.attr("color") ?: root.textContent.trim()),
                )
                else -> error("Unsupported Android color XML element: <${root.tagName}>")
            }
        } finally {
            stack.remove(canonicalPath)
            if (isRootCall) {
                resolvingFiles.remove()
            }
        }
    }

    private fun inflateSelector(context: Context, element: Element): ColorStateList {
        val states = mutableListOf<IntArray>()
        val colors = mutableListOf<Int>()
        element.childElements().filter { it.tagName == "item" }.forEach { item ->
            val stateSpec = buildList {
                val attributes = item.attributes
                for (index in 0 until attributes.length) {
                    val attribute = attributes.item(index)
                    val name = attribute.localName ?: attribute.nodeName.removePrefix("android:")
                    val stateId = STATE_ATTRIBUTES[name]
                    if (stateId == null) {
                        require(!name.startsWith("state_")) {
                            "Unsupported Android selector state: $name"
                        }
                        continue
                    }
                    val enabled = resolver.resolveScalar(context, attribute.nodeValue)
                        .toBooleanStrictOrNull()
                        ?: error("Invalid Android boolean value: ${attribute.nodeValue}")
                    add(if (enabled) stateId else -stateId)
                }
            }.toIntArray()
            val colorSource = item.attr("color")
                ?: error("<selector><item> requires android:color")
            var color = resolver.parseColor(context, colorSource)
            item.attr("alpha")?.let { alphaSource ->
                val resolved = resolver.resolveScalar(context, alphaSource)
                val alpha = if (resolved.endsWith("%")) {
                    resolved.removeSuffix("%").toFloat() / 100f
                } else {
                    resolved.toFloat()
                }
                color = ColorUtils.setAlphaComponent(
                    color,
                    (android.graphics.Color.alpha(color) * alpha.coerceIn(0f, 1f)).roundToInt(),
                )
            }
            states += stateSpec
            colors += color
        }
        require(colors.isNotEmpty()) { "<selector> requires at least one <item>" }
        return ColorStateList(states.toTypedArray(), colors.toIntArray())
    }

    private fun Element.attr(name: String): String? {
        return getAttributeNS(ANDROID_NAMESPACE, name).ifBlank {
            getAttribute("android:$name")
        }.ifBlank {
            getAttribute(name)
        }.ifBlank { null }
    }

    companion object {

        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        private const val MAX_COLOR_DEPTH = 32

        @Suppress("DEPRECATION")
        private val STATE_ATTRIBUTES = mapOf(
            "state_above_anchor" to android.R.attr.state_above_anchor,
            "state_accelerated" to android.R.attr.state_accelerated,
            "state_active" to android.R.attr.state_active,
            "state_activated" to android.R.attr.state_activated,
            "state_checkable" to android.R.attr.state_checkable,
            "state_checked" to android.R.attr.state_checked,
            "state_drag_can_accept" to android.R.attr.state_drag_can_accept,
            "state_drag_hovered" to android.R.attr.state_drag_hovered,
            "state_enabled" to android.R.attr.state_enabled,
            "state_empty" to android.R.attr.state_empty,
            "state_expanded" to android.R.attr.state_expanded,
            "state_first" to android.R.attr.state_first,
            "state_focused" to android.R.attr.state_focused,
            "state_hovered" to android.R.attr.state_hovered,
            "state_last" to android.R.attr.state_last,
            "state_long_pressable" to android.R.attr.state_long_pressable,
            "state_middle" to android.R.attr.state_middle,
            "state_multiline" to android.R.attr.state_multiline,
            "state_pressed" to android.R.attr.state_pressed,
            "state_selected" to android.R.attr.state_selected,
            "state_single" to android.R.attr.state_single,
            "state_window_focused" to android.R.attr.state_window_focused,
        )
    }
}
