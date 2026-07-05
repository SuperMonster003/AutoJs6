package org.autojs.autojs.core.plugin.mediainfo

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.mediainfo.api.IMediainfoPlugin
import org.autojs.plugin.mediainfo.api.MediainfoOptionKeys
import org.autojs.plugin.mediainfo.api.MediainfoPluginActions
import org.autojs.plugin.mediainfo.api.MediainfoPluginIds
import java.io.File

object MediainfoPluginHost {

    const val ACTION_MEDIAINFO = MediainfoPluginActions.MEDIAINFO

    private val host = AidlPluginHost(
        action = ACTION_MEDIAINFO,
        tag = "MediainfoPluginHost",
        moduleLabel = "MediaInfo plugin",
        proxyFactory = { binder -> IMediainfoPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
    )

    suspend fun discover(context: Context) = host.discover(context)

    suspend fun probe(context: Context, packageName: String): PluginInfo = host.probe(context, packageName)

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    suspend fun inform(context: Context, file: File): String {
        val target = host.selectOrThrow(context, engine = MediainfoPluginIds.ENGINE)
        return openReadOnly(file).use { fd ->
            host.call(context, target) { proxy -> proxy.inform(fd, file.name) }
        }
    }

    suspend fun get(context: Context, file: File, streamKind: String, streamNumber: Int, parameter: String): String {
        val target = host.selectOrThrow(context, engine = MediainfoPluginIds.ENGINE)
        return openReadOnly(file).use { fd ->
            host.call(context, target) { proxy -> proxy.get(fd, file.name, streamKind, streamNumber, parameter) }
        }
    }

    suspend fun snapshot(
        context: Context,
        file: File,
        includeInform: Boolean = true,
        includeSections: Boolean = true,
    ): String {
        val target = host.selectOrThrow(context, engine = MediainfoPluginIds.ENGINE)
        val options = Bundle().apply {
            putBoolean(MediainfoOptionKeys.INCLUDE_INFORM, includeInform)
            putBoolean(MediainfoOptionKeys.INCLUDE_SECTIONS, includeSections)
        }
        return openReadOnly(file).use { fd ->
            host.call(context, target) { proxy -> proxy.snapshot(fd, file.name, options) }
        }
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
