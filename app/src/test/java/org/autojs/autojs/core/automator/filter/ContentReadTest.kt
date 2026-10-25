package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.search.DFS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/selector/ROADMAP.md P2.1: a `content` condition reads the description and the text of a node at most once each
 * (the text only when the description does not match), whatever the form of the comparison and also within a search.
 * zh-CN: P2.1: `content` 条件对每个节点的描述与文本各至多读取一次 (描述不匹配时才读文本), 与比较形式无关, 在搜索中也是如此.
 */
class ContentReadTest {

    private fun node(desc: String?, text: String) = FakeUiNode.root {
        this.desc = desc
        this.text = text
    }

    @Test
    fun eachValueReadAtMostOnce() {
        val matchers = listOf(
            StringMatcher.equals("a"), StringMatcher.startsWith("a"), StringMatcher.endsWith("a"),
            StringMatcher.contains("a"), StringMatcher.matches("a.*"), StringMatcher.match("a"),
        )
        for (matcher in matchers) {
            val filter = StringPropertyFilter(StringProperty.CONTENT, matcher)
            val byDesc = node("a", "b")
            assertTrue(filter.filter(byDesc))
            assertEquals("$matcher desc", 1, byDesc.descReads)
            assertEquals("$matcher text", 0, byDesc.textReads)
            val byText = node("b", "a")
            assertTrue(filter.filter(byText))
            assertEquals("$matcher desc", 1, byText.descReads)
            assertEquals("$matcher text", 1, byText.textReads)
            val noDesc = node(null, "b")
            assertFalse(filter.filter(noDesc))
            assertEquals("$matcher desc", 1, noDesc.descReads)
            assertEquals("$matcher text", 1, noDesc.textReads)
        }
    }

    @Test
    fun withinASearch() {
        val root = FakeUiNode.root {
            child("a") { desc = "x" }
            child("b") {
                text = "x"
                child("b0") { desc = "y" }
            }
        }
        val selector = Selector().apply {
            add(StringPropertyFilter(StringProperty.CONTENT, StringMatcher.equals("x")))
            add(BooleanFilter(BooleanFilter.CLICKABLE, false))
        }
        assertEquals(listOf("a", "b"), DFS.search(root, selector.snapshot()).map { it.name })
        root.descendantsAndSelf().forEach {
            assertEquals(it.name, 1, it.descReads)
            assertTrue(it.name, it.textReads <= 1)
        }
        assertEquals(0, root.find("a").textReads)
    }

}
