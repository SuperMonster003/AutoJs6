package org.autojs.autojs.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.autojs.autojs.ui.error.ProcessExitHistoryActivity
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R

class DeveloperOptionsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_developer_options, rootKey)
        findPreference<Preference>("process_exit_history")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ProcessExitHistoryActivity::class.java))
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.excludePaddingClippableViewFromBottomNavigationBar()
    }

}