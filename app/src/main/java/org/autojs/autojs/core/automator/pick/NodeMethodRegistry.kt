package org.autojs.autojs.core.automator.pick

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.rhino.JsArgumentCoercion
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Array as JavaArray

/**
 * The methods of a node class that a `pickup` / `detect` result type calls by name (`"text"`, `["child", 0]`), with
 * their overloads and a caller that converts script arguments as the parameters declare them ([JsArgumentCoercion]:
 * JS numbers to `int` / `float` / `long`, NaN for an integer refused before calling). A name finds the methods of that
 * name, then the getter `get` + the name with its first character in upper case (`hintText` / `HintText` find
 * `getHintText`), the lookup the former `Invoker` made, without its `getMethod` reflection and its exceptions per miss.
 * [checks] tell the arguments some methods refuse (an invalid compass) before calling them.
 *
 * zh-CN: 节点类中可被 `pickup` / `detect` 结果类型按名称调用的方法 (`"text"`, `["child", 0]`), 含其重载与按参数声明转换脚本参数的调用器
 * ([JsArgumentCoercion]: JS 数值转 `int` / `float` / `long`, 整数处的 NaN 在调用前拒绝). 名称先找同名方法, 再找 `get` + 首字母大写的 getter
 * (`hintText` / `HintText` 找到 `getHintText`), 即原 `Invoker` 的查找, 但不再逐次以 `getMethod` 反射并在未命中时抛异常. [checks] 在调用前判定部分方法拒绝的参数 (无效的罗盘).
 *
 * Created by SuperMonster003 on Oct 25, 2026.
 */
