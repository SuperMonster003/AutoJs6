package org.autojs.autojs.apkbuilder.template

import org.autojs.autojs.apkbuilder.template.PackagedNativeLibraryPolicy.Candidate
import org.autojs.autojs.apkbuilder.template.PackagedNativeLibraryPolicy.Failure
import org.autojs.autojs.apkbuilder.template.PackagedNativeLibraryPolicy.Source
import org.autojs.autojs.core.plugin.nativealign.ElfDynamicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PackagedNativeLibraryPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val host = Source("AutoJs6", "org.autojs.autojs6", "6.8.0", isHost = true)
    private val openCv = Source("OpenCV", "io.github.supermonster003.autojs6.plugin.opencv", "1.1.4")
    private val paddle = Source("Paddle OCR", "io.github.supermonster003.autojs6.plugin.paddleocr.v3", "1.0.5")
    private val rapid = Source("Rapid OCR", "io.github.supermonster003.autojs6.plugin.rapidocr.ppocrv6", "1.0.6")
    private val paddleOnnx = Source("Paddle OCR", "io.github.supermonster003.autojs6.plugin.paddleocr.v3", "2.0.0")

    private val elf = HashMap<File, ElfDynamicInfo>()

    private fun candidate(
        name: String,
        source: Source,
        sha256: String,
        abi: String = "arm64-v8a",
        info: ElfDynamicInfo? = null,
    ): Candidate {
        val file = temporaryFolder.newFile()
        info?.let { elf[file] = it }
        return Candidate(abi, name, source, sha256, file)
    }

    private fun elfInfo(
        needed: List<String> = emptyList(),
        defined: Set<String> = emptySet(),
        required: Set<String> = emptySet(),
        build: String? = null,
    ) = ElfDynamicInfo(needed, defined, required, build?.let { ElfDynamicInfo.AndroidIdent(21, "r28c", it) })

    private fun resolve(vararg candidates: Candidate) = PackagedNativeLibraryPolicy.resolve(candidates.toList()) { elf[it] }

    @Test
    fun identicalLibrariesArePackagedOnceWithEveryOwner() {
        val fromOpenCv = candidate("libopencv_java4.so", openCv, "aa")
        val fromRapid = candidate("libopencv_java4.so", rapid, "aa")

        val selected = resolve(fromOpenCv, fromRapid).single()

        assertSame(fromOpenCv.file, selected.file)
        assertEquals(listOf(openCv, rapid), selected.sources)
        assertEquals("lib/arm64-v8a/libopencv_java4.so", selected.entryName)
    }

    @Test
    fun differentContentUnderOneNameStopsTheBuild() {
        try {
            resolve(candidate("libopencv_java4.so", openCv, "aa"), candidate("libopencv_java4.so", rapid, "bb"))
            fail("Expected a conflict")
        } catch (conflict: Failure.Conflict) {
            assertEquals("lib/arm64-v8a/libopencv_java4.so", conflict.entryName)
            assertEquals(openCv, conflict.first)
            assertEquals(rapid, conflict.second)
        }
    }

    @Test
    fun onnxRuntimeSharedByRapidOcrAndPaddleOcrIsPackagedOnce() {
        // Both plugins take libonnxruntime.so from the same onnxruntime-android release; only the
        // ONNX Runtime Paddle OCR plugin ships the Java binding libonnxruntime4j_jni.so.
        val fromRapid = candidate("libonnxruntime.so", rapid, "ort")
        val fromPaddle = candidate("libonnxruntime.so", paddleOnnx, "ort")
        val jni = candidate("libonnxruntime4j_jni.so", paddleOnnx, "jni")

        val selected = resolve(fromRapid, fromPaddle, jni).sortedBy { it.entryName }

        assertEquals(listOf("lib/arm64-v8a/libonnxruntime.so", "lib/arm64-v8a/libonnxruntime4j_jni.so"), selected.map { it.entryName })
        assertEquals(listOf(rapid, paddleOnnx), selected.first().sources)
        assertEquals(listOf(paddleOnnx), selected.last().sources)
    }

    @Test
    fun differentOnnxRuntimeBuildsStopTheBuild() {
        try {
            resolve(candidate("libonnxruntime.so", rapid, "ort-a"), candidate("libonnxruntime.so", paddleOnnx, "ort-b"))
            fail("Expected a conflict")
        } catch (conflict: Failure.Conflict) {
            assertEquals("lib/arm64-v8a/libonnxruntime.so", conflict.entryName)
        }
    }

    @Test
    fun abisAreResolvedIndependently() {
        val selected = resolve(
            candidate("libRapidOcr.so", rapid, "aa", abi = "arm64-v8a"),
            candidate("libRapidOcr.so", rapid, "bb", abi = "x86_64"),
        )
        assertEquals(listOf("lib/arm64-v8a/libRapidOcr.so", "lib/x86_64/libRapidOcr.so"), selected.map { it.entryName })
    }

    @Test
    fun newestCxxRuntimeWins() {
        val older = candidate("libc++_shared.so", host, "old", info = elfInfo(build = "9000000"))
        val newer = candidate("libc++_shared.so", paddle, "new", info = elfInfo(build = "11883388"))

        val selected = resolve(older, newer).single()

        assertSame(newer.file, selected.file)
        assertEquals(listOf(paddle), selected.sources)
    }

    @Test
    fun hostCxxRuntimeWinsTies() {
        val plugin = candidate("libc++_shared.so", paddle, "plugin", info = elfInfo(build = "11883388"))
        val fromHost = candidate("libc++_shared.so", host, "host", info = elfInfo(build = "11883388"))

        assertSame(fromHost.file, resolve(plugin, fromHost).single().file)
    }

    @Test
    fun libraryNeedingNewerStdSymbolsStopsTheBuild() {
        val runtime = candidate("libc++_shared.so", host, "host", info = elfInfo(defined = setOf("_ZNSt6__ndk14cout")))
        val engine = candidate(
            "libRapidOcr.so", rapid, "engine",
            info = elfInfo(
                needed = listOf("libc++_shared.so"),
                required = setOf("_ZNSt6__ndk14cout", "_ZNSt6__ndk112to_charsEPcS0_d", "free"),
            ),
        )
        try {
            resolve(runtime, engine)
            fail("Expected an outdated runtime")
        } catch (failure: Failure.CxxRuntimeTooOld) {
            assertEquals("lib/arm64-v8a/libRapidOcr.so", failure.library.entryName)
            assertEquals(listOf(host), failure.runtime.sources)
            assertEquals(listOf("_ZNSt6__ndk112to_charsEPcS0_d"), failure.missingSymbols)
        }
    }

    @Test
    fun stdSymbolsDefinedByAnotherPackagedLibraryAreAccepted() {
        val runtime = candidate("libc++_shared.so", host, "host", info = elfInfo())
        val provider = candidate("libonnxruntime.so", rapid, "ort", info = elfInfo(defined = setOf("_ZNSt6__ndk16vectorIiE4dataEv")))
        val engine = candidate(
            "libRapidOcr.so", rapid, "engine",
            info = elfInfo(needed = listOf("libc++_shared.so"), required = setOf("_ZNSt6__ndk16vectorIiE4dataEv")),
        )
        assertEquals(3, resolve(runtime, provider, engine).size)
    }

    @Test
    fun librariesWithoutTheSharedRuntimeOrUnreadableElfAreNotChecked() {
        val runtime = candidate("libc++_shared.so", host, "host", info = elfInfo())
        val staticRuntime = candidate(
            "libmlkit.so", rapid, "static",
            info = elfInfo(needed = listOf("libc.so"), required = setOf("_ZNSt6__ndk14cout")),
        )
        val unreadable = candidate("libunknown.so", rapid, "unknown")
        assertEquals(3, resolve(runtime, staticRuntime, unreadable).size)
    }

    @Test
    fun conflictMessageNamesBothSources() {
        val message = Failure.Conflict("lib/x86/libfoo.so", openCv, rapid).message.orEmpty()
        assertTrue(message, openCv.description in message && rapid.description in message)
        assertEquals("OpenCV (io.github.supermonster003.autojs6.plugin.opencv) 1.1.4", openCv.description)
    }
}
