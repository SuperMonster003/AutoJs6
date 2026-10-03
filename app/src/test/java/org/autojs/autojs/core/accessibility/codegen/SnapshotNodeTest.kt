package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.NodeInfoSnapshot
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.accessibility.inspector.InspectorNodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SnapshotNodeTest {

    private fun fixture(name: String): SnapshotNode =
        SnapshotNode.load(File(requireNotNull(javaClass.getResource("/codegen/" + name + ".layout.json")).toURI()))

    private fun selector() = UiSelector(null as AccessibilityBridge?)

    @Test
    fun loginLoadsWithoutAndroidAndRetainsProperties() {
        val root = fixture("login")
        assertNull(root.parent())
        assertEquals(3, root.childCount())
        assertEquals(720, root.width())
        assertEquals(640, root.centerY())
        val input = root.child(0)!!
        assertNull(input.id())
        assertEquals("用户名", input.desc())
        assertTrue(input.editable())
        assertTrue(input.hasAction("SET_TEXT"))
        assertEquals(1, input.depth())
        assertEquals(0, input.indexInParent())
        assertSame(root, input.parent())
        assertSame(input, root.child(0))
        assertEquals("登录", root.child(-1)!!.text())
        assertSame(root.child(0), root.child(-3))
        assertNull(root.child(-4))
        assertNull(root.child(3))
        assertNull(root.child(Int.MIN_VALUE))
        assertFalse(input.performAction(0))
        input.recycle()
        assertSame(input, root.child(0))
    }

    @Test
    fun duplicateListHasTwentyRowsInScriptPreOrder() {
        val root = fixture("list-duplicates")
        val traversal = InspectorNodes.preOrder(root)
        assertEquals(61, traversal.size)
        assertEquals(20, root.childCount())
        assertEquals(20, root.rowCount())
        val buttons = selector().text("删除").searchIn(root)
        assertEquals(20, buttons.size)
        for (i in 0 until 20) {
            val row = root.child(i)!!
            assertSame(root, row.parent())
            assertSame(row, traversal[1 + 3 * i])
            assertSame(row.child(0), traversal[2 + 3 * i])
            assertSame(row.child(1), traversal[3 + 3 * i])
            assertSame(row, buttons[i].parent())
            assertSame(row.child(1), buttons[i])
            assertEquals(i, row.indexInParent())
        }
    }

    @Test
    fun fullIdsDistinguishPackagesWhileEntryIdsMatchBoth() {
        val root = fixture("cross-package-id")
        assertEquals("android:id/title", root.child(0)!!.id())
        assertEquals(2, selector().id("title").searchIn(root).size)
        assertEquals(listOf(root.child(0)), selector().id("android:id/title").searchIn(root))
        assertEquals(listOf(root.child(1)), selector().id("org.autojs.test.codegen.fixture:id/title").searchIn(root))
    }

    @Test
    fun uncapturedPropertiesAndLegacyDefaultsStayNeutral() {
        val root = SnapshotNode(NodeInfoSnapshot(id = "entry-only", childCount = 12, depth = 7, indexInParent = 4))
        assertNull(root.id())
        assertEquals(0, root.childCount())
        assertEquals(7, root.depth())
        assertEquals(4, root.indexInParent())
        assertEquals(-1, root.row())
        assertEquals(0, root.columnCount())
        assertEquals("", root.text())
        assertFalse(root.password())
        assertFalse(root.contentInvalid())
        assertFalse(root.multiLine())
    }

    @Test
    fun deeplyNestedFiveThousandNodeTreeDoesNotUseRecursiveConstruction() {
        var snapshot = NodeInfoSnapshot(text = "4999", depth = 4999, indexInParent = 0)
        for (depth in 4998 downTo 0) {
            snapshot = NodeInfoSnapshot(text = depth.toString(), depth = depth, indexInParent = if (depth == 0) -1 else 0, children = listOf(snapshot))
        }
        val nodes = InspectorNodes.preOrder(SnapshotNode(snapshot))
        assertEquals(5000, nodes.size)
        assertEquals("4999", nodes.last().text())
        assertSame(nodes[4998], nodes.last().parent())
    }

    @Test
    fun malformedEnvelopeAndBoundsAreRejectedAtLoadTime() {
        val path = Files.createTempFile("codegen-layout-", ".json")
        try {
            for (content in listOf("{}", "[]", """{"root":null}""", """{"root":{"bounds":[1]}}""")) {
                Files.writeString(path, content)
                assertThrows(IllegalArgumentException::class.java) { SnapshotNode.load(path.toFile()) }
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
