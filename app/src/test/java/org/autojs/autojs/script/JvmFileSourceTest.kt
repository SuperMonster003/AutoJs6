package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class JvmFileSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sourceKeepsItsActualExtensionPathAndDedicatedEngine() {
        val file = temporaryFolder.newFile("Main.JAVA")
        val source = JvmFileSource(file)

        assertEquals(JvmSourceKind.JAVA, source.sourceKind)
        assertEquals("JAVA", source.extension)
        assertEquals("Main", source.name)
        assertEquals("Main.JAVA", source.fullName)
        assertEquals(file.toString(), source.fullPath)
        assertEquals("jvm-source", source.engineName)
    }

    @Test
    fun overriddenOriginChangesFullPathWithoutChangingTheActualExtension() {
        val source = JvmFileSource(temporaryFolder.newFile("Main.Kt"))
        source.overriddenFullPath = "cache/MaterializedSource"

        assertEquals("Kt", source.extension)
        assertEquals("cache/MaterializedSource", source.fullPath)
        assertEquals("MaterializedSource.Kt", source.fullName)
    }

    @Test
    fun constructorRejectsUnsupportedJvmInputs() {
        assertThrows(UnsupportedJvmSourceException::class.java) {
            JvmFileSource(File("Main.kts"))
        }
    }

    @Test
    fun constructorRejectsMissingFilesAndDirectories() {
        assertThrows(UnsafeJvmSourcePathException::class.java) {
            JvmFileSource(File(temporaryFolder.root, "Missing.java"))
        }
        assertThrows(UnsafeJvmSourcePathException::class.java) {
            JvmFileSource(File(temporaryFolder.root, "Directory.kt").apply { mkdir() })
        }
    }

    @Test
    fun aliasedPathsResolveToTheirCanonicalTarget() {
        // Roadmap v2 M1: aliased paths (e.g. /sdcard symlinks, "child/..") canonicalize instead
        // of failing; the source must still resolve to an existing regular .java/.kt file.
        val canonicalFile = temporaryFolder.newFile("Aliased.java")
        File(canonicalFile.parentFile, "child").mkdir()
        val aliasedFile = File(canonicalFile.parentFile, "child/../${canonicalFile.name}")

        val source = JvmFileSource(aliasedFile)
        assertEquals(canonicalFile.canonicalFile, source.file)
        assertEquals(JvmSourceKind.JAVA, source.sourceKind)
    }
}
