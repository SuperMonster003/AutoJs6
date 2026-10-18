package org.autojs.autojs.engine

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** SAF locations and a full-size camera output, with one result per request. */
internal object RuntimeWebFileChooser {
    const val REQUEST = 4027
    const val CAMERA_PERMISSION = 4028
    fun mode(options: JSONObject): Int = when (val value = options.opt("mode")) {
        null, JSONObject.NULL -> if (options.optBoolean("multiple")) 1 else 0
        "open" -> 0; "multiple" -> 1; "directory" -> 2; "save" -> 3
        is Number -> RuntimeViewAttributes.integer(value, 0, 3)
        else -> error("File mode must be open, multiple, directory, save or 0..3")
    }
    fun create(context: Context, options: JSONObject, complete: (Result<List<Uri>?>) -> Unit): RuntimeWebActionActivity.Action {
        val mode = mode(options)
        val accepts = options.optJSONArray("acceptTypes") ?: JSONArray()
        val types = (0 until accepts.length()).flatMap { accepts.getString(it).split(',') }.mapNotNull { raw ->
            val value = raw.trim()
            when { value.isBlank() -> null; value.startsWith('.') -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(value.drop(1).lowercase()); else -> value }
        }.distinct()
        val capture = options.optBoolean("captureEnabled") && mode in 0..1 && types.isNotEmpty() && types.all { it.startsWith("image/") }
        val writable = mode == 3 || when { options.has("writable") -> options.getBoolean("writable"); options.has("permissionMode") -> options.getInt("permissionMode") == 1; else -> mode == 2 }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        val fileName = options.optString("fileName").ifBlank { options.optString("filenameHint", "download") }
        if (mode == 3) RuntimeWebDownloads.checkFileName(fileName)
        var captureFile: File? = null
        var captureUri: Uri? = null
        val picker = if (capture) {
            val directory = File(context.cacheDir, "runtime-web-capture").apply { check(isDirectory || mkdirs()) }
            captureFile = File(directory, "${UUID.randomUUID()}.jpg")
            val authority = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
                .single { it.name == androidx.core.content.FileProvider::class.java.name }.authority
            captureUri = androidx.core.content.FileProvider.getUriForFile(context, authority, requireNotNull(captureFile))
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, captureUri).apply {
                clipData = ClipData.newRawUri("capture", captureUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        } else Intent(when (mode) { 2 -> Intent.ACTION_OPEN_DOCUMENT_TREE; 3 -> Intent.ACTION_CREATE_DOCUMENT; else -> Intent.ACTION_OPEN_DOCUMENT }).apply {
            addFlags(flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if (mode == 2) addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            if (mode != 2) {
                addCategory(Intent.CATEGORY_OPENABLE); type = types.singleOrNull() ?: "*/*"
                if (types.size > 1 && mode != 3) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                if (mode == 1) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                if (mode == 3) { type = types.firstOrNull()?.takeUnless { it.endsWith("/*") } ?: "application/octet-stream"; putExtra(Intent.EXTRA_TITLE, fileName) }
            }
            if (Build.VERSION.SDK_INT >= 26 && options.has("initialUri")) putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(options.getString("initialUri")))
        }
        return object : RuntimeWebActionActivity.Action(context) {
            fun launchPicker(activity: Activity) {
                if (isDone) return
                try { activity.startActivityForResult(picker, REQUEST) }
                catch (failure: Throwable) { finish { cleanup(); complete(Result.failure(failure)) } }
            }
            override fun attach(activity: Activity, state: Bundle?) {
                if (state != null) return
                val declared = capture && context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                    .requestedPermissions.orEmpty().contains(Manifest.permission.CAMERA)
                if (declared && context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                    activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION)
                else launchPicker(activity)
            }
            override fun permissions(code: Int, results: IntArray) {
                if (code == CAMERA_PERMISSION) {
                    val owner = activity?.get()
                    if (results.singleOrNull() == PackageManager.PERMISSION_GRANTED && owner != null) launchPicker(owner) else close()
                }
            }
            override fun result(code: Int, data: Intent?) {
                if (isDone) return
                val value = runCatching {
                    if (code != Activity.RESULT_OK) null else if (capture) {
                        val file = requireNotNull(captureFile); val uri = requireNotNull(captureUri)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, bounds)
                        require(file.length() > 0 && bounds.outWidth > 0 && bounds.outHeight > 0) { "The camera returned no full-size image" }
                        RuntimeWebFileAccess.capture(uri, file); listOf(uri)
                    } else {
                        requireNotNull(data) { "The document picker returned no result" }
                        val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } } ?: listOfNotNull(data.data)
                        require(mode == 1 || uris.size <= 1) { "A single selection returned multiple URIs" }
                        require(uris.isNotEmpty()) { "The document picker returned no URI" }
                        if (mode == 2) require(uris.size == 1 && DocumentsContract.isTreeUri(uris.single())) { "The picker returned no document tree" }
                        uris.distinct().onEach { RuntimeWebFileAccess.validate(context, it, writable) }
                    }
                }
                finish {
                    try { complete(value) } finally { cleanup() }
                }
            }
            fun cleanup() {
                captureUri?.let { context.revokeUriPermission(it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                captureUri?.let(RuntimeWebFileAccess::discardUnusedCapture)
                if (captureUri == null || !RuntimeWebFileAccess.isCapture(requireNotNull(captureUri))) captureFile?.delete()
            }
            override fun close() = finish { cleanup(); complete(Result.success(null)) }
        }.launch()
    }
}
