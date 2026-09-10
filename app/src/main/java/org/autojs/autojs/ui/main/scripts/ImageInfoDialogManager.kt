package org.autojs.autojs.ui.main.scripts

import android.content.Context
import android.content.DialogInterface
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.text.format.DateFormat
import com.afollestad.materialdialogs.DialogAction
import java.io.File
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.ui.dialog.InfoDialog
import org.autojs.autojs6.R

object ImageInfoDialogManager {

    @JvmStatic
    @JvmOverloads
    fun showImageInfoDialog(
        context: Context,
        imageFile: File,
        onDismissListener: DialogInterface.OnDismissListener? = null,
    ): InfoDialog {
        val infoDialog = InfoDialog(context)
        val items = infoDialog.items.apply {
            addItem(R.string.apk_info_format, id = "format")
            addItem(R.string.media_info_resolution_label, id = "resolution")
            addItem(R.string.media_info_file_size_label, id = "fileSize")
            addItem(R.string.image_info_modified_label, id = "modified")
            addItem(R.string.image_info_orientation_label, id = "orientation")
        }
        val scope = infoDialog.scope
        var detailedInfo = ""
        val dialog = infoDialog.builder
            .title(imageFile.name)
            .autoDismiss(false)
            .iconRes(R.drawable.ic_image_black_48dp)
            .limitIconToDefaultSize()
            .neutralText("EXIF")
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ ->
                if (detailedInfo.isNotBlank()) DisplayExifInfoActivity.launch(context, detailedInfo)
            }
            .negativeText(R.string.text_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { materialDialog, _ -> materialDialog.dismiss() }
            .let { infoDialog.show(onDismissListener) }

        dialog.getActionButton(DialogAction.NEUTRAL).apply {
            isEnabled = false
            setTextColor(context.getColor(R.color.dialog_button_unavailable))
        }

        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { readMetadata(context, imageFile) }
            }
            result.onSuccess { metadata ->
                items["format"].setValueIfAbsent(metadata.format)
                items["resolution"].setValueIfAbsent(metadata.resolution)
                items["fileSize"].setValueIfAbsent(metadata.fileSize)
                items["modified"].setValueIfAbsent(metadata.modified)
                items["orientation"].setValueIfAbsent(metadata.orientation)

                detailedInfo = metadata.detailedInfo
                dialog.getActionButton(DialogAction.NEUTRAL).apply {
                    isEnabled = detailedInfo.isNotBlank()
                    setTextColor(
                        context.getColor(
                            if (isEnabled) R.color.dialog_button_hint else R.color.dialog_button_unavailable,
                        ),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                listOf(
                    items["format"],
                    items["resolution"],
                    items["fileSize"],
                    items["modified"],
                    items["orientation"],
                ).forEach { it.setValueIfAbsent(null) }
            }
        }
        return infoDialog
    }

    private fun readMetadata(context: Context, file: File): ImageMetadata {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Unable to decode image" }
        val exif = runCatching { ExifInterface(file.path) }.getOrNull()
        val orientation = exif
            ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL
        val format = options.outMimeType
            ?.substringAfter('/', "")
            ?.uppercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)
            ?: file.extension.uppercase(Locale.ROOT).ifBlank { context.getString(R.string.text_unknown) }
        val modified = DateFormat.getMediumDateFormat(context).format(Date(file.lastModified())) + " " +
                DateFormat.getTimeFormat(context).format(Date(file.lastModified()))
        val exifEntries = EXIF_TAGS.mapNotNull { tag ->
            exif?.getAttribute(tag)?.takeIf(String::isNotBlank)?.let { tag to it }
        }
        val resolution = "${options.outWidth} x ${options.outHeight}"
        val orientationText = orientationText(orientation)
        return ImageMetadata(
            format = format,
            resolution = resolution,
            fileSize = PFiles.formatSizeWithUnit(file.length()),
            modified = modified,
            orientation = orientationText,
            detailedInfo = buildDetailedInfo(
                file = file,
                format = format,
                resolution = resolution,
                fileSize = PFiles.formatSizeWithUnit(file.length()),
                modified = modified,
                orientation = orientationText,
                exifEntries = exifEntries,
            ),
        )
    }

    private fun buildDetailedInfo(
        file: File,
        format: String,
        resolution: String,
        fileSize: String,
        modified: String,
        orientation: String,
        exifEntries: List<Pair<String, String>>,
    ): String = buildString {
        appendLine("# EXIF")
        appendLine()
        appendLine("General")
        appendLine("Complete name : ${file.path}")
        appendLine("Format : $format")
        appendLine("File size : $fileSize")
        appendLine("Resolution : $resolution")
        appendLine("Modified : $modified")
        appendLine("Orientation : $orientation")
        if (exifEntries.isNotEmpty()) {
            appendLine()
            appendLine("EXIF")
            val labelWidth = exifEntries.maxOf { (tag, _) -> tag.length }
            exifEntries.forEach { (tag, value) ->
                appendLine("${tag.padEnd(labelWidth)} : $value")
            }
        }
    }.trimEnd()

    private fun orientationText(orientation: Int): String = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "0° + flip"
        ExifInterface.ORIENTATION_ROTATE_180 -> "180°"
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> "180° + flip"
        ExifInterface.ORIENTATION_TRANSPOSE -> "90° + flip"
        ExifInterface.ORIENTATION_ROTATE_90 -> "90°"
        ExifInterface.ORIENTATION_TRANSVERSE -> "270° + flip"
        ExifInterface.ORIENTATION_ROTATE_270 -> "270°"
        else -> "0°"
    }

    private data class ImageMetadata(
        val format: String,
        val resolution: String,
        val fileSize: String,
        val modified: String,
        val orientation: String,
        val detailedInfo: String,
    )

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_IMAGE_LENGTH,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
    )
}
