package org.autojs.autojs.rhino

import com.android.tools.r8.CompilationFailedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

/**
 * The D8 half of [ClassFileDexer] on a plain JVM: the same `com.android.tools.r8` the host bundles turns class files
 * into the `classes*.dex` layout `DexClassLoader` reads, and reports bad input as an exception rather than exiting.
 * The legacy dx half only exists for API 24 / 25 and is covered by `ClassFileDexerDeviceTest` on an API 24 emulator.
 * zh-CN: 在纯 JVM 上测试 [ClassFileDexer] 的 D8 部分: 宿主打包的同一个 `com.android.tools.r8` 把 class 文件转成
 * `DexClassLoader` 读取的 `classes*.dex` 布局, 坏输入以异常报告而不是退出进程. 旧版 dx 部分只服务 API 24 / 25,
 * 由 API 24 模拟器上的 `ClassFileDexerDeviceTest` 覆盖.
 */
class ClassFileDexerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    class Probe {
        fun value(): String = "P5_PROBE_OK"
    }

    @Test
    fun `runD8 turns a class jar into classes dex`() {
        val output = temporaryFolder.newFolder("d8")

        val dexFiles = ClassFileDexer.runD8(classJar(Probe::class.java), output, minApi = 24, runtimeLibraries = emptyList())

        assertEquals(listOf("classes.dex"), dexFiles.map { it.name })
        val bytes = dexFiles.single().readBytes()
        assertEquals("dex\n", String(bytes, 0, 4, Charsets.US_ASCII))
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains(PROBE_DESCRIPTOR))
        assertEquals(dexFiles, ClassFileDexer.dexOutputs(output))
    }

    @Test
    fun `packDexJar writes the DexClassLoader layout`() {
        val output = temporaryFolder.newFolder("d8")
        val dexFiles = ClassFileDexer.runD8(classJar(Probe::class.java), output, minApi = 24, runtimeLibraries = emptyList())
        val dexJar = File(temporaryFolder.root, "out.jar")

        ClassFileDexer.packDexJar(dexFiles, dexJar)

        ZipFile(dexJar).use { zip ->
            val entries = zip.entries().toList()
            assertEquals(listOf("classes.dex"), entries.map { it.name })
            zip.getInputStream(entries.single()).use { input ->
                assertEquals("dex\n", String(input.readNBytes(4), Charsets.US_ASCII))
            }
        }
    }

    @Test
    fun `packDexJar replaces an existing file and rejects an empty list`() {
        val dexJar = temporaryFolder.newFile("stale.jar").apply { writeText("stale") }
        val dex = temporaryFolder.newFile("classes.dex").apply { writeBytes(byteArrayOf(0x64, 0x65, 0x78, 0x0a)) }

        ClassFileDexer.packDexJar(listOf(dex), dexJar)
        ZipFile(dexJar).use { zip -> assertEquals(listOf("classes.dex"), zip.entries().toList().map { it.name }) }

        assertThrows(java.io.IOException::class.java) { ClassFileDexer.packDexJar(emptyList(), dexJar) }
    }

    @Test
    fun `runD8 reports broken input as an exception instead of exiting`() {
        val broken = File(temporaryFolder.root, "broken.jar")
        JarOutputStream(FileOutputStream(broken)).use { jar ->
            jar.putNextEntry(JarEntry("org/autojs/Broken.class"))
            jar.write(ByteArray(64) { 0x42 })
            jar.closeEntry()
        }
        val output = temporaryFolder.newFolder("d8")

        assertThrows(CompilationFailedException::class.java) {
            ClassFileDexer.runD8(broken, output, minApi = 24, runtimeLibraries = emptyList())
        }
        assertEquals(emptyList<File>(), ClassFileDexer.dexOutputs(output))
    }

    private fun classJar(vararg classes: Class<*>): File {
        val jar = File(temporaryFolder.root, "input-${System.nanoTime()}.jar")
        JarOutputStream(FileOutputStream(jar)).use { out ->
            classes.forEach { type ->
                val entry = type.name.replace('.', '/') + ".class"
                val bytes = checkNotNull(type.classLoader).getResourceAsStream(entry)?.use { it.readBytes() }
                    ?: error("Missing class file $entry")
                out.putNextEntry(JarEntry(entry))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return jar
    }

    private companion object {
        const val PROBE_DESCRIPTOR = "Lorg/autojs/autojs/rhino/ClassFileDexerTest\$Probe;"
    }

}
