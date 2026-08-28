package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TypeScriptRuntimeDependencyLoadingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun snapshotsTheExactDependencyInventoryForRuntimeOverlays() {
        val root = temporaryFolder.newFolder("runtime-overlay")
        val packageDirectory = File(root, "node_modules/dual-package").apply { mkdirs() }
        val packageJson = File(packageDirectory, "package.json").apply {
            writeText("""{"name":"dual-package","main":"index.cjs"}""")
        }
        val commonJs = File(packageDirectory, "index.cjs").apply {
            writeText("module.exports = { value: 42 };\n")
        }
        val declarations = File(packageDirectory, "index.d.ts").apply {
            writeText("export declare const value: number;\n")
        }
        val layer = dependencyLayer(root, packageJson, commonJs, declarations)

        val snapshot = verifiedRuntimeDependencySources(root.canonicalFile, layer)

        assertEquals(3, snapshot.size)
        assertEquals(
            setOf(packageJson.canonicalPath, commonJs.canonicalPath, declarations.canonicalPath),
            snapshot.mapTo(linkedSetOf(), TypeScriptRuntimeDependencySource::hostPath),
        )
        assertEquals(
            "module.exports = { value: 42 };\n",
            snapshot.single { it.hostPath == commonJs.canonicalPath }.source,
        )
    }

    @Test
    fun dependencyMutationAfterCompilationHasAStableRuntimeError() {
        val root = temporaryFolder.newFolder("runtime-drift")
        val entry = File(root, "node_modules/demo/index.cjs").apply {
            parentFile.mkdirs()
            writeText("module.exports = 1;\n")
        }
        val layer = dependencyLayer(root, entry)
        entry.writeText("module.exports = 2;\n")

        val failure = assertThrows(TypeScriptRuntimeDependencyException::class.java) {
            verifiedRuntimeDependencySources(root.canonicalFile, layer)
        }

        assertEquals(TypeScriptR2Contract.ERROR_RUNTIME_DEPENDENCY_CHANGED, failure.code)
        assertTrue(failure.message.orEmpty().contains("node_modules/demo/index.cjs"))
    }

    @Test
    fun rhinoSelectsRequireFromADualPackageAndResolvesNestedVersions() {
        val root = temporaryFolder.newFolder("rhino-dual")
        val caller = File(root, "main.js").apply { writeText("module.exports = {};\n") }
        val dual = File(root, "node_modules/dual-package").apply { mkdirs() }
        val dualManifest = File(dual, "package.json").apply {
            writeText(
                """
                {
                  "name":"dual-package",
                  "type":"module",
                  "exports":{".":{
                    "types":"./index.d.ts",
                    "import":"./index.mjs",
                    "require":"./index.cjs"
                  }}
                }
                """.trimIndent(),
            )
        }
        File(dual, "index.mjs").writeText("export const value = 42;\n")
        val dualCommonJs = File(dual, "index.cjs").apply {
            writeText("exports.value = 42;\n")
        }

        val parentPackage = File(root, "node_modules/parent-package").apply { mkdirs() }
        val parentEntry = File(parentPackage, "index.cjs").apply {
            writeText("module.exports = require('shared');\n")
        }
        val rootShared = File(root, "node_modules/shared/index.cjs").apply {
            parentFile.mkdirs()
            writeText("module.exports = 'root';\n")
        }
        val nestedShared = File(parentPackage, "node_modules/shared/index.cjs").apply {
            parentFile.mkdirs()
            writeText("module.exports = 'nested';\n")
        }
        val resolver = TypeScriptRhinoDependencyResolver(
            root,
            mapOf(dualManifest.canonicalPath to dualManifest.readText()),
        )

        assertEquals(dualCommonJs.canonicalFile, resolver.resolve("dual-package", caller.toURI())?.canonicalFile)
        assertEquals(nestedShared.canonicalFile, resolver.resolve("shared", parentEntry.toURI())?.canonicalFile)
        assertTrue(rootShared.isFile)
    }

    @Test
    fun rhinoRejectsAnEsmOnlyPackageWithThePublicStableCode() {
        val root = temporaryFolder.newFolder("rhino-esm-only")
        val caller = File(root, "main.js").apply { writeText("module.exports = {};\n") }
        val packageDirectory = File(root, "node_modules/esm-only").apply { mkdirs() }
        val manifest = File(packageDirectory, "package.json").apply {
            writeText(
                """{"name":"esm-only","type":"module","exports":{".":{"import":"./index.mjs"}}}""",
            )
        }
        File(packageDirectory, "index.mjs").writeText("export const value = 42;\n")
        val resolver = TypeScriptRhinoDependencyResolver(
            root,
            mapOf(manifest.canonicalPath to manifest.readText()),
        )

        val failure = assertThrows(TypeScriptRuntimeDependencyException::class.java) {
            resolver.resolve("esm-only", caller.toURI())
        }

        assertEquals(TypeScriptR2Contract.ERROR_RHINO_ESM_ONLY_DEPENDENCY, failure.code)
        assertTrue(failure.message.orEmpty().contains("Node profile"))
    }

    @Test
    fun realPackageMatrixSelectsCommonJsEntriesAndRejectsEsmOnlyReleases() {
        val root = temporaryFolder.newFolder("t2-real-package-matrix")
        val caller = File(root, "main.js").apply { writeText("module.exports = {};\n") }
        val manifests = linkedMapOf<String, String>()

        fun installPackage(name: String, manifestSource: String, vararg entries: String): File {
            val directory = File(root, "node_modules/$name").apply { mkdirs() }
            val manifest = File(directory, "package.json").apply { writeText(manifestSource) }
            manifests[manifest.canonicalPath] = manifestSource
            entries.forEach { relativePath ->
                File(directory, relativePath).apply {
                    parentFile?.mkdirs()
                    writeText("module.exports = {};\n")
                }
            }
            return directory
        }

        val dayjs = installPackage(
            "dayjs",
            """{"name":"dayjs","version":"1.11.13","main":"dayjs.min.js","types":"index.d.ts"}""",
            "dayjs.min.js",
        )
        val lodash = installPackage(
            "lodash",
            """{"name":"lodash","version":"4.17.21","main":"lodash.js"}""",
            "lodash.js",
        )
        val zod = installPackage(
            "zod",
            """{"name":"zod","version":"4.1.5","type":"module","exports":{".":{"types":"./index.d.cts","import":"./index.js","require":"./index.cjs"}}}""",
            "index.cjs",
            "index.js",
        )
        installPackage(
            "uuid",
            """{"name":"uuid","version":"13.0.0","type":"module","types":"./dist/index.d.ts","exports":{".":{"node":"./dist-node/index.js","default":"./dist/index.js"}}}""",
            "dist-node/index.js",
            "dist/index.js",
        )
        installPackage(
            "nanoid",
            """{"name":"nanoid","version":"5.1.5","type":"module","types":"./index.d.ts","exports":{".":{"default":"./index.js"}}}""",
            "index.js",
        )
        val resolver = TypeScriptRhinoDependencyResolver(root, manifests)

        assertEquals(
            File(dayjs, "dayjs.min.js").canonicalFile,
            resolver.resolve("dayjs", caller.toURI())?.canonicalFile,
        )
        assertEquals(
            File(lodash, "lodash.js").canonicalFile,
            resolver.resolve("lodash", caller.toURI())?.canonicalFile,
        )
        assertEquals(
            File(zod, "index.cjs").canonicalFile,
            resolver.resolve("zod", caller.toURI())?.canonicalFile,
        )
        listOf("uuid", "nanoid").forEach { packageName ->
            val failure = assertThrows(TypeScriptRuntimeDependencyException::class.java) {
                resolver.resolve(packageName, caller.toURI())
            }
            assertEquals(TypeScriptR2Contract.ERROR_RHINO_ESM_ONLY_DEPENDENCY, failure.code)
        }
    }

    private fun dependencyLayer(
        root: File,
        vararg files: File,
    ): TypeScriptPreparedDependencyLayer {
        val descriptors = files.map { file ->
            val relative = file.canonicalPath
                .removePrefix(root.canonicalPath + File.separator)
                .replace(File.separatorChar, '/')
            val bytes = file.readBytes()
            TypeScriptPreparedDependencyLayerFile(
                archivePath = "dependencies/$relative",
                size = bytes.size.toLong(),
                sha256 = sha256(bytes),
            )
        }.sortedBy(TypeScriptPreparedDependencyLayerFile::archivePath)
        return TypeScriptPreparedDependencyLayer(
            identityKind = "content",
            identityPath = "",
            identityFingerprint = "11".repeat(32),
            inventoryFingerprint = "22".repeat(32),
            fingerprint = "33".repeat(32),
            files = descriptors,
            totalBytes = descriptors.sumOf(TypeScriptPreparedDependencyLayerFile::size),
            totalPathBytes = descriptors.sumOf { descriptor ->
                descriptor.archivePath.toByteArray(StandardCharsets.UTF_8).size.toLong()
            },
        )
    }
}
