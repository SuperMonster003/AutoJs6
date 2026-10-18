package org.autojs.autojs.engine

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Private UI delegate. Requests survive Activity recreation and finish exactly once. */
class RuntimeWebActionActivity : Activity() {
    private var action: Action? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        action = actions[intent.getStringExtra("request")]
        val task = action ?: return finish()
        task.activity = WeakReference(this)
        task.attach(this, state)
    }
    @Deprecated("Android Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST) action?.result(resultCode, data)
    }
    override fun onDestroy() {
        val task = action
        if (task?.activity?.get() === this) {
            task.detach()
            task.activity = null
            if (!isChangingConfigurations) task.close()
        }
        action = null
        super.onDestroy()
    }
    internal abstract class Action(private val context: Context) : Closeable {
        val id = UUID.randomUUID().toString()
        var activity: WeakReference<RuntimeWebActionActivity>? = null
        private var done = false
        fun launch(): Action {
            actions[id] = this
            try { context.startActivity(Intent(context, RuntimeWebActionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK).putExtra("request", id)) }
            catch (error: Throwable) { actions.remove(id); close(); throw error }
            return this
        }
        abstract fun attach(activity: Activity, state: Bundle?)
        open fun result(code: Int, data: Intent?) = Unit
        open fun detach() = Unit
        protected fun finish(completion: () -> Unit) {
            if (done) return
            done = true; actions.remove(id)
            try { completion() } finally { activity?.get()?.let { it.finishActivity(REQUEST); it.finish() } }
        }
    }
    companion object {
        private const val REQUEST = 4027
        private val actions = ConcurrentHashMap<String, Action>()

        internal fun chooseFiles(context: Context, options: JSONObject, complete: (Result<List<Uri>?>) -> Unit): Action {
            val accepts = options.optJSONArray("acceptTypes") ?: JSONArray()
            val mimeTypes = (0 until accepts.length()).flatMap { accepts.getString(it).split(',') }.mapNotNull { raw ->
                val value = raw.trim()
                when { value.isBlank() -> null; value.startsWith('.') -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(value.substring(1).lowercase()); else -> value }
            }.distinct()
            val multiple = options.optBoolean("multiple", false)
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mimeTypes.singleOrNull() ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            if (mimeTypes.size > 1) picker.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            if (android.os.Build.VERSION.SDK_INT >= 26 && options.has("initialUri")) picker.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(options.getString("initialUri")))
            return object : Action(context) {
                override fun attach(activity: Activity, state: Bundle?) {
                    if (state == null) try { activity.startActivityForResult(picker, REQUEST) }
                    catch (failure: Throwable) { finish { complete(Result.failure(failure)) } }
                }
                override fun result(code: Int, data: Intent?) {
                    val value = runCatching {
                        if (code != RESULT_OK || data == null) null else {
                            val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } }
                                ?: listOfNotNull(data.data)
                            require(multiple || uris.size <= 1) { "A single-file request returned multiple files" }
                            require(uris.isNotEmpty()) { "The document picker returned no URI" }
                            uris.distinct().onEach { RuntimeWebFileAccess.validate(context, it) }
                        }
                    }
                    // Acquire document grants before the delegated Activity finishes.
                    finish { complete(value) }
                }
                override fun close() = finish { complete(Result.success(null)) }
            }.launch()
        }

        internal fun dialog(context: Context, kind: String, data: JSONObject, complete: (Any?) -> Unit): Action = object : Action(context) {
            private var dialog: AlertDialog? = null
            override fun attach(activity: Activity, state: Bundle?) {
                val input = if (kind == "onJsPrompt") EditText(activity).apply { setText(data.optString("defaultValue")) } else null
                dialog = AlertDialog.Builder(activity).setTitle(data.optString("url")).setMessage(data.optString("message"))
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish { complete(input?.text?.toString() ?: true) } }
                    .apply { if (input != null) setView(input); if (kind != "onJsAlert") setNegativeButton(android.R.string.cancel) { _, _ -> close() } }
                    .setOnCancelListener { close() }.create().also { it.show() }
            }
            override fun detach() { dialog?.setOnCancelListener(null); dialog?.dismiss(); dialog = null }
            override fun close() = finish { complete(null) }
        }.launch()

        internal fun fullscreen(context: Context, view: View, complete: () -> Unit): Action = object : Action(context) {
            override fun attach(activity: Activity, state: Bundle?) {
                (view.parent as? ViewGroup)?.removeView(view)
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                activity.setContentView(view, ViewGroup.LayoutParams(-1, -1))
            }
            override fun detach() { (view.parent as? ViewGroup)?.removeView(view) }
            override fun close() = finish { detach(); complete() }
        }.launch()
    }
}

/** Reference counts only grants acquired by this feature; existing user grants remain intact. */
internal object RuntimeWebFileAccess {
    private data class Grant(var users: Int, val acquired: Boolean)
    private val grants = HashMap<String, Grant>()
    fun validate(context: Context, uri: Uri) {
        require(uri.scheme == "content") { "File selection requires a readable content URI" }
        val provider = context.packageManager.resolveContentProvider(uri.authority.orEmpty(), 0)
        require(provider != null && provider.applicationInfo.uid != Process.myUid()) { "A picker cannot expose this app's private provider" }
        require(context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED) { "Document read permission was not granted" }
    }
    fun acquire(context: Context, uri: Uri) {
        validate(context, uri)
        grants[uri.toString()]?.let { it.users++; return }
        val existing = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        val acquired = if (existing) false else try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); true
        } catch (_: SecurityException) {
            // Custom handlers may return an ordinary temporary read grant. Android owns its lifetime.
            validate(context, uri); false
        }
        grants[uri.toString()] = Grant(1, acquired)
    }
    fun release(context: Context, uri: Uri) {
        val grant = grants[uri.toString()] ?: return
        if (--grant.users != 0) return
        grants.remove(uri.toString())
        if (grant.acquired) context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
