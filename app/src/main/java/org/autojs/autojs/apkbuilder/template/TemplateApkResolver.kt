package org.autojs.autojs.apkbuilder.template

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.dongliu.apk.parser.ApkFile
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateProtocol
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateRequest
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateResult
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

object TemplateApkResolver {

    private const val TAG = "TemplateApkResolver"

    sealed class Source(open val warnings: List<String>) {
        abstract fun open(context: Context): InputStream

        class BuiltIn(
            override val warnings: List<String> = emptyList(),
        ) : Source(warnings) {
            override fun open(context: Context): InputStream {
                return context.assets.open(ApkBuilder.TEMPLATE_APK_NAME)
            }
        }

        class Unavailable(
            val message: String,
            override val warnings: List<String> = emptyList(),
        ) : Source(warnings) {
            override fun open(context: Context): InputStream {
                throw FileNotFoundException(message)
            }
        }

        class Plugin(
            val pluginPackageName: String,
            val templateFile: File,
            override val warnings: List<String>,
        ) : Source(warnings) {
            override fun open(context: Context): InputStream {
                return FileInputStream(templateFile)
            }
        }
    }

    data class Decision(
        val source: Source,
        val requiresUserConfirmation: Boolean,
        val warnings: List<String>,
        val errors: List<String> = emptyList(),
        val canBuild: Boolean = true,
        val builtInTemplateAvailable: Boolean = true,
    )

    @JvmStatic
    fun resolveBlocking(context: Context): Decision = runBlocking(Dispatchers.IO) {
        runCatching { resolve(context) }
            .onFailure { Log.w(TAG, "resolve failed, fallback to bundled template if available", it) }
            .getOrElse { error ->
                builtInDecision(
                    context = context.applicationContext,
                    warnings = listOfNotNull(error.message),
                    errors = listOfNotNull(error.message),
                )
            }
    }

    suspend fun resolve(context: Context): Decision {
        val appContext = context.applicationContext
        val opened = runCatching {
            ApkBuilderTemplatePluginHost.openTemplate(appContext, buildRequest(appContext))
        }.onFailure {
            Log.w(TAG, "template plugin open failed, fallback to bundled template if available", it)
        }.getOrNull() ?: return builtInDecision(appContext)

        val result = opened.result
        val templateFd = result.templateFd ?: return builtInDecision(
            context = appContext,
            warnings = opened.hostCompatibilityWarnings + result.warnings + result.errors,
            errors = result.errors,
        )

        val cacheFile = runCatching {
            copyTemplateToCache(appContext, opened.target.serviceInfo.packageName, templateFd)
        }.onFailure {
            Log.w(TAG, "copy plugin template failed, fallback to bundled template if available", it)
        }.getOrNull() ?: return builtInDecision(
            context = appContext,
            warnings = opened.hostCompatibilityWarnings + result.warnings + result.errors,
            errors = result.errors,
        )

        val validation = validateTemplateFile(cacheFile, result)
        if (validation.fatalErrors.isNotEmpty()) {
            cacheFile.delete()
            return builtInDecision(
                context = appContext,
                warnings = opened.hostCompatibilityWarnings + result.warnings + result.errors + validation.fatalErrors,
                errors = result.errors + validation.fatalErrors,
            )
        }

        val warnings = (
                opened.hostCompatibilityWarnings +
                        result.warnings +
                        result.errors +
                        validation.warnings
                ).filter { it.isNotBlank() }.distinct()

        return Decision(
            source = Source.Plugin(
                pluginPackageName = opened.target.serviceInfo.packageName,
                templateFile = cacheFile,
                warnings = warnings,
            ),
            requiresUserConfirmation = result.compatibilityLevel != ApkBuilderTemplateResult.LEVEL_OK || warnings.isNotEmpty(),
            warnings = warnings,
            errors = result.errors,
            builtInTemplateAvailable = hasBuiltInTemplate(appContext),
        )
    }

    private fun buildRequest(context: Context): ApkBuilderTemplateRequest {
        return ApkBuilderTemplateRequest(
            hostPackageName = context.packageName,
            hostVersionName = BuildConfig.VERSION_NAME,
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            requiredProtocolVersion = ApkBuilderTemplateProtocol.VERSION,
            allowRiskyTemplate = true,
        )
    }

