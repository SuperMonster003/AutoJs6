package org.autojs.autojs.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TerminalPathsTest {

    @Test
    fun layoutFollowsTheDocumentedTree() {
        val root = File("/data/user/0/app/files/terminal")
        val paths = TerminalPaths(root)
        assertEquals(File(root, "home"), paths.home)
        assertEquals(File(root, "usr"), paths.prefix)
        assertEquals(File(root, "usr/bin"), paths.bin)
        assertEquals(File(root, "usr/etc/profile"), paths.profile)
        assertEquals(File(root, "usr/tmp"), paths.tmp)
        assertEquals(File(root, "usr/lib/autojs6-node-cli"), paths.nodeCliRoot)
        assertEquals(File(root, "usr/lib/node_modules"), paths.globalNodeModules)
        assertEquals(File(root, "usr/.npm"), paths.npmCache)
        assertEquals(File(root, "usr/.corepack"), paths.corepackHome)
    }

    @Test
    fun ensureLayoutCreatesDirectoriesAndWritesTheProfileOnce() {
        val root = Files.createTempDirectory("terminal").toFile()
        try {
            val paths = TerminalPaths(root).ensureLayout()
            listOf(paths.home, paths.bin, paths.etc, paths.tmp, paths.lib).forEach { assertTrue(it.path, it.isDirectory) }
            assertEquals(TerminalPaths.DEFAULT_PROFILE, paths.profile.readText())
            assertTrue(paths.profile.readText().contains("PS1="))
            assertTrue(paths.profile.readText().contains("HISTFILE="))

            paths.profile.writeText("# user edit\n")
            TerminalPaths(root).ensureLayout()
            assertEquals("# user edit\n", paths.profile.readText())

            assertTrue(paths.clearAll())
            assertFalse(root.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun upgradesTheManagedPromptWithoutOverwritingCustomProfiles() {
        val root = Files.createTempDirectory("terminal-profile-migration").toFile()
        try {
            val paths = TerminalPaths(root).ensureLayout()
            paths.profile.writeText(TerminalPaths.LEGACY_DEFAULT_PROFILE)
            paths.ensureLayout()
            assertEquals(TerminalPaths.DEFAULT_PROFILE, paths.profile.readText())
            assertTrue(paths.profile.readText().contains("; esac)\n"))
            val custom = TerminalPaths.LEGACY_DEFAULT_PROFILE + "\n# custom setting\n"
            paths.profile.writeText(custom)
            paths.ensureLayout()
            assertEquals(custom, paths.profile.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun tildePathCollapsesOnlyTheHomePrefix() {
        val home = "/data/user/0/app/files/terminal/home"
        assertEquals("~", TerminalPaths.toTildePath(home, home))
        assertEquals("~/projects/demo", TerminalPaths.toTildePath("$home/projects/demo", home))
        assertEquals("/data/user/0/app/files/terminal/homework", TerminalPaths.toTildePath("/data/user/0/app/files/terminal/homework", home))
        assertEquals("/sdcard/Scripts", TerminalPaths.toTildePath("/sdcard/Scripts", home))
    }

}
