package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.plugin.compose.api.ComposeUiContract
import org.autojs.plugin.compose.api.model.ModifierOp
import org.autojs.plugin.compose.api.model.UiCommand
import org.autojs.plugin.compose.api.model.UiEvent
import org.autojs.plugin.compose.api.model.UiTree
import org.autojs.plugin.compose.api.model.UiValue
import org.mozilla.javascript.Function

/** A bridge owned by one script session. Only [ComposeNodeSnapshot.tree] crosses the renderer ABI. */
interface ComposeNodeBinding {
    val isClosed: Boolean
    fun publish(snapshot: ComposeNodeSnapshot)
    fun execute(command: UiCommand)
}

/** The reactive layer publishes current only after a successful frame receipt. */
interface ComposeNodeRef { var current: ComposeNode? }

class ComposeNodeSnapshot internal constructor(
    val root: ComposeNode,
    val tree: UiTree,
    val callbacks: Map<Int, (UiEvent) -> Unit>,
    val retainedImages: List<Any>,
    val refs: Map<ComposeNodeRef, ComposeNode>,
    val scriptCallbackCount: Int,
    internal val states: Map<ComposeNode, ComposeNodeState>,
    internal val editors: Map<ComposeNode, ComposeNodeEditor?>,
)

internal data class ComposeNodeListeners(val token: Int, val functions: List<Function>)
internal data class ComposeNativeViewLease(val id: Int, val view: android.view.View)

internal data class ComposeNodeState(
    val source: Map<String, UiValue> = emptyMap(),
    val props: Map<String, UiValue> = emptyMap(),
    val modifier: List<ModifierOp> = emptyList(),
    val explicitModifier: ComposeModifier? = null,
    val modifierCallbacks: Map<Int, Function> = emptyMap(),
    val listeners: Map<String, ComposeNodeListeners> = emptyMap(),
    val images: Map<String, List<Any>> = emptyMap(),
    val nativeView: ComposeNativeViewLease? = null,
    val children: List<ComposeNode> = emptyList(),
    val slots: Map<String, ComposeNode> = emptyMap(),
    val parent: ComposeNode? = null,
    val ref: ComposeNodeRef? = null,
)

internal data class ComposeNodeEditor(val text: String = "", val start: Int = 0, val end: Int = 0, val sequence: Long = 0)

/** Long-lived script handle. Internal identity exists before mount; the public ID does not. */
class ComposeNode internal constructor(
    internal val graph: ComposeNodeGraph,
    val type: String,
    val key: String?,
    internal val stableId: Int,
) {
    internal var state = ComposeNodeState()
    internal var affinity: ComposeNodeBinding? = null
    internal var mountedOnce = false
    internal var editor: ComposeNodeEditor? = null
    internal var deferredEdit: UiCommand.Edit? = null
    val js: ComposeNodeNativeObject by lazy(LazyThreadSafetyMode.NONE) { ComposeNodeNativeObject(this) }
    val nodeId get() = if (graph.isMounted(this)) stableId else ComposeUiContract.NO_NODE_ID
    val parent get() = state.parent
    val children get() = state.children.toList()

    fun set(properties: Any?): ComposeNode = graph.set(this, properties)
    fun get(name: String): Any? = graph.get(this, name)
    fun append(child: Any?): ComposeNode = graph.insert(this, state.children.size, child)
    fun insert(index: Int, child: Any?): ComposeNode = graph.insert(this, index, child)
    fun remove(child: Any?): ComposeNode = graph.remove(this, child)
    fun replace(children: Any?): ComposeNode = graph.replace(this, children)
    fun clear(): ComposeNode = graph.replace(this, emptyList<Any>())
    fun slot(name: String, child: Any?): ComposeNode = graph.slot(this, name, child)
    fun on(name: String, callback: Function): ComposeNode = graph.on(this, name, callback)
    fun off(name: String, callback: Function? = null): ComposeNode = graph.off(this, name, callback)
    fun focus(): ComposeNode = graph.command(this, UiCommand.Focus(stableId))
    fun blur(): ComposeNode = graph.command(this, UiCommand.Blur(stableId))
    fun scrollTo(target: Any?): ComposeNode = graph.scrollTo(this, target)
    fun edit(edit: Any?): ComposeNode = graph.edit(this, edit)
    override fun toString() = "ComposeNode(type=$type, nodeId=$nodeId)"
}
