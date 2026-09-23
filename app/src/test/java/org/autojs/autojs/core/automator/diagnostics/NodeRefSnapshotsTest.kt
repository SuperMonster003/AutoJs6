package org.autojs.autojs.core.automator.diagnostics

import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeRefSnapshotsTest {
    @Test fun eightSnapshotsUseAccessOrderAndBareReferencesUseTheNewestDump() {
        val store = NodeRefSnapshots()
        val snapshots = (1..8).map { register(store, "text$it") }
        store.resolve("#n1", snapshots[0].id)
        val latest = register(store, "latest")
        assertEquals(8, store.size)
        assertEquals(snapshots[0].id, store.resolve("#n1", snapshots[0].id).snapshot.id)
        stale { store.resolve("#n1", snapshots[1].id) }
        assertEquals(latest.id, store.resolve("#n1").snapshot.id)
        store.clear()
        stale { store.resolve("#n1", latest.id) }
    }

    @Test fun referencesAreIsolatedAcrossProviderScopes() {
        val first = NodeRefSnapshots()
        val second = NodeRefSnapshots()
        val snapshot = register(first, "same")
        register(second, "same")
        stale { second.resolve("#n1", snapshot.id) }
        val reference = first.resolve("#n1", snapshot.id)
        stale { second.relocate(reference, listOf(node("same")), "app/window1") { true } }
    }

    @Test fun closingScopeCannotBeRepopulatedByALateDump() {
        val store = NodeRefSnapshots()
        register(store, "button")
        store.close()
        assertEquals(0, store.size)
        stale { register(store, "late") }
        stale { store.resolve("#n1") }
    }

    @Test fun uniqueFingerprintMovesWithinToleranceWithoutDependingOnOldNodeObjects() {
        val store = NodeRefSnapshots()
        register(store, "button")
        val current = node("button").apply { bounds(20, 25, 120, 125) }
        val result = store.relocate(store.resolve("#n1"), listOf(current), "app/window1") { it.text() }
        assertEquals("button", result)
        current.bounds(100, 100, 200, 200)
        stale { store.relocate(store.resolve("#n1"), listOf(current), "app/window1") { true } }
        current.bounds(0, 0, 100, 100)
        current.text = "changed"
        stale { store.relocate(store.resolve("#n1"), listOf(current), "app/window1") { true } }
    }

    @Test fun activityWindowAndPackageChangesCannotRetargetAReference() {
        val store = NodeRefSnapshots()
        register(store, "button")
        stale { store.relocate(store.resolve("#n1"), listOf(node("button")), "app/window2") { true } }
        val otherApp = node("button").apply { packageName = "another.app" }
        stale { store.relocate(store.resolve("#n1"), listOf(otherApp), "app/window1") { true } }
    }

    @Test fun anonymousContainerDoesNotAliasItsSmallerDescendants() {
        val store = NodeRefSnapshots()
        val root = FakeUiNode.root {
            packageName = "app"; className = "FrameLayout"; bounds(0, 746, 1800, 991)
            child("nested") { packageName = "app"; className = "FrameLayout"; bounds(183, 771, 623, 825) }
        }
        store.register(listOf(CompactNodeText.capture(root, 0)), "active", "app/window1")
        val released = ArrayList<FakeUiNode>()
        val matched = store.relocate(store.resolve("#n1"), listOf(root), "app/window1", release = { released += it }) { it }
        assertTrue(matched === root)
        assertEquals(listOf(root.find("nested")), released)
    }

    @Test fun aSmallerReplacementInsideTheOldBoundsCannotReceiveTheAction() {
        val store = NodeRefSnapshots()
        register(store, "button")
        val replacement = node("button").apply { bounds(49, 49, 51, 51) }
        var acted = false
        stale { store.relocate(store.resolve("#n1"), listOf(replacement), "app/window1") { acted = true } }
        assertFalse(acted)
    }

    @Test fun ambiguousFingerprintsFailClosedAndAllBorrowedChildrenAreReleased() {
        val store = NodeRefSnapshots()
        register(store, "button")
        val root = FakeUiNode.root {
            child("a") { packageName = "app"; className = "Button"; text = "button"; bounds(0, 0, 100, 100) }
            child("b") { packageName = "app"; className = "Button"; text = "button"; bounds(5, 5, 105, 105) }
        }
        val released = ArrayList<FakeUiNode>()
        var acted = false
        stale { store.relocate(store.resolve("#n1"), listOf(root), "app/window1", release = { released += it }) { acted = true } }
        assertFalse(acted)
        assertEquals(setOf(root.find("a"), root.find("b")), released.toSet())
        assertEquals(2, released.size)
    }

    @Test fun oversizedIdentitiesMalformedRefsAndEvictionDuringScanAreStale() {
        val store = NodeRefSnapshots(1)
        val reference = store.resolveAfterRegister("button")
        register(store, "replacement")
        stale { store.relocate(reference, listOf(node("button")), "app/window1") { true } }
        listOf("#n0", "#n-1", "#n999999999", "x", "#n2").forEach { stale { store.resolve(it) } }
        val huge = node("x".repeat(CompactNodeText.MAX_IDENTITY_CHARS + 1))
        store.register(listOf(CompactNodeText.capture(huge, 0)), "active", "app/window1")
        stale { store.resolve("#n1") }
    }

    private fun NodeRefSnapshots.resolveAfterRegister(text: String): NodeRefSnapshots.Reference { register(this, text); return resolve("#n1") }
    private fun register(store: NodeRefSnapshots, text: String) =
        store.register(listOf(CompactNodeText.capture(node(text), 0)), "active", "app/window1")
    private fun node(label: String) = FakeUiNode.root {
        packageName = "app"; className = "Button"; text = label; bounds(0, 0, 100, 100)
    }
    private fun stale(block: () -> Any?) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("expected NODE_REF_STALE, received $error", error is NodeRefSnapshots.Stale)
        assertTrue(error!!.message!!.startsWith("NODE_REF_STALE:"))
    }
}
