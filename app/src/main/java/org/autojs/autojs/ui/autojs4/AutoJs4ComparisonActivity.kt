package org.autojs.autojs.ui.autojs4

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import org.autojs.autojs.core.plugin.autojs4.AutoJs4Comparison
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityAutojs4ComparisonBinding
import java.util.Locale

/**
 * Result page of "Run with AutoJs4 and compare": summary, line diff of both console outputs and a
 * one-tap copy of an issue template. zh-CN: "使用 AutoJs4 运行并对照" 的结果页: 摘要, 两次控制台输出的逐行差异,
 * 以及一键复制 issue 模板.
 */
class AutoJs4ComparisonActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = AutoJs4Comparison.latest ?: run {
            finish()
            return
        }
        val binding = ActivityAutojs4ComparisonBinding.inflate(layoutInflater).also {
            setContentView(it.root)
            it.toolbar.apply {
                title = getString(R.string.text_autojs4_comparison)
                setSupportActionBar(this)
            }
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            it.toolbar.setNavigationOnClickListener { finish() }
        }
        binding.summary.text = summary(result)
        binding.diff.text = if (result.diff.isEmpty()) "" else result.diff.joinToString("\n") { "${it.tag} ${it.text}" }
        binding.copy.setOnClickListener {
            ClipboardUtils.setClip(this, issueTemplate(result))
            ViewUtils.showToast(this, R.string.text_already_copied_to_clip)
        }
        binding.close.setOnClickListener { finish() }
    }

    private fun summary(result: AutoJs4Comparison.Result): String = buildString {
        appendLine(result.targetPath)
        appendLine()
        appendLine(
            if (result.identical) getString(R.string.text_autojs4_comparison_identical)
            else getString(R.string.text_autojs4_comparison_different, result.differences)
        )
        appendLine(getString(R.string.text_autojs4_comparison_duration, duration(result.autoJs6.durationMillis), duration(result.autoJs4.durationMillis)))
        for (side in listOf(result.autoJs6, result.autoJs4)) {
            outcomeLine(side)?.let { appendLine(it) }
        }
    }.trimEnd()

    private fun outcomeLine(side: AutoJs4Comparison.Side): String? = when (side.outcome) {
        AutoJs4Comparison.Outcome.SUCCEEDED -> null
        AutoJs4Comparison.Outcome.TIMED_OUT -> getString(R.string.text_autojs4_comparison_timed_out, side.engine, AutoJs4Comparison.TIMEOUT_SECONDS)
        AutoJs4Comparison.Outcome.FAILED -> getString(R.string.text_autojs4_comparison_failed, side.engine, side.detail ?: "")
    }

    private fun duration(millis: Long): String = String.format(Locale.US, "%.3f s", millis / 1000.0)

    private fun issueTemplate(result: AutoJs4Comparison.Result): String = buildString {
        appendLine("### Script")
        appendLine()
        appendLine("`${result.targetPath}`")
        appendLine()
        appendLine("### Environment")
        appendLine()
        appendLine("- AutoJs6 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), AutoJs4 Runtime ${pluginVersion()}")
        appendLine("- ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("### Result")
        appendLine()
        appendLine(summary(result).lines().drop(2).joinToString("\n") { "- $it" })
        appendLine()
        for (side in listOf(result.autoJs6, result.autoJs4)) {
            appendLine("### ${side.engine} output")
            appendLine()
            appendLine("```text")
            side.lines.forEach { appendLine(it) }
            appendLine("```")
            appendLine()
        }
        appendLine("### Diff (- AutoJs6, + AutoJs4)")
        appendLine()
        appendLine("```diff")
        result.diff.forEach { appendLine("${it.tag} ${it.text}") }
        appendLine("```")
    }

    private fun pluginVersion(): String = try {
        val info = packageManager.getPackageInfo(AutoJs4RuntimePluginUi.OFFICIAL_PACKAGE, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        "${info.versionName} ($code)"
    } catch (_: PackageManager.NameNotFoundException) {
        "-"
    }

    companion object {

        fun launch(context: Context) {
            context.startActivity(Intent(context, AutoJs4ComparisonActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

    }

}
