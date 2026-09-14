package org.autojs.autojs.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TerminalEnvironmentTest {

    private val spec = TerminalEnvironment.Spec(
        home = "/data/user/0/app/files/terminal/home",
        prefix = "/data/user/0/app/files/terminal/usr",
        tmpDir = "/data/user/0/app/files/terminal/usr/tmp",
        profile = "/data/user/0/app/files/terminal/usr/etc/profile",
        pathPrepend = listOf("/data/user/0/app/files/terminal/usr/bin"),
    )

    @Test
    fun dropsLoaderAndNodeOverridesButKeepsOtherInheritedVariables() {
        val env = TerminalEnvironment.build(
            spec,
            mapOf(
                "LD_LIBRARY_PATH" to "/vendor/lib64",
                "LD_PRELOAD" to "libx.so",
                "NODE_OPTIONS" to "--inspect",
                "NODE_PATH" to "/x",
                "ANDROID_DATA" to "/data",
                "PATH" to "/system/bin:/system/xbin",
            ),
        )
        assertNull(env["LD_LIBRARY_PATH"])
        assertNull(env["LD_PRELOAD"])
        assertNull(env["NODE_OPTIONS"])
        assertNull(env["NODE_PATH"])
        assertEquals("/data", env["ANDROID_DATA"])
    }

    @Test
    fun prependsPathDeduplicatesAndFallsBackWhenPathIsMissing() {
        val withPath = TerminalEnvironment.build(spec, mapOf("PATH" to "/system/bin:/data/user/0/app/files/terminal/usr/bin::/system/xbin"))
        assertEquals("/data/user/0/app/files/terminal/usr/bin:/system/bin:/system/xbin", withPath["PATH"])

        val withoutPath = TerminalEnvironment.build(spec, emptyMap())
        assertEquals("/data/user/0/app/files/terminal/usr/bin:/system/bin:/system/xbin", withoutPath["PATH"])
    }

    @Test
    fun laysTerminalVariablesOverInheritedOnes() {
        val env = TerminalEnvironment.build(spec, mapOf("HOME" to "/", "TERM" to "dumb", "SHELL" to "/bin/bash"))
        assertEquals(spec.home, env["HOME"])
        assertEquals(spec.prefix, env["PREFIX"])
        assertEquals(spec.tmpDir, env["TMPDIR"])
        assertEquals(spec.profile, env["ENV"])
        assertEquals(TerminalEnvironment.DEFAULT_TERM, env["TERM"])
        assertEquals(TerminalEnvironment.DEFAULT_LANG, env["LANG"])
        assertEquals(TerminalEnvironment.DEFAULT_SHELL, env["SHELL"])
    }

    @Test
    fun extrasOverrideAndNullRemoves() {
        val env = TerminalEnvironment.build(
            spec.copy(extras = mapOf("npm_config_registry" to "https://registry.npmjs.org/", "ANDROID_DATA" to null)),
            mapOf("ANDROID_DATA" to "/data"),
        )
        assertEquals("https://registry.npmjs.org/", env["npm_config_registry"])
        assertFalse(env.containsKey("ANDROID_DATA"))
    }

    @Test
    fun skipsInheritedEntriesThatCouldNotBeRenderedSafely() {
        // Mirrors the BOOTCLASSPATH-without-'=' and leading-space PATH bugs seen in other terminals.
        // zh-CN: 对应其他终端中出现过的 BOOTCLASSPATH 缺 '=' 与 PATH 前导空格问题.
        val env = TerminalEnvironment.build(
            spec,
            mapOf(
                " PATH" to "/leading/space",
                "BAD=NAME" to "x",
                "WITH_NUL" to "a" + Char(0) + "b",
                "1STARTS_WITH_DIGIT" to "x",
                "GOOD_NAME" to "ok",
            ),
        )
        assertFalse(env.containsKey(" PATH"))
        assertFalse(env.containsKey("BAD=NAME"))
        assertFalse(env.containsKey("WITH_NUL"))
        assertFalse(env.containsKey("1STARTS_WITH_DIGIT"))
        assertEquals("ok", env["GOOD_NAME"])
    }

    @Test
    fun toEnvpRendersKeyValuePairsInOrder() {
        val envp = TerminalEnvironment.toEnvp(linkedMapOf("A" to "1", "B" to "x=y"))
        assertEquals(listOf("A=1", "B=x=y"), envp.toList())
    }

    @Test
    fun toEnvpRejectsInvalidNamesAndNulValues() {
        try {
            TerminalEnvironment.toEnvp(mapOf("BAD NAME" to "1"))
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        try {
            TerminalEnvironment.toEnvp(mapOf("A" to "x" + Char(0)))
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertTrue(TerminalEnvironment.isValidName("_x9"))
        assertFalse(TerminalEnvironment.isValidName(""))
    }

}
