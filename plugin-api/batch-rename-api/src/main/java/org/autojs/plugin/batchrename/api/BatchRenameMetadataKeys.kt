package org.autojs.plugin.batchrename.api

/**
 * Keys of the photo metadata Bundle of [BatchRenameItemKeys.METADATA] ([BatchRenameValues.FEATURE_METADATA]). The host
 * reads EXIF, and XMP for the author and the copyright when EXIF lacks them, and sends raw values; the plugin formats
 * them for the variables (plugin roadmap D34, D47-D50). A missing key means the image does not record the value.
 * Explorer Action v26 sessions answer the same keys (`ExplorerPhotoMetadataKeys`).
 *
 * zh-CN: [BatchRenameItemKeys.METADATA] 中照片元数据 Bundle 的键. 宿主读取 EXIF, EXIF 缺作者与版权时读取 XMP, 并发送原始值; 由插件为
 * 变量格式化 (插件路线图 D34, D47-D50). 缺少某个键表示图片没有记录该值. Explorer Action v26 会话应答同一组键.
 */
object BatchRenameMetadataKeys {

    /** String: camera manufacturer (EXIF Make). */
    const val CAMERA_MAKE = "cameraMake"

    /** String: camera model (EXIF Model). */
    const val CAMERA_MODEL = "cameraModel"

    /** String: lens model (EXIF LensModel). */
    const val LENS = "lens"

    /** Int: ISO sensitivity. */
    const val ISO = "iso"

    /** Double: f-number, 2.8 for f/2.8. */
    const val F_NUMBER = "fNumber"

    /** Double: exposure time in seconds. */
    const val EXPOSURE_TIME = "exposureTime"

    /** Double: focal length in millimeters. */
    const val FOCAL_LENGTH = "focalLength"

    /** Int: EXIF Flash bit field; bit 0 tells whether the flash fired. */
    const val FLASH = "flash"

    /** Int: width in pixels as stored, before the orientation applies. */
    const val WIDTH = "width"

    /** Int: height in pixels as stored, before the orientation applies. */
    const val HEIGHT = "height"

    /** String: author (EXIF Artist, otherwise XMP dc:creator). */
    const val AUTHOR = "author"

    /** String: copyright (EXIF Copyright, otherwise XMP dc:rights). */
    const val COPYRIGHT = "copyright"

    /** Double: latitude in degrees, negative south of the equator. */
    const val LATITUDE = "latitude"

    /** Double: longitude in degrees, negative west of Greenwich. */
    const val LONGITUDE = "longitude"

    /** Double: altitude in meters, negative below sea level. */
    const val ALTITUDE = "altitude"

    /** String: local date and time the photo was taken, `yyyy-MM-ddTHH:mm:ss`, as recorded (no time zone conversion). */
    const val DATE_TAKEN = "dateTaken"

    /** Double: exposure bias in EV. */
    const val EXPOSURE_BIAS = "exposureBias"

    /** Int: EXIF Orientation, 1 to 8. */
    const val ORIENTATION = "orientation"

    /** String: one of the `COLOR_SPACE_*` values of [BatchRenameValues]. */
    const val COLOR_SPACE = "colorSpace"

    val ALL: List<String> = listOf(
        CAMERA_MAKE, CAMERA_MODEL, LENS, ISO, F_NUMBER, EXPOSURE_TIME, FOCAL_LENGTH, FLASH, WIDTH, HEIGHT, AUTHOR, COPYRIGHT,
        LATITUDE, LONGITUDE, ALTITUDE, DATE_TAKEN, EXPOSURE_BIAS, ORIENTATION, COLOR_SPACE,
    )
}
