package org.autojs.autojs.engine

import androidx.annotation.Keep
import org.mozilla.classfile.ByteCode
import org.mozilla.classfile.ClassFileWriter
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Referenced by generated DEX, including in minified packaged applications. */
@Keep
interface RuntimeJavaCallbackHandler {
    fun invoke(receiver: Any, method: Int, arguments: Array<Any?>): Any?
}

/** Serialization must not call an adapter's user-defined toString callback. */
@Keep
interface RuntimeJavaAdapter

internal object RuntimeJavaSubclassGenerator {
    private const val HANDLER = "org/autojs/autojs/engine/RuntimeJavaCallbackHandler"
    private const val FIELD = "__autojs6_handler"
    private val primitiveSignatures = mapOf(java.lang.Void.TYPE to "V", Boolean::class.javaPrimitiveType to "Z", Byte::class.javaPrimitiveType to "B",
        Short::class.javaPrimitiveType to "S", Char::class.javaPrimitiveType to "C", Int::class.javaPrimitiveType to "I", Long::class.javaPrimitiveType to "J",
        Float::class.javaPrimitiveType to "F", Double::class.javaPrimitiveType to "D")
    private val boxes = mapOf("Z" to "Boolean", "B" to "Byte", "S" to "Short", "C" to "Character", "I" to "Integer", "J" to "Long", "F" to "Float", "D" to "Double")
    private val unbox = mapOf("Z" to "booleanValue", "B" to "byteValue", "S" to "shortValue", "C" to "charValue", "I" to "intValue", "J" to "longValue", "F" to "floatValue", "D" to "doubleValue")
    fun validName(name:String):Boolean = name.split('.').all { part ->
        val points=part.codePoints().toArray()
        points.isNotEmpty() && Character.isJavaIdentifierStart(points[0]) && points.drop(1).all(Character::isJavaIdentifierPart)
    }
    fun key(method: Method) = method.name + method.parameterTypes.joinToString(",", "(", ")") { it.typeName }
    fun superName(index: Int) = "__autojs6_super_$index"
    private fun signature(type: Class<*>): String = primitiveSignatures[type] ?: if (type.isArray) type.name.replace('.', '/') else "L${type.name.replace('.', '/')};"
    private fun signature(method: Method) = method.parameterTypes.joinToString("", "(", ")") { signature(it) } + signature(method.returnType)
    private fun slots(type: Class<*>) = if (type == Long::class.javaPrimitiveType || type == Double::class.javaPrimitiveType) 2 else 1

    fun methods(base: Class<*>, interfaces: List<Class<*>>, handlers: Set<String>): List<Method> {
        require(Modifier.isPublic(base.modifiers) && !Modifier.isFinal(base.modifiers) && !base.isInterface) { "Superclass must be public and non-final" }
        require(interfaces.all { it.isInterface && Modifier.isPublic(it.modifiers) }) { "Interfaces must be public interfaces" }
        val all = LinkedHashMap<String, Method>()
        val inheritedInterfaces = mutableListOf<Class<*>>()
        var type: Class<*>? = base
        while (type != null) {
            for (method in type.declaredMethods) if (Modifier.isPublic(method.modifiers) || Modifier.isProtected(method.modifiers))
                all.putIfAbsent(method.name + signature(method), method)
            inheritedInterfaces.addAll(type.interfaces)
            type = type.superclass
        }
        for (method in (inheritedInterfaces + interfaces).flatMap { it.methods.toList() }) {
            val key=method.name+signature(method);val previous=all[key]
            if(previous==null || previous.declaringClass.isInterface && previous.declaringClass.isAssignableFrom(method.declaringClass))all[key]=method
        }
        val selected = all.values.filter { key(it) in handlers || it.name in handlers || "*" in handlers && Modifier.isAbstract(it.modifiers) }
        if("*" in handlers)require(selected.map(::key).distinct().size==1) { "A function implementation requires one abstract method" }
        for (name in handlers.filter { it!="*" }) require(selected.any { key(it) == name || it.name == name }) { "No overridable method matches $name" }
        require(selected.none { Modifier.isStatic(it.modifiers) || Modifier.isFinal(it.modifiers) }) { "Static/final methods cannot be overridden" }
        val missing = all.values.filter { Modifier.isAbstract(it.modifiers) && it !in selected }
        require(missing.isEmpty()) { "Missing abstract implementations: ${missing.joinToString { key(it) }}" }
        return selected.sortedBy { it.name + signature(it) }
    }

