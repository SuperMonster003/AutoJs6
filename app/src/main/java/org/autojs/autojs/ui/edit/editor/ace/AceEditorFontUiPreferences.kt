package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import org.autojs.autojs.ui.edit.editor.ace.font.AceFontIds
import org.autojs.autojs.ui.edit.editor.ace.font.FontFeature
import org.autojs.autojs6.R

enum class AceEditorFontSortField(
    val typeLockedByDefault: Boolean,
) {
    NAME(typeLockedByDefault = true),
    SIZE(typeLockedByDefault = false),
}

enum class AceEditorFontSortMode(
    @param:StringRes val labelRes: Int,
    val field: AceEditorFontSortField,
) {
    NAME_ASC(R.string.text_sort_by_name_asc, AceEditorFontSortField.NAME),
    NAME_DESC(R.string.text_sort_by_name_desc, AceEditorFontSortField.NAME),
    SIZE_ASC(R.string.text_sort_by_size_asc, AceEditorFontSortField.SIZE),
    SIZE_DESC(R.string.text_sort_by_size_desc, AceEditorFontSortField.SIZE),
    ;

    companion object {
        val DEFAULT = NAME_ASC

        fun fromStoredValue(value: String?): AceEditorFontSortMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

enum class AceEditorFontType(
    val storageValue: String,
    internal val sortPriority: Int,
) {
    BUILT_IN("built_in", 0),
    INSTALLED("installed", 1),
    NOT_INSTALLED("not_installed", 2),
    ;

    companion object {
        fun fromStorageValue(value: String): AceEditorFontType? = entries.firstOrNull {
            it.storageValue == value
        }
    }
}

data class AceEditorFontPreviewFilter(
    val excludedFeatures: Set<FontFeature> = emptySet(),
    val excludeOther: Boolean = false,
    val excludedTypes: Set<AceEditorFontType> = emptySet(),
) {
    val hasSelectedFeatures: Boolean
        get() = FILTERABLE_FEATURES.any { it !in excludedFeatures } || !excludeOther

    val hasSelectedTypes: Boolean
        get() = excludedTypes.size < AceEditorFontType.entries.size

    fun allows(option: AceEditorFontOption): Boolean {
        if (!hasSelectedFeatures || !hasSelectedTypes || option.type in excludedTypes) return false

        val selectedFeatureMatches = option.features.any {
            it in FILTERABLE_FEATURES && it !in excludedFeatures
        }
        val isOther = option.features.none { it in FILTERABLE_FEATURES }
        return selectedFeatureMatches || (!excludeOther && isOther)
    }

    companion object {
        /** Features represented by dedicated checkboxes; OTHER means having none of these. */
        val FILTERABLE_FEATURES: Set<FontFeature> = setOf(
            FontFeature.MONO,
            FontFeature.NERD,
            FontFeature.VARIABLE,
            FontFeature.CN,
            FontFeature.JP,
        )

        val DEFAULT = AceEditorFontPreviewFilter()
    }
}

/** Persisted presentation preferences shared by the font dialog and font previewer. */
object AceEditorFontUiPreferences {

    private const val SHARED_PREFERENCES_NAME = "ace_editor_font_ui_preferences"
    private const val KEY_SORT_MODE = "key_\$_ace_editor_font_sort_mode"
    private const val KEY_NAME_SORT_TYPE_LOCKED = "key_\$_ace_editor_font_name_sort_type_locked"
    private const val KEY_SIZE_SORT_TYPE_LOCKED = "key_\$_ace_editor_font_size_sort_type_locked"
    private const val KEY_EXCLUDED_PREVIEW_FEATURES = "key_\$_ace_editor_font_preview_excluded_features"
    private const val KEY_EXCLUDED_PREVIEW_TYPES = "key_\$_ace_editor_font_preview_excluded_types"
    private const val OTHER_FEATURE_STORAGE_VALUE = "other"
    private const val KEY_SELECTED_VARIANT_PREFIX = "key_\$_ace_editor_font_selected_variant_"

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

    fun getTypeLocked(context: Context, field: AceEditorFontSortField): Boolean =
        preferences(context).getBoolean(typeLockedKey(field), field.typeLockedByDefault)

    fun setTypeLocked(
        context: Context,
        field: AceEditorFontSortField,
        typeLocked: Boolean,
    ) {
        preferences(context).edit {
            if (typeLocked == field.typeLockedByDefault) {
                remove(typeLockedKey(field))
            } else {
                putBoolean(typeLockedKey(field), typeLocked)
            }
        }
    }

    fun resetTypeLocks(context: Context) {
        preferences(context).edit {
            remove(KEY_NAME_SORT_TYPE_LOCKED)
            remove(KEY_SIZE_SORT_TYPE_LOCKED)
        }
    }

    fun sortedOptions(
        context: Context,
        options: List<AceEditorFontOption>,
    ): List<AceEditorFontOption> {
        val mode = getSortMode(context)
        return sortedOptions(options, mode, getTypeLocked(context, mode.field))
    }

    internal fun sortedOptions(
        options: List<AceEditorFontOption>,
        mode: AceEditorFontSortMode,
        typeLocked: Boolean,
    ): List<AceEditorFontOption> = options.sortedWith(comparator(mode, typeLocked))

    fun getPreviewFilter(context: Context): AceEditorFontPreviewFilter {
        val byCatalogValue = FontFeature.entries.associateBy(FontFeature::catalogValue)
        val storedFeatures = preferences(context)
            .getStringSet(KEY_EXCLUDED_PREVIEW_FEATURES, emptySet())
            .orEmpty()
        val excluded = storedFeatures
            .mapNotNullTo(linkedSetOf()) { byCatalogValue[it] }
        val byTypeValue = AceEditorFontType.entries.associateBy(AceEditorFontType::storageValue)
        val excludedTypes = preferences(context)
            .getStringSet(KEY_EXCLUDED_PREVIEW_TYPES, emptySet())
            .orEmpty()
            .mapNotNullTo(linkedSetOf()) { byTypeValue[it] }
        return AceEditorFontPreviewFilter(
            excludedFeatures = excluded,
            excludeOther = OTHER_FEATURE_STORAGE_VALUE in storedFeatures,
            excludedTypes = excludedTypes,
        )
    }

    fun setPreviewFilter(context: Context, filter: AceEditorFontPreviewFilter) {
        setExcludedFeatures(context, filter.excludedFeatures, filter.excludeOther)
        setExcludedTypes(context, filter.excludedTypes)
    }

    fun setExcludedFeatures(
        context: Context,
        features: Set<FontFeature>,
        excludeOther: Boolean = false,
    ) {
        preferences(context).edit {
            if (features.isEmpty() && !excludeOther) {
                remove(KEY_EXCLUDED_PREVIEW_FEATURES)
            } else {
                putStringSet(
                    KEY_EXCLUDED_PREVIEW_FEATURES,
                    features.mapTo(linkedSetOf(), FontFeature::catalogValue).apply {
                        if (excludeOther) add(OTHER_FEATURE_STORAGE_VALUE)
                    },
                )
            }
        }
    }

    fun setExcludedTypes(context: Context, types: Set<AceEditorFontType>) {
        preferences(context).edit {
            if (types.isEmpty()) {
                remove(KEY_EXCLUDED_PREVIEW_TYPES)
            } else {
                putStringSet(
                    KEY_EXCLUDED_PREVIEW_TYPES,
                    types.mapTo(linkedSetOf(), AceEditorFontType::storageValue),
                )
            }
        }
    }

    fun resetPreviewFilter(context: Context) {
        preferences(context).edit {
            remove(KEY_EXCLUDED_PREVIEW_FEATURES)
            remove(KEY_EXCLUDED_PREVIEW_TYPES)
        }
    }

    fun getSelectedVariantId(context: Context, groupId: String): String? {
        if (!AceFontIds.validId.matches(groupId)) return null
        return preferences(context).getString(KEY_SELECTED_VARIANT_PREFIX + groupId, null)
            ?.takeIf(AceFontIds.validId::matches)
    }

    fun setSelectedVariantId(context: Context, groupId: String, fontId: String) {
        require(AceFontIds.validId.matches(groupId)) { "Invalid ACE font group id: $groupId" }
        require(AceFontIds.validId.matches(fontId)) { "Invalid ACE font variant id: $fontId" }
        preferences(context).edit {
            putString(KEY_SELECTED_VARIANT_PREFIX + groupId, fontId)
        }
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

    private fun typeLockedKey(field: AceEditorFontSortField): String = when (field) {
        AceEditorFontSortField.NAME -> KEY_NAME_SORT_TYPE_LOCKED
        AceEditorFontSortField.SIZE -> KEY_SIZE_SORT_TYPE_LOCKED
    }

    private fun comparator(
        mode: AceEditorFontSortMode,
        typeLocked: Boolean,
    ): Comparator<AceEditorFontOption> {
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
        val fieldComparator = when (mode) {
            AceEditorFontSortMode.NAME_ASC -> nameAscending
            AceEditorFontSortMode.NAME_DESC -> nameDescending
            AceEditorFontSortMode.SIZE_ASC -> sizeComparator(descending = false)
            AceEditorFontSortMode.SIZE_DESC -> sizeComparator(descending = true)
        }
        if (!typeLocked) return fieldComparator
        return compareBy<AceEditorFontOption> { it.type.sortPriority }
            .then(fieldComparator)
    }
}
