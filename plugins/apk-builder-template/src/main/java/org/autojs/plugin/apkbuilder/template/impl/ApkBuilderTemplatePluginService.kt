package org.autojs.plugin.apkbuilder.template.impl

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateRequest
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateResult
import org.autojs.plugin.apkbuilder.template.IApkBuilderTemplatePlugin
import java.util.concurrent.Executors

class ApkBuilderTemplatePluginService : Service() {

    private val io = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private val binder = object : IApkBuilderTemplatePlugin.Stub() {

        override fun getInfo() = ApkBuilderTemplateMetadata.pluginInfo(this@ApkBuilderTemplatePluginService)

        override fun getTemplateInfo() = ApkBuilderTemplateMetadata.templateInfo(this@ApkBuilderTemplatePluginService)

        override fun openTemplate(request: ApkBuilderTemplateRequest): ApkBuilderTemplateResult {
            val info = getTemplateInfo()
            val warnings = arrayListOf<String>()
            val errors = arrayListOf<String>()

            if (request.requiredProtocolVersion > BuildConfig.PROTOCOL_VERSION) {
                warnings += "Plugin protocol is older than host requirement: plugin=${BuildConfig.PROTOCOL_VERSION}, host=${request.requiredProtocolVersion}"
            }
            if (request.hostPackageName.isNotBlank() && request.hostPackageName != BuildConfig.HOST_PACKAGE_NAME) {
                warnings += "Host package mismatch: plugin targets ${BuildConfig.HOST_PACKAGE_NAME}, host=${request.hostPackageName}"
            }
            if (request.hostVersionName.isNotBlank() && request.hostVersionName != BuildConfig.HOST_VERSION_NAME) {
                warnings += "Host versionName mismatch: plugin=${BuildConfig.HOST_VERSION_NAME}, host=${request.hostVersionName}"
            }
            if (request.hostVersionCode > 0L && request.hostVersionCode != BuildConfig.HOST_VERSION_CODE) {
                warnings += "Host versionCode mismatch: plugin=${BuildConfig.HOST_VERSION_CODE}, host=${request.hostVersionCode}"
            }

            val assetReadable = runCatching {
                assets.open(ApkBuilderTemplateMetadata.TEMPLATE_APK_ASSET).use { Unit }
            }
            if (assetReadable.isFailure) {
                errors += "Template asset is not readable: ${assetReadable.exceptionOrNull()?.message}"
                return ApkBuilderTemplateResult(
                    info = info,
                    compatibilityLevel = ApkBuilderTemplateResult.LEVEL_BLOCK,
                    warnings = warnings,
                    errors = errors,
                )
            }

            val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrElse { error ->
                errors += "Failed to create template pipe: ${error.message}"
                return ApkBuilderTemplateResult(
                    info = info,
                    compatibilityLevel = ApkBuilderTemplateResult.LEVEL_BLOCK,
                    warnings = warnings,
                    errors = errors,
                )
            }

            val readFd = pipe[0]
            val writeFd = pipe[1]
            io.execute {
                runCatching {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { output ->
                        assets.open(ApkBuilderTemplateMetadata.TEMPLATE_APK_ASSET).use { input ->
                            input.copyTo(output, 256 * 1024)
                            output.flush()
                        }
                    }
                }.onFailure {
                    runCatching { writeFd.close() }
                }
            }

            return ApkBuilderTemplateResult(
                info = info,
                templateFd = readFd,
                compatibilityLevel = if (warnings.isEmpty()) {
                    ApkBuilderTemplateResult.LEVEL_OK
                } else {
                    ApkBuilderTemplateResult.LEVEL_WARN
                },
                warnings = warnings,
                errors = errors,
                extras = Bundle().apply {
                    putString("templateAsset", ApkBuilderTemplateMetadata.TEMPLATE_APK_ASSET)
                },
            )
        }
    }
}
