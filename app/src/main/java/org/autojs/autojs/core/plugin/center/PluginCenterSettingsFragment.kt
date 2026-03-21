package org.autojs.autojs.core.plugin.center

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R

class PluginCenterSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_plugin_center_preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.excludePaddingClippableViewFromBottomNavigationBar()
    }
}
