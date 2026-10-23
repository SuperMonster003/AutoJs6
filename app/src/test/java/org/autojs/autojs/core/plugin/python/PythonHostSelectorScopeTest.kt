package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.NodeRecyclePolicy
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The Python selector scope over a fake tree (roadmap P5.4): the wire protocol, the limits and the
 * retention semantics the Android broker relies on, verified without a device.
 * zh-CN: 假节点树上的 Python 选择器作用域 (Roadmap P5.4): Android broker 依赖的线协议, 限额与保留语义, 无需设备即可验证.
 */
class PythonHostSelectorScopeTest {

    //        root
    //       /    \
    //     a        b
    //   /  \      /  \
    //  a0  a1   b0   b1
    //             \
    //             b1x
    private fun tree() = FakeUiNode.root {
        packageName = "com.example"
        className = "android.widget.FrameLayout"
        bounds(0, 0, 1080, 1920)
        child("a") {
            text = "Login"
            className = "android.widget.Button"
            id = "com.example:id/login"
            clickable = true
            enabled = true
            bounds(10, 20, 110, 70)
            child("a0") { text = "a0"; desc = "first" }
            child("a1") { text = "a1"; editable = true }
        }
        child("b") {
            desc = "Menu"
            scrollable = true
            child("b0")
            child("b1") {
                child("b1x") { text = "deep" }
            }
        }
    }

    private class Platform(var root: FakeUiNode?) : PythonHostSelectorScope.Platform<FakeUiNode> {

        val stale = HashSet<String>()
        val clicked = ArrayList<String>()
        val texts = ArrayList<Pair<String, String>>()
        var failCapture: String? = null

        override fun activeRoot(): FakeUiNode? = root

        override fun capture(node: FakeUiNode): PythonHostSelectorValues {
            check(node.name != failCapture) { "Unable to read ${node.name}" }
            return PythonHostSelectorValues.of(node)
        }

        override fun refresh(node: FakeUiNode): Boolean = node.name !in stale

        override fun click(node: FakeUiNode): Boolean {
            clicked += node.name
            return true
        }

        override fun setText(node: FakeUiNode, text: String): Boolean {
            texts += node.name to text
            return true
        }

    }

    private val recycling = NodeLifecycle(NodeRecyclePolicy.RECYCLE)

    private val noRecycling = NodeLifecycle(NodeRecyclePolicy.NO_OP)

    private fun scope(platform: Platform, lifecycle: NodeLifecycle = noRecycling) = PythonHostSelectorScope(platform, lifecycle)

    @Test
    fun snapshotListsTheTreeBreadthFirstWithIdsParentsAndDepths() {
        val snapshot = scope(Platform(tree())).snapshot(maxNodes = 64, maxDepth = 16)

        assertEquals(1L, snapshot.generation)
        assertFalse(snapshot.truncated)
        assertEquals(listOf("node-1-1", "node-1-2", "node-1-3", "node-1-4", "node-1-5", "node-1-6", "node-1-7", "node-1-8"), snapshot.nodes.map { it.id })
        assertEquals(listOf(null, "node-1-1", "node-1-1", "node-1-2", "node-1-2", "node-1-3", "node-1-3", "node-1-7"), snapshot.nodes.map { it.parentId })
        assertEquals(listOf(0, 1, 1, 2, 2, 2, 2, 3), snapshot.nodes.map { it.depth })
        assertEquals(listOf("", "Login", "", "a0", "a1", "", "", "deep"), snapshot.nodes.map { it.text })
        assertEquals(listOf(2, 2, 2, 0, 0, 0, 1, 0), snapshot.nodes.map { it.childCount })

        val login = snapshot.nodes[1]
        assertEquals("com.example:id/login", login.resourceId)
        assertEquals("android.widget.Button", login.className)
        assertEquals(PythonHostSelectorBounds(10, 20, 110, 70), login.bounds)
        assertTrue(login.clickable)
        assertTrue(login.enabled)
        assertTrue(login.truncatedFields.isEmpty())
        assertEquals("Menu", snapshot.nodes[2].description)
        assertTrue(snapshot.nodes[2].scrollable)
        assertEquals("first", snapshot.nodes[3].description)
        assertNull(snapshot.nodes[3].resourceId)
    }

