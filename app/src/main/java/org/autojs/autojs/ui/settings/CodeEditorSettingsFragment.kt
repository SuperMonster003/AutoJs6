package org.autojs.autojs.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EdgeEffect
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.edit.editor.EditorDisplayPreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.ui.edit.keyboard.AceActionHistoryStore
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R

class CodeEditorSettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var suppressedPreferenceActionKey: String? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_code_editor_settings, rootKey)
        syncEditorEnginePreferenceAvailability()
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
            key == getString(R.string.key_editor_pinch_to_zoom_strategy)
        ) {
            syncEditorEnginePreferenceAvailability()
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
                preference.performClick()
            }
        }
    }

    private fun openInitialPreferenceIfNeeded() {
        initialPreferenceKey?.let { openPreference(it, suppressInitialActionRecord) }
    }

    private fun recordAceSettingAction(preference: Preference) {
        if (!recordAceActionFavorites) {
            return
        }
        val preferenceKey = preference.key ?: return
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
        setPreferenceAvailable(R.string.key_ace_editor_font, aceSelected)
        setPreferenceAvailable(R.string.key_ace_lsp_enabled, aceSelected, R.string.summary_ace_lsp_enabled)
        setPreferenceAvailable(R.string.key_ace_word_wrap_enabled, aceSelected, R.string.summary_ace_word_wrap_enabled)
        setPreferenceAvailable(R.string.key_editor_word_wrap_indent_style, aceSelected, R.string.summary_editor_word_wrap_indent_style)
        setPreferenceAvailable(R.string.key_editor_gutter_width_mode, aceSelected, R.string.summary_editor_gutter_width_mode)
        setPreferenceAvailable(R.string.key_editor_fold_markers_enabled, aceSelected, R.string.summary_editor_fold_markers_enabled)

        if (!aceSelected) {
            EditorDisplayPreferences.setWordWrapEnabled(false)
            EditorDisplayPreferences.setWordWrapIndentStyle(EditorDisplayPreferences.WORD_WRAP_INDENT_STYLE_CONTINUATION)
            EditorDisplayPreferences.setGutterWidthMode(EditorDisplayPreferences.GUTTER_WIDTH_MODE_FIXED)
        }

        if (!aceSelected && currentPinchToZoomKey() == getString(R.string.key_editor_pinch_to_zoom_scale_view)) {
            Pref.putString(
                key(R.string.key_editor_pinch_to_zoom_strategy),
                key(R.string.key_editor_pinch_to_zoom_change_text_size),
            )
        }
        findPreference<EditorPinchToZoomPreference>(getString(R.string.key_editor_pinch_to_zoom_strategy))?.refreshSummary()
    }

    private fun setPreferenceAvailable(
        @StringRes keyRes: Int,
        available: Boolean,
        @StringRes enabledSummaryRes: Int? = null,
    ) {
        findPreference<Preference>(getString(keyRes))?.apply {
            isEnabled = available
            if (enabledSummaryRes != null) {
                val summaryRes = if (available) enabledSummaryRes else R.string.summary_editor_setting_unavailable_for_current_engine
                if (this is MaterialListPreference) {
                    if (available) {
                        useEntrySummaryProvider()
                    } else {
                        useStaticSummaryProvider(getString(summaryRes))
                    }
                } else {
                    summary = getString(summaryRes)
                }
            }
        }
    }

    private fun currentPinchToZoomKey(): String {
        return Pref.getString(
            key(R.string.key_editor_pinch_to_zoom_strategy),
            key(R.string.default_key_editor_pinch_to_zoom_strategy),
        )
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
