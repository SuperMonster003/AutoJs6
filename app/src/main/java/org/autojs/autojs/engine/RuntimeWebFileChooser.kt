package org.autojs.autojs.engine

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** SAF locations and temporary media captures, with one result per request. */
internal object RuntimeWebFileChooser {
    const val REQUEST = 4027
    const val CAMERA_PERMISSION = 4028
    private val io = Executors.newCachedThreadPool { task -> Thread(task, "runtime-web-capture").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    fun mode(options: JSONObject): Int = when (val value = options.opt("mode")) {
        null, JSONObject.NULL -> if (options.optBoolean("multiple")) 1 else 0
        "open" -> 0; "multiple" -> 1; "directory" -> 2; "save" -> 3
        is Number -> RuntimeViewAttributes.integer(value, 0, 3)
        else -> error("File mode must be open, multiple, directory, save or 0..3")
    }
    internal fun acceptTypes(options: JSONObject): List<String> {
        val accepts = options.optJSONArray("acceptTypes") ?: JSONArray()
        return (0 until accepts.length()).flatMap { accepts.getString(it).split(',') }.mapNotNull { raw ->
            val value = raw.trim().lowercase()
            when { value.isBlank() -> null; value.startsWith('.') -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(value.drop(1).lowercase()); else -> value }
        }.distinct()
    }
    internal fun captureKind(options: JSONObject, types: List<String> = acceptTypes(options)): String? {
        if (!options.optBoolean("captureEnabled") || mode(options) !in 0..1) return null
        if (!options.isNull("captureKind")) return options.getString("captureKind").also { require(it in listOf("image", "video", "audio")) { "captureKind must be image, video or audio" } }
        return listOf("image", "video", "audio").firstOrNull { kind -> types.isNotEmpty() && types.all { it.startsWith("$kind/") } }
    }
    internal fun durationLimit(options: JSONObject): Int? = if (options.isNull("durationLimitSeconds")) null
        else RuntimeViewAttributes.integer(options.get("durationLimitSeconds"), 1, Int.MAX_VALUE / 1000)
    internal fun sizeLimit(options: JSONObject): Long? {
        if (options.isNull("sizeLimitBytes")) return null
        val number = (options.get("sizeLimitBytes") as? Number)?.toDouble()
        require(number != null && number.isFinite() && number % 1.0 == 0.0 && number in 1.0..9007199254740991.0) { "sizeLimitBytes must be a positive safe integer" }
        return number.toLong()
    }
    internal fun saveFileName(options: JSONObject): String = listOf("fileName", "filenameHint").firstNotNullOfOrNull { key ->
        if (options.isNull(key)) null else options.getString(key).takeUnless(String::isBlank)
    } ?: "download"
    internal fun newCapture(context: Context, extension: String): Pair<File, Uri> {
        val directory = File(context.cacheDir, "runtime-web-capture").apply { check(isDirectory || mkdirs()) }
        val file = File(directory, "${UUID.randomUUID()}.$extension")
        val authority = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
            .single { it.name == androidx.core.content.FileProvider::class.java.name }.authority
        return file to androidx.core.content.FileProvider.getUriForFile(context, authority, file)
    }
    internal fun cameraIntent(kind: String, options: JSONObject, uri: Uri): Intent =
        Intent(if (kind == "video") MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri).apply {
            clipData = ClipData.newRawUri("capture", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (kind == "video") {
                if (!options.isNull("videoQuality")) putExtra(MediaStore.EXTRA_VIDEO_QUALITY, RuntimeViewAttributes.integer(options.get("videoQuality"), 0, 1))
                durationLimit(options)?.let { putExtra(MediaStore.EXTRA_DURATION_LIMIT, it) }
                sizeLimit(options)?.let { putExtra(MediaStore.EXTRA_SIZE_LIMIT, it) }
            }
        }
    internal fun validateCapture(file: File, kind: String) {
        require(file.length() > 0) { "The capture returned no media" }
        if (kind == "image") {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The camera returned no full-size image" }
        } else {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.path)
                val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("$kind/") == true }
                require(track != null) { "The capture returned no $kind track" }
                extractor.selectTrack(track)
                require(extractor.sampleTime >= 0) { "The capture returned no media samples" }
            } finally { extractor.release() }
        }
    }
    fun create(context: Context, options: JSONObject, complete: (Result<List<Uri>?>) -> Unit): RuntimeWebActionActivity.Action {
        val mode = mode(options)
        val types = acceptTypes(options)
        val kind = captureKind(options, types)
        if (kind == "audio") return RuntimeWebAudioCapture(context, options, complete).launch()
        val capture = kind != null
        val writable = mode == 3 || when { options.has("writable") -> options.getBoolean("writable"); options.has("permissionMode") -> options.getInt("permissionMode") == 1; else -> mode == 2 }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        val fileName = saveFileName(options)
        if (mode == 3) RuntimeWebDownloads.checkFileName(fileName)
        var captureFile: File? = null
        var captureUri: Uri? = null
        val picker = if (capture) {
            val output = newCapture(context, if (kind == "video") "mp4" else "jpg")
            captureFile = output.first; captureUri = output.second
            cameraIntent(requireNotNull(kind), options, output.second)
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
                if (code == Activity.RESULT_OK && capture) {
                    io.execute {
                        val value = runCatching {
                            check(!isDone) { "Capture cancelled" }
                            val file = requireNotNull(captureFile)
                            if (file.length() == 0L && data?.data != null && data.data != captureUri) {
                                val source = requireNotNull(data.data)
                                RuntimeWebFileAccess.validate(context, source)
                                requireNotNull(context.contentResolver.openInputStream(source)).use { input ->
                                    check(!isDone) { "Capture cancelled" }
                                    file.outputStream().use { output ->
                                        val buffer = ByteArray(32768)
                                        while (true) { check(!isDone) { "Capture cancelled" }; val size = input.read(buffer); if (size < 0) break; output.write(buffer, 0, size) }
                                    }
                                }
                            }
                            validateCapture(file, requireNotNull(kind)); listOf(requireNotNull(captureUri))
                        }
                        main.post {
                            if (isDone) { cleanup(); return@post }
                            value.onSuccess { RuntimeWebFileAccess.capture(it.single(), requireNotNull(captureFile)) }
                            finish { try { complete(value) } finally { cleanup() } }
                        }
                    }
                    return
                }
                val value = runCatching {
                    if (code != Activity.RESULT_OK) null else {
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
