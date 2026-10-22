package org.autojs.autojs.core.ui.preview

import org.autojs.autojs.core.ui.preview.UiPreviewSourceReader.FailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPreviewSourceReaderTest {

    private fun layoutOf(source: String) = UiPreviewSourceReader.read(source) as UiPreviewSourceReader.Layout

    private fun failureOf(source: String) = UiPreviewSourceReader.read(source) as UiPreviewSourceReader.Failure

    @Test
    fun theFirstLayoutCallIsReadAndItsElementsMapToTheSource() {
        val source = """
            "ui";
            ui.layout(
                <vertical>
                    <text id="alpha" text="Alpha"/>
                    <button text="Beta"/>
                </vertical>
            );
            ui.layout(<frame/>);
        """.trimIndent()
        val layout = layoutOf(source)
        assertTrue(layout.xml, layout.xml.startsWith("<vertical>") && layout.xml.endsWith("</vertical>"))
        assertEquals(source.indexOf("<vertical>"), layout.literalOffset)
        assertEquals(listOf("<vertical>", "<text", "<button").map(source::indexOf), layout.elementOffsets.toList())
        assertEquals(layout.literalOffset, layout.elementOffset(9))
        assertEquals(3 to 8, UiPreviewSourceReader.positionOf(source, layout.elementOffset(1)))
        assertNull(layout.androidLayout)
        assertEquals("{}", layout.bindingsJson)
    }

    @Test
    fun expressionsBecomeBindingsOverTheLiteralDeclarations() {
        val source = """
            "ui";
            const title = "Hello";
            let size = 16, items = [1, "two", true, null], style = { color: "#ff0000", "pad": -4, half: 0.5 };
            var counter = 0;
            counter++;
            var twice = "a", twice = "b";
            const computed = title + "!";
            const greeting = `hi`;
            function render() {}
            ui.layout(<text text={title} textSize={size} hint={a < b ? "x" : "y"}>{items.length}</text>);
        """.trimIndent()
        val layout = layoutOf(source)
        assertEquals(
            "<text text=\"{{title}}\" textSize=\"{{size}}\" hint=\"{{a &lt; b ? &quot;x&quot; : &quot;y&quot;}}\">{{items.length}}</text>",
            layout.xml,
        )
        assertEquals(
            "{\"title\":\"Hello\",\"size\":16,\"items\":[1,\"two\",true,null],\"style\":{\"color\":\"#ff0000\",\"pad\":-4,\"half\":0.5},\"greeting\":\"hi\"}",
            layout.bindingsJson,
        )
        // Inside a converted expression every offset maps to its brace in the source.
        val brace = source.indexOf("{size}")
        val converted = layout.xml.indexOf("{{size}}")
        assertEquals(brace, layout.sourceOffsetOf(converted))
        assertEquals(brace, layout.sourceOffsetOf(converted + 4))
        assertEquals(source.indexOf(" textSize"), layout.sourceOffsetOf(layout.xml.indexOf(" textSize")))
    }

    @Test
    fun aLayoutMayComeThroughOneVariable() {
        val layout = layoutOf("var view = <frame><text/></frame>;\nui.layout(view);")
        assertEquals("<frame><text/></frame>", layout.xml)
        assertEquals(listOf(11, 18), layout.elementOffsets.toList())

        val reassigned = "var view = <frame/>;\nview = <text/>;\nui.layout(view);"
        failureOf(reassigned).let {
            assertEquals(FailureKind.UNSUPPORTED_ARGUMENT, it.kind)
            assertEquals(reassigned.lastIndexOf("view"), it.offset)
        }
        assertEquals(FailureKind.UNSUPPORTED_ARGUMENT, failureOf("var view = <frame/>, view = <text/>;\nui.layout(view);").kind)
        assertEquals(FailureKind.UNSUPPORTED_ARGUMENT, failureOf("ui.layout(make());").kind)
        assertEquals(FailureKind.UNSUPPORTED_ARGUMENT, failureOf("ui.layout(<frame/>, 1);").kind)
    }

    @Test
    fun scriptsWithoutALayoutOrWithSyntaxErrorsFail() {
        failureOf("\"ui\";\nvar a = ;\nui.layout(<frame/>);").let {
            assertEquals(FailureKind.SYNTAX_ERROR, it.kind)
            assertEquals(1, UiPreviewSourceReader.positionOf("\"ui\";\nvar a = ;\nui.layout(<frame/>);", it.offset).first)
        }
        assertEquals(FailureKind.NO_LAYOUT, failureOf("\"ui\";\ntoast(\"hi\");").kind)
        assertEquals(FailureKind.NO_LAYOUT, failureOf("layout(<frame/>); ui.inflate(<frame/>);").kind)
    }

    @Test
    fun theFirstUseAndroidLayoutCallDecidesTheMode() {
        assertEquals(true, layoutOf("ui.useAndroidLayout();\nui.layout(<frame/>);").androidLayout)
        assertEquals(false, layoutOf("ui.useAndroidLayout(false);\nui.useAndroidLayout(true);\nui.layout(<frame/>);").androidLayout)
        assertNull(layoutOf("ui.useAndroidLayout(flag);\nui.layout(<frame/>);").androidLayout)
    }

    @Test
    fun crlfSourcesAndFormatCharsKeepTheirOffsets() {
        val zwj = String(Character.toChars(0x200D))
        val source = "\"ui\";\nui.layout(<vertical>\n  <text text=\"a${zwj}b\" textSize={size}/>\n  <button/>\n</vertical>);".replace("\n", "\r\n")
        val layout = layoutOf(source)
        val literal = source.substring(source.indexOf("<vertical>"), source.indexOf(");"))
        assertEquals(literal.replace("{size}", "\"{{size}}\""), layout.xml)
        assertEquals(listOf("<vertical>", "<text", "<button").map(source::indexOf), layout.elementOffsets.toList())
        assertEquals(3 to 2, UiPreviewSourceReader.positionOf(source, layout.elementOffset(2)))
    }

    @Test
    fun positionsCountLinesAndColumnsFromZero() {
        assertEquals(0 to 0, UiPreviewSourceReader.positionOf("ab\ncd", 0))
        assertEquals(0 to 2, UiPreviewSourceReader.positionOf("ab\ncd", 2))
        assertEquals(1 to 0, UiPreviewSourceReader.positionOf("ab\ncd", 3))
        assertEquals(1 to 2, UiPreviewSourceReader.positionOf("ab\ncd", 99))
        assertEquals(0 to 0, UiPreviewSourceReader.positionOf("ab\ncd", -1))
    }
}
