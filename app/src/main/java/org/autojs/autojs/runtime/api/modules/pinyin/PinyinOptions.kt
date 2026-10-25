package org.autojs.autojs.runtime.api.modules.pinyin

import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.util.RhinoUtils.coerceString

internal object PinyinOptions {

    fun parseMode(value: Any): Int? = MODE.parse(value)

    fun parseStyle(value: Any): Int? = STYLE.parse(value)

    /** The message [parseMode] rejects [value] with, null when it accepts it (the script module checks first, R2). */
    fun modeRejection(value: Any): String? = MODE.rejection(value)

    /** The message [parseStyle] rejects [value] with, null when it accepts it. */
    fun styleRejection(value: Any): String? = STYLE.rejection(value)

    /**
     * A `mode` / `style` option: an enum constant, a number equal to a constant's value, or a name of a constant in any
     * case; null for a nullish value. [rejection] decides without an exception, [parse] throws its message as an
     * IllegalArgumentException.
     *
     * zh-CN: mode / style 选项: 枚举常量, 等于常量值的数值, 或不区分大小写的常量名; nullish 为 null. [rejection] 不经异常判定, [parse] 以其消息抛出
     * IllegalArgumentException.
     */
    private class Choice<E : Enum<E>>(private val label: String, private val type: Class<E>, private val entries: List<E>, private val valueOf: (E) -> Int) {

        fun rejection(value: Any): String? {
            val unwrapped = value.jsUnwrapped()
            return when {
                unwrapped.isJsNullish() || type.isInstance(unwrapped) -> null
                unwrapped is Number -> unwrapped.toDouble().let { number -> "Invalid $label constant: $unwrapped".takeUnless { entries.any { valueOf(it).toDouble() == number } } }
                else -> "Invalid $label name: $unwrapped".takeUnless { named(unwrapped) != null }
            }
        }

        fun parse(value: Any): Int? {
            rejection(value)?.let { throw IllegalArgumentException(it) }
            val unwrapped = value.jsUnwrapped()
            return when {
                unwrapped.isJsNullish() -> null
                type.isInstance(unwrapped) -> valueOf(type.cast(unwrapped))
                unwrapped is Number -> unwrapped.toDouble().toInt()
                else -> valueOf(named(unwrapped)!!)
            }
        }

        private fun named(value: Any?): E? = coerceString(value, "").uppercase().let { name -> entries.find { it.name == name } }

    }

    private val MODE = Choice("mode", PinyinMode::class.java, PinyinMode.entries) { it.value }

    private val STYLE = Choice("style", PinyinStyle::class.java, PinyinStyle.entries) { it.value }

    enum class PinyinStyle(val value: Int) {
        NORMAL(0),
        TONE(1),
        TONE2(2),
        TO3NE(5),
        INITIALS(3),
        FIRST_LETTER(4),
    }

    @Suppress("SpellCheckingInspection")
    enum class PinyinMode(val value: Int) {
        NORMAL(0),
        SURNAME(1),
        PLACE_NAME(2),
        PLACENAME(2),
    }

    object DefaultOptions {
        val MODE = PinyinMode.NORMAL
        val STYLE = PinyinStyle.TONE
        const val SEGMENT: Boolean = false
        const val HETERONYM: Boolean = false
    }
}
