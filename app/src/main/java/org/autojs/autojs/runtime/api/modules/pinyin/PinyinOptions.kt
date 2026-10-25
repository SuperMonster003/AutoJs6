package org.autojs.autojs.runtime.api.modules.pinyin

import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.util.RhinoUtils.coerceString

internal object PinyinOptions {

    fun parseMode(value: Any): Int? {
        val unwrapped = value.jsUnwrapped()
        return when {
            unwrapped.isJsNullish() -> null
            unwrapped is PinyinMode -> unwrapped.value
            unwrapped is Number -> {
                val number = unwrapped.toDouble()
                require(PinyinMode.entries.any { it.value.toDouble() == number }) {
                    "Invalid mode constant: $unwrapped"
                }
                number.toInt()
            }
            else -> coerceString(unwrapped, "").uppercase().let { name ->
                PinyinMode.entries.find { it.name == name }?.value
                    ?: throw IllegalArgumentException("Invalid mode name: $unwrapped")
            }
        }
    }

    fun parseStyle(value: Any): Int? {
        val unwrapped = value.jsUnwrapped()
        return when {
            unwrapped.isJsNullish() -> null
            unwrapped is PinyinStyle -> unwrapped.value
            unwrapped is Number -> {
                val number = unwrapped.toDouble()
                require(PinyinStyle.entries.any { it.value.toDouble() == number }) {
                    "Invalid style constant: $unwrapped"
                }
                number.toInt()
            }
            else -> coerceString(unwrapped, "").uppercase().let { name ->
                PinyinStyle.entries.find { it.name == name }?.value
                    ?: throw IllegalArgumentException("Invalid style name: $unwrapped")
            }
        }
    }

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
