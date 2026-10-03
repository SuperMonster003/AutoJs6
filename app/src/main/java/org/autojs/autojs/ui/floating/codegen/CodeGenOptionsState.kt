package org.autojs.autojs.ui.floating.codegen

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Attr
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Mode
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Receiver
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Style
import org.autojs.autojs.core.accessibility.codegen.TargetSet
import org.autojs.autojs.core.automator.UiNode

/**
 * Input retained independently of the Android controls. Inactive text fields are retained but do
 * not affect validation; an empty SET_TEXT value deliberately means clearing the target's text.
 * zh-CN: 独立于 Android 控件保存输入. 未启用的输入框保留内容但不参与校验; 空的 SET_TEXT 表示清空目标文本.
 */
data class CodeGenOptionsState(
    val scope: Scope = Scope.SINGLE,
    val collectionIndex: Int = 0,
    val query: Query = Query.WAIT,
    val timeoutText: String = CodeGenRequest.DEFAULT_TIMEOUT_MS.toString(),
    val action: ActionChoice = ActionChoice.NONE,
    val actionText: String = "",
    val receiver: Receiver = Receiver.AUTO,
    val attributes: Set<Attr> = Attr.entries.toSet(),
    val style: Style = Style.CLASSIC,
) {
    enum class Query { WAIT, WAIT_TIMEOUT, CHECK }

    enum class ActionChoice { NONE, CLICK, LONG_CLICK, SET_TEXT, SCROLL_FORWARD, SCROLL_BACKWARD }

    enum class Error { COLLECTION_UNAVAILABLE, COLLECTION_SELECTION_INVALID, TIMEOUT_INVALID, TARGET_OUTSIDE_ROOT }

    sealed interface Conversion<out N>

    data class Ready<N : UiNode<N>>(val request: CodeGenRequest<N>) : Conversion<N>

    data class Invalid(
        val errors: List<Error> = emptyList(),
        val requestErrors: List<CodeGenRequest.Error> = emptyList(),
    ) : Conversion<Nothing>

    fun <N : UiNode<N>> toRequest(root: N, target: N, targetSets: List<TargetSet<N>>): Conversion<N> {
        val errors = ArrayList<Error>()
        if (!isWithin(root, target)) errors += Error.TARGET_OUTSIDE_ROOT
        val targets = when (scope) {
            Scope.SINGLE -> listOf(target)
            Scope.COLLECTION -> {
                val collection = targetSets.getOrNull(collectionIndex)
                when {
                    targetSets.isEmpty() -> errors += Error.COLLECTION_UNAVAILABLE
                    collection == null || collection.count < 2 || target !in collection.nodes -> errors += Error.COLLECTION_SELECTION_INVALID
                }
                collection?.nodes.orEmpty().toList()
            }
        }
        val mode = when (query) {
            Query.WAIT -> Mode.WAIT
            Query.CHECK -> Mode.CHECK
            Query.WAIT_TIMEOUT -> {
                val text = timeoutText.trim()
                val millis = text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toLongOrNull()
                if (millis == null || millis !in CodeGenRequest.MIN_TIMEOUT_MS..CodeGenRequest.MAX_TIMEOUT_MS) {
                    errors += Error.TIMEOUT_INVALID
                    Mode.WAIT_TIMEOUT(CodeGenRequest.DEFAULT_TIMEOUT_MS)
                } else {
                    Mode.WAIT_TIMEOUT(millis)
                }
            }
        }
        if (errors.isNotEmpty()) return Invalid(errors)
        val request = CodeGenRequest(
            targets = targets,
            scope = scope,
            mode = mode,
            action = when (action) {
                ActionChoice.NONE -> Action.NONE
                ActionChoice.CLICK -> Action.CLICK
                ActionChoice.LONG_CLICK -> Action.LONG_CLICK
                ActionChoice.SET_TEXT -> Action.SET_TEXT(actionText)
                ActionChoice.SCROLL_FORWARD -> Action.SCROLL_FORWARD
                ActionChoice.SCROLL_BACKWARD -> Action.SCROLL_BACKWARD
            },
            receiver = receiver,
            attributes = attributes.toSet(),
            style = style,
        )
        val requestErrors = request.validate()
        return if (requestErrors.isEmpty()) Ready(request) else Invalid(requestErrors = requestErrors)
    }

    private fun <N : UiNode<N>> isWithin(root: N, target: N): Boolean {
        val visited = HashSet<N>()
        var node: N? = target
        while (node != null && visited.add(node)) {
            if (node == root) return true
            node = node.parent()
        }
        return false
    }
}
