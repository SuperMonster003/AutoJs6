package org.autojs.autojs.core.plugin.compose

import org.autojs.autojs6.R
import org.autojs.plugin.compose.api.ComposeUiErrorCodes as Codes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ComposeErrorMessagesTest {
    private val resources = sequenceOf(File("src/main/res"), File("app/src/main/res"), File("../app/src/main/res"))
        .first { File(it, "values/strings.xml").isFile }

    @Test fun everyFrozenCodeSelectsTheExpectedTranslatedPrompt() {
        val expected = mapOf(
            Codes.PLUGIN_UNAVAILABLE to "error_plugin_not_found_in_installed_apps",
            Codes.PLUGIN_DISABLED to "error_plugin_is_not_enabled_in_plugin_center",
            Codes.PLUGIN_UNAUTHORIZED to "error_plugin_is_not_authorized_in_plugin_center",
            Codes.PLUGIN_INCOMPATIBLE to "error_compose_plugin_incompatible",
            Codes.UI_MODE_REQUIRED to "error_compose_ui_mode_required",
            Codes.PERMISSION_REQUIRED to "error_compose_permission_required",
            Codes.INVALID_ARGUMENT to "error_compose_invalid_argument",
            Codes.UNKNOWN_COMPONENT to "error_compose_unknown_component",
            Codes.UNKNOWN_PROP to "error_compose_unknown_prop",
            Codes.INVALID_MODIFIER to "error_compose_invalid_modifier",
            Codes.SCOPE_MISMATCH to "error_compose_scope_mismatch",
            Codes.DUPLICATE_KEY to "error_compose_duplicate_key",
            Codes.LIMIT_EXCEEDED to "error_compose_limit_exceeded",
            Codes.RENDER_FAILED to "error_compose_render_failed",
            Codes.SESSION_CLOSED to "error_compose_session_closed",
            Codes.NODE_DETACHED to "error_compose_node_detached",
            Codes.RENDERER_FAILED to "error_compose_renderer_failed",
            Codes.INTERNAL to "error_compose_internal",
        )
        val codes = Codes::class.java.declaredFields.filter { it.type == String::class.java }.map { it.get(null) as String }.toSet()
        assertEquals(codes, expected.keys)
        expected.forEach { (code, name) ->
            assertEquals(code, R.string::class.java.getDeclaredField(name).getInt(null), ComposeUiError.messageResource(code))
        }
    }

    @Test fun allElevenResourceDirectoriesHaveSortedCompleteComposeErrorsAndMatchingPlaceholders() {
        val locales = listOf("values", "values-en", "values-ar", "values-es", "values-fr", "values-ja", "values-ko",
            "values-ru", "values-zh", "values-zh-rHK", "values-zh-rTW")
        val translated = locales.associateWith { locale ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resources, "$locale/strings.xml"))
            val nodes = document.getElementsByTagName("string")
            val entries = (0 until nodes.length).map { nodes.item(it) as Element }
                .filter { it.getAttribute("name").startsWith("error_compose_") }
                .map { it.getAttribute("name") to it.textContent }
            assertEquals(locale, entries.map { it.first }.sorted(), entries.map { it.first })
            assertEquals(locale, entries.size, entries.map { it.first }.distinct().size)
            entries.toMap()
        }
        val english = translated.getValue("values")
        assertEquals(18, english.size)
        assertEquals(english, translated.getValue("values-en"))
        translated.forEach { (locale, strings) ->
            assertEquals(locale, english.keys, strings.keys)
            strings.forEach { (name, text) ->
                assertTrue("$locale/$name", text.isNotBlank())
                val placeholders = Regex("%[1-9]\\$[ds]")
                assertEquals("$locale/$name", placeholders.findAll(english.getValue(name)).map { it.value }.toList(),
                    placeholders.findAll(text).map { it.value }.toList())
            }
        }
    }
}
