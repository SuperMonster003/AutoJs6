package org.autojs.autojs.core.plugin.nativealign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Uses the NDK r28c C++ runtimes checked into app/src/main/jniLibs as real 64-bit and 32-bit fixtures. */
class ElfDynamicInfoTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun runtime(abi: String) = File("src/main/jniLibs/$abi/libc++_shared.so").also {
        assertTrue("Missing fixture ${it.absolutePath}", it.isFile)
    }

    @Test
    fun readsNeededSymbolsAndAndroidIdentOf64BitLibrary() {
        val info = ElfDynamicInfo.read(runtime("arm64-v8a"))

        assertEquals(listOf("libc.so", "libm.so", "libdl.so"), info.needed)
        assertTrue(info.definedSymbols.any { "__ndk1" in it })
        assertTrue("__cxa_throw" in info.definedSymbols)
        assertTrue("free" in info.requiredSymbols)
        assertFalse(info.requiredSymbols.any { "__ndk1" in it })
        // NDK r28c ships a runtime whose ident note names the toolchain build it came from.
        assertEquals("r27-beta1", info.androidIdent?.ndkVersion)
        assertEquals(11883388L, info.androidIdent?.buildNumber)
        assertEquals(21, info.androidIdent?.apiLevel)
    }

    @Test
    fun reads32BitLibrary() {
        val info = ElfDynamicInfo.read(runtime("armeabi-v7a"))

        assertEquals(listOf("libc.so", "libm.so", "libdl.so"), info.needed)
        assertTrue(info.definedSymbols.any { "__ndk1" in it })
        assertEquals(11883388L, info.androidIdent?.buildNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonElfInput() {
        val file = temporaryFolder.newFile("not-elf.so").apply { writeText("not an ELF file") }
        ElfDynamicInfo.read(file)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedElf() {
        val bytes = runtime("arm64-v8a").readBytes().copyOf(4096)
        val file = temporaryFolder.newFile("truncated.so").apply { writeBytes(bytes) }
        ElfDynamicInfo.read(file)
    }
}
