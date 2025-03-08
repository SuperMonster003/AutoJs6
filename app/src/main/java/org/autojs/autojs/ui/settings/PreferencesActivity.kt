package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPreferencesBinding

/**
 * Created by Stardust on Feb 2, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
open class PreferencesActivity : BaseActivity() {

    private var binding: ActivityPreferencesBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreferencesBinding.inflate(layoutInflater).also {
            setContentView(it.root)
            it.toolbar.apply {
                setTitle(R.string.text_settings)
                setSupportActionBar(this)
                setNavigationOnClickListener { finish() }
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_preferences, PreferencesFragment())
            .disallowAddToBackStack()
            .commit()
    }

    override fun onStart() {
        super.onStart()
        binding!!.toolbar.navigationIcon?.let {
            val aimColor = when {
                ThemeColorManager.isThemeColorLuminanceLight() -> ContextCompat.getColor(this, R.color.day)
                else -> ContextCompat.getColor(this, R.color.night)
            }
            it.setTint(aimColor)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    companion object {

        @JvmStatic
        @JvmOverloads
        fun launch(context: Context = GlobalAppContext.get()) {
            Intent(context, PreferencesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
        }

    }

}