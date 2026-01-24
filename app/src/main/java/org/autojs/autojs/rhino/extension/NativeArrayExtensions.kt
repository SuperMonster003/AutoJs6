package org.autojs.autojs.rhino.extension

import org.mozilla.javascript.NativeArray

object NativeArrayExtensions {

    @JvmStatic
    fun NativeArray.jsBytesToByteArray(): ByteArray {
        val size = this.size
        val bytes = ByteArray(size)
        for (i in 0 until size) {
            val number = this[i]
            if (number is Number) {
                bytes[i] = number.toByte()
            }
        }
        return bytes
    }

    @JvmStatic
    fun NativeArray.jsBytesToString(): String {
        return String(this.jsBytesToByteArray())
    }

}