    fun generate(name: String, base: Class<*>, interfaces: List<Class<*>>, methods: List<Method>): ByteArray {
        val own = name.replace('.', '/');val parent = base.name.replace('.', '/')
        val writer = ClassFileWriter(own, parent, "AutoJs6JavaAdapter")
        writer.setFlags((ClassFileWriter.ACC_PUBLIC.toInt() or ClassFileWriter.ACC_SUPER.toInt()).toShort())
        writer.addInterface(RuntimeJavaAdapter::class.java.name.replace('.', '/'))
        interfaces.forEach { writer.addInterface(it.name.replace('.', '/')) }
        writer.addField(FIELD, "L$HANDLER;", ClassFileWriter.ACC_PRIVATE)
        val constructors = base.declaredConstructors.filter { Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers) }
        require(constructors.isNotEmpty()) { "Superclass has no public/protected constructor" }
        for (constructor in constructors) {
            val args = constructor.parameterTypes.joinToString("") { signature(it) }
            writer.startMethod("<init>", "(L$HANDLER;$args)V", ClassFileWriter.ACC_PUBLIC)
            // JVM permits assigning this class's own fields before super(). This also supports constructor callbacks.
            writer.addLoadThis();writer.addALoad(1);writer.add(ByteCode.PUTFIELD, own, FIELD, "L$HANDLER;")
            writer.addLoadThis();var local = 2
            for (type in constructor.parameterTypes) { load(writer, type, local);local += slots(type) }
            writer.addInvoke(ByteCode.INVOKESPECIAL, parent, "<init>", "($args)V");writer.add(ByteCode.RETURN);writer.stopMethod(local)
        }
        methods.forEachIndexed { index, method ->
            writer.startMethod(method.name, signature(method), ClassFileWriter.ACC_PUBLIC)
            writer.addLoadThis();writer.add(ByteCode.GETFIELD, own, FIELD, "L$HANDLER;")
            writer.addLoadThis();writer.addPush(index);writer.addPush(method.parameterCount);writer.add(ByteCode.ANEWARRAY, "java/lang/Object")
            var local = 1
            method.parameterTypes.forEachIndexed { slot, type ->
                writer.add(ByteCode.DUP);writer.addPush(slot);load(writer, type, local);local += slots(type)
                if (type.isPrimitive) { val sig = signature(type);writer.addInvoke(ByteCode.INVOKESTATIC, "java/lang/${boxes.getValue(sig)}", "valueOf", "($sig)Ljava/lang/${boxes.getValue(sig)};") }
                writer.add(ByteCode.AASTORE)
            }
            writer.addInvoke(ByteCode.INVOKEINTERFACE, HANDLER, "invoke", "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;")
            result(writer, method.returnType);writer.stopMethod(local)
            if (!Modifier.isAbstract(method.modifiers) && !method.declaringClass.isInterface) {
                writer.startMethod(superName(index), signature(method), (ClassFileWriter.ACC_PUBLIC.toInt() or 0x1000).toShort())
                writer.addLoadThis();local = 1
                for (type in method.parameterTypes) { load(writer, type, local);local += slots(type) }
                writer.addInvoke(ByteCode.INVOKESPECIAL, parent, method.name, signature(method));writer.add(returnCode(method.returnType));writer.stopMethod(local)
            }
        }
        return writer.toByteArray()
    }
    private fun load(writer: ClassFileWriter, type: Class<*>, local: Int) = when (signature(type)) {
        "J" -> writer.addLLoad(local);"D" -> writer.addDLoad(local);"F" -> writer.addFLoad(local)
        "I", "Z", "B", "S", "C" -> writer.addILoad(local)
        else -> writer.addALoad(local)
    }
    private fun returnCode(type: Class<*>) = when (signature(type)) {
        "V" -> ByteCode.RETURN;"J" -> ByteCode.LRETURN;"D" -> ByteCode.DRETURN;"F" -> ByteCode.FRETURN
        "I", "Z", "B", "S", "C" -> ByteCode.IRETURN
        else -> ByteCode.ARETURN
    }
    private fun result(writer: ClassFileWriter, type: Class<*>) {
        val sig = signature(type)
        if (sig == "V") writer.add(ByteCode.POP)
        else if (type.isPrimitive) {
            val box = "java/lang/${boxes.getValue(sig)}";writer.add(ByteCode.CHECKCAST, box)
            writer.addInvoke(ByteCode.INVOKEVIRTUAL, box, unbox.getValue(sig), "()$sig")
        } else writer.add(ByteCode.CHECKCAST, type.name.replace('.', '/'))
        writer.add(returnCode(type))
    }
}
