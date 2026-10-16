package org.autojs.autojs.core.ui.inflater

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.appcompat.content.res.AppCompatResources
import org.autojs.autojs.core.ui.inflater.util.Res
import org.autojs.autojs.util.ColorUtils
import java.io.File
import java.util.LinkedHashMap

/**
 * Resolves source Android resources belonging to a Rhino project and falls back to resources
 * compiled into the host application or the Android framework.
 */
class AndroidResourceResolver(
    val repository: ScriptResourceRepository = ScriptResourceRepository(),
) {

    private val drawableInflater = AndroidDrawableInflater(this)
    private val colorStateListInflater = AndroidColorStateListInflater(this)

    fun configure(sourceFile: File? = null, workingDirectory: File? = null) {
        repository.configure(sourceFile, workingDirectory)
    }

    fun resolveAttributes(context: Context, source: Map<String, String>): HashMap<String, String> {
        val attributes = LinkedHashMap<String, String>()
        val style = source["style"]
        val projectStyle = style?.let(repository::resolveStyleDetails)
        val compiledStyle = projectStyle?.compiledParent ?: style?.takeIf { projectStyle == null }
        context.findScriptThemeAttributes()
            .filterKeys { it !in SCRIPT_THEME_CONTROL_ATTRIBUTES && !it.startsWith("xmlns") }
            .filterKeys { !compiledStyleDefinesAttribute(context, compiledStyle, it) }
            .let(attributes::putAll)
        if (projectStyle != null) {
            attributes.putAll(projectStyle.attributes)
        }
        attributes.putAll(source)
        if (projectStyle != null) {
            projectStyle.compiledParent
                ?.let { attributes["style"] = it }
                ?: attributes.remove("style")
        }
        attributes.replaceAll { name, value ->
            when {
                name == "style" || name.startsWith("xmlns") -> value
                else -> resolveScalar(context, value)
            }
        }
        // A LinkedHashMap keeps the document order the inflater applies the attributes in.
        return attributes
    }

    /**
     * Script theme values are applied later through setters because they are not compiled into an
     * Android Theme. Do not let that emulation overwrite attributes inherited from an explicit
     * compiled style, whose precedence is higher than the enclosing theme.
     */
    private fun compiledStyleDefinesAttribute(
        context: Context,
        styleReference: String?,
        attributeName: String,
    ): Boolean {
        val styleId = styleReference
            ?.let { Res.parseStyle(context, it) }
            ?.takeIf { it != 0 }
            ?: return false
        val (packageName, name) = when {
            attributeName.startsWith("android:") ->
                "android" to attributeName.removePrefix("android:")
            attributeName.startsWith("app:") ->
                context.packageName to attributeName.removePrefix("app:")
            else -> context.packageName to attributeName
        }
        val attributeId = context.resources.getIdentifier(name, "attr", packageName)
        if (attributeId == 0) {
            return false
        }
        val styleOnlyTheme = context.resources.newTheme().apply {
            applyStyle(styleId, true)
        }
        return styleOnlyTheme.obtainStyledAttributes(intArrayOf(attributeId)).let { typedArray ->
            try {
                typedArray.hasValue(0)
            } finally {
                typedArray.recycle()
            }
        }
    }

    fun resolveScalar(context: Context, source: String): String =
        resolveScalar(context, source, 0)

    private fun resolveScalar(context: Context, source: String, depth: Int): String {
        check(depth < MAX_RESOURCE_DEPTH) {
            "Android resource reference depth exceeds $MAX_RESOURCE_DEPTH while resolving $source"
        }
        val projectValue = repository.resolveValue(source)
        if (projectValue != source) {
            return resolveScalar(context, projectValue, depth + 1)
        }
        resolveScriptThemeAttribute(context, source)?.let {
            return if (it == source) it else resolveScalar(context, it, depth + 1)
        }
        if (parseResourceReference(source)?.type == "color") {
            return source
        }
        resolveCompiledResource(context, source)?.let { return it }
        resolveThemeAttribute(context, source)?.let {
            return if (it == source) it else resolveScalar(context, it, depth + 1)
        }
        parseResourceReference(source)
            ?.takeIf { it.type in SCALAR_TYPES }
            ?.let { throw Resources.NotFoundException("Android resource not found: $source") }
        return source
    }

    fun readLayout(reference: String): String? = repository.readLayout(reference)

    fun canResolveDrawable(value: String): Boolean {
        val resolved = repository.resolveValue(value)
        val reference = parseResourceReference(value)
        return value == "@null" ||
            resolved != value ||
            reference?.type in DRAWABLE_TYPES ||
            reference?.type == "color" ||
            value.startsWith("?")
    }

    fun resolveDrawable(context: Context, source: String): Drawable? {
        if (source == "@null") {
            return null
        }
        val projectValue = repository.resolveValue(source)
        if (projectValue != source) {
            return resolveDrawable(context, projectValue)
        }
        repository.findDrawable(source)?.let { file ->
            return drawableInflater.inflate(context, file)
        }
        if (source.startsWith("#")) {
            return ColorDrawable(parseColor(context, source))
        }
        if (source.startsWith("?")) {
            val themedValue = resolveScalar(context, source)
            if (themedValue != source) {
                return resolveDrawable(context, themedValue)
            }
        }
        if (source.startsWith("?")) {
            return resolveThemeDrawable(context, source)
        }
        parseResourceReference(source)?.let { reference ->
            if (reference.type == "color") {
                return ColorDrawable(parseColor(context, source))
            }
            if (reference.type in DRAWABLE_TYPES) {
                val id = findCompiledResourceId(context, reference)
                if (id != 0) {
                    return AppCompatResources.getDrawable(context, id)
                }
                throw Resources.NotFoundException("Android drawable resource not found: $source")
            }
        }
        return null
    }

    fun parseColor(context: Context, source: String): Int {
        repository.findColor(source)?.let { file ->
            return colorStateListInflater.inflate(context, file).defaultColor
        }
        val projectValue = repository.resolveValue(source)
        if (projectValue != source) {
            return parseColor(context, projectValue)
        }
        if (source.startsWith("?")) {
            val themedValue = resolveScalar(context, source)
            if (themedValue != source) {
                return parseColor(context, themedValue)
            }
        }
        resolveThemeColorStateList(context, source)?.let { return it.defaultColor }
        parseResourceReference(source)?.let { reference ->
            if (reference.type == "color") {
                val id = findCompiledResourceId(context, reference)
                if (id != 0) {
                    return context.resources.getColor(id, context.theme)
                }
                throw Resources.NotFoundException("Android color resource not found: $source")
            }
        }
        val resolved = resolveScalar(context, source)
        if (resolved != source) {
            return parseColor(context, resolved)
        }
        return ColorUtils.parse(context, source)
    }

    fun parseColorStateList(context: Context, source: String): ColorStateList {
        val projectValue = repository.resolveValue(source)
        if (projectValue != source) {
            return parseColorStateList(context, projectValue)
        }
        repository.findColor(source)?.let { file ->
            return colorStateListInflater.inflate(context, file)
        }
        if (source.startsWith("?")) {
            val themedValue = resolveScalar(context, source)
            if (themedValue != source) {
                return parseColorStateList(context, themedValue)
            }
        }
        parseResourceReference(source)?.let { reference ->
            if (reference.type == "color") {
                val id = findCompiledResourceId(context, reference)
                if (id != 0) {
                    return context.resources.getColorStateList(id, context.theme)
                }
            }
        }
        resolveThemeColorStateList(context, source)?.let { return it }
        return ColorStateList.valueOf(parseColor(context, source))
    }

    internal fun parseDimension(context: Context, source: String): Float {
        return org.autojs.autojs.core.ui.inflater.util.Dimensions.parseToPixel(
            resolveScalar(context, source),
            context,
        )
    }

    private fun resolveCompiledResource(context: Context, source: String): String? {
        val reference = parseResourceReference(source) ?: return null
        if (reference.type !in SCALAR_TYPES) {
            return null
        }
        val id = findCompiledResourceId(context, reference)
        if (id == 0) {
            return null
        }
        val resources = context.resources
        return when (reference.type) {
            "string" -> resources.getString(id)
            "color" -> ColorUtils.toString(resources.getColor(id, context.theme))
            "dimen" -> "${resources.getDimension(id)}px"
            "integer" -> resources.getInteger(id).toString()
            "bool" -> resources.getBoolean(id).toString()
            "fraction" -> resources.getFraction(id, 1, 1).toString()
            "array" -> resolveCompiledArray(context, id)
            else -> null
        }
    }

    private fun resolveCompiledArray(context: Context, id: Int): String {
        val resources = context.resources
        val array = resources.obtainTypedArray(id)
        return try {
            (0 until array.length()).joinToString("|") { index ->
                val value = TypedValue()
                if (!array.getValue(index, value)) {
                    return@joinToString ""
                }
                when (value.type) {
                    TypedValue.TYPE_NULL -> ""
                    TypedValue.TYPE_STRING -> array.getString(index).orEmpty()
                    TypedValue.TYPE_DIMENSION ->
                        "${value.getDimension(resources.displayMetrics)}px"
                    TypedValue.TYPE_FLOAT -> value.float.toString()
                    TypedValue.TYPE_INT_BOOLEAN -> (value.data != 0).toString()
                    in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT ->
                        ColorUtils.toString(value.data)
                    in TypedValue.TYPE_FIRST_INT..TypedValue.TYPE_LAST_INT ->
                        value.data.toString()
                    TypedValue.TYPE_REFERENCE -> value.resourceId
                        .takeIf { it != 0 }
                        ?.let { resourceReference(resources, it) }
                        .orEmpty()
                    else -> array.getString(index).orEmpty()
                }
            }
        } finally {
            array.recycle()
        }
    }

    private fun resolveThemeAttribute(context: Context, source: String): String? {
        val attrId = findThemeAttributeId(context, source)
        if (attrId == 0) {
            return null
        }
        val typedValue = TypedValue()
        if (!context.theme.resolveAttribute(attrId, typedValue, true)) {
            return null
        }
        typedValue.resourceId
            .takeIf { it != 0 }
            ?.let { resourceReference(context.resources, it) }
            ?.let { return it }
        return when (typedValue.type) {
            in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT ->
                ColorUtils.toString(typedValue.data)
            TypedValue.TYPE_DIMENSION ->
                "${typedValue.getDimension(context.resources.displayMetrics)}px"
            TypedValue.TYPE_FLOAT -> typedValue.float.toString()
            TypedValue.TYPE_FRACTION -> typedValue.getFraction(1f, 1f).toString()
            TypedValue.TYPE_STRING -> typedValue.string?.toString()
            TypedValue.TYPE_INT_BOOLEAN -> (typedValue.data != 0).toString()
            in TypedValue.TYPE_FIRST_INT..TypedValue.TYPE_LAST_INT -> typedValue.data.toString()
            TypedValue.TYPE_REFERENCE -> typedValue.resourceId
                .takeIf { it != 0 }
                ?.let { resourceReference(context.resources, it) }
            else -> null
        }
    }

    private fun resolveThemeDrawable(context: Context, source: String): Drawable? {
        val attrId = findThemeAttributeId(context, source)
        if (attrId == 0) {
            return null
        }
        val attrs = intArrayOf(attrId)
        return context.obtainStyledAttributes(attrs).let { typedArray ->
            try {
                typedArray.getDrawable(0)
            } finally {
                typedArray.recycle()
            }
        }
    }

    private fun resolveThemeColorStateList(context: Context, source: String): ColorStateList? {
        val attrId = findThemeAttributeId(context, source)
        if (attrId == 0) {
            return null
        }
        return context.obtainStyledAttributes(intArrayOf(attrId)).let { typedArray ->
            try {
                typedArray.getColorStateList(0)
            } finally {
                typedArray.recycle()
            }
        }
    }

    private fun findThemeAttributeId(context: Context, source: String): Int {
        val match = THEME_ATTRIBUTE.matchEntire(source.trim()) ?: return 0
        val packageName = if (match.groupValues[1] == "android") "android" else context.packageName
        return context.resources.getIdentifier(match.groupValues[2], "attr", packageName)
    }

    private fun resolveScriptThemeAttribute(context: Context, source: String): String? {
        val match = THEME_ATTRIBUTE.matchEntire(source.trim()) ?: return null
        val attributes = context.findScriptThemeAttributes()
        val name = match.groupValues[2]
        val candidates = if (match.groupValues[1] == "android") {
            listOf("android:$name", name)
        } else {
            listOf(name, "app:$name")
        }
        return candidates.firstNotNullOfOrNull(attributes::get)
    }

    private fun findCompiledResourceId(context: Context, reference: ResourceReference): Int {
        val packageName = reference.packageName ?: context.packageName
        return context.resources.getIdentifier(reference.name, reference.type, packageName)
    }

    private fun resourceReference(resources: Resources, id: Int): String? = runCatching {
        val packageName = resources.getResourcePackageName(id)
        val type = resources.getResourceTypeName(id)
        val name = resources.getResourceEntryName(id)
        "@${if (packageName == "android") "android:" else ""}$type/$name"
    }.getOrNull()

    private fun parseResourceReference(source: String): ResourceReference? {
        val match = RESOURCE_REFERENCE.matchEntire(source.trim()) ?: return null
        return ResourceReference(
            packageName = match.groupValues[1].ifBlank { null },
            type = match.groupValues[2],
            name = match.groupValues[3],
        )
    }

    private data class ResourceReference(
        val packageName: String?,
        val type: String,
        val name: String,
    )

    companion object {

        private val RESOURCE_REFERENCE =
            Regex("@(?:\\+)?(?:([A-Za-z_][A-Za-z0-9_.]*):)?([\\w-]+)/([A-Za-z_][A-Za-z0-9_]*)")
        private val THEME_ATTRIBUTE =
            Regex("\\?(?:(android):)?(?:attr/)?([A-Za-z_][A-Za-z0-9_]*)")
        private val SCALAR_TYPES = setOf(
            "array",
            "bool",
            "color",
            "dimen",
            "fraction",
            "integer",
            "string",
        )
        private val DRAWABLE_TYPES = setOf("drawable", "mipmap")
        private val SCRIPT_THEME_CONTROL_ATTRIBUTES = setOf(
            "android:theme",
            "style",
            "theme",
        )
        private const val MAX_RESOURCE_DEPTH = 32
    }
}
