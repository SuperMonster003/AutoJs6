package org.autojs.autojs.core.automator.diagnostics

import com.google.gson.JsonParser
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The three forms of [NodeDump] over an in-memory tree: the text lines (indent, bounds, quoted
 * strings, set flags), the depth / visibility / property / budget options, valid nested JSON
 * (object or array form) and the `uiautomator dump` XML.
 * zh-CN: [NodeDump] 在内存节点树上的三种形式: 文本行 (缩进, 边界, 加引号的字符串, 已置位的标志), 深度 / 可见性 / 属性 / 预算选项,
 * 合法的嵌套 JSON (对象或数组形式) 与 `uiautomator dump` 风格的 XML.
 */
class NodeDumpTest {

    private val root = FakeUiNode.root("root") {
        className = "android.widget.FrameLayout"
        packageName = "com.example"
        bounds(0, 0, 1080, 1920)
        child("title") {
            className = "android.widget.TextView"
            text = "Hello \"world\""
            id = "title"
            bounds(0, 0, 1080, 100)
        }
        child("list") {
            className = "androidx.recyclerview.widget.RecyclerView"
            scrollable = true
            bounds(0, 100, 1080, 1920)
            child("row1", clickable = true) {
                className = "android.widget.LinearLayout"
                bounds(0, 100, 1080, 300)
                child("row1.text") {
                    className = "android.widget.TextView"
                    text = "Wi-Fi"
                    bounds(0, 100, 1080, 300)
                }
            }
            child("hidden") {
                className = "android.widget.TextView"
                text = "hidden"
                visibleToUser = false
                enabled = false
                bounds(0, 300, 1080, 400)
            }
        }
    }

    @Test
    fun textLinesAreIndentedAndCarryTheSetProperties() {
        val result = NodeDump.dump(root)
        assertEquals(
            listOf(
                "FrameLayout [0,0][1080,1920]",
                "  TextView [0,0][1080,100] id=\"title\" text=\"Hello \\\"world\\\"\"",
                "  RecyclerView [0,100][1080,1920] scrollable",
                "    LinearLayout [0,100][1080,300] clickable",
                "      TextView [0,100][1080,300] text=\"Wi-Fi\"",
                "    TextView [0,300][1080,400] text=\"hidden\" !enabled !visibleToUser",
            ).joinToString("\n"),
            result.text,
        )
        assertEquals(6, result.nodes)
        assertFalse(result.truncated)
    }

