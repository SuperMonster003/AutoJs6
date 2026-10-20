package org.autojs.autojs.ksp.modules

/*
 * The processor's view of one @ScriptModule class, built by ScriptModuleAnalyzer from the KSP symbols and consumed by
 * BindingGenerator (Kotlin source) and MetadataWriter (script-modules.json).
 *
 * zh-CN: 处理器对一个 @ScriptModule 类的理解, 由 ScriptModuleAnalyzer 从 KSP 符号构建, 供 BindingGenerator (Kotlin 源码) 与
 * MetadataWriter (script-modules.json) 使用.
 */

internal enum class ModuleInstance { OBJECT, NO_ARG, RUNTIME }

internal data class ModuleModel(
    val qualifiedName: String,
    val simpleName: String,
    val name: String,
    val aliases: List<String>,
    val dollarPrefix: Boolean,
    val eager: Boolean,
    val parent: String?,
    val attributes: Int,
    val emitter: Boolean,
    val instance: ModuleInstance,
    val functions: List<FunctionGroup>,
    val invoke: Overload?,
    val construct: Overload?,
    val accessors: List<Accessor>,
    val javaClasses: List<JavaClassBinding>,
    val prototype: Member?,
    val source: String,
) {
    val bindingName: String get() = "${simpleName}Binding"

    /** Names the module object is bound to, in declaration order. */
    val names: List<String>
        get() = (listOf(name) + aliases).flatMap { if (dollarPrefix) listOf(it, "\$$it") else listOf(it) }
}

/** Where a member is bound: on the module object, on the global object, or both. */
internal data class Placement(val onModule: Boolean, val onGlobal: Boolean) {
    val label: String
        get() = when {
            onModule && onGlobal -> "ALSO"
            onGlobal -> "ONLY"
            else -> "NONE"
        }
}

internal data class FunctionGroup(
    val id: Int,
    val name: String,
    val aliases: List<String>,
    val placement: Placement,
    val attributes: Int,
    /** `NONE`, `LITERAL` or `FUNCTIONAL` (the annotation's `ScriptToString` entry). */
    val toStringRole: String,
    val overloads: List<Overload>,
) {
    /** The JS `length` of the function object: the parameters before the first optional one, over all overloads. */
    val length: Int get() = overloads.minOf { it.required }
}

internal data class Overload(
    val kotlinName: String,
    val params: List<Param>,
    val returnType: String,
    val returnsUnit: Boolean,
    val source: String,
) {
    val scriptParams: List<Param> get() = params.filter { it.kind.injected == null }
    val required: Int get() = scriptParams.count { !it.optional && !it.vararg }
    val vararg: Boolean get() = scriptParams.any { it.vararg }

    /** Largest argument count taken positionally, or null when a vararg takes the rest. */
    val max: Int? get() = if (vararg) null else scriptParams.size

    val signature: String get() = signature(kotlinName)

    fun signature(name: String): String =
        scriptParams.joinToString(", ", "$name(", ")") { p ->
            buildString {
                if (p.vararg) append("...")
                append(p.name).append(": ").append(p.type)
                if (p.optional) append(" = default")
            }
        }
}

internal data class Param(
    val name: String,
    /** The Kotlin type as source text (fully qualified, with nullability), the element type for a vararg. */
    val type: String,
    /** For [ParamKind.TYPED]: the class literal source without type arguments. */
    val rawClass: String,
    val kind: ParamKind,
    val nullable: Boolean,
    val optional: Boolean,
    val vararg: Boolean,
)

/**
 * How a script value becomes a Kotlin argument (roadmap T4). [converter] names the `ScriptArgs` function for a
 * non-null parameter, `<converter>OrNull` is used for a nullable one; [guard] names the `ScriptArgs` test used to pick
 * between overloads with the same argument count. [injected] marks parameters filled by the generated code.
 */
internal enum class ParamKind(val converter: String?, val guard: String?, val varargConverter: String? = null, val injected: String? = null) {
    RAW("nonNullish", null, "rest"),
    STRING("string", "isString", "strings"),
    BOOLEAN("boolean", "isBoolean"),
    DOUBLE("double", "isNumber", "doubles"),
    FLOAT("float", "isNumber"),
    INT("int", "isNumber", "ints"),
    LONG("long", "isNumber"),
    BYTES("bytes", "isBytes"),
    LIST("list", "isList"),
    MAP("map", "isMap"),
    TYPED("typed", "isInstance"),
    CONTEXT(null, null, injected = "cx"),
    SCOPE(null, null, injected = "scope"),
}

internal data class Accessor(
    val id: Int,
    val name: String,
    val kotlinName: String,
    val isFunction: Boolean,
    val isGetter: Boolean,
    val mutable: Boolean,
    val type: String,
    val setterParam: Param?,
    val placement: Placement,
    val attributes: Int,
    val source: String,
)

internal data class JavaClassBinding(
    val name: String,
    val type: String,
    val placement: Placement,
    val attributes: Int,
)

internal data class Member(val kotlinName: String, val isFunction: Boolean)
