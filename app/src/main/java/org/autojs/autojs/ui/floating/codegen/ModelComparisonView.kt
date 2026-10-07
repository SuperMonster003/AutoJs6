package org.autojs.autojs.ui.floating.codegen

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import org.autojs.autojs.core.accessibility.codegen.store.ModelVersionInfo
import org.autojs.autojs.core.accessibility.codegen.store.VersionComparison
import org.autojs.autojs.ui.floating.layoutinspector.InspectorTheme
import org.autojs.autojs6.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The fixed E.5 comparison table, independent of its Activity or floating dialog owner.
 * Values wrap without truncation. At large font sizes the table scrolls horizontally instead of
 * reducing the requested text size or collapsing the installed and incoming columns together.
 */
class ModelComparisonView(
    context: Context,
    oldInfo: ModelVersionInfo,
    newInfo: ModelVersionInfo,
) : HorizontalScrollView(context) {
    private val colors = InspectorTheme(context)
    private val formatter = ModelComparisonFormatter(context)
    private val table = ComparisonTable(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    init {
        isFillViewport = true
        isHorizontalScrollBarEnabled = true
        clipToPadding = false
        addView(table, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        table.addView(tableRow().apply {
            addView(cell(context.getString(R.string.text_codegen_compare_field), heading = true), columnParams(FIELD_WEIGHT))
            addView(cell(context.getString(R.string.text_codegen_compare_installed), heading = true), columnParams(VALUE_WEIGHT))
            addView(cell(context.getString(R.string.text_codegen_compare_new), heading = true), columnParams(VALUE_WEIGHT))
        })
        VersionComparison(formatter, formatter.locale.toLanguageTag()).build(oldInfo, newInfo).forEach { row ->
            val label = formatter.fieldLabel(row.field)
            table.addView(tableRow().apply {
                if (row.differs) setBackgroundColor(colors.selectionBackground)
                addView(cell(
                    if (row.differs) context.getString(R.string.text_codegen_compare_different, label) else label,
                    emphasized = row.differs,
                ), columnParams(FIELD_WEIGHT))
                addView(cell(row.oldText, emphasized = row.differs), columnParams(VALUE_WEIGHT))
                addView(cell(row.newText, emphasized = row.differs), columnParams(VALUE_WEIGHT))
            })
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val minimumTableWidth = (300 * resources.displayMetrics.density * resources.configuration.fontScale.coerceAtLeast(1f)).roundToInt()
        table.targetWidth = max(available, minimumTableWidth)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private class ComparisonTable(context: Context) : LinearLayout(context) {
        var targetWidth = 0

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // HorizontalScrollView measures its child with UNSPECIFIED width even when the child's
            // LayoutParams specify a fixed width. Enforce the viewport/font-scaled width here so
            // weighted cells wrap at their actual column width instead of expanding to long names.
            super.onMeasure(MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY), heightMeasureSpec)
        }
    }

    private fun tableRow() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun cell(value: String, heading: Boolean = false, emphasized: Boolean = false) = AppCompatTextView(context).apply {
        text = value
        textSize = 13f
        gravity = Gravity.TOP or Gravity.START
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        setTextColor(if (emphasized) colors.selectionText else colors.text)
        setPaddingRelative(dp(6), dp(10), dp(6), dp(10))
        if (heading || emphasized) setTypeface(typeface, Typeface.BOLD)
        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        if (heading) ViewCompat.setAccessibilityHeading(this, true)
    }

    private fun columnParams(weight: Float) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val FIELD_WEIGHT = 0.28f
        const val VALUE_WEIGHT = 0.36f
    }
}
