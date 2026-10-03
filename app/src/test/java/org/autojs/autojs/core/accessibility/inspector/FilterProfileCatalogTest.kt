package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.accessibility.inspector.FilterProfile.BooleanProperty
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.IntRangeSpec
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.Tri
import org.autojs.autojs.core.accessibility.inspector.FilterProfileCatalog.NameError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Roadmap P3.1 / D6: the immutable default, create / update / rename / delete / select rules and
 * the tolerant JSON codec of [FilterProfileCatalog].
 * zh-CN: Roadmap P3.1 / D6: 不可变的默认配置, 建立 / 更新 / 改名 / 删除 / 选中规则, 以及 [FilterProfileCatalog] 的宽容 JSON 编解码.
 */
class FilterProfileCatalogTest {

    private var counter = 0

    private fun catalog(selectedId: String? = null) = FilterProfileCatalog.empty(selectedId) { "id${++counter}" }

    @Test
    fun defaultIsFirstImmutableAndSelected() {
        val c = catalog()
        assertEquals(listOf(FilterProfile.DEFAULT), c.profiles)
        assertSame(FilterProfile.DEFAULT, c.selected)
        assertFalse(c.canUpdate(FilterProfile.DEFAULT))
        assertFalse(c.update(FilterProfile.DEFAULT.copy(requireText = true)))
        assertFalse(c.rename(FilterProfile.DEFAULT_ID, "Renamed"))
        assertFalse(c.delete(FilterProfile.DEFAULT_ID))
        assertEquals(listOf(FilterProfile.DEFAULT), c.profiles)
    }

    @Test
    fun saveAsCreatesAndSelects() {
        val c = catalog()
        val created = c.saveAs(FilterProfile.DEFAULT.copy(requireText = true), " Mine ")
        assertEquals("id1", created.id)
        assertEquals("Mine", created.name)
        assertTrue(created.requireText)
        assertEquals(listOf(FilterProfile.DEFAULT, created), c.profiles)
        assertEquals(created, c.selected)
        assertTrue(c.canUpdate(created))
    }

    @Test
    fun namesMustBeNonBlankAndUnique() {
        val c = catalog()
        val mine = c.saveAs(FilterProfile.DEFAULT, "Mine")
        assertEquals(NameError.BLANK, c.validateName("  "))
        assertEquals(NameError.DUPLICATE, c.validateName("Mine"))
        assertEquals(NameError.DUPLICATE, c.validateName(" Mine "))
        assertNull(c.validateName("Mine", exceptId = mine.id))
        assertNull(c.validateName("Other"))
        try {
            c.saveAs(FilterProfile.DEFAULT, "Mine")
            fail("duplicate name accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("DUPLICATE"))
        }
        assertEquals(2, c.profiles.size)
    }

    @Test
    fun updateRenameDelete() {
        val c = catalog()
        val a = c.saveAs(FilterProfile.DEFAULT, "A")
        val b = c.saveAs(FilterProfile.DEFAULT, "B")
        assertTrue(c.update(a.copy(requireId = true)))
        assertTrue(c[a.id]!!.requireId)
        assertFalse(c.update(a.copy(id = "unknown")))
        assertTrue(c.rename(a.id, " A2 "))
        assertEquals("A2", c[a.id]!!.name)
        assertFalse(c.rename(a.id, "B"))
        assertFalse(c.rename(a.id, ""))
        assertFalse(c.rename("unknown", "X"))
        assertEquals(b, c.selected)
        assertTrue(c.delete(b.id))
        assertSame(FilterProfile.DEFAULT, c.selected)
        assertFalse(c.delete(b.id))
        assertEquals(listOf(FilterProfile.DEFAULT, c[a.id]), c.profiles)
    }

    @Test
    fun selectionFallsBackToDefault() {
        val c = catalog(selectedId = "missing")
        assertEquals(FilterProfile.DEFAULT_ID, c.selectedId)
        val a = c.saveAs(FilterProfile.DEFAULT, "A")
        assertEquals(a, c.select(a.id))
        assertSame(FilterProfile.DEFAULT, c.select("nope"))
        assertEquals(FilterProfile.DEFAULT_ID, c.selectedId)
    }

    @Test
    fun jsonRoundTripKeepsEveryDimensionAndTheSelection() {
        val c = catalog()
        val full = FilterProfile(
            id = "x", name = "x",
            booleans = mapOf(BooleanProperty.CLICKABLE to Tri.YES, BooleanProperty.ENABLED to Tri.NO, BooleanProperty.FOCUSED to Tri.ANY),
            requireId = true, requireText = false, requireDesc = true,
            classPresets = setOf(ClassPreset.BUTTON, ClassPreset.SCROLL),
            depth = IntRangeSpec(1, 4), childCount = IntRangeSpec(max = 2), indexInParent = IntRangeSpec(min = 0),
            minWidth = 10, minHeight = null, onScreenOnly = true,
            packages = setOf("com.a", "com.b"), actions = setOf("ACTION_CLICK"),
        )
        val a = c.saveAs(full, "Full")
        val b = c.saveAs(FilterProfile.DEFAULT, "Plain")
        val decoded = FilterProfileCatalog.fromJson(c.toJson(), selectedId = a.id)
        // Tri.ANY entries are dropped on the way out; everything else must survive.
        assertEquals(listOf(FilterProfile.DEFAULT, a.copy(booleans = a.booleans.filterValues { it != Tri.ANY }), b), decoded.profiles)
        assertEquals(a.id, decoded.selectedId)
    }

    @Test
    fun decodingIsTolerant() {
        assertEquals(listOf(FilterProfile.DEFAULT), FilterProfileCatalog.fromJson(null).profiles)
        assertEquals(listOf(FilterProfile.DEFAULT), FilterProfileCatalog.fromJson("garbage").profiles)
        assertEquals(listOf(FilterProfile.DEFAULT), FilterProfileCatalog.fromJson("{\"a\":1}").profiles)
        val json = """[
            {"id":"default","name":"hijack","requireText":true},
            {"id":"a","name":" A ","booleans":{"CLICKABLE":"YES","BOGUS":"NO","ENABLED":"MAYBE","FOCUSED":"ANY"},"classPresets":["BUTTON","NOPE"],"depth":{"min":2},"minWidth":0,"packages":["", "com.x"],"unknownField":1},
            {"id":"a","name":"dup"},
            {"name":"no id"},
            null
        ]"""
        val decoded = FilterProfileCatalog.fromJson(json, selectedId = "a")
        val a = decoded["a"]!!
        assertEquals(listOf(FilterProfile.DEFAULT, a), decoded.profiles)
        assertEquals("A", a.name)
        assertEquals(mapOf(BooleanProperty.CLICKABLE to Tri.YES), a.booleans)
        assertEquals(setOf(ClassPreset.BUTTON), a.classPresets)
        assertEquals(IntRangeSpec(min = 2), a.depth)
        assertEquals(IntRangeSpec.ANY, a.childCount)
        assertNull(a.minWidth)
        assertEquals(setOf("com.x"), a.packages)
        assertFalse(a.requireText)
        assertEquals("a", decoded.selectedId)
        assertSame(FilterProfile.DEFAULT, decoded[FilterProfile.DEFAULT_ID])
    }

}