    @Test
    fun snapshotJsonCarriesTheFrozenSchema() {
        val json = scope(Platform(tree())).snapshot(maxNodes = 2, maxDepth = 16).toJson()

        assertEquals(PythonHostSelectorPolicy.SNAPSHOT_SCHEMA, json.get("schema").asString)
        assertEquals(1L, json.get("generation").asLong)
        assertTrue(json.get("truncated").asBoolean)
        val nodes = json.getAsJsonArray("nodes")
        assertEquals(2, nodes.size())
        val node = nodes[1].asJsonObject
        assertEquals(
            listOf(
                "id", "parentId", "depth", "childCount", "text", "description", "resourceId", "className", "packageName", "bounds",
                "clickable", "editable", "enabled", "focused", "selected", "checkable", "checked", "scrollable", "password", "visibleToUser", "truncatedFields",
            ),
            node.keySet().toList(),
        )
        assertEquals("node-1-2", node.get("id").asString)
        assertEquals("node-1-1", node.get("parentId").asString)
        assertTrue(nodes[0].asJsonObject.get("parentId").isJsonNull)
        assertTrue(nodes[0].asJsonObject.get("resourceId").isJsonNull)
        assertEquals(10, node.getAsJsonObject("bounds").get("left").asInt)
    }

    @Test
    fun snapshotStopsAtTheDepthAndNodeLimitsAndReleasesWhatItDoesNotRetain() {
        val root = tree()
        val platform = Platform(root)

        val shallow = scope(platform, recycling).snapshot(maxNodes = 64, maxDepth = 1)
        assertTrue(shallow.truncated)
        assertEquals(listOf("node-1-1", "node-1-2", "node-1-3"), shallow.nodes.map { it.id })
        // The nodes of the deeper levels were never obtained; the retained ones are alive.
        // zh-CN: 更深层的节点根本未被取得; 保留的节点仍然存活.
        assertFalse(root.isRecycled)
        assertFalse(root.find("a").isRecycled)
        assertFalse(root.find("a0").isRecycled)

        val other = tree()
        val limited = scope(Platform(other), recycling).snapshot(maxNodes = 3, maxDepth = 16)
        assertTrue(limited.truncated)
        assertEquals(3, limited.nodes.size)
        // The pending children of the last retained nodes were obtained and then released.
        // zh-CN: 最后保留的节点已取得的待处理子节点被释放.
        assertTrue(other.find("a0").isRecycled)
        assertTrue(other.find("b1").isRecycled)
        assertFalse(other.find("b1x").isRecycled)
        assertFalse(other.find("b").isRecycled)
    }

    @Test
    fun snapshotReleasesTheNodeWhoseCaptureFails() {
        val root = tree()
        val platform = Platform(root).apply { failCapture = "b" }
        try {
            scope(platform, recycling).snapshot(maxNodes = 64, maxDepth = 16)
            fail("Expected the capture failure to propagate")
        } catch (_: IllegalStateException) {
        }
        // root and a were retained and then let go, b failed while being read, a0 and a1 were still pending,
        // b0, b1 and b1x were never obtained (docs/memory-phase2/ROADMAP.md C44).
        // zh-CN: root 与 a 先被保留后被放开, b 在读取时失败, a0 与 a1 仍在待处理队列中, b0, b1 与 b1x 从未取得
        // (docs/memory-phase2/ROADMAP.md C44).
        assertEquals(
            listOf("root", "a", "a0", "a1", "b"),
            root.descendantsAndSelf().filter { it.isRecycled }.map { it.name },
        )
    }

