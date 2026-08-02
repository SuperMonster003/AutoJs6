package org.autojs.plugin.dexcompiler.fake

import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import org.autojs.plugin.dexcompiler.api.DexCompilerValidation
import org.autojs.plugin.dexcompiler.api.DexSha256
import java.io.InputStream

class FakeDexArtifact private constructor(
    outputBytes: ByteArray,
    val outputSha256: DexSha256,
    val dexEntryCount: Int,
) {
    private val outputBytes = outputBytes.copyOf()

    val outputSizeBytes: Long
        get() = outputBytes.size.toLong()

    fun bytes(): ByteArray = outputBytes.copyOf()

    companion object {
        fun create(outputBytes: ByteArray, dexEntryCount: Int): FakeDexArtifact = FakeDexArtifact(
            outputBytes = outputBytes,
            outputSha256 = FakeDexHashes.sha256(outputBytes),
            dexEntryCount = dexEntryCount,
        )
    }
}

class FakeDexCompilerEngine {
    fun compile(
        request: DexCompileRequest,
        programInput: InputStream,
        scenario: FakeDexCompilerScenario,
    ): FakeDexArtifact {
        DexCompilerValidation.validateRequestAgainst(
            request,
            FakeDexCompilerFixture.capabilities,
            FakeDexCompilerFixture.protocolVersion,
        )
        BoundedJarValidator.readAndValidate(request, programInput)
        val classesDex = if (scenario == FakeDexCompilerScenario.INVALID_OUTPUT) {
            INVALID_DEX.copyOf()
        } else {
            EmbeddedDexFixture.bytes()
        }
        val output = DeterministicDexZip.create(classesDex)
        if (output.size.toLong() > request.maxOutputBytes) {
            throw FakeDexCompileFailure(
                DexCompilerErrorCode.OUTPUT_TOO_LARGE,
                DexCompilerFailurePhase.OUTPUT_PACKAGING,
                "DEX ZIP exceeds the requested output limit",
            )
        }
        if (output.size.toLong() > FakeDexCompilerFixture.capabilities.limits.maxOutputBytes) {
            throw FakeDexCompileFailure(
                DexCompilerErrorCode.OUTPUT_TOO_LARGE,
                DexCompilerFailurePhase.OUTPUT_PACKAGING,
                "DEX ZIP exceeds the fixture output limit",
            )
        }
        return FakeDexArtifact.create(output, dexEntryCount = 1)
    }

    private companion object {
        val INVALID_DEX = "not-a-valid-dex".toByteArray(Charsets.US_ASCII)
    }
}
