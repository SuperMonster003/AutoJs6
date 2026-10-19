package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Array as JavaArray

/** Public Java members run in the host UID. Android permissions and hidden API rules still apply. */
internal class JavaInteropClassSpec(val type: Class<*>) {
    val className: String get() = type.name
    fun toJson(): JSONObject = JSONObject()
        .put("className", className).put("explicit", true)
        .put("constructable", !type.isInterface && !Modifier.isAbstract(type.modifiers) && type.constructors.isNotEmpty())
        .put("staticMethods", JSONArray(type.methods.filter { Modifier.isStatic(it.modifiers) }.map { it.name }.distinct().sorted()))
        .put("instanceMethods", JSONArray(type.methods.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.distinct().sorted()))
        .put("staticFields", JSONArray(type.fields.filter { Modifier.isStatic(it.modifiers) }.map { it.name }.distinct().sorted()))
        .put("instanceFields", JSONArray(type.fields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.distinct().sorted()))
        .put("fields", JSONArray(type.fields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.distinct().sorted()))
        .put("constructors", JSONArray(type.constructors.map { signature(it.parameterTypes) }.sorted()))
        .put("methods", JSONArray(type.methods.map { "${it.name}${signature(it.parameterTypes)}:${it.returnType.name}" }.distinct().sorted()))
    private fun signature(types: Array<Class<*>>) = types.joinToString(",", "(", ")") { it.name }
}

/** The legacy name is retained for broker compatibility; there is no class/member allowlist. */
internal object ControlledJavaInteropPolicy {
    val methods = setOf("type", "callStatic", "new", "call", "callInstance", "getField", "getStatic", "setField", "setStatic",
        "release", "dispose", "getContext", "newArray", "arrayLength", "getArrayItem", "setArrayItem", "toArray", "instanceOf", "getClassName", "proxy", "defineClass", "loadDex", "loadJar", "drainCallbacks", "completeCallback", "closeCallbacks", "callSuper", "retain")

    interface Handles {
        fun resolve(handleId: String): Any?
        fun register(value: Any, spec: JavaInteropClassSpec): JSONObject
        fun loadClass(name: String, loader: String? = null): Class<*> = ControlledJavaInteropPolicy.loadClass(name)
        fun constructorPrefix(type: Class<*>): Array<Any?> = emptyArray()
        fun describeClass(type: Class<*>): JSONObject = JavaInteropClassSpec(type).toJson()
    }

    private val primitives = mapOf("boolean" to Boolean::class.javaPrimitiveType!!, "byte" to Byte::class.javaPrimitiveType!!,
        "short" to Short::class.javaPrimitiveType!!, "int" to Int::class.javaPrimitiveType!!, "long" to Long::class.javaPrimitiveType!!,
        "float" to Float::class.javaPrimitiveType!!, "double" to Double::class.javaPrimitiveType!!, "char" to Char::class.javaPrimitiveType!!,
        "void" to Void.TYPE)
    private val boxed = mapOf(Boolean::class.javaPrimitiveType to Boolean::class.javaObjectType, Byte::class.javaPrimitiveType to Byte::class.javaObjectType,
        Short::class.javaPrimitiveType to Short::class.javaObjectType, Int::class.javaPrimitiveType to Int::class.javaObjectType,
        Long::class.javaPrimitiveType to Long::class.javaObjectType, Float::class.javaPrimitiveType to Float::class.javaObjectType,
        Double::class.javaPrimitiveType to Double::class.javaObjectType, Char::class.javaPrimitiveType to Char::class.javaObjectType)

    fun loadClass(name: String): Class<*> {
        val value = name.trim()
        require(value.isNotEmpty()) { "className must not be empty" }
        primitives[value]?.let { return it }
        if (value.endsWith("[]")) return JavaArray.newInstance(loadClass(value.dropLast(2)), 0).javaClass
        return Class.forName(value, false, ControlledJavaInteropPolicy::class.java.classLoader)
    }

    fun policyJson(): JSONObject = JSONObject().put("limitsEnforcedBy", "host_provider").put("access", "public")
        .put("allowedClasses", JSONArray()).put("deniedClassPrefixes", JSONArray()).put("classes", JSONObject())
    fun describeClass(className: String) = JavaInteropClassSpec(loadClass(className)).toJson()
    fun validateClass(className: String): ErrorDescriptor? = try { loadClass(className); null } catch (e: Exception) {
        ErrorDescriptor(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, "Cannot load Java class $className: ${e.message}")
    }

