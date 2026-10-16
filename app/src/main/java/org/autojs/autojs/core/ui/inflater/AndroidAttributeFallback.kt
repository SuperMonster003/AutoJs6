package org.autojs.autojs.core.ui.inflater

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.InflateException
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.AndroidAttributeTable.Format
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.core.ui.inflater.util.Ids
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The attribute fallback of the dynamic layout inflater (host roadmap `docs/dev/android-xml-attributes-roadmap.md`
 * A2): an attribute that no handler of the inflater takes is looked up in [AndroidAttributeTable] for the view's
 * class; when the platform or a bundled library defines it there (on this API level), a public one-argument method
 * named by [AndroidAttributeSetters] sets it, with the value converted for that method's parameter type. View
 * instances are always created without an `AttributeSet`, so this is how an Android XML attribute reaches a view the
 * inflater has no handler for. Candidate methods are found once per class and attribute; a value no candidate can
 * take is an inflate error, like a bad value for a handler.
 *
 * zh-CN: 动态布局器的属性兜底 (宿主路线图 A2): 没有处理器接受的属性在属性表中按 View 类查找, 平台或自带库定义了它 (且本 API 可用) 时, 以
 * [AndroidAttributeSetters] 给出的公开单参方法设置, 取值按参数类型转换. 候选方法按类与属性缓存; 任何候选都无法接受的取值按布局错误处理.
 */
class AndroidAttributeFallback(private val table: AndroidAttributeTable, private val resourceParser: ResourceParser) {

    private val methods = ConcurrentHashMap<String, List<Method>>()
    private val layoutMembers = ConcurrentHashMap<String, List<Member>>()

    /**
     * Sets [name] on [view] if the metadata knows it for the view's class, or (A3) a `layout_*` attribute on the view's
     * layout params if it knows it for the [parent]'s class; see [AttributeOutcome].
     */
    fun apply(view: View, namespace: String?, name: String, value: String, parent: ViewGroup?): AttributeOutcome {
        val member = table.find(view.javaClass, namespace, name)
            ?: return if (name.startsWith("layout_") && parent != null) applyLayout(view, parent, namespace, name, value) else AttributeOutcome.UNKNOWN
        if (Build.VERSION.SDK_INT < member.sinceApi) return AttributeOutcome.UNSUPPORTED
        val values = Values(view, resourceParser, member.attr)
        AndroidAttributeSetters.SPECIAL[member.attr.name]?.let { special ->
            if (special(values, view, value)) return AttributeOutcome.APPLIED
        }
        val candidates = candidatesOf(view.javaClass, member.attr)
        if (candidates.isEmpty()) return AttributeOutcome.UNSUPPORTED
        var reason: String? = null
        for (method in candidates) {
            val argument = try {
                values.convert(method.parameterTypes[0], value)
            } catch (e: IllegalArgumentException) {
                reason = e.message
                continue
            }
            try {
                method.invoke(view, argument)
            } catch (e: InvocationTargetException) {
                val cause = e.targetException
                throw InflateException("${member.attr.qualifiedName}=\"$value\" failed in ${method.name}: ${cause.message}", cause)
            }
            return AttributeOutcome.APPLIED
        }
        throw InflateException("${member.attr.qualifiedName}=\"$value\" is not a valid value" + (reason?.let { ": $it" } ?: ""))
    }

    /**
     * A `layout_*` attribute of the parent's layout params (host roadmap A3): a special rule, else a public setter
     * or a public field of the params named by [AndroidLayoutParamRules.property]; the params are validated (when they
     * can be, as ConstraintLayout's) and assigned back so the parent lays the view out again.
     */
    private fun applyLayout(view: View, parent: ViewGroup, namespace: String?, name: String, value: String): AttributeOutcome {
        if (AndroidLayoutParamRules.isDesignTime(name)) return AttributeOutcome.IGNORED_NAMESPACE
        val member = table.findLayout(parent.javaClass, namespace, name) ?: return AttributeOutcome.UNKNOWN
        if (Build.VERSION.SDK_INT < member.sinceApi) return AttributeOutcome.UNSUPPORTED
        val params = view.layoutParams ?: return AttributeOutcome.UNSUPPORTED
        val values = Values(view, resourceParser, member.attr)
        val applied = try {
            AndroidLayoutParamRules.SPECIAL[name]?.invoke(values, view, params, value) == true || setLayoutMember(params, member.attr, values, value)
        } catch (e: IllegalArgumentException) {
            throw InflateException("${member.attr.qualifiedName}=\"$value\" is not a valid value: ${e.message}", e)
        } catch (e: InvocationTargetException) {
            throw InflateException("${member.attr.qualifiedName}=\"$value\" failed: ${e.targetException.message}", e.targetException)
        }
        if (!applied) return AttributeOutcome.UNSUPPORTED
        params.javaClass.methods.firstOrNull { it.name == "validate" && it.parameterTypes.isEmpty() }?.invoke(params)
        view.layoutParams = params
        return AttributeOutcome.APPLIED
    }

