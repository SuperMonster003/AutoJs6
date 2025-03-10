package org.autojs.autojs.inrt

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.inrt.autojs.AutoJs
import org.autojs.autojs.inrt.launch.GlobalProjectLauncher
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityMainInrtBinding

class LogActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupView()
        if (intent.getBooleanExtra(EXTRA_LAUNCH_SCRIPT, false)) {
            GlobalProjectLauncher.launch(this)
        }
    }

    private fun setupView() {
        ActivityMainInrtBinding.inflate(layoutInflater).also { binding ->
            setContentView(binding.root)
            binding.toolbar.apply {
                setSupportActionBar(this)
            }
            binding.console.apply {
                setConsole(AutoJs.instance.globalConsole as ConsoleImpl)
                findViewById<View>(R.id.input_container).visibility = View.GONE
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        startActivity(Intent(this, SettingsActivity::class.java))
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main_inrt, menu)
        return true
    }

    companion object {

        const val EXTRA_LAUNCH_SCRIPT = "launch_script"

    }

}
