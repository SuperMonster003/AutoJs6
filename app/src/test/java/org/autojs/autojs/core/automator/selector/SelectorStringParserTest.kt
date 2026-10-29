package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.filter.BoundsFilter
import org.autojs.autojs.core.automator.filter.Preference
import org.autojs.autojs.core.automator.selector.SelectorStringParser.Op
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The string selector syntax (roadmap D13 / P5.3): what [SelectorStringParser] accepts, what the
 * resulting filters match on a fake tree, and the errors with their positions and candidates.
 * zh-CN: 字符串选择器语法 (Roadmap D13 / P5.3): [SelectorStringParser] 接受什么, 得到的过滤器在假树上匹配什么, 以及带位置与候选的错误.
 */
class SelectorStringParserTest {

    private val parser = SelectorStringParser()

    private fun screen() = FakeUiNode.root("screen") {
        packageName = "com.example"
        child("login") {
            text = "Login"
            clickable = true
            enabled = true
            id = "com.example:id/login"
            className = "android.widget.Button"
            bounds(0, 0, 100, 100)
        }
        child("logout") {
            text = "Logout"
            clickable = true
            enabled = false
            bounds(0, 100, 100, 200)
        }
        child("blog") {
            text = "Blog"
            desc = "Say \"hi\""
        }
        child("signIn") {
            text = "Sign in"
            child("deep") {
                text = "Deep"
                clickable = true
            }
        }
    }

    private fun select(syntax: String): UiSelector = parser.parse(syntax, UiSelector(null as AccessibilityBridge?))

    private fun names(syntax: String): List<String> = select(syntax).searchIn(screen()).map { it.name }

    private fun error(syntax: String): SelectorStringException = assertThrows(SelectorStringException::class.java) { select(syntax) }

    @Test
    fun tokenizeSplitsTermsAndRecordsPositions() {
        val terms = parser.tokenize("text=Login clickable !enabled desc~=^Sub id=\"a b\"")
        assertEquals(listOf("text", "clickable", "enabled", "desc", "id"), terms.map { it.key })
        assertEquals(listOf(Op.EQUALS, Op.FLAG, Op.NOT, Op.MATCH, Op.EQUALS), terms.map { it.op })
        assertEquals(listOf("Login", null, null, "^Sub", "a b"), terms.map { it.value })
        assertEquals(listOf(0, 11, 21, 30, 41), terms.map { it.position })
        assertEquals(listOf(false, false, false, false, true), terms.map { it.quoted })
        assertEquals("id=\"a b\"", terms[4].text)
        assertEquals(emptyList<SelectorStringParser.Term>(), parser.tokenize("   "))
    }

    @Test
    fun anUnquotedValueRunsToTheNextSpaceAndKeepsEqualsSigns() {
        val terms = parser.tokenize("text=a=b\tid=x/y")
        assertEquals(listOf("a=b", "x/y"), terms.map { it.value })
    }

