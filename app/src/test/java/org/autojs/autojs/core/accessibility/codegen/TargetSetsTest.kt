package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.NodeInfoSnapshot
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TargetSetsTest {
    private fun fixture(name: String): SnapshotNode =
        SnapshotNode.load(File(requireNotNull(javaClass.getResource("/codegen/$name.layout.json")).toURI()))

    @Test
    fun repeatedFixtureProducesTwentyTargetsAndDeduplicatesAllThreeRules() {
        val root = fixture("list-duplicates")
        val suggestions = TargetSets.suggest(root, root.child(3)!!.child(1)!!)
        assertEquals(1, suggestions.size)
        val proposal = suggestions.single()
        assertEquals(TargetSet.Kind.FULL_ID_CLASS, proposal.kind)
        assertEquals(20, proposal.count)
        assertEquals((0 until 20).map { root.child(it)!!.child(1)!! }, proposal.nodes)
        assertEquals(listOf("20"), proposal.description.arguments)
        assertTrue(proposal.description.renderChinese().contains("20"))
        assertThrows(UnsupportedOperationException::class.java) { (proposal.nodes as MutableList<*>).clear() }
    }

    @Test
    fun noRepeatedGroupAndTargetOutsideRootProduceNoSuggestions() {
        val root = fixture("login")
        assertTrue(TargetSets.suggest(root, root.child(2)!!).isEmpty())
        assertTrue(TargetSets.suggest(root, fixture("login").child(2)!!).isEmpty())
    }

    @Test
    fun fullIdGroupDoesNotConflateCrossPackageEntryNamesOrMissingIds() {
        val root = FakeUiNode.root {
            child("first") { id = "app:id/title"; className = "Text" }
            child("second") { id = "android:id/title"; className = "Text" }
            child("third") { id = "app:id/title"; className = "Button" }
        }
        assertTrue(TargetSets.suggest(root, root.find("first")).isEmpty())
        val missingIds = FakeUiNode.root {
            child("first") { className = "Text" }
            child("second") { className = "Button" }
        }
        assertTrue(TargetSets.suggest(missingIds, missingIds.find("first")).isEmpty())
    }

    @Test
    fun threeDistinctSuggestionsPreservePreferenceAndPreorder() {
        val root = FakeUiNode.root {
            child("list") {
                repeat(2) { row ->
                    child("row$row") {
                        className = "Row"
                        child("title$row") { className = "Text"; text = "Item $row" }
                        child("button$row") { className = "Button"; id = "app:id/buy"; text = "Buy" }
                    }
                }
            }
            child("sameId") { className = "Button"; id = "app:id/buy"; text = "Other" }
            child("sameText") { className = "Text"; text = "Buy" }
        }
        val suggestions = TargetSets.suggest(root, root.find("button0"))
        assertEquals(TargetSet.Kind.entries, suggestions.map { it.kind })
        assertEquals(listOf("button0", "button1", "sameId"), suggestions[0].nodes.map { it.name })
        assertEquals(listOf("button0", "button1"), suggestions[1].nodes.map { it.name })
        assertEquals(listOf("button0", "button1", "sameText"), suggestions[2].nodes.map { it.name })
        suggestions.forEach { assertTrue(it.description.renderChinese().isNotEmpty()) }
    }

    @Test
    fun nearestRepeatedContainerWinsAndDynamicTextIsIgnored() {
        val root = FakeUiNode.root {
            repeat(2) { section ->
                child("section$section") {
                    className = "Section"
                    repeat(3) { row ->
                        child("row$section$row") {
                            className = "Row"
                            child("title$section$row") { className = "Text"; text = "Value $section-$row" }
                            child("button$section$row") { className = "Button"; text = "Unique $section-$row" }
                        }
                    }
                }
            }
        }
        val suggestion = TargetSets.suggest(root, root.find("button11")).single()
        assertEquals(TargetSet.Kind.REPEATED_CONTAINER, suggestion.kind)
        assertEquals(listOf("button10", "button11", "button12"), suggestion.nodes.map { it.name })
    }

    @Test
    fun repeatedSubtreeSignatureIncludesOrderedShapeClassAndFullId() {
        val root = FakeUiNode.root {
            repeat(5) { row ->
                child("row$row") {
                    className = if (row == 4) "OtherRow" else "Row"
                    if (row == 2) child("extra") { className = "Label" }
                    child("button$row") { className = "Button"; id = if (row == 3) "other:id/buy" else "app:id/buy" }
                }
            }
        }
        val suggestions = TargetSets.suggest(root, root.find("button0"))
        assertEquals(2, suggestions.size)
        assertEquals(listOf("button0", "button1"), suggestions[1].nodes.map { it.name })
    }

    @Test
    fun emptyTextIsNotACollectionCriterion() {
        val root = FakeUiNode.root {
            child("a") { className = "Button" }
            child("b") { className = "Button" }
            child("c") { className = "Text" }
        }
        val suggestions = TargetSets.suggest(root, root.find("a"))
        assertEquals(listOf(TargetSet.Kind.REPEATED_CONTAINER), suggestions.map { it.kind })
        assertEquals(2, suggestions.single().count)
    }

    @Test
    fun regeneratedWrappersAndIncorrectIndexMetadataDoNotBreakRelativePaths() {
        val raw = SnapshotNode(NodeInfoSnapshot(children = (0..2).map { row ->
            NodeInfoSnapshot(className = "Row", children = listOf(
                NodeInfoSnapshot(className = "Label", text = "Item $row"),
                NodeInfoSnapshot(className = "Button", text = "Action $row"),
            ))
        }))
        val root = Rewrapped(raw)
        val target = root.child(1)!!.child(1)!!
        assertFalse(target === root.child(1)!!.child(1)!!)
        val suggestion = TargetSets.suggest(root, target).single()
        assertEquals(TargetSet.Kind.REPEATED_CONTAINER, suggestion.kind)
        assertEquals((0..2).map { root.child(it)!!.child(1)!! }, suggestion.nodes)
    }

    @Test
    fun deepTreeUsesIterativeTraversalAndSignatures() {
        var snapshot = NodeInfoSnapshot(className = "Leaf", text = "Selected")
        repeat(4999) { snapshot = NodeInfoSnapshot(className = "Wrapper", children = listOf(snapshot)) }
        val root = SnapshotNode(snapshot)
        var target = root
        repeat(4999) { target = target.child(0)!! }
        assertTrue(TargetSets.suggest(root, target).isEmpty())
    }

    /** Fresh wrappers with equality for the underlying node, as used by InspectorNode. */
    private class Rewrapped(private val delegate: SnapshotNode) : UiNode<Rewrapped> by RewrappedDelegate(delegate) {
        override fun parent(): Rewrapped? = delegate.parent()?.let(::Rewrapped)
        override fun child(i: Int): Rewrapped? = delegate.child(i)?.let(::Rewrapped)
        override fun equals(other: Any?): Boolean = other is Rewrapped && other.delegate === delegate
        override fun hashCode(): Int = System.identityHashCode(delegate)
    }

    private class RewrappedDelegate(private val delegate: SnapshotNode) : UiNode<Rewrapped> {
        override fun parent(): Rewrapped? = delegate.parent()?.let(::Rewrapped)
        override fun child(i: Int): Rewrapped? = delegate.child(i)?.let(::Rewrapped)
        override fun childCount() = delegate.childCount()
        override fun indexInParent() = delegate.indexInParent()
        override fun depth() = delegate.depth()
        override fun drawingOrder() = delegate.drawingOrder()
        override fun packageName() = delegate.packageName()
        override fun className() = delegate.className()
        override fun id() = delegate.id()
        override fun idHex() = delegate.idHex()
        override fun text() = delegate.text()
        override fun desc() = delegate.desc()
        override fun left() = delegate.left()
        override fun top() = delegate.top()
        override fun right() = delegate.right()
        override fun bottom() = delegate.bottom()
        override fun width() = delegate.width()
        override fun height() = delegate.height()
        override fun centerX() = delegate.centerX()
        override fun centerY() = delegate.centerY()
        override fun checkable() = delegate.checkable()
        override fun checked() = delegate.checked()
        override fun focusable() = delegate.focusable()
        override fun focused() = delegate.focused()
        override fun visibleToUser() = delegate.visibleToUser()
        override fun accessibilityFocused() = delegate.accessibilityFocused()
        override fun selected() = delegate.selected()
        override fun clickable() = delegate.clickable()
        override fun longClickable() = delegate.longClickable()
        override fun enabled() = delegate.enabled()
        override fun password() = delegate.password()
        override fun scrollable() = delegate.scrollable()
        override fun editable() = delegate.editable()
        override fun contentInvalid() = delegate.contentInvalid()
        override fun contextClickable() = delegate.contextClickable()
        override fun multiLine() = delegate.multiLine()
        override fun dismissable() = delegate.dismissable()
        override fun row() = delegate.row()
        override fun column() = delegate.column()
        override fun rowSpan() = delegate.rowSpan()
        override fun columnSpan() = delegate.columnSpan()
        override fun rowCount() = delegate.rowCount()
        override fun columnCount() = delegate.columnCount()
        override fun actionNames() = delegate.actionNames()
        override fun performAction(action: Int, vararg arguments: org.autojs.autojs.core.automator.ActionArgument) = false
        override fun recycle() = Unit
    }
}
