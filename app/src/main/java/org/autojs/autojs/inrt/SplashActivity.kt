package org.autojs.autojs.inrt

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
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

    private lateinit var launchGate: InrtLaunchGate

    override fun onCreate(savedInstanceState: Bundle?) {
        val projectConfig = ProjectConfig.fromAssets(this, ProjectConfig.configFileOfDir("project"))
        val launchConfig = projectConfig.launchConfig

        val shouldHideMainPage = Pref.shouldHideLogs() || !launchConfig.isLogsVisible
        val shouldShowSplash = launchConfig.isSplashVisible && !shouldHideMainPage
        setTheme(if (shouldShowSplash) R.style.AppTheme_Splash else R.style.AppTheme_SevereTransparent)

        super.onCreate(savedInstanceState)
        launchGate = InrtLaunchGate(this, launchConfig, ::runScript, ::finish)

        var timeout = INIT_TIMEOUT

        Pref.updateLauncherVisibility(launchConfig)

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
        lifecycleScope.launch {
            if (timeout > 0 && Pref.isFirstUsing) {
                delay(timeout)
            }
            launchGate.begin()
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
                    finishAfterLaunchFailure()
                }
            }
        }
    }

    private fun finishAfterLaunchFailure() {
        if (isFinishing || isDestroyed) {
            return
        }
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

}