    fun handleJson(id: String, value: Any, spec: JavaInteropClassSpec): JSONObject {
        val snapshot = JSONObject()
        snapshot.put("string", if (value is String) value else if (value is Enum<*>) value.name else value.javaClass.name + "@" + Integer.toHexString(System.identityHashCode(value)))
        if (value is Enum<*>) snapshot.put("name", value.name)
        // Only immutable primitive snapshots; object graphs are accessed through explicit calls.
        for (field in value.javaClass.fields.filterNot { Modifier.isStatic(it.modifiers) }) runCatching {
            when (val raw = field.get(value)) {
                null -> snapshot.put(field.name, JSONObject.NULL)
                is String, is Boolean, is Int, is Short, is Byte -> snapshot.put(field.name, raw)
                is Long -> snapshot.put(field.name, longValue(raw))
                is Float, is Double -> snapshot.put(field.name, numberValue((raw as Number).toDouble()))
                else -> Unit
            }
        }
        return JSONObject().put("__autojs6JavaHandle", id).put("className", spec.className).put("value", snapshot)
    }

    fun execute(method: String, payload: JSONObject, handles: Handles, context: Any): Result = reflectCall(method) {
        fun type() = handles.loadClass(payload.getString("className"), payload.optString("loader").takeIf { it.isNotEmpty() })
        fun target() = handles.resolve(payload.getString("handle")) ?: error("Java object handle is released or belongs to another execution")
        fun args() = payload.optJSONArray("args") ?: JSONArray()
        fun types(): Array<Class<*>>? = payload.optJSONArray("parameterTypes")?.let { a -> Array(a.length()) { handles.loadClass(a.getString(it),payload.optString("loader").takeIf { it.isNotEmpty() }) } }
        fun field(target: Any?, type: Class<*>, static: Boolean, write: Boolean): Any? {
            val member = type.getField(payload.getString("field"))
            require(Modifier.isStatic(member.modifiers) == static) { "Field static/instance mismatch" }
            if (!write) return member.get(target)
            require(!Modifier.isFinal(member.modifiers)) { "Cannot write a final field" }
            val value = coerce(payload.opt("value"), member.type, handles) ?: error("Field value does not match ${member.type.name}")
            member.set(target, value.value)
            return true
        }
        when (method) {
            "type" -> return@reflectCall handles.describeClass(type())
            "getContext" -> return@reflectCall handles.register(context, JavaInteropClassSpec(context.javaClass))
            "getClassName" -> return@reflectCall target().javaClass.name
            "instanceOf" -> return@reflectCall type().isInstance(target())
            "new" -> {
                val klass = type()
                val prefix = handles.constructorPrefix(klass)
                val (member, values) = selectOverload(klass.constructors.map { it to it.parameterTypes.drop(prefix.size).toTypedArray() }, args(), handles, types())
                return@reflectCall handles.register(member.newInstance(*prefix, *values), JavaInteropClassSpec(klass))
            }
            "callStatic", "call", "callInstance" -> {
                val static = method == "callStatic"
                val receiver = if (static) null else target()
                val klass = receiver?.javaClass ?: type()
                val members = klass.methods.filter { it.name == payload.getString("method") && Modifier.isStatic(it.modifiers) == static }
                    .sortedBy { it.isBridge }.map { accessibleMethod(klass, it) }.distinctBy { it.name + it.parameterTypes.joinToString { type -> type.name } }
                val (member, values) = selectOverload(members.map { it to it.parameterTypes }, args(), handles, types())
                marshal(member.invoke(receiver, *values), handles)
            }
            "getField", "setField" -> target().let { marshal(field(it, it.javaClass, false, method == "setField"), handles) }
            "getStatic", "setStatic" -> marshal(field(null, type(), true, method == "setStatic"), handles)
            "newArray" -> {
                val component = type()
                require(component != Void.TYPE) { "void arrays are not supported" }
                val input = payload.get("value")
                val array = if (input is JSONArray) {
                    val arrayType = JavaArray.newInstance(component, 0).javaClass
                    coerce(input, arrayType, handles)?.value ?: error("Array elements do not match ${component.name}")
                } else {
                    val length = index(input)
                    require(length >= 0) { "Array length must be non-negative" }
                    JavaArray.newInstance(component, length)
                }
                handles.register(array!!, JavaInteropClassSpec(array.javaClass))
            }
            "arrayLength" -> JavaArray.getLength(target())
            "getArrayItem" -> marshal(JavaArray.get(target(), index(payload.get("index"))), handles)
            "setArrayItem" -> {
                val array = target()
                val value = coerce(payload.opt("value"), array.javaClass.componentType ?: error("Expected an array"), handles)
                    ?: error("Array element has the wrong type")
                JavaArray.set(array, index(payload.get("index")), value.value)
                true
            }
            "toArray" -> arrayJson(target(), handles)
            else -> error("Unsupported Java operation: $method")
        }
    }

