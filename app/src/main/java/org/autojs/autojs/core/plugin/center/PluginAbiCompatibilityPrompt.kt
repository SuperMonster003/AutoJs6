package org.autojs.autojs.core.plugin.center

import android.content.Context
import org.autojs.autojs.core.pref.StateKey
import org.autojs.autojs.core.pref.keys.DialogStateKeys
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.util.AndroidUtils
import org.autojs.autojs.util.DialogUtils.buildAndShowAdaptiveOrNull
import org.autojs.autojs6.R

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 27, 2026.
 */
object PluginAbiCompatibilityPrompt {

    fun runBeforeDownload(
        context: Context,
        item: PluginCenterItem,
        onContinue: () -> Unit,
    ) {
        runBeforeAction(
            context = context,
            item = item,
            remindKey = DialogStateKeys.PLUGIN_ABI_MISMATCH_DOWNLOAD_PROMPT,
            contentRes = R.string.prompt_plugin_abi_mismatch_before_download,
            onContinue = onContinue,
        )
    }

    fun runBeforeEnable(
        context: Context,
        item: PluginCenterItem,
        onContinue: () -> Unit,
    ) {
        PluginNativeAlignmentPrompt.runBeforeAction(context, item, download = false) {
            runBeforeAction(
                context = context,
                item = item,
                remindKey = DialogStateKeys.PLUGIN_ABI_MISMATCH_ENABLE_PROMPT,
                contentRes = R.string.prompt_plugin_abi_mismatch_before_enable,
                onContinue = onContinue,
            )
        }
    }

    private fun runBeforeAction(
        context: Context,
        item: PluginCenterItem,
        remindKey: StateKey<Boolean>,
        contentRes: Int,
        onContinue: () -> Unit,
    ) {
        val resolvedSupportedAbis = resolveSupportedAbisForPrompt(item)
        if (!PluginAbiUtils.hasMismatchWithDevice(resolvedSupportedAbis)) {
            onContinue()
            return
        }
        val pluginAbiText = PluginAbiUtils.formatSupportedAbisForDisplay(
            resolvedSupportedAbis,
            context.getString(R.string.text_unknown),
            context.getString(R.string.text_all),
        )
        val deviceAbiText = PluginAbiUtils.formatAbiListOrFallback(
            AndroidUtils.deviceFilteredAbiList,
            context.getString(R.string.text_none),
        )
        val content = context.getString(contentRes, pluginAbiText, deviceAbiText)
        buildAndShowAdaptiveOrNull {
            NotAskAgainDialog.Builder(context, remindKey).apply {
                title(R.string.text_prompt)
                content(content)
                positiveText(R.string.dialog_button_continue)
                positiveColorRes(R.color.dialog_button_not_recommended)
                onPositive { _, _ -> onContinue() }
                negativeText(R.string.dialog_button_cancel)
                negativeColorRes(R.color.dialog_button_default)
                cancelable(false)
            }.build()
        } ?: onContinue()
    }

    private fun resolveSupportedAbisForPrompt(item: PluginCenterItem): List<String>? {
        item.supportedAbis?.let { return it }
        val parsed = linkedSetOf<String>()
        listOfNotNull(item.updatableApkUrl, item.installableApkUrl).forEach { url ->
            val fromUrl = PluginAbiUtils.parseSupportedAbisFromText(url)
                .filterNot { it == PluginAbiUtils.ABI_UNIVERSAL }
            parsed += fromUrl
        }
        return parsed.takeIf { it.isNotEmpty() }?.let { PluginAbiUtils.normalizeAbiList(it) }
    }
}