class NodeMethodTable(
    val type: Class<*>,
    methods: Collection<Method>,
    private val checks: Map<String, (List<Any?>) -> String?> = emptyMap(),
) {

    private val byName: Map<String, List<Method>> = methods.groupBy { it.name }.mapValues { (_, list) -> list.sortedWith(ORDER) }

    /** The method names. zh-CN: 方法名. */
    val names: Set<String> get() = byName.keys

    /** The methods by name, in the order a call tries them. zh-CN: 按名称的方法, 顺序即调用时的尝试顺序. */
    val methods: Map<String, List<Method>> get() = byName

    /**
     * The spellings worth suggesting: every name, and `xyz` for a getter `getXyz` (the first character an ASCII letter).
     * zh-CN: 可作为候选的拼写: 每个名称, 以及 getter `getXyz` 的 `xyz` (首字符为 ASCII 字母).
     */
    val spellings: List<String> by lazy {
        val out = LinkedHashSet<String>(byName.keys)
        for (name in byName.keys) {
            val first = name.getOrNull(3) ?: continue
            if (name.startsWith("get") && first in 'A'..'Z') out += first.lowercaseChar() + name.substring(4)
        }
        out.toList()
    }

    /**
     * The methods [name] finds, in the order a call tries them: [name] itself, then the getter `get` + [name] with its
     * first character in upper case. zh-CN: [name] 找到的方法, 顺序即调用时的尝试顺序: [name] 本身, 再是 `get` + 首字母大写的 getter.
     */
    fun lookup(name: String): List<Method> {
        if (name.isEmpty()) return emptyList()
        val own = byName[name].orEmpty()
        val getter = byName["get${name.replaceFirstChar { it.uppercase() }}"].orEmpty()
        return if (getter.isEmpty()) own else own + getter
    }

    /**
     * The method [name] calls with [args], told without calling anything: the first method in [lookup] order whose
     * parameters take the converted arguments.
     * zh-CN: [name] 以 [args] 调用的方法, 判定时不调用任何方法: 按 [lookup] 顺序首个能接受转换后参数的方法.
     */
    fun resolve(name: String, args: List<Any?>): NodeMethodResolution {
        val candidates = lookup(name)
        if (candidates.isEmpty()) return NodeMethodResolution.UnknownName
        var refusal: String? = null
        for (method in candidates) {
            when (val converted = convert(method, args)) {
                null -> continue
                NAN_REFUSED -> refusal = refusal ?: NAN
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val arguments = converted as Array<Any?>
                    checks[method.name]?.invoke(arguments.toList())?.let { return NodeMethodResolution.Rejected(it) }
                    return NodeMethodResolution.Found(method, arguments)
                }
            }
        }
        return refusal?.let { NodeMethodResolution.Rejected(it) } ?: NodeMethodResolution.NoOverload(candidates.map(::signature))
    }

    /** [args] converted for [method], [NAN_REFUSED] for NaN in an integer parameter, or null when they do not fit. */
    private fun convert(method: Method, args: List<Any?>): Any? {
        val params = method.parameterTypes
        val fixed = if (method.isVarArgs) params.size - 1 else params.size
        if (if (method.isVarArgs) args.size < fixed else args.size != fixed) return null
        val out = arrayOfNulls<Any?>(params.size)
        var nan = false
        for (i in 0 until fixed) {
            out[i] = when (val arg = argument(params[i], args[i])) {
                MISFIT -> return null
                NAN_REFUSED -> NAN_REFUSED.also { nan = true }
                else -> arg
            }
        }
        if (method.isVarArgs) {
            val component = params.last().componentType!!
            val rest = JavaArray.newInstance(component, args.size - fixed)
            for (i in fixed until args.size) {
                when (val arg = argument(component, args[i])) {
                    MISFIT -> return null
                    NAN_REFUSED -> nan = true
                    else -> JavaArray.set(rest, i - fixed, arg)
                }
            }
            out[params.lastIndex] = rest
        }
        return if (nan) NAN_REFUSED else out
    }

    /** [arg] as a parameter of [type] takes it, [NAN_REFUSED] or [MISFIT]. */
    private fun argument(type: Class<*>, arg: Any?): Any? {
        if (arg is Double && arg.isNaN() && type in INTEGRAL) return NAN_REFUSED
        val value = when {
            arg is CharSequence && arg !is String && type == String::class.java -> arg.toString()
            else -> JsArgumentCoercion.coerce(type, arg)
        }
        return if (fits(type, value)) value else MISFIT
    }

    /** Whether reflection passes [value] to a parameter of [type] (unboxing and widening of the primitives). */
    private fun fits(type: Class<*>, value: Any?): Boolean = when (type) {
        java.lang.Boolean.TYPE -> value is Boolean
        java.lang.Character.TYPE -> value is Char
        java.lang.Byte.TYPE -> value is Byte
        java.lang.Short.TYPE -> value is Short || value is Byte
        java.lang.Integer.TYPE -> value is Int || value is Short || value is Byte || value is Char
        java.lang.Long.TYPE -> value is Long || value is Int || value is Short || value is Byte || value is Char
        java.lang.Float.TYPE -> value is Float || value is Long || value is Int || value is Short || value is Byte || value is Char
        java.lang.Double.TYPE -> value is Double || value is Float || value is Long || value is Int || value is Short || value is Byte || value is Char
        else -> value == null || type.isInstance(value)
    }

    companion object {

        /** The message of `roundToInt` / `roundToLong` for NaN (kotlin.math), as the former conversion threw it. */
        private const val NAN = "Cannot round NaN value."

        private val NAN_REFUSED = Any()
        private val MISFIT = Any()

        private val INTEGRAL: Set<Class<*>> = setOf(
            java.lang.Long.TYPE, java.lang.Long::class.java, java.lang.Integer.TYPE, java.lang.Integer::class.java,
            java.lang.Short.TYPE, java.lang.Short::class.java, java.lang.Byte.TYPE, java.lang.Byte::class.java,
        )

        /** Plain methods before varargs, fewer parameters first, then by the parameter types: a stable order. */
        private val ORDER: Comparator<Method> = compareBy<Method> { it.isVarArgs }.thenBy { it.parameterCount }.thenBy { m -> m.parameterTypes.joinToString(",") { it.name } }

        /** `name(type, ...)` with the simple names of the parameter types. zh-CN: 以参数类型简名写出的签名. */
        fun signature(method: Method): String = "${method.name}(${method.parameterTypes.joinToString(", ") { if (it.isArray) "${it.componentType!!.simpleName}..." else it.simpleName }})"

    }

}

