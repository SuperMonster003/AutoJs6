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
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R

class CodeEditorSettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_code_editor_settings, rootKey)
        syncEditorEnginePreferenceAvailability()
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

    private fun syncEditorEnginePreferenceAvailability() {
        val aceSelected = EditorEnginePreferences.get() == EditorEngineType.ACE
        setPreferenceAvailable(R.string.key_ace_editor_font, aceSelected)
        setPreferenceAvailable(R.string.key_ace_word_wrap_enabled, aceSelected, R.string.summary_ace_word_wrap_enabled)
        setPreferenceAvailable(R.string.key_ace_lsp_enabled, aceSelected, R.string.summary_ace_lsp_enabled)
        setPreferenceAvailable(R.string.key_editor_fold_markers_enabled, aceSelected, R.string.summary_editor_fold_markers_enabled)

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
            if (this !is MaterialListPreference && enabledSummaryRes != null) {
                summary = getString(
                    if (available) enabledSummaryRes else R.string.summary_editor_setting_unavailable_for_current_engine
                )
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

    companion object {

        private const val ARG_EMBEDDED_IN_DIALOG = "embedded_in_dialog"

        fun newInstance(embeddedInDialog: Boolean = false): CodeEditorSettingsFragment {
            return CodeEditorSettingsFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_EMBEDDED_IN_DIALOG, embeddedInDialog)
                }
            }
        }
    }
}