    @Test
    fun snapshotTruncatesAtTheEncodedSizeLimit() {
        val root = FakeUiNode.root {
            repeat(120) { index ->
                child("c$index") { text = "x".repeat(PythonHostSelectorPolicy.MAX_NODE_TEXT_CODE_POINTS + 10) }
            }
        }
        val snapshot = scope(Platform(root)).snapshot(maxNodes = 128, maxDepth = 16)

        assertTrue(snapshot.truncated)
        assertTrue(snapshot.nodes.size < 121)
        assertTrue(snapshot.toJson().toString().toByteArray().size <= PythonHostSelectorPolicy.MAX_SNAPSHOT_VALUE_BYTES)
        assertEquals(listOf("text"), snapshot.nodes[1].truncatedFields)
        assertEquals(PythonHostSelectorPolicy.MAX_NODE_TEXT_CODE_POINTS, snapshot.nodes[1].text!!.length)
    }

    @Test
    fun everySnapshotAdvancesTheGenerationAndForgetsTheRetainedNodes() {
        val platform = Platform(tree())
        val scope = scope(platform)
        val first = scope.snapshot(maxNodes = 64, maxDepth = 16)
        assertTrue(scope.click(first.nodes[1].id))

        val second = scope.snapshot(maxNodes = 64, maxDepth = 16)
        assertEquals(2L, second.generation)
        assertEquals("node-2-1", second.nodes[0].id)
        try {
            scope.click(first.nodes[1].id)
            fail("Expected the node of the previous generation to be stale")
        } catch (_: PythonHostCapabilityStaleNodeException) {
        }
        assertEquals(listOf("a"), platform.clicked)
    }

    @Test
    fun findMatchesThroughTheRegistryFiltersAndRetainsTheMatch() {
        val platform = Platform(tree())
        val scope = scope(platform)

        val exact = scope.find(PythonHostSelectorQuery(text = "Login"), maxNodes = 512, maxDepth = 16)!!
        assertEquals("node-1-1", exact.id)
        assertNull(exact.parentId)
        assertEquals(1, exact.depth)
        assertEquals("com.example:id/login", exact.resourceId)

        val contains = scope.find(PythonHostSelectorQuery(textContains = "og", clickable = true), maxNodes = 512, maxDepth = 16)!!
        assertEquals("node-1-2", contains.id)
        assertEquals("Login", contains.text)

        val deep = scope.find(PythonHostSelectorQuery(text = "deep"), maxNodes = 512, maxDepth = 16)!!
        assertEquals(3, deep.depth)
        assertEquals(0, deep.childCount)

        assertEquals("first", scope.find(PythonHostSelectorQuery(descriptionContains = "irs"), maxNodes = 512, maxDepth = 16)!!.description)
        assertEquals("Menu", scope.find(PythonHostSelectorQuery(description = "Menu", scrollable = true), maxNodes = 512, maxDepth = 16)!!.description)
        assertEquals("a1", scope.find(PythonHostSelectorQuery(editable = true), maxNodes = 512, maxDepth = 16)!!.text)
        assertEquals("android.widget.Button", scope.find(PythonHostSelectorQuery(className = "android.widget.Button"), maxNodes = 512, maxDepth = 16)!!.className)
        assertEquals("Login", scope.find(PythonHostSelectorQuery(resourceId = "com.example:id/login", enabled = true), maxNodes = 512, maxDepth = 16)!!.text)

        assertTrue(scope.click(exact.id))
        assertTrue(scope.setText(contains.id, "hello"))
        assertEquals(listOf("a"), platform.clicked)
        assertEquals(listOf("a" to "hello"), platform.texts)
    }

