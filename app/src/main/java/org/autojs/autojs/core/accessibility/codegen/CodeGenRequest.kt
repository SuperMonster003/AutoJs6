package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.automator.UiNode

/**
 * The complete intent of a code-generation request, independent of the dialog and Android.
 * Validation collects errors so the dialog can retain the user's input.
 * zh-CN: 独立于对话框与 Android 的完整代码生成意图. 校验收集错误, 便于对话框保留用户输入.
 */
data class CodeGenRequest<N : UiNode<N>>(
    val targets: List<N>,
    val scope: Scope = Scope.SINGLE,
    val mode: Mode = Mode.WAIT,
    val action: Action = Action.NONE,
    val receiver: Receiver = Receiver.AUTO,
    val attributes: Set<Attr> = Attr.entries.toSet(),
    val style: Style = Style.CLASSIC,
) {

    enum class Scope { SINGLE, COLLECTION }

    sealed interface Mode {
        data object WAIT : Mode
        data class WAIT_TIMEOUT(val millis: Long) : Mode
        data object CHECK : Mode
    }

    sealed interface Action {
        data object NONE : Action
        data object CLICK : Action
        data object LONG_CLICK : Action
        data class SET_TEXT(val text: String) : Action
        data object SCROLL_FORWARD : Action
        data object SCROLL_BACKWARD : Action
    }

    enum class Receiver { AUTO, SELF }

    enum class Attr { ID, TEXT, DESC }

    enum class Style { CLASSIC, FLOW }

    enum class Error {
        EMPTY_TARGETS,
        SINGLE_REQUIRES_ONE_TARGET,
        TIMEOUT_OUT_OF_RANGE,
        TARGET_NOT_EDITABLE,
    }

    fun validate(): List<Error> = buildList {
        if (targets.isEmpty()) add(Error.EMPTY_TARGETS)
        if (scope == Scope.SINGLE && targets.size > 1) add(Error.SINGLE_REQUIRES_ONE_TARGET)
        if (mode is Mode.WAIT_TIMEOUT && mode.millis !in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) add(Error.TIMEOUT_OUT_OF_RANGE)
        if (action is Action.SET_TEXT && receiver == Receiver.AUTO && targets.any { !it.editable() }) add(Error.TARGET_NOT_EDITABLE)
    }

    companion object {
        const val MIN_TIMEOUT_MS = 100L
        const val MAX_TIMEOUT_MS = 600_000L
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}

/**
 * Migration of the old four query options. A collection without a suggested target set stays
 * invalid; it must not silently become a single-target request.
 * zh-CN: 旧版四种查询选项的迁移. 无建议目标集合的集合请求保持无效, 不静默降为单目标.
 */
enum class LegacyMode {
    FIND_ONE,
    UNTIL_FIND,
    WAIT_FOR,
    EXISTS,
    NONE;

    fun <N : UiNode<N>> toRequest(
        target: N,
        targetSets: List<List<N>> = emptyList(),
        action: CodeGenRequest.Action = CodeGenRequest.Action.NONE,
        receiver: CodeGenRequest.Receiver = CodeGenRequest.Receiver.AUTO,
        attributes: Set<CodeGenRequest.Attr> = CodeGenRequest.Attr.entries.toSet(),
        style: CodeGenRequest.Style = CodeGenRequest.Style.CLASSIC,
    ): CodeGenRequest<N> = CodeGenRequest(
        targets = if (this == UNTIL_FIND) targetSets.firstOrNull().orEmpty() else listOf(target),
        scope = if (this == UNTIL_FIND) CodeGenRequest.Scope.COLLECTION else CodeGenRequest.Scope.SINGLE,
        mode = if (this == EXISTS) CodeGenRequest.Mode.CHECK else CodeGenRequest.Mode.WAIT,
        action = action,
        receiver = receiver,
        attributes = attributes,
        style = style,
    )
}
