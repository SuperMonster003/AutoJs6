package org.autojs.autojs.core.plugin.mediainfo

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.mediainfo.api.IMediainfoPlugin
import org.autojs.plugin.mediainfo.api.MediainfoOptionKeys
import org.autojs.plugin.mediainfo.api.MediainfoPluginCapabilityKeys
import org.autojs.plugin.mediainfo.api.MediainfoPluginActions
import org.autojs.plugin.mediainfo.api.MediainfoPluginIds
import org.autojs.plugin.mediainfo.api.MediainfoSnapshotSchemas
import org.autojs.plugin.mediainfo.api.MediainfoQueryOptions
import org.autojs.plugin.mediainfo.api.MediainfoReportIdentity
import org.json.JSONObject
import java.io.File

data class MediainfoCapabilities(
    val snapshotSchemas: List<String>,
    val defaultSnapshotSchema: String,
    val engineVersion: String?,
    val streamCount: Boolean = false,
    val infoKinds: List<String> = listOf("TEXT"),
)

object MediainfoPluginHost {

    const val ACTION_MEDIAINFO = MediainfoPluginActions.MEDIAINFO

    private val host = AidlPluginHost(
        action = ACTION_MEDIAINFO,
        tag = "MediainfoPluginHost",
        moduleLabel = "MediaInfo plugin",
        proxyFactory = { binder -> IMediainfoPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
    )

    suspend fun discover(context: Context, packageName: String? = null) = host.discover(context, packageName)

    suspend fun probe(context: Context, packageName: String): PluginInfo = host.probe(context, packageName)

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    suspend fun capabilities(context: Context): MediainfoCapabilities {
        val target = host.selectOrThrow(context, engine = MediainfoPluginIds.ENGINE)
        return capabilitiesOf(target.pluginInfo)
    }

    suspend fun inform(context: Context, file: File): String {
        val target = host.selectOrThrow(context, engine = MediainfoPluginIds.ENGINE)
        return openReadOnly(file).use { fd ->
            MediainfoReportIdentity.inform(
                host.call(context, target) { proxy -> proxy.inform(fd, file.absolutePath) },
                file.absolutePath,
            )
        }
    }

    suspend fun get(context: Context, file: File, streamKind: String, streamNumber: Int, parameter: String, infoKind: String = "TEXT"): String {
        val query = MediainfoQueryOptions.parse(streamNumber, infoKind)
        val target = host.selectOrThrow(context, engine = MediainfoPluginIds.ENGINE)
        val capabilities = capabilitiesOf(target.pluginInfo)
        require(query.infoKind in capabilities.infoKinds) {
            "${MediainfoQueryOptions.UNSUPPORTED}: plugin does not support infoKind ${query.infoKind}"
        }
        return openReadOnly(file).use { fd ->
            host.call(context, target) { proxy ->
                if (query.infoKind == "TEXT") {
                    proxy.get(fd, file.absolutePath, streamKind, query.streamNumber, parameter)
                } else {
                    proxy.getDetail(fd, file.absolutePath, streamKind, query.streamNumber, parameter, query.infoKind)
                }
            }.let { value ->
                if (streamKind.equals("GENERAL", ignoreCase = true) && query.streamNumber == 0 &&
                    parameter == "CompleteName" && query.infoKind == "TEXT"
                ) file.absolutePath else value
            }
        }
    }

    suspend fun countGet(context: Context, file: File, streamKind: String): Int {
        val target = host.selectOrThrow(context, engine = MediainfoPluginIds.ENGINE)
        require(capabilitiesOf(target.pluginInfo).streamCount) {
            "${MediainfoQueryOptions.UNSUPPORTED}: plugin does not support stream counting"
        }
        return openReadOnly(file).use { fd ->
            host.call(context, target) { proxy -> proxy.countGet(fd, file.absolutePath, streamKind) }
        }
    }

    suspend fun snapshot(
        context: Context,
        file: File,
        includeInform: Boolean = true,
        includeSections: Boolean = true,
        schema: String? = null,
    ): String {
        val target = host.selectOrThrow(context, engine = MediainfoPluginIds.ENGINE)
        val advertisedSchemas = target.pluginInfo
            ?.capabilities
            ?.let { capabilities ->
                runCatching {
                    capabilities.getStringArray(MediainfoPluginCapabilityKeys.SNAPSHOT_SCHEMAS)
                }.getOrNull()
            }
        val resolvedSchema = MediainfoSnapshotSchemas.requireSupported(schema, advertisedSchemas)
        val options = Bundle().apply {
            putString(MediainfoOptionKeys.SOURCE_NAME, file.absolutePath)
            putBoolean(MediainfoOptionKeys.INCLUDE_INFORM, includeInform)
            putBoolean(MediainfoOptionKeys.INCLUDE_SECTIONS, includeSections)
            if (schema != null) {
                putString(MediainfoOptionKeys.SCHEMA, resolvedSchema)
            }
        }
        return openReadOnly(file).use { fd ->
            normalizeSnapshotIdentity(
                host.call(context, target) { proxy -> proxy.snapshot(fd, file.name, options) },
                file.absolutePath,
            )
        }
    }

    /** Also repairs reports from older plugins that ignore SOURCE_NAME. */
    internal fun normalizeSnapshotIdentity(raw: String, sourceName: String): String {
        val snapshot = JSONObject(raw)
        if (snapshot.has("inform")) {
            snapshot.put("inform", MediainfoReportIdentity.inform(snapshot.getString("inform"), sourceName))
        }
        snapshot.optJSONObject("sections")?.let { sections ->
            for (name in listOf("file", "general")) {
                val entries = sections.optJSONArray(name) ?: continue
                for (index in 0 until entries.length()) {
                    entries.optJSONObject(index)?.takeIf { it.has("completeName") }?.put("completeName", sourceName)
                }
            }
        }
        return snapshot.toString()
    }

    fun trimInform(inform: String): String {
        var result = inform
        replaceMap.forEach { (old, new) ->
            result = result.replace(old, new + " ".repeat(maxOf(0, old.length - new.length)))
        }
        var maxWhitespaceToTrim = 0
        result.split("\n").forEach { line ->
            countSpacesBeforeColon(line).let { if (maxWhitespaceToTrim == 0 || it in 1..maxWhitespaceToTrim) maxWhitespaceToTrim = it }
        }
        return result.split("\n")
            .joinToString("\n") {
                trimSpacesBeforeColon(it, maxOf(0, maxWhitespaceToTrim - 1))
                    .replace(Regex("^[A-Z][a-z]+$"), "# $0")
            }
    }

    private fun openReadOnly(file: File): ParcelFileDescriptor {
        require(file.isFile) { "MediaInfo target does not exist: ${file.path}" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun capabilitiesOf(pluginInfo: PluginInfo?): MediainfoCapabilities {
        val bundle = pluginInfo?.capabilities
        val advertisedSchemas = runCatching {
            bundle?.getStringArray(MediainfoPluginCapabilityKeys.SNAPSHOT_SCHEMAS)
        }.getOrNull().orEmpty()
        val snapshotSchemas = buildList {
            add(MediainfoSnapshotSchemas.V1)
            advertisedSchemas.forEach { schema ->
                if (schema in MediainfoSnapshotSchemas.VALUES && schema !in this) {
                    add(schema)
                }
            }
        }
        val advertisedDefault = runCatching {
            bundle?.getString(MediainfoPluginCapabilityKeys.DEFAULT_SNAPSHOT_SCHEMA)
        }.getOrNull()
        val defaultSnapshotSchema = advertisedDefault
            ?.takeIf { it in snapshotSchemas }
            ?: MediainfoSnapshotSchemas.DEFAULT
        val engineVersion = runCatching {
            bundle?.getString(MediainfoPluginCapabilityKeys.ENGINE_VERSION)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        return MediainfoCapabilities(
            snapshotSchemas = snapshotSchemas,
            defaultSnapshotSchema = defaultSnapshotSchema,
            engineVersion = engineVersion,
            streamCount = runCatching { bundle?.getBoolean(MediainfoPluginCapabilityKeys.STREAM_COUNT) }.getOrNull() == true,
            infoKinds = buildList {
                add("TEXT")
                runCatching { bundle?.getStringArray(MediainfoPluginCapabilityKeys.INFO_KINDS) }
                    .getOrNull().orEmpty().forEach { kind ->
                        if (kind in MediainfoQueryOptions.INFO_KINDS && kind !in this) add(kind)
                    }
            },
        )
    }

    private fun countSpacesBeforeColon(input: String): Int {
        val index = input.indexOf(':')
        if (index == -1) return 0
        var count = 0
        for (i in index - 1 downTo 0) {
            if (input[i] == ' ') {
                count++
            } else {
                break
            }
        }
        return count
    }

    private fun trimSpacesBeforeColon(input: String, count: Int): String {
        if (count == 0) return input
        @Suppress("RegExpSimplifiable")
        val regex = Regex("\\s{1,$count}+:")
        return regex.replace(input) { matchResult ->
            matchResult.value.replaceFirst("\\s+".toRegex(), "")
        }
    }

    private val replaceMap = arrayOf(
        "first frame" to "1st frame",
        "GOP, Open/Closed of 1st frame" to "GOP, O/C of 1st frame",
    )
}
