package org.autojs.autojs.core.plugin.batchrename

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.batchrename.api.BatchRenameMetadataKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Reading the metadata of 2000 images the way `files.batchRename` does (plugin roadmap P6.4: four at a time on the IO
 * dispatcher). The images are small generated JPEGs with EXIF, so the time measures the EXIF parsing and file access,
 * not large camera files. The timing goes to logcat under [TAG]; the assertion only catches gross regressions.
 * zh-CN: 按 `files.batchRename` 的方式读取 2000 张图片的元数据 (插件路线图 P6.4: 在 IO 调度器上每次 4 个). 图片为生成的带 EXIF 的小
 * JPEG, 因此耗时反映 EXIF 解析与文件访问, 而非大尺寸相机文件. 耗时按 [TAG] 写入 logcat; 断言只拦截严重退化.
 */
class BatchRenameMetadataBenchmarkTest {

    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "metadata-benchmark-${System.nanoTime()}").apply { mkdirs() }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun twoThousandImagesAreReadWithinTheBudget() = runBlocking {
        val template = BatchRenameSampleImages.write(
            File(root, "template.jpg"),
            exif = mapOf(ExifInterface.TAG_MODEL to "Bench Camera", ExifInterface.TAG_DATETIME_ORIGINAL to "2026:10:15 08:30:59"),
        )
        val files = (1..COUNT).map { index -> template.copyTo(File(root, "IMG_$index.jpg")) }
        val timings = List(3) {
            measureTimeMillis {
                val values = BatchRenameScriptService.readMetadata(files)
                assertEquals(COUNT, values.count { it?.get(BatchRenameMetadataKeys.CAMERA_MODEL) == "Bench Camera" })
            }
        }
        Log.i(TAG, "metadata files=$COUNT readMs=$timings")
        assertTrue("reading took $timings ms", timings.min() < 60_000)
    }

    private companion object {
        const val COUNT = 2000
        const val TAG = "BatchRenameBenchmark"
    }
}
