package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context
import androidx.annotation.StringRes
import org.autojs.autojs.core.pref.keys.EditorKeyboardKeys
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set
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

/**
 * Persisted presentation preferences shared by the font dialog and font previewer, in the named store
 * `ace_editor_font_ui_preferences` declared in [EditorKeyboardKeys] (docs/settings/ROADMAP.md P3.2, names unchanged).
 * The [Context] parameters are kept for the callers; the store is opened by the settings layer with the application
 * context, as before.
 */
object AceEditorFontUiPreferences {

    private const val OTHER_FEATURE_STORAGE_VALUE = "other"

    private val store
        get() = SettingsStores[NamedStores.ACE_EDITOR_FONT_UI_PREFERENCES]

    @Suppress("UNUSED_PARAMETER")
    fun getSortMode(context: Context): AceEditorFontSortMode =
        AceEditorFontSortMode.fromStoredValue(EditorKeyboardKeys.FONT_SORT_MODE.storedOrNull())

    @Suppress("UNUSED_PARAMETER")
    fun setSortMode(context: Context, mode: AceEditorFontSortMode) {
        EditorKeyboardKeys.FONT_SORT_MODE.set(if (mode == AceEditorFontSortMode.DEFAULT) null else mode.name)
    }

    @Suppress("UNUSED_PARAMETER")
    fun resetSortMode(context: Context) {
        EditorKeyboardKeys.FONT_SORT_MODE.remove()
    }

    @Suppress("UNUSED_PARAMETER")
    fun getTypeLocked(context: Context, field: AceEditorFontSortField): Boolean =
        typeLockedKey(field).storedOrNull() ?: field.typeLockedByDefault

    @Suppress("UNUSED_PARAMETER")
    fun setTypeLocked(
        context: Context,
        field: AceEditorFontSortField,
        typeLocked: Boolean,
    ) {
        typeLockedKey(field).set(if (typeLocked == field.typeLockedByDefault) null else typeLocked)
    }

    @Suppress("UNUSED_PARAMETER")
    fun resetTypeLocks(context: Context) {
        store.edit {
            remove(EditorKeyboardKeys.FONT_NAME_SORT_TYPE_LOCKED.name)
            remove(EditorKeyboardKeys.FONT_SIZE_SORT_TYPE_LOCKED.name)
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
        val storedFeatures = EditorKeyboardKeys.FONT_PREVIEW_EXCLUDED_FEATURES.get()
        val excluded = storedFeatures
            .mapNotNullTo(linkedSetOf()) { byCatalogValue[it] }
        val byTypeValue = AceEditorFontType.entries.associateBy(AceEditorFontType::storageValue)
        val excludedTypes = EditorKeyboardKeys.FONT_PREVIEW_EXCLUDED_TYPES.get()
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

    @Suppress("UNUSED_PARAMETER")
    fun setExcludedFeatures(
        context: Context,
        features: Set<FontFeature>,
        excludeOther: Boolean = false,
    ) {
        EditorKeyboardKeys.FONT_PREVIEW_EXCLUDED_FEATURES.set(
            if (features.isEmpty() && !excludeOther) null
            else features.mapTo(linkedSetOf(), FontFeature::catalogValue).apply {
                if (excludeOther) add(OTHER_FEATURE_STORAGE_VALUE)
            },
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun setExcludedTypes(context: Context, types: Set<AceEditorFontType>) {
        EditorKeyboardKeys.FONT_PREVIEW_EXCLUDED_TYPES.set(
            if (types.isEmpty()) null else types.mapTo(linkedSetOf(), AceEditorFontType::storageValue),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun resetPreviewFilter(context: Context) {
        store.edit {
            remove(EditorKeyboardKeys.FONT_PREVIEW_EXCLUDED_FEATURES.name)
            remove(EditorKeyboardKeys.FONT_PREVIEW_EXCLUDED_TYPES.name)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getSelectedVariantId(context: Context, groupId: String): String? {
        if (!AceFontIds.validId.matches(groupId)) return null
        return EditorKeyboardKeys.FONT_SELECTED_VARIANT.key(groupId).storedOrNull()
            ?.takeIf(AceFontIds.validId::matches)
    }

    @Suppress("UNUSED_PARAMETER")
    fun setSelectedVariantId(context: Context, groupId: String, fontId: String) {
        require(AceFontIds.validId.matches(groupId)) { "Invalid ACE font group id: $groupId" }
        require(AceFontIds.validId.matches(fontId)) { "Invalid ACE font variant id: $fontId" }
        EditorKeyboardKeys.FONT_SELECTED_VARIANT.key(groupId).set(fontId)
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

    private fun typeLockedKey(field: AceEditorFontSortField) = when (field) {
        AceEditorFontSortField.NAME -> EditorKeyboardKeys.FONT_NAME_SORT_TYPE_LOCKED
        AceEditorFontSortField.SIZE -> EditorKeyboardKeys.FONT_SIZE_SORT_TYPE_LOCKED
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