/**
 * What [NodeMethodTable.resolve] makes of a method name and its arguments.
 * zh-CN: [NodeMethodTable.resolve] 对方法名及其参数的判定.
 */
sealed class NodeMethodResolution {

    /** The [method] to call with the converted [arguments] (`auto.explain` shows them, P3.5). zh-CN: 以转换后的 [arguments] 调用的 [method] (`auto.explain` 展示它们, P3.5). */
    class Found(val method: Method, internal val arguments: Array<Any?>) : NodeMethodResolution() {

        /**
         * Calls [method] on [node]. A null pointer or an interruption inside the method yields null, as the former
         * `Invoker` had it; any other exception it throws is thrown as is.
         * zh-CN: 在 [node] 上调用 [method]. 方法内的空指针或中断得到 null, 同原 `Invoker`; 其余异常原样抛出.
         */
        fun invoke(node: Any): Any? = try {
            method.invoke(node, *arguments)
        } catch (e: InvocationTargetException) {
            val target = e.targetException
            if (target is NullPointerException || target is InterruptedException || target.cause is InterruptedException) null else throw target
        }

    }

    /** No method of that name, nor its getter. zh-CN: 没有该名称的方法, 也没有对应的 getter. */
    object UnknownName : NodeMethodResolution()

    /** Methods of that name, none taking the arguments; [signatures] lists them. zh-CN: 有该名称的方法, 但都不接受这些参数; [signatures] 列出它们. */
    class NoOverload(val signatures: List<String>) : NodeMethodResolution()

    /** The method that takes the arguments refuses them, for [reason] (NaN for an integer, an invalid compass). zh-CN: 接受参数的方法拒绝了它们, 原因为 [reason]. */
    class Rejected(val reason: String) : NodeMethodResolution()

}

/**
 * The node method registry of `UiObject` (docs/selector/ROADMAP.md D11, P1.2): the public methods `UiObject` and its
 * interfaces of the host declare (`UiObjectActions`, `UiNode`; 151 at P1.2), and the getters without parameters of
 * `AccessibilityNodeInfoCompat` (`getHintText` / `isChecked` ...; 65, and the readers `canOpenPopup` /
 * `hasRequestInitialAccessibilityFocus` of appendix C Q10), built once by reflection. Not in it: the methods of
 * `Object`, `recycle`, internal (mangled) names, and the other methods of `AccessibilityNodeInfoCompat`: its setters
 * (the nodes of the service are sealed and refuse them), its methods with parameters even where `UiObject` overrides
 * them (`getChild(int)`: use `child`; `findAccessibilityNodeInfosByText`; `performAction(int)`: the `performAction` of
 * `UiObjectActions` takes the same calls), and the rest without parameters that read nothing (`refresh`, `unwrap`).
 * `NodeMethodRegistryTest` checks the classification against `UiObject::class.java.methods`.
 *
 * zh-CN: `UiObject` 的节点方法注册表 (D11, P1.2): `UiObject` 及宿主中它实现的接口 (`UiObjectActions`, `UiNode`) 声明的公开方法 (P1.2 时 151 个), 加上
 * `AccessibilityNodeInfoCompat` 的无参 getter (`getHintText` / `isChecked` ..., 以及附录 C Q10 补收的只读方法 `canOpenPopup` / `hasRequestInitialAccessibilityFocus`), 由反射一次生成. 不含: `Object` 的方法, `recycle`, internal (混淆) 名称,
 * 以及 `AccessibilityNodeInfoCompat` 的其余方法: setter (服务给出的节点已密封, 会拒绝), 带参数的方法即使被 `UiObject` 覆盖 (`getChild(int)`: 改用 `child`;
 * `findAccessibilityNodeInfosByText`; `performAction(int)`: `UiObjectActions` 的 `performAction` 接受同样的调用), 以及不读取内容的其余无参方法 (`refresh`, `unwrap`).
 * `NodeMethodRegistryTest` 以 `UiObject::class.java.methods` 核对分类.
 */
