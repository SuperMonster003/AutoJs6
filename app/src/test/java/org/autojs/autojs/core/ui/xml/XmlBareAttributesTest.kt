package org.autojs.autojs.core.ui.xml

import org.junit.Assert.assertEquals
import org.junit.Test

class XmlBareAttributesTest {

    @Test
    fun bareAttributesBecomeTrue() {
        assertEquals("<console global=\"true\"/>", XmlBareAttributes.normalize("<console global/>"))
        assertEquals("<console global=\"true\" />", XmlBareAttributes.normalize("<console global />"))
        assertEquals("<console global=\"true\"></console>", XmlBareAttributes.normalize("<console global></console>"))
        assertEquals(
            "<console w=\"*\" global=\"true\" timeVisible=\"true\" h=\"200\"/>",
            XmlBareAttributes.normalize("<console w=\"*\" global timeVisible h=\"200\"/>"),
        )
    }

    @Test
    fun valuedAttributesAreLeftAlone() {
        val xml = "<vertical>\n    <text text=\"a > b\" size='16'/>\n    <console global=\"false\"/>\n</vertical>"
        assertEquals(xml, XmlBareAttributes.normalize(xml))
        assertEquals("<text text = \"x\"/>", XmlBareAttributes.normalize("<text text = \"x\"/>"))
    }

    @Test
    fun nonElementSyntaxIsUntouched() {
        val xml = "<?xml version=\"1.0\"?><!-- a bare comment --><vertical><![CDATA[ raw <x y> ]]>1 < 2</vertical>"
        assertEquals(xml, XmlBareAttributes.normalize(xml))
        assertEquals("plain text", XmlBareAttributes.normalize("plain text"))
        assertEquals("<broken", XmlBareAttributes.normalize("<broken"))
    }

    @Test
    fun quotedValuesMayContainClosingBrackets() {
        assertEquals(
            "<text text=\"/>\" bold=\"true\"/>",
            XmlBareAttributes.normalize("<text text=\"/>\" bold/>"),
        )
    }

}
