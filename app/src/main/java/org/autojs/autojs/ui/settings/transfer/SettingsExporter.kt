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
import org.autojs.autojs.core.pref.data.SettingsDataCapture
import org.autojs.autojs.core.pref.data.SettingsDataEntries
import org.autojs.autojs.theme.library.ColorLibraryJson
import org.autojs.autojs6.BuildConfig
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * Writes the archive of the export wizard (docs/settings/ROADMAP.md P6.4, T7 / T11): the snapshot of the chosen
 * categories into the document the user created, at most [SettingsArchive.MAX_BYTES], with the data files of the chosen
 * data categories, only the entries their detail pages chose (P10.2). A failed write deletes the document, so no
 * partial archive stays behind (the writer leaves that to the caller). Credentials are sealed for the device key
 * (D23 / T21, P6.5), and for the password the user set as well (P7.7). Values are never logged (R6).
 *
 * zh-CN: 写入导出向导的归档 (ROADMAP P6.4, T7 / T11): 把所选分类的快照写入用户创建的文档, 不超过 [SettingsArchive.MAX_BYTES], 并写入
 * 所选数据分类的数据文件, 只含其细化页选中的条目 (P10.2). 写入失败时删除该文档, 不留下不完整的归档 (写入器把这一步交给调用方). 凭据以
 * 设备密钥加密 (D23 / T21, P6.5), 用户设了口令时另以口令加密 (P7.7). 不记录任何值 (R6).
 */
object SettingsExporter {

    /** What was written: the name of the document and the counts of the done page. zh-CN: 写入结果: 文档名与完成页的计数. */
    data class Result(val fileName: String, val categories: Int, val entries: Int, val unexportable: Int)

    /**
     * Writes [snapshot] and [data] to [out] as exported at [now], sensitive categories sealed for [key] (and
     * [password]); the manifest written is returned. Pure (R3).
     * zh-CN: 把 [snapshot] 与 [data] 以导出时间 [now] 写入 [out], 敏感分类以 [key] (及 [password]) 加密; 返回写入的 manifest. 纯 JVM (R3).
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
        data: List<SettingsArchiveWriter.DataFile> = emptyList(),
    ): SettingsArchiveManifest =
        SettingsArchiveWriter.write(out, snapshot, SettingsArchive.exportTime(now), host, device, data, maxBytes = maxBytes, key = key, password = password)

    /**
     * [Result] of a written [manifest] and the [snapshot] it came from; the data categories count as categories, their
     * entries as entries.
     * zh-CN: 由写入的 manifest 与快照得出结果; 数据分类计入分类数, 其条目计入条目数.
     */
    @JvmStatic
    fun result(fileName: String, manifest: SettingsArchiveManifest, snapshot: SettingsSnapshot) = Result(
        fileName,
        manifest.categories.size + manifest.data.size,
        manifest.categories.sumOf { it.entries } + manifest.data.sumOf { it.entries },
        snapshot.unexportable.size,
    )

    /**
     * The data files of the data categories among [categories] as their pages export them now
     * ([SettingsDataCapture]), each with only the entries [refine] chose for it (all of them without a choice, P10.2);
     * a category left without entries is not written. Throws [IOException] when a chosen category cannot be read.
     * Runs on a background thread.
     * zh-CN: [categories] 中数据分类当前按各自页面导出所得的数据文件 ([SettingsDataCapture]), 每个只含 [refine] 为其选中的条目 (没有选择
     * 时为全部, P10.2); 没有剩下条目的分类不写入. 所选分类无法读取时抛 [IOException]. 在后台线程调用.
     */
    @JvmStatic
    @JvmOverloads
    fun dataFiles(
        context: Context,
        categories: List<SettingCategory>,
        refine: Map<String, List<String>> = emptyMap(),
        now: Long = System.currentTimeMillis(),
    ): List<SettingsArchiveWriter.DataFile> {
        val wanted = categories.filter { it.isData }
        if (wanted.isEmpty()) return emptyList()
        val app = ColorLibraryJson.AppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        return dataFiles(wanted, SettingsDataCapture.readAll(context, wanted, now), refine, now, app)
    }

    /**
     * The data files of [wanted] out of what [read] holds for them, null for a category that could not be read. Pure (R3).
     * zh-CN: 从 [read] 中得出 [wanted] 的数据文件, 值为 null 表示该分类无法读取. 纯 JVM (R3).
     */
    @JvmStatic
    fun dataFiles(
        wanted: List<SettingCategory>,
        read: Map<SettingCategory, SettingsArchiveWriter.DataFile?>,
        refine: Map<String, List<String>>,
        now: Long,
        app: ColorLibraryJson.AppInfo,
    ): List<SettingsArchiveWriter.DataFile> = wanted.mapNotNull { category ->
        val file = read[category] ?: throw IOException("Cannot read the data of ${category.id}")
        SettingsDataEntries.select(file, refine[category.id], now, app = app)
    }

    /**
     * Captures the settings and data of [selected] (category ids, the data entries chosen by [refine]) and writes them
     * to the document [uri] (truncated first), the credentials with a password slot of [password] as well (P7.7);
     * deletes the document and rethrows when that fails. Runs on a background thread.
     * zh-CN: 读取 [selected] (分类 id, 数据条目按 [refine] 选择) 的设置与数据并写入文档 [uri] (先截断), 凭据另加 [password] 的口令槽
     * (P7.7); 失败时删除文档并重新抛出. 在后台线程调用.
     */
    @JvmStatic
    @JvmOverloads
    fun export(
        context: Context,
        uri: Uri,
        selected: List<String>,
        now: Long = System.currentTimeMillis(),
        password: SettingsPassword? = null,
        refine: Map<String, List<String>> = emptyMap(),
    ): Result {
        val categories = selected.mapNotNull(SettingCategory::ofIdOrNull)
        val snapshot = SettingsSnapshot.capture(categories)
        val resolver = context.contentResolver
        try {
            val key = if (categories.any { it.sensitive }) AndroidSettingsKeys.device(context) else null
            val data = dataFiles(context, categories, refine, now)
            val manifest = openTruncated(context, uri).use { write(it, snapshot, now, host(context), device(), key = key, password = password, data = data) }
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
