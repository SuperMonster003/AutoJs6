package org.autojs.autojs.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionPropertiesTest {

    @Suppress("unused")
    private class Fixture {
        companion object {
            const val CODE = 42
            const val NAME = "fixture"
            @JvmField
            val SHARED = listOf("a", "b")
            val computed: Int get() = CODE * 2
            val isReady: Boolean get() = true
            private const val SECRET = "hidden"
            private val internalList = ArrayList<String>()
            fun helper(): String = "not a property"
            fun getWithArgument(argument: Int): Int = argument
        }
    }

    @Test
    fun `exports constants, JvmField values and public getters by property name`() {
        val getters = CompanionProperties.publicGetters(Fixture::class.java, Fixture.Companion)
        assertEquals(setOf("CODE", "NAME", "SHARED", "computed", "isReady"), getters.keys)
        assertEquals(42, getters.getValue("CODE")())
        assertEquals("fixture", getters.getValue("NAME")())
        assertEquals(listOf("a", "b"), getters.getValue("SHARED")())
        assertEquals(84, getters.getValue("computed")())
        assertEquals(true, getters.getValue("isReady")())
    }

    @Test
    fun `hides private properties, functions and the companion holder`() {
        val getters = CompanionProperties.publicGetters(Fixture::class.java, Fixture.Companion)
        listOf("SECRET", "internalList", "helper", "getWithArgument", "withArgument", "Companion", "INSTANCE").forEach {
            assertEquals(it, null, getters[it])
        }
    }

}
