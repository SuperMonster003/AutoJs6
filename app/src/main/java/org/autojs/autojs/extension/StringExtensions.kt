package org.autojs.autojs.extension

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

}
