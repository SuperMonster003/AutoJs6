package org.autojs.autojs.core.plugin.r8

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class R8CompilerHostBoundaryTest {
    @Test
    fun frozenAarAndGradleWiringRemainExact() {
        val root = hostRoot()
        val aar = root.resolve(
            "libs/r8-compiler-api-0_1_0/r8-compiler-api-0.1.0.aar",
        )
        val protocolSource = root.resolve(
            "plugin-api/protocol-wire-api/src/main/java/org/autojs/plugin/protocol/wire/TaggedWire.kt",
        )
        assertTrue(aar.isFile)
        assertTrue(protocolSource.isFile)
        assertEquals(FROZEN_R8_API_SHA256, sha256(aar))
        assertEquals(FROZEN_PROTOCOL_SOURCE_SHA256, sha256(protocolSource))

        val appBuild = root.resolve("app/build.gradle.kts").readText()
        val settings = root.resolve("settings.gradle.kts").readText()
        val wrapperBuild = root.resolve("libs/r8-compiler-api-0_1_0/build.gradle.kts").readText()
        assertTrue(appBuild.contains("implementation(project(\":plugin-api:protocol-wire-api\"))"))
        assertTrue(appBuild.contains("implementation(project(\":libs:r8-compiler-api-0_1_0\"))"))
        assertTrue(settings.contains("\"r8-compiler-api-0_1_0\""))
        assertTrue(wrapperBuild.contains("files += \"r8-compiler-api-0.1.0.aar\""))
        assertFalse(appBuild.contains("AutoJs6-Plugin-R8-Compiler"))
        assertFalse(settings.contains("AutoJs6-Plugin-R8-Compiler"))
    }

    @Test
    fun r8HostSourcesDoNotReachIntoDexTransportOrPreferenceNamespace() {
        val sources = hostRoot()
            .resolve("app/src/main/java/org/autojs/autojs/core/plugin/r8")
            .walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .toList()
        assertTrue(sources.isNotEmpty())
        val combined = sources.joinToString("\n") { it.readText() }

        assertFalse(combined.contains("import org.autojs.autojs.core.plugin.dex"))
        assertFalse(combined.contains("org.autojs.plugin.dexcompiler"))
        assertFalse(combined.contains("dex_compiler_experimental"))
        assertTrue(combined.contains(R8CompilerSemanticCacheKey.CACHE_DOMAIN))
        assertTrue(combined.contains(R8CompilerSemanticCacheKey.CACHE_DIRECTORY))
    }

    @Test
    fun callbackCallerMustMatchPinnedPackageUidExactly() {
        assertTrue(R8CompilerTransportCallerPolicy.isExpectedUid(10_042, 10_042))
        assertFalse(R8CompilerTransportCallerPolicy.isExpectedUid(10_042, 10_043))
        assertFalse(R8CompilerTransportCallerPolicy.isExpectedUid(-1, -1))
    }

    private fun hostRoot(): File {
        val workingDirectory = checkNotNull(System.getProperty("user.dir"))
        val root = generateSequence(File(workingDirectory).absoluteFile) {
            it.parentFile
        }.firstOrNull {
            it.resolve("settings.gradle.kts").isFile && it.resolve("app").isDirectory
        }
        assertNotNull("Unable to locate the AutoJs6 root", root)
        return checkNotNull(root)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        private const val FROZEN_R8_API_SHA256 =
            "e9df49b7e49992615a15bc0af2372a4525f02b4a2a915a560ddab3128bb2f066"
        private const val FROZEN_PROTOCOL_SOURCE_SHA256 =
            "e0155f9f41280246d408cb062f7185a8edb4679ae748a3d8cb6f403ef93e2a5d"
    }
}
