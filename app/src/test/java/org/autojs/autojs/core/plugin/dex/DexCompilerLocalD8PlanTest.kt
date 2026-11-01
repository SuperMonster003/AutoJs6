package org.autojs.autojs.core.plugin.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

class DexCompilerLocalD8PlanTest {

    @Test
    fun cacheIdentityCoversInputSetMinApiCompilerAndExactOrderedRuntime() {
        val baseline = DexCompilerLocalD8SemanticKey.sha256(
            inputSetSha256 = "11".repeat(32),
            minimumApi = 24,
            compilerIdentity = DexCompilerLocalD8Plan.COMPILER_IDENTITY,
            runtimeLibrarySha256 = "22".repeat(32),
        )
        val variants = listOf(
            DexCompilerLocalD8SemanticKey.sha256(
                "33".repeat(32), 24, DexCompilerLocalD8Plan.COMPILER_IDENTITY, "22".repeat(32),
            ),
            DexCompilerLocalD8SemanticKey.sha256(
                "11".repeat(32), 25, DexCompilerLocalD8Plan.COMPILER_IDENTITY, "22".repeat(32),
            ),
            DexCompilerLocalD8SemanticKey.sha256(
                "11".repeat(32), 24, "different-d8", "22".repeat(32),
            ),
            DexCompilerLocalD8SemanticKey.sha256(
                "11".repeat(32), 24, DexCompilerLocalD8Plan.COMPILER_IDENTITY, "44".repeat(32),
            ),
        )

        variants.forEach { variant -> assertNotEquals(baseline, variant) }
        val first = File("runtime-a.jar").absoluteFile
        val second = File("runtime-b.jar").absoluteFile
        val digests = mapOf(
            first to "aa".repeat(32),
            second to "bb".repeat(32),
        )
        fun fingerprint(build: String, files: List<File>) =
            DexCompilerLocalRuntimeLibraryFingerprint.sha256(build, files) { file ->
                checkNotNull(digests[file])
            }

        val runtimeBaseline = fingerprint("device/build", listOf(first, second))
        assertNotEquals(runtimeBaseline, fingerprint("device/build", listOf(second, first)))
        assertNotEquals(runtimeBaseline, fingerprint("other/build", listOf(first, second)))
    }

    private fun plan(
        program: File = File("program.jar"),
        classpath: Collection<File> = listOf(File("classpath.jar")),
        runtimeLibraries: Collection<File> = listOf(File("runtime.jar")),
    ) = DexCompilerLocalD8Plan(
        program = program,
        orderedClasspath = classpath,
        orderedRuntimeLibraries = runtimeLibraries,
        minimumApi = 24,
        inputSetSha256 = "11".repeat(32),
        runtimeLibrarySha256 = "22".repeat(32),
    )
}
