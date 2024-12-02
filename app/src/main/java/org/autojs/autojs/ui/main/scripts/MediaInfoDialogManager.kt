package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val root = binding.root as ViewGroup

        val dialog = MaterialDialog.Builder(context)
            .title(explorerItem.name)
            .customView(root, false)
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

        CoroutineScope(Dispatchers.Main).launch {
            val mediaInfo = MediaInfo()
            val filePath = explorerItem.path

            val deferredVideoFormat = async(Dispatchers.IO) { mediaInfo(filePath, VIDEO, "Format") }
            val deferredAudioFormat = async(Dispatchers.IO) { mediaInfo(filePath, AUDIO, "Format") }
            val deferredContainerFormat = async(Dispatchers.IO) { mediaInfo(filePath, GENERAL, "Format") }
            val deferredMediaInfoTrimmed = async(Dispatchers.IO) { mediaInfo.getMediaInfoTrimmed(explorerItem.path) }

            launch(Dispatchers.IO) {
                mediaInfo(filePath, GENERAL, "FileSize/String").let {
                    binding.fileSizeValue.bindWith(it)
                }
            }
            launch(Dispatchers.IO) {
                mediaInfo(filePath, GENERAL, "Duration/String").let { binding.durationValue.bindWith(it) }
            }
            launch(Dispatchers.IO) {
                mediaInfo(filePath, GENERAL, "Album").let { binding.albumValue.bindWith(it) }
            }
            launch(Dispatchers.IO) {
                mediaInfo(filePath, GENERAL, "Track").let { binding.trackNameValue.bindWith(it) }
            }
            launch(Dispatchers.IO) {
                mediaInfo(filePath, GENERAL, "Performer").let { binding.performerValue.bindWith(it) }
            }
            launch(Dispatchers.IO) {
                mediaInfo(filePath, AUDIO, "BitRate/String").let { binding.bitRateForAudioValue.bindWith(it) }
            }

            launch(Dispatchers.IO) {
                mediaInfo(filePath, VIDEO, "BitRate/String").let { binding.bitRateForVideoValue.bindWith(it) }
            }
            launch(Dispatchers.IO) {
                mediaInfo(filePath, VIDEO, "DisplayAspectRatio/String").let { binding.aspectRatioValue.bindWith(it) }
            }
            launch(Dispatchers.IO) {
                mediaInfo(filePath, VIDEO, "FrameRate").let { binding.frameRateValue.bindWith(it) }
            }
            launch(Dispatchers.IO) {
                val deferredVideoWidth = async(Dispatchers.IO) { mediaInfo(filePath, VIDEO, "Width") }
                val deferredVideoHeight = async(Dispatchers.IO) { mediaInfo(filePath, VIDEO, "Height") }
                val (width, height) = awaitAll(deferredVideoWidth, deferredVideoHeight)
                binding.resolutionValue.bindWith(if (width.isNotEmpty() && height.isNotEmpty()) "$width × $height" else "")
            }

            when {
                deferredVideoFormat.await().isNotEmpty() -> {
                    setViewsAsVideoPlaceholder(binding)
                    dialog.setIcon(R.drawable.ic_movie)
                    dialog.getActionButton(DialogAction.POSITIVE)
                    setDialogPlayable(dialog, context, explorerItem)
                }
                deferredAudioFormat.await().isNotEmpty() -> {
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
            }

            when (val containerFormat = deferredContainerFormat.await()) {
                MEDIA_INFO_ERROR_OPENING_FILE -> {
                    dialog.setContent(MEDIA_INFO_ERROR_OPENING_FILE)
                    dialog.getActionButton(DialogAction.NEUTRAL).let {
                        it.text = "MediaInfo"
                        it.setOnClickListener { ViewUtils.showSnack(binding.root, MEDIA_INFO_ERROR_OPENING_FILE, false) }
                    }
                }
                else -> {
                    containerFormat.let { binding.containerFormatValue.bindWith(it) }
                    deferredAudioFormat.await().let { binding.audioFormatValue.bindWith(it) }
                    deferredVideoFormat.await().let { binding.videoFormatValue.bindWith(it) }
                }
            }

            updateDialogNeutralButton(dialog, context, deferredMediaInfoTrimmed.await())
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

    @Suppress("UNUSED_PARAMETER")
    private fun setViewsAsMediaMenuPlaceholder(binding: MediaFileInfoDialogListItemBinding) {
        /* Nothing to do yet. */
    }

    @Suppress("UNUSED_PARAMETER")
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

    private suspend fun TextView.bindWith(text: String) {
        withContext(Dispatchers.Main) { this@bindWith.text = text.takeUnless { text.isEmpty() } ?: context.getString(R.string.text_unknown) }
    }

    private operator fun MediaInfo.invoke(filePath: String, streamKind: MediaInfo.StreamKind, parameter: String): String {
        return this.get(filePath, streamKind, 0, parameter)
    }

}