package org.autojs.autojs.core.automator.query

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.filter.StringMatcher
import org.autojs.autojs.core.automator.filter.StringProperty
import org.autojs.autojs.core.automator.filter.StringPropertyFilter
import org.autojs.autojs.core.automator.search.DFS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The check of a single result (docs/selector/ROADMAP.md D6 (c), P3.2): [HitVerifier] over [FakeUiNode] trees whose nodes
 * can fail to refresh ([FakeUiNode.refreshable]), as a node that left the window between the search and the return does.
 * zh-CN: 单个结果的校验 (D6 (c), P3.2): 在节点可能刷新失败 ([FakeUiNode.refreshable]) 的 [FakeUiNode] 树上运行 [HitVerifier],
 * 模拟在搜索与返回之间离开窗口的节点.
 */
class HitVerifierTest {

    private val stale = mutableListOf<String>()

    private fun dialog(vararg names: String) = FakeUiNode.root {
        for (name in names) child(name) { text = "OK" }
    }

    private fun searchOk(root: FakeUiNode): FakeUiNode? =
        DFS.search(root, StringPropertyFilter(StringProperty.TEXT, StringMatcher.equals("OK")), 1).firstOrNull()

    private fun verify(found: FakeUiNode, retries: Int = HitVerifier.RETRIES, again: () -> FakeUiNode?) =
        HitVerifier.verify(found, retries, refresh = FakeUiNode::refresh, stale = { stale += it.name }, again = again)

    @Test
    fun aFreshHitIsReturnedWithoutSearchingAgain() {
        val root = dialog("ok")
        var searches = 0
        val node = verify(searchOk(root)!!) { searches += 1; searchOk(root) }
        assertSame(root.find("ok"), node)
        assertEquals(0, searches)
        assertEquals(emptyList<String>(), stale)
    }

    @Test
    fun aStaleHitIsReplacedByTheNodeFoundAgain() {
        // The dialog closed after the search and a new one with the same button opened. zh-CN: 搜索后对话框关闭, 又打开了带同样按钮的新对话框.
        var root = dialog("old")
        val first = searchOk(root)!!
        first.refreshable = false
        root = dialog("new")
        val node = verify(first) { searchOk(root) }
        assertSame(root.find("new"), node)
        assertEquals(listOf("old"), stale)
    }

    @Test
    fun aHitStillStaleAfterTwoSearchesIsNothingFound() {
        var round = 0
        val first = dialog("n0").find("n0").also { it.refreshable = false }
        val node = verify(first) {
            round += 1
            dialog("n$round").find("n$round").also { it.refreshable = false }
        }
        assertNull(node)
        assertEquals(HitVerifier.RETRIES, round)
        assertEquals(listOf("n0", "n1", "n2"), stale)
    }

    /** A query without limits (`untilFindOne()`) searches until a node refreshes. zh-CN: 无上限的查询 (`untilFindOne()`) 一直搜索到节点刷新成功为止. */
    @Test
    fun anUnboundedCheckSearchesUntilANodeRefreshes() {
        var round = 0
        val first = dialog("n0").find("n0").also { it.refreshable = false }
        val node = verify(first, Int.MAX_VALUE) {
            round += 1
            dialog("n$round").find("n$round").also { it.refreshable = round == 5 }
        }
        assertEquals("n5", node?.name)
        assertEquals(listOf("n0", "n1", "n2", "n3", "n4"), stale)
    }

    @Test
    fun aSearchFindingNothingEndsTheCheck() {
        val first = dialog("gone").find("gone").also { it.refreshable = false }
        var searches = 0
        assertNull(verify(first) { searches += 1; null })
        assertEquals(1, searches)
        assertEquals(listOf("gone"), stale)
    }

}
