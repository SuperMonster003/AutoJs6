package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View.MeasureSpec.UNSPECIFIED
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.extension.MaterialDialogExtensions.makeTextCopyable
import org.autojs.autojs.extension.MaterialDialogExtensions.setCopyableText
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MediaFileInfoDialogListItemBinding
import org.mediainfo.android.MediaInfo
import org.mediainfo.android.MediaInfo.StreamKind.AUDIO
import org.mediainfo.android.MediaInfo.StreamKind.GENERAL
import org.mediainfo.android.MediaInfo.StreamKind.VIDEO

object MediaInfoDialogManager {

    private const val MEDIA_INFO_ERROR_OPENING_FILE = "Error opening file..."

    @JvmStatic
    @SuppressLint("SetTextI18n")
    fun showMediaInfoDialog(context: Context, explorerItem: ExplorerItem) {
        val binding = MediaFileInfoDialogListItemBinding.inflate(LayoutInflater.from(context))

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
            .show()
            .apply { setOnDismissListener { scope.cancel() } }

        scope.launch {
            val mediaInfo = MediaInfo()
            val filePath = explorerItem.path

            val videoFormatDeferred = async(Dispatchers.IO) { mediaInfo(filePath, VIDEO, "Format") }
            val audioFormatDeferred = async(Dispatchers.IO) { mediaInfo(filePath, AUDIO, "Format") }
            val containerFormatDeferred = async(Dispatchers.IO) { mediaInfo(filePath, GENERAL, "Format") }
            val mediaInfoTrimmedDeferred = async(Dispatchers.IO) { mediaInfo.getMediaInfoTrimmed(explorerItem.path) }

            launch(Dispatchers.IO) {
                mediaInfo(filePath, GENERAL, "FileSize/String").let { binding.fileSizeValue.bindWith(dialog, it) }
                mediaInfo(filePath, GENERAL, "Duration/String").let { binding.durationValue.bindWith(dialog, it) }
                mediaInfo(filePath, AUDIO, "BitRate/String").let { binding.bitRateForAudioValue.bindWith(dialog, it) }
                mediaInfo(filePath, VIDEO, "BitRate/String").let { binding.bitRateForVideoValue.bindWith(dialog, it) }
                mediaInfo(filePath, GENERAL, "Track").let { binding.trackNameValue.bindWith(dialog, it) }
                mediaInfo(filePath, GENERAL, "Performer").let { binding.performerValue.bindWith(dialog, it) }
                mediaInfo(filePath, GENERAL, "Album").let { binding.albumValue.bindWith(dialog, it) }
                run {
                    val width = mediaInfo(filePath, VIDEO, "Width")
                    val height = mediaInfo(filePath, VIDEO, "Height")
                    binding.resolutionValue.bindWith(dialog, if (width.isNotEmpty() && height.isNotEmpty()) "$width × $height" else "")
                }
                mediaInfo(filePath, VIDEO, "DisplayAspectRatio/String").let { binding.aspectRatioValue.bindWith(dialog, it) }
                mediaInfo(filePath, VIDEO, "FrameRate").let { binding.frameRateValue.bindWith(dialog, it) }
            }

            when {
                videoFormatDeferred.await().isNotEmpty() -> {
                    setViewsAsVideoPlaceholder(binding)
                    dialog.setIcon(R.drawable.ic_movie)
                    dialog.getActionButton(DialogAction.POSITIVE)
                    setDialogPlayable(dialog, context, explorerItem)
                }
                audioFormatDeferred.await().isNotEmpty() -> {
                    setViewsAsMediaPlaceholder(binding)
                    dialog.setIcon(R.drawable.ic_voice_note)
                    setDialogPlayable(dialog, context, explorerItem)
                }
                explorerItem.isMediaMenu -> {
                    setViewsAsMediaMenuPlaceholder(binding)
                    dialog.setIcon(R.drawable.ic_media_menu)
                    dialog.getActionButton(DialogAction.POSITIVE)
                    setDialogOpenable(dialog, context, explorerItem)
                }
                else -> {
                    setViewsAsLeastPlaceholder(binding)
                    dialog.setIcon(R.drawable.ic_question_mark)
                    setDialogOpenable(dialog, context, explorerItem)
                }
            }

            withContext(Dispatchers.Main) {
                restoreEssentialViews(binding, context)
                updateGuidelines(binding)
                updateSplitLineVisibility(binding)
                dialog.makeTextCopyable { it.titleView }
            }

            when (val containerFormat = containerFormatDeferred.await()) {
                MEDIA_INFO_ERROR_OPENING_FILE -> {
                    dialog.setContent(MEDIA_INFO_ERROR_OPENING_FILE)
                    dialog.getActionButton(DialogAction.NEUTRAL).let {
                        it.text = "MediaInfo"
                        it.setOnClickListener { ViewUtils.showSnack(binding.root, MEDIA_INFO_ERROR_OPENING_FILE, false) }
                    }
                }
                else -> {
                    containerFormat.let { binding.containerFormatValue.bindWith(dialog, it) }
                    audioFormatDeferred.await().let { binding.audioFormatValue.bindWith(dialog, it) }
                    videoFormatDeferred.await().let { binding.videoFormatValue.bindWith(dialog, it) }
                }
            }

            updateDialogNeutralButton(dialog, context, mediaInfoTrimmedDeferred.await())
        }
    }