    /** Sets the first setter or field of [params] that takes the value; false when the params have none for [attr]. */
    private fun setLayoutMember(params: ViewGroup.LayoutParams, attr: AndroidAttributeTable.Attr, values: Values, value: String): Boolean {
        val members = layoutMembersOf(params.javaClass, attr)
        if (members.isEmpty()) return false
        var reason: String? = null
        for (member in members) {
            val type = if (member is Method) member.parameterTypes[0] else (member as Field).type
            val argument = try {
                values.convert(type, value)
            } catch (e: IllegalArgumentException) {
                reason = e.message
                continue
            }
            if (member is Method) member.invoke(params, argument) else (member as Field).set(params, argument)
            return true
        }
        throw IllegalArgumentException(reason ?: "no member of ${params.javaClass.simpleName} takes it")
    }

    /** The public setters and fields of [paramsClass] that may take the layout attribute [attr], in the order they are tried. */
    internal fun layoutMembersOf(paramsClass: Class<*>, attr: AndroidAttributeTable.Attr): List<Member> =
        layoutMembers.getOrPut(paramsClass.name + "|" + attr.qualifiedName) {
            val property = AndroidLayoutParamRules.property(attr.name)
            val kinds = Kind.preferred(attr.formats)
            fun rank(type: Class<*>) = kinds.indexOfFirst { it.accepts(type) }
            val names = AndroidAttributeSetters.candidates(property) + ("set" + property.replaceFirstChar { it.uppercaseChar() } + "Id")
            val setters = names.flatMap { candidate ->
                paramsClass.methods
                    .filter { !Modifier.isStatic(it.modifiers) && it.parameterTypes.size == 1 && it.name.equals(candidate, ignoreCase = true) }
                    .filter { rank(it.parameterTypes[0]) >= 0 }
                    .sortedBy { rank(it.parameterTypes[0]) }
            }
            val fields = paramsClass.fields.filter {
                !Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers) && it.name.equals(property, ignoreCase = true) && rank(it.type) >= 0
            }
            (setters + fields).distinct()
        }

    /** The public one-argument methods that may set [attr] on [viewClass], in the order they are tried. */
    internal fun candidatesOf(viewClass: Class<*>, attr: AndroidAttributeTable.Attr): List<Method> =
        methods.getOrPut(viewClass.name + "|" + attr.qualifiedName) {
            val kinds = Kind.preferred(attr.formats)
            val oneArgument = viewClass.methods.filter { !Modifier.isStatic(it.modifiers) && it.parameterTypes.size == 1 }
            AndroidAttributeSetters.candidates(attr.name).flatMap { name ->
                oneArgument
                    .filter { it.name.equals(name, ignoreCase = true) }
                    .mapNotNull { method -> kinds.indexOfFirst { it.accepts(method.parameterTypes[0]) }.takeIf { it >= 0 }?.let { method to it } }
                    .sortedBy { it.second }
                    .map { it.first }
            }.distinct()
        }

    /** What a setter parameter can be filled from, in [preferred] order for an attribute's formats. */
    internal enum class Kind {
        JAVA_ENUM, ENUM_INT, COLOR_STATE_LIST, COLOR_INT, DRAWABLE, DIMENSION_FLOAT, DIMENSION_INT, FLOAT, INT, LONG, BOOLEAN, TEXT, ID;

        fun accepts(type: Class<*>): Boolean = when (this) {
            JAVA_ENUM -> type.isEnum
            ENUM_INT, COLOR_INT, DIMENSION_INT, INT, ID -> type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType
            COLOR_STATE_LIST -> type == ColorStateList::class.java
            DRAWABLE -> type == Drawable::class.java
            DIMENSION_FLOAT, FLOAT -> type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType
            LONG -> type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType
            BOOLEAN -> type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType
            TEXT -> type == CharSequence::class.java || type == String::class.java
        }

        companion object {
            fun preferred(formats: Set<Format>): List<Kind> = LinkedHashSet<Kind>().apply {
                if (Format.ENUM in formats || Format.FLAG in formats) addAll(listOf(JAVA_ENUM, ENUM_INT))
                if (Format.COLOR in formats) addAll(listOf(COLOR_STATE_LIST, COLOR_INT, DRAWABLE))
                if (Format.REFERENCE in formats) addAll(listOf(DRAWABLE, COLOR_STATE_LIST, TEXT, ID))
                if (Format.DIMENSION in formats) addAll(listOf(DIMENSION_FLOAT, DIMENSION_INT))
                if (Format.FLOAT in formats || Format.FRACTION in formats) add(FLOAT)
                if (Format.INTEGER in formats) addAll(listOf(INT, LONG, FLOAT))
                if (Format.BOOLEAN in formats) add(BOOLEAN)
                if (Format.STRING in formats) add(TEXT)
            }.toList()
        }
    }

    /**
     * Converts attribute values for [view] the way the inflater's handlers read them: dimensions through
     * [Dimensions], colors, color state lists and drawables through the resource resolver (project and compiled
     * resources), ids through [Ids], enum and flag names through the attribute's values. Throws
     * [IllegalArgumentException] for a value the requested type cannot hold.
     */
    class Values(val view: View, private val resourceParser: ResourceParser, private val attr: AndroidAttributeTable.Attr?) {

        fun convert(type: Class<*>, value: String): Any = when {
            type.isEnum -> javaEnum(type, value)
            Kind.BOOLEAN.accepts(type) -> boolean(value)
            Kind.INT.accepts(type) -> int(value)
            Kind.LONG.accepts(type) -> int(value).toLong()
            Kind.FLOAT.accepts(type) -> floatOrDimension(value)
            Kind.TEXT.accepts(type) -> value
            Kind.DRAWABLE.accepts(type) -> drawable(value)
            Kind.COLOR_STATE_LIST.accepts(type) -> colorStateList(value)
            else -> throw IllegalArgumentException("${type.simpleName} parameters are not supported")
        }

        /** A string, array (joined with `|`) or other scalar resource resolved; any other value as written. */
        fun scalar(value: String): String = resourceParser.androidResources.resolveScalar(view.context, value.trim())

        fun boolean(value: String): Boolean = when (value.trim().lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("\"$value\" is not a boolean")
        }

        /** An int from enum / flag names, an id reference, a color, a dimension or a number, as the formats allow. */
        fun int(value: String): Int {
            val text = value.trim()
            val formats = attr?.formats.orEmpty()
            attr?.enums?.get(text)?.let { return it }
            if (Format.FLAG in formats && attr != null) {
                val parts = text.split('|').map { it.trim() }
                if (parts.all { it in attr.flags }) return parts.fold(0) { acc, part -> acc or attr.flags.getValue(part) }
            }
            if (ID_REFERENCE.matches(text)) return Ids.parse(text)
            if (Format.COLOR in formats && (text.startsWith("#") || text.startsWith("@") || text.startsWith("?"))) return color(text)
            if (Format.DIMENSION in formats && DIMENSION.matches(text)) return Dimensions.parseToIntPixel(text, view)
            return text.toIntOrNull() ?: runCatching { java.lang.Long.decode(text).toInt() }.getOrNull()
                ?: throw IllegalArgumentException("\"$value\" is not an integer")
        }

        fun float(value: String): Float {
            val text = value.trim()
            FRACTION.matchEntire(text)?.let { return it.groupValues[1].toFloat() / 100f }
            return text.toFloatOrNull() ?: throw IllegalArgumentException("\"$value\" is not a number")
        }

        /** A dimension in pixels (a bare number is dp, the inflater's convention). */
        fun dimension(value: String): Float {
            if (!DIMENSION.matches(value.trim())) throw IllegalArgumentException("\"$value\" is not a dimension")
            return Dimensions.parseToPixel(value.trim(), view)
        }

        private fun floatOrDimension(value: String): Float {
            val text = value.trim()
            val formats = attr?.formats.orEmpty()
            return when {
                Format.DIMENSION in formats && (DIMENSION_WITH_UNIT.matches(text) || Format.FLOAT !in formats && DIMENSION.matches(text)) -> dimension(text)
                else -> float(text)
            }
        }

        fun color(value: String): Int = resourceParser.androidResources.parseColor(view.context, value.trim())

        fun colorStateList(value: String): ColorStateList = resourceParser.androidResources.parseColorStateList(view.context, value.trim())

        fun drawable(value: String): Drawable = resourceParser.drawables.parse(view, value.trim())
            ?: throw IllegalArgumentException("\"$value\" is not a drawable")

        /** The flag names of a `|`-separated value. */
        fun flagNames(value: String): Set<String> = value.split('|').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        /** An enum constant by its XML name (`centerCrop` -> `CENTER_CROP`, `src_atop` -> `SRC_ATOP`) or its own name. */
        private fun javaEnum(type: Class<*>, value: String): Any {
            val constants = type.enumConstants ?: throw IllegalArgumentException("${type.simpleName} has no constants")
            val wanted = enumConstantName(value)
            return constants.firstOrNull { (it as Enum<*>).name == wanted || it.name == value.trim() }
                ?: throw IllegalArgumentException("\"$value\" is not one of ${constants.joinToString { (it as Enum<*>).name }}")
        }

        companion object {
            private val ID_REFERENCE = Regex("""@(?:\+|android:)?id/[A-Za-z0-9_.]+""")
            private val DIMENSION = Regex("""-?(?:\d+\.?\d*|\.\d+)(?:px|dp|dip|sp|pt|in|mm)?""")
            private val DIMENSION_WITH_UNIT = Regex("""-?(?:\d+\.?\d*|\.\d+)(?:px|dp|dip|sp|pt|in|mm)""")
            private val FRACTION = Regex("""(-?(?:\d+\.?\d*|\.\d+))%p?""")

            /** `centerCrop` -> `CENTER_CROP`, `fitXY` -> `FIT_XY`, `src_atop` -> `SRC_ATOP`. */
            fun enumConstantName(value: String): String =
                value.trim().replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase(Locale.ROOT)
        }
    }
}