    @Test
    fun quotedValuesUnescape() {
        val terms = parser.tokenize("""desc='Say \'hi\'' text="tab\there" id="back\\slash"""")
        assertEquals(listOf("Say 'hi'", "tab\there", "back\\slash"), terms.map { it.value })
    }

    @Test
    fun equalsIsTheWholeStringAndFlagsAreBooleans() {
        assertEquals(listOf("login"), names("text=Login"))
        assertEquals(listOf("login"), names("text=Login clickable"))
        assertEquals(listOf("logout"), names("clickable !enabled"))
        assertEquals(listOf("login", "deep"), names("clickable enabled=true"))
        assertEquals(listOf("logout"), names("enabled=false"))
        assertEquals(emptyList<String>(), names("text=login"))
    }

    @Test
    fun matchTermsAreRegularExpressionSearches() {
        assertEquals(listOf("login", "logout"), names("text~=^Log"))
        assertEquals(listOf("login", "logout", "blog"), names("text=/log/i"))
        assertEquals(listOf("login", "logout"), names("text=/^log/i"))
        assertEquals(listOf("blog"), names("desc~=hi"))
        assertEquals(listOf("login"), names("className=android.widget.Button id~=login$"))
    }

    @Test
    fun aQuotedRegexLiteralIsAPlainValue() {
        assertEquals(emptyList<String>(), names("text=\"/log/i\""))
    }

    @Test
    fun quotedValuesKeepSpacesAndEscapes() {
        assertEquals(listOf("signIn"), names("text=\"Sign in\""))
        assertEquals(listOf("signIn"), names("text='Sign in'"))
        assertEquals(listOf("blog"), names("desc='Say \\\"hi\\\"'"))
    }

    @Test
    fun numbersAreParsedLenientlyAndCommaValuesFillMultiArgumentOverloads() {
        assertEquals(listOf("login", "logout"), names("depth=1 clickable"))
        assertEquals(listOf("deep"), names("depth=2"))
        // BoundsFilter evaluates through the screen metrics (an Android context), so only its construction is checked here.
        // zh-CN: BoundsFilter 的求值经过屏幕度量 (Android 上下文), 这里只检查它的构造.
        val filters = select("bounds=0,0,100,100").selector.filters
        assertEquals(1, filters.size)
        assertTrue(filters[0].toString(), filters[0] is BoundsFilter)
        assertEquals(2, select("bounds=0,0,100,100 boundsInside=0,0,200,200").selector.filters.size)
    }

    @Test
    fun commaValuesOfPreferAreSeveralKeys() {
        // A vararg property fits the whole value as one argument too, so the comma split comes first (docs/selector/ROADMAP.md Q21).
        // zh-CN: 可变参数属性也能以整值为单个参数匹配, 因此先按逗号拆分 (Q21).
        val preference = select("text=Login prefer=visible,center").selector.preference!!
        assertEquals(listOf(Preference.Key.VISIBLE, Preference.Key.CENTER), preference.keys)
        assertEquals(listOf(Preference.Key.ENABLED), select("prefer=enabled").selector.preference!!.keys)
        val e = error("prefer=visible,middle")
        assertEquals("prefer=visible,middle", e.term)
        assertTrue(e.detail, e.detail.startsWith("Unknown key of prefer: \"middle\""))
        val quoted = error("prefer=\"visible,center\"").detail
        assertTrue(quoted, quoted.startsWith("Unknown key of prefer: \"visible,center\""))
    }

    @Test
    fun commaValuesOfActionAreSeveralActions() {
        // A search recycles the nodes it passes over, so every search gets a tree of its own.
        // zh-CN: 搜索会回收经过的节点, 因此每次搜索使用新的树.
        fun screen() = FakeUiNode.root("screen") {
            child("field") { actions = listOf("ACTION_CLICK", "ACTION_SET_TEXT") }
            child("button") { actions = listOf("ACTION_CLICK") }
        }
        fun found(syntax: String) = select(syntax).searchIn(screen()).map { it.name }
        assertEquals(listOf("field"), found("action=CLICK,SET_TEXT"))
        assertEquals(listOf("field", "button"), found("action=CLICK"))
        assertEquals("action([CLICK,SET_TEXT])", select("action=CLICK,SET_TEXT").toString())
        assertEquals(emptyList<String>(), found("action=\"CLICK,SET_TEXT\""))
    }

    @Test
    fun termsAppendToTheGivenSelector() {
        val selector = UiSelector(null as AccessibilityBridge?).text("Login")
        assertTrue(parser.parse("clickable", selector) === selector)
        assertEquals(2, selector.selector.filters.size)
        assertEquals(listOf("login"), selector.searchIn(screen()).map { it.name })
    }

    @Test
    fun anUnknownKeyReportsPositionTermAndCandidates() {
        val e = error("text=Login textt=x")
        assertEquals(11, e.position)
        assertEquals("textt=x", e.term)
        assertTrue(e.candidates.toString(), "text" in e.candidates)
        assertTrue(e.message, e.message!!.contains("unknown property \"textt\"") && e.message!!.contains("did you mean"))
        assertEquals("text=Login textt=x", e.syntax)
    }

    @Test
    fun aMissingValueIsReportedAfterTheOperator() {
        val e = error("clickable=")
        assertEquals(10, e.position)
        assertTrue(e.message, e.message!!.contains("missing value after \"clickable=\""))
        assertTrue(error("text~=").message!!.contains("missing value after \"text~=\""))
        assertTrue(error("text= x").message!!.contains("missing value after \"text=\""))
    }

    @Test
    fun aValuePropertyWithoutAValueIsRefused() {
        val e = error("clickable text")
        assertEquals(10, e.position)
        assertEquals("text", e.term)
        assertTrue(e.message, e.message!!.contains("\"text\" needs a value: text=value"))
    }

    @Test
    fun negationAppliesToBooleanPropertiesOnly() {
        val e = error("!text")
        assertEquals(0, e.position)
        assertTrue(e.message, e.message!!.contains("\"!text\" applies to boolean properties only"))
        assertTrue(error("!clickable=false").message!!.contains("\"!clickable\" takes no value"))
    }

    @Test
    fun matchAppliesToStringPropertiesOnly() {
        val e = error("depth~=3")
        assertEquals("depth~=3", e.term)
        assertTrue(e.message, e.message!!.contains("\"depth~=\" applies to string properties only"))
        assertTrue(e.message, e.message!!.contains("text, desc"))
        assertEquals(setOf("text", "desc", "content", "id", "className", "packageName"), parser.matchableProperties.toSet())
    }

    @Test
    fun anInvalidRegularExpressionIsReportedAtItsTerm() {
        val e = error("clickable text~=[")
        assertEquals(10, e.position)
        assertEquals("text~=[", e.term)
        assertTrue(e.message, e.message!!.contains("Invalid selector string at 10"))
        assertEquals("text=/[/", error("text=/[/").term)
    }

    @Test
    fun aValueThatFitsNoOverloadNamesTheSignatures() {
        val e = error("depth=deep")
        assertEquals("depth=deep", e.term)
        assertTrue(e.message, e.message!!.contains("value \"deep\" does not fit \"depth\"") && e.message!!.contains("depth("))
        assertTrue(error("bounds=1,2,3").message!!.contains("does not fit \"bounds\""))
    }

    @Test
    fun malformedQuotesAndOperatorsAreReportedWhereTheyAre() {
        assertTrue(error("text=\"unterminated").message!!.contains("unterminated quoted value"))
        val glued = error("text=\"a\"b")
        assertEquals(8, glued.position)
        assertTrue(glued.message, glued.message!!.contains("expected a space after the closing quote"))
        val odd = error("text:Login")
        assertEquals(4, odd.position)
        assertTrue(odd.message, odd.message!!.contains("unexpected \":\" after \"text\""))
        val bare = error("=Login")
        assertEquals(0, bare.position)
        assertTrue(bare.message, bare.message!!.contains("expected a property name"))
    }

    @Test
    fun suggestionsPreferPrefixesThenSubstringsThenNearMisses() {
        assertEquals("text", parser.suggest("Text").first())
        assertTrue(parser.suggest("txt").toString(), "text" in parser.suggest("txt"))
        assertEquals("clickable", parser.suggest("click").first())
        assertTrue(parser.suggest("Match").all { "Match" in it })
        assertTrue(parser.suggest("zzzzzz").isEmpty())
        assertTrue(parser.suggest("cl", limit = 2).size <= 2)
    }

    @Test
    fun regexLiteralsNeedBothSlashesAndKnownFlags() {
        assertEquals("/a/gi", SelectorStringParser.regexLiteral("/a/gi"))
        assertEquals("/a/", SelectorStringParser.regexLiteral("/a/"))
        assertNull(SelectorStringParser.regexLiteral("/a"))
        assertNull(SelectorStringParser.regexLiteral("a/b/"))
        assertNull(SelectorStringParser.regexLiteral("/a/x"))
        assertNull(SelectorStringParser.regexLiteral("//"))
        assertEquals(0, SelectorStringParser.editDistance("text", "text"))
        assertEquals(1, SelectorStringParser.editDistance("text", "txt"))
        assertEquals(3, SelectorStringParser.editDistance("abc", ""))
    }

    @Test
    fun anEmptyStringAddsNothing() {
        val selector = select("")
        assertTrue(selector.selector.filters.isEmpty())
        assertFalse(select(" \t ").selector.filters.isNotEmpty())
    }


    @Test
    fun integerBoundsReadAsMinAndMaxKeys() {
        assertEquals(listOf("deep"), names("minDepth=2"))
        assertEquals(listOf("login", "logout"), names("maxDepth=1 clickable"))
        assertEquals(listOf("signIn"), names("minIndexInParent=3"))
        assertEquals(listOf("login", "deep"), names("maxIndexInParent=0 clickable"))
        assertEquals("minDepth(2).maxDepth(3)", select("minDepth=2 maxDepth=3").toString())
        val e = error("minDepth=x")
        assertEquals(0, e.position)
        assertTrue(e.detail, "minDepth" in e.detail)
    }

}
