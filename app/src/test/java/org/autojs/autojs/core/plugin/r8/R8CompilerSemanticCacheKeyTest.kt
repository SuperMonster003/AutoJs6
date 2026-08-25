package org.autojs.autojs.core.plugin.r8

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R8CompilerSemanticCacheKeyTest {
    @Test
    fun sameR8SemanticsProduceTheSameCanonicalKey() {
        val capabilities = R8CompilerHostFixtures.capabilities()
        val first = R8CompilerSemanticCacheKey.sha256(
            R8CompilerHostFixtures.request(capabilities),
            capabilities,
            R8CompilerHostFixtures.identity(),
        )
        val second = R8CompilerSemanticCacheKey.sha256(
            R8CompilerHostFixtures.request(capabilities),
            capabilities,
            R8CompilerHostFixtures.identity(),
        )
        assertEquals(first, second)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun inputMinApiCompilerAndProviderIdentityAllChangeTheKey() {
        val capabilities = R8CompilerHostFixtures.capabilities()
        val baseline = key(capabilities = capabilities)
        assertNotEquals(baseline, key(capabilities, minApi = 25))
        assertNotEquals(baseline, key(capabilities, inputSeed = 9))
        val otherCompiler = R8CompilerHostFixtures.capabilities("8.13.18")
        assertNotEquals(baseline, key(otherCompiler))
        assertNotEquals(baseline, key(capabilities, identity = R8CompilerHostFixtures.identity(updateTime = 9L)))
    }

    @Test
    fun r8CacheDomainAndDirectoryCannotAliasTheDexCache() {
        assertEquals("autojs6:r8-compiler:v1", R8CompilerSemanticCacheKey.CACHE_DOMAIN)
        assertEquals("r8-compiler-cache-v1", R8CompilerSemanticCacheKey.CACHE_DIRECTORY)
        assertNotEquals("dex-compiler-cache-v1", R8CompilerSemanticCacheKey.CACHE_DIRECTORY)
        assertFalseContainsDex(R8CompilerSemanticCacheKey.CACHE_DOMAIN)
    }

    private fun key(
        capabilities: org.autojs.plugin.r8compiler.api.R8CompilerCapabilities,
        minApi: Int = 24,
        inputSeed: Int = 1,
        identity: R8CompilerPinnedIdentity = R8CompilerHostFixtures.identity(),
    ) = R8CompilerSemanticCacheKey.sha256(
        R8CompilerHostFixtures.request(capabilities, minApi, inputSeed),
        capabilities,
        identity,
    )

    private fun assertFalseContainsDex(value: String) {
        assertTrue(!value.contains("dex-compiler", ignoreCase = true))
    }
}
