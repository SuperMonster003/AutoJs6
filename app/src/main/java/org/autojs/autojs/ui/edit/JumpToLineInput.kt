package org.autojs.autojs.ui.edit

/** Returns the maximum line as replacement text only when [input] exceeds it. */
internal fun jumpToLineUpperBoundCorrection(
    input: CharSequence,
    maximumLine: Int,
): String? {
    require(maximumLine >= 1) { "Maximum line must be positive" }
    val candidate = input.toString()
    if (candidate.isEmpty() || candidate.any { it !in '0'..'9' }) return null
    val significantDigits = candidate.trimStart('0').ifEmpty { "0" }
    val maximum = maximumLine.toString()
    val exceedsMaximum = significantDigits.length > maximum.length ||
        significantDigits.length == maximum.length && significantDigits > maximum
    return maximum.takeIf { exceedsMaximum }
}
