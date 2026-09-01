package org.autojs.autojs.apkbuilder.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files

class RemoteBunPackagingEntryPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun canonicalEntryInsideProjectRootRemainsEligibleForPreflight() {
        val projectRoot = temporaryFolder.newFolder("project")
        val entry = File(projectRoot, "src/main.bun.js").apply {
            checkNotNull(parentFile).mkdirs()
            writeText("console.log('bun')")
        }

        assertEquals(
            entry.canonicalFile,
            RemoteApkBuildRequestPreparer.resolveProjectEntry(projectRoot, "src/../src/main.bun.js"),
        )
    }

    @Test
    fun singleFileEntryUsesItsCanonicalIdentityForBunPreflight() {
        val canonicalTarget = temporaryFolder.newFile("actual.bun.js").canonicalFile
        val aliasedSource = object : File(temporaryFolder.root, "main.js") {
            override fun isDirectory(): Boolean = false

            override fun getCanonicalFile(): File = canonicalTarget
        }

        assertEquals(
            canonicalTarget,
            RemoteApkBuildRequestPreparer.resolveProjectEntry(aliasedSource, "ignored.js"),
        )
    }

    @Test
    fun parentTraversalOutsideProjectRootFailsClosed() {
        val projectRoot = temporaryFolder.newFolder("project")
        temporaryFolder.newFile("outside.bun.js").writeText("\"bun\";")

        assertThrows(IOException::class.java) {
            RemoteApkBuildRequestPreparer.resolveProjectEntry(projectRoot, "../outside.bun.js")
        }
    }

    @Test
    fun symlinkedEntryOutsideProjectRootFailsClosed() {
        val projectRoot = temporaryFolder.newFolder("project")
        val outside = temporaryFolder.newFile("outside-target.bun.js").apply { writeText("\"bun\";") }
        val link = File(projectRoot, "main.js").toPath()
        try {
            Files.createSymbolicLink(link, outside.toPath())
        } catch (error: UnsupportedOperationException) {
            assumeNoException(error)
        } catch (error: IOException) {
            assumeNoException(error)
        } catch (error: SecurityException) {
            assumeNoException(error)
        }

        assertThrows(IOException::class.java) {
            RemoteApkBuildRequestPreparer.resolveProjectEntry(projectRoot, "main.js")
        }
    }
}
