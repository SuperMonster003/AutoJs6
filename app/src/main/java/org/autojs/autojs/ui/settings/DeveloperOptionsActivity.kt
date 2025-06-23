package org.autojs.autojs.ui.settings

import android.os.Bundle
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils.setToolbarAsBack
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityDeveloperOptionsBinding

/**
 * Created by SuperMonster003 on Jun 2, 2022.
 */
class DeveloperOptionsActivity : BaseActivity() {

    private lateinit var binding: ActivityDeveloperOptionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDeveloperOptionsBinding.inflate(layoutInflater).also { binding ->
            setContentView(binding.root)
            binding.toolbar.setNavigationOnClickListener { finish() }
        }

        setToolbarAsBack(this, R.string.text_developer_options)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_developer_options, DeveloperOptionsFragment())
            .disallowAddToBackStack()
            .commit()
    }

    override fun onStart() {
        super.onStart()
        binding.toolbar.navigationIcon?.setTint(ThemeColorManager.getDayOrNightColorByLuminance(this))
    }

}
