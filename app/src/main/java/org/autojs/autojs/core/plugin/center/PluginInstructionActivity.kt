package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByThemeColorLuminance
import org.autojs.autojs.util.WebViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPluginInstructionBinding

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 22, 2026.
 */
class PluginInstructionActivity : BaseActivity() {

    private lateinit var binding: ActivityPluginInstructionBinding
    private var markdownContent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginInstructionBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val instructionHardCoded = intent.getStringExtra(EXTRA_INSTRUCTION_HARD_CODED).orEmpty()
        val instructionMarkdownUrl = intent.getStringExtra(EXTRA_INSTRUCTION_MARKDOWN_URL).orEmpty()
        val source = intent.getStringExtra(EXTRA_SOURCE)
            ?.runCatching { PluginIndexSource.valueOf(this) }
            ?.getOrNull()
            ?: PluginIndexSource.OFFICIAL
        val extraTitle = intent.getStringExtra(EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: packageName

        binding.toolbar.subtitle = extraTitle

        setToolbarAsBack(getString(R.string.text_plugin_instruction))

        binding.webView.settings.apply {
            setSupportMultipleWindows(true)
            @SuppressLint("SetJavaScriptEnabled")
            javaScriptEnabled = true
        }
        if (ViewUtils.isNightModeYes(this)) {
            WebViewUtils.adaptDarkMode(binding.webView)
        }
        WebViewUtils.excludeWebViewFromStatusBarAndNavigationBar(binding.webViewParent, binding.webView)

        lifecycleScope.launch {
            markdownContent = PluginInstructionProvider.load(
                context = this@PluginInstructionActivity,
                request = PluginInstructionProvider.Request(
                    packageName = packageName,
                    source = source,
                    instructionHardCoded = instructionHardCoded,
                    instructionMarkdownUrl = instructionMarkdownUrl,
                ),
            )
            binding.webView.loadMarkdown(markdownContent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_plugin_instruction, menu)
        setUpToolbarColors()
        return true
    }

    private fun setUpToolbarColors() {
        binding.toolbar.setMenuIconsColorByThemeColorLuminance(this)
        binding.toolbar.setNavigationIconColorByThemeColorLuminance(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_copy_instruction -> {
                val jsCode = extractJavaScriptCode(markdownContent)
                if (jsCode.isBlank()) {
                    ViewUtils.showToast(this, R.string.text_no_js_code_to_copy, true)
                } else {
                    ClipboardUtils.setClip(this, jsCode)
                    ViewUtils.showToast(this, R.string.text_already_copied_to_clip)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun extractJavaScriptCode(markdown: String): String {
        if (markdown.isBlank()) return ""
        val fenceRegex = Regex("```\\s*([A-Za-z0-9_-]*)\\s*\\r?\\n([\\s\\S]*?)```")
        val blocks = fenceRegex.findAll(markdown)
            .mapNotNull { match ->
                val lang = match.groupValues.getOrNull(1)?.trim()?.lowercase().orEmpty()
                val code = match.groupValues.getOrNull(2).orEmpty().trim()
                val isJavaScript = lang.isBlank() || lang == "js" || lang == "javascript"
                code.takeIf { isJavaScript && it.isNotBlank() }
            }.toList()
        return blocks.joinToString("\n\n")
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_INSTRUCTION_HARD_CODED = "extra_instruction_hard_coded"
        private const val EXTRA_INSTRUCTION_MARKDOWN_URL = "extra_instruction_markdown_url"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SOURCE = "extra_source"

        fun launch(
            context: Context,
            packageName: String,
            title: String,
            source: PluginIndexSource,
            instructionHardCoded: String?,
            instructionMarkdownUrl: String?,
        ) {
            Intent(context, PluginInstructionActivity::class.java)
                .putExtra(EXTRA_PACKAGE_NAME, packageName)
                .putExtra(EXTRA_INSTRUCTION_HARD_CODED, instructionHardCoded)
                .putExtra(EXTRA_INSTRUCTION_MARKDOWN_URL, instructionMarkdownUrl)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SOURCE, source.name)
                .startSafely(context)
        }
    }
}
