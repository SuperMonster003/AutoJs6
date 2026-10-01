package org.autojs.autojs.rhino

import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@SdkSuppress(minSdkVersion = 26)
class AndroidClassLoaderUnsafeDeviceTest {

    @Test
    fun loadedLibraryCanResolveAndInvokeUnsafeAtomicMethodHandles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "unsafe-atomics-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        try {
            // tools/sdk37/fixtures/UnsafeAtomicsProbe.java, javac --release 8, D8 --min-api 26.
            val bytes = instrumentation.context.assets.open("rhino/unsafe-atomics.dex").use { it.readBytes() }
            val input = File(root, "unsafe-atomics.dex").apply { writeBytes(bytes) }
            val loader = AndroidClassLoader(context.classLoader, File(root, "classes"), false)
            repeat(2) {
                // The static initializer uses publicLookup().findVirtual(), just like the Kotlin compiler.
                val type = loader.loadDex(input).loadClass("org.autojs.sdk37.fixture.UnsafeAtomicsProbe")
                val instance = type.getConstructor().newInstance()
                val addInt = type.getMethod("addInt", Int::class.javaPrimitiveType)
                val addLong = type.getMethod("addLong", Long::class.javaPrimitiveType)
                assertEquals(0, addInt.invoke(instance, 7))
                assertEquals(7, addInt.invoke(instance, -2))
                assertEquals(5, addInt.invoke(instance, 0))
                assertEquals(0L, addLong.invoke(instance, 4_000_000_000L))
                assertEquals(4_000_000_000L, addLong.invoke(instance, -1L))
                assertEquals(3_999_999_999L, addLong.invoke(instance, 0L))
                assertArrayEquals("Original DEX changed", bytes, input.readBytes())
            }
        } finally {
            root.walkBottomUp().forEach { it.setWritable(true, true) }
            assertTrue(root.deleteRecursively())
        }
    }
}
