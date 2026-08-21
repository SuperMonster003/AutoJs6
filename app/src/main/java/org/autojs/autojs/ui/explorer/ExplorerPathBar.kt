package org.autojs.autojs.ui.explorer

import android.content.Context
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
        AppCompatTextView(context).apply {
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
                ContextCompat.getColor(
                    context,
                    if (isCurrent) R.color.text_color_primary else R.color.text_color_primary_alpha_40,
                ),
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

    private fun createSeparatorView() = AppCompatTextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        )
        gravity = Gravity.CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        includeFontPadding = false
        text = PATH_SEPARATOR
        setTextSize(TypedValue.COMPLEX_UNIT_SP, SEPARATOR_TEXT_SIZE_SP)
        setTextColor(ContextCompat.getColor(context, R.color.text_color_primary_alpha_30))
        setPadding(dp(SEPARATOR_HORIZONTAL_PADDING_DP), 0, dp(SEPARATOR_HORIZONTAL_PADDING_DP), 0)
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

    private companion object {
        const val PATH_SEPARATOR = "\u203A"
        const val PATH_TEXT_SIZE_SP = 14F
        const val SEPARATOR_TEXT_SIZE_SP = 21F
        const val SEGMENT_HORIZONTAL_PADDING_DP = 5F
        const val SEPARATOR_HORIZONTAL_PADDING_DP = 1F
    }
}
