package org.autojs.autojs.inrt

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.inrt.autojs.AutoJs
import org.autojs.autojs.inrt.launch.GlobalProjectLauncher
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.ui.splash.SplashActivity.Companion.INIT_TIMEOUT
import org.autojs.autojs6.databinding.ActivitySplashInrtBinding

/**
 * Created by Stardust on Feb 2, 2018.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val binding = ActivitySplashInrtBinding.inflate(layoutInflater).apply {
            setContentView(root)
        }
        val projectConfig = ProjectConfig.fromAssets(this, ProjectConfig.configFileOfDir("project"))
        var timeout = INIT_TIMEOUT

        when {
            !projectConfig.launchConfig.isSplashVisible -> {
                timeout = 0L
            }
            else -> {
                CoroutineScope(Dispatchers.Main).launch {
                    binding.slug.typeface = Typeface.createFromAsset(assets, "roboto_medium.ttf")
                    binding.slug.isVisible = true
                    binding.icon.isVisible = true
                }
            }
        }
        CoroutineScope(Dispatchers.Main).launch {
            if (timeout > 0 && Pref.isFirstUsing) {
                delay(timeout)
            }
            checkPermission(WRITE_EXTERNAL_STORAGE)
        }
        super.onCreate(savedInstanceState)
    }

    private fun runScript() {
        CoroutineScope(Dispatchers.IO).launch {
            val context = this@SplashActivity
            try {
                GlobalProjectLauncher.launch(context)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    startActivity(Intent(context, LogActivity::class.java))
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

    private fun getRequestPermissions(permissions: Array<out String>) = ArrayList<String>().let { list ->
        permissions.filterTo(list) { checkSelfPermission(it) == PERMISSION_DENIED }
        list.toTypedArray()
    }

    companion object {

        private const val PERMISSION_REQUEST_CODE = 11186

    }

}

