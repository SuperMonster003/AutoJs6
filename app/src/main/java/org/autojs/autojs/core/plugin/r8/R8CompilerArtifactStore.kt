package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8CompileRequest
import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import org.autojs.plugin.r8compiler.api.R8Result
import java.io.InputStream

internal interface R8CompilerStoredArtifact {
    val cacheKeySha256: String
    val generation: String
    val result: R8Result

    fun openOutputBundle(): InputStream
    fun openArtifact(role: R8ArtifactRole): InputStream
}

internal interface R8CompilerArtifactStore {
    fun lookup(
        cacheKeySha256: String,
        request: R8CompileRequest,
        capabilities: R8CompilerCapabilities,
    ): R8CompilerStoredArtifact?

    fun publishVerified(
        cacheKeySha256: String,
        verified: R8CompilerVerifiedArtifacts,
        request: R8CompileRequest,
        capabilities: R8CompilerCapabilities,
    ): R8CompilerStoredArtifact
}
