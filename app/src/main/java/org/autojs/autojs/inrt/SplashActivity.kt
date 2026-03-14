package org.autojs.autojs.inrt

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.graphics.Typeface
import android.os.Build
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
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivitySplashInrtBinding

/**
 * Created by Stardust on Feb 2, 2018.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val projectConfig = ProjectConfig.fromAssets(this, ProjectConfig.configFileOfDir("project"))
        val launchConfig = projectConfig.launchConfig

        val shouldHideMainPage = Pref.shouldHideLogs() || !launchConfig.isLogsVisible
        val shouldShowSplash = launchConfig.isSplashVisible && !shouldHideMainPage
        setTheme(if (shouldShowSplash) R.style.AppTheme_Splash else R.style.AppTheme_SevereTransparent)

        super.onCreate(savedInstanceState)

        var timeout = INIT_TIMEOUT

        applyLauncherVisibility(launchConfig.isLauncherVisible)

        when {
            !shouldShowSplash -> {
                timeout = 0L
                window.setWindowAnimations(0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            }
            else -> {
                val binding = ActivitySplashInrtBinding.inflate(layoutInflater).apply {
                    setContentView(root)
                }
                CoroutineScope(Dispatchers.Main).launch {
                    binding.slug.typeface = Typeface.createFromAsset(assets, "roboto_medium.ttf")
                    val slug = launchConfig.slug
                    binding.slug.text = slug
                    binding.slug.isVisible = slug.isNotBlank()
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

    private fun applyLauncherVisibility(visible: Boolean) {
        val state = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(
            ComponentName(this, SplashActivity::class.java),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    companion object {

        private const val PERMISSION_REQUEST_CODE = 11186

    }

}

