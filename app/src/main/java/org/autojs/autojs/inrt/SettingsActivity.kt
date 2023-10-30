package org.autojs.autojs.inrt

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/12/8.
 * Modified by SuperMonster003 as of Oct 27, 2023.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupViews()
    }

    private fun setupViews() {
        setContentView(R.layout.activity_settings_inrt)
        supportFragmentManager.beginTransaction().replace(R.id.fragment_setting, PreferenceFragment()).commit()
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitle(R.string.text_settings)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class PreferenceFragment : androidx.preference.PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preference_inrt)
        }
    }

}
