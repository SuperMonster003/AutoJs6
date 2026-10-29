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
 * @property assign the module has no module object (the current `Augmentable.assign` of global / legacy): every member
 * is bound on the global object (members keep the default `global = NONE`), [ScriptPrototype] objects become the
 * prototypes of the global object when the module is installed, and [name] is only the module's name in
 * `ScriptModuleLayer.ORDER`, never a script name. An assign module has no aliases, no invoke / construct, no parent,
 * no sub-modules, no emitter and no [builtIn].
 * @property proxy the module object is a proxy without own properties (the current `AugmentableProxy.proxying` of ui /
 * console): reading a name asks the [ScriptProxyGet] function, or without one looks the name up among the members
 * (functions, properties, getters, Java classes, sub-modules and the prototypes); writing a name calls the
 * [ScriptProxySet] function, or without one defines it on the proxy. A proxy module is not callable or constructible.
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
    val assign: Boolean = false,
    val proxy: Boolean = false,
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
 * @property arity for an overload whose last script parameter is a vararg (`vararg args: Any?`) that the function body
 * counts itself, alone or after fixed parameters (`source: Any?, vararg args: Any?`, appendix C Q171): the argument
 * counts of its real signatures, every script argument counted (the fixed parameters too), `[n]` for exactly n,
 * `[min, max]` (max -1: no upper limit), as its argument check enforces them (`ensureArguments*` of the current
 * module, the checks of the module). Metadata only (`arity` of the overload in script-modules.json, appendix C Q112):
 * the call and the function `length` do not change. Empty: not declared (a true variadic function, as `console.log`
 * or `files.join(base, ...paths)`).
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
    val arity: IntArray = [],
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
 * [arity] as [ScriptFunction.arity].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptInvoke(val arity: IntArray = [])

/**
 * Makes the module object a constructor (`new Canvas(...)`); the annotated function returns the new object. [arity] as
 * [ScriptFunction.arity].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptConstruct(val arity: IntArray = [])

/**
 * Provides the prototype of the module object (a Java object such as `ScriptRuntime.util`, or a script object), read
 * once when the module object is built; the prototype's own prototype becomes the module object's original one. A
 * `List` value is a chain, nearest first (the current `augment(target, listOf(...))`, as colors with its color tables).
 * A `ScriptNameLookup` value becomes an object without own properties that resolves names through it (the current
 * `SimpleGetterProxy` prototypes: the Java classes of global, the constants of WebSocket). For an assign module
 * ([ScriptModule.assign]) the chain goes on the global object, read when the module is installed.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptPrototype

/**
 * Runs when the module is installed on a runtime (before its names are defined and before an eager module is built),
 * with the module instance created at that moment. The function takes one `ScriptModuleInstallation`, which defines
 * further lazy names, registers actions run right after the module object is built and reaches other modules (for
 * what the annotations cannot declare: names computed at run time as selector's, side effects at start-up as ui's,
 * links between modules as `app.autojs`). At most one per module.
 *
 * The names the hook defines are declared here for the metadata (`installs` of the module in script-modules.json, read
 * by the d.ts and documentation tools; appendix C Q110), as the annotations declare the other members; the host's tests
 * check them against what the hook defines. Nothing reads them at run time.
 *
 * @property names the names the hook defines, grouped by kind and placement.
 * @property computed for a hook that also defines names computed at run time, which cannot be listed: where they come
 * from (as the global selector functions of selector); "" for none.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptInstall(
    val names: Array<ScriptInstallNames> = [],
    val computed: String = "",
)

/**
 * Names a [ScriptInstall] hook defines, of one [kind] and placement: on the module object, on the global object, or
 * both ([global]), or with [target] on the object bound to that name on the global object (as `wait.async`).
 *
 * @property attributes Rhino attributes ([ScriptAttributes]) the names are defined with.
 * @property arity for [ScriptInstallKind.FUNCTION] names: the argument counts the functions check, as
 * [ScriptFunction.arity] (metadata only, appendix C Q127); empty: not declared.
 */
@Target()
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptInstallNames(
    val names: Array<String>,
    val kind: ScriptInstallKind = ScriptInstallKind.PROPERTY,
    val global: ScriptGlobal = ScriptGlobal.NONE,
    val attributes: Int = ScriptAttributes.PERMANENT,
    val target: String = "",
    val arity: IntArray = [],
)

/** What a name defined by a [ScriptInstall] hook holds. */
enum class ScriptInstallKind {
    /** A data property whose value is a script function to call. */
    FUNCTION,

    /** An accessor property. */
    GETTER,

    /** A data property holding any other value (an error constructor, an object, a constant). */
    PROPERTY,
}

/**
 * Reads a name of a proxy module ([ScriptModule.proxy]): `fun get(members: Scriptable, name: String): Any?`, where
 * `members` holds the module's members; returning `Scriptable.NOT_FOUND` makes the name undefined. Called for every
 * name not defined on the proxy itself. At most one per module.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptProxyGet

/**
 * Writes a name of a proxy module ([ScriptModule.proxy]): `fun set(members: Scriptable, name: String, value: Any?)`.
 * At most one per module.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ScriptProxySet

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
