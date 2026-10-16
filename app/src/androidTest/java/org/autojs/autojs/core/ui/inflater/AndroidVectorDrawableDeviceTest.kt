package org.autojs.autojs.core.ui.inflater

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * Host roadmap `docs/dev/android-xml-attributes-roadmap.md` A8 on a device: each test vector is inflated twice, by
 * the platform from the copy aapt compiled into the test APK (`androidTest/res/drawable`) and by the host from the
 * identical source text in a script project (`androidTest/assets/ui/vector`), drawn at a square and a non-square size,
 * and compared pixel by pixel (premultiplied): at most 1 % of the pixels may differ by more than 32 in a channel, and the
 * mean difference stays at most 3 (from API 31; 5 % and 6 before it, whose renderer draws clip paths without
 * anti-aliasing and softer edges at a non-square size).
 *
 * zh-CN: 宿主路线图 A8 的设备验证: 每个测试矢量图分别由平台 (aapt 编译进测试 APK 的副本) 与宿主 (脚本工程中相同的源文本) 创建, 按方形与非方形尺寸绘制并逐像素比较.
 */
class AndroidVectorDrawableDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testContext = instrumentation.context
    private val context = instrumentation.targetContext

    @Test
    fun pathCommandsArcsAndGroupsMatchThePlatform() = compare("ui_vector_paths")

    @Test
    fun strokesAndTrimmedPathsMatchThePlatform() = compare("ui_vector_stroke")

    @Test
    fun clipPathsFillTypeAlphaAndTintMatchThePlatform() = compare("ui_vector_clip")

    @Test
    fun inlineGradientsMatchThePlatform() = compare("ui_vector_gradient")

    private fun compare(name: String) {
        val id = testContext.resources.getIdentifier(name, "drawable", testContext.packageName)
        assertTrue("$name is not in the test APK", id != 0)
        val platform = testContext.getDrawable(id)!!
        assertEquals("VectorDrawable", platform.javaClass.simpleName)
        val directory = File(context.cacheDir, "vector-${UUID.randomUUID()}")
        val file = File(directory, "res/drawable/$name.xml").apply {
            parentFile!!.mkdirs()
            writeText(testContext.assets.open("ui/vector/$name.xml").use { it.readBytes().toString(Charsets.UTF_8) })
        }
        try {
            val resolver = AndroidResourceResolver()
            resolver.configure(file, null, ResourceConfiguration.of(context))
            val host = resolver.resolveDrawable(context, "@drawable/$name")!!
            assertEquals("ScriptVectorDrawable", host.javaClass.simpleName)
            assertEquals(platform.intrinsicWidth, host.intrinsicWidth)
            assertEquals(platform.intrinsicHeight, host.intrinsicHeight)
            for ((width, height) in listOf(96 to 96, 128 to 80)) {
                // Fresh drawables for each size: the platform keeps a bitmap cache per instance.
                val expected = render(testContext.getDrawable(id)!!, width, height)
                val actual = render(resolver.resolveDrawable(context, "@drawable/$name")!!, width, height)
                val (differing, mean) = difference(expected, actual)
                // Before API 31 the platform renderer differs in edges only: its clip paths are not anti-aliased and
                // its edges are softer at a non-square size. A geometry error still exceeds the looser bounds.
                val strict = Build.VERSION.SDK_INT >= 31
                val maxDiffering = width * height / if (strict) 100 else 20
                val maxMean = if (strict) 3.0 else 6.0
                if (differing > maxDiffering || mean > maxMean) {
                    // Kept for a look: run-as org.autojs.autojs6 ls files/vector-diff
                    val out = File(context.filesDir, "vector-diff").apply { mkdirs() }
                    File(out, "$name-${width}x$height-platform.png").outputStream().use { expected.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    File(out, "$name-${width}x$height-host.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                assertTrue(
                    "$name at ${width}x$height: $differing pixels differ by more than 32 (in ${differingBox(expected, actual)}), mean difference ${"%.2f".format(mean)}",
                    differing <= maxDiffering && mean <= maxMean,
                )
                // Something was drawn at all.
                assertTrue("$name at ${width}x$height is empty", (0 until width * height).any { Color.alpha(expected.getPixel(it % width, it / width)) > 0 })
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun render(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** Pixels whose largest premultiplied channel difference exceeds 32, and the mean of those differences over all pixels. */
    private fun difference(a: Bitmap, b: Bitmap): Pair<Int, Double> {
        val width = a.width
        val height = a.height
        val pa = IntArray(width * height).also { a.getPixels(it, 0, width, 0, 0, width, height) }
        val pb = IntArray(width * height).also { b.getPixels(it, 0, width, 0, 0, width, height) }
        var differing = 0
        var total = 0L
        for (i in pa.indices) {
            val d = max(max(channel(pa[i], pb[i], 24), channel(pa[i], pb[i], 16)), max(channel(pa[i], pb[i], 8), channel(pa[i], pb[i], 0)))
            if (d > 32) differing++
            total += d
        }
        return differing to total.toDouble() / pa.size
    }

    /** The bounding box of the pixels that differ by more than 32, as `left,top-right,bottom`. */
    private fun differingBox(a: Bitmap, b: Bitmap): String {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val pa = a.getPixel(x, y)
            val pb = b.getPixel(x, y)
            if (listOf(24, 16, 8, 0).maxOf { channel(pa, pb, it) } > 32) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        return if (right < 0) "nothing" else "$left,$top-$right,$bottom"
    }

    private fun channel(x: Int, y: Int, shift: Int): Int {
        fun premultiplied(pixel: Int): Int {
            val value = pixel ushr shift and 0xff
            return if (shift == 24) value else value * (pixel ushr 24) / 255
        }
        return abs(premultiplied(x) - premultiplied(y))
    }
}
