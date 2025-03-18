package org.autojs.autojs.extension

import java.text.Normalizer

object StringExtensions {

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

}