object NodeMethodRegistry {

    /** Why a public method of the node class is in the registry or not. zh-CN: 节点类的公开方法在或不在注册表中的原因. */
    enum class Kind(val registered: Boolean) {

        /** Declared by `UiObject` or an interface of the host. zh-CN: 由 `UiObject` 或宿主的接口声明. */
        OWN(true),

        /**
         * A getter without parameters of `AccessibilityNodeInfoCompat`: `getXxx` / `isXxx`, and the readers `canXxx` / `hasXxx`
         * (appendix C Q10). zh-CN: `AccessibilityNodeInfoCompat` 的无参 getter: `getXxx` / `isXxx`, 以及只读的 `canXxx` / `hasXxx` (附录 C Q10).
         */
        COMPAT_GETTER(true),

        /** `equals`, `hashCode`, `toString`, `getClass`, `wait`, `notify`, `notifyAll`. zh-CN: `Object` 的方法. */
        OBJECT(false),

        /** `recycle`: the nodes of a query are recycled by the platform policy, not by scripts. zh-CN: `recycle`: 查询节点按平台策略回收, 不由脚本回收. */
        RECYCLE(false),

        /**
         * A static, synthetic or covariant bridge method, an internal one (its name mangled with `$`), or one whose signature
         * names a class the platform lacks (API 24-29: `getExtraRenderingInfo` returns a class of API 30; appendix C Q16).
         * zh-CN: 静态, 合成或协变桥接方法, internal 方法 (名称含 `$`), 或签名中含平台所缺类的方法 (API 24-29: `getExtraRenderingInfo` 返回 API 30 的类; 附录 C Q16).
         */
        INTERNAL(false),

        /** Another method of `AccessibilityNodeInfoCompat` (a setter, a method with parameters, `refresh`, `unwrap`). zh-CN: `AccessibilityNodeInfoCompat` 的其他方法. */
        COMPAT(false),
    }

    private val OBJECT_METHODS = setOf("equals", "hashCode", "toString", "getClass", "wait", "notify", "notifyAll")

    /** The registry of `UiObject`. zh-CN: `UiObject` 的注册表. */
    val uiObject: NodeMethodTable by lazy { tableOf(SignatureReader) }

    private val compatSignatures: Set<String> by lazy { compatSignatures(SignatureReader) }

    /** Why [method] (a public method of `UiObject`) is in the registry or not. zh-CN: [method] (`UiObject` 的公开方法) 在或不在注册表中的原因. */
    fun kindOf(method: Method): Kind = kindOf(method, compatSignatures, SignatureReader)

    /** The registry of `UiObject` with the signatures read by [reader]. zh-CN: 以 [reader] 读取签名的 `UiObject` 注册表. */
    internal fun tableOf(reader: SignatureReader): NodeMethodTable {
        val compat = compatSignatures(reader)
        val methods = UiObject::class.java.methods.filter { kindOf(it, compat, reader).registered }
        return NodeMethodTable(UiObject::class.java, methods, mapOf("compass" to ::compassRefusal))
    }

    private fun compatSignatures(reader: SignatureReader): Set<String> = AccessibilityNodeInfoCompat::class.java.methods.mapNotNullTo(HashSet()) { readable { signatureKey(it, reader) } }

