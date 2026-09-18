package com.mcal.apksigner.utils

object Base64 {
    @JvmStatic
    fun encode(raw: ByteArray): String {
        val encoded = StringBuffer()
        var i = 0
        while (i < raw.size) {
            encoded.append(encodeBlock(raw, i))
            i += 3
        }
        return encoded.toString()
    }

    @JvmStatic
    fun decode(base64: String): ByteArray {
        var pad = 0
        var i = base64.length - 1
        while (base64[i] == '=') {
            pad++
            i--
        }
        val length = base64.length * 6 / 8 - pad
        val raw = ByteArray(length)
        var rawIndex = 0
        i = 0
        while (i < base64.length) {
            val block = ((getValue(base64[i]) shl 18)
                    + (getValue(base64[i + 1]) shl 12)
                    + (getValue(base64[i + 2]) shl 6)
                    + getValue(base64[i + 3]))
            var j = 0
            while (j < 3 && rawIndex + j < raw.size) {
                raw[rawIndex + j] = (block shr 8 * (2 - j) and 0xff).toByte()
                j++
            }
            rawIndex += 3
            i += 4
        }
        return raw
    }

    private fun encodeBlock(raw: ByteArray, offset: Int): CharArray {
        var block = 0
        val slack = raw.size - offset - 1
        val end = if (slack >= 2) {
            2
        } else {
            slack
        }
        for (i in 0..end) {
            val b = raw[offset + i]
            val neuter = if (b < 0) {
                b + 256
            } else {
                b.toInt()
            }
            block += neuter shl 8 * (2 - i)
        }
        val base64 = CharArray(4)
        for (i in 0..3) {
            val sixBit = block ushr 6 * (3 - i) and 0x3f
            base64[i] = getChar(sixBit)
        }
        if (slack < 1) {
            base64[2] = '='
        }
        if (slack < 2) {
            base64[3] = '='
        }
        return base64
    }

    private fun getChar(sixBit: Int): Char {
        if (sixBit in 0..25) {
            return ('A'.code + sixBit).toChar()
        }
        if (sixBit in 26..51) {
            return ('a'.code + (sixBit - 26)).toChar()
        }
        if (sixBit in 52..61) {
            return ('0'.code + (sixBit - 52)).toChar()
        }
        if (sixBit == 62) {
            return '+'
        }
        return if (sixBit == 63) {
            '/'
        } else {
            '?'
        }
    }

    private fun getValue(c: Char): Int {
        if (c in 'A'..'Z') {
            return c.code - 'A'.code
        }
        if (c in 'a'..'z') {
            return c.code - 'a'.code + 26
        }
        if (c in '0'..'9') {
            return c.code - '0'.code + 52
        }
        if (c == '+') {
            return 62
        }
        if (c == '/') {
            return 63
        }
        return if (c == '=') {
            0
        } else {
            -1
        }
    }
}
