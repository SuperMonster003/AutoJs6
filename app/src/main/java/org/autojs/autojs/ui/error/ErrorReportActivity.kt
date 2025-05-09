package org.autojs.autojs.ui.error

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityErrorReportBinding
import java.util.*

/**
 * Created by Stardust on Feb 2, 2017.
 * Transformed by SuperMonster003 on Mar 10, 2025.
 */
class ErrorReportActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            ActivityErrorReportBinding.inflate(layoutInflater).also { binding ->
                setContentView(binding.root)
                binding.toolbar.apply {
                    title = getString(R.string.text_error_report)
                    setSupportActionBar(this)
                }
                supportActionBar?.setHomeButtonEnabled(false)
            }
        } catch (t: Throwable) {
            t.message?.let { msg ->
                copyToClip(msg)
                ViewUtils.showToast(this, msg)
            }
            Log.e(TAG, t.message ?: "", t)
            exitAfter(3000)
        }
    }

    override fun onResume() {
        super.onResume()

        val message = intent.getStringExtra("message")
        val errorDetail = intent.getStringExtra("error")

        val errorMessage = buildString {
            appendLine("Version: ${BuildConfig.VERSION_CODE}")
            appendLine("Android: ${Build.VERSION.SDK_INT}")
            message?.let { appendLine().appendLine(it) }
            errorDetail?.let { appendLine().appendLine(it) }
        }

        MaterialDialog.Builder(this)
            .title(R.string.text_app_crashed)
            .content(R.string.crash_feedback)
            .neutralText(R.string.text_copy_debug_info)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNegative { _, _ ->
                copyToClip(errorMessage)
                exitAfter(1500)
            }
            .positiveText(R.string.text_exit)
            .positiveColorRes(R.color.dialog_button_failure)
            .onPositive { _, _ -> exit() }
            .cancelable(false)
            .show()
    }

    private fun exitAfter(delay: Int) {
        Timer().schedule(object : TimerTask() {
            override fun run() = exit()
        }, delay.toLong())
    }

    private fun copyToClip(text: String) {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Debug", text))
        ViewUtils.showToast(this, R.string.text_already_copied_to_clip)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() = exit()

    private fun exit() = finishAffinity()

    companion object {

        private val TAG: String = ErrorReportActivity::class.java.simpleName

        @JvmStatic
        @JvmOverloads
        fun test(context: Context = GlobalAppContext.get()) {
            Intent(context, ErrorReportActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("message", "Test message\nSecond line")
                .putExtra("error", "Test error\nSecond line")
                .let { context.startActivity(it) }
        }

    }

}

