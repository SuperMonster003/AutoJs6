package org.autojs.autojs.ui.explorer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import org.autojs.autojs6.R
import kotlin.math.roundToInt

class ExplorerPathBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val pathContainer = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
    }

    private var onParentPathClickListener: ((String) -> Unit)? = null

    init {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(
            pathContainer,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun setOnParentPathClickListener(listener: (String) -> Unit) {
        onParentPathClickListener = listener
    }

    internal fun setSegments(segments: List<ExplorerPathSegment>) {
        pathContainer.removeAllViews()
        segments.forEachIndexed { index, segment ->
            if (index > 0) pathContainer.addView(createSeparatorView())
            pathContainer.addView(createSegmentView(segment, index == segments.lastIndex))
        }
        pathContainer.post {
            scrollTo(pathContainer.width, 0)
        }
    }

    private fun createSegmentView(segment: ExplorerPathSegment, isCurrent: Boolean) =
        OpticallyCenteredTextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
            gravity = Gravity.CENTER
            includeFontPadding = false
            isSingleLine = true
            text = segment.label
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            setTextSize(TypedValue.COMPLEX_UNIT_SP, PATH_TEXT_SIZE_SP)
            setPadding(dp(SEGMENT_HORIZONTAL_PADDING_DP), 0, dp(SEGMENT_HORIZONTAL_PADDING_DP), 0)
            setTextColor(
                if (isCurrent) {
                    resolveThemeColor(android.R.attr.textColorPrimary)
                } else {
                    ContextCompat.getColor(context, R.color.explorer_category_operation_button)
                },
            )
            typeface = Typeface.create(
                if (isCurrent) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL,
            )
            if (!isCurrent) {
                isClickable = true
                isFocusable = true
                setSelectableItemBackground()
                setOnClickListener { onParentPathClickListener?.invoke(segment.path) }
            }
        }

    private fun createSeparatorView() = OpticallyCenteredTextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        )
        gravity = Gravity.CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        includeFontPadding = false
        text = PATH_SEPARATOR
        setTextSize(TypedValue.COMPLEX_UNIT_SP, SEPARATOR_TEXT_SIZE_SP)
        setTextColor(ContextCompat.getColor(context, R.color.explorer_category_operation_button))
        setPadding(dp(SEPARATOR_HORIZONTAL_PADDING_DP), 0, dp(SEPARATOR_HORIZONTAL_PADDING_DP), 0)
    }

    private fun resolveThemeColor(attribute: Int): Int {
        val typedValue = TypedValue()
        check(context.theme.resolveAttribute(attribute, typedValue, true)) {
            "Unable to resolve theme color attribute: $attribute"
        }
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun AppCompatTextView.setSelectableItemBackground() {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)) {
            setBackgroundResource(typedValue.resourceId)
        }
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    ).roundToInt()

    private class OpticallyCenteredTextView(context: Context) : AppCompatTextView(context) {

        private val textBounds = Rect()
        private var inkCenterFromBaseline: Float? = null

        override fun onDraw(canvas: Canvas) {
            val value = text?.toString().orEmpty()
            if (value.isEmpty()) {
                super.onDraw(canvas)
                return
            }

            val centerFromBaseline = inkCenterFromBaseline
                ?: measureInkCenterFromBaseline(value)?.also { inkCenterFromBaseline = it }
            if (centerFromBaseline == null) {
                super.onDraw(canvas)
                return
            }

            // Latin descenders can make equal glyph bounds still look top-heavy next to CJK text.
            // Align the alpha-weighted ink centers while leaving the full-height click target untouched.
            val contentCenterY = paddingTop + (height - paddingTop - paddingBottom) / 2F
            val textCenterY = baseline + centerFromBaseline
            val verticalOffset = (contentCenterY - textCenterY).roundToInt()
            if (verticalOffset == 0) {
                super.onDraw(canvas)
                return
            }

            val checkpoint = canvas.save()
            canvas.translate(0F, verticalOffset.toFloat())
            super.onDraw(canvas)
            canvas.restoreToCount(checkpoint)
        }

        private fun measureInkCenterFromBaseline(value: String): Float? {
            paint.getTextBounds(value, 0, value.length, textBounds)
            if (textBounds.isEmpty) return null

            val sampleWidth = textBounds.width() + INK_SAMPLE_PADDING_PX * 2
            val sampleHeight = textBounds.height() + INK_SAMPLE_PADDING_PX * 2
            val sampleBaseline = INK_SAMPLE_PADDING_PX - textBounds.top.toFloat()
            val bitmap = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
            return try {
                val samplePaint = Paint(paint).apply {
                    color = Color.WHITE
                    alpha = 255
                    clearShadowLayer()
                }
                Canvas(bitmap).drawText(
                    value,
                    INK_SAMPLE_PADDING_PX - textBounds.left.toFloat(),
                    sampleBaseline,
                    samplePaint,
                )

                val pixels = IntArray(sampleWidth * sampleHeight)
                bitmap.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
                var totalAlpha = 0L
                var alphaWeightedY = 0.0
                pixels.forEachIndexed { index, pixel ->
                    val pixelAlpha = Color.alpha(pixel)
                    if (pixelAlpha == 0) return@forEachIndexed
                    val pixelCenterY = index / sampleWidth + 0.5
                    totalAlpha += pixelAlpha
                    alphaWeightedY += pixelCenterY * pixelAlpha
                }
                if (totalAlpha == 0L) {
                    null
                } else {
                    (alphaWeightedY / totalAlpha - sampleBaseline).toFloat()
                }
            } finally {
                bitmap.recycle()
            }
        }

    }

    private companion object {
        const val PATH_SEPARATOR = "\u203A"
        const val PATH_TEXT_SIZE_SP = 14F
        const val SEPARATOR_TEXT_SIZE_SP = 21F
        const val SEGMENT_HORIZONTAL_PADDING_DP = 5F
        const val SEPARATOR_HORIZONTAL_PADDING_DP = 1F
        const val INK_SAMPLE_PADDING_PX = 2
    }
}
