package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.modules.colors.Colors
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiLimits
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.model.ShapeKind
import org.autojs.plugin.compose.api.model.UiValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

class ComposeScriptValuesTest {
    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private lateinit var values: ComposeScriptValues

    @Before
    fun enterRhino() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
        with(RhinoUtils) { context.registerStandardObjects(scope) }
        // Reuse the colors hex path. Named-color fixtures avoid ColorTable's Android application lookup.
        values = ComposeScriptValues(colorParser = ::colorWithoutAndroid)
    }

    @After
    fun exitRhino() = Context.exit()

    @Test
    fun aliasesAndShortcutsFollowFrozenCatalogOrder() {
        val props = values.objectProperties(evaluate("({testTag:'body', alpha:0.5, padding:[8,'16dp'], w:120, verticalArrangement:'center'})"), "Column")
        val result = values.normalize("Column", props)
        assertEquals(UiValue.Enum("center"), result.props["arrangement"])
        assertEquals(listOf("width", "padding", "alpha", "testTag"), result.modifier.map { it.name })
        assertEquals(listOf(UiValue.Dp(8.0), UiValue.Dp(16.0)), result.modifier[1].args)
        assertEquals("checked", values.property("RadioButton", "selected", true).name)
        assertEquals("text", values.property("TextField", "value", "input").name)
        assertEquals("valueChange", values.eventName("TextField", "onValueChange"))
        assertEquals("click", values.eventName("Text", "click"))
    }

    @Test
    fun conflictingAliasesAndUnknownPropertiesCarryComponentAndProperty() {
        error("Column", "arrangement", ComposeUiErrorCodes.INVALID_ARGUMENT) {
            values.normalize("Column", mapOf("arrangement" to "top", "verticalArrangement" to "bottom"))
        }
        error("Text", "selected", ComposeUiErrorCodes.UNKNOWN_PROP) { values.property("Text", "selected", true) }
        error("Text", "onCheckedChange", ComposeUiErrorCodes.UNKNOWN_PROP) { values.eventName("Text", "onCheckedChange") }
        error("Unknown", "text", ComposeUiErrorCodes.UNKNOWN_COMPONENT) { values.property("Unknown", "text", "value") }
    }

    @Test
    fun dimensionsRejectWrongUnitsNegativeSizesAndNonfiniteValues() {
        assertEquals(UiValue.Dp(8.5), values.property("Column", "spacing", "8.5dp").value)
        assertEquals(UiValue.Sp(16.0), values.property("Text", "fontSize", "16sp").value)
        assertEquals(UiValue.Sp(16.0), values.property("Text", "fontSize", 16).value)
        for (raw in listOf(-1, "-1dp", "8sp", "8", Double.NaN, Double.POSITIVE_INFINITY, "1e50dp")) {
            error("Column", "spacing", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Column", "spacing", raw) }
        }
        error("Text", "fontSize", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Text", "fontSize", "12dp") }
        error("Text", "fontSize", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Text", "fontSize", 0) }
        val offset = ComposeModifier(values, scope.globalThis).append("offset", arrayOf<Any?>(-3, "-4dp")).resolve { 1 }.single()
        assertEquals(listOf(UiValue.Dp(-3.0), UiValue.Dp(-4.0)), offset.args)
    }

    @Test
    fun colorsUseExistingColorsVocabularyAndArgbSemantics() {
        for (raw in listOf("#f81", "#80223344", "red", "TRANSPARENT", -1, 0xFF112233L)) {
            val converted = values.property("Text", "color", raw).value as UiValue.Color
            assertEquals(colorWithoutAndroid(raw), converted.argb)
        }
        assertEquals(UiValue.Null, values.property("Text", "color", null).value)
        for (raw in listOf("#12345", "definitely-not-a-color", Double.NaN, false)) {
            error("Text", "color", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Text", "color", raw) }
        }
        error("Text", "color", ComposeUiErrorCodes.INVALID_ARGUMENT) {
            ComposeScriptValues().property("Text", "color", "#invalid")
        }
    }

    @Test
    fun stringsRespectUtf16LimitAndKeepSupplementaryCharacters() {
        val exact = "\uD83D\uDE42".repeat(ComposeUiLimits.MAX_STRING_CHARS / 2)
        val converted = values.property("Text", "text", exact).value as UiValue.Str
        assertEquals(exact, converted.value)
        assertEquals(exact, values.toRaw(converted, scope.globalThis))
        error("Text", "text", ComposeUiErrorCodes.LIMIT_EXCEEDED) { values.property("Text", "text", "$exact!") }
        error("Text", "text", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Text", "text", 42) }
        error("Text", "text", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Text", "text", Undefined.instance) }
    }

    @Test
    fun textStylesShapesAndEnumsStayTyped() {
        val style = values.property("Text", "style", evaluate("({fontFamily:'serif',fontSize:'18sp',fontWeight:600,italic:true,lineHeight:24,letterSpacing:'-0.25sp'})")).value as UiValue.TextStyle
        assertEquals("serif", style.fontFamily)
        assertEquals(18.0, style.fontSize)
        assertEquals(600, style.fontWeight)
        assertEquals(true, style.italic)
        assertEquals(-0.25, style.letterSpacing)
        val source = values.toRaw(style, scope.globalThis) as Scriptable
        assertEquals("serif", source.get("fontFamily", source))
        assertEquals(UiValue.Enum("headlineSmall"), values.property("Text", "style", "headlineSmall").value)
        assertEquals(UiValue.Shape(ShapeKind.CIRCLE), values.property("Card", "shape", "circle").value)
        assertEquals(UiValue.Shape(ShapeKind.ROUNDED, 12.0), values.property("Card", "shape", evaluate("({shape:'rounded',radius:'12dp'})")).value)
        assertEquals(UiValue.IconName("Filled.Home"), values.property("Icon", "name", "Filled.Home").value)
        error("Text", "style.size", ComposeUiErrorCodes.UNKNOWN_PROP) { values.property("Text", "style", mapOf("size" to 10)) }
        error("Text", "style.fontWeight", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Text", "style", mapOf("fontWeight" to 500.5)) }
        error("Text", "overflow", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Text", "overflow", "truncate") }
        error("Card", "shape", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Card", "shape", mapOf("shape" to "circle", "radius" to 2)) }
    }

    @Test
    fun listAritySparseArraysAndBoundsAreValidated() {
        for (raw in listOf(listOf(1, 2, 3), listOf(-1), -1)) {
            error("LazyColumn", "contentPadding", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("LazyColumn", "contentPadding", raw) }
        }
        assertEquals(UiValue.ListOf(listOf(UiValue.Dp(8.0), UiValue.Dp(16.0))), values.property("LazyColumn", "contentPadding", evaluate("[8,'16dp']")).value)
        error("Slider", "range", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Slider", "range", evaluate("[0,,1]")) }
        error("Slider", "range", ComposeUiErrorCodes.LIMIT_EXCEEDED) { values.property("Slider", "range", evaluate("new Array(2001)")) }
        error("Column", "padding", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.property("Column", "padding", listOf(1, 2, 3)) }
    }

    @Test
    fun normalizedPropertiesCanBeReusedWithoutMutableSourceObjects() {
        val style = evaluate("({fontSize:18})") as Scriptable
        val padding = evaluate("[8,12]") as NativeArray
        val normalizedStyle = values.property("Text", "style", style).value
        val normalizedPadding = values.property("Text", "padding", padding).value
        style.put("fontSize", style, 99)
        padding.put(0, padding, 99)
        val result = values.normalize("Text", mapOf("style" to normalizedStyle, "padding" to normalizedPadding))
        assertEquals(UiValue.TextStyle(fontSize = 18.0), result.props["style"])
        assertEquals(listOf(UiValue.Dp(8.0), UiValue.Dp(12.0)), result.modifier.single().args)
    }

    @Test
    fun imageSourcesAreResolvedAtTheInjectedBoundaryAndErrorsKeepTheirCause() {
        val marker = IllegalStateException("resolver failure")
        val received = ArrayList<Any>()
        val resolverValues = ComposeScriptValues(imageResolver = { received.add(it); throw marker })
        for (source in listOf("/sdcard/image.png", "@drawable/ic_launcher", Any())) {
            val error = error("Image", "src", ComposeUiErrorCodes.INVALID_ARGUMENT) { resolverValues.property("Image", "src", source) }
            assertSame(source, received.last())
            assertSame(marker, error.cause)
        }
        error("Image", "src", ComposeUiErrorCodes.INVALID_ARGUMENT) {
            resolverValues.property("Image", "src", ConsString("/sdcard/", "image.png"))
        }
        assertEquals("/sdcard/image.png", received.last())
        assertTrue(received.last() is String)
    }

    @Test
    fun themeRolesAndTypographyAreConvertedAndUnknownRolesAreRejected() {
        val theme = values.theme(evaluate("({seed:'#102030',colors:{primary:'#405060'},dark:'system',dynamicColor:true,typography:{fontFamily:'serif',scale:1.2}})"))
        assertEquals(0xFF102030.toInt(), theme.seedArgb)
        assertEquals(mapOf("primary" to 0xFF405060.toInt()), theme.colorOverrides)
        assertEquals(null, theme.dark)
        assertTrue(theme.dynamicColor)
        assertEquals("serif", theme.fontFamily)
        assertEquals(1.2, theme.fontScale)
        assertEquals(null, values.theme(mapOf("dark" to ConsString("sys", "tem"))).dark)
        error("Theme", "colors.typo", ComposeUiErrorCodes.UNKNOWN_PROP) { values.theme(mapOf("colors" to mapOf("typo" to "red"))) }
        error("Theme", "dark", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.theme(mapOf("dark" to "yes")) }
        error("Theme", "typography.scale", ComposeUiErrorCodes.INVALID_ARGUMENT) { values.theme(mapOf("typography" to mapOf("scale" to 0))) }
    }

    @Test
    fun modifierMethodsExactlyMatchCatalogAndBranchesAreImmutable() {
        val base = ComposeModifier(values, scope.globalThis)
        assertEquals(ComponentCatalog.V1.modifiers.map { it.name }.toSet(), base.ids.toSet())
        scope.globalThis.put("base", scope.globalThis, base)
        val first = evaluate("base.padding(8).background('#fff').weight(1)") as ComposeModifier
        val second = evaluate("base.background('#000').padding(4)") as ComposeModifier
        assertEquals(emptyList<String>(), base.resolve { 1 }.map { it.name })
        assertEquals(listOf("padding", "background", "weight"), first.resolve { 1 }.map { it.name })
        assertEquals(listOf("background", "padding"), second.resolve { 1 }.map { it.name })
        val combined = values.normalize("Text", mapOf("padding" to 2), first)
        assertEquals(listOf("padding", "padding", "background", "weight"), combined.modifier.map { it.name })
    }

    @Test
    fun modifierCallbacksAllocatePerNodeWhileDeclarationStaysReusable() {
        val function = evaluate("(function() {})") as Function
        val chain = ComposeModifier(values, scope.globalThis).append("clickable", arrayOf<Any?>(function, false))
        var allocated = 0
        val first = chain.resolve { assertSame(function, it); ++allocated }
        val second = chain.resolve { assertSame(function, it); ++allocated }
        assertEquals(UiValue.Num(1.0), first.single().args[0])
        assertEquals(UiValue.Num(2.0), second.single().args[0])
        assertEquals(UiValue.Bool(false), first.single().args[1])
        error("Modifier", "clickable", ComposeUiErrorCodes.INVALID_ARGUMENT) { ComposeModifier(values, scope.globalThis).append("clickable", arrayOf(12)) }
    }

    @Test
    fun modifierArgumentErrorsAndCombinedChainLimitsAreBounded() {
        val base = ComposeModifier(values, scope.globalThis)
        error("Modifier", "padding", ComposeUiErrorCodes.INVALID_ARGUMENT) { base.append("padding", arrayOf(1, 2, 3)) }
        error("Modifier", "alpha", ComposeUiErrorCodes.INVALID_ARGUMENT) { base.append("alpha", arrayOf(1.1)) }
        error("Modifier", "weight", ComposeUiErrorCodes.INVALID_ARGUMENT) { base.append("weight", arrayOf(0)) }
        val maximum = (1..ComposeUiLimits.MAX_MODIFIER_OPS).fold(base) { chain, _ -> chain.append("width", arrayOf(1)) }
        error("Modifier", "width", ComposeUiErrorCodes.LIMIT_EXCEEDED) { maximum.append("width", arrayOf(1)) }
        error("Text", "modifier", ComposeUiErrorCodes.LIMIT_EXCEEDED) { values.normalize("Text", mapOf("padding" to 1), maximum) }
    }

    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "compose-values", 1, null)

    private fun colorWithoutAndroid(raw: Any): Int {
        val value = when (raw) {
            "red" -> "#FF0000"
            "TRANSPARENT" -> "#00000000"
            is String -> raw.also { require(it.startsWith('#') || it.toLongOrNull() != null) }
            else -> raw
        }
        return Colors.toFullHexRhino(value).substring(1).toLong(16).toInt()
    }

    private fun error(component: String, prop: String, code: String, action: () -> Unit): ComposeUiContractException {
        val error = assertThrows(ComposeUiContractException::class.java, action)
        assertEquals(code, error.code)
        assertEquals(prop, error.prop)
        assertTrue("Missing component/property context: ${error.message}", error.message!!.contains("$component.$prop"))
        return error
    }
}
