package org.autojs.autojs.runtime.api.augment.ui

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

class TypeScriptTsxRuntimeTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevel

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
            requireNotNull(TypeScriptTsxRuntime.renderOrNull(root)).xml,
        )
        assertNull(TypeScriptTsxRuntime.renderOrNull("<vertical />"))
    }

    @Test
    fun factoryRejectsUnsupportedEventsObjectsAndInvalidXmlText() {
        assertThrows(IllegalArgumentException::class.java) {
            element("button", props("onLongClick" to object : BaseFunction() {}))
        }
        assertThrows(IllegalArgumentException::class.java) {
            element("text", props("text" to props("nested" to true)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            element("text", null, "bad\u0000text")
        }
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
            TypeScriptTsxRuntime.renderOrNull(duplicateIdRoot)
        }

        val multipleRoots = element(
            TypeScriptTsxRuntime.FragmentToken,
            null,
            element("text", props("text" to "one")),
            element("text", props("text" to "two")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            TypeScriptTsxRuntime.renderOrNull(multipleRoots)
        }

        assertThrows(IllegalArgumentException::class.java) {
            TypeScriptTsxRuntime.renderOrNull(element("vertical", null, "text"))
        }
    }

    private fun element(
        type: Any,
        props: NativeObject?,
        vararg children: Any?,
    ): TypeScriptTsxRuntime.Value = TypeScriptTsxRuntime.create(
        arrayOf(type, props, *children),
    )

    private fun props(vararg entries: Pair<String, Any?>): NativeObject =
        RhinoUtils.newNativeObject(scope).also { value ->
            entries.forEach { (name, property) ->
                ScriptableObject.putProperty(value, name, property)
            }
        }
}
