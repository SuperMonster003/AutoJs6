package org.autojs.autojs.apkbuilder.template

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateProtocol

object TemplateApkResolver {

    private const val TAG = "TemplateApkResolver"

    data class Source(
        val pluginPackageName: String,
        val warnings: List<String>,
    )

    data class Decision(
        val source: Source?,
        val requiresUserConfirmation: Boolean,
        val warnings: List<String>,
        val errors: List<String> = emptyList(),
    ) {
        val canBuild: Boolean get() = source != null && errors.isEmpty()
    }

    @JvmStatic
    fun resolveBlocking(context: Context): Decision = runBlocking(Dispatchers.IO) {
        runCatching { resolve(context) }
            .onFailure { Log.w(TAG, "resolve failed", it) }
            .getOrElse { error ->
                unavailableDecision(
                    warnings = listOfNotNull(error.message),
                    errors = listOfNotNull(error.message),
                )
            }
    }

    suspend fun resolve(context: Context): Decision {
        val appContext = context.applicationContext
        val resolveResult = runCatching {
            ApkBuilderTemplatePluginHost.resolveRemoteBuilder(
                appContext,
                ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION,
                ApkBuilderTemplatePluginIds.PACKAGE_NAME,
            )
        }.onFailure {
            Log.w(TAG, "remote APK Builder plugin resolution failed", it)
        }
        val resolved = resolveResult.getOrNull() ?: return unavailableDecision(
            warnings = pluginUnavailableWarnings(appContext, resolveResult.exceptionOrNull()),
            errors = listOfNotNull(resolveResult.exceptionOrNull()?.message),
        )

        val warnings = resolved.hostCompatibilityWarnings
            .filter { it.isNotBlank() }
            .distinct()

        return Decision(
            source = Source(
                pluginPackageName = resolved.target.serviceInfo.packageName,
                warnings = warnings,
            ),
            requiresUserConfirmation = warnings.isNotEmpty(),
            warnings = warnings,
            errors = emptyList(),
        )
    }

    private fun unavailableDecision(
        warnings: List<String> = emptyList(),
        errors: List<String> = emptyList(),
    ): Decision {
        val cleanWarnings = warnings.filter { it.isNotBlank() }.distinct()
        val cleanErrors = errors.filter { it.isNotBlank() }.distinct()
        val message = "APK builder is unavailable: no enabled, official, compatible APK Builder plugin was found."
        return Decision(
            source = null,
            requiresUserConfirmation = false,
            warnings = (cleanWarnings + message).distinct(),
            errors = (cleanErrors + message).distinct(),
        )
    }

    private suspend fun pluginUnavailableWarnings(context: Context, error: Throwable?): List<String> {
        val hostWarnings = runCatching {
            ApkBuilderTemplatePluginHost.unavailableWarnings(context)
        }.onFailure {
            Log.w(TAG, "template plugin availability diagnostics failed", it)
        }.getOrDefault(emptyList())
        return (hostWarnings + listOfNotNull(error?.message))
            .filter { it.isNotBlank() }
            .distinct()
    }

}
