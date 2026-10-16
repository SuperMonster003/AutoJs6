package org.autojs.autojs.core.plugin.batchrename

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.batchrename.api.BatchRenameMetadataKeys
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * The metadata reader on real camera files the tests cannot generate (plugin roadmap P6.4): HEIF and DNG and other RAW
 * formats, copied by the maintainer to a folder of the device (instrumentation argument `batchRename.samples`, default
 * `/sdcard/samples`; the host needs all files access). Skipped when the folder is absent. One line per file goes to
 * logcat under [TAG] with the read time and the fields found; reading must never throw, and every file but those named
 * "broken" must give its size. Missing fields are logged, not failed: on 2026-10-16 each missing one was absent from the
 * file itself (the ISO of a Nikon D90 NEF lives only in the maker note, a Leica M8 records no aperture, a HEIF saved by
 * a social app carries no EXIF at all).
 *
 * zh-CN: 元数据读取器在测试无法生成的真实相机文件上的表现 (插件路线图 P6.4): HEIF, DNG 及其他 RAW 格式, 由维护者复制到设备上的文件夹
 * (instrumentation 参数 `batchRename.samples`, 默认 `/sdcard/samples`; 宿主需要所有文件访问权限). 文件夹不存在时跳过. 每个文件一行写入
 * [TAG] 日志, 含读取耗时与读到的字段; 读取不得抛出, 除名称含 "broken" 的文件外都必须读到尺寸. 缺少的字段只记录不判失败:
 * 2026-10-16 核对过, 缺少的字段在文件中本来就没有 (Nikon D90 NEF 的 ISO 只在厂商注释中, Leica M8 不记录光圈, 社交应用保存的 HEIF
 * 完全没有 EXIF).
 */
class BatchRenameMetadataSamplesDeviceTest {

    @Test
    fun realCameraFilesGiveTheirMetadata() {
        val folder = File(InstrumentationRegistry.getArguments().getString("batchRename.samples") ?: "/sdcard/samples")
        val files = folder.walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()
        assumeTrue("no samples in $folder", files.isNotEmpty())
        val sizeless = mutableListOf<String>()
        files.forEach { file ->
            var values: Map<String, Any>? = null
            val millis = measureTimeMillis { values = BatchRenameMetadataReader.read(file) }
            val found = values.orEmpty()
            val camera = listOfNotNull(found[BatchRenameMetadataKeys.CAMERA_MAKE], found[BatchRenameMetadataKeys.CAMERA_MODEL]).joinToString(" ")
            Log.i(
                TAG,
                "${file.parentFile?.name}/${file.name} ms=$millis keys=${found.size} camera=$camera " +
                    "size=${found[BatchRenameMetadataKeys.WIDTH]}x${found[BatchRenameMetadataKeys.HEIGHT]} " +
                    "taken=${found[BatchRenameMetadataKeys.DATE_TAKEN]} iso=${found[BatchRenameMetadataKeys.ISO]} " +
                    "f=${found[BatchRenameMetadataKeys.F_NUMBER]} exposure=${found[BatchRenameMetadataKeys.EXPOSURE_TIME]} " +
                    "focal=${found[BatchRenameMetadataKeys.FOCAL_LENGTH]} orientation=${found[BatchRenameMetadataKeys.ORIENTATION]} " +
                    "gps=${found.containsKey(BatchRenameMetadataKeys.LATITUDE)} keyNames=${found.keys.sorted()}",
            )
            val sized = found.containsKey(BatchRenameMetadataKeys.WIDTH) && found.containsKey(BatchRenameMetadataKeys.HEIGHT)
            if (!sized && !file.name.contains("broken", ignoreCase = true)) sizeless += file.name
        }
        assertTrue("files without a size: $sizeless", sizeless.isEmpty())
    }

    private companion object {
        const val TAG = "BatchRenameSamples"
    }
}
