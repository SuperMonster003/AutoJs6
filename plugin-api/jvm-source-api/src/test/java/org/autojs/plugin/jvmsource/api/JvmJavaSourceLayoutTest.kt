package org.autojs.plugin.jvmsource.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JvmJavaSourceLayoutTest {
    @Test
    fun derivesQualifiedEntryFromCommentSeparatedPackageAndAllowsImports() {
        val source = """
            ﻿/* license */ package com /* separator */ . example.scripts;
            import java.util.List;
            import static java.util.Collections.emptyList;
            public final class Main { List<?> values = emptyList(); }
        """.trimIndent()

        val inspection = JvmJavaSourceLayoutPolicy.inspect(source.toByteArray())

        assertEquals("Main.java", inspection.layout.sourceFileName)
        assertEquals("com.example.scripts.Main", inspection.layout.entryClassName)
        assertEquals(source.removePrefix("\uFEFF"), inspection.normalizedSource)
    }

    @Test
    fun ignoresPackageWordsInsideCommentsAndLiterals() {
        val source = """
            /* package hidden; */
            import java.util.List;
            public final class Main { String value = "package hidden;"; }
        """.trimIndent()

        assertEquals("Main", JvmJavaSourceLayoutPolicy.inspect(source.toByteArray()).layout.entryClassName)
    }

    @Test
    fun rejectsAmbiguousOrCompilerPretranslatedPackageShapes() {
        val invalid = listOf(
            "package ; public final class Main {}".toByteArray(),
            "package first; package second; public final class Main {}".toByteArray(),
            "import java.util.List; package late; public final class Main {}".toByteArray(),
            "package café; public final class Main {}".toByteArray(),
            "\\u0070ackage hidden; public final class Main {}".toByteArray(),
            "public final class Main {}\u0000".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28),
        )

        invalid.forEach { source ->
            assertThrows(IllegalArgumentException::class.java) {
                JvmJavaSourceLayoutPolicy.inspect(source)
            }
        }
    }
}
