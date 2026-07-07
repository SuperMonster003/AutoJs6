package org.autojs.plugin.apkbuilder.template.impl

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateRequest
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateResult
import org.autojs.plugin.apkbuilder.template.IApkBuildCallback
import org.autojs.plugin.apkbuilder.template.IApkBuildSession
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

            if (request.requiredProtocolVersion > info.protocolVersion) {
                warnings += "Plugin protocol is older than host requirement: plugin=${info.protocolVersion}, host=${request.requiredProtocolVersion}"
            }
            if (request.hostPackageName.isNotBlank() && request.hostPackageName != info.hostPackageName) {
                warnings += "Host package mismatch: plugin targets ${info.hostPackageName}, host=${request.hostPackageName}"
            }
            if (request.hostVersionName.isNotBlank() && request.hostVersionName != info.hostVersionName) {
                warnings += "Host versionName mismatch: plugin=${info.hostVersionName}, host=${request.hostVersionName}"
            }
            if (request.hostVersionCode > 0L && request.hostVersionCode != info.hostVersionCode) {
                warnings += "Host versionCode mismatch: plugin=${info.hostVersionCode}, host=${request.hostVersionCode}"
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
                    putString("runtimeKitAsset", ApkBuilderTemplateMetadata.RUNTIME_KIT_JSON_ASSET)
                },
            )
        }

        override fun openBuildSession(
            request: ApkBuildRequest,
            callback: IApkBuildCallback?,
        ): IApkBuildSession = RemoteApkBuildSession(
            context = this@ApkBuilderTemplatePluginService,
            request = request,
            callback = callback,
            executor = io,
        )
    }
}
