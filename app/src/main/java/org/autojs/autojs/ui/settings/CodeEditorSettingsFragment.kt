package org.autojs.autojs.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EdgeEffect
import androidx.annotation.StringRes
import androidx.core.view.doOnNextLayout
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreference
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.preference.MaterialDialogPreference
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.theme.preference.PreferenceButtonColorMutable
import org.autojs.autojs.theme.preference.PreferenceSummaryColorMutable
import org.autojs.autojs.theme.preference.PreferenceTitleColorMutable
import org.autojs.autojs.theme.preference.ThemeColorSwitchPreference
import org.autojs.autojs.ui.edit.editor.EditorDisplayPreferences
import org.autojs.autojs.ui.edit.editor.EditorPinchToZoomPreferences
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.ui.edit.keyboard.AceActionHistoryStore
import org.autojs.autojs.ui.edit.theme.EditorChromePalette
import org.autojs.autojs.ui.edit.theme.EditorDialogViewThemeApplier
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R

class CodeEditorSettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var suppressedPreferenceActionKey: String? = null
    private var themePreviewHandler: ((Theme) -> Unit)? = null
    private var fontPreviewHandler: ((String) -> Unit)? = null
    private var preferencesCreated = false
    private val unavailablePreferenceKeys = mutableSetOf<String>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        EditorPinchToZoomPreferences.migrateLegacyPreferenceIfNeeded()
        EditorEnginePreferences.initialize(requireContext())
        setPreferencesFromResource(R.xml.fragment_code_editor_settings, rootKey)
        applyEmbeddedDialogPreferenceColors()
        preferencesCreated = true
        syncEditorEnginePreferenceAvailability()
        syncThemePreviewPreference()
        syncFontPreviewPreference()
        suppressedPreferenceActionKey = initialPreferenceKey.takeIf { suppressInitialActionRecord }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
                return EdgeEffect(recyclerView.context).apply {
                    color = ThemeColorManager.colorPrimary
                }
            }
        }
        listView.isHorizontalScrollBarEnabled = false
        listView.isVerticalScrollBarEnabled = false
        if (embeddedInDialog) {
            EditorDialogViewThemeApplier.setSubtreeExcluded(listView, true)
            // The dialog applier may have visited rows between child inflation and this callback.
            // Rebind once after exclusion so even the first rendered frame comes from A/B/C state.
            listView.adapter?.notifyDataSetChanged()
        } else {
            listView.excludePaddingClippableViewFromBottomNavigationBar()
        }
        openInitialPreferenceIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        EditorEnginePreferences.reconcile(requireContext())
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        syncEditorEnginePreferenceAvailability()
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (
            key == getString(R.string.key_editor_engine) ||
            key == getString(R.string.key_ace_lsp_enabled) ||
            key == getString(R.string.key_ace_word_wrap_enabled)
        ) {
            syncEditorEnginePreferenceAvailability()
        }
        if (
            key == getString(R.string.key_editor_engine) ||
            key == getString(R.string.key_editor_theme_mode) ||
            key == getString(R.string.key_editor_theme) ||
            key == getString(R.string.key_editor_theme_light) ||
            key == getString(R.string.key_editor_theme_dark)
        ) {
            findPreference<EditorThemePreference>(getString(R.string.key_editor_theme))?.refreshSummary()
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        recordAceSettingAction(preference)
        return super.onPreferenceTreeClick(preference)
    }

    fun openPreference(preferenceKey: String, suppressActionRecord: Boolean = false) {
        if (suppressActionRecord) {
            suppressedPreferenceActionKey = preferenceKey
        }
        listView.post {
            findPreference<Preference>(preferenceKey)?.let { preference ->
                scrollToPreference(preference)
                if (preferenceKey in unavailablePreferenceKeys && preference !is MaterialPreference) {
                    showUnavailableEditorSettingHint(getString(R.string.summary_editor_setting_unavailable_for_current_engine))
                } else {
                    clickPreferenceRow(preference)
                }
            }
        }
    }

    private fun clickPreferenceRow(preference: Preference) {
        val recycler = listView
        val positions = recycler.adapter as? PreferenceGroup.PreferencePositionCallback ?: return
        val position = positions.getPreferenceAdapterPosition(preference)
        if (position == RecyclerView.NO_POSITION) return
        val holder = recycler.findViewHolderForAdapterPosition(position)
        if (holder != null) {
            holder.itemView.performClick()
        } else {
            // Use the public View click path after scrolling has bound the requested row.
            recycler.doOnNextLayout {
                if (view == null) return@doOnNextLayout
                val current = positions.getPreferenceAdapterPosition(preference)
                if (current != RecyclerView.NO_POSITION) {
                    recycler.findViewHolderForAdapterPosition(current)?.itemView?.performClick()
                }
            }
            recycler.scrollToPosition(position)
        }
    }

    fun setThemePreviewHandler(handler: ((Theme) -> Unit)?) {
        themePreviewHandler = handler
        syncThemePreviewPreference()
    }

    fun setFontPreviewHandler(handler: ((String) -> Unit)?) {
        fontPreviewHandler = handler
        syncFontPreviewPreference()
    }

    fun dismissOpenPreferenceDialogs() {
        fun dismissRecursively(group: PreferenceGroup) {
            for (index in 0 until group.preferenceCount) {
                val preference = group.getPreference(index)
                (preference as? MaterialDialogPreference)?.dismissDialogIfShowing()
                (preference as? PreferenceGroup)?.let(::dismissRecursively)
            }
        }
        preferenceScreen?.let(::dismissRecursively)
    }

    private fun openInitialPreferenceIfNeeded() {
        initialPreferenceKey?.let { openPreference(it, suppressInitialActionRecord) }
    }

    /**
     * PreferenceViewHolder reapplies the host Activity theme whenever a row is rebound. Persist all
     * editor-owned role colors on the Preference objects so each bind wins after row recycling.
     */
    internal fun applyEmbeddedDialogPreferenceColors(
        palette: EditorSettingsPreferencePalette = EditorChromePalette.current().let { colors ->
            EditorSettingsPreferencePalette.generate(
                background = colors.background,
                primary = colors.foreground,
            )
        },
    ) {
        if (!embeddedInDialog) {
            return
        }
        fun applyToGroup(group: PreferenceGroup) {
            for (index in 0 until group.preferenceCount) {
                val preference = group.getPreference(index)
                (preference as? PreferenceTitleColorMutable)?.titleColor =
                    if (preference is PreferenceCategory) {
                        palette.categoryAndButton
                    } else {
                        palette.title
                    }
                (preference as? PreferenceSummaryColorMutable)?.summaryColor = palette.subtitle
                if (preference is ThemeColorSwitchPreference) {
                    preference.setButtonColor(
                        palette.categoryAndButton,
                        contrastMatters = false,
                    )
                } else {
                    (preference as? PreferenceButtonColorMutable)?.buttonColor =
                        palette.categoryAndButton
                }
                (preference as? PreferenceGroup)?.let(::applyToGroup)
            }
        }
        preferenceScreen?.let(::applyToGroup)
    }

    private fun recordAceSettingAction(preference: Preference) {
        if (!recordAceActionFavorites) {
            return
        }
        val preferenceKey = preference.key ?: return
        if (preferenceKey in unavailablePreferenceKeys) {
            return
        }
        if (!EditorSettingActionRegistry.isKnownPreferenceKey(requireContext(), preferenceKey)) {
            return
        }
        if (suppressedPreferenceActionKey == preferenceKey) {
            suppressedPreferenceActionKey = null
            return
        }
        val label = EditorSettingActionRegistry.labelForPreferenceKey(requireContext(), preferenceKey) ?: return
        AceActionHistoryStore.record(
            requireContext(),
            AceActionHistoryStore.Action(
                id = EditorSettingActionRegistry.actionIdForPreferenceKey(preferenceKey),
                label = label,
                mutatesText = false,
            ),
        )
    }

    private fun syncEditorEnginePreferenceAvailability() {
        val aceSelected = EditorEnginePreferences.get() == EditorEngineType.ACE
        val nativeSelected = !aceSelected
        setPreferenceAvailable(R.string.key_ace_editor_font, aceSelected)
        setPreferenceAvailable(R.string.key_ace_font_ligatures_enabled, aceSelected, R.string.summary_ace_font_ligatures_enabled)
        setPreferenceAvailable(R.string.key_ace_font_styles_enabled, aceSelected, R.string.summary_ace_font_styles_enabled)
        setPreferenceAvailable(R.string.key_ace_print_margin_enabled, aceSelected, R.string.summary_ace_print_margin_enabled)
        setPreferenceAvailable(R.string.key_ace_indent_guides_enabled, aceSelected, R.string.summary_ace_indent_guides_enabled)
        setPreferenceAvailable(R.string.key_ace_highlight_indent_guides_enabled, aceSelected, R.string.summary_ace_highlight_indent_guides_enabled)
        setPreferenceAvailable(R.string.key_ace_whitespaces_in_selection_enabled, aceSelected, R.string.summary_ace_whitespaces_in_selection_enabled)
        setPreferenceAvailable(R.string.key_ace_custom_scrollbar_enabled, aceSelected, R.string.summary_ace_custom_scrollbar_enabled)
        setPreferenceAvailable(R.string.key_ace_scrollbar_auto_hide_enabled, aceSelected, R.string.summary_ace_scrollbar_auto_hide_enabled)
        setPreferenceAvailable(R.string.key_ace_svg_gutter_icons_enabled, aceSelected, R.string.summary_ace_svg_gutter_icons_enabled)
        setPreferenceAvailable(R.string.key_ace_touch_magnifier_enabled, aceSelected, R.string.summary_ace_touch_magnifier_enabled)
        setPreferenceAvailable(R.string.key_ace_lsp_enabled, aceSelected, R.string.summary_ace_lsp_enabled)
        syncAceLspPreferencesVisibility(aceSelected)
        setPreferenceAvailable(R.string.key_ace_word_wrap_enabled, aceSelected, R.string.summary_ace_word_wrap_enabled)
        syncWordWrapIndentPreferenceVisibility(aceSelected)
        setPreferenceAvailable(R.string.key_editor_gutter_width_mode, aceSelected, R.string.summary_editor_gutter_width_mode)
        setPreferenceAvailable(R.string.key_editor_fold_markers_enabled, aceSelected, R.string.summary_editor_fold_markers_enabled)
        setPreferenceAvailable(R.string.key_ace_smart_hint_completion_trigger, aceSelected, R.string.summary_ace_smart_hint_completion_trigger)
        setPreferenceAvailable(R.string.key_ace_smart_hint_completion_min_chars, aceSelected, R.string.summary_ace_smart_hint_completion_min_chars)
        setPreferenceAvailable(R.string.key_ace_smart_hint_completion_delay, aceSelected, R.string.summary_ace_smart_hint_completion_delay)
        setPreferenceAvailable(R.string.key_ace_smart_hint_signature_trigger, aceSelected, R.string.summary_ace_smart_hint_signature_trigger)
        setPreferenceAvailable(R.string.key_ace_smart_hint_arrow_overload_cycling, aceSelected, R.string.summary_ace_smart_hint_arrow_overload_cycling)
        setPreferenceAvailable(R.string.key_ace_symbol_highlight_enabled, aceSelected, R.string.summary_ace_symbol_highlight_enabled)
        setPreferenceAvailable(R.string.key_ace_inline_completion_preview_enabled, aceSelected, R.string.summary_ace_inline_completion_preview_enabled)
        setPreferenceAvailable(R.string.key_editor_code_highlight_threshold_bytes, nativeSelected)
        setPreferenceAvailable(R.string.key_editor_large_file_mode_threshold_bytes, nativeSelected)
    }

    private fun syncThemePreviewPreference() {
        if (!preferencesCreated) {
            return
        }
        val handler = themePreviewHandler.takeIf { embeddedInDialog }
        findPreference<EditorThemePreference>(getString(R.string.key_editor_theme))
            ?.setThemePreviewHandler(handler)
    }

    private fun syncFontPreviewPreference() {
        if (!preferencesCreated) {
            return
        }
        val handler = fontPreviewHandler.takeIf { embeddedInDialog }
        findPreference<AceEditorFontPreference>(getString(R.string.key_ace_editor_font))
            ?.setFontPreviewHandler(handler)
    }

    private fun syncAceLspPreferencesVisibility(aceSelected: Boolean) {
        val visible = aceSelected && AceEditorLspPreferences.isEnabled()
        findPreference<Preference>(getString(R.string.key_ace_language_support_matrix))?.isVisible = aceSelected
        listOf(
            R.string.key_ace_lsp_declaration_groups,
            R.string.key_ace_lsp_file_types,
            R.string.key_ace_semantic_languages,
        ).forEach { keyRes ->
            findPreference<Preference>(getString(keyRes))?.apply {
                isVisible = visible
            }
        }
    }

    private fun syncWordWrapIndentPreferenceVisibility(aceSelected: Boolean) {
        val visible = aceSelected && EditorDisplayPreferences.isWordWrapEnabled()
        val preferenceKey = getString(R.string.key_editor_word_wrap_indent_style)
        findPreference<Preference>(preferenceKey)?.apply {
            if (visible) {
                isVisible = true
                setPreferenceAvailable(
                    R.string.key_editor_word_wrap_indent_style,
                    true,
                    R.string.summary_editor_word_wrap_indent_style,
                )
            } else {
                key?.let(unavailablePreferenceKeys::remove)
                if (this is MaterialListPreference) {
                    useEntrySummaryProvider()
                }
                isVisible = false
            }
        }
    }

    private fun setPreferenceAvailable(
        @StringRes keyRes: Int,
        available: Boolean,
        @StringRes enabledSummaryRes: Int? = null,
    ) {
        findPreference<Preference>(getString(keyRes))?.apply {
            val preferenceKey = key ?: return@apply
            val unavailableSummary = getString(R.string.summary_editor_setting_unavailable_for_current_engine)
            if (available) {
                unavailablePreferenceKeys.remove(preferenceKey)
            } else {
                unavailablePreferenceKeys.add(preferenceKey)
            }

            val onUnavailableClick = {
                showUnavailableEditorSettingHint(unavailableSummary)
            }
            if (this is ThemeColorSwitchPreference) {
                isEnabled = true
                isSelectable = true
                setAvailableForCurrentContext(
                    available = available,
                    unavailableChecked = false,
                    onUnavailableClick = onUnavailableClick,
                )
            } else {
                isEnabled = available || this !is SwitchPreference
                isSelectable = available || this !is SwitchPreference
            }
            if (this is MaterialPreference &&
                this !is EditorCodeHighlightThresholdPreference &&
                this !is EditorLargeFileModeThresholdPreference
            ) {
                setAvailableForCurrentContext(available, onUnavailableClick)
            }

            when (this) {
                is MaterialListPreference -> {
                    if (available) {
                        useEntrySummaryProvider()
                    } else {
                        useStaticSummaryProvider(unavailableSummary)
                    }
                }
                is AceEditorFontPreference -> {
                    setAvailableForCurrentEngine(available, unavailableSummary)
                }
                is EditorCodeHighlightThresholdPreference -> {
                    setAvailableForCurrentEngine(available, unavailableSummary, onUnavailableClick)
                }
                is EditorLargeFileModeThresholdPreference -> {
                    setAvailableForCurrentEngine(available, unavailableSummary, onUnavailableClick)
                }
                else -> {
                    when {
                        enabledSummaryRes != null -> {
                            summary = if (available) getString(enabledSummaryRes) else unavailableSummary
                        }
                        !available -> {
                            summary = unavailableSummary
                        }
                    }
                }
            }
        }
    }

    private fun showUnavailableEditorSettingHint(message: CharSequence) {
        if (embeddedInDialog) {
            ViewUtils.showSnack(requireView(), message)
        } else {
            ViewUtils.showToast(requireContext(), message)
        }
    }

    private val embeddedInDialog: Boolean
        get() = arguments?.getBoolean(ARG_EMBEDDED_IN_DIALOG) == true

    private val initialPreferenceKey: String?
        get() = arguments?.getString(ARG_INITIAL_PREFERENCE_KEY)

    private val recordAceActionFavorites: Boolean
        get() = arguments?.getBoolean(ARG_RECORD_ACE_ACTION_FAVORITES) == true

    private val suppressInitialActionRecord: Boolean
        get() = arguments?.getBoolean(ARG_SUPPRESS_INITIAL_ACTION_RECORD) == true

    companion object {

        private const val ARG_EMBEDDED_IN_DIALOG = "embedded_in_dialog"
        private const val ARG_INITIAL_PREFERENCE_KEY = "initial_preference_key"
        private const val ARG_RECORD_ACE_ACTION_FAVORITES = "record_ace_action_favorites"
        private const val ARG_SUPPRESS_INITIAL_ACTION_RECORD = "suppress_initial_action_record"

        fun newInstance(
            embeddedInDialog: Boolean = false,
            initialPreferenceKey: String? = null,
            recordAceActionFavorites: Boolean = false,
            suppressInitialActionRecord: Boolean = false,
        ): CodeEditorSettingsFragment {
            return CodeEditorSettingsFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_EMBEDDED_IN_DIALOG, embeddedInDialog)
                    putBoolean(ARG_RECORD_ACE_ACTION_FAVORITES, recordAceActionFavorites)
                    putBoolean(ARG_SUPPRESS_INITIAL_ACTION_RECORD, suppressInitialActionRecord)
                    initialPreferenceKey?.let { putString(ARG_INITIAL_PREFERENCE_KEY, it) }
                }
            }
        }
    }
}
