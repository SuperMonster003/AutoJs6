package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.text.format.Formatter
import android.util.Log
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.ui.dialog.InfoDialog
import org.autojs.autojs.ui.dialog.InfoDialogItems
import org.autojs.autojs6.R
import org.json.JSONObject

object MediaInfoDialogManager {

    private const val TAG = "MediaInfoDialog"
    private const val SNAPSHOT_KEY_INFORM = "inform"

    @JvmStatic
    @JvmOverloads
    @SuppressLint("SetTextI18n")
    fun showMediaInfoDialog(
        context: Context,
        explorerItem: ExplorerItem,
        builderApplier: (MaterialDialog.Builder.() -> Unit)? = null,
    ) = showMediaInfoDialog(context, explorerItem, builderApplier, null, null)

    @JvmStatic
    @SuppressLint("SetTextI18n")
    fun showMediaInfoDialog(
        context: Context,
        explorerItem: ExplorerItem,
        builderApplier: (MaterialDialog.Builder.() -> Unit)?,
        onDismissListener: DialogInterface.OnDismissListener?,
        onPlayRequested: (() -> Boolean)? = null,
    ): InfoDialog {
        val infoDialog = InfoDialog(context)
        val items = infoDialog.items.apply {
            addItem(R.string.media_info_container_format_label, id = "containerFormat")
            addItem(R.string.media_info_video_format_label, id = "videoFormat").isVisible = false
            addItem(R.string.media_info_audio_format_label, id = "audioFormat").isVisible = false
            addItem(R.string.media_info_file_size_label, id = "fileSize")
            addItem(R.string.media_info_duration_label, id = "duration")
            addItem(R.string.media_info_bit_rate_label, id = "bitRateForAudio").isVisible = false
            addSeparator(id = "splitLine").isVisible = false
            addItem(R.string.media_info_bit_rate_label, id = "bitRateForVideo").isVisible = false
            addItem(R.string.media_info_resolution_label, id = "resolution").isVisible = false
            addItem(R.string.media_info_aspect_ratio_label, id = "aspectRatio").isVisible = false
            addItem(R.string.media_info_frame_rate_label, id = "frameRate").isVisible = false
            addItem(R.string.media_info_track_name_label, id = "trackName").isVisible = false
            addItem(R.string.media_info_performer_label, id = "performer").isVisible = false
            addItem(R.string.media_info_album_label, id = "album").isVisible = false
        }

        val scope = infoDialog.scope

        val dialog = infoDialog.builder
            .title(explorerItem.name)
            .autoDismiss(false)
            .iconRes(R.drawable.transparent)
            .limitIconToDefaultSize()
            .positiveText(R.string.ellipsis_six)
            .positiveColorRes(R.color.dialog_button_unavailable)
            .negativeText(R.string.text_cancel)
            .onNegative { materialDialog, _ -> materialDialog.dismiss() }
            .neutralText(R.string.ellipsis_six)
            .neutralColorRes(R.color.dialog_button_unavailable)
            .also { builder -> builderApplier?.invoke(builder) }
            .let { infoDialog.show(onDismissListener) }

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    loadMediaInfo(context, File(explorerItem.path))
                }
                renderMediaInfo(dialog, items, context, explorerItem, result, onPlayRequested)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Unexpected MediaInfo dialog failure", error)
                renderEmergencyFailure(
                    dialog = dialog,
                    items = items,
                    context = context,
                    explorerItem = explorerItem,
                    failureMessage = MediaInfoFailureMessage.from(error),
                    onPlayRequested = onPlayRequested,
                )
            }
        }
        return infoDialog
    }

    private suspend fun loadMediaInfo(context: Context, file: File): MediaInfoLoadResult {
        val fileSizeFallback = runCatching { Formatter.formatFileSize(context, file.length()) }.getOrDefault("")
        val containerFormatFallback = file.extension.uppercase(Locale.US)
        return try {
            val snapshot = JSONObject(
                MediainfoPluginHost.snapshot(
                    context = context,
                    file = file,
                    includeInform = true,
                    includeSections = false,
                )
            )
            require(snapshot.has(SNAPSHOT_KEY_INFORM)) { "MediaInfo plugin returned an invalid snapshot" }
            val inform = snapshot.optString(SNAPSHOT_KEY_INFORM)
            require(inform.isNotBlank()) { "MediaInfo plugin returned empty information" }
            MediaInfoLoadResult(
                data = MediaInfoInformParser.parse(inform).withEssentialFallbacks(
                    fileSizeFallback = fileSizeFallback,
                    containerFormatFallback = containerFormatFallback,
                ),
                detailedInfo = MediainfoPluginHost.trimInform(inform),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "MediaInfo plugin failed; using Android media metadata fallback", error)
            val failureMessage = MediaInfoFailureMessage.from(error)
            val data = runCatching { readPlatformMediaInfo(file) }
                .onFailure { fallbackError -> Log.w(TAG, "Android media metadata fallback failed", fallbackError) }
                .getOrDefault(MediaInfoDialogData())
                .withEssentialFallbacks(
                    fileSizeFallback = fileSizeFallback,
                    containerFormatFallback = containerFormatFallback,
                )
            MediaInfoLoadResult(
                data = data,
                detailedInfo = data.asFallbackReport(failureMessage),
                failureMessage = failureMessage,
            )
        }
    }

    private fun renderMediaInfo(
        dialog: MaterialDialog,
        items: InfoDialogItems,
        context: Context,
        explorerItem: ExplorerItem,
        result: MediaInfoLoadResult,
        onPlayRequested: (() -> Boolean)?,
    ) {
        val data = result.data
        val dialogKind = data.resolveDialogKind(explorerItem.isMediaMenu)
        applyDialogKindVisibility(items, dialogKind)
        when (dialogKind) {
            MediaInfoDialogKind.VIDEO -> {
                dialog.setIcon(R.drawable.ic_movie)
                setDialogPlayable(dialog, context, explorerItem, onPlayRequested)
            }
            MediaInfoDialogKind.AUDIO -> {
                dialog.setIcon(R.drawable.ic_voice_note)
                setDialogPlayable(dialog, context, explorerItem, onPlayRequested)
            }
            MediaInfoDialogKind.MEDIA_MENU -> {
                dialog.setIcon(R.drawable.ic_media_menu)
                setDialogOpenable(dialog, context, explorerItem)
            }
            MediaInfoDialogKind.OTHER -> {
                dialog.setIcon(R.drawable.ic_question_mark)
                setDialogOpenable(dialog, context, explorerItem)
            }
        }

        val unavailable = context.getString(R.string.text_unavailable)
        items["containerFormat"].bindWith(data.containerFormat, unavailable)
        items["videoFormat"].bindWith(data.videoFormat, unavailable)
        items["audioFormat"].bindWith(data.audioFormat, unavailable)
        items["fileSize"].bindWith(data.fileSize, unavailable)
        items["duration"].bindWith(data.duration, unavailable)
        items["bitRateForAudio"].bindWith(data.audioBitRate, unavailable)
        items["bitRateForVideo"].bindWith(data.videoBitRate, unavailable)
        items["trackName"].bindWith(data.trackName, unavailable)
        items["performer"].bindWith(data.performer, unavailable)
        items["album"].bindWith(data.album, unavailable)
        items["resolution"].bindWith(
            if (data.width.isNotBlank() && data.height.isNotBlank()) "${data.width} × ${data.height}" else "",
            unavailable,
        )
        items["aspectRatio"].bindWith(data.aspectRatio, unavailable)
        items["frameRate"].bindWith(data.frameRate, unavailable)

        result.failureMessage?.let { dialog.setContent("MediaInfo: $it") }
        updateDialogNeutralButton(dialog, context, result.detailedInfo)
    }

    private fun renderEmergencyFailure(
        dialog: MaterialDialog,
        items: InfoDialogItems,
        context: Context,
        explorerItem: ExplorerItem,
        failureMessage: String,
        onPlayRequested: (() -> Boolean)?,
    ) {
        val data = MediaInfoDialogData()
        val result = MediaInfoLoadResult(
            data = data,
            detailedInfo = data.asFallbackReport(failureMessage),
            failureMessage = failureMessage,
        )
        try {
            renderMediaInfo(dialog, items, context, explorerItem, result, onPlayRequested)
        } catch (renderError: Exception) {
            Log.e(TAG, "Unable to render MediaInfo failure state", renderError)
            runCatching {

                val unavailable = context.getString(R.string.text_unavailable)
                items["containerFormat"].bindWith("", unavailable)
                items["fileSize"].bindWith("", unavailable)
                items["duration"].bindWith("", unavailable)
                dialog.setContent("MediaInfo: $failureMessage")
                updateDialogNeutralButton(dialog, context, result.detailedInfo)
            }.onFailure { finalError ->
                Log.e(TAG, "Unable to render minimal MediaInfo failure state", finalError)
            }
        }
    }

    private fun readPlatformMediaInfo(file: File): MediaInfoDialogData {
        var containerFormat = ""
        var videoFormat = ""
        var audioFormat = ""
        var duration = ""
        var audioBitRate = ""
        var videoBitRate = ""
        var trackName = ""
        var performer = ""
        var album = ""
        var width = ""
        var height = ""
        var aspectRatio = ""
        var frameRate = ""
        var hasVideo = false
        var hasAudio = false

        val extractor = try {
            MediaExtractor()
        } catch (error: Exception) {
            Log.d(TAG, "MediaExtractor fallback is unavailable", error)
            null
        }
        if (extractor != null) {
            try {
                extractor.setDataSource(file.absolutePath)
                repeat(extractor.trackCount) { trackIndex ->
                    val format = extractor.getTrackFormat(trackIndex)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    when {
                        mime.startsWith("video/") -> {
                            hasVideo = true
                            if (videoFormat.isBlank()) videoFormat = mime
                            if (width.isBlank()) width = format.number(MediaFormat.KEY_WIDTH)?.toString().orEmpty()
                            if (height.isBlank()) height = format.number(MediaFormat.KEY_HEIGHT)?.toString().orEmpty()
                            if (videoBitRate.isBlank()) videoBitRate = format.number(MediaFormat.KEY_BIT_RATE).formatBitRate()
                            if (frameRate.isBlank()) frameRate = format.number(MediaFormat.KEY_FRAME_RATE).formatFrameRate()
                        }
                        mime.startsWith("audio/") -> {
                            hasAudio = true
                            if (audioFormat.isBlank()) audioFormat = mime
                            if (audioBitRate.isBlank()) audioBitRate = format.number(MediaFormat.KEY_BIT_RATE).formatBitRate()
                        }
                    }
                }
            } catch (error: Exception) {
                Log.d(TAG, "MediaExtractor fallback could not read ${file.name}", error)
            } finally {
                try {
                    extractor.release()
                } catch (error: Exception) {
                    Log.d(TAG, "MediaExtractor fallback could not be released", error)
                }
            }
        }

        val retriever = try {
            MediaMetadataRetriever()
        } catch (error: Exception) {
            Log.d(TAG, "MediaMetadataRetriever fallback is unavailable", error)
            null
        }
        if (retriever != null) {
            try {
                retriever.setDataSource(file.absolutePath)
                containerFormat = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                duration = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    .toLongOrNull()
                    .formatDuration()
                trackName = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                performer = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                hasVideo = hasVideo || retriever.metadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO).equals("yes", ignoreCase = true)
                hasAudio = hasAudio || retriever.metadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO).equals("yes", ignoreCase = true)
                if (width.isBlank()) width = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                if (height.isBlank()) height = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (frameRate.isBlank()) {
                    frameRate = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        .toDoubleOrNull()
                        .formatFrameRate()
                }
                val totalBitRate = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    .toLongOrNull()
                    .formatBitRate()
                if (hasVideo && videoBitRate.isBlank()) {
                    videoBitRate = totalBitRate
                } else if (hasAudio && audioBitRate.isBlank()) {
                    audioBitRate = totalBitRate
                }
            } catch (error: Exception) {
                Log.d(TAG, "MediaMetadataRetriever fallback could not read ${file.name}", error)
            } finally {
                try {
                    retriever.release()
                } catch (error: Exception) {
                    Log.d(TAG, "MediaMetadataRetriever fallback could not be released", error)
                }
            }
        }

        val numericWidth = width.toIntOrNull()
        val numericHeight = height.toIntOrNull()
        if (numericWidth != null && numericHeight != null && numericWidth > 0 && numericHeight > 0) {
            val divisor = greatestCommonDivisor(numericWidth, numericHeight)
            aspectRatio = "${numericWidth / divisor}:${numericHeight / divisor}"
        }

        return MediaInfoDialogData(
            containerFormat = containerFormat,
            videoFormat = videoFormat,
            audioFormat = audioFormat,
            duration = duration,
            audioBitRate = audioBitRate,
            videoBitRate = videoBitRate,
            trackName = trackName,
            performer = performer,
            album = album,
            width = width,
            height = height,
            aspectRatio = aspectRatio,
            frameRate = frameRate,
            hasVideo = hasVideo,
            hasAudio = hasAudio,
        )
    }

    private fun MediaMetadataRetriever.metadata(key: Int): String {
        return extractMetadata(key).orEmpty().trim()
    }

    private fun MediaFormat.number(key: String): Number? {
        if (!containsKey(key)) return null
        return runCatching { getInteger(key) }.getOrNull()
            ?: runCatching { getLong(key) }.getOrNull()
            ?: runCatching { getFloat(key) }.getOrNull()
    }

    private fun Number?.formatBitRate(): String {
        val bitsPerSecond = this?.toLong()?.takeIf { it > 0L } ?: return ""
        return when {
            bitsPerSecond >= 1_000_000L -> String.format(Locale.US, "%.2f Mb/s", bitsPerSecond / 1_000_000.0)
            bitsPerSecond >= 1_000L -> String.format(Locale.US, "%.0f kb/s", bitsPerSecond / 1_000.0)
            else -> "$bitsPerSecond b/s"
        }
    }

    private fun Number?.formatFrameRate(): String {
        val framesPerSecond = this?.toDouble()?.takeIf { it > 0.0 } ?: return ""
        return when {
            framesPerSecond % 1.0 == 0.0 -> String.format(Locale.US, "%.0f FPS", framesPerSecond)
            else -> String.format(Locale.US, "%.3f FPS", framesPerSecond)
        }
    }

    private fun Long?.formatDuration(): String {
        val totalSeconds = this?.takeIf { it >= 0L }?.let { (it + 500L) / 1_000L } ?: return ""
        val hours = totalSeconds / 3_600L
        val minutes = totalSeconds % 3_600L / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private tailrec fun greatestCommonDivisor(a: Int, b: Int): Int {
        return if (b == 0) a else greatestCommonDivisor(b, a % b)
    }

    private fun setDialogPlayable(
        dialog: MaterialDialog,
        context: Context,
        explorerItem: ExplorerItem,
        onPlayRequested: (() -> Boolean)?,
    ) {
        dialog.getActionButton(DialogAction.POSITIVE).let {
            it.text = context.getString(R.string.text_play)
            it.setTextColor(context.getColor(R.color.dialog_button_attraction))
            it.setOnClickListener {
                dialog.dismiss()
                val launched = runCatching { onPlayRequested?.invoke() == true }
                    .onFailure { error ->
                        Log.w(TAG, "Primary media action failed; using external player fallback", error)
                    }
                    .getOrDefault(false)
                if (!launched) {
                    explorerItem.play(context)
                }
            }
        }
    }

    private fun setDialogOpenable(dialog: MaterialDialog, context: Context, explorerItem: ExplorerItem) {
        dialog.getActionButton(DialogAction.POSITIVE).let {
            it.text = context.getString(R.string.text_open)
            it.setTextColor(context.getColor(R.color.dialog_button_default))
            it.setOnClickListener {
                dialog.dismiss()
                explorerItem.view(context)
            }
        }
    }

    private fun applyDialogKindVisibility(
        items: InfoDialogItems,
        dialogKind: MediaInfoDialogKind,
    ) {
        items["videoFormat"].isVisible = dialogKind.showsVideoDetails
        items["bitRateForVideo"].isVisible = dialogKind.showsVideoDetails
        items["resolution"].isVisible = dialogKind.showsVideoDetails
        items["aspectRatio"].isVisible = dialogKind.showsVideoDetails
        items["frameRate"].isVisible = dialogKind.showsVideoDetails

        items["audioFormat"].isVisible = dialogKind.showsAudioDetails
        items["bitRateForAudio"].isVisible = dialogKind.showsAudioDetails
        items["album"].isVisible = dialogKind.showsAudioDetails
        items["trackName"].isVisible = dialogKind.showsAudioDetails
        items["performer"].isVisible = dialogKind.showsAudioDetails

        items.entry("splitLine").view.isVisible = dialogKind.showsSplitLine
    }

    @SuppressLint("SetTextI18n")
    private fun updateDialogNeutralButton(dialog: MaterialDialog, context: Context, mediaInfo: String) {
        dialog.getActionButton(DialogAction.NEUTRAL).let {
            it.text = "MediaInfo"
            if (mediaInfo.isBlank()) {
                it.isEnabled = false
                it.setTextColor(context.getColor(R.color.dialog_button_unavailable))
                it.setOnClickListener(null)
            } else {
                it.isEnabled = true
                it.setTextColor(context.getColor(R.color.dialog_button_hint))
                it.setOnClickListener { DisplayMediaInfoActivity.launch(context, mediaInfo) }
            }
        }
    }

    private fun InfoDialogItems.Item.bindWith(text: String, unavailable: String) {
        setValue(text.ifBlank { unavailable })
    }

    private data class MediaInfoLoadResult(
        val data: MediaInfoDialogData,
        val detailedInfo: String,
        val failureMessage: String? = null,
    )

}
