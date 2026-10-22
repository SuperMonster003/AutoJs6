package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.AndroidSettingsKeys
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.core.pref.archive.SettingsDeviceKey
import org.autojs.autojs.core.pref.archive.SettingsPassword
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs6.BuildConfig
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * Writes the archive of the export wizard (docs/settings/ROADMAP.md P6.4, T7 / T11): the snapshot of the chosen
 * categories into the document the user created, at most [SettingsArchive.MAX_BYTES]. A failed write deletes the
 * document, so no partial archive stays behind (the writer leaves that to the caller). Credentials are sealed for the
 * device key (D23 / T21, P6.5), and for the password the user set as well (P7.7). Values are never logged (R6).
 *
 * zh-CN: 写入导出向导的归档 (ROADMAP P6.4, T7 / T11): 把所选分类的快照写入用户创建的文档, 不超过 [SettingsArchive.MAX_BYTES].
 * 写入失败时删除该文档, 不留下不完整的归档 (写入器把这一步交给调用方). 凭据以设备密钥加密 (D23 / T21, P6.5), 用户设了口令时另以口令
 * 加密 (P7.7). 不记录任何值 (R6).
 */
object SettingsExporter {

    /** What was written: the name of the document and the counts of the done page. zh-CN: 写入结果: 文档名与完成页的计数. */
    data class Result(val fileName: String, val categories: Int, val entries: Int, val unexportable: Int)

    /**
     * Writes [snapshot] to [out] as exported at [now], sensitive categories sealed for [key] (and [password]); the
     * manifest written is returned. Pure (R3).
     * zh-CN: 把 [snapshot] 以导出时间 [now] 写入 [out], 敏感分类以 [key] (及 [password]) 加密; 返回写入的 manifest. 纯 JVM (R3).
     */
    @JvmStatic
    @JvmOverloads
    fun write(
        out: OutputStream,
        snapshot: SettingsSnapshot,
        now: Long,
        host: SettingsArchiveManifest.Host,
        device: SettingsArchiveManifest.Device,
        maxBytes: Long = SettingsArchive.MAX_BYTES,
        key: SettingsDeviceKey? = null,
        password: SettingsPassword? = null,
    ): SettingsArchiveManifest =
        SettingsArchiveWriter.write(out, snapshot, SettingsArchive.exportTime(now), host, device, maxBytes = maxBytes, key = key, password = password)

    /** [Result] of a written [manifest] and the [snapshot] it came from. zh-CN: 由写入的 manifest 与快照得出结果. */
    @JvmStatic
    fun result(fileName: String, manifest: SettingsArchiveManifest, snapshot: SettingsSnapshot) =
        Result(fileName, manifest.categories.size, manifest.categories.sumOf { it.entries }, snapshot.unexportable.size)

    /**
     * Captures the settings of [selected] (category ids) and writes them to the document [uri] (truncated first), the
     * credentials with a password slot of [password] as well (P7.7); deletes the document and rethrows when that fails.
     * Runs on a background thread.
     * zh-CN: 读取 [selected] (分类 id) 的设置并写入文档 [uri] (先截断), 凭据另加 [password] 的口令槽 (P7.7); 失败时删除文档并重新抛出.
     * 在后台线程调用.
     */
    @JvmStatic
    @JvmOverloads
    fun export(context: Context, uri: Uri, selected: List<String>, now: Long = System.currentTimeMillis(), password: SettingsPassword? = null): Result {
        val categories = selected.mapNotNull(SettingCategory::ofIdOrNull)
        val snapshot = SettingsSnapshot.capture(categories)
        val resolver = context.contentResolver
        try {
            val key = if (categories.any { it.sensitive }) AndroidSettingsKeys.device(context) else null
            val manifest = openTruncated(context, uri).use { write(it, snapshot, now, host(context), device(), key = key, password = password) }
            return result(displayName(context, uri) ?: uri.lastPathSegment.orEmpty(), manifest, snapshot)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw e
        }
    }

    /** "wt" where the provider has it; a document created just now is empty anyway. zh-CN: 提供方支持时用 "wt"; 刚创建的文档本就为空. */
    private fun openTruncated(context: Context, uri: Uri): OutputStream {
        val resolver = context.contentResolver
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: FileNotFoundException) {
            resolver.openOutputStream(uri, "w")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        }
        return stream ?: throw IOException("No output stream for $uri")
    }

    @JvmStatic
    fun host(context: Context) = SettingsArchiveManifest.Host(context.packageName, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)

    @JvmStatic
    fun device() = SettingsArchiveManifest.Device(Build.MODEL.orEmpty(), Build.VERSION.SDK_INT)

    /** The display name of the document [uri], or null. zh-CN: 文档 [uri] 的显示名称, 没有时为 null. */
    @JvmStatic
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