    private fun kindOf(method: Method, compat: Set<String>, reader: SignatureReader): Kind = readable {
        when {
            Modifier.isStatic(method.modifiers) || method.isSynthetic || isCovariantBridge(method) || '$' in method.name -> Kind.INTERNAL
            method.name in OBJECT_METHODS -> Kind.OBJECT
            method.name == "recycle" -> Kind.RECYCLE
            signatureKey(method, reader) !in compat -> if (method.declaringClass.name.startsWith(HOST_PACKAGE)) Kind.OWN else Kind.COMPAT
            isGetter(method, reader) -> Kind.COMPAT_GETTER
            else -> Kind.COMPAT
        }
    } ?: Kind.INTERNAL

    /**
     * [block], or null when it reads a signature naming a class the platform lacks (appendix C Q16).
     * zh-CN: [block] 的结果, 读取的签名含平台所缺的类时为 null (附录 C Q16).
     */
    private inline fun <T : Any> readable(block: () -> T): T? = try {
        block()
    } catch (_: LinkageError) {
        null
    } catch (_: TypeNotPresentException) {
        null
    }

    /**
     * A bridge the compiler made for a covariant return (`child(int): UiNode` beside `child(int): UiObject`). Kotlin also
     * flags the forwarders to the default methods of an interface (`click`, `setText` of `UiObjectActions`) as bridges;
     * those have no twin and are the methods themselves.
     * zh-CN: 编译器为协变返回值生成的桥接方法 (`child(int): UiNode` 与 `child(int): UiObject` 并存). Kotlin 也把转发到接口默认方法的方法
     * (`UiObjectActions` 的 `click`, `setText`) 标为桥接, 它们没有同签名的对应方法, 本身就是要调用的方法.
     */
    private fun isCovariantBridge(method: Method): Boolean = method.isBridge && method.declaringClass.declaredMethods.any {
        !it.isBridge && it.name == method.name && it.parameterTypes.contentEquals(method.parameterTypes)
    }

    private fun isGetter(method: Method, reader: SignatureReader): Boolean {
        if (method.parameterCount != 0 || reader.returnType(method) == Void.TYPE) return false
        val name = method.name
        return READER_PREFIXES.any { name.length > it.length && name.startsWith(it) && name[it.length] in 'A'..'Z' }
    }

    private fun signatureKey(method: Method, reader: SignatureReader) = method.name + reader.parameterTypes(method).joinToString(",", "(", ")") { it.name }

    /**
     * Reads the types in the signature of a method. ART resolves them when they are read, and throws `NoClassDefFoundError`
     * for a class the platform lacks (appendix C Q16); HotSpot resolves them when it lists the methods, so a JVM test
     * hands in a reader that fails as ART does.
     * zh-CN: 读取方法签名中的类型. ART 在读取时才解析, 遇平台所缺的类抛 `NoClassDefFoundError` (附录 C Q16); HotSpot 在列出方法时即已解析,
     * 因此 JVM 测试传入一个像 ART 那样失败的读取器.
     */
    internal interface SignatureReader {

        fun returnType(method: Method): Class<*> = method.returnType

        fun parameterTypes(method: Method): Array<Class<*>> = method.parameterTypes

        /** The reader of the platform. zh-CN: 平台的读取器. */
        companion object : SignatureReader

    }

    /** `compass(compass)`: a compass that does not parse is refused (it threw once a node was found). zh-CN: 无法解析的罗盘被拒绝 (原先在找到节点后抛出). */
    private fun compassRefusal(args: List<Any?>): String? = when (val parse = (args.firstOrNull() as? CharSequence)?.let(CompassParser::tryParse)) {
        is CompassParse.NotCompass -> PickMessages.invalidCompass(parse.compass)
        is CompassParse.BadRest -> PickMessages.invalidRestCompass(parse.rest)
        else -> null
    }

    private const val HOST_PACKAGE = "org.autojs."

    /** The prefixes of the readers without parameters (D11; `can` / `has` added by appendix C Q10). zh-CN: 无参只读方法的前缀. */
    private val READER_PREFIXES = listOf("get", "is", "can", "has")

}
