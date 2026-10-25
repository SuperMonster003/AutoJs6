package org.autojs.autojs.core.automator.pick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alias table of the `pickup` / `detect` result types: every spelling maps to its type, the
 * plural forms are derived consistently, and everything else is a method name or unsupported.
 * zh-CN: `pickup` / `detect` 结果类型的别名表: 每种拼写映射到其类型, 复数形式的派生一致, 其余为方法名或不支持.
 */
class PickResultTypeTest {

    @Test
    fun everySpellingMapsToItsType() {
        val expected = mapOf(
            PickResultType.Widget to listOf("#", "w", "widget"),
            PickResultType.WidgetCollection to listOf("{}", "wc", "collection", "list", "#{}", "{#}", "w{}", "{w}"),
            PickResultType.Widgets to listOf("[]", "ws", "widgets", "##", "#[]", "[#]", "w[]", "[w]"),
            PickResultType.Content to listOf("$", "txt", "content"),
            PickResultType.Contents to listOf("contents", "$$", "$[]", "[$]", "txt[]", "[txt]", "content[]", "[content]"),
            PickResultType.Point to listOf(".", "pt", "point"),
            PickResultType.Points to listOf("points", "pts", "..", ".[]", "[.]", "point[]", "[point]", "pt[]", "[pt]"),
            PickResultType.Selector to listOf("@", "selector", "sel"),
            PickResultType.Existence to listOf("?", "exists"),
        )
        expected.forEach { (type, spellings) ->
            assertEquals(spellings, PickResultType.spellings(type))
            spellings.forEach { spelling -> assertEquals(spelling, type, PickResultType.of(spelling)) }
        }
        assertEquals(PickResultType.WIDGET, "widget")
        assertEquals(PickResultType.Widget, PickResultType.of(PickResultType.WIDGET))
    }

    @Test
    fun spellingsAreUniqueAcrossTypes() {
        val all = listOf(
            PickResultType.Widget, PickResultType.WidgetCollection, PickResultType.Widgets,
            PickResultType.Content, PickResultType.Contents, PickResultType.Point, PickResultType.Points,
            PickResultType.Selector, PickResultType.Existence,
        ).flatMap { PickResultType.spellings(it) }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun otherStringsAreMethodNamesAndTheEmptyStringIsNothing() {
        assertEquals(PickResultType.Nothing, PickResultType.of(""))
        assertEquals(PickResultType.Invocation("text", emptyList()), PickResultType.of("text"))
        assertEquals(PickResultType.Invocation("bounds", emptyList()), PickResultType.of("bounds"))
        assertEquals(PickResultType.Invocation("Text", emptyList()), PickResultType.of("Text"))
    }

    @Test
    fun listsAreInvocationsWithArgumentsOrTheWidgetsWhenEmpty() {
        assertEquals(PickResultType.Widgets, PickResultType.of(emptyList<Any>()))
        assertEquals(PickResultType.Invocation("child", listOf(0)), PickResultType.of(listOf("child", 0)))
        assertEquals(PickResultType.Invocation("#", emptyList()), PickResultType.of(listOf("#")))
        assertEquals(PickResultType.Invocation("compass", listOf("p", 2.0)), PickResultType.of(listOf("compass", "p", 2.0)))
        assertEquals(PickResultType.Unsupported(listOf(1, "x")), PickResultType.of(listOf(1, "x")))
    }

    @Test
    fun otherValuesAreUnsupported() {
        assertEquals(PickResultType.Unsupported(5), PickResultType.of(5))
        assertEquals(PickResultType.Unsupported(true), PickResultType.of(true))
        assertEquals(PickResultType.Unsupported(null), PickResultType.of(null))
        val builder = StringBuilder("#")
        assertEquals(PickResultType.Unsupported(builder), PickResultType.of(builder))
    }

    @Test
    fun theArrayFormOfASymbolIsItsPlural() {
        assertEquals(PickResultType.Widgets, PickResultType.arrayFormOf("#"))
        assertEquals(PickResultType.Widgets, PickResultType.arrayFormOf("widget"))
        assertEquals(PickResultType.Contents, PickResultType.arrayFormOf("$"))
        assertEquals(PickResultType.Contents, PickResultType.arrayFormOf("content"))
        assertEquals(PickResultType.Points, PickResultType.arrayFormOf("."))
        assertNull(PickResultType.arrayFormOf("[]"))
        assertNull(PickResultType.arrayFormOf("@"))
        assertNull(PickResultType.arrayFormOf("text"))
        assertNull(PickResultType.arrayFormOf(null))
    }

    /**
     * Appendix C Q9 (docs/selector/ROADMAP.md): the new layer's `SelectorForms` touched `WidgetCollection` before anything
     * asked for the alias table, so the JVM initialized `PickResultType` while that object was still being initialized,
     * and the eight collection spellings resolved to method names for the life of the process.
     * zh-CN: 附录 C Q9: 新层 SelectorForms 在任何查表之前先触达 WidgetCollection, JVM 在该对象初始化途中初始化 PickResultType,
     * 于是 8 种集合拼写在整个进程内都解析为方法名.
     */
    @Test
    fun theCollectionSpellingsResolveWhenTheCollectionIsTouchedFirst() {
        val loader = IsolatedLoader()
        val collection = loader.objectOf("WidgetCollection")
        COLLECTION_SPELLINGS.forEach { assertSame(it, collection, loader.of(it)) }
        assertEquals(COLLECTION_SPELLINGS, loader.spellings(collection))
    }

    @Test
    fun everySpellingResolvesWhicheverTypeIsTouchedFirst() {
        SYMBOLIC_TYPES.forEach { first ->
            val loader = IsolatedLoader()
            loader.objectOf(first)
            SYMBOLIC_TYPES.forEach { name ->
                val type = loader.objectOf(name)
                val spellings = loader.spellings(type)
                assertTrue("$first first: $name", spellings.isNotEmpty())
                spellings.forEach { assertSame("$first first: $it", type, loader.of(it)) }
            }
        }
    }

    /** Loads the `PickResultType` hierarchy afresh, so a test decides which of its classes is initialized first. zh-CN: 重新加载 PickResultType 层级, 由测试决定先初始化其中哪个类. */
    private class IsolatedLoader : ClassLoader(PickResultType::class.java.classLoader) {

        private val base = PickResultType::class.java.name

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name != base && !name.startsWith("$base$")) return super.loadClass(name, resolve)
            synchronized(this) {
                findLoadedClass(name)?.let { return it }
                val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
                return defineClass(name, bytes, 0, bytes.size)
            }
        }

        /** The object [simpleName] of the hierarchy, initializing its class. zh-CN: 层级中的对象 [simpleName], 会初始化其类. */
        fun objectOf(simpleName: String): Any = Class.forName("$base$$simpleName", true, this).getField("INSTANCE").get(null)

        fun of(value: Any?): Any? = companion().let { it.javaClass.getMethod("of", Any::class.java).invoke(it, value) }

        @Suppress("UNCHECKED_CAST")
        fun spellings(type: Any): List<String> = companion().let {
            it.javaClass.getMethod("spellings", Class.forName(base, false, this)).invoke(it, type) as List<String>
        }

        private fun companion(): Any = Class.forName(base, true, this).getField("Companion").get(null)

    }

    private companion object {

        val COLLECTION_SPELLINGS = listOf("{}", "wc", "collection", "list", "#{}", "{#}", "w{}", "{w}")

        val SYMBOLIC_TYPES = listOf("Widget", "WidgetCollection", "Widgets", "Content", "Contents", "Point", "Points", "Selector", "Existence")

    }

}
