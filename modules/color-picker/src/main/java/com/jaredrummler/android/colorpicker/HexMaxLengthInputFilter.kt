package com.jaredrummler.android.colorpicker

import android.text.InputFilter
import android.text.Spanned

class HexMaxLengthInputFilter(private val maxLength: Int) : InputFilter {

    override fun filter(
        source: CharSequence, start: Int, end: Int,
        dest: Spanned, dstart: Int, dend: Int,
    ): CharSequence? {
        val result = buildString {
            append(dest.subSequence(0, dstart))
            append(source.subSequence(start, end))
            append(dest.subSequence(dend, dest.length))
        }.trimStart('#').replace("\\s+".toRegex(), "")

        if (result.length <= maxLength) return null

        val keep = maxLength - (dest.length - (dend - dstart))
        if (keep <= 0) return ""
        return source.subSequence(start, start + keep)
    }

}