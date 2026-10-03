package org.autojs.autojs.ui.floating.codegen

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.codegen.CodeGenCandidate
import org.autojs.autojs.core.accessibility.codegen.CodeGenResult
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.inspector.InspectorNode
import org.autojs.autojs.ui.floating.layoutinspector.InspectorDialogBuilder
import org.autojs.autojs.ui.floating.layoutinspector.InspectorTheme
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.ThreadUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import kotlin.math.roundToInt

/**
 * Verified generated code, its factual explanation and up to nine alternative plans. The same
 * dialog survives a layout preview; the owner must [dispose] it when the inspector closes.
 *
 * zh-CN: 展示通过验证的代码, 事实依据与至多九个备选计划. 布局预览只隐藏当前对话框;
 * 分析窗口关闭时由持有者调用 [dispose], 防止预览结束后重新弹出窗口.
 */
class CodeGenResultDialog(
    context: Context,
    result: CodeGenResult<InspectorNode>,
    private val onPreview: ((List<NodeInfo>, () -> Unit) -> Unit)? = null,
) : InspectorDialogBuilder(context) {

    private val colors = InspectorTheme(this.context)
    private val candidates = result.candidates.take(MAX_CANDIDATES)
    private var selectedIndex = 0
    private var dialog: MaterialDialog? = null
    private var disposed = false
    private var previewToken = 0L
    private var previewPending = false

    private val codeView = textView(14f).apply {
        typeface = Typeface.MONOSPACE
        setTextIsSelectable(true)
        highlightColor = colors.matchBackground
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setBackgroundColor(colors.selectionBackground)
        setTextColor(colors.selectionText)
    }
    private val explanationView = textView(14f)
    private val countView = textView(14f).apply { setTypeface(typeface, Typeface.BOLD) }
    private val preferredView = planChoice().apply {
        setText(R.string.text_codegen_preferred)
        setOnClickListener { select(0) }
    }
    private val alternativesAdapter = AlternativesAdapter()
    private val scroll = ScrollView(this.context).apply {
        isFillViewport = false
        clipToPadding = false
        setPadding(dp(20), 0, dp(20), dp(8))
        addView(LinearLayout(this@CodeGenResultDialog.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(codeView, rowParams(bottom = 12))
            addView(explanationView, rowParams(bottom = 10))
            addView(countView, rowParams(bottom = 12))
            if (candidates.size > 1) {
                addView(preferredView, rowParams())
                addView(textView(14f).apply {
                    setText(R.string.text_codegen_alternatives)
                    setTypeface(typeface, Typeface.BOLD)
                }, rowParams(top = 12, bottom = 4))
                addView(RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = alternativesAdapter
                    isNestedScrollingEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    itemAnimator = null
                }, rowParams())
            }
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private val selectedCandidate: CodeGenCandidate<InspectorNode> get() = candidates[selectedIndex]

    init {
        require(candidates.isNotEmpty()) { "At least one verified candidate is required" }
        title(R.string.text_codegen_results_title)
        customView(scroll, false)
        autoDismiss(false)
        positiveText(R.string.dialog_button_copy)
        negativeText(R.string.text_close)
        onPositive { _, _ ->
            ClipboardUtils.setClip(this.context, selectedCandidate.code)
            ViewUtils.showToast(this.context, R.string.text_already_copied_to_clip)
        }
        onNegative { _, _ -> dispose() }
        if (onPreview != null) {
            neutralText(R.string.text_codegen_show_in_layout)
            onNeutral { current, _ -> preview(current) }
        }
        dismissListener {
            disposed = true
            previewPending = false
            previewToken++
        }
        showCandidate()
    }

    override fun build(): MaterialDialog {
        check(!disposed) { "Result dialog has been disposed" }
        return dialog ?: super.build().also { dialog = it }
    }

    /** Cancel pending preview restoration and release the hidden or visible dialog. */
    fun dispose() {
        disposed = true
        previewPending = false
        previewToken++
        dialog?.dismiss()
        dialog = null
    }

    private fun select(index: Int) {
        if (disposed || index !in candidates.indices || selectedIndex == index) return
        val previous = selectedIndex
        selectedIndex = index
        showCandidate()
        if (previous > 0) alternativesAdapter.notifyItemChanged(previous - 1)
        if (index > 0) alternativesAdapter.notifyItemChanged(index - 1)
        scroll.post { scroll.smoothScrollTo(0, 0) }
    }

    private fun showCandidate() {
        val candidate = selectedCandidate
        codeView.text = candidate.code
        explanationView.text = CodeGenText.explanation(context, candidate.explanation)
        countView.text = CodeGenText.matchCount(
            context, candidate.matchedCount, candidate.plan.cardinality == LocatePlan.Cardinality.ALL,
        )
        preferredView.isChecked = selectedIndex == 0
        preferredView.setBackgroundColor(if (selectedIndex == 0) colors.selectionBackground else colors.background)
        preferredView.setTextColor(if (selectedIndex == 0) colors.selectionText else colors.text)
    }

    private fun preview(current: MaterialDialog) {
        val handler = onPreview ?: return
        if (disposed || previewPending) return
        val token = ++previewToken
        previewPending = true
        current.hide()
        val restore = {
            ThreadUtils.runOnMain {
                if (!disposed && previewPending && token == previewToken) {
                    previewPending = false
                    previewToken++
                    current.showAdaptive()
                }
            }
        }
        try {
            handler(selectedCandidate.resolved.map { it.info }, restore)
        } catch (failure: RuntimeException) {
            // Keep a failed preview from stranding an invisible dialog.
            // zh-CN: 预览失败时恢复对话框, 避免留下无法返回的隐藏窗口.
            restore()
            Log.w("CodeGenResultDialog", "Could not show the generated plan in the inspector", failure)
        }
    }

    private fun textView(size: Float): AppCompatTextView = AppCompatTextView(context).apply {
        textSize = size
        setTextColor(colors.text)
    }

    private fun planChoice(): AppCompatRadioButton = AppCompatRadioButton(context).apply {
        textSize = 14f
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(4), dp(6), dp(8), dp(6))
        buttonTintList = colors.controls
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun rowParams(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private inner class AlternativesAdapter : RecyclerView.Adapter<AlternativeHolder>() {
        override fun getItemCount(): Int = (candidates.size - 1).coerceAtLeast(0)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlternativeHolder {
            return AlternativeHolder(planChoice().apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }

        override fun onBindViewHolder(holder: AlternativeHolder, position: Int) {
            val index = position + 1
            val selected = selectedIndex == index
            holder.choice.apply {
                text = CodeGenText.candidateSummary(context, candidates[index].plan)
                isChecked = selected
                setBackgroundColor(if (selected) colors.selectionBackground else colors.background)
                setTextColor(if (selected) colors.selectionText else colors.text)
                setOnClickListener { select(index) }
            }
        }
    }

    private class AlternativeHolder(val choice: AppCompatRadioButton) : RecyclerView.ViewHolder(choice)

    companion object {
        private const val MAX_CANDIDATES = 10
    }
}
