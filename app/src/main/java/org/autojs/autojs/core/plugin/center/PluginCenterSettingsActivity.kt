package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils.setToolbarAsBack
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPluginCenterSettingsBinding

class PluginCenterSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityPluginCenterSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPluginCenterSettingsBinding.inflate(layoutInflater).also {
            setContentView(it.root)
            it.toolbar.setNavigationOnClickListener { finish() }
        }

        setToolbarAsBack(this, R.string.text_plugin_center_settings)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_plugin_center_settings, PluginCenterSettingsFragment())
            .disallowAddToBackStack()
            .commit()
    }

    override fun onStart() {
        super.onStart()
        binding.toolbar.navigationIcon?.setTint(ThemeColorManager.getDayOrNightColorByLuminance(this))
    }

    companion object {
        fun startActivity(context: Context) {
            Intent(context, PluginCenterSettingsActivity::class.java)
                .startSafely(context)
        }
    }
}
