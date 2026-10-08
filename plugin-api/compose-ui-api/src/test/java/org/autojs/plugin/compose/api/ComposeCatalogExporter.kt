package org.autojs.plugin.compose.api

import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.catalog.EventSpec
import org.autojs.plugin.compose.api.catalog.ValueType
import org.autojs.plugin.compose.api.model.UiValue
import org.autojs.plugin.compose.api.model.ValueKind
import java.io.File

/** Build tooling only: read the actual frozen objects, never parse Kotlin source or ship in the AAR. */
object ComposeCatalogExporter {
    const val SCHEMA_VERSION = 1
    const val SOURCE = "org.autojs.plugin.compose.api.catalog.ComponentCatalog.V1"

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: ComposeCatalogExporter <output.json>" }
        val target = File(args.single())
        target.parentFile?.mkdirs()
        target.writeText(render(), Charsets.UTF_8)
        println("Exported ${ComponentCatalog.V1.components.size} components to ${target.absolutePath}; external declarations/docs have not been checked.")
    }

    fun document(catalog: ComponentCatalog = ComponentCatalog.V1, version: Int = 1, source: String = SOURCE): Map<String, Any?> = linkedMapOf(
        "schemaVersion" to SCHEMA_VERSION,
        "contractVersion" to version,
        "source" to source,
        "componentCount" to catalog.components.size,
        "propertyCount" to catalog.components.sumOf { it.props.size },
        "valueKinds" to ValueKind.entries.map { it.name }.sorted(),
        "components" to catalog.components.sortedBy { it.name }.map { component ->
            linkedMapOf(
                "name" to component.name,
                "invocation" to component.invocation.name,
                "children" to component.childrenPolicy.name,
                "scope" to component.scope.name,
                "commands" to component.commands.sorted(),
                "properties" to component.props.sortedBy { it.name }.map { property ->
                    linkedMapOf(
                        "name" to property.name,
                        "type" to type(property.type),
                        "required" to property.required,
                        "default" to encodeValue(property.defaultValue),
                        "aliases" to property.aliases.sorted(),
                        "update" to property.update.name,
                    )
                },
                "slots" to component.slots.sortedBy { it.name }.map { linkedMapOf("name" to it.name, "required" to it.required) },
                "events" to component.events.sortedBy { it.name }.map(::event),
            )
        },
    )

    fun render(): String = json(document()) + "\n"

    private fun type(type: ValueType): Map<String, Any?> = linkedMapOf(
        "kinds" to type.kinds.map { it.name }.sorted(),
        "enumValues" to type.enumValues.sorted(),
        "minimum" to type.minimum,
        "maximum" to type.maximum,
        "minimumExclusive" to type.minimumExclusive,
        "integral" to type.integral,
        "listKinds" to type.listKinds.map { it.name }.sorted(),
        "listMinimum" to type.listMinimum,
        "listMaximum" to type.listMaximum,
    )

    private fun event(event: EventSpec): Map<String, Any?> = linkedMapOf(
        "name" to event.name,
        "payload" to event.payloadFields.sortedBy { it.name }.map {
            linkedMapOf("name" to it.name, "type" to it.type.name, "required" to it.required)
        },
    )

    /** null means unspecified; {kind:NULL,value:null} means an explicitly specified null default. */
    private fun encodeValue(value: UiValue?): Map<String, Any?>? {
        if (value == null) return null
        val content: Any? = when (value) {
            is UiValue.Str -> value.value
            is UiValue.Num -> value.value
            is UiValue.Bool -> value.value
            is UiValue.Color -> value.argb
            is UiValue.Dp -> value.value
            is UiValue.Sp -> value.value
            is UiValue.Enum -> value.value
            is UiValue.IconName -> value.value
            is UiValue.Shape -> linkedMapOf("shape" to value.shape.name, "radius" to value.radius)
            is UiValue.TextStyle -> linkedMapOf("fontFamily" to value.fontFamily, "fontSize" to value.fontSize,
                "fontWeight" to value.fontWeight, "italic" to value.italic, "lineHeight" to value.lineHeight, "letterSpacing" to value.letterSpacing)
            is UiValue.ListOf -> value.values.map(::encodeValue)
            UiValue.Null -> null
            is UiValue.BitmapRef -> error("Catalog defaults cannot contain a process-local bitmap")
        }
        return linkedMapOf("kind" to value.kind.name, "value" to content)
    }

    /** Small deterministic encoder avoids depending on Android's stub JSONObject in this JVM tool. */
    internal fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (char.code < 32 || char.isSurrogate()) append("\\u" + char.code.toString(16).padStart(4, '0')) else append(char)
                }
            }
            append('"')
        }
        is Boolean, is Int, is Long -> value.toString()
        is Double -> { require(value.isFinite()); value.toString() }
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (key, item) -> require(key is String); "${json(key)}:${json(item)}" }
        is List<*> -> value.joinToString(",", "[", "]", transform = ::json)
        else -> error("Unsupported catalog JSON type: ${value.javaClass.name}")
    }
}