    @Test
    fun maxDepthHidesDeeperLevelsAndSaysSo() {
        val lines = NodeDump.dump(root, DumpOptions(maxDepth = 1)).text.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[2], lines[2].endsWith("scrollable (+2 children)"))
        assertEquals("FrameLayout [0,0][1080,1920] (+2 children)", NodeDump.dump(root, DumpOptions(maxDepth = 0)).text)
    }

    @Test
    fun visibleOnlySkipsInvisibleSubtrees() {
        val text = NodeDump.dump(root, DumpOptions(visibleOnly = true)).text
        assertEquals(5, text.lines().size)
        assertFalse(text.contains("hidden"))
        val invisibleRoot = FakeUiNode.root("r") { visibleToUser = false }
        assertEquals("", NodeDump.dump(invisibleRoot, DumpOptions(visibleOnly = true)).text)
    }

    @Test
    fun propertiesChooseWhatIsPrinted() {
        val lines = NodeDump.dump(root, DumpOptions(properties = listOf("text", "clickable", "childCount"))).text.lines()
        assertEquals("childCount=2", lines[0])
        assertEquals("  text=\"Hello \\\"world\\\"\" childCount=0", lines[1])
        assertEquals("    clickable childCount=1", lines[3])
        try {
            DumpOptions(properties = listOf("nope"))
            fail("unknown property accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message, e.message!!.contains("\"nope\""))
        }
    }

    @Test
    fun maxNodesTruncates() {
        val result = NodeDump.dump(root, DumpOptions(maxNodes = 2))
        assertEquals(listOf("FrameLayout [0,0][1080,1920]", "  TextView [0,0][1080,100] id=\"title\" text=\"Hello \\\"world\\\"\"", "... truncated at 2 nodes"), result.text.lines())
        assertEquals(2, result.nodes)
        assertTrue(result.truncated)
        val xml = NodeDump.dump(root, DumpOptions(format = DumpFormat.XML, maxNodes = 1)).text
        assertTrue(xml, xml.contains("<!-- truncated at 1 nodes -->"))
        assertTrue(xml, xml.endsWith("</hierarchy>"))
    }

    @Test
    fun jsonIsValidAndNested() {
        val text = NodeDump.dump(root, DumpOptions(format = DumpFormat.JSON)).text
        val json = JsonParser.parseString(text).asJsonObject
        assertEquals("android.widget.FrameLayout", json["className"].asString)
        assertTrue(json["id"].isJsonNull)
        assertEquals(listOf(0, 0, 1080, 1920), json["bounds"].asJsonArray.map { it.asInt })
        assertEquals(2, json["childCount"].asInt)
        val children = json["children"].asJsonArray
        assertEquals(2, children.size())
        assertEquals("Hello \"world\"", children[0].asJsonObject["text"].asString)
        val list = children[1].asJsonObject
        assertTrue(list["scrollable"].asBoolean)
        assertEquals(2, list["children"].asJsonArray.size())
        assertEquals("Wi-Fi", list["children"].asJsonArray[0].asJsonObject["children"].asJsonArray[0].asJsonObject["text"].asString)
        assertEquals(0, list["children"].asJsonArray[1].asJsonObject["children"].asJsonArray.size())
    }

    @Test
    fun jsonListFormHoldsOneObjectPerRoot() {
        val array = JsonParser.parseString(NodeDump.dump(listOf(root, root), DumpOptions(format = DumpFormat.JSON)).text).asJsonArray
        assertEquals(2, array.size())
        assertEquals("android.widget.FrameLayout", array[1].asJsonObject["className"].asString)
        assertTrue(JsonParser.parseString(NodeDump.dump(listOf(root), DumpOptions(format = DumpFormat.JSON), asList = false).text).isJsonObject)
        assertEquals("[]", NodeDump.dump(emptyList<FakeUiNode>(), DumpOptions(format = DumpFormat.JSON)).text)
        val truncated = JsonParser.parseString(NodeDump.dump(root, DumpOptions(format = DumpFormat.JSON, maxNodes = 3)).text).asJsonObject
        assertEquals(2, truncated["children"].asJsonArray.size())
        assertEquals(0, truncated["children"].asJsonArray[1].asJsonObject["children"].asJsonArray.size())
    }

    @Test
    fun xmlFollowsUiautomatorDump() {
        val xml = NodeDump.dump(root, DumpOptions(format = DumpFormat.XML)).text
        val lines = xml.lines()
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", lines[0])
        assertEquals("<hierarchy>", lines[1])
        assertEquals(
            "  <node index=\"-1\" text=\"\" resource-id=\"\" class=\"android.widget.FrameLayout\" package=\"com.example\" content-desc=\"\" checkable=\"false\" checked=\"false\" clickable=\"false\" enabled=\"true\" focusable=\"false\" focused=\"false\" scrollable=\"false\" long-clickable=\"false\" password=\"false\" selected=\"false\" visible-to-user=\"true\" bounds=\"[0,0][1080,1920]\">",
            lines[2],
        )
        assertTrue(lines[3], lines[3].startsWith("    <node index=\"0\" text=\"Hello &quot;world&quot;\" resource-id=\"title\" class=\"android.widget.TextView\""))
        assertTrue(lines[3], lines[3].endsWith(" />"))
        assertEquals("    </node>", lines[lines.size - 3])
        assertEquals("  </node>", lines[lines.size - 2])
        assertEquals("</hierarchy>", lines.last())
        val escaped = FakeUiNode.root("r") { text = "a<b>&\"c\"\nd" }
        assertTrue(NodeDump.dump(escaped, DumpOptions(format = DumpFormat.XML, properties = listOf("text"))).text.contains("text=\"a&lt;b&gt;&amp;&quot;c&quot;&#10;d\""))
    }

    @Test
    fun severalRootsAreLabelledInTheTextForm() {
        val lines = NodeDump.dump(listOf(root, FakeUiNode.root("other") { className = "x.Other" }), DumpOptions(maxDepth = 0)).text.lines()
        assertEquals(listOf("[root 1/2]", "FrameLayout [0,0][1080,1920] (+2 children)", "", "[root 2/2]", "Other [0,0][0,0]"), lines)
    }

    @Test
    fun helpersRenderLinesAndProperties() {
        val row = root.find("row1")
        assertEquals("LinearLayout [0,100][1080,300] clickable", NodeDump.line(row))
        assertEquals("clickable actions=[]", NodeDump.line(row, listOf("clickable", "actions")))
        val map = NodeDump.properties(root.find("title"), listOf("text", "bounds", "childCount", "id"))
        assertEquals(listOf("text", "bounds", "childCount", "id"), map.keys.toList())
        assertEquals("Hello \"world\"", map["text"])
        assertEquals(listOf(0, 0, 1080, 100), map["bounds"])
        assertEquals(0, map["childCount"])
        assertEquals("title", map["id"])
        assertNull(NodeDump.value(root, "desc"))
        assertEquals(DumpFormat.XML, DumpFormat.parse("XML"))
        try {
            DumpFormat.parse("yaml")
            fail("unknown format accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message, e.message!!.contains("\"yaml\""))
        }
        val long = FakeUiNode.root("r") { text = "x".repeat(100) }
        assertEquals("text=\"" + "x".repeat(77) + "...\"", NodeDump.line(long, listOf("text")))
    }

}
