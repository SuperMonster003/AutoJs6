package org.autojs.autojs.runtime.api.modules

import kotlin.reflect.KClass

/*
 * Annotations of the script module layer (Augment refactor, docs/augment/ROADMAP.md D5 / T2). A Kotlin class annotated
 * with @ScriptModule declares one script-visible module; the KSP processor `ksp-script-modules-processor` reads the
 * annotations below and generates, per module, a `<Class>Binding` in `org.autojs.autojs.runtime.api.modules.generated`
 * (mount table, dispatch by function id, argument conversion from the real Kotlin signatures) plus the
 * `autojs6/script-modules.json` metadata. Nothing reads these annotations at run time (BINARY retention), so the
 * host only needs them at compile time.
 *
 * zh-CN: 脚本模块层的注解 (Augment 重构, ROADMAP D5 / T2). 带 @ScriptModule 的 Kotlin 类声明一个脚本可见的模块; KSP 处理器读取下列注解,
 * 为每个模块生成 `<类名>Binding` (挂载表, 按函数编号分发, 基于真实 Kotlin 签名的参数转换) 与 `autojs6/script-modules.json` 元数据.
 * 运行时不读取这些注解 (BINARY 保留), 宿主只在编译期需要它们.
 */

/**
 * Declares a script module.
 *
 * The class is either an `object` or a class with a public constructor taking no argument or one
 * `org.autojs.autojs.runtime.ScriptRuntime`.
 *
 * @property name primary name on the target object (the global object, or the [parent] module object).
 * @property aliases further names bound to the same module object (as `powerManager` / `power_manager`).
 * @property dollarPrefix also bind `$name` for the primary name and every alias (the current default of
 * `AugmentableKey`).
 * @property eager build the module when the runtime starts instead of on first access (roadmap T3), for modules
 * with start-up side effects or held by the host directly.
 * @property parent the @ScriptModule class this module is mounted on (as `util.inspect`); `Unit::class` for a
 * top-level module. A sub-module is built together with its parent module object.
 * @property attributes Rhino attributes ([ScriptAttributes]) of the names binding the module object on its target
 * (the current `augment(target, additionalAttributes = READONLY)` of selector / continuation adds READONLY).
 * @property emitter append an event emitter object (the methods of `scriptRuntime.events.emitter()`) to the end of the
 * module object's prototype chain, after the [ScriptPrototype] objects (the current `AsEmitter` of engines / images).
 * @property builtIn the JavaScript built-in object (a global such as `Array`, `Math` or `Number`) that the "extend
 * JavaScript built-in objects" option and `jsox` extend with the members declaring [ScriptFunction.builtIn] /
 * [ScriptProperty.builtIn] (the current `JsBuildInObjectExtensible` of Arrayx / Mathx / Numberx); "" for none.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptModule(
    val name: String,
    val aliases: Array<String> = [],
    val dollarPrefix: Boolean = true,
    val eager: Boolean = false,
    val parent: KClass<*> = Unit::class,
    val attributes: Int = ScriptAttributes.PERMANENT,
    val emitter: Boolean = false,
    val builtIn: String = "",
)

/**
 * Exposes a public function of a module as a script function.
 *
 * Several Kotlin functions with the same script [name] form one overloaded script function: the generated code
 * picks the overload by the number of arguments (trailing `undefined` arguments count as omitted) and, for equal
 * counts, by the argument types. Parameters follow the real Kotlin signature (roadmap T4): Kotlin default values make
 * a parameter optional, `vararg` takes the remaining arguments, `Any?` receives the raw script value, and parameters
 * of type `org.mozilla.javascript.Context` / `org.mozilla.javascript.VarScope` in front of the script parameters are
 * injected by the generated code instead of taken from the arguments. A `Unit` result becomes `undefined`.
 *
 * @property name script name; defaults to the Kotlin function name.
 * @property aliases further names bound to the same function object.
 * @property global where the function is bound besides (or instead of) the module object.
 * @property attributes Rhino property attributes ([ScriptAttributes]).
 * @property toStringRole for a script function named (or aliased) `toString`: whether it describes the module object
 * or converts its arguments ([ScriptToString]); any other function keeps [ScriptToString.NONE].
 * @property builtIn where the function is also bound when the module's [ScriptModule.builtIn] object is extended.
 * @property builtInOnly bind the function only there, neither on the module object nor on the global object (the
 * current `AS_IGNORED` flag of `Math.maxi` / `Math.mini`).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptFunction(
    val name: String = "",
    val aliases: Array<String> = [],
    val global: ScriptGlobal = ScriptGlobal.NONE,
    val attributes: Int = ScriptAttributes.PERMANENT,
    val toStringRole: ScriptToString = ScriptToString.NONE,
    val builtIn: Array<ScriptBuiltIn> = [],
    val builtInOnly: Boolean = false,
)

/**
 * Exposes a property (or a function without script parameters) as an accessor property evaluated on every read.
 * A `var` property also gets a setter that converts the assigned value to the property type.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptGetter(
    val name: String = "",
    val global: ScriptGlobal = ScriptGlobal.NONE,
    val attributes: Int = ScriptAttributes.PERMANENT,
)

/**
 * Exposes a property as a data property whose value is read once, when the module object is built; [builtIn] as for
 * [ScriptFunction.builtIn] ([ScriptBuiltIn.PROTOTYPE] does not apply to a property).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptProperty(
    val name: String = "",
    val global: ScriptGlobal = ScriptGlobal.NONE,
    val attributes: Int = ScriptAttributes.PERMANENT,
    val builtIn: Array<ScriptBuiltIn> = [],
)

/** Binds a Java class (as `NativeJavaClass`) under [name] on the module object or the global object. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@Repeatable
@MustBeDocumented
annotation class ScriptJavaClass(
    val name: String,
    val type: KClass<*>,
    val global: ScriptGlobal = ScriptGlobal.NONE,
    val attributes: Int = ScriptAttributes.PERMANENT,
)

/**
 * Makes the module object callable (`mime('text/plain')`, `util.inspect(o)`); the annotated function takes the call
 * arguments with the same conversion rules as [ScriptFunction]. At most one per module; overloads are not supported.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptInvoke

/** Makes the module object a constructor (`new Canvas(...)`); the annotated function returns the new object. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptConstruct

/**
 * Provides the prototype of the module object (a Java object such as `ScriptRuntime.util`, or a script object), read
 * once when the module object is built; the prototype's own prototype becomes the module object's original one. A
 * `List` value is a chain, nearest first (the current `augment(target, listOf(...))`, as colors with its color tables).
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptPrototype

/** Where a member is bound in addition to (or instead of) the module object. */
enum class ScriptGlobal {
    /** Only on the module object. */
    NONE,

