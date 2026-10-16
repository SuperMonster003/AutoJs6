package org.autojs.autojs.core.plugin.batchrename

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Xml
import androidx.exifinterface.media.ExifInterface
import org.autojs.plugin.batchrename.api.BatchRenameMetadataKeys
import org.autojs.plugin.batchrename.api.BatchRenameValues
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.Locale
import kotlin.math.pow

/**
 * Reads the photo metadata of the Batch Rename metadata variables (plugin roadmap D34): EXIF through
 * `androidx.exifinterface` (JPEG, PNG, WebP, HEIF, DNG and other RAW formats), and the XMP packet for the author and the
 * copyright when EXIF lacks them. Values stay raw ([BatchRenameMetadataKeys]); the plugin formats them. Shared by the
 * `files.batchRename` script path and Explorer Action v26 sessions. Never throws: an unreadable file has no metadata.
 *
 * zh-CN: 读取 Batch Rename 元数据变量所需的照片元数据 (插件路线图 D34): 经 `androidx.exifinterface` 读取 EXIF (JPEG, PNG, WebP, HEIF,
 * DNG 及其他 RAW 格式), EXIF 缺作者与版权时读取 XMP 包. 值保持原始形式 ([BatchRenameMetadataKeys]), 由插件格式化. 由
 * `files.batchRename` 脚本路径与 Explorer Action v26 会话共用. 从不抛出: 无法读取的文件没有元数据.
 */
object BatchRenameMetadataReader {

