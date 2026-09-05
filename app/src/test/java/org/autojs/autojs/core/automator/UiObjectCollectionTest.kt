package org.autojs.autojs.core.automator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The index helpers added to [UiObjectCollection] in roadmap P2.6 (`at` / `first` / `last` /
 * `nonNull` / `slice`), over placeholder nodes: no platform call is involved.
 * zh-CN: Roadmap P2.6 加入 [UiObjectCollection] 的序号辅助方法 (`at` / `first` / `last` / `nonNull` / `slice`), 以占位节点
 * 测试: 不涉及平台调用.
 */
class UiObjectCollectionTest {

    // Distinct wrappers of nothing; equality is by identity of the wrapped info (null == null),
    // so the tests compare by reference. zh-CN: 若干空包装; 相等性按被包装 info 的同一性 (null == null), 因此测试按引用比较.
    private val a = UiObject(null)
    private val b = UiObject(null)
    private val c = UiObject(null)

    private val abc = UiObjectCollection.of(listOf(a, b, c))

    // Every placeholder equals every other (null == null), so elements are checked by reference.
    // zh-CN: 占位节点彼此相等 (null == null), 因此逐个按引用核对元素.
    private fun assertNodes(actual: UiObjectCollection, vararg expected: UiObject) {
        assertEquals(expected.size, actual.size())
        expected.forEachIndexed { i, node -> assertSame(node, actual[i]) }
    }

    @Test
    fun atCountsNegativeIndicesFromTheEndAndIsNullOutOfRange() {
        assertSame(a, abc.at(0))
        assertSame(c, abc.at(2))
        assertSame(c, abc.at(-1))
        assertSame(a, abc.at(-3))
        assertNull(abc.at(3))
        assertNull(abc.at(-4))
        assertNull(UiObjectCollection.EMPTY.at(0))
    }

    @Test
    fun firstAndLastAreNullOnlyWhenEmpty() {
        assertSame(a, abc.first())
        assertSame(c, abc.last())
        val single = UiObjectCollection.of(listOf(b))
        assertSame(b, single.first())
        assertSame(b, single.last())
        assertNull(UiObjectCollection.EMPTY.first())
        assertNull(UiObjectCollection.EMPTY.last())
    }

    @Test
    fun nonNullDropsTheNullEntriesAndKeepsAnAlreadyCleanCollection() {
        val holey = UiObjectCollection.of(listOf(a, null, b, null))
        assertEquals(4, holey.size())
        val clean = holey.nonNull()
        assertEquals(2, clean.size())
        assertSame(a, clean[0])
        assertSame(b, clean[1])
        assertSame(abc, abc.nonNull())
        assertSame(UiObjectCollection.EMPTY, UiObjectCollection.EMPTY.nonNull())
    }

    @Test
    fun sliceFollowsTheArrayPrototypeRules() {
        assertSame(abc, abc.slice())
        assertSame(abc, abc.slice(0))
        assertSame(abc, abc.slice(0, 3))
        assertSame(abc, abc.slice(0, 99))
        assertNodes(abc.slice(1), b, c)
        assertNodes(abc.slice(1, 2), b)
        assertNodes(abc.slice(-2), b, c)
        assertNodes(abc.slice(0, -1), a, b)
        assertNodes(abc.slice(-99, 2), a, b)
        assertTrue(abc.slice(2, 1).isEmpty())
        assertTrue(abc.slice(3).isEmpty())
        assertTrue(abc.slice(-1, -2).isEmpty())
        assertSame(UiObjectCollection.EMPTY, abc.slice(5, 9))
    }

    @Test
    fun sliceKeepsTheCollectionType() {
        val part = abc.slice(1)
        assertSame(c, part.at(-1))
        assertSame(b, part.first())
        assertNodes(part.slice(1), c)
    }

}
