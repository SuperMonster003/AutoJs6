package org.autojs.autojs.core.automator.filter

import java.util.Locale
import kotlin.math.pow

/**
 * Number rendering for the string form of selectors (`boundsLeft(0.5)`, `screenCenterX(true, 0.016)`):
 * up to three decimals, trailing zeros dropped, always a dot. Locale-independent on purpose, since
 * the string form is meant to be pasted back into a script.
 * zh-CN: 选择器字符串形式中的数字渲染 (`boundsLeft(0.5)`, `screenCenterX(true, 0.016)`): 至多三位小数, 去尾零, 一律用点号.
 * 有意不依赖区域设置, 因为字符串形式是要能粘贴回脚本的.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal object FilterFormat {

    private const val MAX_FRACTION_DIGITS = 3

    private val trailingZeros = "\\.?0+$".toRegex()

    fun number(d: Double): String {
        var digits = 0
        while (digits < MAX_FRACTION_DIGITS && d * 10.0.pow(digits) % 1.0 != 0.0) {
            digits += 1
        }
        val s = String.format(Locale.ROOT, "%.${digits}f", d)
        return if (s.contains('.')) s.replace(trailingZeros, "") else s
    }

}
