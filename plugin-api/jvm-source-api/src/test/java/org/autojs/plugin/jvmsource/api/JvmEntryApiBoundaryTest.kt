package org.autojs.plugin.jvmsource.api

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmEntryApiBoundaryTest {

    @Test
    fun entryApiUsesOnlyJavaAndItsOwnTypes() {
        val apiTypes = listOf(
            AutoJsJvmEntry::class.java,
            JvmScriptContext::class.java,
            JvmAppApi::class.java,
            JvmClipboardApi::class.java,
            JvmConsoleApi::class.java,
            JvmCancellation::class.java,
            JvmCancellationException::class.java,
        )

        apiTypes.forEach { type ->
            assertTrue("${type.name} must be public", Modifier.isPublic(type.modifiers))
            val exposedTypes = buildList {
                type.declaredFields.forEach { add(it.type) }
                type.declaredConstructors.forEach { constructor ->
                    addAll(constructor.parameterTypes)
                    addAll(constructor.exceptionTypes)
                }
                type.declaredMethods.forEach { method ->
                    add(method.returnType)
                    addAll(method.parameterTypes)
                    addAll(method.exceptionTypes)
                }
            }
            exposedTypes.forEach { exposed ->
                val name = exposed.componentType?.name ?: exposed.name
                assertFalse("Android type leaked through ${type.name}: $name", name.startsWith("android."))
                assertFalse("Host type leaked through ${type.name}: $name", name.startsWith("org.autojs.autojs."))
            }
        }
    }

    @Test
    fun entrySignatureIsJavaFriendly() {
        val run = AutoJsJvmEntry::class.java.getMethod("run", JvmScriptContext::class.java)
        assertEquals(Any::class.java, run.returnType)
        assertEquals(arrayOf(Exception::class.java).toList(), run.exceptionTypes.toList())

        assertEquals(
            java.lang.Boolean.TYPE,
            JvmAppApi::class.java.getMethod("launch", String::class.java).returnType,
        )
        assertEquals(
            JvmAppApi::class.java,
            JvmScriptContext::class.java.getMethod("app").returnType,
        )
        assertEquals(
            JvmCancellation::class.java,
            JvmScriptContext::class.java.getMethod("cancellation").returnType,
        )
        assertEquals(
            JvmClipboardApi::class.java,
            JvmScriptContext::class.java.getMethod("clipboard").returnType,
        )
        assertEquals(
            String::class.java,
            JvmClipboardApi::class.java.getMethod("getText").returnType,
        )
        assertEquals(
            Void.TYPE,
            JvmClipboardApi::class.java.getMethod("setText", String::class.java).returnType,
        )
        assertEquals(
            JvmConsoleApi::class.java,
            JvmScriptContext::class.java.getMethod("console").returnType,
        )
        assertEquals(
            Void.TYPE,
            JvmScriptContext::class.java.getMethod("sleep", java.lang.Long.TYPE).returnType,
        )
        assertEquals(
            Void.TYPE,
            JvmScriptContext::class.java.getMethod("toast", String::class.java).returnType,
        )
    }
}
