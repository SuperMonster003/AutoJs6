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
            assertNull(prepared.dependencyLayer)
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
    fun packsThreeDirectDependenciesIntoAnIndependentReadOnlyLayer() {
        withProject { root ->
            File(root, "package.json").writeText(
                """{"type":"module","dependencies":{"dayjs":"1.11.13","lodash":"4.17.21","zod":"4.1.5"}}""",
            )
            File(root, "package-lock.json").writeText(
                """{"name":"t2-project","lockfileVersion":3,"packages":{}}""",
            )
            File(root, "main.ts").writeText("export const ready: boolean = true;\n")
            listOf("dayjs", "lodash", "zod").forEachIndexed { index, packageName ->
                val packageRoot = File(root, "node_modules/$packageName").apply { mkdirs() }
                File(packageRoot, "package.json").writeText(
                    """{"name":"$packageName","scripts":{"test":"ignored"}}""",
                )
                File(packageRoot, "index.js").writeText("export const value = $index;\n")
                File(packageRoot, "index.d.ts").writeText("export declare const value: number;\n")
                File(packageRoot, "README.md").writeText("excluded documentation")
                File(packageRoot, "index.js.map").writeText("{}")
                File(packageRoot, "tests").apply { mkdirs() }
                    .resolve("fixture.js").writeText("throw new Error('excluded');\n")
            }

            fun prepare(name: String): TypeScriptPreparedCompilation =
                TypeScriptProjectArchive.write(
                    projectRoot = root,
                    entryFile = File(root, "main.ts"),
                    requestId = "$REQUEST_ID-$name",
                    projectId = PROJECT_ID,
                    authority = nodeAuthority(),
                    destination = File(root, "$name.zip"),
                )

            val first = prepare("first")
            val firstLayer = requireNotNull(first.dependencyLayer)
            assertEquals("package-lock", firstLayer.identityKind)
            assertEquals("dependencies/lockfiles/package-lock.json", firstLayer.identityPath)
            assertEquals(10, firstLayer.files.size)
            assertEquals(1, first.sourceFiles.size)
            assertTrue(firstLayer.files.all { file -> file.archivePath.startsWith("dependencies/") })
            assertFalse(firstLayer.files.any { file ->
                file.archivePath.endsWith(".md") ||
                    file.archivePath.endsWith(".map") ||
                    "/tests/" in file.archivePath
            })

            val firstManifest = ZipFile(first.archiveFile).use { zip ->
                assertTrue(zip.getEntry("dependencies/node_modules/dayjs/index.js") != null)
                assertTrue(zip.getEntry("dependencies/node_modules/lodash/README.md") == null)
                TypeScriptStrictJson.parseObject(
                    zip.getInputStream(zip.getEntry("request-manifest.json")).use { it.readBytes() },
                )
            }
            @Suppress("UNCHECKED_CAST")
            val dependencyManifest =
                firstManifest.getValue("dependencyLayer") as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val callerFiles = firstManifest.getValue("files") as List<Map<String, Any?>>
            assertEquals(1, callerFiles.size)
            assertEquals(
                10L,
                (dependencyManifest.getValue("fileCount") as TypeScriptStrictJson.NumberToken)
                    .source.toLong(),
            )

            File(root, "node_modules/dayjs/index.js").writeText("export const value = 42;\n")
            val contentChanged = prepare("content-changed")
            assertEquals(
                firstManifest.getValue("callerInventoryFingerprint"),
                ZipFile(contentChanged.archiveFile).use { zip ->
                    TypeScriptStrictJson.parseObject(
                        zip.getInputStream(zip.getEntry("request-manifest.json")).use {
                            it.readBytes()
                        },
                    ).getValue("callerInventoryFingerprint")
                },
            )
            assertFalse(
                firstLayer.fingerprint == requireNotNull(contentChanged.dependencyLayer).fingerprint,
            )

            File(root, "package-lock.json").appendText("\n")
            val lockChanged = prepare("lock-changed")
            assertFalse(
                requireNotNull(contentChanged.dependencyLayer).fingerprint ==
                    requireNotNull(lockChanged.dependencyLayer).fingerprint,
            )
        }
    }

    @Test
    fun rejectsNativeDependencyBeforeFilteredFilesEnterTheArchive() {
        withProject { root ->
            File(root, "main.ts").writeText("import bcrypt from 'bcrypt';\n")
            val packageRoot = File(root, "node_modules/bcrypt").apply { mkdirs() }
            File(packageRoot, "package.json").writeText(
                """{"name":"bcrypt","version":"6.0.0","scripts":{"install":"node-gyp-build"}}""",
            )
            File(packageRoot, "index.js").writeText("module.exports = {};\n")
            File(packageRoot, "binding.gyp").writeText("{}\n")
            File(packageRoot, "prebuilds/linux-arm64").apply { mkdirs() }
                .resolve("bcrypt.glibc.node").writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))

            val error = runCatching {
                TypeScriptProjectArchive.write(
                    projectRoot = root,
                    entryFile = File(root, "main.ts"),
                    requestId = REQUEST_ID,
                    projectId = PROJECT_ID,
                    authority = nodeAuthority(),
                    destination = File(root, "native-input.zip"),
                )
            }.exceptionOrNull()

            assertTrue(error is TypeScriptDependencyBoundaryException)
            error as TypeScriptDependencyBoundaryException
            assertEquals(TypeScriptR2Contract.ERROR_NATIVE_DEPENDENCY_UNSUPPORTED, error.code)
            assertEquals("native-binary", error.signal)
            assertEquals("bcrypt", error.packageName)
            assertEquals("bcrypt/prebuilds/linux-arm64/bcrypt.glibc.node", error.dependencyPath)
        }
    }

    @Test
    fun rejectsDependencyInstallLifecycleButKeepsOrdinaryScriptsInert() {
        withProject { root ->
            File(root, "main.ts").writeText("import value from 'scripted';\n")
            val packageRoot = File(root, "node_modules/scripted").apply { mkdirs() }
            File(packageRoot, "package.json").writeText(
                """{"name":"scripted","scripts":{"build":"ignored","test":"ignored","preinstall":"first-in-json","postinstall":"second-in-json","install":"last-in-json"}}""",
            )
            File(packageRoot, "index.js").writeText("export default 1;\n")

            val error = runCatching {
                TypeScriptProjectArchive.write(
                    projectRoot = root,
                    entryFile = File(root, "main.ts"),
                    requestId = REQUEST_ID,
                    projectId = PROJECT_ID,
                    authority = nodeAuthority(),
                    destination = File(root, "lifecycle-input.zip"),
                )
            }.exceptionOrNull()

            assertTrue(error is TypeScriptDependencyBoundaryException)
            error as TypeScriptDependencyBoundaryException
            assertEquals(
                TypeScriptR2Contract.ERROR_DEPENDENCY_INSTALL_SCRIPT_UNSUPPORTED,
                error.code,
            )
            assertEquals("scripts.install", error.signal)
            assertEquals("scripted", error.packageName)
            assertEquals("scripted/package.json", error.dependencyPath)
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
    fun bindsNearestEsmAndCommonJsWorkspacePackageBoundaries() {
        withProject { root ->
            File(root, "package.json").writeText("{}")
            File(root, "main.mts").writeText(
                "import { esmValue } from './packages/@scope/esm/value.js'; " +
                    "import { cjsValue } from './packages/cjs/value.js'; " +
                    "export const total = esmValue + cjsValue;\n",
            )
            val esm = File(root, "packages/@scope/esm").apply { mkdirs() }
            File(esm, "package.json").writeText("""{"type":"module"}""")
            File(esm, "value.ts").writeText("export const esmValue = 20;\n")
            val cjs = File(root, "packages/cjs").apply { mkdirs() }
            File(cjs, "package.json").writeText("""{"type":"commonjs"}""")
            File(cjs, "value.ts").writeText("export const cjsValue = 22;\n")

            val archive = File(root, "input.zip")
            val prepared = TypeScriptProjectArchive.write(
                projectRoot = root,
                entryFile = File(root, "main.mts"),
                requestId = REQUEST_ID,
                projectId = PROJECT_ID,
                authority = nodeAuthority(),
                destination = archive,
            )

            val metadata = requireNotNull(prepared.packageMetadata)
            assertEquals(
                listOf("packages/@scope/esm", "packages/cjs"),
                metadata.dependencyRoots,
            )
            assertEquals(
                listOf(
                    "package.json",
                    "packages/@scope/esm/package.json",
                    "packages/cjs/package.json",
                ),
                metadata.files.map(TypeScriptPreparedPackageMetadataFile::archivePath),
            )
            assertEquals(
                "commonjs",
                metadata.nearestFileForSource("sources/packages/cjs/value.ts").typeState,
            )
            assertEquals(
                "module",
                metadata.nearestFileForSource("/sources/packages/@scope/esm/value.ts").typeState,
            )
            ZipFile(archive).use { zip ->
                assertTrue(zip.getEntry("package.json") != null)
                assertTrue(zip.getEntry("packages/@scope/esm/package.json") != null)
                assertTrue(zip.getEntry("packages/cjs/package.json") != null)
                val manifest = TypeScriptStrictJson.parseObject(
                    zip.getInputStream(zip.getEntry("request-manifest.json")).use { it.readBytes() },
                )
                @Suppress("UNCHECKED_CAST")
                val packageMetadata = manifest.getValue("packageMetadata") as Map<String, Any?>
                assertEquals(metadata.dependencyRoots, packageMetadata.getValue("dependencyRoots"))
                @Suppress("UNCHECKED_CAST")
                val files = packageMetadata.getValue("files") as List<Map<String, Any?>>
                assertEquals(
                    metadata.files.map(TypeScriptPreparedPackageMetadataFile::archivePath),
                    files.map { file -> file.getValue("path") },
                )
            }
        }
    }

    @Test
    fun rejectsLifecycleScriptsInWorkspacePackageMetadata() {
        withProject { root ->
            File(root, "package.json").writeText("{}")
            File(root, "main.mts").writeText("export const value = 1;\n")
            val nested = File(root, "packages/esm").apply { mkdirs() }
            File(nested, "package.json").writeText(
                """{"type":"module","scripts":{"build":"ignored"}}""",
            )
            File(nested, "value.ts").writeText("export const value = 1;\n")

            val error = runCatching {
                TypeScriptProjectArchive.write(
                    projectRoot = root,
                    entryFile = File(root, "main.mts"),
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
    fun rejectsPackageMetadataOutsideControlledWorkspaceRoots() {
        withProject { root ->
            File(root, "package.json").writeText("{}")
            File(root, "main.mts").writeText("export const value = 1;\n")
            val nested = File(root, "nested").apply { mkdirs() }
            File(nested, "package.json").writeText("""{"type":"module"}""")
            File(nested, "value.ts").writeText("export const value = 1;\n")

            val error = runCatching {
                TypeScriptProjectArchive.write(
                    projectRoot = root,
                    entryFile = File(root, "main.mts"),
                    requestId = REQUEST_ID,
                    projectId = PROJECT_ID,
                    authority = nodeAuthority(),
                    destination = File(root, "input.zip"),
                )
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertTrue(error?.message.orEmpty().contains("controlled packages workspace"))
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

    @Test
    fun editorProjectSnapshotMatchesTheCompilerSourceInventory() {
        withProject { root ->
            File(root, "project.json").writeText("""{"main":"src/main.ts"}""")
            File(root, "tsconfig.json").writeText("""{"compilerOptions":{"strict":true}}""")
            val entry = File(root, "src/main.ts").apply {
                requireNotNull(parentFile).mkdirs()
                writeText("import { answer } from './shared'; export const value = answer;\n")
            }
            File(root, "src/shared.ts").writeText("export const answer: number = 42;\n")
            File(root, "src/data.json").writeText("""{"answer":42}""")
            File(root, "build/ignored.ts").apply {
                requireNotNull(parentFile).mkdirs()
                writeText("export const ignored = true;\n")
            }
            File(root, "node_modules/pkg/index.d.ts").apply {
                requireNotNull(parentFile).mkdirs()
                writeText("export declare const external: string;\n")
            }

            val prepared = TypeScriptProjectArchive.write(
                projectRoot = root,
                entryFile = entry,
                requestId = REQUEST_ID,
                projectId = PROJECT_ID,
                authority = rhinoAuthority(),
                destination = File(root, "editor-source-golden.zip"),
            )
            val editorSnapshot = requireNotNull(
                TypeScriptEditorProjectSnapshotProvider.captureRequired(entry.path),
            )

            assertEquals(TypeScriptCompilerContract.PROFILE_RHINO, editorSnapshot.targetProfile)
            assertEquals(
                prepared.sourceFiles.map(TypeScriptPreparedSource::projectRelativePath),
                editorSnapshot.sourceFiles.map { source -> source.relativePath },
            )
            assertEquals(
                prepared.sourceFiles.map(TypeScriptPreparedSource::sha256),
                editorSnapshot.sourceFiles.map { source -> source.sha256 },
            )
            assertFalse(editorSnapshot.sourceFiles.any { source ->
                source.relativePath.startsWith("build/") ||
                    source.relativePath.startsWith("node_modules/")
            })
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
        dependencyLayerSchemaRevision = TypeScriptR2Contract.DEPENDENCY_LAYER_SCHEMA_REVISION,
        dependencyLayerPolicyRevision = TypeScriptR2Contract.DEPENDENCY_LAYER_POLICY_REVISION,
        dependencyLayerPolicyFingerprint = DEPENDENCY_LAYER_POLICY_FINGERPRINT,
        dependencyResolverPolicyRevision =
            TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_REVISION,
        dependencyResolverPolicyFingerprint =
            TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
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
        dependencyLayerSchemaRevision = TypeScriptR2Contract.DEPENDENCY_LAYER_SCHEMA_REVISION,
        dependencyLayerPolicyRevision = TypeScriptR2Contract.DEPENDENCY_LAYER_POLICY_REVISION,
        dependencyLayerPolicyFingerprint = DEPENDENCY_LAYER_POLICY_FINGERPRINT,
        dependencyResolverPolicyRevision =
            TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_REVISION,
        dependencyResolverPolicyFingerprint =
            TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
    )

    private companion object {
        const val REQUEST_ID = "host-project-request"
        const val PROJECT_ID = "host-project"
        const val CONTROLLED_TSCONFIG_POLICY_FINGERPRINT =
            "849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d"
        const val PACKAGE_METADATA_POLICY_FINGERPRINT =
            "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97"
        const val DEPENDENCY_LAYER_POLICY_FINGERPRINT =
            "6666666666666666666666666666666666666666666666666666666666666666"
    }
}
