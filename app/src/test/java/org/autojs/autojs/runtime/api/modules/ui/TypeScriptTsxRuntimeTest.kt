package org.autojs.autojs.runtime.api.modules.ui

import org.autojs.autojs6.R
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.TopLevel
import java.io.File

class TypeScriptTsxRuntimeTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevel
    private val messages = TypeScriptTsxRuntime.MessageResolver { resourceId, formatArgs ->
        "$resourceId:${formatArgs.joinToString("|")}"
    }

    @Before
    fun enterContext() {
        context = Context.enter()
        scope = with(RhinoUtils) { context.getOrCreateStandardObjects() }
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun factoryRendersFragmentsEscapingTextAndGeneratedEventTargets() {
        val listener = object : BaseFunction() {}
        val text = element("text", null, "A<&\"'")
        val button = element(
            "button",
            props("onClick" to listener),
            "Run",
        )
        val fragment = element(
            TypeScriptTsxRuntime.FragmentToken,
            null,
            text,
            button,
        )
        val root = element("vertical", props("padding" to 8), fragment)

        assertEquals(
            "<vertical padding=\"8\"><text text=\"A&lt;&amp;&quot;&apos;\" />" +
                "<button id=\"__autojs6_tsx_0\" text=\"Run\" /></vertical>",
            requireNotNull(TypeScriptTsxRuntime.renderOrNull(root, messages)).xml,
        )
        assertNull(TypeScriptTsxRuntime.renderOrNull("<vertical />", messages))
    }

    @Test
    fun factoryRejectsUnsupportedEventsObjectsAndInvalidXmlText() {
        val unsupportedEvent = assertThrows(IllegalArgumentException::class.java) {
            element("button", props("onLongClick" to object : BaseFunction() {}))
        }
        assertEquals(
            "${R.string.error_typescript_tsx_event_unsupported}:onLongClick",
            unsupportedEvent.message,
        )
        assertThrows(IllegalArgumentException::class.java) {
            element("text", props("text" to props("nested" to true)))
        }
        val invalidText = assertThrows(IllegalArgumentException::class.java) {
            element("text", null, "bad\u0000text")
        }
        assertEquals(
            "${R.string.error_typescript_tsx_invalid_xml_text}:",
            invalidText.message,
        )
    }

    @Test
    fun rendererRejectsAmbiguousRootsDuplicateIdsAndContainerText() {
        val duplicateIdRoot = element(
            "vertical",
            null,
            element("text", props("id" to "same", "text" to "one")),
            element("text", props("id" to "same", "text" to "two")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            TypeScriptTsxRuntime.renderOrNull(duplicateIdRoot, messages)
        }

        val multipleRoots = element(
            TypeScriptTsxRuntime.FragmentToken,
            null,
            element("text", props("text" to "one")),
            element("text", props("text" to "two")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            TypeScriptTsxRuntime.renderOrNull(multipleRoots, messages)
        }

        assertThrows(IllegalArgumentException::class.java) {
            TypeScriptTsxRuntime.renderOrNull(element("vertical", null, "text"), messages)
        }
    }

    @Test
    fun neutralFragmentsKeepLegacyNumbersBooleansWhitespaceAndArrayRules() {
        val values = context.newArray(scope, arrayOf<Any?>("before", 7, true, false, null))
        val fragment = element(TypeScriptTsxRuntime.FragmentToken, null, values)
        ScriptableObject.putProperty(values, 0, "after")
        assertEquals("<text text=\"before7\" />", TypeScriptTsxRuntime.renderOrNull(element("text", null, fragment), messages)?.xml)
        val whitespace = element(TypeScriptTsxRuntime.FragmentToken, null, " ", element("text", null, "root"))
        assertEquals("<text text=\"root\" />", TypeScriptTsxRuntime.renderOrNull(whitespace, messages)?.xml)
        val javaCollection = element(TypeScriptTsxRuntime.FragmentToken, null, listOf("java collection"))
        assertEquals("<text text=\"java collection\" />", TypeScriptTsxRuntime.renderOrNull(element("text", null, javaCollection), messages)?.xml)
        val tooWide = element(TypeScriptTsxRuntime.FragmentToken, null, context.newArray(scope, 4097))
        val failure = assertThrows(IllegalArgumentException::class.java) { element("vertical", null, tooWide) }
        assertEquals("${R.string.error_typescript_tsx_child_array_too_large}:", failure.message)
    }

    @Test
    fun validationMessagesAreResourceBackedAcrossEverySupportedLocale() {
        val root = hostRoot()
        val source = root.resolve(
            "app/src/main/java/org/autojs/autojs/runtime/api/modules/ui/TypeScriptTsxRuntime.kt",
        ).readText()
        val referencedNames = Regex("""R\.string\.(error_typescript_tsx_[a-z0-9_]+)""")
            .findAll(source)
            .map { match -> match.groupValues[1] }
            .toSet()

        assertEquals(29, referencedNames.size)
        val localizedMessages = RESOURCE_DIRECTORIES.associateWith { directory ->
            val strings = root.resolve("app/src/main/res/$directory/strings.xml").readText()
            Regex("""<string name="(error_typescript_tsx_[a-z0-9_]+)">([^<]*)</string>""")
                .findAll(strings)
                .associate { match -> match.groupValues[1] to match.groupValues[2] }
        }
        localizedMessages.forEach { (directory, messages) ->
            assertEquals("Incomplete TSX messages in $directory", referencedNames, messages.keys)
        }
        assertEquals(localizedMessages.getValue("values"), localizedMessages.getValue("values-en"))
        assertEquals(
            emptyList<String>(),
            source.lineSequence()
                .filter { line -> line.contains('"') && line.contains("TypeScript TSX") }
                .toList(),
        )
    }

    private fun element(
        type: Any,
        props: NativeObject?,
        vararg children: Any?,
    ): Any = TypeScriptTsxRuntime.create(
        arrayOf(type, props, *children),
        messages,
    )

    private fun props(vararg entries: Pair<String, Any?>): NativeObject =
        RhinoUtils.newNativeObject(scope).also { value ->
            entries.forEach { (name, property) ->
                ScriptableObject.putProperty(value, name, property)
            }
        }

    private fun hostRoot(): File {
        val workingDirectory = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        return checkNotNull(
            generateSequence(workingDirectory) { directory -> directory.parentFile }
                .firstOrNull { directory ->
                    directory.resolve("settings.gradle.kts").isFile &&
                        directory.resolve("app/src/main/res/values/strings.xml").isFile
                },
        ) { "Unable to locate the AutoJs6 root" }
    }

    companion object {
        private val RESOURCE_DIRECTORIES = listOf(
            "values",
            "values-en",
            "values-ar",
            "values-es",
            "values-fr",
            "values-ja",
            "values-ko",
            "values-ru",
            "values-zh",
            "values-zh-rHK",
            "values-zh-rTW",
        )
    }
}
