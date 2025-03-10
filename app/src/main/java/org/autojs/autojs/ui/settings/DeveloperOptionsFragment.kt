package org.autojs.autojs.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class DeveloperOptionsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_developer_options, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewUtils.excludePaddingClippableViewFromNavigationBar(listView)
    }

}