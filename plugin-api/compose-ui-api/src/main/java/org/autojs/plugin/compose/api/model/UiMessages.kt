package org.autojs.plugin.compose.api.model

import android.os.Bundle
import org.autojs.plugin.compose.api.ComposeUiContract
import org.autojs.plugin.compose.api.ComposeUiLimits

sealed class UiPatch : ComposeParcelable() {
    /** props replaces the complete map; null modifier/callbacks retain the previous values. */
    class SetProps(nodeId: Int, props: Map<String, UiValue>, modifier: List<ModifierOp>? = null, callbacks: Map<String, Int>? = null) : UiPatch() {
        val nodeId = positive(nodeId)
        val props = frozenMap(props, ComposeUiLimits.MAX_PROPERTIES_PER_NODE)
        val modifier = modifier?.let { frozenList(it, ComposeUiLimits.MAX_MODIFIER_OPS) }
        val callbacks = callbacks?.let { frozenMap(it, ComposeUiLimits.MAX_PROPERTIES_PER_NODE) }
        init { this.callbacks?.values?.forEach(::positive) }
        override fun fields() = listOf(nodeId, props, modifier, callbacks)
        companion object { @JvmField val CREATOR = creator<SetProps>() }
    }
    class Insert(parentId: Int, val index: Int, val subtree: UiTree) : UiPatch() {
        val parentId = positive(parentId)
        init { if (index < 0) invalid("Negative insertion index") }
        override fun fields() = listOf(parentId, index, subtree)
        companion object { @JvmField val CREATOR = creator<Insert>() }
    }
    class Remove(parentId: Int, nodeId: Int) : UiPatch() {
        val parentId = positive(parentId)
        val nodeId = positive(nodeId)
        override fun fields() = listOf(parentId, nodeId)
        companion object { @JvmField val CREATOR = creator<Remove>() }
    }
    /** toIndex is the destination index AFTER removal from the current parent's children. */
    class Move(parentId: Int, nodeId: Int, val toIndex: Int) : UiPatch() {
        val parentId = positive(parentId)
        val nodeId = positive(nodeId)
        init { if (toIndex < 0) invalid("Negative destination index") }
        override fun fields() = listOf(parentId, nodeId, toIndex)
        companion object { @JvmField val CREATOR = creator<Move>() }
    }
    class ReplaceSlot(parentId: Int, slot: String, val subtree: UiTree?) : UiPatch() {
        val parentId = positive(parentId)
        val slot = name(slot)
        override fun fields() = listOf(parentId, slot, subtree)
        companion object { @JvmField val CREATOR = creator<ReplaceSlot>() }
    }
    class SetRoot(val tree: UiTree) : UiPatch() {
        override fun fields() = listOf(tree)
        companion object { @JvmField val CREATOR = creator<SetRoot>() }
    }
    companion object { @JvmField val CREATOR = creator<UiPatch>() }
}

class UiPatchBatch(sessionId: Int, generation: Long, patches: List<UiPatch>) : ComposeParcelable() {
    val sessionId = positive(sessionId)
    val generation = nonnegative(generation)
    val patches = frozenList(patches, ComposeUiLimits.MAX_PATCHES_PER_BATCH)
    override fun fields() = listOf(sessionId, generation, patches)
    companion object { @JvmField val CREATOR = creator<UiPatchBatch>() }
}

/** Payload supports only immutable scalar values. Both input and getter Bundle are detached snapshots. */
class UiEvent(sessionId: Int, generation: Long, nodeId: Int, type: String, val callbackId: Int, payload: Bundle = Bundle()) : ComposeParcelable() {
    val sessionId = positive(sessionId)
    val generation = nonnegative(generation)
    val nodeId = nodeId.also { if (it != ComposeUiContract.NO_NODE_ID) positive(it) }
    val type = name(type)
    internal val payloadValues: Map<String, Any?> = snapshotPayload(payload)
    val payload: Bundle get() = payloadBundle(payloadValues)
    init { if (callbackId < 0) invalid("Negative callback ID") }
    override fun fields() = listOf(sessionId, generation, nodeId, type, callbackId, payloadValues)
    companion object { @JvmField val CREATOR = creator<UiEvent>() }
}

@Suppress("DEPRECATION")
internal fun snapshotPayload(source: Bundle): Map<String, Any?> {
    limit(source.size() <= ComposeUiLimits.MAX_EVENT_FIELDS, "Event payload exceeds field limit")
    return frozenMap(source.keySet().associateWith { key ->
        when (val value = source.get(key)) {
            null, is Boolean, is Int, is Long -> value
            is String -> text(value)
            is Double -> finite(value)
            is Float -> finite(value.toDouble())
            else -> invalid("Event payload must contain only scalar values")
        }
    }, ComposeUiLimits.MAX_EVENT_FIELDS)
}

internal fun payloadBundle(values: Map<String, Any?>) = Bundle().apply {
    values.forEach { (key, value) ->
        when (value) {
            null -> putString(key, null)
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Double -> putDouble(key, value)
            else -> invalid("Invalid payload scalar")
        }
    }
}

class TextSelection(val start: Int, val end: Int) : ComposeParcelable() {
    init { if (start < 0 || end < 0) invalid("Selection offsets must be nonnegative") }
    override fun fields() = listOf(start, end)
    companion object { @JvmField val CREATOR = creator<TextSelection>() }
}

enum class SnackbarDuration { SHORT, LONG, INDEFINITE }

sealed class UiCommand : ComposeParcelable() {
    class Focus(nodeId: Int) : UiCommand() {
        val nodeId = positive(nodeId)
        override fun fields() = listOf(nodeId)
        companion object { @JvmField val CREATOR = creator<Focus>() }
    }
    class Blur(nodeId: Int) : UiCommand() {
        val nodeId = positive(nodeId)
        override fun fields() = listOf(nodeId)
        companion object { @JvmField val CREATOR = creator<Blur>() }
    }
    /** index selects a lazy item. Without index, offset is an absolute scroll position in pixels. */
    class ScrollTo(nodeId: Int, val index: Int? = null, val offset: Int? = null) : UiCommand() {
        val nodeId = positive(nodeId)
        init {
            if (index == null && offset == null || index != null && index < 0 || index == null && offset!! < 0) invalid("Invalid scroll target")
        }
        override fun fields() = listOf(nodeId, index, offset)
        companion object { @JvmField val CREATOR = creator<ScrollTo>() }
    }
    /** UTF-16 selection offsets may be reversed. editSeq is the last sequence observed by the script. */
    class Edit(nodeId: Int, text: String? = null, val selection: TextSelection? = null, editSeq: Long) : UiCommand() {
        val nodeId = positive(nodeId)
        val text = text?.let(::text)
        val editSeq = nonnegative(editSeq)
        init {
            if (text == null && selection == null) invalid("Empty edit")
            if (text != null && selection != null && maxOf(selection.start, selection.end) > text.length) invalid("Selection outside text")
        }
        override fun fields() = listOf(nodeId, text, selection, editSeq)
        companion object { @JvmField val CREATOR = creator<Edit>() }
    }
    class ShowSnackbar(message: String, actionLabel: String? = null, val duration: SnackbarDuration = SnackbarDuration.SHORT, val callbackId: Int? = null) : UiCommand() {
        val message = text(message)
        val actionLabel = actionLabel?.let(::text)
        init { callbackId?.let(::positive) }
        override fun fields() = listOf(message, actionLabel, duration, callbackId)
        companion object { @JvmField val CREATOR = creator<ShowSnackbar>() }
    }
    companion object { @JvmField val CREATOR = creator<UiCommand>() }
}
