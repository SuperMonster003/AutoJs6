package org.autojs.autojs.extension

import androidx.core.net.toUri
import java.text.Normalizer

object StringExtensions {

    val String.estimateVisualWidth: Int
        get() {
            var width = 0
            var i = 0
            while (i < length) {
                // 取 code point (可能是两位 surrogates 拼成一个 code point)
                val cp = codePointAt(i)
                // 移动下标, 跳过组合过的 surrogate
                i += Character.charCount(cp)

                // 简易判断: 东亚全宽/Emoji 等
                width += if (isLikelyFullwidth(cp)) 2 else 1
            }
            return width
        }

    fun String.toDoubleOrNaN() = this.toDoubleOrNull() ?: Double.NaN

    fun String.padStart(length: Int, padStr: String) = when {
        this.length >= length || padStr.isEmpty() -> this
        else -> getPadding(length, padStr) + this
    }

    fun String.padEnd(length: Int, padStr: String) = when {
        this.length >= length || padStr.isEmpty() -> this
        else -> this + getPadding(length, padStr)
    }

    private fun String.getPadding(length: Int, padStr: String): String {
        val repeatTimes = (length - this.length) / padStr.length
        val remainderStr = padStr.take((length - this.length) % padStr.length)
        return padStr.repeat(repeatTimes) + remainderStr
    }

    fun CharSequence.looseMatches(reference: CharSequence): Boolean {
        return this.toString().normalizeForMatch() == reference.toString().normalizeForMatch()
    }

    private fun String.normalizeForMatch(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .trim()
            .replace(Regex("\\p{M}"), "") // 移除组合符号, 如上标符号
            .replace(Regex("[^\\p{L}\\p{N}@.|]+"), "") // 只保留 [字母/数字/特定符号]
            .lowercase()
    }

    private fun isLikelyFullwidth(codePoint: Int) = when (codePoint) {
        // CJK 中日韩统一表意文字
        in 0x4E00..0x9FFF -> true
        // 常见的 Emoji 起始, 又或者判断 Character.getType(cp)
        in 0x1F300..0x1FAFF -> true
        // East Asian Fullwidth, Wide 等范围, 可参考 EastAsianWidth.txt
        else -> false
    }

    @JvmStatic
    fun String.isUri(): Boolean {
        if (isBlank()) return false
        val uri = runCatching { this.toUri() }.getOrNull() ?: return false
        uri.scheme?.lowercase() ?: return false
        return true
    }

}
