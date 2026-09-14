package org.autojs.autojs.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSessionLauncherTest {

    @Test
    fun commandExecsTheShellInsideTheRequestedDirectory() {
        val argv = TerminalSessionLauncher.buildCommand("/sdcard/Scripts/my project")
        assertEquals(5, argv.size)
        assertEquals("/system/bin/sh", argv[0])
        assertEquals("-c", argv[1])
        assertEquals(TerminalSessionLauncher.WRAPPER_SCRIPT, argv[2])
        // $0 names the shell to exec, $1 is the directory; the path is passed as data, never interpolated.
        // zh-CN: $0 为待 exec 的 shell, $1 为目录; 路径作为数据传入, 不做插值.
        assertEquals("/system/bin/sh", argv[3])
        assertEquals("/sdcard/Scripts/my project", argv[4])
    }

    @Test
    fun wrapperFallsBackToHomeAndExecsArgvZero() {
        val script = TerminalSessionLauncher.WRAPPER_SCRIPT
        assertTrue(script.startsWith("cd -- \"\$1\""))
        assertTrue(script.contains("|| cd \"\$HOME\""))
        assertTrue(script.endsWith("exec \"\$0\""))
    }

    @Test
    fun customShellIsUsedForBothExecutableAndArgvZero() {
        val argv = TerminalSessionLauncher.buildCommand("/tmp", shell = "/system/bin/mksh")
        assertEquals("/system/bin/mksh", argv[0])
        assertEquals("/system/bin/mksh", argv[3])
    }

}
