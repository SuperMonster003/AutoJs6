package org.autojs.plugin.compose.api.model

import android.graphics.Bitmap
import org.autojs.plugin.compose.api.ComposeUiLimits
import kotlin.math.abs

enum class ValueKind { STRING, NUMBER, BOOLEAN, COLOR, DP, SP, ENUM, TEXT_STYLE, BITMAP, ICON_NAME, SHAPE, LIST, NULL }
enum class ShapeKind { RECTANGLE, ROUNDED, CIRCLE }
enum class ScopeKind { ANY, COLUMN_ROW, BOX }

sealed class UiValue(val kind: ValueKind) : ComposeParcelable() {
    internal open val depth = 1

    class Str(value: String) : UiValue(ValueKind.STRING) {
        val value = text(value)
        override fun fields() = listOf(value)
        companion object { @JvmField val CREATOR = creator<Str>() }
    }
    class Num(value: Double) : UiValue(ValueKind.NUMBER) {
        val value = finite(value)
        override fun fields() = listOf(value)
        companion object { @JvmField val CREATOR = creator<Num>() }
    }
    class Bool(val value: Boolean) : UiValue(ValueKind.BOOLEAN) {
        override fun fields() = listOf(value)
        companion object { @JvmField val CREATOR = creator<Bool>() }
    }
    class Color(val argb: Int) : UiValue(ValueKind.COLOR) {
        override fun fields() = listOf(argb)
        companion object { @JvmField val CREATOR = creator<Color>() }
    }
    class Dp(value: Double) : UiValue(ValueKind.DP) {
        val value = dimension(value)
        override fun fields() = listOf(value)
        companion object { @JvmField val CREATOR = creator<Dp>() }
    }
    class Sp(value: Double) : UiValue(ValueKind.SP) {
        val value = dimension(value)
        override fun fields() = listOf(value)
        companion object { @JvmField val CREATOR = creator<Sp>() }
    }
    class Enum(value: String) : UiValue(ValueKind.ENUM) {
        val value = name(value)
        override fun fields() = listOf(value)
        companion object { @JvmField val CREATOR = creator<Enum>() }
    }
    class IconName(value: String) : UiValue(ValueKind.ICON_NAME) {
        val value = name(value)
        override fun fields() = listOf(value)
        companion object { @JvmField val CREATOR = creator<IconName>() }
    }
    class Shape(val shape: ShapeKind, radius: Double = 0.0) : UiValue(ValueKind.SHAPE) {
        val radius = dimension(radius)
        init { if (radius < 0 || (shape != ShapeKind.ROUNDED && radius != 0.0)) invalid("Invalid shape radius") }
        override fun fields() = listOf(shape, radius)
        companion object { @JvmField val CREATOR = creator<Shape>() }
    }
    class TextStyle(
        fontFamily: String? = null,
        fontSize: Double? = null,
        val fontWeight: Int? = null,
        val italic: Boolean? = null,
        lineHeight: Double? = null,
        letterSpacing: Double? = null,
    ) : UiValue(ValueKind.TEXT_STYLE) {
        val fontFamily = fontFamily?.let(::name)
        val fontSize = fontSize?.let(::dimension)
        val lineHeight = lineHeight?.let(::dimension)
        val letterSpacing = letterSpacing?.let(::dimension)
        init {
            if (fontWeight != null && fontWeight !in 1..1000) invalid("Invalid font weight")
            if (fontSize != null && fontSize <= 0 || lineHeight != null && lineHeight <= 0) invalid("Invalid text size")
        }
        override fun fields() = listOf(fontFamily, fontSize, fontWeight, italic, lineHeight, letterSpacing)
        companion object { @JvmField val CREATOR = creator<TextStyle>() }
    }
    /** The caller retains ownership. get() returns null after owner collection or recycle. */
    class BitmapRef private constructor(internal val handle: Long) : UiValue(ValueKind.BITMAP) {
        constructor(bitmap: Bitmap) : this(BitmapHandles.register(bitmap))
        fun get(): Bitmap? = BitmapHandles.get(handle)
        override fun fields() = listOf(handle)
        companion object {
            @JvmField val CREATOR = creator<BitmapRef>()
            internal fun restored(handle: Long) = BitmapRef(handle)
        }
    }
    class ListOf(values: List<UiValue>) : UiValue(ValueKind.LIST) {
        val values = frozenList(values, ComposeUiLimits.MAX_VALUE_ITEMS)
        override val depth = 1 + (this.values.maxOfOrNull { it.depth } ?: 0)
        init { limit(depth <= ComposeUiLimits.MAX_VALUE_DEPTH, "Value nesting exceeds contract limit") }
        override fun fields() = listOf(values)
        companion object { @JvmField val CREATOR = creator<ListOf>() }
    }
    object Null : UiValue(ValueKind.NULL) {
        override fun fields() = emptyList<Any?>()
        @JvmField val CREATOR = creator<Null>()
    }

    companion object {
        @JvmField val CREATOR = creator<UiValue>()
        internal fun dimension(value: Double): Double = finite(value).also {
            if (abs(it) > Float.MAX_VALUE) invalid("Dimension cannot be represented as a finite float")
        }
    }
}
