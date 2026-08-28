package org.autojs.autojs.core.plugin.typescript

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TypeScriptCompilationCacheKeyTest {

    @Test
    fun ignoresPerRequestAuthenticationButRetainsCompilationSemantics() {
        val first = rhinoPrepared(
            requestId = "host-first",
            inputArchiveSha256 = hash('8'),
            requestManifestSha256 = hash('9'),
        )
        val second = rhinoPrepared(
            requestId = "host-second",
            inputArchiveSha256 = hash('a'),
            requestManifestSha256 = hash('b'),
        ).copy(archiveFile = File("another-request.zip"))

        assertEquals(
            TypeScriptCompilationCacheKey.sha256(first),
            TypeScriptCompilationCacheKey.sha256(second),
        )
    }

    @Test
    fun canonicalizesSourceGraphOrder() {
        val prepared = rhinoPrepared()
        val reordered = prepared.copy(sourceFiles = prepared.sourceFiles.reversed())

        assertEquals(
            TypeScriptCompilationCacheKey.sha256(prepared),
            TypeScriptCompilationCacheKey.sha256(reordered),
        )
    }

    @Test
    fun invalidatesForSourceConfigIdentityAndCompilerAuthorityChanges() {
        val base = rhinoPrepared()
        val baseKey = TypeScriptCompilationCacheKey.sha256(base)
        val variants = listOf(
            base.copy(projectId = "directory-other"),
            base.copy(entrySourceUri = "autojs://source/other/main.ts"),
            base.copy(entryPath = "sources/other.ts"),
            base.copy(rootPaths = listOf("sources/main.ts")),
            base.copy(
                sourceFiles = base.sourceFiles.mapIndexed { index, source ->
                    if (index == 0) source.copy(sha256 = hash('c')) else source
                },
            ),
            base.copy(configSha256 = hash('d')),
            base.copy(authority = base.authority.copy(compilerVersion = "6.0.4")),
            base.copy(
                authority = base.authority.copy(compilerDistributionFingerprint = hash('e')),
            ),
            base.copy(authority = base.authority.copy(declarationsFingerprint = hash('f'))),
            base.copy(authority = base.authority.copy(profileRevision = 2)),
            base.copy(
                authority = base.authority.copy(configPolicyFingerprint = hash('0')),
            ),
        )

        variants.forEach { variant ->
            assertNotEquals(baseKey, TypeScriptCompilationCacheKey.sha256(variant))
        }
    }

    @Test
    fun includesNodePackageMetadataAndPolicyAuthority() {
        val base = nodePrepared()
        val packageChanged = base.copy(
            packageMetadata = requireNotNull(base.packageMetadata).let { metadata ->
                metadata.copy(
                    files = metadata.files.mapIndexed { index, file ->
                        if (index == 0) file.copy(sha256 = hash('c')) else file
                    },
                )
            },
        )
        val policyChanged = base.copy(
            authority = base.authority.copy(packageMetadataPolicyFingerprint = hash('d')),
        )

        assertNotEquals(
            TypeScriptCompilationCacheKey.sha256(base),
            TypeScriptCompilationCacheKey.sha256(packageChanged),
        )
        assertNotEquals(
            TypeScriptCompilationCacheKey.sha256(base),
            TypeScriptCompilationCacheKey.sha256(policyChanged),
        )
    }

    @Test
    fun includesDependencyLockContentInventoryAndPolicyAuthority() {
        val base = nodePrepared()
        val layer = requireNotNull(base.dependencyLayer)
        val variants = listOf(
            base.copy(dependencyLayer = layer.copy(fingerprint = hash('c'))),
            base.copy(
                dependencyLayer = layer.copy(
                    identityFingerprint = hash('d'),
                    inventoryFingerprint = hash('e'),
                    fingerprint = hash('f'),
                    files = layer.files.mapIndexed { index, file ->
                        if (index == 0) file.copy(sha256 = hash('d')) else file
                    },
                ),
            ),
            base.copy(
                authority = base.authority.copy(
                    dependencyLayerPolicyFingerprint = hash('0'),
                ),
            ),
        )

        variants.forEach { variant ->
            assertNotEquals(
                TypeScriptCompilationCacheKey.sha256(base),
                TypeScriptCompilationCacheKey.sha256(variant),
            )
        }

        val resolverAuthorityBytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeBoolean(true)
                data.writeInt(TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_REVISION)
                data.writeBoolean(true)
                data.write(
                    TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_FINGERPRINT
                        .chunked(2)
                        .map { byte -> byte.toInt(16).toByte() }
                        .toByteArray(),
                )
            }
        }.toByteArray()
        val canonicalBytes = TypeScriptCompilationCacheKey.canonicalBytes(base)
        assertArrayEquals(
            resolverAuthorityBytes,
            canonicalBytes.copyOfRange(
                canonicalBytes.size - resolverAuthorityBytes.size,
                canonicalBytes.size,
            ),
        )
    }

    private fun rhinoPrepared(
        requestId: String = "host-request",
        inputArchiveSha256: String = hash('6'),
        requestManifestSha256: String = hash('7'),
    ) = TypeScriptPreparedCompilation(
        requestId = requestId,
        projectId = "directory-project",
        entrySourceUri = "autojs://source/directory-project/main.ts",
        entryPath = "sources/main.ts",
        archiveFile = File("request.zip"),
        inputArchiveSha256 = inputArchiveSha256,
        requestManifestSha256 = requestManifestSha256,
        configByteLength = 2L,
        configSha256 = hash('5'),
        authority = rhinoAuthority(),
        rootPaths = listOf("sources/lib.ts", "sources/main.ts"),
        sourceFiles = listOf(
            TypeScriptPreparedSource("sources/main.ts", "main.ts", File("main.ts"), hash('1')),
            TypeScriptPreparedSource("sources/lib.ts", "lib.ts", File("lib.ts"), hash('2')),
        ),
    )

    private fun nodePrepared() = TypeScriptPreparedCompilation(
        requestId = "host-node",
        projectId = "directory-node",
        entrySourceUri = "autojs://source/directory-node/sources/main.ts",
        entryPath = "sources/main.ts",
        archiveFile = File("node-request.zip"),
        inputArchiveSha256 = hash('6'),
        requestManifestSha256 = hash('7'),
        configByteLength = null,
        configSha256 = null,
        authority = nodeAuthority(),
        rootPaths = listOf("sources/main.ts"),
        sourceFiles = listOf(
            TypeScriptPreparedSource("sources/main.ts", "main.ts", File("main.ts"), hash('1')),
        ),
        packageMetadata = TypeScriptPreparedPackageMetadata(
            dependencyRoots = emptyList(),
            files = listOf(
                TypeScriptPreparedPackageMetadataFile(
                    archivePath = "package.json",
                    size = 17L,
                    sha256 = hash('2'),
                    typeState = "module",
                ),
            ),
            inventoryFingerprint = hash('3'),
        ),
        dependencyLayer = dependencyLayer(),
    )

    private fun dependencyLayer(): TypeScriptPreparedDependencyLayer {
        val files = listOf(
            TypeScriptPreparedDependencyLayerFile(
                archivePath = "dependencies/lockfiles/package-lock.json",
                size = 17L,
                sha256 = hash('8'),
            ),
            TypeScriptPreparedDependencyLayerFile(
                archivePath = "dependencies/node_modules/dayjs/index.js",
                size = 21L,
                sha256 = hash('b'),
            ),
        )
        return TypeScriptPreparedDependencyLayer(
            identityKind = "package-lock",
            identityPath = "dependencies/lockfiles/package-lock.json",
            identityFingerprint = hash('8'),
            inventoryFingerprint = hash('9'),
            fingerprint = hash('a'),
            files = files,
            totalBytes = files.sumOf(TypeScriptPreparedDependencyLayerFile::size),
            totalPathBytes = files.sumOf { file -> file.archivePath.toByteArray().size.toLong() },
        )
    }

    private fun rhinoAuthority() = TypeScriptCompilerAuthority(
        targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
        compilerVersion = "6.0.3",
        compilerDistributionFingerprint = hash('1'),
        declarationsFingerprint = hash('2'),
        declarationProfileFingerprint = hash('3'),
        runtimeCapabilitiesFingerprint = hash('4'),
        profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        profileRevision = 1,
        configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        configPolicyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
        configPolicyFingerprint = hash('5'),
    )

    private fun nodeAuthority() = TypeScriptCompilerAuthority(
        targetProfile = TypeScriptCompilerContract.PROFILE_NODE,
        compilerVersion = "6.0.3",
        compilerDistributionFingerprint = hash('1'),
        declarationsFingerprint = hash('2'),
        declarationProfileFingerprint = hash('3'),
        runtimeCapabilitiesFingerprint = hash('4'),
        profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        profileRevision = 2,
        configSchemaRevision = TypeScriptCompilerAuthority.CONFIG_NONE_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_NONE,
        packageMetadataSchemaRevision = 1,
        packageMetadataPolicyRevision = 1,
        packageMetadataPolicyFingerprint = hash('5'),
        dependencyLayerSchemaRevision = TypeScriptR2Contract.DEPENDENCY_LAYER_SCHEMA_REVISION,
        dependencyLayerPolicyRevision = TypeScriptR2Contract.DEPENDENCY_LAYER_POLICY_REVISION,
        dependencyLayerPolicyFingerprint = hash('6'),
        dependencyResolverPolicyRevision =
            TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_REVISION,
        dependencyResolverPolicyFingerprint =
            TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
    )

    private fun hash(character: Char) = character.toString().repeat(64)
}
