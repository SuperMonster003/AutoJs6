package com.stardust.autojs.inrt

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View

import com.stardust.autojs.inrt.autojs.AutoJs
import com.stardust.autojs.inrt.launch.GlobalProjectLauncher
import com.stardust.autojs.core.console.ConsoleView
import com.stardust.autojs.core.console.ConsoleImpl

class LogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupView()
        if (intent.getBooleanExtra(EXTRA_LAUNCH_SCRIPT, false)) {
            GlobalProjectLauncher.launch(this)
        }
    }

    private fun setupView() {
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val consoleView = findViewById<ConsoleView>(R.id.console)
        consoleView.setConsole(AutoJs.instance.globalConsole as ConsoleImpl)
        consoleView.findViewById<View>(R.id.input_container).visibility = View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        startActivity(Intent(this, SettingsActivity::class.java))
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    companion object {


        const val EXTRA_LAUNCH_SCRIPT = "launch_script"
    }
}
