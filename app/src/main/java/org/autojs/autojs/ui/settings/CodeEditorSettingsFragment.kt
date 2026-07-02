package org.autojs.autojs.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EdgeEffect
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.theme.preference.ThemeColorSwitchPreference
import org.autojs.autojs.ui.edit.editor.EditorDisplayPreferences
import org.autojs.autojs.ui.edit.editor.EditorPinchToZoomPreferences
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.ui.edit.keyboard.AceActionHistoryStore
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R

class CodeEditorSettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var suppressedPreferenceActionKey: String? = null
    private var themePreviewHandler: ((Theme) -> Unit)? = null
    private var preferencesCreated = false
    private val unavailablePreferenceKeys = mutableSetOf<String>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        EditorPinchToZoomPreferences.migrateLegacyPreferenceIfNeeded()
        setPreferencesFromResource(R.xml.fragment_code_editor_settings, rootKey)
        preferencesCreated = true
        syncEditorEnginePreferenceAvailability()
        syncThemePreviewPreference()
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
        if (!embeddedInDialog) {
            listView.excludePaddingClippableViewFromBottomNavigationBar()
        }
        openInitialPreferenceIfNeeded()
    }

    override fun onResume() {
        super.onResume()
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
                    preference.performClick()
                }
            }
        }
    }

    fun setThemePreviewHandler(handler: ((Theme) -> Unit)?) {
        themePreviewHandler = handler
        syncThemePreviewPreference()
    }

    private fun openInitialPreferenceIfNeeded() {
        initialPreferenceKey?.let { openPreference(it, suppressInitialActionRecord) }
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
        setPreferenceAvailable(R.string.key_ace_lsp_enabled, aceSelected, R.string.summary_ace_lsp_enabled)
        syncAceLspFileTypesPreferenceVisibility(aceSelected)
        setPreferenceAvailable(R.string.key_ace_word_wrap_enabled, aceSelected, R.string.summary_ace_word_wrap_enabled)
        syncWordWrapIndentPreferenceVisibility(aceSelected)
        setPreferenceAvailable(R.string.key_editor_gutter_width_mode, aceSelected, R.string.summary_editor_gutter_width_mode)
        setPreferenceAvailable(R.string.key_editor_fold_markers_enabled, aceSelected, R.string.summary_editor_fold_markers_enabled)
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

    private fun syncAceLspFileTypesPreferenceVisibility(aceSelected: Boolean) {
        findPreference<Preference>(getString(R.string.key_ace_lsp_file_types))?.apply {
            isVisible = aceSelected && AceEditorLspPreferences.isEnabled()
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