    private fun setDialogPlayable(dialog: MaterialDialog, context: Context, explorerItem: ExplorerItem) {
        dialog.getActionButton(DialogAction.POSITIVE).let {
            it.text = context.getString(R.string.text_play)
            it.setTextColor(context.getColor(R.color.dialog_button_attraction))
            it.setOnClickListener {
                dialog.dismiss()
                explorerItem.play(context)
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

    private fun setViewsAsVideoPlaceholder(binding: MediaFileInfoDialogListItemBinding) {
        binding.videoFormatParent.isVisible = true
        binding.bitRateForVideoParent.isVisible = true
        binding.resolutionParent.isVisible = true
        binding.aspectRatioParent.isVisible = true
        binding.frameRateParent.isVisible = true
    }

    private fun setViewsAsMediaPlaceholder(binding: MediaFileInfoDialogListItemBinding) {
        binding.audioFormatParent.isVisible = true
        binding.bitRateForAudioParent.isVisible = true
        binding.albumParent.isVisible = true
        binding.trackNameParent.isVisible = true
        binding.performerParent.isVisible = true
    }

    @Suppress("UNUSED_PARAMETER", "unused")
    private fun setViewsAsMediaMenuPlaceholder(binding: MediaFileInfoDialogListItemBinding) {
        /* Nothing to do yet. */
    }

    @Suppress("UNUSED_PARAMETER", "unused")
    private fun setViewsAsLeastPlaceholder(binding: MediaFileInfoDialogListItemBinding) {
        /* Nothing to do yet. */
    }

    private fun restoreEssentialViews(binding: MediaFileInfoDialogListItemBinding, context: Context) {
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
            it.setTextColor(context.getColor(R.color.dialog_button_hint))
            it.setOnClickListener { DisplayMediaInfoActivity.launch(context, mediaInfo) }
        }
    }

    private fun updateGuidelines(binding: MediaFileInfoDialogListItemBinding) {
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

    private fun updateSplitLineVisibility(binding: MediaFileInfoDialogListItemBinding) {
        binding.splitLine.isVisible = listOf(
            binding.bitRateForVideoParent,
            binding.resolutionParent,
            binding.aspectRatioParent,
            binding.frameRateParent,
            binding.albumParent,
            binding.trackNameParent,
            binding.performerParent,
        ).any { it.isVisible }
    }

    private suspend fun TextView.bindWith(dialog: MaterialDialog, text: String) {
        withContext(Dispatchers.Main) {
            dialog.setCopyableText(this@bindWith, text)
        }
    }

    private operator fun MediaInfo.invoke(filePath: String, streamKind: MediaInfo.StreamKind, parameter: String): String {
        return this.get(filePath, streamKind, 0, parameter)
    }

}