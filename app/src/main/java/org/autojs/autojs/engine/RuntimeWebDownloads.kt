package org.autojs.autojs.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import org.json.JSONObject
import java.io.File

/** Uses Android's persistent, UID-scoped download service. Closing a WebView does not delete downloads. */
internal class RuntimeWebDownloads(private val view: WebView) {
    private val context get() = view.context
    private val manager get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    fun start(options: JSONObject): JSONObject {
        val url = Uri.parse(options.getString("url"))
        require(url.scheme in setOf("http", "https")) { "DownloadManager requires an HTTP(S) URL" }
        val filename = options.optString("fileName").ifBlank { URLUtil.guessFileName(url.toString(), options.optString("contentDisposition"), options.optString("mimeType")) }
        require(filename.isNotBlank() && filename == File(filename).name && filename !in setOf(".", "..") && !filename.contains('\\')) { "fileName must be a filename, not a path" }
        val request = DownloadManager.Request(url).setTitle(options.optString("title", filename))
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        options.optString("mimeType").takeIf { it.isNotBlank() }?.let(request::setMimeType)
        request.addRequestHeader("User-Agent", options.optString("userAgent", view.settings.userAgentString))
        if (options.optBoolean("includeCookies")) CookieManager.getInstance().getCookie(url.toString())?.let { request.addRequestHeader("Cookie", it) }
        options.optJSONObject("headers")?.let { headers -> headers.keys().forEach { request.addRequestHeader(it, headers.getString(it)) } }
        val id = manager.enqueue(request)
        return JSONObject().put("id", id).put("url", url.toString()).put("fileName", filename)
    }
    fun get(id: Long): Any = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
        if (!cursor.moveToFirst()) return@use JSONObject.NULL
        fun number(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
        fun text(column: String) = cursor.getString(cursor.getColumnIndexOrThrow(column))
        JSONObject().put("id", id).put("status", when (number(DownloadManager.COLUMN_STATUS).toInt()) {
            DownloadManager.STATUS_SUCCESSFUL -> "successful"; DownloadManager.STATUS_FAILED -> "failed";
            DownloadManager.STATUS_PAUSED -> "paused"; DownloadManager.STATUS_RUNNING -> "running"; else -> "pending"
        }).put("reason", number(DownloadManager.COLUMN_REASON)).put("bytesDownloaded", number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            .put("totalBytes", number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).put("url", text(DownloadManager.COLUMN_URI))
            .put("localUri", text(DownloadManager.COLUMN_LOCAL_URI) ?: JSONObject.NULL).put("mimeType", text(DownloadManager.COLUMN_MEDIA_TYPE) ?: JSONObject.NULL)
            .put("contentUri", manager.getUriForDownloadedFile(id)?.toString() ?: JSONObject.NULL)
    }
    fun remove(id: Long) = manager.remove(id) > 0
    fun read(id: Long, offset: Long, length: Int): JSONObject {
        require(offset >= 0 && length in 1..32768) { "Download reads require a nonnegative offset and 1..32768 bytes" }
        val data = ParcelFileDescriptor.AutoCloseInputStream(manager.openDownloadedFile(id)).use { input ->
            var remaining = offset
            while (remaining > 0) { val skipped = input.skip(remaining); if (skipped <= 0) break; remaining -= skipped }
            if (remaining > 0) ByteArray(0) else { val bytes = ByteArray(length); val size = input.read(bytes); if (size < 0) ByteArray(0) else bytes.copyOf(size) }
        }
        return JSONObject().put("base64", Base64.encodeToString(data, Base64.NO_WRAP)).put("bytesRead", data.size)
    }
}
