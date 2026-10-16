package org.autojs.plugin.tool.api

import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * One input descriptor of a call (PluginToolInputJson), as PluginToolCall hands it out (MCP roadmap D59). The base class
 * closes the descriptor when the call ends.
 *
 * zh-CN: 一次调用的一个输入描述符, 由 PluginToolCall 提供; 调用结束时由基类关闭描述符.
 */
class PluginToolInput internal constructor(
    /** The parameter the input belongs to. */
    val name: String,
    /** PluginToolValues.INPUT_SOURCE_*: the screen, a file, or a file of a directory parameter. */
    val source: String,
    val mimeType: String?,
    /** The file name; for a file of a directory parameter its name inside the directory. */
    val displayName: String?,
    /** Bytes behind the descriptor, -1 when unknown. */
    val byteCount: Long,
    /** Pixels of an image input, -1 when unknown. */
    val width: Int,
    val height: Int,
    val descriptor: ParcelFileDescriptor,
) {

    /** True for the screen the host captured for an image parameter given as PluginToolValues.IMAGE_SCREEN. */
    val isScreen: Boolean
        get() = source == PluginToolValues.INPUT_SOURCE_SCREEN

    /** A stream over the descriptor; closing it closes the descriptor, so open it once. */
    fun openStream(): InputStream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)

    /** Reads the whole input; more than [maxBytes] reports PluginToolErrorCodes.LIMIT_EXCEEDED. */
    @Throws(IOException::class)
    fun readBytes(maxBytes: Long): ByteArray = openStream().use { stream ->
        val out = ByteArrayOutputStream(if (byteCount in 0..minOf(maxBytes, Int.MAX_VALUE - 8L)) byteCount.toInt() else 8192)
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw PluginToolException(PluginToolErrorCodes.LIMIT_EXCEEDED, "the input $name has more than $maxBytes bytes")
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }

    internal fun close() {
        runCatching { descriptor.close() }
    }

    internal companion object {

        /** Pairs the entries of PluginToolInvokeKeys.INPUTS_JSON with the descriptors, entry i for descriptor i. */
        fun fromRequest(inputsJson: String?, descriptors: List<ParcelFileDescriptor>): List<PluginToolInput> {
            val entries = JSONArray(inputsJson ?: "[]")
            return descriptors.mapIndexed { index, descriptor ->
                val entry = entries.optJSONObject(index) ?: JSONObject()
                PluginToolInput(
                    name = entry.optString(PluginToolInputJson.NAME),
                    source = entry.optString(PluginToolInputJson.SOURCE),
                    mimeType = entry.stringOrNull(PluginToolInputJson.MIME_TYPE),
                    displayName = entry.stringOrNull(PluginToolInputJson.DISPLAY_NAME),
                    byteCount = entry.optLong(PluginToolInputJson.BYTE_COUNT, -1L),
                    width = entry.optInt(PluginToolInputJson.WIDTH, -1),
                    height = entry.optInt(PluginToolInputJson.HEIGHT, -1),
                    descriptor = descriptor,
                )
            }
        }

        private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    }
}
