package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.NodeInfoSnapshot
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Attr
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Receiver
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Cardinality
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Rejected
import org.autojs.autojs.core.accessibility.codegen.verify.RejectionReason
import org.autojs.autojs.core.accessibility.codegen.verify.Verification
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.NodeRecyclePolicy
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class PlanVerifierTest {
    private fun spec(vararg conditions: Pair<String, Any>) = SelectorSpec(conditions.map { Condition(it.first, it.second) })
    private fun self(spec: SelectorSpec, all: Boolean = false, receiver: PlanReceiver = PlanReceiver.Self) =
        LocatePlan(Family.SELF, spec, cardinality = if (all) Cardinality.ALL else Cardinality.ONE, receiver = receiver)

    private fun listTree() = FakeUiNode.root {
        id = "fixture:id/list"
        repeat(3) { index ->
            child("row$index", clickable = true) {
                child("label$index") { text = "Item $index" }
                child("button$index", clickable = true) { text = "Delete"; id = "fixture:id/delete" }
            }
        }
    }

    private fun <N : UiNode<N>> verified(result: Verification<N>): Verified<N> {
        assertTrue("Expected verified plan, got $result", result is Verified)
        return result as Verified<N>
    }

    private fun rejected(result: Verification<*>, reason: RejectionReason): Rejected {
        assertTrue("Expected rejected plan, got $result", result is Rejected)
        return (result as Rejected).also { assertEquals(reason, it.reason) }
    }

    @Test
    fun selfUsesFirstResultInsteadOfTreatingAnyHitOrUniquenessAsSuccess() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val plan = self(spec("text" to "Delete"))
        val first = verified(verifier.verify(CodeGenRequest(listOf(root.find("button0"))), plan))
        assertSame(root.find("button0"), first.resolved.single())
        assertEquals(3, first.facts.selectorMatchCount)
        rejected(verifier.verify(CodeGenRequest(listOf(root.find("button1"))), plan), RejectionReason.WRONG_TARGET)
        rejected(verifier.verify(CodeGenRequest(listOf(root.find("button0"))), self(spec("text" to "missing"))), RejectionReason.EMPTY_RESULT)
    }

    @Test
    fun positiveNegativeAndOutOfRangeIndicesMatchFindOnceSemantics() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val request = CodeGenRequest(listOf(root.find("button1")))
        fun plan(index: Int) = LocatePlan(Family.SELF_INDEX, spec("text" to "Delete"), listOf(PlanStep.Index(index)))
        val positive = verified(verifier.verify(request, plan(1)))
        val negative = verified(verifier.verify(request, plan(-2)))
        assertEquals(1, positive.facts.index)
        assertEquals(-2, negative.facts.index)
        assertEquals(1, negative.facts.resolvedIndex)
        assertEquals(positive.resolved, negative.resolved)
        rejected(verifier.verify(request, plan(0)), RejectionReason.WRONG_TARGET)
        for (index in listOf(3, -4, Int.MAX_VALUE, Int.MIN_VALUE)) {
            rejected(verifier.verify(request, plan(index)), RejectionReason.INDEX_OUT_OF_RANGE)
        }
    }

    @Test
    fun collectionRequiresExactlyTheExpectedSetRegardlessOfOrder() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val buttons = (0..2).map { root.find("button$it") }
        val all = self(spec("text" to "Delete"), all = true)
        val request = CodeGenRequest(buttons.reversed(), Scope.COLLECTION)
        assertEquals(buttons, verified(verifier.verify(request, all)).resolved)
        val extra = rejected(verifier.verify(request.copy(targets = buttons.take(2)), all), RejectionReason.EXTRA_TARGETS)
        assertEquals(1, extra.extraCount)
        assertEquals(0, extra.missingCount)
        val subset = self(spec("text" to "Delete", "indexInParent" to 1, "depth" to 2, "id" to "delete", "clickable" to false), all = true)
        rejected(verifier.verify(request, subset), RejectionReason.EMPTY_RESULT)
        val missed = rejected(verifier.verify(request, self(spec("text" to "Item 0"), all = true)), RejectionReason.TARGET_SET_MISMATCH)
        assertEquals(1, missed.extraCount)
        assertEquals(3, missed.missingCount)
        val expected = request.copy(targets = buttons + root.find("label0"))
        val missing = rejected(verifier.verify(expected, all), RejectionReason.MISSING_TARGETS)
        assertEquals(1, missing.missingCount)
    }

    @Test
    fun anchorSearchIncludesTheAnchorAndTakesItsFirstMatch() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val plan = LocatePlan(Family.ANCHOR_SEARCH, spec("id" to "list"), listOf(PlanStep.Search(SelectorSpec())))
        val result = verified(verifier.verify(CodeGenRequest(listOf(root)), plan))
        assertSame(root, result.resolved.single())
        assertEquals(10, result.facts.selectorMatchCount)
        assertEquals(1, result.facts.anchorMatchCount)
        rejected(verifier.verify(CodeGenRequest(listOf(root.find("row0"))), plan), RejectionReason.WRONG_TARGET)
    }

    @Test
    fun containerSearchIncludesTheContainerAndRejectsAmbiguousAnchors() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val plan = LocatePlan(Family.CONTAINER_ITEMS, spec("id" to "list"), listOf(PlanStep.Search(SelectorSpec())), Cardinality.ALL)
        val request = CodeGenRequest(root.descendantsAndSelf(), Scope.COLLECTION)
        val result = verified(verifier.verify(request, plan))
        assertEquals(root.descendantsAndSelf(), result.resolved)
        assertEquals(10, result.facts.containerItemCount)
        val ambiguous = LocatePlan(Family.ANCHOR_SEARCH, spec("text" to "Delete"), listOf(PlanStep.Search(SelectorSpec())))
        rejected(verifier.verify(CodeGenRequest(listOf(root.find("button0"))), ambiguous), RejectionReason.AMBIGUOUS_ANCHOR)
        val absent = LocatePlan(Family.ANCHOR_SEARCH, spec("id" to "missing"), listOf(PlanStep.Search(SelectorSpec())))
        rejected(verifier.verify(CodeGenRequest(listOf(root)), absent), RejectionReason.ANCHOR_NOT_FOUND)
    }

    @Test
    fun compassPathUsesZeroAndNegativeIndicesAndCountsTheActualRelations() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val plan = LocatePlan(Family.ANCHOR_PATH, spec("text" to "Item 1"), listOf(PlanStep.Up(1), PlanStep.Child(-1)))
        val result = verified(verifier.verify(CodeGenRequest(listOf(root.find("button1"))), plan))
        assertEquals(2, result.facts.pathSteps)
        assertEquals(1, result.facts.pathUpLevels)
        assertEquals(1, result.facts.pathChildSteps)
        assertEquals(2, result.facts.anchorDistance)
        val firstChild = LocatePlan(Family.ANCHOR_PATH, spec("id" to "list"), listOf(PlanStep.Child(0), PlanStep.Child(0)))
        verified(verifier.verify(CodeGenRequest(listOf(root.find("label0"))), firstChild))
        val badPath = LocatePlan(Family.ANCHOR_PATH, spec("id" to "list"), listOf(PlanStep.Child(3)))
        rejected(verifier.verify(CodeGenRequest(listOf(root)), badPath), RejectionReason.UNREACHABLE_PATH)
    }

    @Test
    fun relativePlansKeepEntryMatchesWhoseNeighbourSubtreeContainsTheMatch() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val request = CodeGenRequest(listOf(root.find("button1")))
        val byRow = LocatePlan(Family.SELF_RELATIVE, spec("text" to "Delete"), listOf(PlanStep.Up(1), PlanStep.Match(spec("text" to "Item 1"))))
        val result = verified(verifier.verify(request, byRow))
        assertSame(root.find("button1"), result.resolved.single())
        assertEquals(3, result.facts.selectorMatchCount)
        assertEquals(1, result.facts.relativeMatchCount)
        assertEquals(1, result.facts.pathSteps)
        assertEquals(1, result.facts.pathUpLevels)
        assertEquals(0, result.facts.pathSiblingSteps)
        assertEquals(0, result.facts.anchorMatchCount)
        val bySibling = LocatePlan(Family.SELF_RELATIVE, spec("text" to "Delete"), listOf(PlanStep.Sibling(-1), PlanStep.Match(spec("text" to "Item 1"))))
        val sibling = verified(verifier.verify(request, bySibling))
        assertSame(root.find("button1"), sibling.resolved.single())
        assertEquals(1, sibling.facts.pathSiblingSteps)
        assertEquals(1, sibling.facts.pathMaxChildIndex)
        // A match every neighbour satisfies keeps all entries and takes the first in pre-order.
        val broad = LocatePlan(Family.SELF_RELATIVE, spec("text" to "Delete"), listOf(PlanStep.Up(1), PlanStep.Match(spec("text" to "Delete"))))
        rejected(verifier.verify(request, broad), RejectionReason.WRONG_TARGET)
        assertEquals(3, verified(verifier.verify(CodeGenRequest(listOf(root.find("button0"))), broad)).facts.relativeMatchCount)
        // Unreachable relations skip the entry instead of failing the plan; nothing kept is an empty result.
        val unreachable = LocatePlan(Family.SELF_RELATIVE, spec("text" to "Delete"), listOf(PlanStep.Up(5), PlanStep.Match(spec("text" to "Item 1"))))
        rejected(verifier.verify(request, unreachable), RejectionReason.EMPTY_RESULT)
        val forward = LocatePlan(Family.SELF_RELATIVE, spec("text" to "Delete"), listOf(PlanStep.Sibling(1), PlanStep.Match(spec("text" to "Item 1"))))
        rejected(verifier.verify(request, forward), RejectionReason.EMPTY_RESULT)
        rejected(verifier.verify(request, LocatePlan(Family.SELF_RELATIVE, spec("text" to "Nothing"), byRow.steps)), RejectionReason.EMPTY_RESULT)
        assertEquals(listOf(PlanStep.Up(1)), byRow.relation)
        assertEquals(spec("text" to "Item 1"), byRow.relativeMatch)
        assertFalse(root.descendantsAndSelf().any { it.isRecycled })
    }

    @Test
    fun relativeCollectionsKeepEveryAcceptedEntryInPreorder() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val all = CodeGenRequest(listOf(root.find("button2"), root.find("button0"), root.find("button1")), Scope.COLLECTION)
        val everyRow = LocatePlan(Family.SELF_RELATIVE, spec("text" to "Delete"), listOf(PlanStep.Up(1), PlanStep.Match(spec("text" to "Delete"))), Cardinality.ALL)
        val result = verified(verifier.verify(all, everyRow))
        assertEquals(listOf(root.find("button0"), root.find("button1"), root.find("button2")), result.resolved)
        assertEquals(3, result.facts.relativeMatchCount)
        val oneRow = LocatePlan(Family.SELF_RELATIVE, spec("text" to "Delete"), listOf(PlanStep.Up(1), PlanStep.Match(spec("text" to "Item 0"))), Cardinality.ALL)
        rejected(verifier.verify(all, oneRow), RejectionReason.MISSING_TARGETS)
        rejected(verifier.verify(all, LocatePlan(Family.SELF_RELATIVE, spec("text" to "Delete"), oneRow.steps)), RejectionReason.CARDINALITY_MISMATCH)
    }

    @Test
    fun navigationNeverEscapesTheCapturedRootEvenIfTheSourceHasAParent() {
        val outside = listTree()
        val captured = outside.find("row0")
        val verifier = PlanVerifier(captured)
        val plan = LocatePlan(Family.ANCHOR_PATH, spec("text" to "Item 0"), listOf(PlanStep.Up(2), PlanStep.Child(0), PlanStep.Child(0)))
        rejected(verifier.verify(CodeGenRequest(listOf(outside.find("label0"))), plan), RejectionReason.UNREACHABLE_PATH)
        rejected(verifier.verify(CodeGenRequest(listOf(outside.find("row1"))), self(SelectorSpec())), RejectionReason.TARGET_OUTSIDE_CAPTURE)
        assertNull(verifier.tree.parent(captured))
        assertEquals(0, verifier.tree.depth(captured))
    }

    @Test
    fun allAttributeSwitchesApplyToEntryAndNestedSelectors() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val target = root.find("label0")
        val request = CodeGenRequest(listOf(target), attributes = emptySet())
        for ((attr, value) in listOf("id" to "list", "text" to "Item 0", "desc" to "label", "textContains" to "Item", "content" to "Item 0")) {
            rejected(verifier.verify(request, self(spec(attr to value))), RejectionReason.FORBIDDEN_ATTRIBUTE)
            val nested = LocatePlan(Family.ANCHOR_SEARCH, SelectorSpec(), listOf(PlanStep.Search(spec(attr to value))))
            rejected(verifier.verify(request, nested), RejectionReason.FORBIDDEN_ATTRIBUTE)
        }
        val noText = request.copy(attributes = setOf(Attr.ID, Attr.DESC))
        rejected(verifier.verify(noText, self(spec("content" to "Item 0"))), RejectionReason.FORBIDDEN_ATTRIBUTE)
    }

    @Test
    fun autoRequiresCapableSelfOrAncestorButExplicitSelfAllowsIntentionalActions() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val target = root.find("label0")
        val request = CodeGenRequest(listOf(target), action = Action.CLICK)
        val plan = self(spec("text" to "Item 0"))
        rejected(verifier.verify(request, plan), RejectionReason.UNSUPPORTED_ACTION)
        val up = self(plan.selector, receiver = PlanReceiver.Up(1))
        assertEquals(1, verified(verifier.verify(request, up)).facts.receiverUpLevels)
        verified(verifier.verify(request.copy(receiver = Receiver.SELF), plan))
        rejected(verifier.verify(request.copy(receiver = Receiver.SELF), up), RejectionReason.RECEIVER_STRATEGY_MISMATCH)
        val incapable = self(plan.selector, receiver = PlanReceiver.Up(2))
        rejected(verifier.verify(request, incapable), RejectionReason.UNSUPPORTED_ACTION)
        val absent = self(plan.selector, receiver = PlanReceiver.Up(3))
        rejected(verifier.verify(request, absent), RejectionReason.RECEIVER_NOT_FOUND)
        rejected(verifier.verify(request.copy(action = Action.NONE), up), RejectionReason.RECEIVER_STRATEGY_MISMATCH)
    }

    @Test
    fun actionCapabilitiesAreCheckedForEveryMemberOfACollection() {
        val root = listTree()
        val buttons = (0..2).map { root.find("button$it") }
        buttons[1].clickable = false
        val verifier = PlanVerifier(root)
        val request = CodeGenRequest(buttons, Scope.COLLECTION, action = Action.CLICK)
        val plan = self(spec("text" to "Delete"), all = true)
        rejected(verifier.verify(request, plan), RejectionReason.UNSUPPORTED_ACTION)
        verified(verifier.verify(request.copy(receiver = Receiver.SELF), plan))
        for (action in listOf(Action.LONG_CLICK, Action.SCROLL_FORWARD, Action.SCROLL_BACKWARD)) {
            rejected(verifier.verify(request.copy(action = action), plan), RejectionReason.UNSUPPORTED_ACTION)
        }
    }

    @Test
    fun supportedActionsAndTextArgumentsLeaveTheCapturedTreeUntouched() {
        val root = FakeUiNode.root {
            text = "input"
            clickable = true
            longClickable = true
            editable = true
            scrollable = true
        }
        val verifier = PlanVerifier(root)
        for (action in listOf(Action.CLICK, Action.LONG_CLICK, Action.SET_TEXT("a\"b\\c"), Action.SCROLL_FORWARD, Action.SCROLL_BACKWARD)) {
            verified(verifier.verify(CodeGenRequest(listOf(root), action = action), self(spec("text" to "input"))))
        }
        assertEquals("input", root.text)
        assertTrue(root.performedActions.isEmpty())
    }

    @Test
    fun invalidRequestsAndCardinalitiesHaveSpecificReasons() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        rejected(verifier.verify(CodeGenRequest(emptyList()), self(SelectorSpec())), RejectionReason.INVALID_REQUEST)
        val request = CodeGenRequest(listOf(root), mode = CodeGenRequest.Mode.WAIT_TIMEOUT(99))
        rejected(verifier.verify(request, self(SelectorSpec())), RejectionReason.INVALID_REQUEST)
        rejected(verifier.verify(CodeGenRequest(listOf(root)), self(SelectorSpec(), all = true)), RejectionReason.CARDINALITY_MISMATCH)
    }

    @Test
    fun matchesAndResolvedOrderAgreeWithTheActualSelectorImplementation() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        val noRecycle = NodeLifecycle(NodeRecyclePolicy.NO_OP)
        val pairs = listOf(
            spec("id" to "delete") to UiSelector(null).id("delete"),
            spec("text" to "Delete") to UiSelector(null).text("Delete"),
            spec("clickable" to true, "depth" to 1) to UiSelector(null).clickable(true).depth(1),
            SelectorSpec() to UiSelector(null),
        )
        for ((conditions, selector) in pairs) {
            val expected = selector.searchIn(root, lifecycle = noRecycle)
            val actual = verifier.match(conditions)
            assertEquals(expected.size, actual.size)
            expected.indices.forEach { assertSame(expected[it], actual[it]) }
            assertEquals(expected, verified(verifier.verify(CodeGenRequest(expected, Scope.COLLECTION), self(conditions, all = true))).resolved)
        }
        val subtree = root.find("row0")
        assertEquals(UiSelector(null).searchIn(subtree, lifecycle = noRecycle), verifier.match(SelectorSpec(), subtree))
        assertTrue(root.descendantsAndSelf().none { it.isRecycled })
    }

    @Test
    fun repeatedQueriesRetainNodesAndReturnImmutableLists() {
        val root = listTree()
        val verifier = PlanVerifier(root)
        repeat(3) {
            verifier.match(spec("text" to "Item $it"))
            verifier.match(spec("text" to "Delete"))
            verifier.match(spec("text" to "missing"))
        }
        assertTrue(root.descendantsAndSelf().none { it.isRecycled })
        assertThrows(UnsupportedOperationException::class.java) { (verifier.match(SelectorSpec()) as MutableList).clear() }
        val result = verified(verifier.verify(CodeGenRequest(listOf(root)), self(SelectorSpec())))
        assertThrows(UnsupportedOperationException::class.java) { (result.resolved as MutableList).clear() }
    }

    @Test
    fun factsIncludeDynamicTextInBothTheAnchorAndTheTarget() {
        val root = FakeUiNode.root {
            text = "Remaining: 01:30"
            child("target") { desc = "2 notifications" }
        }
        val plan = LocatePlan(Family.ANCHOR_SEARCH, spec("text" to root.text), listOf(PlanStep.Search(spec("desc" to "2 notifications"))))
        val facts = verified(PlanVerifier(root).verify(CodeGenRequest(listOf(root.find("target"))), plan)).facts
        assertTrue(facts.dynamicText)
        assertTrue(facts.dynamicDesc)
        assertEquals(2, facts.conditionCount)
        assertEquals(1, facts.anchorDistance)
    }

    @Test
    fun freshWrappersOfTheSameCapturedNodeAreCanonicalizedByNodeIdentity() {
        val source = listTree()
        val root = wrap(source)
        val target = wrap(source.find("button1"))
        val verifier = PlanVerifier(root)
        val plan = LocatePlan(Family.SELF_INDEX, spec("text" to "Delete"), listOf(PlanStep.Index(1)))
        val result = verified(verifier.verify(CodeGenRequest(listOf(target)), plan))
        assertNotSame(target, result.resolved.single())
        assertSame(verifier.canonical(target), result.resolved.single())
        assertEquals(target, result.resolved.single())
        assertEquals(1, verifier.tree.childIndex(target))
        val unrelated = wrap(listTree().find("button1"))
        rejected(verifier.verify(CodeGenRequest(listOf(unrelated)), plan), RejectionReason.TARGET_OUTSIDE_CAPTURE)
    }

    @Test
    fun cancellationIsObservedBeforeConstructionOnCachedMatchesAndDuringTraversal() {
        val root = listTree()
        val cancelled = CancellationSignal().apply { cancel() }
        assertThrows(CodeGenCancelledException::class.java) { PlanVerifier(root, cancelled) }
        val signal = CancellationSignal()
        val verifier = PlanVerifier(root, signal)
        verifier.match(SelectorSpec())
        signal.cancel()
        assertThrows(CodeGenCancelledException::class.java) { verifier.match(SelectorSpec()) }
        assertThrows(CodeGenCancelledException::class.java) { verifier.verify(CodeGenRequest(listOf(root)), self(SelectorSpec())) }

        val midSearch = CancellationSignal()
        val wrapped = wrap(root) { name -> if (name == "text") midSearch.cancel() }
        val interrupted = PlanVerifier(wrapped, midSearch)
        assertThrows(CodeGenCancelledException::class.java) { interrupted.match(spec("text" to "Delete")) }
        assertTrue(root.descendantsAndSelf().none { it.isRecycled })
    }

    @Test
    fun threadInterruptionIsObservedWithoutClearingItsFlag() {
        val signal = CancellationSignal()
        try {
            Thread.currentThread().interrupt()
            assertThrows(CodeGenCancelledException::class.java) { signal.throwIfCancelled() }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun fiveThousandDeepNodesCanBeIndexedMatchedAndVerifiedWithoutRecursiveTraversal() {
        var snapshot = NodeInfoSnapshot(text = "leaf", depth = 4999)
        repeat(4999) { snapshot = NodeInfoSnapshot(children = listOf(snapshot)) }
        val root = SnapshotNode(snapshot)
        val verifier = PlanVerifier(root)
        assertEquals(5000, verifier.nodes.size)
        assertEquals(4999, verifier.tree.depth(verifier.nodes.last()))
        val result = verified(verifier.verify(CodeGenRequest(listOf(verifier.nodes.last())), self(spec("text" to "leaf"))))
        assertSame(verifier.nodes.last(), result.resolved.single())
    }

    // A JVM-only fixture with InspectorNode's navigation and equality semantics: every navigation
    // creates a new wrapper, whose equality denotes the underlying captured node's identity.
    // zh-CN: 复现 InspectorNode 导航及相等语义的纯 JVM 夹具: 导航总是创建新包装, 相等性标识原节点身份.
    private interface WrappedNode : UiNode<WrappedNode>

    private class WrapperHandler(val node: FakeUiNode, val onRead: (String) -> Unit) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            onRead(method.name)
            return when (method.name) {
                "parent" -> node.parent()?.let { wrap(it, onRead) }
                "child" -> node.child(args!![0] as Int)?.let { wrap(it, onRead) }
                "equals" -> args?.firstOrNull()?.let { other ->
                    Proxy.isProxyClass(other.javaClass) && (Proxy.getInvocationHandler(other) as? WrapperHandler)?.node === node
                } ?: false
                "hashCode" -> System.identityHashCode(node)
                "toString" -> "Wrapped($node)"
                else -> method.invoke(node, *(args ?: emptyArray()))
            }
        }
    }

    companion object {
        private fun wrap(node: FakeUiNode, onRead: (String) -> Unit = {}): WrappedNode = Proxy.newProxyInstance(
            WrappedNode::class.java.classLoader,
            arrayOf(WrappedNode::class.java),
            WrapperHandler(node, onRead),
        ) as WrappedNode
    }
}
