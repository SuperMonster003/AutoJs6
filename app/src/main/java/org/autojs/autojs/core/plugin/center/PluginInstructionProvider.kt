package org.autojs.autojs.core.plugin.center

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs6.R
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 21, 2026.
 */
object PluginInstructionProvider {

    private val mountPathPlaceholderRegex = Regex("""\{\{\s*MOUNT_PATH\s*\}\}""")
    private val markdownCache = ConcurrentHashMap<String, String>()

    data class Request(
        val packageName: String,
        val source: PluginIndexSource,
        val instructionHardCoded: String?,
        val instructionMarkdownUrl: String? = null,
    )

    suspend fun load(context: Context, request: Request): String = withContext(Dispatchers.IO) {
        when (request.source) {
            PluginIndexSource.OFFICIAL -> {
                val instruction = PluginResourceResolver.resolve(
                    context,
                    request.packageName,
                    "plugin_instruction" to PluginResourceResolver.ResType.RAW,
                    request.instructionHardCoded,
                )?.trim().takeUnless {
                    it.isNullOrBlank()
                } ?: context.getString(R.string.text_no_instruction_available)
                replaceMountPathPlaceholder(request.packageName, instruction)
            }
            PluginIndexSource.THIRD_PARTY -> {
                val instruction = loadThirdPartyInstruction(context, request)
                val resolvedInstruction = replaceMountPathPlaceholder(request.packageName, instruction)
                prependAutoMountHintIfEnabled(context, request.packageName, resolvedInstruction)
            }
        }
    }

    private fun loadThirdPartyInstruction(
        context: Context,
        request: Request,
    ): String {
        val markdownInstruction = request.instructionMarkdownUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { fetchMarkdownInstruction(it) }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (!markdownInstruction.isNullOrBlank()) {
            return markdownInstruction
        }
        val hardCodedInstruction = request.instructionHardCoded
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (!hardCodedInstruction.isNullOrBlank()) {
            return hardCodedInstruction
        }
        return buildThirdPartyInstruction(
            context = context,
            packageName = request.packageName,
        )
    }

    private fun prependAutoMountHintIfEnabled(
        context: Context,
        packageName: String,
        instruction: String,
    ): String {
        val config = PluginMountStore.getMountConfig(packageName)
        if (!config.enabled) return instruction

        val mountPathInfo = resolveMountPathInfo(packageName)
        val body = instruction.trim().ifBlank {
            context.getString(R.string.text_no_instruction_available)
        }
        return buildString {
            appendLine(
                context.getString(
                    R.string.text_plugin_instruction_auto_mounted_hint,
                    mountPathInfo.fullPath,
                    mountPathInfo.displayPath,
                ),
            )
            appendLine()
            appendLine("---")
            appendLine()
            append(body)
        }
    }

    private fun fetchMarkdownInstruction(url: String): String? {
        markdownCache[url]?.let { return it }
        val text = runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("Accept", "text/markdown,text/plain,*/*")
                setRequestProperty("User-Agent", "AutoJs6-PluginInstruction")
            }
            try {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
        if (!text.isNullOrBlank()) {
            markdownCache[url] = text
        }
        return text
    }

    private fun buildThirdPartyInstruction(
        context: Context,
        packageName: String,
    ): String {
        val config = PluginMountStore.getMountConfig(packageName)
        val mountPathInfo = resolveMountPathInfo(packageName)
        val header = context.getString(R.string.text_plugin_instruction_example_title)
        return if (config.enabled) {
            buildString {
                appendLine(header)
                appendLine("```js")
                append("console.log(")
                append(mountPathInfo.displayPath)
                appendLine(".exec());")
                append("```")
            }
        } else {
            buildString {
                appendLine(header)
                appendLine("```js")
                append("let myPlugin = plugins.load('")
                append(packageName)
                appendLine("');")
                appendLine("console.log(myPlugin.exec());")
                append("```")
            }
        }
    }

    private fun replaceMountPathPlaceholder(packageName: String, instruction: String): String {
        if (instruction.isBlank()) return instruction
        if (!mountPathPlaceholderRegex.containsMatchIn(instruction)) return instruction
        val mountPath = resolveMountPathInfo(packageName).displayPath
        return instruction.replace(mountPathPlaceholderRegex, mountPath)
    }

    private fun resolveMountPathInfo(packageName: String): MountPathInfo {
        val config = PluginMountStore.getMountConfig(packageName)
        val effectiveObjectName = config.objectName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: PluginMountStore.defaultObjectName(packageName)
        val effectiveMountPoint = PluginMountStore.normalizeMountPoint(config.mountPoint)
        val displayPath = if (effectiveMountPoint.isNullOrBlank()) {
            effectiveObjectName
        } else {
            "$effectiveMountPoint.$effectiveObjectName"
        }
        val fullPath = if (effectiveMountPoint.isNullOrBlank()) {
            "global.$effectiveObjectName"
        } else {
            "$effectiveMountPoint.$effectiveObjectName"
        }
        return MountPathInfo(
            displayPath = displayPath,
            fullPath = fullPath,
        )
    }

    private data class MountPathInfo(
        val displayPath: String,
        val fullPath: String,
    )
}
