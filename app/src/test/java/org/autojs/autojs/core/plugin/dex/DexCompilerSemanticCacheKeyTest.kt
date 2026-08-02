package org.autojs.autojs.core.plugin.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DexCompilerSemanticCacheKeyTest {

    @Test
    fun optionInsertionOrderDoesNotChangeKey() {
        val first = spec(options = linkedMapOf("desugar" to "enabled", "intermediate" to "false"))
        val second = spec(options = linkedMapOf("intermediate" to "false", "desugar" to "enabled"))

        assertEquals(DexCompilerSemanticCacheKey.sha256(first), DexCompilerSemanticCacheKey.sha256(second))
        assertEquals(
            DexCompilerSemanticCacheKey.canonicalBytes(first).toList(),
            DexCompilerSemanticCacheKey.canonicalBytes(second).toList(),
        )
    }

    @Test
    fun everyRequiredSemanticAndProvenanceFieldChangesKey() {
        val baseline = spec()
        val baselineKey = DexCompilerSemanticCacheKey.sha256(baseline)
        val variants = listOf(
            spec(inputSha = "11".repeat(32)),
            spec(policy = 2),
            spec(protocolMajor = 2),
            spec(protocolMinor = 1),
            spec(compilerFamily = "d8-next"),
            spec(compilerVersion = "8.13.20"),
            spec(minApi = 35),
            spec(mode = DexCompilationMode.RELEASE),
            spec(outputMode = DexOutputMode.DEX_INDEXED_ZIP, options = mapOf("output-layout" to "alternate")),
            spec(libraryFingerprint = "22".repeat(32)),
            spec(desugaredIdentity = "desugar-v2"),
            spec(options = mapOf("desugar" to "disabled")),
            spec(provenance = provenance(packageName = "other.plugin")),
            spec(provenance = provenance(componentClassName = "OtherService")),
            spec(provenance = provenance(providerId = "other-d8")),
            spec(provenance = provenance(packageUid = 10_001)),
            spec(provenance = provenance(signers = listOf("33".repeat(32)))),
            spec(provenance = provenance(signers = listOf("bb".repeat(32), "33".repeat(32)))),
            spec(provenance = provenance(versionCode = 8)),
            spec(provenance = provenance(lastUpdateTime = 456)),
        )

        variants.forEach { variant ->
            assertNotEquals(baselineKey, DexCompilerSemanticCacheKey.sha256(variant))
        }
    }

    @Test
    fun callerMutationCannotChangeCapturedOptions() {
        val options = linkedMapOf("desugar" to "enabled")
        val captured = spec(options = options)
        val before = DexCompilerSemanticCacheKey.sha256(captured)

        options["desugar"] = "disabled"
        options["new-option"] = "new-value"

        assertEquals(before, DexCompilerSemanticCacheKey.sha256(captured))
    }

    @Test
    fun signerEnumerationOrderDoesNotChangeKey() {
        val first = spec(provenance = provenance(signers = listOf("11".repeat(32), "22".repeat(32))))
        val second = spec(provenance = provenance(signers = listOf("22".repeat(32), "11".repeat(32))))

        assertEquals(DexCompilerSemanticCacheKey.sha256(first), DexCompilerSemanticCacheKey.sha256(second))
    }

    @Test
    fun lengthPrefixingPreventsDelimiterAmbiguity() {
        val first = spec(options = mapOf("a" to "b=c", "d" to "e"))
        val second = spec(options = mapOf("a" to "b", "c=d" to "e"))

        assertNotEquals(DexCompilerSemanticCacheKey.sha256(first), DexCompilerSemanticCacheKey.sha256(second))
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedSha256IsRejected() {
        spec(inputSha = "not-a-sha")
    }

    private fun spec(
        inputSha: String = "00".repeat(32),
        policy: Int = 1,
        protocolMajor: Int = 1,
        protocolMinor: Int = 0,
        compilerFamily: String = "d8",
        compilerVersion: String = "8.13.19",
        minApi: Int = 36,
        mode: DexCompilationMode = DexCompilationMode.DEBUG,
        outputMode: DexOutputMode = DexOutputMode.DEX_INDEXED_ZIP,
        libraryFingerprint: String = "aa".repeat(32),
        desugaredIdentity: String? = null,
        options: Map<String, String> = mapOf("desugar" to "enabled"),
        provenance: DexCompilerProviderProvenance = provenance(),
    ) = DexCompilerCacheSpec(
        inputSha256 = inputSha,
        canonicalizationPolicyVersion = policy,
        protocolMajor = protocolMajor,
        protocolMinor = protocolMinor,
        compilerFamily = compilerFamily,
        compilerVersion = compilerVersion,
        minimumApi = minApi,
        compilationMode = mode,
        outputMode = outputMode,
        runtimeLibraryFingerprint = libraryFingerprint,
        desugaredLibraryConfigurationIdentity = desugaredIdentity,
        semanticOptions = options,
        providerProvenance = provenance,
    )

    private fun provenance(
        packageName: String = "example.dex.plugin",
        componentClassName: String = "CompilerService",
        providerId: String = "fixture-d8",
        packageUid: Int = 10_000,
        signers: Collection<String> = listOf("bb".repeat(32)),
        versionCode: Long = 7,
        lastUpdateTime: Long = 123,
    ) = DexCompilerProviderProvenance(
        packageName,
        componentClassName,
        providerId,
        packageUid,
        signers,
        versionCode,
        lastUpdateTime,
    )
}
