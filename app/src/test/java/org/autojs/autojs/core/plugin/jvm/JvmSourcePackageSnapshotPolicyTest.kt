package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmSourcePackageCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

class JvmSourcePackageSnapshotPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun collectsCanonicalMultiClassTreeAndNormalizesEveryCompilationUnit() {
        val root = temporaryFolder.newFolder("source-package")
        val demo = root.resolve("demo").apply { check(mkdir()) }
        val entry = demo.resolve("Main.java").apply {
            writeBytes("\uFEFFpackage demo; public final class Main { Helper helper; }".toByteArray())
        }
        demo.resolve("Helper.java").writeText("package demo; final class Helper {}")

        val snapshot = JvmSourcePackageSnapshotPolicy.collect(root, entry, OPERATIONS)
        val decoded = JvmSourcePackageCodec.decode(snapshot.archiveBytes)

        assertEquals("Main.java", snapshot.sourceFileName)
        assertEquals("demo.Main", snapshot.entryClassName)
        assertEquals(2, snapshot.sourceFileCount)
        assertEquals(listOf("demo/Helper.java", "demo/Main.java"), decoded.sourcePaths)
        assertArrayEquals(
            "package demo; public final class Main { Helper helper; }".toByteArray(),
            decoded.sourceBytes("demo/Main.java"),
        )
    }

    @Test
    fun rejectsPackagePathThatDisagreesWithDeclaredPackageAndNonJavaEntries() {
        val wrongRoot = temporaryFolder.newFolder("wrong-package")
        val wrongEntry = wrongRoot.resolve("Main.java").apply {
            writeText("package demo; public final class Main {}")
        }
        wrongRoot.resolve("Helper.java").writeText("final class Helper {}")
        assertThrows(IllegalArgumentException::class.java) {
            JvmSourcePackageSnapshotPolicy.collect(wrongRoot, wrongEntry, OPERATIONS)
        }

        val mixedRoot = temporaryFolder.newFolder("mixed-package")
        val mixedEntry = mixedRoot.resolve("Main.java").apply { writeText("public final class Main {}") }
        mixedRoot.resolve("Helper.java").writeText("final class Helper {}")
        mixedRoot.resolve("notes.txt").writeText("must not be silently ignored")
        assertThrows(IllegalArgumentException::class.java) {
            JvmSourcePackageSnapshotPolicy.collect(mixedRoot, mixedEntry, OPERATIONS)
        }
    }

    @Test
    fun rejectsSymbolicLinksWithoutReadingTheirTargetsWhenSupported() {
        val root = temporaryFolder.newFolder("linked-package")
        val entry = root.resolve("Main.java").apply { writeText("public final class Main {}") }
        root.resolve("Helper.java").writeText("final class Helper {}")
        val outside = temporaryFolder.newFile("outside.java").apply { writeText("secret") }
        val link = root.resolve("Linked.java")
        if (runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isFailure) return

        assertThrows(IllegalArgumentException::class.java) {
            JvmSourcePackageSnapshotPolicy.collect(root, entry, OPERATIONS)
        }
        assertEquals("secret", outside.readText())
    }

    @Test
    fun explicitPackageAuthorizationRequiresEntryInsideRoot() {
        val root = temporaryFolder.newFolder("authorization-root")
        root.resolve("Main.java").writeText("public final class Main {}")
        val outside = temporaryFolder.newFile("Outside.java").apply { writeText("public class Main {}") }

        assertThrows(IllegalArgumentException::class.java) {
            JvmSourceExplicitRunner.createAuthorizedSourcePackage(root, outside)
        }
        assertTrue(outside.isFile)
    }

    private companion object {
        val OPERATIONS = object : JvmSourcePackageSnapshotPolicy.Operations {
            override fun typeNoFollow(file: File): JvmSourcePackageSnapshotPolicy.NodeType {
                val attributes = runCatching {
                    Files.readAttributes(
                        file.toPath(),
                        BasicFileAttributes::class.java,
                        LinkOption.NOFOLLOW_LINKS,
                    )
                }.getOrNull() ?: return JvmSourcePackageSnapshotPolicy.NodeType.MISSING
                return when {
                    attributes.isSymbolicLink -> JvmSourcePackageSnapshotPolicy.NodeType.SYMBOLIC_LINK
                    attributes.isDirectory -> JvmSourcePackageSnapshotPolicy.NodeType.DIRECTORY
                    attributes.isRegularFile -> JvmSourcePackageSnapshotPolicy.NodeType.REGULAR_FILE
                    else -> JvmSourcePackageSnapshotPolicy.NodeType.OTHER
                }
            }

            override fun children(directory: File): List<File>? = directory.listFiles()?.toList()

            override fun readRegularFile(file: File, maximumBytes: Long): ByteArray =
                file.readBytes().also { require(it.size.toLong() <= maximumBytes) }
        }
    }
}
