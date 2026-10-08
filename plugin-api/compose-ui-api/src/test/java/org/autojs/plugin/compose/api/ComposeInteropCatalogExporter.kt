package org.autojs.plugin.compose.api

import org.autojs.plugin.compose.api.interop.AndroidViewInteropV1
import org.autojs.plugin.compose.api.model.ValueKind
import java.io.File

/** Separate optional-extension projection. The frozen V1 exporter and its output remain unchanged. */
object ComposeInteropCatalogExporter {
    const val SOURCE = "org.autojs.plugin.compose.api.interop.AndroidViewInteropV1"
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: ComposeInteropCatalogExporter <output.json>" }
        val target = File(args.single())
        target.parentFile?.mkdirs()
        target.writeText(ComposeCatalogExporter.json(document()) + "\n", Charsets.UTF_8)
        println("Exported negotiated AndroidView interop catalog to ${target.absolutePath}")
    }
    fun document(): Map<String, Any?> {
        val component = AndroidViewInteropV1.component
        return linkedMapOf(
            "schemaVersion" to 1, "contractVersion" to 1,
            "extensionVersion" to AndroidViewInteropV1.VERSION, "feature" to AndroidViewInteropV1.FEATURE,
            "source" to SOURCE, "componentCount" to 1, "propertyCount" to component.props.size,
            "valueKinds" to ValueKind.entries.map { it.name }.sorted(),
            "components" to listOf(linkedMapOf(
                "name" to component.name, "invocation" to component.invocation.name,
                "children" to component.childrenPolicy.name, "scope" to component.scope.name,
                "commands" to component.commands.sorted(),
                "properties" to component.props.sortedBy { it.name }.map { property ->
                    require(property.defaultValue == null) { "Interop defaults need an explicit encoder before extension" }
                    val type = property.type
                    linkedMapOf("name" to property.name, "type" to linkedMapOf(
                        "kinds" to type.kinds.map { it.name }.sorted(), "enumValues" to type.enumValues.sorted(),
                        "minimum" to type.minimum, "maximum" to type.maximum, "minimumExclusive" to type.minimumExclusive,
                        "integral" to type.integral, "listKinds" to type.listKinds.map { it.name }.sorted(),
                        "listMinimum" to type.listMinimum, "listMaximum" to type.listMaximum,
                    ), "required" to property.required, "default" to null, "aliases" to property.aliases.sorted(), "update" to property.update.name)
                },
                "slots" to component.slots.sortedBy { it.name }.map { linkedMapOf("name" to it.name, "required" to it.required) },
                "events" to component.events.sortedBy { it.name }.map { event -> linkedMapOf(
                    "name" to event.name, "payload" to event.payloadFields.sortedBy { it.name }.map {
                        linkedMapOf("name" to it.name, "type" to it.type.name, "required" to it.required)
                    },
                ) },
            )),
            "scriptProjection" to mapOf("AndroidView.view" to linkedMapOf(
                "input" to "View | ((context: android.content.Context) => View)", "output" to "View", "wire" to "leaseId",
            )),
        )
    }
}
