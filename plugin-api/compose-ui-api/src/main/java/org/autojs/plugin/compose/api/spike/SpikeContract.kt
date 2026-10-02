package org.autojs.plugin.compose.api.spike

import android.content.Context
import android.os.Bundle
import android.view.View

/** P0 regression fixture only, outside the frozen V1 surface. Remove with the P2.1 renderer migration. */
object SpikeContract {
    const val VERSION = -1
    const val PACKAGE = "io.github.supermonster003.autojs6.plugin.compose.ui"
    const val INFO_ACTION = "org.autojs.plugin.INFO"
    const val INFO_CATEGORY = "compose-ui"
    const val FACTORY_META = "org.autojs.plugin.compose.RENDERER_FACTORY"
    const val HOST_VERSION_META = "requiresHostVersion"
    const val COLUMN = "Column"
    const val TEXT = "Text"
    const val BUTTON = "Button"
    const val CLICK = "click"
    const val ERROR = "error"
    const val INVALID_TREE = "INVALID_TREE"
    const val RENDER_FAILED = "RENDER_FAILED"
    const val CLOSED = "CLOSED"
    const val WRONG_THREAD = "WRONG_THREAD"
    const val MAX_NODES = 64
    const val MAX_DEPTH = 8
    const val MAX_TEXT = 4096
}

data class UiNode(
    val type: String,
    val text: String = "",
    val callbackId: Int = 0,
    val children: List<UiNode> = emptyList(),
)

data class UiEvent(val name: String, val callbackId: Int = 0, val code: String? = null)

fun interface ComposeUiEventSink { fun enqueue(event: UiEvent) }

interface ComposeUiRendererFactory {
    fun contractVersion(): Int
    fun create(context: Context, eventSink: ComposeUiEventSink): ComposeUiRenderer
    /** Class objects permit identity checks from both sides, including minified APKs. */
    fun classOrigins(): Map<String, Class<*>>
    fun probe(context: Context): Bundle
}

interface ComposeUiRenderer {
    fun view(): View
    fun apply(root: UiNode)
    fun dispose()
    fun diagnostics(): Bundle
}

class ComposeUiContractException(val code: String) : IllegalArgumentException(code)
