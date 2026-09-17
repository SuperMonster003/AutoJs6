package org.autojs.autojs.core.plugin.jvm

import org.junit.Assert.assertEquals
import org.junit.Test

class JvmSourceR1JavaPolicyTest {
    @Test
    fun defaultPackageMainAndDeclarationWordsInLiteralsAreAccepted() {
        JvmSourceR1JavaPolicy.requireSupportedFileName("Main.java")
        assertEquals("Main", JvmSourceR1JavaPolicy.inspect(
            "public final class Main { String value = \"import fake.Name;\"; }".toByteArray(),
        ).entryClassName)
        assertEquals("Main.java", JvmSourceR1JavaPolicy.inspect(
            "/* package fake; */ public final class Main {}".toByteArray(),
        ).sourceFileName)
    }

    @Test
    fun packageAndImportsProduceDynamicProtocolLayout() {
        val layout = JvmSourceR1JavaPolicy.inspect(
            """
                package com.example.scripts;
                import java.util.List;
                import static java.util.Collections.emptyList;
                public final class Main { List<?> value = emptyList(); }
            """.trimIndent().toByteArray(),
        )

        assertEquals("Main.java", layout.sourceFileName)
        assertEquals("com.example.scripts.Main", layout.entryClassName)
    }

    @Test
    fun callerSelectedEntryProducesDynamicProtocolLayout() {
        val layout = JvmSourceR1JavaPolicy.inspect(
            "package com.example.scripts; public final class ScriptEntry {}".toByteArray(),
            "ScriptEntry",
        )

        assertEquals("ScriptEntry.java", layout.sourceFileName)
        assertEquals("com.example.scripts.ScriptEntry", layout.entryClassName)
    }

    @Test
    fun anyWellFormedJavaFileNameIsAccepted() {
        listOf("Main.java", "main.java", "test-17.java", "My_Script.JAVA", "has space.java").forEach { fileName ->
            JvmSourceR1JavaPolicy.requireSupportedFileName(fileName)
        }
    }

    @Test
    fun nonJavaOrUnsafeFileNamesAreRejected() {
        listOf("Main.kt", ".java", "Main.java.txt", "a/b.java", "a\\b.java", "nul\u0000.java").forEach { fileName ->
            try {
                JvmSourceR1JavaPolicy.requireSupportedFileName(fileName)
                throw AssertionError("Unexpectedly accepted: $fileName")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun jlsUnicodeEscapeCannotHideAnImportToken() {
        JvmSourceR1JavaPolicy.inspect(
            "\\u0069mport java.util.List; public final class Main {}".toByteArray(),
        )
    }

    @Test
    fun byteOrderMarkIsHandledConsistentlyWithProvider() {
        assertEquals("Main", JvmSourceR1JavaPolicy.inspect(
            "\uFEFFpublic final class Main {}".toByteArray(),
        ).entryClassName)
    }

    @Test
    fun malformedMultipleOrLatePackageDeclarationsAreRejected() {
        listOf(
            "package ; public final class Main {}",
            "package one; package two; public final class Main {}",
            "import java.util.List; package late; public final class Main {}",
            "package café; public final class Main {}",
        ).forEach { source ->
            try {
                JvmSourceR1JavaPolicy.inspect(source.toByteArray())
                throw AssertionError("Unexpectedly accepted: $source")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }
}
