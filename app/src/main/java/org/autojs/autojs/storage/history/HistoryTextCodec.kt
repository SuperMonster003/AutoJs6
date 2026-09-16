package org.autojs.autojs.storage.history

import org.autojs.autojs.util.StringUtils
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Decoding of revision blobs and of the on-disk file they are compared against.
 * zh-CN: revision blob 及与之比较的磁盘文件的解码.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object HistoryTextCodec {

    private val DEFAULT_CHARSET: Charset = StandardCharsets.UTF_8

    fun charsetOrDefault(encodingName: String): Charset =
        runCatching { Charset.forName(encodingName) }.getOrElse { DEFAULT_CHARSET }

    /**
     * Decode a revision whose BOM presence is known from metadata.
     * zh-CN: 解码一条 BOM 信息已由元数据记录的 revision.
     */
    fun decodeRevision(bytes: ByteArray, encodingName: String, hadBom: Boolean): String {
        val charset = charsetOrDefault(encodingName)
        val effective = if (hadBom) StringUtils.dropBom(bytes, charset) else bytes
        return String(effective, charset)
    }

    /**
     * Decode raw file bytes with the given encoding, dropping a BOM if one is present.
     * zh-CN: 用给定编码解码文件原始字节, 存在 BOM 时丢弃.
     */
    fun decodeFile(bytes: ByteArray, encodingName: String): String {
        val charset = charsetOrDefault(encodingName)
        val effective = if (StringUtils.hasBom(bytes, charset)) StringUtils.dropBom(bytes, charset) else bytes
        return String(effective, charset)
    }
}
