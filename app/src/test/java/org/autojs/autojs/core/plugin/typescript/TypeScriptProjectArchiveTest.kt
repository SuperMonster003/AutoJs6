package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptProjectArchiveTest {

    @Test
    fun writesRhinoProjectSnapshotWithTsConfigJsonAndMoreThanTenModules() {
        withProject { root ->
            File(root, "project.json").writeText("""{"main":"main.ts"}""")
            File(root, "tsconfig.json").writeText(
                """{"compilerOptions":{"noImplicitReturns":true,"strict":true}}""",
            )
            File(root, "main.ts").writeText("import { value } from './模块/m0'; toast(value);\n")
            val moduleDirectory = File(root, "模块").apply { mkdirs() }
            repeat(11) { index ->
                File(moduleDirectory, "m$index.ts").writeText(
                    if (index == 10) {
                        "export const value: number = 42;\n"
                    } else {
                        "export { value } from './m${index + 1}';\n"
                    },
                )
            }
            File(root, "data.json").writeText("""{"name":"snapshot"}""")
            File(root, "helper.js").writeText("module.exports = 7;\n")

            val archive = File(root, "input.zip")
            val prepared = TypeScriptProjectArchive.write(
                projectRoot = root,
                entryFile = File(root, "main.ts"),
                requestId = REQUEST_ID,
                projectId = PROJECT_ID,
                authority = rhinoAuthority(),
                destination = archive,
            )

            assertEquals("sources/main.ts", prepared.entryPath)
            assertEquals(12, prepared.rootPaths.size)
            assertEquals(14, prepared.sourceFiles.size)
            assertNull(prepared.packageMetadata)
            assertTrue(prepared.rootPaths.contains("sources/模块/m10.ts"))
            assertTrue(prepared.sourceFiles.all { source ->
                source.originalFile?.isFile == true && source.sha256.length == 64
            })

            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                assertTrue(entries.all { entry -> entry.method == ZipEntry.STORED })
                assertEquals("request-manifest.json", entries.first().name)
                assertEquals("tsconfig.json", entries[1].name)
                assertFalse(entries.any { entry -> entry.name == "project.json" })
                val manifest = TypeScriptStrictJson.parseObject(
                    zip.getInputStream(zip.getEntry("request-manifest.json")).use { it.readBytes() },
                )
                assertEquals(prepared.rootPaths, manifest.getValue("rootPaths"))
                assertTrue(manifest.containsKey("config"))
                assertFalse(manifest.containsKey("packageMetadata"))
            }
        }
    }

    @Test
    fun bindsNodePackageMetadataForEsmAndCommonJsTsProjects() {
        listOf("module", "commonjs").forEach { packageType ->
            withProject { root ->
                File(root, "project.json").writeText("""{"type":"node","main":"main.ts"}""")
                File(root, "package.json").writeText("""{"type":"$packageType"}""")
                File(root, "tsconfig.json").writeText("""{"compilerOptions":{"strict":false}}""")
                File(root, "main.ts").writeText("import { value } from './lib/m0.js';\n")
                val library = File(root, "lib").apply { mkdirs() }
                repeat(11) { index ->
                    File(library, "m$index.ts").writeText(
                        if (index == 10) {
                            "export const value: number = 42;\n"
                        } else {
                            "export { value } from './m${index + 1}.js';\n"
                        },
                    )
                }

                val archive = File(root, "node-$packageType.zip")
                val prepared = TypeScriptProjectArchive.write(
                    projectRoot = root,
                    entryFile = File(root, "main.ts"),
                    requestId = "$REQUEST_ID-$packageType",
                    projectId = "$PROJECT_ID-$packageType",
                    authority = nodeAuthority(),
                    destination = archive,
                )

                assertEquals(packageType, prepared.packageMetadata?.typeState)
                assertNull(prepared.configByteLength)
                ZipFile(archive).use { zip ->
                    assertTrue(zip.getEntry("package.json") != null)
                    assertTrue(zip.getEntry("tsconfig.json") == null)
                    val manifest = TypeScriptStrictJson.parseObject(
                        zip.getInputStream(zip.getEntry("request-manifest.json")).use { it.readBytes() },
                    )
                    assertTrue(manifest.containsKey("packageMetadata"))
                    assertFalse(manifest.containsKey("config"))
                    assertEquals(prepared.rootPaths, manifest.getValue("rootPaths"))
                }
            }
        }
    }

    @Test
    fun rejectsNodePackageLifecycleScripts() {
        withProject { root ->
            File(root, "package.json").writeText(
                """{"type":"module","scripts":{"postinstall":"echo unsafe"}}""",
            )
            File(root, "main.ts").writeText("export const value = 1;\n")
            val error = runCatching {
                TypeScriptProjectArchive.write(
                    projectRoot = root,
                    entryFile = File(root, "main.ts"),
                    requestId = REQUEST_ID,
                    projectId = PROJECT_ID,
                    authority = nodeAuthority(),
                    destination = File(root, "input.zip"),
                )
            }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertTrue(error?.message.orEmpty().contains("lifecycle scripts"))
        }
    }

    @Test
    fun rejectsNestedNodePackageBoundaryUntilItHasExplicitAuthority() {
        withProject { root ->
            File(root, "package.json").writeText("""{"type":"commonjs"}""")
            File(root, "main.ts").writeText("import './nested/value.js';\n")
            val nested = File(root, "nested").apply { mkdirs() }
            File(nested, "package.json").writeText("""{"type":"module"}""")
            File(nested, "value.ts").writeText("export const value = 1;\n")

            val error = runCatching {
                TypeScriptProjectArchive.write(
                    projectRoot = root,
                    entryFile = File(root, "main.ts"),
                    requestId = REQUEST_ID,
                    projectId = PROJECT_ID,
                    authority = nodeAuthority(),
                    destination = File(root, "input.zip"),
                )
            }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertTrue(error?.message.orEmpty().contains("Nested TypeScript package boundaries"))
        }
    }

    @Test
    fun clearsAllSourceSnapshotsWhenPreparationFails() {
        withProject { root ->
            File(root, "a.ts").writeText("export const a = 1;\n")
            File(root, "b.ts").writeText("export const b = 2;\n")
            val snapshots = mutableListOf<ByteArray>()
            var readCount = 0
            val error = runCatching {
                TypeScriptProjectArchive.write(
                    projectRoot = root,
                    entryFile = File(root, "a.ts"),
                    requestId = REQUEST_ID,
                    projectId = PROJECT_ID,
                    authority = rhinoAuthority(),
                    destination = File(root, "input.zip"),
                    sourceContentReader = TypeScriptSourceContentReader { file, _ ->
                        readCount += 1
                        val bytes = if (readCount == 2) {
                            byteArrayOf(0xc3.toByte())
                        } else {
                            file.readBytes()
                        }
                        snapshots += bytes
                        bytes
                    },
                )
            }.exceptionOrNull()

            assertTrue(error != null)
            assertEquals(2, snapshots.size)
            assertTrue(snapshots.all { bytes -> bytes.all { byte -> byte == 0.toByte() } })
        }
    }

    private fun withProject(block: (File) -> Unit) {
        val root = Files.createTempDirectory("typescript-project-test-").toFile()
        try {
            block(root)
        } finally {
            root.walkBottomUp().forEach { entry -> entry.delete() }
        }
    }

    private fun rhinoAuthority() = TypeScriptCompilerAuthority(
        targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
        compilerVersion = "6.0.3",
        compilerDistributionFingerprint = "1".repeat(64),
        declarationsFingerprint = "2".repeat(64),
        declarationProfileFingerprint = "3".repeat(64),
        runtimeCapabilitiesFingerprint = "4".repeat(64),
        profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        profileRevision = 1,
        configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        configPolicyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
        configPolicyFingerprint = CONTROLLED_TSCONFIG_POLICY_FINGERPRINT,
    )

    private fun nodeAuthority() = TypeScriptCompilerAuthority(
        targetProfile = TypeScriptCompilerContract.PROFILE_NODE,
        compilerVersion = "6.0.3",
        compilerDistributionFingerprint = "1".repeat(64),
        declarationsFingerprint = "2".repeat(64),
        declarationProfileFingerprint = "3".repeat(64),
        runtimeCapabilitiesFingerprint = "4".repeat(64),
        profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        profileRevision = 2,
        configSchemaRevision = TypeScriptCompilerAuthority.CONFIG_NONE_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_NONE,
        packageMetadataSchemaRevision = 1,
        packageMetadataPolicyRevision = 1,
        packageMetadataPolicyFingerprint = PACKAGE_METADATA_POLICY_FINGERPRINT,
    )

    private companion object {
        const val REQUEST_ID = "host-project-request"
        const val PROJECT_ID = "host-project"
        const val CONTROLLED_TSCONFIG_POLICY_FINGERPRINT =
            "849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d"
        const val PACKAGE_METADATA_POLICY_FINGERPRINT =
            "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97"
    }
}
