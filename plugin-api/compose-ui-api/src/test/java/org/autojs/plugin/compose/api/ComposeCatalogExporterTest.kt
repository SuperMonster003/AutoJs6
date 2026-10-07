package org.autojs.plugin.compose.api

import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.junit.Assert.*
import org.junit.Test

class ComposeCatalogExporterTest {
    @Suppress("UNCHECKED_CAST")
    @Test fun exportContainsEveryFrozenComponentAndItsCanonicalProperties() {
        val exported = ComposeCatalogExporter.document()
        val components = exported["components"] as List<Map<String, Any?>>
        assertEquals(1, exported["contractVersion"])
        assertEquals(ComponentCatalog.V1.components.map { it.name }.sorted(), components.map { it["name"] })
        for (component in components) {
            val actual = ComponentCatalog.V1.requireComponent(component["name"] as String)
            val properties = component["properties"] as List<Map<String, Any?>>
            assertEquals(actual.props.map { it.name }.sorted(), properties.map { it["name"] })
            assertEquals(actual.invocation.name, component["invocation"])
            for (property in properties) {
                val definition = actual.props.single { it.name == property["name"] }
                val type = property["type"] as Map<String, Any?>
                assertEquals(definition.type.kinds.map { it.name }.sorted(), type["kinds"])
                assertEquals(definition.type.enumValues.sorted(), type["enumValues"])
                assertEquals(definition.required, property["required"])
                assertEquals(definition.aliases.sorted(), property["aliases"])
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun defaultsKeepUnspecifiedAndExplicitNullDistinctAndRetainListBounds() {
        val components = ComposeCatalogExporter.document()["components"] as List<Map<String, Any?>>
        fun property(component: String, name: String) = (components.single { it["name"] == component }["properties"] as List<Map<String, Any?>>).single { it["name"] == name }
        assertNull(property("Text", "color")["default"])
        assertEquals(mapOf("kind" to "NULL", "value" to null), property("LinearProgressIndicator", "progress")["default"])
        val range = property("Slider", "range")["type"] as Map<String, Any?>
        assertEquals(listOf("NUMBER"), range["listKinds"])
        assertEquals(2, range["listMinimum"])
        assertEquals(2, range["listMaximum"])
        assertEquals("EDIT_COMMAND", property("TextField", "text")["update"])
    }

    @Test fun outputIsDeterministicAndEncodesUnicodeWithoutDroppingUtf16Units() {
        assertEquals(ComposeCatalogExporter.render(), ComposeCatalogExporter.render())
        assertEquals("\"line\\n\\\"\\\\\\t\\ud83d\\ude03\\u0001\"", ComposeCatalogExporter.json("line\n\"\\\t\uD83D\uDE03\u0001"))
    }
}