    /** On the module object and on the global object (the current `AS_GLOBAL` flag). */
    ALSO,

    /** Only on the global object (the current `globalAssignmentFunctions` / `globalAssignmentGetters`). */
    ONLY,
}

/**
 * Where a member of a module declaring [ScriptModule.builtIn] is bound when that built-in object is extended (the
 * current `AS_JSOX_STATIC` / `AS_JSOX_PROTO` / `AS_JSOX_GLOBAL` flags).
 */
enum class ScriptBuiltIn {
    /** On the built-in object itself (`Math.sum`, `Array.ensureArray`, `Number.ICU`). */
    STATIC,

    /**
     * On the built-in object's prototype, with the receiver (`this`, a primitive for `Number.prototype`) passed as the
     * first argument (`[1, 1].distinct()` calls `distinct([1, 1])`); the function `length` leaves the receiver out.
     */
    PROTOTYPE,

    /** On the global object, replacing what is there (`parseFloat`). */
    GLOBAL,
}

/**
 * Role of a script function named `toString` (the current `AS_LITERAL_TO_STRING` / `AS_FUNCTIONAL_TO_STRING` flags).
 */
enum class ScriptToString {
    /** Not a `toString` function, or nothing to declare. */
    NONE,

    /** Describes the module object (as `String(global)`); `util.inspect` treats it as the object's custom toString. */
    LITERAL,

    /**
     * Converts its arguments (as `colors.toString(color)`); the module object also gets the hidden marker function
     * `__augmented_custom_to_string__` (DONTENUM), so `util.inspect` does not treat it as a custom toString.
     */
    FUNCTIONAL,
}

/**
 * Rhino property attribute bits, mirrored here because this module does not depend on Rhino. They must stay equal to
 * `org.mozilla.javascript.ScriptableObject.READONLY` / `DONTENUM` / `PERMANENT` (checked by the host's tests).
 */
object ScriptAttributes {
    const val EMPTY = 0
    const val READONLY = 1
    const val DONTENUM = 2
    const val PERMANENT = 4
}