    @Test
    fun findComparesLiterallyAndNeverMatchesAMissingProperty() {
        val scope = scope(Platform(tree()))

        // No short-id / short-class-name expansion, unlike the script selector's id() / className().
        // zh-CN: 不做短 id / 短类名展开, 与脚本选择器的 id() / className() 不同.
        assertNull(scope.find(PythonHostSelectorQuery(resourceId = "login"), maxNodes = 512, maxDepth = 16))
        assertNull(scope.find(PythonHostSelectorQuery(className = "Button"), maxNodes = 512, maxDepth = 16))
        assertNull(scope.find(PythonHostSelectorQuery(text = "login"), maxNodes = 512, maxDepth = 16))
        assertNull(scope.find(PythonHostSelectorQuery(description = "", clickable = false), maxNodes = 512, maxDepth = 16))
        assertNull(scope.find(PythonHostSelectorQuery(text = "Login", clickable = false), maxNodes = 512, maxDepth = 16))
    }

    @Test
    fun findReleasesTheVisitedNodesButTheMatch() {
        val root = tree()
        val scope = scope(Platform(root), recycling)

        val match = scope.find(PythonHostSelectorQuery(text = "a1"), maxNodes = 512, maxDepth = 16)!!
        assertEquals("node-1-1", match.id)
        // Visited before the match (level order): root, a, b, a0; the match itself is retained,
        // the nodes still pending (b0, b1) are released, b1x was never obtained.
        // zh-CN: 命中前访问过 (按层序): root, a, b, a0; 命中节点本身被保留, 仍待处理的 (b0, b1) 被释放, b1x 从未取得.
        assertTrue(root.isRecycled)
        assertTrue(root.find("a").isRecycled)
        assertTrue(root.find("b").isRecycled)
        assertTrue(root.find("a0").isRecycled)
        assertFalse(root.find("a1").isRecycled)
        assertTrue(root.find("b0").isRecycled)
        assertTrue(root.find("b1").isRecycled)
        assertFalse(root.find("b1x").isRecycled)

        val fresh = tree()
        assertNull(scope(Platform(fresh), recycling).find(PythonHostSelectorQuery(text = "nowhere"), maxNodes = 512, maxDepth = 16))
        assertTrue(fresh.descendantsAndSelf().all { it.isRecycled })
    }

    @Test
    fun findReportsTheScanLimits() {
        val scope = scope(Platform(tree()))

        try {
            scope.find(PythonHostSelectorQuery(text = "deep"), maxNodes = 3, maxDepth = 16)
            fail("Expected the node limit to be reported")
        } catch (_: PythonHostCapabilitySelectorScanLimitException) {
        }
        try {
            scope.find(PythonHostSelectorQuery(text = "deep"), maxNodes = 512, maxDepth = 2)
            fail("Expected the depth limit to be reported")
        } catch (_: PythonHostCapabilitySelectorScanLimitException) {
        }
        // A match within the limits is not affected by what lies beyond them.
        // zh-CN: 限额内的命中不受限额之外的部分影响.
        assertEquals("Login", scope.find(PythonHostSelectorQuery(text = "Login"), maxNodes = 2, maxDepth = 1)!!.text)
        // Nothing beyond the depth means no truncation.
        // zh-CN: 深度之外没有节点即不算截断.
        assertNull(scope.find(PythonHostSelectorQuery(text = "nowhere"), maxNodes = 512, maxDepth = 3))
    }

    @Test
    fun retainedNodesGoStaleWhenTheyCannotBeRefreshedOrAreUnknown() {
        val root = tree()
        val platform = Platform(root)
        val scope = scope(platform, recycling)
        val login = scope.find(PythonHostSelectorQuery(text = "Login"), maxNodes = 512, maxDepth = 16)!!

        platform.stale += "a"
        try {
            scope.click(login.id)
            fail("Expected a stale node")
        } catch (_: PythonHostCapabilityStaleNodeException) {
        }
        assertTrue(root.find("a").isRecycled)
        platform.stale -= "a"
        try {
            scope.click(login.id)
            fail("Expected the released node to stay unknown")
        } catch (_: PythonHostCapabilityStaleNodeException) {
        }
        try {
            scope.setText("node-1-99", "x")
            fail("Expected an unknown node id to be stale")
        } catch (_: PythonHostCapabilityStaleNodeException) {
        }
        assertTrue(platform.clicked.isEmpty())
    }

