package org.autojs.autojs.inrt

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.autojs.autojs.theme.preference.ThemeColorPermissionSwitchPreference
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivitySettingsInrtBinding

/**
 * Created by Stardust on Dec 8, 2017.
 * Modified by SuperMonster003 as of Oct 27, 2023.
 */
class SettingsActivity : AppCompatActivity() {

    private val preferenceFragment by lazy { PreferenceFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsInrtBinding.inflate(layoutInflater).also { setContentView(it.root) }
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_setting, preferenceFragment)
            .commit()
        binding.toolbar.apply {
            setTitle(R.string.text_settings)
            setSupportActionBar(this)
            setNavigationOnClickListener { finish() }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        findPreference(R.string.key_all_files_access)?.sync()
        findPreference(R.string.key_display_over_other_apps)?.sync()
        findPreference(R.string.key_post_notifications_permission)?.sync()
    }

    private fun findPreference(keyRes: Int): ThemeColorPermissionSwitchPreference? {
        return preferenceFragment.findPreference(getString(keyRes))
    }

    class PreferenceFragment : androidx.preference.PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preference_inrt)
        }
    }

}
