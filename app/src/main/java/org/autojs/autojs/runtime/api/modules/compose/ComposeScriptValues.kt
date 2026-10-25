package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.runtime.api.modules.colors.Colors
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiLimits
import org.autojs.plugin.compose.api.ComposeUiThemeColors
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.catalog.ValueType
import org.autojs.plugin.compose.api.model.ModifierOp
import org.autojs.plugin.compose.api.model.ShapeKind
import org.autojs.plugin.compose.api.model.ThemeSpec
import org.autojs.plugin.compose.api.model.UiValue
import org.autojs.plugin.compose.api.model.ValueKind
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.VarScope

/** The only script-to-contract conversion path. Android image loading is supplied by the runtime. */
class ComposeScriptValues(
    private val imageResolver: (Any) -> ResolvedImage = {
        throw IllegalArgumentException("An image resolver is required")
    },
    private val colorParser: (Any) -> Int = { Colors.toIntRhino(it) },
) {
    /** Keep [owner] beside the node: BitmapRef deliberately does not own decoded pixel data. */
    data class ResolvedImage(val reference: UiValue.BitmapRef, val owner: Any)
    data class ConvertedProperty(val name: String, val value: UiValue, val retainedImages: List<Any> = emptyList())
    data class ConvertedProperties(
        val props: Map<String, UiValue>,
        val modifier: List<ModifierOp>,
        val retainedImages: List<Any> = emptyList(),
    )

    private val catalog = org.autojs.plugin.compose.api.v2.ComposeUiV2.interopCatalog

    fun objectProperties(value: Any?, component: String): Map<String, Any?> {
        val raw = unwrap(value)
        if (isNullish(raw)) return emptyMap()
        val result = LinkedHashMap<String, Any?>()
        when (raw) {
            is Map<*, *> -> {
                propertyCount(raw.size, component)
                raw.forEach { (key, entry) ->
                    if (key !is String) invalid(component, "props", "Object keys must be strings")
                    result[key] = entry
                }
            }
            is Scriptable -> {
                if (raw is NativeArray || raw is Function) invalid(component, "props", "Expected a property object")
                val ids = raw.ids
                propertyCount(ids.size, component)
                ids.forEach { id ->
                    if (id !is String) invalid(component, "props", "Object keys must be strings")
                    result[id] = ScriptableObject.getProperty(raw, id)
                }
            }
            else -> invalid(component, "props", "Expected a property object")
        }
        return result
    }

    private fun propertyCount(count: Int, component: String) {
        if (count > ComposeUiLimits.MAX_PROPERTIES_PER_NODE) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, component, "props", "Too many properties")
    }

    fun property(component: String, name: String, raw: Any?): ConvertedProperty = contextual(component, name) {
        val spec = catalog.component(component)
            ?: fail(ComposeUiErrorCodes.UNKNOWN_COMPONENT, component, name, "Unknown component")
        val shortcut = catalog.shortcuts.firstOrNull { it.name == name }
        val owners = ArrayList<Any>()
        if (shortcut != null) {
            val modifier = catalog.modifier(shortcut.modifierName)!!
            val value = if (shortcut.expandList && (isArray(raw) || raw is UiValue.ListOf)) {
                val arguments = if (raw is UiValue.ListOf) raw.values else arrayValues(raw, component, name)
                if (modifier.signatures.none { arguments.size in it.requiredCount..it.args.size }) {
                    invalid(component, name, "Unsupported shortcut argument count")
                }
                val signature = modifier.signatures.first { arguments.size in it.requiredCount..it.args.size }
                UiValue.ListOf(arguments.mapIndexed { index, argument -> convert(component, name, argument, signature.args[index].type, owners) })
            } else {
                convert(component, name, raw, modifier.signatures.first().args.first().type, owners)
            }
            return@contextual ConvertedProperty(name, value, owners)
        }
        val prop = spec.props.firstOrNull { it.name == name || name in it.aliases }
            ?: fail(ComposeUiErrorCodes.UNKNOWN_PROP, component, name, "Unknown property")
        ConvertedProperty(prop.name, convert(component, name, raw, prop.type, owners), owners)
    }

    /** key/ref/slots/events/children are removed by the node graph before this call. */
    fun normalize(
        component: String,
        raw: Map<String, Any?>,
        explicitModifier: ComposeModifier? = null,
        allocateCallback: (Function) -> Int = { invalid(component, "modifier", "A callback allocator is required") },
    ): ConvertedProperties {
        if (raw.size > ComposeUiLimits.MAX_PROPERTIES_PER_NODE) {
            fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, component, "props", "Too many properties")
        }
        val converted = LinkedHashMap<String, UiValue>()
        val owners = ArrayList<Any>()
        raw.forEach { (name, value) ->
            val prop = property(component, name, value)
            if (converted.put(prop.name, prop.value) != null) invalid(component, prop.name, "Conflicting property aliases")
            owners.addAll(prop.retainedImages)
        }
        return contextual(component, "modifier") {
            val normalized = catalog.normalizeProperties(component, converted, explicitModifier?.resolve(allocateCallback) ?: emptyList())
            ConvertedProperties(normalized.props, normalized.modifier, owners)
        }
    }

    fun eventName(component: String, name: String): String {
        val spec = catalog.component(component)
            ?: fail(ComposeUiErrorCodes.UNKNOWN_COMPONENT, component, name, "Unknown component")
        return spec.events.firstOrNull { it.name == name || callbackProperty(it.name) == name }?.name
            ?: fail(ComposeUiErrorCodes.UNKNOWN_PROP, component, name, "Unknown event")
    }

    fun theme(raw: Any?): ThemeSpec = contextual("Theme", "theme") {
        val fields = objectProperties(raw, "Theme")
        rejectUnknown(fields, setOf("seed", "colors", "dark", "dynamicColor", "typography"), "Theme")
        val colors = fields["colors"]?.let { value ->
            objectProperties(value, "Theme.colors").mapValues { (name, candidate) ->
                if (name !in ComposeUiThemeColors.names) fail(ComposeUiErrorCodes.UNKNOWN_PROP, "Theme", "colors.$name", "Unknown color role")
                color("Theme", "colors.$name", candidate)
            }
        } ?: emptyMap()
        val typography = fields["typography"]?.let { objectProperties(it, "Theme.typography") } ?: emptyMap()
        rejectUnknown(typography, setOf("fontFamily", "scale"), "Theme.typography")
        val darkValue = unwrap(fields["dark"])
        val dark = when {
            darkValue == null -> null
            darkValue is Boolean -> darkValue
            darkValue is CharSequence && string("Theme", "dark", darkValue) == "system" -> null
            else -> invalid("Theme", "dark", "Expected a boolean or system")
        }
        ThemeSpec(
            seedArgb = fields["seed"]?.let { color("Theme", "seed", it) },
            colorOverrides = colors,
            dark = dark,
            dynamicColor = fields["dynamicColor"]?.let { boolean("Theme", "dynamicColor", it) } ?: false,
            fontFamily = typography["fontFamily"]?.let { string("Theme", "typography.fontFamily", it) },
            fontScale = typography["scale"]?.let { number("Theme", "typography.scale", it).also { n ->
                if (n <= 0 || n > Float.MAX_VALUE) invalid("Theme", "typography.scale", "Expected a positive finite scale")
            } },
        )
    }

    internal fun modifierArgument(component: String, name: String, raw: Any?, type: ValueType): UiValue =
        contextual(component, name) { convert(component, name, raw, type, ArrayList()) }

    private fun convert(component: String, prop: String, original: Any?, type: ValueType, owners: MutableList<Any>): UiValue {
        val raw = unwrap(original)
        val value = when {
            raw is UiValue -> raw
            raw == null && ValueKind.NULL in type.kinds -> UiValue.Null
            isNullish(raw) -> invalid(component, prop, "A value is required")
            ValueKind.COLOR in type.kinds -> UiValue.Color(color(component, prop, raw))
            ValueKind.BITMAP in type.kinds -> imageResolver(if (raw is CharSequence) string(component, prop, raw) else raw!!)
                .also { owners.add(it.owner) }.reference
            ValueKind.TEXT_STYLE in type.kinds && raw !is CharSequence -> textStyle(component, prop, raw)
            ValueKind.SHAPE in type.kinds -> shape(component, prop, raw)
            ValueKind.LIST in type.kinds && isArray(raw) -> {
                val list = arrayValues(raw, component, prop)
                if (list.size !in type.listMinimum..type.listMaximum) invalid(component, prop, "Invalid list length")
                val itemType = ValueType(type.listKinds.ifEmpty { setOf(ValueKind.NUMBER) })
                UiValue.ListOf(list.map { convert(component, prop, it, itemType, owners) })
            }
            ValueKind.DP in type.kinds -> UiValue.Dp(dimension(component, prop, raw, "dp"))
            ValueKind.SP in type.kinds -> UiValue.Sp(dimension(component, prop, raw, "sp"))
            ValueKind.ENUM in type.kinds -> UiValue.Enum(string(component, prop, raw))
            ValueKind.ICON_NAME in type.kinds -> UiValue.IconName(string(component, prop, raw))
            ValueKind.STRING in type.kinds -> UiValue.Str(string(component, prop, raw))
            ValueKind.BOOLEAN in type.kinds -> UiValue.Bool(boolean(component, prop, raw))
            ValueKind.NUMBER in type.kinds -> UiValue.Num(number(component, prop, raw))
            else -> invalid(component, prop, "Unsupported value type")
        }
        if (!type.accepts(value)) invalid(component, prop, "Value does not match the component catalog")
        if (prop == "contentPadding") {
            if (value is UiValue.Dp && value.value < 0 || value is UiValue.ListOf &&
                (value.values.size !in setOf(1, 2, 4) || value.values.any { (it as UiValue.Dp).value < 0 })) {
                invalid(component, prop, "Expected one, two or four nonnegative dimensions")
            }
        }
        return value
    }

    private fun textStyle(component: String, prop: String, raw: Any?): UiValue.TextStyle {
        val fields = objectProperties(raw, "$component.$prop")
        rejectUnknown(fields, setOf("fontFamily", "fontSize", "fontWeight", "italic", "lineHeight", "letterSpacing"), component, prop)
        return UiValue.TextStyle(
            fontFamily = fields["fontFamily"]?.let { string(component, "$prop.fontFamily", it) },
            fontSize = fields["fontSize"]?.let { dimension(component, "$prop.fontSize", it, "sp") },
            fontWeight = fields["fontWeight"]?.let { integer(component, "$prop.fontWeight", it, 1, 1000) },
            italic = fields["italic"]?.let { boolean(component, "$prop.italic", it) },
            lineHeight = fields["lineHeight"]?.let { dimension(component, "$prop.lineHeight", it, "sp") },
            letterSpacing = fields["letterSpacing"]?.let { dimension(component, "$prop.letterSpacing", it, "sp") },
        )
    }

    private fun shape(component: String, prop: String, raw: Any?): UiValue.Shape {
        val fields = if (raw is CharSequence) mapOf("shape" to raw) else objectProperties(raw, "$component.$prop")
        rejectUnknown(fields, setOf("shape", "radius"), component, prop)
        val kind = when (string(component, "$prop.shape", fields["shape"])) {
            "rectangle" -> ShapeKind.RECTANGLE
            "rounded" -> ShapeKind.ROUNDED
            "circle" -> ShapeKind.CIRCLE
            else -> invalid(component, prop, "Expected rectangle, rounded or circle")
        }
        val radius = fields["radius"]?.let { dimension(component, "$prop.radius", it, "dp") } ?: 0.0
        return UiValue.Shape(kind, radius)
    }

    private fun color(component: String, prop: String, raw: Any?): Int = contextual(component, prop) {
        val unwrapped = unwrap(raw) ?: invalid(component, prop, "Expected a color")
        if (unwrapped is Number && !unwrapped.toDouble().isFinite()) invalid(component, prop, "Color number must be finite")
        colorParser(if (unwrapped is CharSequence) string(component, prop, unwrapped) else unwrapped)
    }

    private fun dimension(component: String, prop: String, raw: Any?, unit: String): Double {
        if (raw is Number) return number(component, prop, raw)
        val input = string(component, prop, raw)
        val match = DIMENSION.matchEntire(input.trim()) ?: invalid(component, prop, "Expected a number or a dimension with units")
        if (match.groupValues[2] != unit) invalid(component, prop, "Expected $unit units")
        return match.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: invalid(component, prop, "Dimension must be finite")
    }

    private fun string(component: String, prop: String, raw: Any?): String {
        val value = unwrap(raw)
        if (value !is CharSequence) invalid(component, prop, "Expected a string")
        if (value.length > ComposeUiLimits.MAX_STRING_CHARS) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, component, prop, "String exceeds contract limit")
        return value.toString()
    }

    private fun boolean(component: String, prop: String, raw: Any?): Boolean = unwrap(raw) as? Boolean
        ?: invalid(component, prop, "Expected a boolean")

    private fun number(component: String, prop: String, raw: Any?): Double = (unwrap(raw) as? Number)?.toDouble()?.takeIf { it.isFinite() }
        ?: invalid(component, prop, "Expected a finite number")

    fun integer(component: String, prop: String, raw: Any?, minimum: Int = 0, maximum: Int = Int.MAX_VALUE): Int {
        val value = number(component, prop, raw)
        if (value % 1.0 != 0.0 || value < minimum || value > maximum) invalid(component, prop, "Expected an integer within the allowed range")
        return value.toInt()
    }

    fun arrayValues(raw: Any?, component: String, prop: String, maximum: Int = ComposeUiLimits.MAX_VALUE_ITEMS): List<Any?> = when (val value = unwrap(raw)) {
        is NativeArray -> {
            if (value.length > maximum) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, component, prop, "Array exceeds contract limit")
            List(value.length.toInt()) { index -> value.get(index, value) }
        }
        is List<*> -> value.also { if (it.size > maximum) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, component, prop, "Array exceeds contract limit") }
        is Array<*> -> {
            if (value.size > maximum) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, component, prop, "Array exceeds contract limit")
            value.toList()
        }
        else -> invalid(component, prop, "Expected an array")
    }

    private fun rejectUnknown(fields: Map<String, Any?>, accepted: Set<String>, component: String, parent: String = "") {
        fields.keys.firstOrNull { it !in accepted }?.let {
            fail(ComposeUiErrorCodes.UNKNOWN_PROP, component, if (parent.isEmpty()) it else "$parent.$it", "Unknown property")
        }
    }

    fun toRaw(value: UiValue, scope: Scriptable): Any? = toRaw(value, RhinoUtils.getDeclarationScope(scope))

    fun toRaw(value: UiValue, scope: VarScope): Any? = when (value) {
        is UiValue.Str -> value.value
        is UiValue.Num -> value.value
        is UiValue.Bool -> value.value
        is UiValue.Color -> value.argb
        is UiValue.Dp -> value.value
        is UiValue.Sp -> value.value
        is UiValue.Enum -> value.value
        is UiValue.IconName -> value.value
        is UiValue.BitmapRef -> Context.javaToJS(value.get(), scope)
        is UiValue.ListOf -> RhinoUtils.newNativeArray(scope, value.values.map { toRaw(it, scope) }.toTypedArray())
        is UiValue.Shape -> rawObject(scope, linkedMapOf("shape" to value.shape.name.lowercase(), "radius" to value.radius))
        is UiValue.TextStyle -> rawObject(scope, linkedMapOf(
            "fontFamily" to value.fontFamily, "fontSize" to value.fontSize, "fontWeight" to value.fontWeight,
            "italic" to value.italic, "lineHeight" to value.lineHeight, "letterSpacing" to value.letterSpacing,
        ).filterValues { it != null })
        UiValue.Null -> null
    }

    private fun rawObject(scope: VarScope, fields: Map<String, Any?>): Scriptable =
        RhinoUtils.newNativeObject(scope).apply { fields.forEach { (name, value) -> put(name, this, value) } }

    internal fun <T> contextual(component: String, prop: String, action: () -> T): T = try {
        action()
    } catch (error: ComposeUiContractException) {
        if (error.message?.startsWith("$component.") == true) throw error
        throw ComposeUiContractException(error.code, "$component.$prop: ${error.message}", error.nodeId, error.prop ?: prop, error)
    } catch (error: Exception) {
        throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "$component.$prop: Invalid value", prop = prop, cause = error)
    }

    companion object {
        private val DIMENSION = Regex("([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)(dp|sp)")
        fun unwrap(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value
        fun isNullish(value: Any?): Boolean = value == null || Undefined.isUndefined(value) || value === Scriptable.NOT_FOUND
        fun isArray(value: Any?): Boolean = when (unwrap(value)) { is NativeArray, is List<*>, is Array<*> -> true; else -> false }
        fun callbackProperty(event: String): String = "on" + event.replaceFirstChar { it.uppercaseChar() }
        internal fun invalid(component: String, prop: String, reason: String): Nothing = fail(ComposeUiErrorCodes.INVALID_ARGUMENT, component, prop, reason)
        internal fun fail(code: String, component: String, prop: String, reason: String): Nothing =
            throw ComposeUiContractException(code, "$component.$prop: $reason", prop = prop)
    }
}
