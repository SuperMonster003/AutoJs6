package org.autojs.autojs.core.plugin.explorer

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.explorer.api.ExplorerActionCapabilityKeys
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.autojs.plugin.explorer.api.ExplorerHostFileInfoKeys
import org.autojs.plugin.explorer.api.ExplorerHostFileInfoValues
import java.io.File
import java.util.Locale

internal data class ExplorerActionHostFileInfo(
    val title: String,
    val summary: String,
)

/**
 * Loads an optional plugin summary only after the host has completed its own package analysis.
 * Any discovery, capability, Binder, validation, or source-identity failure returns null so the
 * existing APK information dialog remains the complete fallback presentation.
 */
internal object ExplorerActionHostFileInfoLoader {

    suspend fun load(
        context: Context,
        sourceFile: File,
        expectedSourceSha256: String,
    ): ExplorerActionHostFileInfo? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val expectedSha256 = ExplorerActionHostFileInfoPolicy.normalizeSha256(expectedSourceSha256)
            ?: return@withContext null
        val source = runCatching { sourceFile.canonicalFile }.getOrNull()
            ?.takeIf { it.isFile && it.canRead() }
            ?: return@withContext null

        runCatching { ExplorerActionRegistry.refresh(appContext) }
            .onFailure { error -> Log.w(TAG, "Explorer Action refresh failed", error) }
            .getOrElse { return@withContext null }

        val actions = ExplorerActionRegistry.matchingActionsForFile(source)
            .filter { action ->
                action.descriptor.targetKind == ExplorerActionValues.TARGET_FILE &&
                    action.descriptor.cardinality == ExplorerActionValues.CARDINALITY_SINGLE &&
                    action.descriptor.accessMode == ExplorerActionValues.ACCESS_READ_ONLY &&
                    action.descriptor.presentation == ExplorerActionValues.PRESENTATION_ACTIVITY &&
                    action.descriptor.outputMode == ExplorerActionValues.OUTPUT_NONE &&
                    supportsHostFileInfo(action)
            }
            .distinctBy(ResolvedExplorerAction::packageName)
            .take(MAX_PLUGIN_ATTEMPTS)
        for (action in actions) {
            if (!PluginEnableStore.isEnabled(appContext, action.packageName, true)) continue
            if (!PluginTrustManager.isAuthorized(appContext, action.packageName)) continue
            val request = Bundle().apply {
                putInt(ExplorerHostFileInfoKeys.VERSION, ExplorerActionProtocol.HOST_FILE_INFO_VERSION)
                putString(ExplorerHostFileInfoKeys.ACTION_ID, action.descriptor.id)
                putString(
                    ExplorerHostFileInfoKeys.DISPLAY_NAME,
                    source.name.take(ExplorerActionProtocol.MAX_TARGET_DISPLAY_NAME_LENGTH),
                )
                putLong(ExplorerHostFileInfoKeys.SIZE, source.length())
                putLong(ExplorerHostFileInfoKeys.LAST_MODIFIED, source.lastModified())
                putString(ExplorerHostFileInfoKeys.EXPECTED_SOURCE_SHA256, expectedSha256)
            }
            val info = runCatching {
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    ExplorerActionPluginHost.getHostFileInfo(
                        context = appContext,
                        target = action.pluginTarget,
                        source = descriptor,
                        request = request,
                    )
                }
            }.onFailure { error ->
                Log.w(TAG, "Host file information failed: ${action.packageName}", error)
            }.getOrNull()?.let { result ->
                ExplorerActionHostFileInfoPolicy.parse(result, expectedSha256)
            }
            if (info != null) return@withContext info
        }
        null
    }

    @Suppress("DEPRECATION")
    private fun supportsHostFileInfo(action: ResolvedExplorerAction): Boolean {
        val version = action.pluginTarget.pluginInfo
            ?.capabilities
            ?.get(ExplorerActionCapabilityKeys.HOST_FILE_INFO_VERSION)
            ?.let(PluginCapabilityResolver::coerceLong)
            ?: return false
        return version in
            ExplorerActionProtocol.MIN_HOST_FILE_INFO_VERSION.toLong()..
                ExplorerActionProtocol.MAX_HOST_FILE_INFO_VERSION.toLong()
    }

    private const val TAG = "ExplorerHostFileInfo"
    private const val MAX_PLUGIN_ATTEMPTS = 3
}

internal object ExplorerActionHostFileInfoPolicy {

    fun parse(
        result: Bundle,
        expectedSourceSha256: String,
    ): ExplorerActionHostFileInfo? = runCatching {
        if (
            result.getInt(
                ExplorerHostFileInfoKeys.ERROR_CODE,
                ExplorerHostFileInfoValues.ERROR_UNRECOVERABLE,
            ) != ExplorerHostFileInfoValues.ERROR_NONE
        ) {
            return@runCatching null
        }
        if (
            result.getInt(ExplorerHostFileInfoKeys.VERSION, 0) !=
            ExplorerActionProtocol.HOST_FILE_INFO_VERSION
        ) {
            return@runCatching null
        }
        val expectedSha256 = normalizeSha256(expectedSourceSha256) ?: return@runCatching null
        val actualSha256 = normalizeSha256(
            result.getString(ExplorerHostFileInfoKeys.SOURCE_SHA256),
        ) ?: return@runCatching null
        if (actualSha256 != expectedSha256) return@runCatching null
        val title = normalizeTitle(result.getString(ExplorerHostFileInfoKeys.TITLE))
            ?: return@runCatching null
        val summary = normalizeSummary(result.getString(ExplorerHostFileInfoKeys.SUMMARY))
            ?: return@runCatching null
        ExplorerActionHostFileInfo(title, summary)
    }.getOrNull()

    internal fun normalizeTitle(value: String?): String? {
        val raw = value ?: return null
        if (raw.length > ExplorerActionProtocol.MAX_HOST_FILE_INFO_TITLE_LENGTH) return null
        return raw
            .map { character -> if (isUnsafe(character)) ' ' else character }
            .joinToString("")
            .replace(HORIZONTAL_WHITESPACE, " ")
            .trim()
            .takeIf(String::isNotEmpty)
    }

    internal fun normalizeSummary(value: String?): String? {
        val raw = value ?: return null
        if (raw.length > ExplorerActionProtocol.MAX_HOST_FILE_INFO_SUMMARY_LENGTH) return null
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        if (lines.size > ExplorerActionProtocol.MAX_HOST_FILE_INFO_SUMMARY_LINES) return null
        val normalized = lines.map { line ->
            if (line.length > ExplorerActionProtocol.MAX_HOST_FILE_INFO_SUMMARY_LINE_LENGTH) {
                return null
            }
            line.map { character -> if (isUnsafe(character)) ' ' else character }
                .joinToString("")
                .replace(HORIZONTAL_WHITESPACE, " ")
                .trim()
        }.joinToString("\n").trim()
        return normalized.takeIf(String::isNotEmpty)
    }

    internal fun normalizeSha256(value: String?): String? = value
        ?.takeIf(SHA256_PATTERN::matches)
        ?.lowercase(Locale.ROOT)

    private fun isUnsafe(character: Char): Boolean =
        character.isISOControl() || Character.getType(character) == Character.FORMAT.toInt()

    private val HORIZONTAL_WHITESPACE = Regex("[\\t\\x0B\\f ]+")
    private val SHA256_PATTERN = Regex("[0-9A-Fa-f]{64}")
}
