package org.autojs.autojs.ui.settings

import android.content.Context
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs6.R
import kotlin.math.abs

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 20, 2026.
 */
object EditorThresholdSettings {

    private const val KIB = 1024
    private const val MIB = KIB * 1024

    val valuesBytes = intArrayOf(
        0,
        16 * KIB, 32 * KIB, 64 * KIB, 96 * KIB,
        128 * KIB, 160 * KIB, 192 * KIB, 224 * KIB, 256 * KIB,
        288 * KIB, 320 * KIB, 384 * KIB, 448 * KIB, 512 * KIB,
        640 * KIB, 768 * KIB, 896 * KIB, 1 * MIB, 2 * MIB,
        3 * MIB, 4 * MIB, 5 * MIB, 6 * MIB, 7 * MIB, 8 * MIB,
        9 * MIB, 10 * MIB, 11 * MIB, 12 * MIB, 13 * MIB,
        14 * MIB, 15 * MIB, 16 * MIB, 17 * MIB, 18 * MIB,
        19 * MIB, 20 * MIB, 25 * MIB, 30 * MIB, 35 * MIB,
        40 * MIB, 45 * MIB, 50 * MIB, 55 * MIB, 60 * MIB,
        65 * MIB, 70 * MIB, 75 * MIB, 80 * MIB, 85 * MIB,
        90 * MIB, 95 * MIB, 100 * MIB,
        -1,
    )

    fun valueAt(progress: Int): Int = valuesBytes[progress.coerceIn(0, valuesBytes.lastIndex)]

    fun progressOf(value: Int): Int {
        valuesBytes.indexOf(value).takeIf { it >= 0 }?.let { return it }

        if (value < 0) return valuesBytes.lastIndex
        if (value == 0) return 0

        val lastThresholdIndex = valuesBytes.lastIndex - 1
        if (value >= valuesBytes[lastThresholdIndex]) {
            return lastThresholdIndex
        }

        var nearestIndex = 1
        var nearestDistance = Int.MAX_VALUE
        for (i in 1..lastThresholdIndex) {
            val distance = abs(valuesBytes[i] - value)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = i
            }
        }
        return nearestIndex
    }

    fun formatCodeHighlightSummary(context: Context, threshold: Int): String = when (threshold) {
        0 -> context.getString(R.string.text_editor_code_highlight_always_disabled)
        -1 -> context.getString(R.string.text_editor_code_highlight_always_enabled)
        else -> context.getString(
            R.string.text_editor_code_highlight_threshold_enabled_for_smaller_files,
            formatSize(threshold),
        )
    }

    fun formatLargeFileModeSummary(context: Context, threshold: Int): String = when (threshold) {
        0 -> context.getString(R.string.text_editor_large_file_mode_always_enabled)
        -1 -> context.getString(R.string.text_editor_large_file_mode_always_disabled)
        else -> context.getString(
            R.string.text_editor_large_file_mode_threshold_enabled_for_larger_files,
            formatSize(threshold),
        )
    }

    @Deprecated("Use the default of EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES (docs/settings/ROADMAP.md P2.3)")
    @Suppress("UNUSED_PARAMETER")
    fun defaultCodeHighlightThreshold(context: Context): Int = EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.defaultOrNull()!!

    @Deprecated("Use the default of EditorKeys.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES (docs/settings/ROADMAP.md P2.3)")
    @Suppress("UNUSED_PARAMETER")
    fun defaultLargeFileModeThreshold(context: Context): Int = EditorKeys.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES.defaultOrNull()!!

    fun formatSize(bytes: Int): String = when {
        bytes >= MIB && bytes % MIB == 0 -> "${bytes / MIB} MiB"
        else -> "${bytes / KIB} KiB"
    }
}
