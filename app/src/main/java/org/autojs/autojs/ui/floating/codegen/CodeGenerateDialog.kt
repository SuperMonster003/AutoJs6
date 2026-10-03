package org.autojs.autojs.ui.floating.codegen

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.bignerdranch.expandablerecyclerview.ChildViewHolder
import com.bignerdranch.expandablerecyclerview.ExpandableRecyclerAdapter
import com.bignerdranch.expandablerecyclerview.ParentViewHolder
import com.bignerdranch.expandablerecyclerview.model.Parent
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenEngine
import org.autojs.autojs.core.accessibility.codegen.CodeGenFailure
import org.autojs.autojs.core.accessibility.codegen.CodeGenOutcome
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Attr
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Receiver
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Style
import org.autojs.autojs.core.accessibility.codegen.CodeGenResult
import org.autojs.autojs.core.accessibility.codegen.TargetSet
import org.autojs.autojs.core.accessibility.codegen.TargetSets
import org.autojs.autojs.core.accessibility.inspector.InspectorNode
import org.autojs.autojs.ui.floating.codegen.CodeGenOptionsState.ActionChoice
import org.autojs.autojs.ui.floating.codegen.CodeGenOptionsState.Query
import org.autojs.autojs.ui.floating.layoutinspector.InspectorDialogBuilder
import org.autojs.autojs.ui.floating.layoutinspector.InspectorTheme
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogListViewBinding
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.roundToInt

/**
 * Options for the verified generator. All option groups keep exactly one radio choice (except
 * independent attribute checkboxes); the detached capture is only searched on the owned worker.
 * zh-CN: 已验证代码生成器的选项. 除独立属性复选框外, 每组始终保留一个单选项; 捕获树只在持有的工作线程上搜索.
 */
