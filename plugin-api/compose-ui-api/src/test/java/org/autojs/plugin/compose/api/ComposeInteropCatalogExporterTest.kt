package org.autojs.plugin.compose.api

import org.autojs.plugin.compose.api.interop.AndroidViewInteropV1
import org.junit.Assert.*
import org.junit.Test

class ComposeInteropCatalogExporterTest {
    @Test fun optionalExportSeparatesItsScriptViewProjectionFromTheFrozenWireKind() {
        val before = ComposeCatalogExporter.render()
        val document = ComposeInteropCatalogExporter.document()
        assertEquals(1, document["contractVersion"]); assertEquals(1, document["extensionVersion"])
        assertEquals(AndroidViewInteropV1.FEATURE, document["feature"])
        assertEquals(1, document["componentCount"]); assertEquals(1, document["propertyCount"])
        @Suppress("UNCHECKED_CAST")
        val projection = document["scriptProjection"] as Map<String, Map<String, String>>
        assertEquals("View | ((context: android.content.Context) => View)", projection.getValue("AndroidView.view")["input"])
        assertEquals("View", projection.getValue("AndroidView.view")["output"])
        assertEquals("leaseId", projection.getValue("AndroidView.view")["wire"])
        assertEquals(before, ComposeCatalogExporter.render())
    }
}
