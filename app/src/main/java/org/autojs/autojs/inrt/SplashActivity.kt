package org.autojs.autojs.inrt

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.autojs.autojs.inrt.autojs.AutoJs
import org.autojs.autojs.inrt.launch.GlobalProjectLauncher
import org.autojs.autojs.ui.splash.SplashActivity.Companion.INIT_TIMEOUT
import org.autojs.autojs6.R
import kotlin.concurrent.thread

/**
 * Created by Stardust on Feb 2, 2018.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_splash_inrt)
        val slug = findViewById<TextView>(R.id.slug)
        slug.typeface = Typeface.createFromAsset(assets, "roboto_medium.ttf")
        if (!Pref.isFirstUsing) {
            main()
        } else {
            Handler(Looper.myLooper()!!).postDelayed({ this@SplashActivity.main() }, INIT_TIMEOUT)
        }
        super.onCreate(savedInstanceState)
    }

    private fun main() {
        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun runScript() {
        thread(true) {
            try {
                GlobalProjectLauncher.launch(this)
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@SplashActivity, e.message, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@SplashActivity, LogActivity::class.java))
                    AutoJs.instance.globalConsole.printAllStackTrace(e)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        runScript()
    }

    private fun checkPermission(@Suppress("SameParameterValue") vararg permissions: String) {
        val requestPermissions = getRequestPermissions(permissions)
        if (requestPermissions.isNotEmpty()) {
            requestPermissions(requestPermissions, PERMISSION_REQUEST_CODE)
        } else {
            runScript()
        }
    }

    private fun getRequestPermissions(permissions: Array<out String>) = ArrayList<String>()
        .let { list ->
            permissions.filterTo(list) { checkSelfPermission(it) == PERMISSION_DENIED }
            list.toTypedArray()
        }

    companion object {

        private const val PERMISSION_REQUEST_CODE = 11186

    }

}

