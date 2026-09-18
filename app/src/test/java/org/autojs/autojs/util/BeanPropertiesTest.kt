package org.autojs.autojs.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BeanPropertiesTest {

    @Suppress("unused", "MemberVisibilityCanBePrivate", "UNUSED_PARAMETER")
    private class Fixture {
        var title: String? = null
        var enabled: Boolean = true
        var isReady: Boolean = false
        var anything: Any? = null
        val readOnly: Int = 7
        var derived: String
            get() = title ?: "fallback"
            set(value) {
                title = value
            }
        private var secret: String = "hidden"
        internal var mangled: Int = 1
        var failing: String
            get() = throw IllegalStateException("getter failed")
            set(value) {
                throw IllegalStateException("setter failed")
            }
        fun getWithArgument(argument: Int): Int = argument
        fun helper(): String = "not a property"
    }

    @Test
    fun `resolves getters and setters of public var properties`() {
        val fixture = Fixture()
        val title = BeanProperties.find(Fixture::class.java, "title")!!
        assertTrue(title.isMutable)
        assertSame(String::class.java, title.type)
        assertNull(title.get(fixture))
        title.set(fixture, "hello")
        assertEquals("hello", fixture.title)
        assertEquals("hello", title.get(fixture))
        title.set(fixture, null)
        assertNull(fixture.title)

        val enabled = BeanProperties.find(Fixture::class.java, "enabled")!!
        assertSame(java.lang.Boolean.TYPE, enabled.type)
        enabled.set(fixture, false)
        assertFalse(fixture.enabled)

        val anything = BeanProperties.find(Fixture::class.java, "anything")!!
        anything.set(fixture, listOf(1, 2))
        assertEquals(listOf(1, 2), fixture.anything)
    }

    @Test
    fun `boolean is-properties keep their name as the getter`() {
        val fixture = Fixture()
        val ready = BeanProperties.find(Fixture::class.java, "isReady")!!
        assertTrue(ready.isMutable)
        assertEquals(false, ready.get(fixture))
        ready.set(fixture, true)
        assertTrue(fixture.isReady)
        assertNull(BeanProperties.find(Fixture::class.java, "ready"))
    }

    @Test
    fun `custom accessors and read-only properties`() {
        val fixture = Fixture()
        val derived = BeanProperties.find(Fixture::class.java, "derived")!!
        assertEquals("fallback", derived.get(fixture))
        derived.set(fixture, "set through the accessor")
        assertEquals("set through the accessor", fixture.title)

        val readOnly = BeanProperties.find(Fixture::class.java, "readOnly")!!
        assertFalse(readOnly.isMutable)
        assertEquals(7, readOnly.get(fixture))
        assertThrows(IllegalStateException::class.java) { readOnly.set(fixture, 8) }
    }

    @Test
    fun `hides private, internal and non-property members`() {
        listOf("secret", "mangled", "helper", "withArgument", "getWithArgument", "", "title\$app", "Companion").forEach {
            assertNull(it, BeanProperties.find(Fixture::class.java, it))
        }
    }

    @Test
    fun `accessor exceptions are rethrown unwrapped`() {
        val fixture = Fixture()
        val failing = BeanProperties.find(Fixture::class.java, "failing")!!
        assertEquals("getter failed", assertThrows(IllegalStateException::class.java) { failing.get(fixture) }.message)
        assertEquals("setter failed", assertThrows(IllegalStateException::class.java) { failing.set(fixture, "x") }.message)
        val enabled = BeanProperties.find(Fixture::class.java, "enabled")!!
        assertThrows(IllegalArgumentException::class.java) { enabled.set(fixture, "not a boolean") }
    }

}
