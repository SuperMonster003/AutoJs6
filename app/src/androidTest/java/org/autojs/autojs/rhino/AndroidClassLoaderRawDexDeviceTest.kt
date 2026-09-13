package org.autojs.autojs.rhino

import android.os.Build
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidClassLoaderRawDexDeviceTest {
    @Test
    fun rawDexAndReusedCacheRemainIntactAfterClassLoading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "sdk37-raw-dex-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        try {
            val bytes = Base64.decode(DEX, Base64.DEFAULT)
            val input = File(root, "probe.dex").apply { writeBytes(bytes) }
            val cache = File(root, "classes")
            val loader = AndroidClassLoader(context.classLoader, cache, false)
            repeat(2) {
                val type = loader.loadDex(input).loadClass("org.autojs.sdk37.fixture.DexProbe")
                assertEquals("SDK37_DEX_OK", type.getMethod("value").invoke(null))
                assertArrayEquals("Original DEX changed", bytes, input.readBytes())
                val staged = File(cache, "safe_probe.dex")
                assertArrayEquals("ART must not overwrite its own DEX input", bytes, staged.readBytes())
                assertFalse("Staged DEX must remain read-only", staged.canWrite())
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                assertTrue(File(cache, "optimized").isDirectory)
            }
        } finally {
            root.walkBottomUp().forEach { it.setWritable(true, true) }
            assertTrue(root.deleteRecursively())
        }
    }

    private companion object {
        // tools/sdk37/fixtures/DexProbe.java, javac --release 8, D8 --min-api 24.
        val DEX = """
            ZGV4CjAzNwCmdjGtDCqEC/aJmOshGxRh7Qcummb5hhXkAgAAcAAAAHhWNBIAAAAAAAAAAFwCAAAKAAAAcAAAAAQAAACYAAAA
            AgAAAKgAAAAAAAAAAAAAAAMAAADAAAAAAQAAANgAAADsAQAA+AAAADABAAA4AQAARwEAAEoBAABeAQAAcgEAAJcBAAClAQAA
            qAEAAK8BAAADAAAABAAAAAUAAAAHAAAAAgAAAAEAAAAAAAAABwAAAAMAAAAAAAAAAAABAAAAAAACAAEAAAAAAAIAAAAIAAAA
            AgAAABEAAAAAAAAAAAAAAAEAAAAAAAAATgIAAAAAAAABAAAAAAAAACgBAAADAAAAGgAGABEAAAABAAEAAQAAACwBAAAEAAAA
            cBAAAAAADgAFAA4AAwAOAAY8aW5pdD4ADURleFByb2JlLmphdmEAAUwAEkxqYXZhL2xhbmcvT2JqZWN0OwASTGphdmEvbGFu
            Zy9TdHJpbmc7ACNMb3JnL2F1dG9qcy9zZGszNy9maXh0dXJlL0RleFByb2JlOwAMU0RLMzdfREVYX09LAAFWAAV2YWx1ZQCc
            AX5+RDh7ImJhY2tlbmQiOiJkZXgiLCJjb21waWxhdGlvbi1tb2RlIjoiZGVidWciLCJoYXMtY2hlY2tzdW1zIjpmYWxzZSwi
            bWluLWFwaSI6MjQsInNoYS0xIjoiMDg0YTg5MTI2YWUwNTk2YzU5OTE0M2RmNTdhNjU0NjQ2YWYyODMxMyIsInZlcnNpb24i
            OiI5LjIuNC1kZXYifQAAAAIAAYGABJACAQn4AQsAAAAAAAAAAQAAAAAAAAABAAAACgAAAHAAAAACAAAABAAAAJgAAAADAAAA
            AgAAAKgAAAAFAAAAAwAAAMAAAAAGAAAAAQAAANgAAAABIAAAAgAAAPgAAAADIAAAAgAAACgBAAACIAAACgAAADABAAAAIAAA
            AQAAAE4CAAAAEAAAAQAAAFwCAAA=
        """.trimIndent()
    }
}
