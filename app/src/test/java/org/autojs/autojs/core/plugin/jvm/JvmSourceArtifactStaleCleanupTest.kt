package org.autojs.autojs.core.plugin.jvm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

class JvmSourceArtifactStaleCleanupTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cleanupDeletesOnlyExactOrdinaryRootsAndNeverFollowsNestedSymlinks() {
        val privateParent = temporaryFolder.newFolder("private-parent")
        val base = File(privateParent, "jvm-source-host").apply { check(mkdir()) }
        val staleRoot = File(base, MANAGED_ROOT).apply { check(mkdir()) }
        File(staleRoot, "source.java").writeText("class Main {}")
        File(staleRoot, "nested").apply {
            check(mkdir())
            File(this, "classes.dex").writeBytes(byteArrayOf(1, 2, 3))
        }
        val malformed = File(base, "host-not-a-uuid").apply { check(mkdir()) }
        val managedRegularFile = File(base, OTHER_MANAGED_ROOT).apply { writeText("not a directory") }
        val external = temporaryFolder.newFolder("external-nested-target")
        val sentinel = File(external, "keep.txt").apply { writeText("keep") }
        val nestedLink = File(staleRoot, "outside-link")
        val nestedLinkCreated = tryCreateSymbolicLink(nestedLink, external)

        val canonicalBase = JvmSourceArtifactStaleCleanup.requirePrivateBase(
            privateParent,
            base,
            NIO_OPERATIONS,
        )
        val report = JvmSourceArtifactStaleCleanup.cleanup(canonicalBase, NIO_OPERATIONS) { true }

        assertEquals(1, report.eligibleRoots)
        assertEquals(1, report.deletedRoots)
        assertFalse(staleRoot.exists())
        assertTrue(malformed.isDirectory)
        assertTrue(managedRegularFile.isFile)
        assertTrue("Nested symlink target was traversed or deleted", sentinel.isFile)
        if (nestedLinkCreated) {
            assertFalse("Nested symlink itself was not unlinked", Files.exists(nestedLink.toPath(), LinkOption.NOFOLLOW_LINKS))
        }
    }

    @Test
    fun baseAndCandidateRootSymlinksFailClosedWhenThePlatformSupportsThem() {
        val privateParent = temporaryFolder.newFolder("symlink-private-parent")
        val externalBase = temporaryFolder.newFolder("external-base")
        val baseLink = File(privateParent, "jvm-source-host")
        if (tryCreateSymbolicLink(baseLink, externalBase)) {
            assertThrows(IllegalArgumentException::class.java) {
                JvmSourceArtifactStaleCleanup.requirePrivateBase(
                    privateParent,
                    baseLink,
                    NIO_OPERATIONS,
                )
            }
        }

        val ordinaryBase = File(privateParent, "ordinary-jvm-source-host").apply { check(mkdir()) }
        val externalRoot = temporaryFolder.newFolder("external-root")
        val sentinel = File(externalRoot, "keep.txt").apply { writeText("keep") }
        val rootLink = File(ordinaryBase, MANAGED_ROOT)
        if (tryCreateSymbolicLink(rootLink, externalRoot)) {
            val canonicalBase = JvmSourceArtifactStaleCleanup.requirePrivateBase(
                privateParent,
                ordinaryBase,
                NIO_OPERATIONS,
            )
            val report = JvmSourceArtifactStaleCleanup.cleanup(canonicalBase, NIO_OPERATIONS) { true }

            assertEquals(0, report.eligibleRoots)
            assertEquals(0, report.deletedRoots)
            assertTrue(Files.exists(rootLink.toPath(), LinkOption.NOFOLLOW_LINKS))
            assertTrue("Candidate root symlink target was traversed or deleted", sentinel.isFile)
        }
    }

    @Test
    fun managedRootNameIsCanonicalAndCaseSensitive() {
        assertTrue(JvmSourceArtifactStaleCleanup.isManagedRootName(MANAGED_ROOT))
        assertFalse(JvmSourceArtifactStaleCleanup.isManagedRootName(MANAGED_ROOT.uppercase()))
        assertFalse(JvmSourceArtifactStaleCleanup.isManagedRootName("prefix-$MANAGED_ROOT"))
        assertFalse(JvmSourceArtifactStaleCleanup.isManagedRootName("host-11111111-2222-3333-4444-55555555555"))
    }

    @Test
    fun activeRootIsRetainedWhenItsLeaseCannotBeClaimed() {
        val privateParent = temporaryFolder.newFolder("active-private-parent")
        val base = File(privateParent, "jvm-source-host").apply { check(mkdir()) }
        val activeRoot = File(base, MANAGED_ROOT).apply { check(mkdir()) }
        File(activeRoot, ".owner.lock").writeText("")
        val canonicalBase = JvmSourceArtifactStaleCleanup.requirePrivateBase(
            privateParent,
            base,
            NIO_OPERATIONS,
        )

        val report = JvmSourceArtifactStaleCleanup.cleanup(canonicalBase, NIO_OPERATIONS) { false }

        assertEquals(1, report.eligibleRoots)
        assertEquals(0, report.deletedRoots)
        assertTrue(activeRoot.isDirectory)
    }

    private fun tryCreateSymbolicLink(link: File, target: File): Boolean = runCatching {
        Files.createSymbolicLink(link.toPath(), target.toPath())
    }.isSuccess

    private companion object {
        const val MANAGED_ROOT = "host-11111111-2222-3333-4444-555555555555"
        const val OTHER_MANAGED_ROOT = "host-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

        val NIO_OPERATIONS = object : JvmSourceArtifactStaleCleanup.Operations {
            override fun typeNoFollow(file: File): JvmSourceArtifactStaleCleanup.NodeType {
                val path = file.toPath()
                val attributes = runCatching {
                    Files.readAttributes(
                        path,
                        BasicFileAttributes::class.java,
                        LinkOption.NOFOLLOW_LINKS,
                    )
                }.getOrNull() ?: return JvmSourceArtifactStaleCleanup.NodeType.MISSING
                return when {
                    attributes.isSymbolicLink -> JvmSourceArtifactStaleCleanup.NodeType.SYMBOLIC_LINK
                    attributes.isDirectory -> JvmSourceArtifactStaleCleanup.NodeType.DIRECTORY
                    attributes.isRegularFile -> JvmSourceArtifactStaleCleanup.NodeType.REGULAR_FILE
                    else -> JvmSourceArtifactStaleCleanup.NodeType.OTHER
                }
            }

            override fun canonicalFile(file: File): File? = runCatching {
                file.toPath().toRealPath().toFile()
            }.getOrNull()

            override fun children(directory: File): List<File>? = runCatching {
                Files.newDirectoryStream(directory.toPath()).use { stream ->
                    stream.map { it.toFile() }.toList()
                }
            }.getOrNull()

            override fun unlink(file: File): Boolean = runCatching {
                Files.deleteIfExists(file.toPath())
            }.getOrDefault(false)

            override fun deleteDirectory(directory: File): Boolean = runCatching {
                Files.deleteIfExists(directory.toPath())
            }.getOrDefault(false)
        }
    }
}
