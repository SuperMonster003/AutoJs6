package org.autojs.autojs.ui.error

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.briefOfCurrentVersionInt
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityErrorReportBinding

/**
 * Created by Stardust on Feb 2, 2017.
 * Transformed by SuperMonster003 on Jan 20, 2026.
 */
class CrashReportActivity : BaseActivity() {

    private val mOnBackPressedCallback = object : OnBackPressedCallback(true) {

        // override fun onBackPressed() = exit()

        override fun handleOnBackPressed() {
            exit()
        }
    }

    private lateinit var crashMessage: String

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val msg = buildString {
            appendLine("${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(briefOfCurrentVersionInt(true))
            intent.getStringExtra("message")?.let { appendLine().appendLine(it.trimEnd()) }
            intent.getStringExtra("error")?.let { appendLine().appendLine(it.trimEnd()) }
        }.also { crashMessage = it }

        val binding = ActivityErrorReportBinding.inflate(layoutInflater).also {
            setContentView(it.root)
            it.toolbar.apply {
                title = getString(R.string.text_app_crashed)
                setSupportActionBar(this)
            }
            supportActionBar?.setHomeButtonEnabled(false)
        }

        val textView = binding.errorMessage.apply { text = msg }

        val textSizeScaleDetector = ViewUtils.TextSizeScaleDetector(this, textView)

        binding.errorMessageContainer.setOnTouchListener { _, event ->
            when (event.pointerCount) {
                2 -> false.also { textSizeScaleDetector.onTouchEvent(event) }
                else -> super.onTouchEvent(event)
            }
        }

        binding.copy.setOnClickListener { copy() }
        binding.restart.setOnClickListener { restart() }
        binding.exit.setOnClickListener { exit() }

        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)
    }

    private fun copy() = ClipboardUtils.setClip(this, crashMessage)

    private fun exit() = finishAffinity()

    private fun restart() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        intent.startSafely(this)
        exit()
    }

}

