package org.autojs.plugin.explorer.api

/**
 * v26. Keys of the photo metadata Bundle `readMetadata` answers ([ExplorerActionHostSessionKeys.METADATA]). The host
 * reads EXIF, and XMP for the author and the copyright when EXIF lacks them, and answers raw values; a missing key
 * means the image does not record the value. The Batch Rename contract uses the same keys (`BatchRenameMetadataKeys`).
 *
 * zh-CN: v26. `readMetadata` 应答的照片元数据 Bundle 的键. 宿主读取 EXIF, EXIF 缺作者与版权时读取 XMP, 并应答原始值; 缺少某个键表示
 * 图片没有记录该值. Batch Rename 契约使用同一组键.
 */
object ExplorerPhotoMetadataKeys {

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

    /** String: `sRGB`, `AdobeRGB` or `Uncalibrated`. */
    const val COLOR_SPACE = "colorSpace"

    val ALL: List<String> = listOf(
        CAMERA_MAKE, CAMERA_MODEL, LENS, ISO, F_NUMBER, EXPOSURE_TIME, FOCAL_LENGTH, FLASH, WIDTH, HEIGHT, AUTHOR, COPYRIGHT,
        LATITUDE, LONGITUDE, ALTITUDE, DATE_TAKEN, EXPOSURE_BIAS, ORIENTATION, COLOR_SPACE,
    )
}
