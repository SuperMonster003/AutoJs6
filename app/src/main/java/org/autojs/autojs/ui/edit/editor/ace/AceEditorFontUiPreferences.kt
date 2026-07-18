package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import org.autojs.autojs.ui.edit.editor.ace.font.FontFeature
import org.autojs.autojs6.R

enum class AceEditorFontSortMode(
    @param:StringRes val labelRes: Int,
) {
    NAME_ASC(R.string.text_sort_by_name_asc),
    NAME_DESC(R.string.text_sort_by_name_desc),
    SIZE_ASC(R.string.text_sort_by_size_asc),
    SIZE_DESC(R.string.text_sort_by_size_desc),
    ;

    companion object {
        val DEFAULT = NAME_ASC

        fun fromStoredValue(value: String?): AceEditorFontSortMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

data class AceEditorFontPreviewFilter(
    val excludedFeatures: Set<FontFeature> = emptySet(),
) {
    fun allows(option: AceEditorFontOption): Boolean =
        option.features.none(excludedFeatures::contains)

    companion object {
        val DEFAULT = AceEditorFontPreviewFilter()
    }
}

/** Persisted presentation preferences shared by the font dialog and font previewer. */
object AceEditorFontUiPreferences {

    private const val SHARED_PREFERENCES_NAME = "ace_editor_font_ui_preferences"
    private const val KEY_SORT_MODE = "key_\$_ace_editor_font_sort_mode"
    private const val KEY_EXCLUDED_PREVIEW_FEATURES = "key_\$_ace_editor_font_preview_excluded_features"

    fun getSortMode(context: Context): AceEditorFontSortMode =
        AceEditorFontSortMode.fromStoredValue(preferences(context).getString(KEY_SORT_MODE, null))

    fun setSortMode(context: Context, mode: AceEditorFontSortMode) {
        preferences(context).edit {
            if (mode == AceEditorFontSortMode.DEFAULT) {
                remove(KEY_SORT_MODE)
            } else {
                putString(KEY_SORT_MODE, mode.name)
            }
        }
    }

    fun resetSortMode(context: Context) {
        preferences(context).edit { remove(KEY_SORT_MODE) }
    }

    fun sortedOptions(
        context: Context,
        options: List<AceEditorFontOption>,
    ): List<AceEditorFontOption> = options.sortedWith(comparator(getSortMode(context)))

    fun getPreviewFilter(context: Context): AceEditorFontPreviewFilter {
        val byCatalogValue = FontFeature.entries.associateBy(FontFeature::catalogValue)
        val excluded = preferences(context)
            .getStringSet(KEY_EXCLUDED_PREVIEW_FEATURES, emptySet())
            .orEmpty()
            .mapNotNullTo(linkedSetOf()) { byCatalogValue[it] }
        return AceEditorFontPreviewFilter(excluded)
    }

    fun setPreviewFilter(context: Context, filter: AceEditorFontPreviewFilter) {
        setExcludedFeatures(context, filter.excludedFeatures)
    }

    fun setExcludedFeatures(context: Context, features: Set<FontFeature>) {
        preferences(context).edit {
            if (features.isEmpty()) {
                remove(KEY_EXCLUDED_PREVIEW_FEATURES)
            } else {
                putStringSet(
                    KEY_EXCLUDED_PREVIEW_FEATURES,
                    features.mapTo(linkedSetOf(), FontFeature::catalogValue),
                )
            }
        }
    }

    fun resetPreviewFilter(context: Context) {
        preferences(context).edit { remove(KEY_EXCLUDED_PREVIEW_FEATURES) }
    }

    fun filteredPreviewOptions(
        context: Context,
        options: List<AceEditorFontOption>,
    ): List<AceEditorFontOption> {
        val filter = getPreviewFilter(context)
        return options.filter(filter::allows)
    }

    fun preparedPreviewOptions(
        context: Context,
        options: List<AceEditorFontOption>,
    ): List<AceEditorFontOption> = filteredPreviewOptions(context, sortedOptions(context, options))

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private fun comparator(mode: AceEditorFontSortMode): Comparator<AceEditorFontOption> {
        val nameAscending = Comparator<AceEditorFontOption> { left, right ->
            left.displayName.compareTo(right.displayName)
                .takeIf { it != 0 }
                ?: left.id.compareTo(right.id)
        }
        val nameDescending = Comparator<AceEditorFontOption> { left, right ->
            right.displayName.compareTo(left.displayName)
                .takeIf { it != 0 }
                ?: right.id.compareTo(left.id)
        }
        fun sizeComparator(descending: Boolean) = Comparator<AceEditorFontOption> { left, right ->
            val leftSize = left.sizeBytes
            val rightSize = right.sizeBytes
            when {
                leftSize == null && rightSize == null -> nameAscending.compare(left, right)
                leftSize == null -> 1
                rightSize == null -> -1
                leftSize != rightSize -> if (descending) rightSize.compareTo(leftSize) else leftSize.compareTo(rightSize)
                else -> nameAscending.compare(left, right)
            }
        }
        return when (mode) {
            AceEditorFontSortMode.NAME_ASC -> nameAscending
            AceEditorFontSortMode.NAME_DESC -> nameDescending
            AceEditorFontSortMode.SIZE_ASC -> sizeComparator(descending = false)
            AceEditorFontSortMode.SIZE_DESC -> sizeComparator(descending = true)
        }
    }
}
