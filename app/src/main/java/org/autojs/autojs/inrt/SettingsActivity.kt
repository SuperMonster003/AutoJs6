package org.autojs.autojs.inrt

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivitySettingsInrtBinding

/**
 * Created by Stardust on Dec 8, 2017.
 * Modified by SuperMonster003 as of Oct 27, 2023.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsInrtBinding.inflate(layoutInflater).also { setContentView(it.root) }
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_setting, PreferenceFragment())
            .commit()
        binding.toolbar.apply {
            setTitle(R.string.text_settings)
            setSupportActionBar(this)
            setNavigationOnClickListener { finish() }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class PreferenceFragment : androidx.preference.PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preference_inrt)
        }
    }

}
