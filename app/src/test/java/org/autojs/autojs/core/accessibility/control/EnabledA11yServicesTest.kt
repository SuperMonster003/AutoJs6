package org.autojs.autojs.core.accessibility.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnabledA11yServicesTest {

    private val self = "org.autojs.autojs6/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
    private val other = "com.example.other/.OtherService"

    @Test
    fun parseDropsBlanksAndTheNullLiteral() {
        assertEquals(emptyList<String>(), EnabledA11yServices.parse(null))
        assertEquals(emptyList<String>(), EnabledA11yServices.parse(""))
        assertEquals(emptyList<String>(), EnabledA11yServices.parse("null"))
        assertEquals(listOf(other, self), EnabledA11yServices.parse(" $other : $self :\n"))
    }

    @Test
    fun containsMatchesWholeEntriesOnly() {
        assertTrue(EnabledA11yServices.contains("$other:$self", self))
        assertTrue(EnabledA11yServices.contains(" $self ", self))
        assertFalse(EnabledA11yServices.contains(other, self))
        assertFalse(EnabledA11yServices.contains("$self.Legacy", self))
        assertFalse(EnabledA11yServices.contains(null, self))
    }

    @Test
    fun detachRemovesEveryServiceOfThePackage() {
        val legacy = "org.autojs.autojs6/.LegacyService"
        assertEquals(other, EnabledA11yServices.detach("$legacy:$other:$self", self))
        assertEquals("", EnabledA11yServices.detach(self, self))
        assertEquals("", EnabledA11yServices.detach("null", self))
        assertEquals(other, EnabledA11yServices.detach(other, self))
    }

    @Test
    fun attachAppendsOnceAtTheEnd() {
        assertEquals(self, EnabledA11yServices.attach(null, self))
        assertEquals(self, EnabledA11yServices.attach("", self))
        assertEquals("$other:$self", EnabledA11yServices.attach(other, self))
        assertEquals("$other:$self", EnabledA11yServices.attach("$self:$other", self))
    }

}
