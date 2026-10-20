package org.autojs.autojs.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EdgeEffect
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.wizard.SetupWizards
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R

class PreferencesFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        EditorEnginePreferences.initialize(requireContext())
        setPreferencesFromResource(R.xml.fragment_preferences, rootKey)
        findPreference<Preference>(getString(R.string.key_setup_wizards))?.setOnPreferenceClickListener {
            SetupWizards.showChooser(requireActivity())
            true
        }
    }

    override fun onResume() {
        super.onResume()
        EditorEnginePreferences.reconcile(requireContext())
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        refreshSharedCodeEditorPreferences()
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key in sharedCodeEditorPreferenceKeys) {
            refreshSharedCodeEditorPreferences()
        }
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
        listView.excludePaddingClippableViewFromBottomNavigationBar()
    }

    private fun refreshSharedCodeEditorPreferences() {
        findPreference<MaterialListPreference>(getString(R.string.key_editor_engine))?.useEntrySummaryProvider()
        findPreference<MaterialListPreference>(getString(R.string.key_editor_log_button_behavior))?.useEntrySummaryProvider()
        findPreference<SwitchPreference>(getString(R.string.key_editor_auto_show_log_bottom_sheet_on_run))?.isChecked =
            EditorKeys.EDITOR_AUTO_SHOW_LOG_BOTTOM_SHEET_ON_RUN.get()
        listView.adapter?.notifyDataSetChanged()
    }

    private val sharedCodeEditorPreferenceKeys: Set<String>
        get() = setOf(
            getString(R.string.key_editor_engine),
            getString(R.string.key_editor_auto_show_log_bottom_sheet_on_run),
            getString(R.string.key_editor_log_button_behavior),
        )
}