class CodeGenerateDialog(
    context: Context,
    root: NodeInfo,
    target: NodeInfo,
    private val onGenerated: ((CodeGenResult<InspectorNode>) -> Unit)? = null,
) : InspectorDialogBuilder(context) {
    private val rootNode = InspectorNode(root)
    private val targetNode = InspectorNode(target)
    private val colors = InspectorTheme(this.context)
    private val texts = CodeGenText(this.context)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "layout-codegen").apply { isDaemon = true } }
    private var state = CodeGenOptionsState()
    private var targetSets: List<TargetSet<InspectorNode>> = emptyList()
    private var collectionsLoading = true
    private var collectionTask: Future<*>? = null
    private var generationTask: Future<*>? = null
    private var cancellation: CancellationSignal? = null
    private var generationId = 0L
    private var busy = false
    private var disposed = false
    private var retiringOptions = false
    private var optionsDialog: MaterialDialog? = null
    private var progressDialog: MaterialDialog? = null
    private var simpleResultDialog: MaterialDialog? = null
    private var progressRunnable: Runnable? = null
    private val binding = DialogListViewBinding.inflate(LayoutInflater.from(this.context))
    private val errorView = TextView(this.context).apply {
        tag = "codegen_error"
        textSize = 14f
        setTextColor(colors.accent)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = View.GONE
    }
    private val groups = createGroups()
    private val adapter = OptionsAdapter(groups)

    init {
        title(R.string.text_generate_code)
        negativeText(R.string.text_cancel)
        positiveText(R.string.text_generate)
        autoDismiss(false)
        onPositive { _, _ -> generate() }
        onNegative { _, _ -> dispose() }
        cancelListener { dispose() }
        dismissListener { if (!retiringOptions) dispose() }
        showListener {
            optionsDialog?.window?.apply {
                clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            }
            loadCollections()
        }
        binding.root.addView(errorView, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // MaterialDialog measures custom content with AT_MOST, so keep a natural list height on
        // the first pass. Weight lets the list shrink when an error or the IME needs more room.
        // zh-CN: 自定义内容按 AT_MOST 测量, 首次保留列表自然高度; 权重使其能为错误提示或输入法收缩.
        binding.list.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        binding.list.layoutManager = LinearLayoutManager(this.context)
        binding.list.adapter = adapter
        binding.list.itemAnimator = null
        customView(binding.root, false)
    }

    override fun build(): MaterialDialog {
        optionsDialog?.let { return it }
        check(!disposed) { "The code-generation dialog has been disposed" }
        return super.build().also { optionsDialog = it }
    }

    /** Called by the inspector host on close, including while generation is in flight.
     * zh-CN: 布局分析窗口关闭时调用, 包括正在生成的情况. */
    fun dispose() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { dispose() }
            return
        }
        if (!disposed) {
            disposed = true
            cancelGeneration(restoreControls = false)
            collectionTask?.cancel(true)
            executor.shutdownNow()
            handler.removeCallbacksAndMessages(null)
        }
        val options = optionsDialog
        optionsDialog = null
        options?.dismiss()
        val result = simpleResultDialog
        simpleResultDialog = null
        result?.dismiss()
    }

    private fun loadCollections() {
        if (disposed || collectionTask != null) return
        collectionTask = executor.submit {
            val suggestions = try {
                TargetSets.suggest(rootNode, targetNode)
            } catch (e: Exception) {
                Log.w(TAG, "Could not prepare target collections", e)
                emptyList()
            }
            handler.post {
                if (disposed) return@post
                targetSets = suggestions
                collectionsLoading = false
                adapter.refreshGroup(1)
            }
        }
    }

    private fun generate() {
        if (disposed || busy) return
        when (val converted = state.toRequest(rootNode, targetNode, targetSets)) {
            is CodeGenOptionsState.Invalid -> {
                val messages = converted.errors.map { error ->
                    context.getString(when (error) {
                        CodeGenOptionsState.Error.COLLECTION_UNAVAILABLE -> R.string.text_codegen_collection_unavailable
                        CodeGenOptionsState.Error.COLLECTION_SELECTION_INVALID -> R.string.text_codegen_collection_error
                        CodeGenOptionsState.Error.TIMEOUT_INVALID -> R.string.text_codegen_timeout_error
                        CodeGenOptionsState.Error.TARGET_OUTSIDE_ROOT -> R.string.text_codegen_target_unavailable
                    })
                } + converted.requestErrors.map(texts::requestError)
                showError(messages.joinToString("\n"))
            }
            is CodeGenOptionsState.Ready -> {
                errorView.visibility = View.GONE
                busy = true
                val id = ++generationId
                val signal = CancellationSignal().also { cancellation = it }
                refreshControls()
                progressRunnable = Runnable {
                    if (!disposed && busy && generationId == id) showProgress()
                }.also { handler.postDelayed(it, 300) }
                generationTask = executor.submit {
                    try {
                        val result = CodeGenEngine().generate(rootNode, converted.request, signal)
                        handler.post { if (isCurrent(id, signal)) finish(result) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Code generation failed", e)
                        handler.post {
                            if (isCurrent(id, signal)) {
                                endGeneration()
                                showError(context.getString(R.string.text_codegen_generation_error))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isCurrent(id: Long, signal: CancellationSignal): Boolean =
        !disposed && optionsDialog?.isShowing == true && generationId == id && !signal.isCancelled

    private fun finish(result: CodeGenOutcome<InspectorNode>) {
        endGeneration()
        when (result) {
            is CodeGenFailure -> if (result.reason != CodeGenFailure.Reason.CANCELLED) showError(texts.failure(result))
            is CodeGenResult -> {
                // Dismiss listeners run asynchronously. Retiring prevents that callback from
                // closing the successful result that is about to be shown.
                // zh-CN: 关闭监听器异步执行; 标记正常结束, 避免它误关即将显示的生成结果.
                retiringOptions = true
                disposed = true
                handler.removeCallbacksAndMessages(null)
                collectionTask?.cancel(true)
                executor.shutdownNow()
                val options = optionsDialog
                optionsDialog = null
                options?.dismiss()
                if (onGenerated != null) onGenerated.invoke(result) else showSimpleResult(result)
            }
        }
    }

    private fun showProgress() {
        if (progressDialog != null) return
        progressDialog = InspectorDialogBuilder(context)
            .content(R.string.text_codegen_generating)
            .progress(true, 0)
            .negativeText(R.string.text_cancel)
            .onNegative { _, _ -> cancelGeneration() }
            .cancelListener { cancelGeneration() }
            .canceledOnTouchOutside(false)
            .build()
            .showAdaptive()
    }

    private fun cancelGeneration(restoreControls: Boolean = true) {
        generationId++
        cancellation?.cancel()
        generationTask?.cancel(true)
        endGeneration(restoreControls)
    }

    private fun endGeneration(restoreControls: Boolean = true) {
        progressRunnable?.let(handler::removeCallbacks)
        progressRunnable = null
        cancellation = null
        generationTask = null
        busy = false
        val progress = progressDialog
        progressDialog = null
        progress?.dismiss()
        if (restoreControls && !disposed) refreshControls()
    }

    private fun refreshControls() {
        optionsDialog?.getActionButton(DialogAction.POSITIVE)?.isEnabled = !busy
        adapter.refreshGroups()
    }

    private fun showError(message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun showSimpleResult(result: CodeGenResult<InspectorNode>) {
        val candidate = result.preferred
        val reason = candidate.explanation.firstOrNull()?.let { texts.explanation(it) }.orEmpty()
        simpleResultDialog = InspectorDialogBuilder(context)
            .title(R.string.text_generated_code)
            .content(listOf(reason, candidate.code).filter { it.isNotEmpty() }.joinToString("\n\n"))
            .negativeText(R.string.dialog_button_dismiss)
            .positiveText(R.string.dialog_button_copy)
            .onPositive { _, _ ->
                ClipboardUtils.setClip(context, candidate.code)
                ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
            }
            .dismissListener { simpleResultDialog = null }
            .build()
            .showAdaptive()
    }

    private fun createGroups(): List<OptionGroup> = listOf(
        OptionGroup(R.string.text_options, false, Attr.entries.map { attr ->
            Option(Kind.CHECK, label = { context.getString(when (attr) {
                Attr.ID -> R.string.text_using_id_selector
                Attr.TEXT -> R.string.text_using_text_selector
                Attr.DESC -> R.string.text_using_desc_selector
            }) }, checked = { attr in state.attributes }, choose = {
                state = state.copy(attributes = if (attr in state.attributes) state.attributes - attr else state.attributes + attr)
            }, tag = "codegen_attr_${attr.name.lowercase()}")
        }),
        OptionGroup(R.string.text_codegen_scope, options = buildList {
            add(radio(R.string.text_codegen_scope_single, { state.scope == Scope.SINGLE }) { state = state.copy(scope = Scope.SINGLE) })
            add(radio(R.string.text_codegen_scope_collection, { state.scope == Scope.COLLECTION }, enabled = { targetSets.isNotEmpty() }) {
                state = state.copy(scope = Scope.COLLECTION)
            })
            add(Option(Kind.INFO, label = {
                context.getString(if (collectionsLoading) R.string.text_codegen_loading_collections else R.string.text_codegen_collection_unavailable)
            }, visible = { targetSets.isEmpty() }))
            repeat(3) { index ->
                add(Option(Kind.RADIO, label = { targetSets.getOrNull(index)?.let(texts::targetSet).orEmpty() },
                    checked = { state.collectionIndex == index }, visible = { state.scope == Scope.COLLECTION && index < targetSets.size },
                    choose = { state = state.copy(collectionIndex = index) }, indent = true, tag = "codegen_collection_$index"))
            }
        }),
        OptionGroup(R.string.text_codegen_query, options = buildList {
            add(radio(R.string.text_codegen_query_wait, { state.query == Query.WAIT }) { state = state.copy(query = Query.WAIT) })
            add(radio(R.string.text_codegen_query_timeout, { state.query == Query.WAIT_TIMEOUT }) { state = state.copy(query = Query.WAIT_TIMEOUT) })
            add(Option(Kind.INPUT, label = { context.getString(R.string.text_codegen_timeout) },
                visible = { state.query == Query.WAIT_TIMEOUT }, value = { state.timeoutText },
                edit = { state = state.copy(timeoutText = it) }, numeric = true, tag = "codegen_timeout_input"))
            add(radio(R.string.text_codegen_query_check, { state.query == Query.CHECK }) { state = state.copy(query = Query.CHECK) })
        }),
        OptionGroup(R.string.text_action, options = buildList {
            for (action in ActionChoice.entries) {
                add(radio(when (action) {
                    ActionChoice.NONE -> R.string.text_codegen_action_none
                    ActionChoice.CLICK -> R.string.text_act_by_click
                    ActionChoice.LONG_CLICK -> R.string.text_act_by_long_click
                    ActionChoice.SET_TEXT -> R.string.text_act_by_set_text
                    ActionChoice.SCROLL_FORWARD -> R.string.text_act_by_scroll_forward
                    ActionChoice.SCROLL_BACKWARD -> R.string.text_act_by_scroll_backward
                }, { state.action == action }) { state = state.copy(action = action) })
                if (action == ActionChoice.SET_TEXT) {
                    add(Option(Kind.INPUT, label = { context.getString(R.string.text_codegen_text_value) },
                        visible = { state.action == ActionChoice.SET_TEXT }, value = { state.actionText },
                        edit = { state = state.copy(actionText = it) }, tag = "codegen_text_input"))
                }
            }
        }),
        OptionGroup(R.string.text_codegen_receiver, options = listOf(
            radio(R.string.text_codegen_receiver_auto, { state.receiver == Receiver.AUTO }) { state = state.copy(receiver = Receiver.AUTO) },
            radio(R.string.text_codegen_receiver_self, { state.receiver == Receiver.SELF }) { state = state.copy(receiver = Receiver.SELF) },
        )),
        OptionGroup(R.string.text_code_style, options = listOf(
            radio(R.string.text_code_style_classic, { state.style == Style.CLASSIC }) { state = state.copy(style = Style.CLASSIC) },
            radio(R.string.text_code_style_flow, { state.style == Style.FLOW }) { state = state.copy(style = Style.FLOW) },
        )),
    )

    private fun radio(label: Int, checked: () -> Boolean, enabled: () -> Boolean = { true }, choose: () -> Unit): Option =
        Option(Kind.RADIO, { context.getString(label) }, checked = checked, enabled = enabled, choose = choose,
            tag = context.resources.getResourceEntryName(label))

    private enum class Kind { RADIO, CHECK, INPUT, INFO }

    private class Option(
        val kind: Kind,
        val label: () -> String,
        val checked: () -> Boolean = { false },
        val enabled: () -> Boolean = { true },
        val visible: () -> Boolean = { true },
        val choose: () -> Unit = {},
        val value: () -> String = { "" },
        val edit: (String) -> Unit = {},
        val numeric: Boolean = false,
        val indent: Boolean = false,
        val tag: String? = null,
    )

    private class OptionGroup(val title: Int, private val expanded: Boolean = true, val options: List<Option>) : Parent<Option> {
        override fun getChildList(): List<Option> = options
        override fun isInitiallyExpanded(): Boolean = expanded
    }

    private inner class GroupHolder(view: View) : ParentViewHolder<OptionGroup, Option>(view) {
        val title: TextView = view.findViewById(R.id.title)
        val icon: ImageView = view.findViewById(R.id.icon)
        init {
            title.setTextColor(colors.text)
            icon.setColorFilter(colors.secondaryText)
            setMainItemClickToExpand()
        }
        override fun onExpansionToggled(expanded: Boolean) { icon.rotation = if (expanded) -90f else 0f }
    }

    private inner class OptionHolder(val row: LinearLayout) : ChildViewHolder<Option>(row) {
        fun bind(option: Option) {
            row.removeAllViews()
            val visible = option.visible()
            row.visibility = if (visible) View.VISIBLE else View.GONE
            row.layoutParams.height = if (visible) ViewGroup.LayoutParams.WRAP_CONTENT else 0
            row.setPadding(dp(if (option.indent) 32 else 16), 0, dp(16), 0)
            if (!visible) return
            val enabled = option.enabled() && !busy && !disposed
            when (option.kind) {
                Kind.RADIO, Kind.CHECK -> {
                    val button = if (option.kind == Kind.RADIO) RadioButton(context) else CheckBox(context)
                    button.apply {
                        tag = option.tag
                        text = option.label()
                        textSize = 16f
                        setTextColor(colors.text)
                        buttonTintList = colors.controls
                        isChecked = option.checked()
                        isEnabled = enabled
                        alpha = if (enabled) 1f else 0.5f
                        minimumHeight = dp(48)
                        setOnClickListener {
                            if (!busy && !disposed) {
                                option.choose()
                                errorView.visibility = View.GONE
                                adapter.refreshGroups()
                            }
                        }
                    }
                    row.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                Kind.INFO -> row.addView(TextView(context).apply {
                    text = option.label()
                    textSize = 13f
                    setTextColor(colors.secondaryText)
                    setPadding(dp(4), 0, dp(4), dp(8))
                })
                Kind.INPUT -> {
                    val input = EditText(context).apply {
                        tag = option.tag
                        id = View.generateViewId()
                        inputType = if (option.numeric) InputType.TYPE_CLASS_NUMBER else
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        setSingleLine(option.numeric)
                        if (!option.numeric) maxLines = 4
                        textSize = 16f
                        setText(option.value())
                        isEnabled = enabled
                        colors.applyTo(this)
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                            override fun afterTextChanged(s: Editable?) {
                                if (!busy && !disposed) {
                                    option.edit(s?.toString().orEmpty())
                                    errorView.visibility = View.GONE
                                }
                            }
                        })
                    }
                    row.addView(TextView(context).apply {
                        text = option.label()
                        textSize = 13f
                        setTextColor(colors.secondaryText)
                        labelFor = input.id
                    })
                    row.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }
        }
    }

    private inner class OptionsAdapter(groups: List<OptionGroup>) : ExpandableRecyclerAdapter<OptionGroup, Option, GroupHolder, OptionHolder>(groups) {
        override fun onCreateParentViewHolder(parentViewGroup: ViewGroup, viewType: Int): GroupHolder =
            GroupHolder(LayoutInflater.from(context).inflate(R.layout.dialog_code_generate_option_group, parentViewGroup, false))

        override fun onCreateChildViewHolder(childViewGroup: ViewGroup, viewType: Int): OptionHolder = OptionHolder(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        override fun onBindParentViewHolder(viewHolder: GroupHolder, parentPosition: Int, optionGroup: OptionGroup) {
            viewHolder.title.setText(optionGroup.title)
            viewHolder.itemView.tag = "codegen_group_${context.resources.getResourceEntryName(optionGroup.title)}"
            viewHolder.icon.rotation = if (viewHolder.isExpanded) 0f else -90f
        }

        override fun onBindChildViewHolder(viewHolder: OptionHolder, parentPosition: Int, childPosition: Int, option: Option) = viewHolder.bind(option)

        fun refreshGroups() {
            groups.indices.forEach(::refreshGroup)
        }

        fun refreshGroup(index: Int) = notifyChildRangeChanged(index, 0, groups[index].options.size)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    companion object { private const val TAG = "CodeGenerateDialog" }
}
