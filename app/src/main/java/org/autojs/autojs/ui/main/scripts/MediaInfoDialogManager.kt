package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View.MeasureSpec.UNSPECIFIED
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.util.DialogUtils.makeTextCopyable
import org.autojs.autojs.util.DialogUtils.setCopyableText
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MediaFileInfoDialogItemsBinding
import org.json.JSONObject
import java.io.File
import java.util.Locale

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
    ) {
        val binding = MediaFileInfoDialogItemsBinding.inflate(LayoutInflater.from(context))

        // Create an independent Scope for the Dialog, bind its lifecycle with the Dialog.
        // zh-CN: 针对 Dialog 独立创建一个 Scope, 生命周期与 Dialog 绑定.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val dialog = MaterialDialog.Builder(context)
            .title(explorerItem.name)
            .customView(binding.root, false)
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
            .build()
            .apply {
                makeTextCopyable { titleView }
                setOnDismissListener {
                    scope.cancel()
                    onDismissListener?.onDismiss(this)
                }
            }
            .showAdaptive()

        restoreEssentialViews(binding, context)
        val loading = context.getString(R.string.ellipsis_six)
        binding.containerFormatValue.bindWith(dialog, loading, loading)
        binding.fileSizeValue.bindWith(dialog, loading, loading)
        binding.durationValue.bindWith(dialog, loading, loading)
        updateGuidelines(binding)

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    loadMediaInfo(context, File(explorerItem.path))
                }
                renderMediaInfo(dialog, binding, context, explorerItem, result, onPlayRequested)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Unexpected MediaInfo dialog failure", error)
                renderEmergencyFailure(
                    dialog = dialog,
                    binding = binding,
                    context = context,
                    explorerItem = explorerItem,
                    failureMessage = MediaInfoFailureMessage.from(error),
                    onPlayRequested = onPlayRequested,
                )
            }
        }
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
        binding: MediaFileInfoDialogItemsBinding,
        context: Context,
        explorerItem: ExplorerItem,
        result: MediaInfoLoadResult,
        onPlayRequested: (() -> Boolean)?,
    ) {
        val data = result.data
        val dialogKind = data.resolveDialogKind(explorerItem.isMediaMenu)
        applyDialogKindVisibility(binding, dialogKind)
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

        restoreEssentialViews(binding, context)
        val unavailable = context.getString(R.string.text_unavailable)
        binding.containerFormatValue.bindWith(dialog, data.containerFormat, unavailable)
        binding.videoFormatValue.bindWith(dialog, data.videoFormat, unavailable)
        binding.audioFormatValue.bindWith(dialog, data.audioFormat, unavailable)
        binding.fileSizeValue.bindWith(dialog, data.fileSize, unavailable)
        binding.durationValue.bindWith(dialog, data.duration, unavailable)
        binding.bitRateForAudioValue.bindWith(dialog, data.audioBitRate, unavailable)
        binding.bitRateForVideoValue.bindWith(dialog, data.videoBitRate, unavailable)
        binding.trackNameValue.bindWith(dialog, data.trackName, unavailable)
        binding.performerValue.bindWith(dialog, data.performer, unavailable)
        binding.albumValue.bindWith(dialog, data.album, unavailable)
        binding.resolutionValue.bindWith(
            dialog,
            if (data.width.isNotBlank() && data.height.isNotBlank()) "${data.width} × ${data.height}" else "",
            unavailable,
        )
        binding.aspectRatioValue.bindWith(dialog, data.aspectRatio, unavailable)
        binding.frameRateValue.bindWith(dialog, data.frameRate, unavailable)

        updateGuidelines(binding)
        result.failureMessage?.let { dialog.setContent("MediaInfo: $it") }
        updateDialogNeutralButton(dialog, context, result.detailedInfo)
    }

    private fun renderEmergencyFailure(
        dialog: MaterialDialog,
        binding: MediaFileInfoDialogItemsBinding,
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
            renderMediaInfo(dialog, binding, context, explorerItem, result, onPlayRequested)
        } catch (renderError: Exception) {
            Log.e(TAG, "Unable to render MediaInfo failure state", renderError)
            runCatching {
                restoreEssentialViews(binding, context)
                val unavailable = context.getString(R.string.text_unavailable)
                binding.containerFormatValue.bindWith(dialog, "", unavailable)
                binding.fileSizeValue.bindWith(dialog, "", unavailable)
                binding.durationValue.bindWith(dialog, "", unavailable)
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
        binding: MediaFileInfoDialogItemsBinding,
        dialogKind: MediaInfoDialogKind,
    ) {
        binding.videoFormatParent.isVisible = dialogKind.showsVideoDetails
        binding.bitRateForVideoParent.isVisible = dialogKind.showsVideoDetails
        binding.resolutionParent.isVisible = dialogKind.showsVideoDetails
        binding.aspectRatioParent.isVisible = dialogKind.showsVideoDetails
        binding.frameRateParent.isVisible = dialogKind.showsVideoDetails

        binding.audioFormatParent.isVisible = dialogKind.showsAudioDetails
        binding.bitRateForAudioParent.isVisible = dialogKind.showsAudioDetails
        binding.albumParent.isVisible = dialogKind.showsAudioDetails
        binding.trackNameParent.isVisible = dialogKind.showsAudioDetails
        binding.performerParent.isVisible = dialogKind.showsAudioDetails

        binding.splitLine.isVisible = dialogKind.showsSplitLine
    }

    private fun restoreEssentialViews(binding: MediaFileInfoDialogItemsBinding, context: Context) {
        binding.containerFormatLabel.text = context.getString(R.string.media_info_container_format_label)
        binding.containerFormatColon.isVisible = true
        binding.containerFormatValue.isVisible = true
        binding.fileSizeLabel.text = context.getString(R.string.media_info_file_size_label)
        binding.fileSizeColon.isVisible = true
        binding.fileSizeValue.isVisible = true
        binding.durationLabel.text = context.getString(R.string.media_info_duration_label)
        binding.durationColon.isVisible = true
        binding.durationValue.isVisible = true
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

    private fun updateGuidelines(binding: MediaFileInfoDialogItemsBinding) {
        val filteredBindings = listOf(
            binding.containerFormatLabel to binding.containerFormatGuideline,
            binding.videoFormatLabel to binding.videoFormatGuideline,
            binding.audioFormatLabel to binding.audioFormatGuideline,
            binding.fileSizeLabel to binding.fileSizeGuideline,
            binding.durationLabel to binding.durationGuideline,
            binding.bitRateForAudioLabel to binding.bitRateForAudioGuideline,
            binding.bitRateForVideoLabel to binding.bitRateForVideoGuideline,
            binding.resolutionLabel to binding.resolutionGuideline,
            binding.aspectRatioLabel to binding.aspectRatioGuideline,
            binding.frameRateLabel to binding.frameRateGuideline,
            binding.albumLabel to binding.albumGuideline,
            binding.trackNameLabel to binding.trackNameGuideline,
            binding.performerLabel to binding.performerGuideline,
        ).filter { (it.first.parent as? ConstraintLayout)?.isVisible == true }

        @Suppress("DuplicatedCode")
        val maxWidth = filteredBindings.maxOfOrNull { it.first.apply { measure(UNSPECIFIED, UNSPECIFIED) }.measuredWidth } ?: return

        filteredBindings.forEach { (_, guideline) ->
            guideline.layoutParams = (guideline.layoutParams as ConstraintLayout.LayoutParams).also {
                it.guideBegin = maxWidth
            }
        }
    }

    private fun TextView.bindWith(dialog: MaterialDialog, text: String, unavailable: String) {
        dialog.setCopyableText(this, text.ifBlank { unavailable })
    }

    private data class MediaInfoLoadResult(
        val data: MediaInfoDialogData,
        val detailedInfo: String,
        val failureMessage: String? = null,
    )

}
