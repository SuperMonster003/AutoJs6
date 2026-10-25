package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.ComposeUiComponents as C
import org.autojs.plugin.compose.api.ComposeUiEvents as E
import org.autojs.plugin.compose.api.ComposeUiProps as P
import org.autojs.plugin.compose.api.model.UiCommand
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.ScriptableObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ComposeNodeGraphTest {
    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private lateinit var graph: ComposeNodeGraph
    private val warnings = ArrayList<String>()

    @Before fun setup() {
        context = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
        graph = ComposeNodeGraph(scope, ComposeScriptValues(colorParser = { (it as Number).toInt() }), { _, _, _ -> }, warn = warnings::add)
        for (type in listOf(C.TEXT, C.COLUMN, C.ROW, C.BOX, C.BUTTON, C.TEXT_FIELD)) {
            ScriptableObject.putProperty(scope, type, RhinoUtils.newBaseFunction(scope, type, { args ->
                graph.create(type, args.getOrNull(0), args.getOrNull(1)).js
            }, RhinoUtils.NOT_CONSTRUCTABLE))
        }
    }

    @After fun close() { graph.close(); Context.exit() }
    private fun js(source: String) = context.evaluateString(scope, source, "compose-node-test.js", 1, null)
    private fun function(source: String = "(function () {})") = js(source) as Function
    private fun text(value: String, key: String? = null) = graph.create(C.TEXT, mapOf(P.TEXT to value) + (key?.let { mapOf("key" to it) } ?: emptyMap()))
    private fun failure(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") }
        catch (failure: ComposeUiContractException) { assertEquals(code, failure.code) }
    }

    private class Binding : ComposeNodeBinding {
        override var isClosed = false
        val frames = ArrayList<ComposeNodeSnapshot>()
        val commands = ArrayList<UiCommand>()
        var failure: RuntimeException? = null
        override fun publish(snapshot: ComposeNodeSnapshot) { failure?.let { throw it }; frames += snapshot }
        override fun execute(command: UiCommand) { commands += command }
    }

    @Test fun rhinoAccessorsAndChildSnapshotsExposeHandlesWithoutLeakingWireObjects() {
        assertEquals("Text|-1|first|second|1|true", Context.toString(js("""
            var leaf = Text({text:'first', key:'stable'});
            var box = Column({}, [leaf]);
            var old = box.children;
            leaf.text = 'second';
            old.length = 0;
            [leaf.type, leaf.nodeId, 'first', leaf.get('text'), box.children.length, leaf.parent === box].join('|');
        """)))
        val leaf = graph.node(ScriptableObject.getProperty(scope, "leaf"))!!
        assertEquals("stable", leaf.key)
        assertEquals(-1, leaf.nodeId)
        assertSame(leaf.js, leaf.js)
        failure(ComposeUiErrorCodes.UNKNOWN_PROP) { leaf.set(mapOf("missing" to true, P.TEXT to "not applied")) }
        assertEquals("second", leaf.get(P.TEXT))
        failure(ComposeUiErrorCodes.UNKNOWN_PROP) { leaf.set(mapOf("key" to "changed")) }
        assertEquals("stable", leaf.key)
    }

    @Test fun childMutationsMoveWithinOneTreeAndRejectCyclesOrDuplicateKeysAtomically() {
        val a = text("a", "a"); val b = text("b", "b"); val c = text("c", "c")
        val left = graph.create(C.COLUMN, children = listOf(a, b))
        val right = graph.create(C.COLUMN, children = listOf(c))
        val root = graph.create(C.ROW, children = listOf(left, right))
        right.insert(0, a)
        assertEquals(listOf(b), left.children); assertEquals(listOf(a, c), right.children); assertSame(right, a.parent)
        right.insert(0, c)
        assertEquals(listOf(c, a), right.children)
        failure(ComposeUiErrorCodes.INVALID_ARGUMENT) { a.append(root) }
        assertEquals(listOf(left, right), root.children)
        failure(ComposeUiErrorCodes.DUPLICATE_KEY) { right.append(text("duplicate", "a")) }
        assertEquals(listOf(c, a), right.children)
        right.remove(0)
        assertNull(c.parent); assertEquals(listOf(a), right.children)
        right.replace(listOf(c, a)); right.clear()
        assertNull(a.parent); assertNull(c.parent); assertTrue(right.children.isEmpty())
    }

    @Test fun slotTransfersAndScopedModifiersValidateTheProspectiveWholeTree() {
        val modifier = ComposeModifier(ComposeScriptValues(), scope).append(ComposeUiModifiers.WEIGHT, arrayOf(1.0))
        val weighted = graph.create(C.TEXT, mapOf(P.TEXT to "weight", "modifier" to modifier))
        val column = graph.create(C.COLUMN, children = weighted)
        val box = graph.create(C.BOX)
        failure(ComposeUiErrorCodes.SCOPE_MISMATCH) { box.append(weighted) }
        assertSame(column, weighted.parent)
        val button = graph.create(C.BUTTON, children = "old")
        val label = text("new")
        failure(ComposeUiErrorCodes.INVALID_ARGUMENT) { button.slot(ComposeUiSlots.CONTENT, label) }
        assertNull(label.parent)
        graph.transaction { button.clear(); button.slot(ComposeUiSlots.CONTENT, label) }
        assertSame(button, label.parent)
        assertEquals(label.stableId, graph.snapshot(button).tree.nodes.single { it.nodeId == button.stableId }.slots[ComposeUiSlots.CONTENT])
        button.slot(ComposeUiSlots.CONTENT, null)
        assertNull(label.parent)
    }

    @Test fun mountPublishesOnceAndSeparatesUnboundDetachedAndClosedHandleBehavior() {
        val button = graph.create(C.BUTTON, children = "press")
        val root = graph.create(C.COLUMN, children = button)
        val binding = Binding()
        failure(ComposeUiErrorCodes.NODE_DETACHED) { button.focus() }
        graph.mount(root, binding)
        assertEquals(1, binding.frames.size); assertTrue(button.nodeId > 0)
        val id = button.nodeId
        graph.transaction { button.set(mapOf(P.ENABLED to false)); root.append(text("extra")); button.set(mapOf(P.ENABLED to true)) }
        assertEquals(2, binding.frames.size)
        button.focus()
        assertEquals(id, (binding.commands.single() as UiCommand.Focus).nodeId)
        root.remove(button)
        assertEquals(-1, button.nodeId)
        failure(ComposeUiErrorCodes.NODE_DETACHED) { button.focus() }
        button.set(mapOf(P.ENABLED to false))
        root.append(button)
        assertEquals(id, button.nodeId)
        root.remove(button)
        assertEquals(-1, button.nodeId)
        binding.isClosed = true
        failure(ComposeUiErrorCodes.SESSION_CLOSED) { button.set(mapOf(P.ENABLED to true)) }
        failure(ComposeUiErrorCodes.SESSION_CLOSED) { root.clear() }
    }

    @Test fun crossSessionTransferAndLateTransactionFailurePreserveBothTrees() {
        val item = text("a")
        val first = graph.create(C.COLUMN, children = item)
        val second = graph.create(C.COLUMN)
        val a = Binding(); val b = Binding()
        graph.mount(first, a); graph.mount(second, b)
        failure(ComposeUiErrorCodes.NODE_DETACHED) { second.append(item) }
        assertSame(first, item.parent); assertTrue(second.children.isEmpty())
        try { graph.transaction { item.set(mapOf(P.TEXT to "changed")); first.append(text("pending")); error("render failed") }; fail() }
        catch (_: IllegalStateException) { }
        assertEquals("a", item.get(P.TEXT)); assertEquals(listOf(item), first.children)
        assertEquals(1, a.frames.size); assertEquals(1, b.frames.size)
    }

    @Test fun closingOneBindingDropsItsOwnedCallbacksWhileOtherSessionsRemainUsable() {
        val callback = function()
        val modifier = ComposeModifier(ComposeScriptValues(), scope).append(ComposeUiModifiers.CLICKABLE, arrayOf(callback))
        val first = graph.create(C.BUTTON, mapOf("onClick" to callback, "modifier" to modifier))
        val second = graph.create(C.BUTTON, mapOf("onClick" to callback))
        val a = Binding(); val b = Binding()
        graph.mount(first, a); graph.mount(second, b)
        assertFalse(first.state.listeners.isEmpty()); assertFalse(first.state.modifierCallbacks.isEmpty())
        a.isClosed = true; graph.unmount(a)
        assertTrue(first.state.listeners.isEmpty()); assertTrue(first.state.modifierCallbacks.isEmpty())
        assertNull(first.state.explicitModifier)
        failure(ComposeUiErrorCodes.SESSION_CLOSED) { first.focus() }
        second.set(mapOf(P.ENABLED to false))
        assertFalse(second.state.listeners.isEmpty()); assertEquals(false, second.get(P.ENABLED))
    }

    @Test fun callbackTokensVersionHandlersAndTextFieldsObserveInputWithoutUserListeners() {
        val button = graph.create(C.BUTTON)
        val first = function(); val second = function()
        button.on(E.CLICK, first)
        val before = graph.snapshot(button)
        button.on(E.CLICK, second)
        val after = graph.snapshot(button)
        val oldToken = before.tree.nodes.single().callbacks.getValue(E.CLICK)
        val newToken = after.tree.nodes.single().callbacks.getValue(E.CLICK)
        assertNotEquals(oldToken, newToken)
        assertEquals(setOf(oldToken), before.callbacks.keys); assertEquals(setOf(newToken), after.callbacks.keys)
        button.off(E.CLICK, first); button.off(E.CLICK)
        assertTrue(graph.snapshot(button).callbacks.isEmpty())
        val field = graph.create(C.TEXT_FIELD)
        val fieldSnapshot = graph.snapshot(field)
        assertEquals(setOf(E.VALUE_CHANGE), fieldSnapshot.tree.nodes.single().callbacks.keys)
        assertEquals(1, fieldSnapshot.callbacks.size)
        field.edit(mapOf("text" to "abc", "selection" to listOf(0, 1)))
        assertEquals("abc", field.get(P.TEXT))
        val binding = Binding(); graph.mount(field, binding)
        val initialEdit = binding.commands.single() as UiCommand.Edit
        assertEquals("abc", initialEdit.text); assertEquals(0L, initialEdit.editSeq)
        assertEquals(0, initialEdit.selection!!.start); assertEquals(1, initialEdit.selection!!.end)
        binding.commands.clear()
        graph.accept(binding, binding.frames.last())
        graph.transaction { field.edit(mapOf("text" to "abcd")); field.edit(mapOf("selection" to listOf(0, 2))) }
        val edit = binding.commands.single() as UiCommand.Edit
        assertEquals("abcd", edit.text); assertEquals(0, edit.selection!!.start); assertEquals(2, edit.selection!!.end)
        assertEquals("abc", binding.frames.last().tree.nodes.single().props[P.TEXT].let { (it as org.autojs.plugin.compose.api.model.UiValue.Str).value })
    }

    @Test fun acceptedFieldRemovalResetsNativeLifetimeAndReinsertionKeepsStableIdentity() {
        val field = graph.create(C.TEXT_FIELD, mapOf(P.TEXT to "seed"))
        val root = graph.create(C.COLUMN, children = field)
        val binding = Binding(); graph.mount(root, binding); graph.accept(binding, binding.frames.last())
        val id = field.nodeId
        field.editor = ComposeNodeEditor("native", 2, 4, 7)
        root.remove(field)
        assertEquals(-1, field.nodeId); assertEquals("native", field.get(P.TEXT)); assertEquals(7L, field.editor!!.sequence)
        graph.accept(binding, binding.frames.last())
        assertEquals(ComposeNodeEditor("seed", 4, 4, 0), field.editor)
        root.append(field)
        assertEquals(id, field.nodeId)
        graph.accept(binding, binding.frames.last())
        field.edit(mapOf("text" to "retry"))
        assertEquals(0L, (binding.commands.single() as UiCommand.Edit).editSeq)
    }

    @Test fun coalescedRemoveReaddPreservesNativeEditorAndDetachedEditsKeepCapturedSequence() {
        val field = graph.create(C.TEXT_FIELD, mapOf(P.TEXT to "seed"))
        val root = graph.create(C.COLUMN, children = field)
        val binding = Binding(); graph.mount(root, binding); graph.accept(binding, binding.frames.last())
        field.editor = ComposeNodeEditor("typed", 1, 3, 7)
        graph.transaction { root.remove(field); field.set(mapOf(P.IS_ERROR to true)); root.append(field) }
        graph.accept(binding, binding.frames.last())
        assertEquals(ComposeNodeEditor("typed", 1, 3, 7), field.editor)
        assertTrue(binding.commands.isEmpty())
        graph.transaction { root.remove(field); field.edit(mapOf("text" to "local")); root.append(field) }
        val request = binding.commands.single() as UiCommand.Edit
        assertEquals(7L, request.editSeq); assertEquals("local", request.text)
        assertEquals("seed", (binding.frames.last().tree.nodes.single { it.nodeId == field.stableId }.props[P.TEXT] as org.autojs.plugin.compose.api.model.UiValue.Str).value)
        graph.accept(binding, binding.frames.last())
        assertEquals(7L, field.editor!!.sequence)
    }

    @Test fun capturedDetachedEditIsNeverRefreshedAcrossAnAcceptedDeletion() {
        val field = graph.create(C.TEXT_FIELD, mapOf(P.TEXT to "seed"))
        val root = graph.create(C.COLUMN, children = field)
        val binding = Binding(); graph.mount(root, binding); graph.accept(binding, binding.frames.last())
        field.editor = ComposeNodeEditor("typed", 5, 5, 8)
        root.remove(field)
        field.edit(mapOf("text" to "captured"))
        graph.accept(binding, binding.frames.last())
        assertEquals(0L, field.editor!!.sequence)
        root.append(field)
        graph.accept(binding, binding.frames.last())
        val stale = binding.commands.single() as UiCommand.Edit
        assertEquals(8L, stale.editSeq); assertEquals("captured", stale.text)
        assertEquals(ComposeNodeEditor("seed", 4, 4, 0), field.editor)
        field.edit(mapOf("text" to "new request"))
        assertEquals(0L, (binding.commands.last() as UiCommand.Edit).editSeq)
    }

    @Test fun receiptsPublishRefsAndRollbackRejectedParentMoves() {
        val ref = object : ComposeNodeRef { override var current: ComposeNode? = null }
        val child = graph.create(C.TEXT, mapOf(P.TEXT to "a", "ref" to ref))
        val left = graph.create(C.COLUMN, children = child)
        val right = graph.create(C.COLUMN)
        val root = graph.create(C.ROW, children = listOf(left, right))
        val binding = Binding(); graph.mount(root, binding)
        assertNull(ref.current)
        graph.accept(binding, binding.frames.last())
        assertSame(child, ref.current)
        right.append(child)
        assertSame(right, child.parent)
        graph.reject(binding, binding.frames.last())
        assertSame(left, child.parent); assertEquals(listOf(child), left.children); assertTrue(right.children.isEmpty())
        assertSame(child, ref.current)
        graph.unmount(binding)
        assertNull(ref.current)
    }

    @Test fun rejectionOfTheInitialFrameKeepsTheOpenSessionConnectedForRecovery() {
        val root = text("first")
        val binding = Binding(); graph.mount(root, binding)
        val id = root.nodeId
        graph.reject(binding, binding.frames.single())
        root.set(mapOf(P.TEXT to "recovered"))
        assertEquals(2, binding.frames.size)
        assertEquals(id, root.nodeId)
        graph.accept(binding, binding.frames.last())
        assertEquals("recovered", root.get(P.TEXT))
    }

    @Test fun flightAndPendingReceiptsRestoreTheCorrectRootAndOnlyPublishAcceptedRefs() {
        fun ref() = object : ComposeNodeRef { override var current: ComposeNode? = null }
        fun draft(type: String, value: String, ref: ComposeNodeRef) = graph.create(type,
            children = graph.create(C.TEXT, mapOf(P.TEXT to value, "ref" to ref)))
        val initialRef = ref(); val flightRef = ref(); val pendingRef = ref()
        val initial = graph.render(null) { draft(C.COLUMN, "accepted", initialRef) }
        val binding = Binding(); graph.mount(initial, binding); graph.accept(binding, binding.frames.last())
        val flight = graph.render(initial) { draft(C.ROW, "flight", flightRef) }
        val flightSnapshot = binding.frames.last()
        graph.render(flight) { draft(C.BOX, "pending", pendingRef) }
        val pendingSnapshot = binding.frames.last()
        graph.reject(binding, flightSnapshot)
        assertSame(initial.children.single(), initialRef.current)
        assertNull(flightRef.current); assertNull(pendingRef.current)
        graph.reject(binding, pendingSnapshot)
        val count = binding.frames.size
        initial.children.single().set(mapOf(P.TEXT to "still connected"))
        assertEquals(count + 1, binding.frames.size)
        assertSame(initial, binding.frames.last().root)
        graph.accept(binding, binding.frames.last())

        val nextRef = ref(); val rejectedRef = ref()
        val next = graph.render(initial) { draft(C.ROW, "next", nextRef) }
        val nextSnapshot = binding.frames.last()
        graph.render(next) { draft(C.BOX, "later", rejectedRef) }
        val rejectedSnapshot = binding.frames.last()
        // An earlier successful frame is observable while its later prospective tree is pending.
        graph.accept(binding, nextSnapshot)
        assertNull(initialRef.current); assertSame(next.children.single(), nextRef.current)
        assertNull(rejectedRef.current)
        graph.reject(binding, rejectedSnapshot)
        val after = binding.frames.size
        next.children.single().set(mapOf(P.TEXT to "recovered next"))
        assertEquals(after + 1, binding.frames.size)
        assertSame(next, binding.frames.last().root)
        assertSame(next.children.single(), nextRef.current); assertNull(rejectedRef.current)
    }

    @Test fun coalescedTextReplacementUsesItsOwnCaretAndAggregateHandlerBudgetIsBounded() {
        val field = graph.create(C.TEXT_FIELD, mapOf(P.TEXT to "initial text"))
        val binding = Binding(); graph.mount(field, binding)
        graph.transaction { field.edit(mapOf("selection" to listOf(10, 10))); field.edit(mapOf("text" to "x")) }
        val command = binding.commands.single() as UiCommand.Edit
        assertEquals("x", command.text)
        assertEquals(1, command.selection!!.start); assertEquals(1, command.selection!!.end)
        failure(ComposeUiErrorCodes.INVALID_ARGUMENT) { field.edit(mapOf("text" to null, "selection" to listOf(0, 0))) }

        val first = graph.create(C.BUTTON); val second = graph.create(C.BUTTON)
        val root = graph.create(C.ROW, children = listOf(first, second))
        val callback = function()
        failure(ComposeUiErrorCodes.LIMIT_EXCEEDED) {
            graph.transaction {
                repeat(ComposeUiLimits.MAX_CALLBACKS_PER_SESSION / 2 + 1) { first.on(E.CLICK, callback); second.on(E.CLICK, callback) }
            }
        }
        assertEquals(0, graph.snapshot(root).scriptCallbackCount)
        assertTrue(graph.snapshot(root).callbacks.isEmpty())
    }

    @Test fun renderReusesActualKeyedHandlesAndPreservesManualNodesAndRollback() {
        val manual = text("manual", "manual")
        val root = graph.render(null) { graph.create(C.COLUMN, children = listOf(text("a", "a"), text("b", "b"), manual)) }
        val a = root.children[0]; val b = root.children[1]
        val binding = Binding(); graph.mount(root, binding); graph.accept(binding, binding.frames.last())
        val oldId = a.nodeId
        val next = graph.render(root) { graph.create(C.COLUMN, children = listOf(text("B", "b"), text("A", "a"), manual)) }
        assertSame(root, next); assertEquals(listOf(b, a, manual), next.children)
        assertEquals("A", a.get(P.TEXT)); assertEquals(oldId, a.nodeId)
        assertSame(root, manual.parent)
        val count = binding.frames.size
        try {
            graph.render(root) { graph.create(C.ROW, children = manual); manual.set(mapOf(P.TEXT to "discard")); error("render failed") }
            fail()
        } catch (_: IllegalStateException) { }
        assertEquals("manual", manual.get(P.TEXT)); assertSame(root, manual.parent)
        assertEquals(listOf(b, a, manual), root.children); assertEquals(count, binding.frames.size)
        failure(ComposeUiErrorCodes.DUPLICATE_KEY) {
            graph.render(root) { graph.create(C.COLUMN, children = listOf(text("x", "same"), text("y", "same"))) }
        }
        assertEquals(listOf(b, a, manual), root.children)
    }

    @Test fun renderMatchesUnkeyedSameTypeIndicesAndWarnsOnlyOnce() {
        val first = graph.render(null) { graph.create(C.COLUMN, children = listOf(text("a"), graph.create(C.BUTTON), text("b"))) }
        val a = first.children[0]; val button = first.children[1]; val b = first.children[2]
        val second = graph.render(first) { graph.create(C.COLUMN, children = listOf(graph.create(C.BUTTON), text("A"), text("B"))) }
        assertSame(first, second); assertEquals(listOf(button, a, b), second.children)
        assertEquals(1, warnings.size)
    }

    @Test fun keyWarningsBelongToEachSessionAndSurviveItsRootTypeReplacement() {
        fun draft(type: String = C.COLUMN) = graph.create(type, children = listOf(text("a"), text("b")))
        val first = graph.render(null) { draft() }
        assertEquals(1, warnings.size)
        val a = Binding(); graph.mount(first, a)
        val replaced = graph.render(first) { draft(C.ROW) }
        graph.render(replaced) { draft(C.COLUMN) }
        assertEquals(1, warnings.size)
        val second = graph.render(null) { draft() }
        assertEquals(2, warnings.size)
        val b = Binding(); graph.mount(second, b)
        graph.render(second) { draft(C.ROW) }
        assertEquals(2, warnings.size)
    }

    @Test fun limitsAndFailedPublisherLeaveLocalSnapshotsUnchanged() {
        val root = graph.create(C.COLUMN, children = text("a"))
        val binding = Binding(); graph.mount(root, binding)
        binding.failure = ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        failure(ComposeUiErrorCodes.LIMIT_EXCEEDED) { root.append(text("b")) }
        assertEquals(1, root.children.size)
        failure(ComposeUiErrorCodes.LIMIT_EXCEEDED) {
            graph.transaction {
                var deep = text("deep")
                repeat(ComposeUiLimits.MAX_DEPTH) { deep = graph.create(C.COLUMN, children = deep) }
            }
        }
        assertEquals(1, root.children.size)
    }
}
