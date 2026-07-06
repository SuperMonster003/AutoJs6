package org.autojs.autojs.ui.widget

import android.content.Context
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextUtils
import android.text.TextPaint
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView.BufferType
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.abs
import kotlin.math.ceil

class ThreeLineStateTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var appliedLineSpacingMultiplier = Float.NaN
    private var rawText: CharSequence? = null
    private var rawBufferType = BufferType.NORMAL
    private var displayText: CharSequence? = null
    private var applyingDisplayText = false

    init {
        rawText = text
        displayText = text
        minLines = FIXED_LINE_COUNT
        maxLines = FIXED_LINE_COUNT
        ellipsize = TextUtils.TruncateAt.MIDDLE
        gravity = gravity.withVerticalGravity(Gravity.CENTER_VERTICAL)
        applyLineSpacingMultiplier(DEFAULT_LINE_SPACING_MULTIPLIER)
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        if (!applyingDisplayText) {
            rawText = text
            rawBufferType = type ?: BufferType.NORMAL
            displayText = text
        }
        super.setText(text, type)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val fittedText = resolveDisplayText(measuredWidth)
        if (!TextUtils.equals(fittedText, displayText)) {
            applyDisplayText(fittedText)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        val fixedHeight = resolveSize(calculateFixedThreeLineHeight(), heightMeasureSpec)
        val targetMultiplier = when (layout?.lineCount) {
            TWO_LINE_COUNT -> calculateDistributedTwoLineSpacingMultiplier(fixedHeight)
            else -> DEFAULT_LINE_SPACING_MULTIPLIER
        }

        if (!targetMultiplier.closeTo(appliedLineSpacingMultiplier)) {
            applyLineSpacingMultiplier(targetMultiplier)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        setMeasuredDimension(measuredWidth, fixedHeight)
    }

    private fun calculateFixedThreeLineHeight(): Int {
        val sampleWidth = ceil(paint.measureText(HEIGHT_SAMPLE_LINE)).toInt().coerceAtLeast(1)
        return makeStaticLayout(HEIGHT_SAMPLE_TEXT, sampleWidth).height + compoundPaddingTop + compoundPaddingBottom
    }

    private fun resolveDisplayText(measuredWidth: Int): CharSequence? {
        val source = rawText ?: return null
        val availableWidth = measuredWidth - compoundPaddingLeft - compoundPaddingRight
        if (availableWidth <= 0 || fitsWithinFixedLinesByDefault(source, availableWidth)) {
            return source
        }

        return compactWrap(source, availableWidth) ?: middleEllipsizeToFit(source, availableWidth)
    }

    private fun middleEllipsizeToFit(source: CharSequence, availableWidth: Int): CharSequence {
        var low = 0
        var high = source.length - 1
        var best: CharSequence = ELLIPSIS

        while (low <= high) {
            val keepCount = (low + high) / 2
            val candidate = source.middleEllipsized(keepCount)
            val compactCandidate = compactWrap(candidate, availableWidth)
            if (compactCandidate != null) {
                best = compactCandidate
                low = keepCount + 1
            } else {
                high = keepCount - 1
            }
        }

        return best
    }

    private fun CharSequence.middleEllipsized(keepCount: Int): CharSequence {
        if (keepCount <= 0) {
            return ELLIPSIS
        }

        val leftCount = (keepCount + 1) / 2
        val rightCount = keepCount / 2
        val rightStart = length - rightCount
        if (leftCount >= rightStart) {
            return this
        }

        return TextUtils.concat(
            subSequence(0, leftCount),
            ELLIPSIS,
            subSequence(rightStart, length),
        )
    }

    private fun fitsWithinFixedLinesByDefault(text: CharSequence, availableWidth: Int): Boolean {
        return makeStaticLayout(text, availableWidth).lineCount <= FIXED_LINE_COUNT
    }

    private fun compactWrap(text: CharSequence, availableWidth: Int): CharSequence? {
        val builder = SpannableStringBuilder()
        var lineCount = 0
        var paragraphStart = 0

        while (paragraphStart <= text.length) {
            val paragraphEnd = text.indexOf('\n', paragraphStart).takeIf { it >= 0 } ?: text.length
            if (paragraphStart == paragraphEnd) {
                if (!builder.appendCompactLine("", ++lineCount)) {
                    return null
                }
            } else {
                var lineStart = paragraphStart
                while (lineStart < paragraphEnd) {
                    val lineEnd = compactLineEnd(text, lineStart, paragraphEnd, availableWidth)
                    if (!builder.appendCompactLine(text.subSequence(lineStart, lineEnd), ++lineCount)) {
                        return null
                    }
                    lineStart = lineEnd
                }
            }

            if (paragraphEnd == text.length) {
                break
            }
            paragraphStart = paragraphEnd + 1
        }

        return builder
    }

    private fun compactLineEnd(text: CharSequence, start: Int, end: Int, availableWidth: Int): Int {
        val measuredCount = paint.breakText(text, start, end, true, availableWidth.toFloat(), null)
        val count = measuredCount.coerceAtLeast(1).withoutTrailingHighSurrogate(text, start, end)
        return (start + count).coerceAtMost(end)
    }

    private fun Int.withoutTrailingHighSurrogate(text: CharSequence, start: Int, end: Int): Int {
        if (this <= 1 || start + this >= end) {
            return this
        }
        return if (Character.isHighSurrogate(text[start + this - 1]) && Character.isLowSurrogate(text[start + this])) {
            this - 1
        } else {
            this
        }
    }

    private fun SpannableStringBuilder.appendCompactLine(text: CharSequence, lineCount: Int): Boolean {
        if (lineCount > FIXED_LINE_COUNT) {
            return false
        }
        if (lineCount > 1) {
            append('\n')
        }
        append(text)
        return true
    }

    private fun makeStaticLayout(text: CharSequence, availableWidth: Int): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, TextPaint(paint), availableWidth)
            .setAlignment(layoutAlignment())
            .setIncludePad(includeFontPadding)
            .setLineSpacing(0f, DEFAULT_LINE_SPACING_MULTIPLIER)
            .setBreakStrategy(breakStrategy)
            .setHyphenationFrequency(hyphenationFrequency)
            .build()
    }

    private fun layoutAlignment(): Layout.Alignment {
        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection)
        return when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            Gravity.CENTER_HORIZONTAL -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
    }

    private fun applyDisplayText(text: CharSequence?) {
        if (TextUtils.equals(text, displayText)) {
            return
        }
        applyingDisplayText = true
        try {
            super.setText(text, rawBufferType)
            displayText = text
        } finally {
            applyingDisplayText = false
        }
    }

    private fun calculateDistributedTwoLineSpacingMultiplier(fixedHeight: Int): Float {
        val fontMetrics = paint.fontMetricsInt
        val lineHeight = fontMetrics.descent - fontMetrics.ascent
        val contentHeight = fixedHeight - compoundPaddingTop - compoundPaddingBottom
        if (lineHeight <= 0 || contentHeight <= 0) {
            return DEFAULT_LINE_SPACING_MULTIPLIER
        }
        val blankHeight = (contentHeight - TWO_LINE_COUNT * lineHeight) / (TWO_LINE_COUNT + 1f)
        return ((lineHeight + blankHeight) / lineHeight).coerceAtLeast(MIN_LINE_SPACING_MULTIPLIER)
    }

    private fun applyLineSpacingMultiplier(multiplier: Float) {
        if (multiplier.closeTo(appliedLineSpacingMultiplier)) {
            return
        }
        setLineSpacing(0f, multiplier)
        appliedLineSpacingMultiplier = multiplier
    }

    private fun Int.withVerticalGravity(verticalGravity: Int): Int {
        return (this and Gravity.VERTICAL_GRAVITY_MASK.inv()) or verticalGravity
    }

    private fun Float.closeTo(other: Float): Boolean {
        return abs(this - other) < FLOAT_EPSILON
    }

    companion object {

        private const val FIXED_LINE_COUNT = 3
        private const val TWO_LINE_COUNT = 2
        private const val DEFAULT_LINE_SPACING_MULTIPLIER = 1.2f
        private const val MIN_LINE_SPACING_MULTIPLIER = 1f
        private const val FLOAT_EPSILON = 0.001f
        private const val ELLIPSIS = "\u2026"
        private const val HEIGHT_SAMPLE_LINE = "A"
        private const val HEIGHT_SAMPLE_TEXT = "$HEIGHT_SAMPLE_LINE\n$HEIGHT_SAMPLE_LINE\n$HEIGHT_SAMPLE_LINE"

    }

}
