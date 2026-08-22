package org.autojs.autojs.core.plugin.jvm

import org.autojs.autojs.script.JvmSourceKind
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class JvmSourceR1KotlinPolicyTest {
    @Test
    fun packageImportsAndLiteralDecoysProduceTheKotlinProtocolLayout() {
        val layout = JvmSourceR1Policy.inspect(
            JvmSourceKind.KOTLIN,
            (
                "\uFEFFpackage com.example.scripts\n\n" +
                    "import java.util.Locale\n\n" +
                    "/* package fake.outer\n" +
                    "   /* package fake.nested */\n" +
                    "*/\n" +
                    "class Main {\n" +
                    "    val ordinary = \"package fake.string\"\n" +
                    "    val raw = \"\"\"package fake.raw\"\"\"\n" +
                    "    val locale = Locale.ROOT\n" +
                    "}\n"
                ).toByteArray(),
        )

        assertEquals("Main.kt", layout.sourceFileName)
        assertEquals("com.example.scripts.Main", layout.entryClassName)
        assertEquals(JvmSourceLanguage.KOTLIN, JvmSourceR1Policy.language(JvmSourceKind.KOTLIN))
    }

    @Test
    fun defaultPackageAndFlexibleSafeFileNamesAreAccepted() {
        val layout = JvmSourceR1Policy.inspect(
            JvmSourceKind.KOTLIN,
            "class Main".toByteArray(),
        )
        assertEquals("Main", layout.entryClassName)

        listOf("Main.kt", "main.kt", "test-17.kt", "My_Script.KT", "has space.kt").forEach { fileName ->
            JvmSourceR1Policy.requireSupportedFileName(JvmSourceKind.KOTLIN, fileName)
        }
    }

    @Test
    fun nonKotlinOrUnsafeFileNamesAreRejected() {
        listOf("Main.java", ".kt", "Main.kts", "Main.kt.txt", "a/b.kt", "a\\b.kt", "nul\u0000.kt")
            .forEach { fileName ->
                expectRejected {
                    JvmSourceR1Policy.requireSupportedFileName(JvmSourceKind.KOTLIN, fileName)
                }
            }
    }

    @Test
    fun malformedUtf8PackageDeclarationsAndUnterminatedLexemesAreRejected() {
        listOf(
            "package ; class Main".toByteArray(),
            "package one\npackage two\nclass Main".toByteArray(),
            "package café\nclass Main".toByteArray(),
            "/* unterminated".toByteArray(),
            "val text = \"unterminated".toByteArray(),
            byteArrayOf(0xC3.toByte(), 0x28),
            "class Main\u0000".toByteArray(),
        ).forEach { source ->
            expectRejected { JvmSourceR1Policy.inspect(JvmSourceKind.KOTLIN, source) }
        }
    }

    private fun expectRejected(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Unexpectedly accepted Kotlin source input")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
