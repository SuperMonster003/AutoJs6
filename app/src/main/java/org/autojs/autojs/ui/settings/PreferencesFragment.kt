package org.autojs.autojs.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EdgeEffect
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R

class PreferencesFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_preferences, rootKey)
        syncEditorEnginePreferenceVisibility()
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

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        syncEditorEnginePreferenceVisibility()
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == getString(R.string.key_editor_engine)) {
            syncEditorEnginePreferenceVisibility()
        }
    }

    private fun syncEditorEnginePreferenceVisibility() {
        val aceSelected = preferenceManager.sharedPreferences
            ?.getString(getString(R.string.key_editor_engine), getString(R.string.default_key_editor_engine))
            .let { EditorEngineType.fromPreferenceValue(it) == EditorEngineType.ACE }

        listOf(
            R.string.key_ace_editor_font,
            R.string.key_ace_word_wrap_enabled,
            R.string.key_ace_line_numbers_enabled,
            R.string.key_ace_lsp_enabled,
        ).forEach { setPreferenceVisible(it, aceSelected) }
    }

    private fun setPreferenceVisible(@StringRes keyRes: Int, visible: Boolean) {
        findPreference<Preference>(getString(keyRes))?.isVisible = visible
    }
}
