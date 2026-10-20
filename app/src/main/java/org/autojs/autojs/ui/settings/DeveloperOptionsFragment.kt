package org.autojs.autojs.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.autojs.autojs.leakcanary.LeakCanarySetup
import org.autojs.autojs.ui.error.ProcessExitHistoryActivity
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R

class DeveloperOptionsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_developer_options, rootKey)
        findPreference<Preference>("process_exit_history")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ProcessExitHistoryActivity::class.java))
            true
        }
        // LeakCanary exists in debug builds only (docs/memory/ROADMAP.md T5).
        // zh-CN: LeakCanary 只存在于 debug 构建 (docs/memory/ROADMAP.md T5).
        findPreference<Preference>(getString(R.string.key_leakcanary_enabled))?.apply {
            isVisible = BuildConfig.DEBUG
            setOnPreferenceChangeListener { _, newValue ->
                LeakCanarySetup.apply(newValue == true)
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.excludePaddingClippableViewFromBottomNavigationBar()
    }

}