package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPreferencesBinding

/**
 * The page "Manage AutoJs6 settings" (docs/settings/ROADMAP.md D4 / T10), hosting [SettingsTransferFragment].
 * [EXTRA_WIZARD] opens a wizard right away ([WIZARD_EXPORT], [WIZARD_IMPORT]).
 *
 * zh-CN: "管理 AutoJs6 设置" 页面 (ROADMAP D4 / T10), 承载 [SettingsTransferFragment]. [EXTRA_WIZARD] 使页面打开后立即
 * 进入向导 ([WIZARD_EXPORT], [WIZARD_IMPORT]).
 */
class SettingsTransferActivity : BaseActivity() {

    private lateinit var binding: ActivityPreferencesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        title = getString(R.string.settings_transfer_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_preferences, SettingsTransferFragment()).commit()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.toolbar.navigationIcon?.setTint(ThemeColorManager.getDayOrNightColorByLuminance(this))
    }

    companion object {

        const val EXTRA_WIZARD = "wizard"
        const val WIZARD_EXPORT = "export"
        const val WIZARD_IMPORT = "import"

        @JvmStatic
        @JvmOverloads
        fun start(context: Context, wizard: String? = null) {
            context.startActivity(Intent(context, SettingsTransferActivity::class.java).putExtra(EXTRA_WIZARD, wizard))
        }
    }
}
