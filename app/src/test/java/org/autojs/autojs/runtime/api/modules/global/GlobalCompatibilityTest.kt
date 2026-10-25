package org.autojs.autojs.runtime.api.modules.global

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined

class GlobalCompatibilityTest {

    @Test
    fun isNullishExaminesOnlyTheFirstArgument() {
        assertTrue(IsNullish.invoke())
        assertTrue(IsNullish.invoke(null, 1))
        assertTrue(IsNullish.invoke(Undefined.instance, 1))
        assertFalse(IsNullish.invoke(1, null))
    }

    @Test
    fun speciesIncludesLegacyHtmlDocumentAndWindowPredicates() {
        assertTrue(Species.isHTMLDocumentRhino(SpeciesObject("HTMLDocument")))
        assertTrue(Species.isWindowRhino(SpeciesObject("Window")))
        assertFalse(Species.isHTMLDocumentRhino(SpeciesObject("Window")))
        assertFalse(Species.isWindowRhino(SpeciesObject("HTMLDocument")))
    }

    private class SpeciesObject(private val species: String) : NativeObject() {
        override fun getClassName(): String = species
    }
}