    private fun index(value: Any): Int {
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toInt().toDouble()) { "Expected an int index/length" }
        return value.toInt()
    }

    /** Public methods of private implementation classes use their public interface/base declaration. */
    private fun accessibleMethod(type: Class<*>, method: Method): Method {
        if (Modifier.isPublic(method.declaringClass.modifiers)) return method
        for (iface in type.interfaces) {
            val member = runCatching { iface.getMethod(method.name, *method.parameterTypes) }.getOrNull()
            if (member != null) return accessibleMethod(iface, member)
        }
        type.superclass?.let { parent ->
            runCatching { parent.getMethod(method.name, *method.parameterTypes) }.getOrNull()?.let { return accessibleMethod(parent, it) }
        }
        return method
    }

    private data class Coerced(val value: Any?, val score: Int)
    private fun <T> selectOverload(candidates: List<Pair<T, Array<Class<*>>>>, args: JSONArray, handles: Handles,
                                 signature: Array<Class<*>>?): Pair<T, Array<Any?>> {
        val matches = candidates.filter { it.second.size == args.length() && (signature == null || it.second.contentEquals(signature)) }
            .mapNotNull { (member, types) ->
                val values = types.mapIndexed { i, t -> coerce(args.opt(i), t, handles) ?: return@mapNotNull null }
                Triple(member, types, values)
            }
        val score = matches.maxOfOrNull { it.third.sumOf { value -> value.score } }
            ?: error("No matching public overload; pass parameterTypes or java.cast for an exact signature")
        val best = matches.filter { it.third.sumOf { value -> value.score } == score }
        // Prefer the most specific reference overload, including for null arguments.
        val chosen = best.singleOrNull() ?: best.singleOrNull { candidate -> best.all { other ->
            candidate === other || candidate.second.indices.all { i -> other.second[i].isAssignableFrom(candidate.second[i]) }
        } } ?: error("Ambiguous Java overload; pass parameterTypes or java.cast")
        return chosen.first to chosen.third.map { it.value }.toTypedArray()
    }

    private fun coerce(value: Any?, type: Class<*>, handles: Handles): Coerced? {
        if (value == null || value === JSONObject.NULL) return if (type.isPrimitive) null else Coerced(null, 0)
        if (value is JSONObject) {
            if (value.has("__autojs6JavaType")) {
                val explicit = handles.loadClass(value.getString("__autojs6JavaType"))
                if (explicit != type && !(boxed[explicit] ?: explicit).let { !type.isPrimitive && type.isAssignableFrom(it) }) return null
                val input = value.opt("value")
                val converted = if (explicit == Long::class.javaPrimitiveType && input is String) input.toLongOrNull()?.let { Coerced(it, 20) }
                    else if ((explicit == Double::class.javaPrimitiveType || explicit == Float::class.javaPrimitiveType) && input is String) {
                        input.toDoubleOrNull()?.let { Coerced(if (explicit == Float::class.javaPrimitiveType) it.toFloat() else it, 20) }
                    } else coerce(input, explicit, handles)
                return converted?.let { Coerced(it.value, 20) }
            }
            if (value.has("__autojs6JavaLong")) return coerce(JSONObject().put("__autojs6JavaType", "long").put("value", value.getString("__autojs6JavaLong")), type, handles)
            val target = handles.resolve(value.optString("__autojs6JavaHandle")) ?: return null
            return if (type.isInstance(target)) Coerced(target, if (target.javaClass == type) 10 else 2) else null
        }
        if (value is JSONArray && type.isArray) {
            val component = type.componentType!!
            val array = JavaArray.newInstance(component, value.length())
            for (i in 0 until value.length()) JavaArray.set(array, i, (coerce(value.opt(i), component, handles) ?: return null).value)
            return Coerced(array, 4)
        }
        val boxedType = boxed[type] ?: type
        return when (value) {
            is Boolean -> if (boxedType.isInstance(value)) Coerced(value, if (boxedType == Boolean::class.javaObjectType) 10 else 1) else null
            is String -> when {
                boxedType == Char::class.javaObjectType && value.length == 1 -> Coerced(value[0], 5)
                boxedType.isInstance(value) -> Coerced(value, if (boxedType == String::class.java) 10 else 2)
                else -> null
            }
            is Number -> {
                val d = value.toDouble()
                val integral = d.isFinite() && d % 1.0 == 0.0 && kotlin.math.abs(d) <= 9007199254740991.0
                when (boxedType) {
                    Int::class.javaObjectType -> if (integral && d >= Int.MIN_VALUE && d <= Int.MAX_VALUE) Coerced(d.toInt(), 10) else null
                    Long::class.javaObjectType -> if (integral) Coerced(d.toLong(), 8) else null
                    Short::class.javaObjectType -> if (integral && d >= Short.MIN_VALUE && d <= Short.MAX_VALUE) Coerced(d.toInt().toShort(), 6) else null
                    Byte::class.javaObjectType -> if (integral && d >= Byte.MIN_VALUE && d <= Byte.MAX_VALUE) Coerced(d.toInt().toByte(), 5) else null
                    Double::class.javaObjectType -> Coerced(d, if (integral) 3 else 10)
                    Float::class.javaObjectType -> if (d.isFinite() && !d.toFloat().isFinite()) null else Coerced(d.toFloat(), if (integral) 2 else 8)
                    else -> if (boxedType.isInstance(value)) Coerced(value, 1) else null
                }
            }
            else -> null
        }
    }

    private fun longValue(value: Long): Any = if (value in -9007199254740991L..9007199254740991L) value else JSONObject().put("__autojs6JavaLong", value.toString())
    private fun numberValue(value: Double): Any = if (value.isFinite()) value else JSONObject().put("__autojs6JavaNumber", value.toString())
    private fun arrayJson(value: Any, handles: Handles, seen: MutableSet<Any> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())): JSONArray {
        seen.add(value)
        return try { JSONArray().also { out ->
            for (i in 0 until JavaArray.getLength(value)) out.put(marshal(JavaArray.get(value, i), handles, seen))
        }} finally { seen.remove(value) }
    }
    internal fun marshal(value: Any?, handles: Handles, seen: MutableSet<Any> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())): Any = when (value) {
        null -> JSONObject.NULL
        is String, is Boolean, is Int, is Short, is Byte -> value
        is Long -> longValue(value)
        is Float, is Double -> numberValue((value as Number).toDouble())
        is Char -> value.toString()
        // Mutable CharSequence implementations (StringBuilder, Spannable) must remain live handles.
        else -> if (value.javaClass.isArray && value !in seen) arrayJson(value, handles, seen) else handles.register(value, JavaInteropClassSpec(value.javaClass))
    }

    internal fun callbackValue(value: Any?, type: Class<*>, handles: Handles): Any? {
        if (type == Void.TYPE) return null
        return (coerce(value, type, handles) ?: error("Callback return value does not match ${type.name}")).value
    }

    private inline fun reflectCall(label: String, block: () -> Any?): Result = try { Result(value = block()) } catch (error: Exception) {
        val cause = if (error is InvocationTargetException) error.targetException ?: error else error
        Result(error = ErrorDescriptor(if (error is InvocationTargetException) NodeRuntimeErrorCodes.JAVA_CALL_FAILED else if(error is ClassNotFoundException) NodeRuntimeErrorCodes.JAVA_CLASS_DENIED else NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
            "$label: ${cause.javaClass.name}: ${cause.message.orEmpty()}"))
    }
    data class ErrorDescriptor(val code: String, val message: String) {
        fun toBridgeError(request: NodeBridgeRequest) = NodeBridgeError.javaInterop(message, code, request.module, request.method)
    }
    data class Result(val value: Any? = null, val error: ErrorDescriptor? = null)
}