    @Test
    fun retentionEvictsTheOldestNodeBeyondTheLimit() {
        val root = FakeUiNode.root {
            repeat(PythonHostSelectorPolicy.MAX_RETAINED_NODES + 1) { index -> child("c$index") { text = "t$index" } }
        }
        // Without recycling: the fake tree hands out the same child objects on every walk, so the
        // release of a visited node would mark a node another find has retained (a platform node is
        // a fresh object per walk).
        // zh-CN: 不回收: 假节点树每次遍历交出的是同一批子对象, 访问后的释放会标记另一次查找保留的节点 (平台节点每次遍历都是新对象).
        val platform = Platform(root)
        val scope = scope(platform)
        val ids = (0..PythonHostSelectorPolicy.MAX_RETAINED_NODES).map { index ->
            scope.find(PythonHostSelectorQuery(text = "t$index"), maxNodes = 1024, maxDepth = 16)!!.id
        }

        assertEquals("node-1-1", ids.first())
        assertEquals("node-1-${PythonHostSelectorPolicy.MAX_RETAINED_NODES + 1}", ids.last())
        try {
            scope.click(ids.first())
            fail("Expected the evicted node to be stale")
        } catch (_: PythonHostCapabilityStaleNodeException) {
        }
        assertTrue(scope.click(ids[1]))
        assertEquals(listOf("c1"), platform.clicked)
    }

    @Test
    fun missingRootMeansTheServiceIsUnavailable() {
        val scope = scope(Platform(null))
        try {
            scope.snapshot(maxNodes = 64, maxDepth = 16)
            fail("Expected the accessibility service to be reported unavailable")
        } catch (_: PythonHostCapabilityAccessibilityUnavailableException) {
        }
        try {
            scope.find(PythonHostSelectorQuery(text = "x"), maxNodes = 64, maxDepth = 16)
            fail("Expected the accessibility service to be reported unavailable")
        } catch (_: PythonHostCapabilityAccessibilityUnavailableException) {
        }
    }

    @Test
    fun closeReleasesTheRetainedNodes() {
        val root = tree()
        val scope = scope(Platform(root), recycling)
        scope.snapshot(maxNodes = 64, maxDepth = 16)
        assertFalse(root.find("a").isRecycled)

        scope.close()
        assertTrue(root.descendantsAndSelf().all { it.isRecycled })
        try {
            scope.click("node-1-2")
            fail("Expected the released node to be stale")
        } catch (_: PythonHostCapabilityStaleNodeException) {
        }
    }

    @Test
    fun valuesReadEveryWireFieldThroughUiNode() {
        val node = FakeUiNode.root {
            text = "T"
            desc = "D"
            id = "p:id/x"
            className = "C"
            packageName = "P"
            bounds(5, 6, 7, 8)
            clickable = true
            editable = true
            enabled = false
            focused = true
            selected = true
            checkable = true
            checked = true
            scrollable = true
            password = true
            visibleToUser = false
            child("k")
        }
        val values = PythonHostSelectorValues.of(node)
        assertEquals(1, values.childCount)
        assertEquals("T", values.text)
        assertEquals("D", values.description)
        assertEquals("p:id/x", values.resourceId)
        assertEquals("C", values.className)
        assertEquals("P", values.packageName)
        assertEquals(PythonHostSelectorBounds(5, 6, 7, 8), values.bounds)
        assertTrue(values.clickable && values.editable && !values.enabled && values.focused && values.selected)
        assertTrue(values.checkable && values.checked && values.scrollable && values.password && !values.visibleToUser)
        // An inverted platform axis collapses onto its anchor, as the protocol promises.
        // zh-CN: 平台报告的颠倒轴折叠到其锚点, 如协议所承诺.
        assertEquals(PythonHostSelectorBounds(50, 60, 50, 60), PythonHostSelectorValues.of(FakeUiNode.root { bounds(50, 60, 40, 30) }).bounds)
    }

}
