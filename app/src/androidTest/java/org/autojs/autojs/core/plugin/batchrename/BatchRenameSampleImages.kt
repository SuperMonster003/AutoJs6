package org.autojs.autojs.core.plugin.batchrename

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Small images with chosen EXIF and XMP for the metadata tests (plugin roadmap P6.4). The XMP packet is ASCII, since
 * ExifInterface writes a string tag as ASCII bytes.
 * zh-CN: 供元数据测试使用的带指定 EXIF 与 XMP 的小图片 (插件路线图 P6.4). XMP 包为 ASCII, 因为 ExifInterface 以 ASCII 字节写入字符串标签.
 */
internal object BatchRenameSampleImages {

    fun xmp(creator: String?, rights: String?): String = buildString {
        append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">")
        append("<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
        creator?.let { append("<dc:creator><rdf:Seq><rdf:li>").append(it).append("</rdf:li></rdf:Seq></dc:creator>") }
        rights?.let { append("<dc:rights><rdf:Alt><rdf:li xml:lang=\"x-default\">").append(it).append("</rdf:li></rdf:Alt></dc:rights>") }
        append("</rdf:Description></rdf:RDF></x:xmpmeta>")
    }

    @Suppress("DEPRECATION")
    fun write(
        file: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        exif: Map<String, String> = emptyMap(),
        xmp: String? = null,
        latLong: Pair<Double, Double>? = null,
        altitude: Double? = null,
    ): File {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 120, 200)) }
        file.outputStream().use { bitmap.compress(format, 90, it) }
        if (exif.isNotEmpty() || xmp != null || latLong != null || altitude != null) {
            ExifInterface(file).apply {
                exif.forEach { (tag, value) -> setAttribute(tag, value) }
                xmp?.let { setAttribute(ExifInterface.TAG_XMP, it) }
                latLong?.let { (latitude, longitude) -> setLatLong(latitude, longitude) }
                altitude?.let(::setAltitude)
                saveAttributes()
            }
        }
        return file
    }
}
