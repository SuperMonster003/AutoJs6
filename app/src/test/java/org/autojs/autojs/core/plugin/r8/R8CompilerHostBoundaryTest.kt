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
            "libs/r8-compiler-api-0_2_0/r8-compiler-api-0.2.0.aar",
        )
        val protocolSource = root.resolve(
            "plugin-api/protocol-wire-api/src/main/java/org/autojs/plugin/protocol/wire/TaggedWire.kt",
        )
        assertTrue(aar.isFile)
        assertTrue(protocolSource.isFile)
        assertEquals(FROZEN_R8_API_SHA256, sha256(aar))
        assertEquals(FROZEN_PROTOCOL_SOURCE_SHA256, normalizedLfSha256(protocolSource))

        val appBuild = root.resolve("app/build.gradle.kts").readText()
        val settings = root.resolve("settings.gradle.kts").readText()
        val wrapperBuild = root.resolve("libs/r8-compiler-api-0_2_0/build.gradle.kts").readText()
        assertTrue(appBuild.contains("implementation(project(\":plugin-api:protocol-wire-api\"))"))
        assertTrue(appBuild.contains("implementation(project(\":libs:r8-compiler-api-0_2_0\"))"))
        assertFalse(appBuild.contains("implementation(project(\":libs:r8-compiler-api-0_1_0\"))"))
        assertTrue(settings.contains("\"r8-compiler-api-0_2_0\""))
        assertTrue(wrapperBuild.contains("files += \"r8-compiler-api-0.2.0.aar\""))
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

    private fun normalizedLfSha256(file: File): String {
        val normalized = file.readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        private const val FROZEN_R8_API_SHA256 =
            "ea1416913db1a93328c2fc8017f36a790e9e2ca04b8ad1c234d763da2d367424"
        private const val FROZEN_PROTOCOL_SOURCE_SHA256 =
            "5d5c672ef0c7907b1a0cf6fd041ff85dc7c864aa07628316db5b9a9abb9e87c8"
    }
}
