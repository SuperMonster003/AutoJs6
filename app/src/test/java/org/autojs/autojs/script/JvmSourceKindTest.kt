package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class JvmSourceKindTest {

    @Test
    fun javaAndKotlinSourcesAreRecognizedCaseInsensitively() {
        assertEquals(JvmSourceKind.JAVA, JvmSourceKind.fromFileName("Main.java"))
        assertEquals(JvmSourceKind.JAVA, JvmSourceKind.fromFileName("src/Main.JAVA"))
        assertEquals(JvmSourceKind.KOTLIN, JvmSourceKind.fromFileName("Main.kt"))
        assertEquals(JvmSourceKind.KOTLIN, JvmSourceKind.fromFileName("src\\Main.Kt"))
        assertEquals(JvmSourceKind.JAVA, JvmSourceKind.fromFileName(".JaVa"))
    }

    @Test
    fun kotlinScriptsAndCompiledArtifactsAreExplicitlyUnsupported() {
        listOf("Main.kts", "Main.KTS", "Main.class", "Main.JAR", "Main.dex", ".CLASS")
            .forEach { fileName ->
                assertNull(fileName, JvmSourceKind.fromFileName(fileName))
                assertTrue(fileName, JvmSourceKind.isUnsupportedJvmFileName(fileName))
                assertTrue(fileName, JvmSourceKind.isJvmSourceOrUnsupportedArtifact(fileName))
            }
    }

    @Test
    fun unrelatedFilesRemainOutsideTheJvmRoute() {
        listOf(null, "", "Main", "Main.js", "Main.auto", "Main.java.txt")
            .forEach { fileName ->
                assertNull(fileName, JvmSourceKind.fromFileName(fileName))
                assertFalse(fileName, JvmSourceKind.isUnsupportedJvmFileName(fileName))
                assertFalse(fileName, JvmSourceKind.isJvmSourceOrUnsupportedArtifact(fileName))
            }
    }

    @Test
    fun recognizedExtensionPreservesTheSpellingFromTheFile() {
        assertEquals("JAVA", JvmSourceKind.recognizedExtension("src/Main.JAVA"))
        assertEquals("Kt", JvmSourceKind.recognizedExtension("src\\Main.Kt"))
        assertEquals("kTs", JvmSourceKind.recognizedExtension("Main.kTs"))
        assertNull(JvmSourceKind.recognizedExtension("Main.js"))
    }

    @Test
    fun jvmNamesCannotFallBackToAJavaScriptContentEntry() {
        listOf("Main.java", "Main.kt", "Main.kts", "Main.class", "Main.jar", "Main.dex")
            .forEach { fileName ->
                assertThrows(fileName, UnsupportedJvmSourceException::class.java) {
                    JvmSourceKind.requireNoJavaScriptFallback(fileName)
                }
            }
        JvmSourceKind.requireNoJavaScriptFallback("main.js")
        JvmSourceKind.requireNoJavaScriptFallback("README.txt")
    }
}
