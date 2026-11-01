package org.autojs.autojs.rhino

import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * The `java.nio.file` calls of the host after `desugar_jdk_libs_nio`: every call goes through `j$.nio.file` now, on API 24
 * / 25 on the desugared file system and from API 26 on a wrapper of the platform one. JVM tests do not cover this, they
 * run on the real `java.nio.file`. The exception types stay those of `java.nio.file`, which `catch` clauses of the host name.
 *
 * zh-CN: `desugar_jdk_libs_nio` 之后宿主的 `java.nio.file` 调用: 全部经 `j$.nio.file`, API 24 / 25 走脱糖的文件系统, API 26 起走平台
 * 文件系统的包装. JVM 测试覆盖不到, 它们用的是真正的 `java.nio.file`. 异常类型仍是宿主 `catch` 所写的 `java.nio.file` 类型.
 */
class JavaNioDesugarDeviceTest {

    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "java-nio-${System.nanoTime()}").apply { mkdirs() }

    @After
    fun deleteFiles() {
        root.deleteRecursively()
    }

    @Test
    fun writesReadsCopiesAndDeletes() {
        val source = File(root, "source.txt")
        val bytes = "one".toByteArray()
        Files.write(source.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        assertArrayEquals(bytes, Files.readAllBytes(source.toPath()))
        assertArrayEquals(bytes, Files.newInputStream(source.toPath()).use { it.readBytes() })

        val copy = File(root, "nested/deep/copy.txt")
        Files.createDirectories(copy.parentFile!!.toPath())
        Files.copy(source.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        assertArrayEquals(bytes, copy.readBytes())

        Files.delete(copy.toPath())
        assertFalse(copy.exists())
    }

    @Test
    fun movesWithEveryOptionTheHostUses() {
        val first = File(root, "first.txt").apply { writeText("first") }
        val second = File(root, "second.txt")
        Files.move(first.toPath(), second.toPath())
        assertEquals("first", second.readText())

        val third = File(root, "third.txt").apply { writeText("third") }
        Files.move(third.toPath(), second.toPath(), StandardCopyOption.REPLACE_EXISTING)
        assertEquals("third", second.readText())

        val atomic = File(root, "atomic.txt")
        Files.move(second.toPath(), atomic.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertEquals("third", atomic.readText())
    }

    @Test
    fun reportsThePlatformExceptionTypes() {
        val present = File(root, "present.txt").apply { writeText("present") }
        val target = File(root, "target.txt").apply { writeText("target") }
        assertThrows(FileAlreadyExistsException::class.java) { Files.move(present.toPath(), target.toPath()) }
        assertThrows(NoSuchFileException::class.java) { Files.readAllBytes(File(root, "missing.txt").toPath()) }
    }

    @Test
    fun comparesAndResolvesPaths() {
        val nested = File(root, "a/../b/c.txt")
        val normalized = nested.toPath().toAbsolutePath().normalize()
        assertEquals(File(root, "b/c.txt").path, normalized.toFile().path)
        assertTrue(normalized.startsWith(root.toPath()))
        assertFalse(root.toPath().startsWith(normalized))
    }

    @Test
    fun recognisesSymbolicLinks() {
        val target = File(root, "target.txt").apply { writeText("target") }
        val link = File(root, "link.txt")
        Os.symlink(target.path, link.path)
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertFalse(Files.isSymbolicLink(target.toPath()))
    }
}
