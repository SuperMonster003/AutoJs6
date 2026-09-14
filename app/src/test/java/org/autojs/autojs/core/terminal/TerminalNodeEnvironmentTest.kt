package org.autojs.autojs.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TerminalNodeEnvironmentTest {

    private val paths = TerminalPaths(File("/data/user/0/org.autojs.autojs6/files/terminal"))

    @Test
    fun defaultOptionsPointNpmAtThePrefixWithoutOverridingTheRegistry() {
        val env = TerminalNodeEnvironment.build(paths)
        assertEquals(paths.nodeCliRoot.path, env[TerminalNodeEnvironment.CLI_ROOT_VARIABLE])
        assertEquals(paths.npmCache.path, env["npm_config_cache"])
        assertEquals(paths.prefix.path, env["npm_config_prefix"])
        assertEquals("false", env["npm_config_bin_links"])
        assertEquals("false", env["npm_config_update_notifier"])
        assertEquals(paths.corepackHome.path, env["COREPACK_HOME"])
        assertEquals("0", env["COREPACK_DEFAULT_TO_LATEST"])
        assertFalse(env.containsKey("npm_config_registry"))
        assertFalse(env.containsKey("COREPACK_NPM_REGISTRY"))
        assertFalse(env.containsKey("npm_config_ignore_scripts"))
    }

    @Test
    fun theDefaultRegistryIsNotExportedButOthersAre() {
        val default = TerminalNodeEnvironment.build(paths, TerminalNodeEnvironment.Options(registry = TerminalNodeEnvironment.DEFAULT_REGISTRY))
        assertFalse(default.containsKey("npm_config_registry"))

        val mirror = TerminalNodeEnvironment.build(paths, TerminalNodeEnvironment.Options(registry = TerminalNodeEnvironment.NPMMIRROR_REGISTRY))
        assertEquals(TerminalNodeEnvironment.NPMMIRROR_REGISTRY, mirror["npm_config_registry"])
        assertEquals(TerminalNodeEnvironment.NPMMIRROR_REGISTRY, mirror["COREPACK_NPM_REGISTRY"])
    }

    @Test
    fun ignoreScriptsIsExportedOnlyWhenEnabled() {
        val env = TerminalNodeEnvironment.build(paths, TerminalNodeEnvironment.Options(ignoreScripts = true))
        assertEquals("true", env["npm_config_ignore_scripts"])
    }

    @Test
    fun customRegistriesMustBeHttps() {
        assertNull(TerminalNodeEnvironment.sanitizeRegistry(null))
        assertNull(TerminalNodeEnvironment.sanitizeRegistry(""))
        assertNull(TerminalNodeEnvironment.sanitizeRegistry("http://registry.example.com/"))
        assertNull(TerminalNodeEnvironment.sanitizeRegistry("https://"))
        assertNull(TerminalNodeEnvironment.sanitizeRegistry("https://registry example.com/"))
        assertEquals("https://registry.example.com/", TerminalNodeEnvironment.sanitizeRegistry(" https://registry.example.com "))
        assertEquals("https://mirror.example.com/npm/", TerminalNodeEnvironment.sanitizeRegistry("https://mirror.example.com/npm/"))
    }

    @Test
    fun theSessionEnvironmentKeepsNodeVariablesAndRejectsNothingValid() {
        val extras = TerminalNodeEnvironment.build(paths, TerminalNodeEnvironment.Options(registry = TerminalNodeEnvironment.NPMMIRROR_REGISTRY, ignoreScripts = true))
        val spec = TerminalEnvironment.Spec(
            home = paths.home.path,
            prefix = paths.prefix.path,
            tmpDir = paths.tmp.path,
            profile = paths.profile.path,
            pathPrepend = listOf(paths.bin.path),
            extras = extras,
        )
        val env = TerminalEnvironment.build(spec, inherited = mapOf("PATH" to "/system/bin"))
        assertEquals(paths.nodeCliRoot.path, env[TerminalNodeEnvironment.CLI_ROOT_VARIABLE])
        assertEquals("${paths.bin.path}:/system/bin", env["PATH"])
        assertEquals("true", env["npm_config_ignore_scripts"])
        assertEquals(extras.size + 8, env.size)
    }

}