    private fun builtInDecision(
        context: Context,
        warnings: List<String> = emptyList(),
        errors: List<String> = emptyList(),
    ): Decision {
        val cleanWarnings = warnings.filter { it.isNotBlank() }.distinct()
        val cleanErrors = errors.filter { it.isNotBlank() }.distinct()
        if (!hasBuiltInTemplate(context)) {
            val message = "APK builder template is unavailable: no readable plugin template was opened and built-in template.apk is not bundled."
            val allWarnings = (cleanWarnings + message).distinct()
            val allErrors = (cleanErrors + message).distinct()
            return Decision(
                source = Source.Unavailable(message, allWarnings),
                requiresUserConfirmation = false,
                warnings = allWarnings,
                errors = allErrors,
                canBuild = false,
                builtInTemplateAvailable = false,
            )
        }

        return Decision(
            source = Source.BuiltIn(cleanWarnings),
            requiresUserConfirmation = false,
            warnings = cleanWarnings,
            errors = cleanErrors,
            builtInTemplateAvailable = true,
        )
    }

    private fun hasBuiltInTemplate(context: Context): Boolean {
        return runCatching {
            context.assets.open(ApkBuilder.TEMPLATE_APK_NAME).use { }
        }.isSuccess
    }

    private fun copyTemplateToCache(
        context: Context,
        packageName: String,
        pfd: ParcelFileDescriptor,
    ): File {
        val dir = File(context.cacheDir, "apk-template-plugin").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val out = File(dir, "${packageName.replace('.', '_')}-${System.nanoTime()}.apk")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            FileOutputStream(out, false).use { output ->
                input.copyTo(output, 256 * 1024)
                output.flush()
            }
        }
        return out
    }

    private fun validateTemplateFile(
        file: File,
        result: ApkBuilderTemplateResult,
    ): TemplateValidation {
        val warnings = mutableListOf<String>()
        val fatalErrors = mutableListOf<String>()

        if (!file.isFile || file.length() <= 0L) {
            fatalErrors += "Plugin returned an empty template APK."
            return TemplateValidation(warnings, fatalErrors)
        }

        runCatching {
            ZipFile(file).use { zip ->
                listOf(
                    "AndroidManifest.xml",
                    "resources.arsc",
                    "classes.dex",
                    "assets/init.js",
                ).forEach { entry ->
                    if (zip.getEntry(entry) == null) {
                        fatalErrors += "Template APK does not contain required entry: $entry"
                    }
                }
            }
        }.onFailure {
            fatalErrors += "Template APK cannot be opened as ZIP: ${it.message}"
        }

        if (fatalErrors.isNotEmpty()) {
            return TemplateValidation(warnings, fatalErrors)
        }

        val declaredSha = result.info?.templateSha256
        if (!declaredSha.isNullOrBlank()) {
            val actualSha = sha256(file)
            if (!declaredSha.equals(actualSha, ignoreCase = true)) {
                warnings += "Template SHA-256 mismatch: declared=$declaredSha actual=$actualSha"
            }
        } else {
            warnings += "Template plugin did not provide SHA-256 metadata."
        }

        runCatching {
            ApkFile(file).use { apk ->
                val meta = apk.apkMeta
                if (meta.packageName != ApkBuilder.INRT_APP_ID) {
                    warnings += "Template packageName mismatch: expected=${ApkBuilder.INRT_APP_ID}, actual=${meta.packageName}"
                }
                val expectedVersionName = result.info?.hostVersionName
                if (!expectedVersionName.isNullOrBlank() && meta.versionName != expectedVersionName) {
                    warnings += "Template versionName mismatch: template=${meta.versionName}, host=$expectedVersionName"
                }
            }
        }.onFailure {
            warnings += "Failed to parse template APK metadata: ${it.message}"
        }

        return TemplateValidation(warnings, fatalErrors)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class TemplateValidation(
        val warnings: List<String>,
        val fatalErrors: List<String>,
    )
}
