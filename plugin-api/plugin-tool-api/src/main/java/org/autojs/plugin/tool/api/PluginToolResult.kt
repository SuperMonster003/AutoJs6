package org.autojs.plugin.tool.api

import android.os.ParcelFileDescriptor
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The result `onCall` of PluginToolProviderService returns (MCP roadmap D59): the result JSON, an optional plain-text summary
 * and the file outputs. Outputs the caller does not want are dropped and every descriptor is closed by the base class.
 *
 * zh-CN: PluginToolProviderService 的 `onCall` 返回的结果: 结果 JSON, 可选的纯文本摘要与文件输出. 调用方不需要的输出会被丢弃,
 * 全部描述符由基类关闭.
 */
class PluginToolResult(
    /** The result as JSON, an object when the tool declares an output schema. */
    val json: String,
) {

    constructor(json: JSONObject) : this(json.toString())

    internal class Output(val name: String, val mimeType: String, val descriptor: ParcelFileDescriptor)

    internal val outputs = ArrayList<Output>()

    /** Optional short summary that consumers may show instead of the JSON. */
    var text: String? = null
        private set

    fun withText(text: String?): PluginToolResult = apply { this.text = text }

    /** Adds the file output [name]; the base class closes [descriptor] after it reported the result. */
    fun withOutput(name: String, mimeType: String, descriptor: ParcelFileDescriptor): PluginToolResult = apply {
        outputs += Output(name, mimeType, descriptor)
    }

    /** Adds [file] as the file output [name]; it is opened at once, so the tool may delete the file afterwards. */
    @Throws(IOException::class)
    fun withOutput(name: String, mimeType: String, file: File): PluginToolResult =
        withOutput(name, mimeType, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))

    /** Adds [bytes] as the file output [name], through a temporary file that is deleted at once. */
    @Throws(IOException::class)
    fun withOutput(name: String, mimeType: String, bytes: ByteArray): PluginToolResult {
        val file = File.createTempFile("plugin-tool-", ".out")
        try {
            file.writeBytes(bytes)
            return withOutput(name, mimeType, file)
        } finally {
            file.delete()
        }
    }

    internal fun closeOutputs() {
        outputs.forEach { runCatching { it.descriptor.close() } }
    }
}
