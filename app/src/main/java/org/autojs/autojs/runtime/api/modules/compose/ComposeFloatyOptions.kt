package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes

/** Legacy floaty geometry uses physical pixels, including Android's -1/-2 size sentinels. */
data class ComposeFloatyOptions(
    val raw: Boolean = false,
    val x: Int? = null,
    val y: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val touchable: Boolean = true,
    val focusable: Boolean = false,
) {
    companion object {
        private val names = setOf("raw", "x", "y", "width", "height", "touchable", "focusable", "theme")

        /** Theme is allowed here but validated and resolved by the session's common theme path. */
        fun parse(value: Any?): ComposeFloatyOptions {
            val fields = ComposeScriptValues().objectProperties(value, "compose.floaty")
            fields.keys.firstOrNull { it !in names }?.let { invalid(it, "Unknown option", ComposeUiErrorCodes.UNKNOWN_PROP) }
            fun boolean(name: String, default: Boolean): Boolean = if (name !in fields) default else
                ComposeScriptValues.unwrap(fields[name]) as? Boolean ?: invalid(name, "Expected a boolean")
            fun number(name: String, size: Boolean = false): Int? {
                if (name !in fields) return null
                val raw = ComposeScriptValues.unwrap(fields[name]) as? Number ?: invalid(name, "Expected an integer pixel value")
                val number = raw.toDouble()
                if (!number.isFinite() || number % 1.0 != 0.0 || number < Int.MIN_VALUE || number > Int.MAX_VALUE) {
                    invalid(name, "Pixel value is outside the integer range")
                }
                val result = number.toInt()
                if (size && result < -2) invalid(name, "Size must be nonnegative, MATCH_PARENT (-1), or WRAP_CONTENT (-2)")
                return result
            }
            return ComposeFloatyOptions(boolean("raw", false), number("x"), number("y"), number("width", true), number("height", true),
                boolean("touchable", true), boolean("focusable", false))
        }

        private fun invalid(property: String, reason: String, code: String = ComposeUiErrorCodes.INVALID_ARGUMENT): Nothing =
            throw ComposeUiContractException(code, "compose.floaty.$property: $reason", prop = property)
    }
}