    /** Characters kept of one text value. zh-CN: 单个文本值保留的字符数. */
    const val MAX_TEXT_LENGTH = 256

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "jpe", "png", "webp", "heic", "heif", "dng", "cr2", "nef", "nrw", "arw", "rw2", "orf", "pef", "srw", "raf", "raw",
    )

    fun isImage(file: File): Boolean = file.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS

    /** The raw values of [file], or null when it is not an image this reader understands. zh-CN: [file] 的原始值. */
    fun read(file: File): Map<String, Any>? {
        if (!isImage(file) || !file.isFile) return null
        val exif = try {
            ExifInterface(file)
        } catch (_: Exception) {
            return null
        }
        val values = LinkedHashMap<String, Any>()
        fun put(key: String, value: Any?) {
            if (value != null) values[key] = value
        }
        put(BatchRenameMetadataKeys.CAMERA_MAKE, text(exif.getAttribute(ExifInterface.TAG_MAKE)))
        put(BatchRenameMetadataKeys.CAMERA_MODEL, text(exif.getAttribute(ExifInterface.TAG_MODEL)))
        put(BatchRenameMetadataKeys.LENS, text(exif.getAttribute(ExifInterface.TAG_LENS_MODEL)))
        put(BatchRenameMetadataKeys.ISO, exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 })
        put(
            BatchRenameMetadataKeys.F_NUMBER,
            positive(exif, ExifInterface.TAG_F_NUMBER)
                ?: positive(exif, ExifInterface.TAG_APERTURE_VALUE)?.let { 2.0.pow(it / 2) },
        )
        put(
            BatchRenameMetadataKeys.EXPOSURE_TIME,
            positive(exif, ExifInterface.TAG_EXPOSURE_TIME)
                ?: number(exif, ExifInterface.TAG_SHUTTER_SPEED_VALUE)?.let { 2.0.pow(-it) },
        )
        put(BatchRenameMetadataKeys.FOCAL_LENGTH, positive(exif, ExifInterface.TAG_FOCAL_LENGTH))
        if (exif.hasAttribute(ExifInterface.TAG_FLASH)) put(BatchRenameMetadataKeys.FLASH, exif.getAttributeInt(ExifInterface.TAG_FLASH, 0))
        val (width, height) = dimensions(exif, file)
        put(BatchRenameMetadataKeys.WIDTH, width)
        put(BatchRenameMetadataKeys.HEIGHT, height)
        var author = text(exif.getAttribute(ExifInterface.TAG_ARTIST))
        var copyright = text(exif.getAttribute(ExifInterface.TAG_COPYRIGHT))
        if (author == null || copyright == null) {
            val xmp = exif.getAttributeBytes(ExifInterface.TAG_XMP)?.let { String(it, Charsets.UTF_8) }
            if (xmp != null) {
                if (author == null) author = text(xmpValue(xmp, "creator"))
                if (copyright == null) copyright = text(xmpValue(xmp, "rights"))
            }
        }
        put(BatchRenameMetadataKeys.AUTHOR, author)
        put(BatchRenameMetadataKeys.COPYRIGHT, copyright)
        exif.latLong?.takeIf { it.size == 2 && it.all(Double::isFinite) }?.let { (latitude, longitude) ->
            put(BatchRenameMetadataKeys.LATITUDE, latitude)
            put(BatchRenameMetadataKeys.LONGITUDE, longitude)
        }
        if (exif.hasAttribute(ExifInterface.TAG_GPS_ALTITUDE)) {
            put(BatchRenameMetadataKeys.ALTITUDE, exif.getAltitude(Double.NaN).takeIf(Double::isFinite))
        }
        put(
            BatchRenameMetadataKeys.DATE_TAKEN,
            dateTime(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)) ?: dateTime(exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)),
        )
        put(BatchRenameMetadataKeys.EXPOSURE_BIAS, number(exif, ExifInterface.TAG_EXPOSURE_BIAS_VALUE))
        put(BatchRenameMetadataKeys.ORIENTATION, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0).takeIf { it in 1..8 })
        put(BatchRenameMetadataKeys.COLOR_SPACE, colorSpace(exif))
        return values
    }

    /** [values] as a Bundle with String, Int and Double entries. zh-CN: 以 String, Int 与 Double 条目表示的 Bundle. */
    fun toBundle(values: Map<String, Any>): Bundle = Bundle().apply {
        values.forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Double -> putDouble(key, value)
            }
        }
    }

    private fun text(value: String?): String? = value
        ?.filter { it >= ' ' && it.code != 0x7f }
        ?.trim()
        ?.take(MAX_TEXT_LENGTH)
        ?.takeIf { it.isNotEmpty() }

    private fun number(exif: ExifInterface, tag: String): Double? =
        if (exif.hasAttribute(tag)) exif.getAttributeDouble(tag, Double.NaN).takeIf(Double::isFinite) else null

    private fun positive(exif: ExifInterface, tag: String): Double? = number(exif, tag)?.takeIf { it > 0 }

    /** EXIF pixel dimensions, otherwise the image header. zh-CN: EXIF 中的像素尺寸, 否则读取图片头. */
    private fun dimensions(exif: ExifInterface, file: File): Pair<Int?, Int?> {
        val width = exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 }
            ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
        val height = exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 }
            ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
        if (width != null && height != null) return width to height
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.path, options) }
        return (options.outWidth.takeIf { it > 0 } ?: width) to (options.outHeight.takeIf { it > 0 } ?: height)
    }

    /** EXIF `yyyy:MM:dd HH:mm:ss` as `yyyy-MM-ddTHH:mm:ss`, or null. zh-CN: 把 EXIF 日期时间转为 ISO 本地形式. */
    internal fun dateTime(value: String?): String? {
        val match = DATE_TIME.matchEntire(value?.trim() ?: return null) ?: return null
        val (year, month, day, hour, minute, second) = match.destructured
        if (year == "0000" || month.toInt() !in 1..12 || day.toInt() !in 1..31 || hour.toInt() > 23 || minute.toInt() > 59 || second.toInt() > 60) {
            return null
        }
        return "$year-$month-${day}T$hour:$minute:$second"
    }

    private const val DUBLIN_CORE = "http://purl.org/dc/elements/1.1/"

    private val DATE_TIME = Regex("([0-9]{4})[:-]([0-9]{2})[:-]([0-9]{2})[ T]([0-9]{2}):([0-9]{2}):([0-9]{2}).*")

    /**
     * EXIF ColorSpace: 1 is sRGB; Adobe RGB is 2 or, by the DCF rule, uncalibrated with the interoperability index R03.
     * zh-CN: EXIF ColorSpace: 1 为 sRGB; Adobe RGB 为 2, 或按 DCF 规则为未校准且互操作索引为 R03.
     */
    private fun colorSpace(exif: ExifInterface): String? {
        if (!exif.hasAttribute(ExifInterface.TAG_COLOR_SPACE)) return null
        return when (exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, 0)) {
            1 -> BatchRenameValues.COLOR_SPACE_SRGB
            2 -> BatchRenameValues.COLOR_SPACE_ADOBE_RGB
            65535 -> if (exif.getAttribute(ExifInterface.TAG_INTEROPERABILITY_INDEX)?.trim() == "R03") {
                BatchRenameValues.COLOR_SPACE_ADOBE_RGB
            } else {
                BatchRenameValues.COLOR_SPACE_UNCALIBRATED
            }
            else -> null
        }
    }

    /**
     * The first `rdf:li` text below the Dublin Core property [element] (`creator` is a sequence, `rights` a language
     * alternative), or the property's own text; null for a packet that does not parse. The parser of [Xml] resolves
     * namespaces, so the property is matched by its namespace and local name, whatever prefix the packet uses.
     * zh-CN: Dublin Core 属性 [element] 之下第一个 `rdf:li` 的文本 (`creator` 为序列, `rights` 为多语言替代), 或该属性自身的文本;
     * 无法解析的包为 null. [Xml] 的解析器会解析命名空间, 因此按命名空间与本地名匹配属性, 与包中使用的前缀无关.
     */
    internal fun xmpValue(xmp: String, element: String): String? = try {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xmp))
        var depth = -1
        var result: String? = null
        while (result == null) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> if (depth < 0 && parser.name == element && parser.namespace == DUBLIN_CORE) depth = parser.depth
                XmlPullParser.TEXT -> if (depth >= 0) result = parser.text.trim().takeIf { it.isNotEmpty() }
                XmlPullParser.END_TAG -> if (depth >= 0 && parser.depth == depth) break
            }
        }
        result
    } catch (_: Exception) {
        null
    }
}
