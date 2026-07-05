package org.autojs.plugin.apkbuilder.template.impl

import android.content.Context
import android.os.Bundle
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateCapabilityKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateInfo
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateProtocol
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo

object ApkBuilderTemplateMetadata {

    const val TEMPLATE_APK_ASSET = "template/template.apk"
    private const val TEMPLATE_SHA256_ASSET = "template/template.sha256"

    fun pluginInfo(context: Context): PluginInfo {
        val templateInfo = templateInfo(context)
        return PluginInfo(
            name = context.getString(R.string.plugin_name),
            description = context.getString(R.string.plugin_description),
            instruction = context.getString(R.string.plugin_instruction),
            author = "AutoJs6",
            collaborators = null,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            versionDate = null,
            id = ApkBuilderTemplatePluginIds.ID,
            engine = ApkBuilderTemplatePluginIds.ENGINE,
            variant = ApkBuilderTemplatePluginIds.VARIANT_INRT_UNIVERSAL,
            supportedAbis = null,
            capabilities = templateInfo.capabilities,
        )
    }

    fun templateInfo(context: Context): ApkBuilderTemplateInfo {
        val templateSha256 = readTextAssetOrNull(context, TEMPLATE_SHA256_ASSET)?.trim()?.takeIf { it.isNotEmpty() }
        return ApkBuilderTemplateInfo(
            id = ApkBuilderTemplatePluginIds.ID,
            name = context.getString(R.string.plugin_name),
            description = context.getString(R.string.plugin_description),
            author = "AutoJs6",
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            versionDate = null,
            protocolVersion = BuildConfig.PROTOCOL_VERSION,
            hostPackageName = BuildConfig.HOST_PACKAGE_NAME,
            hostVersionName = BuildConfig.HOST_VERSION_NAME,
            hostVersionCode = BuildConfig.HOST_VERSION_CODE,
            templatePackageName = BuildConfig.TEMPLATE_PACKAGE_NAME,
            templateSha256 = templateSha256,
            templateSizeBytes = templateSizeBytes(context),
            capabilities = Bundle().apply {
                putLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION, BuildConfig.HOST_VERSION_CODE)
                putString(ApkBuilderTemplateCapabilityKeys.BUILT_FOR_HOST_VERSION_NAME, BuildConfig.HOST_VERSION_NAME)
                putLong(ApkBuilderTemplateCapabilityKeys.BUILT_FOR_HOST_VERSION_CODE, BuildConfig.HOST_VERSION_CODE)
                putInt(ApkBuilderTemplateCapabilityKeys.PROTOCOL_VERSION, BuildConfig.PROTOCOL_VERSION)
                putString(ApkBuilderTemplateCapabilityKeys.TEMPLATE_KIND, ApkBuilderTemplatePluginIds.VARIANT_INRT_UNIVERSAL)
                putString(ApkBuilderTemplateCapabilityKeys.TEMPLATE_PACKAGE_NAME, BuildConfig.TEMPLATE_PACKAGE_NAME)
                putString(ApkBuilderTemplateCapabilityKeys.TEMPLATE_SHA256, templateSha256)
                putBoolean(ApkBuilderTemplateCapabilityKeys.SUPPORTS_TEMPLATE_APK, true)
                putBoolean(ApkBuilderTemplateCapabilityKeys.SUPPORTS_REMOTE_BUILD, BuildConfig.ENABLE_REMOTE_BUILD)
                putInt(ApkBuilderTemplateCapabilityKeys.REMOTE_BUILD_PROTOCOL_VERSION, BuildConfig.REMOTE_BUILD_PROTOCOL_VERSION)
                putString("remoteBuildStatus", if (BuildConfig.ENABLE_REMOTE_BUILD) "experimental" else "disabled")
                putInt("remoteBuildApiVersion", ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION)
            },
        )
    }

    private fun readTextAssetOrNull(context: Context, assetPath: String): String? {
        return runCatching {
            context.assets.open(assetPath).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private fun templateSizeBytes(context: Context): Long {
        return runCatching {
            context.assets.open(TEMPLATE_APK_ASSET).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                }
                total
            }
        }.getOrDefault(0L)
    }
}
