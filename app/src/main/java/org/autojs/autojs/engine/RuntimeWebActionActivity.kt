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
import android.widget.EditText
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
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        action?.permissions(requestCode, grantResults)
    }
    override fun onStop() {
        if (!isChangingConfigurations) action?.background()
        super.onStop()
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
        @Volatile private var done = false
        protected val isDone get() = done
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
        open fun background() = Unit
        open fun permissions(code: Int, results: IntArray) = Unit
        protected fun finish(completion: () -> Unit) {
            if (done) return
            done = true; actions.remove(id)
            try { completion() } finally { activity?.get()?.let { it.finishActivity(REQUEST); it.finish() } }
        }
    }
    companion object {
        private const val REQUEST = 4027
        private val actions = ConcurrentHashMap<String, Action>()

        internal fun chooseFiles(context: Context, options: JSONObject, complete: (Result<List<Uri>?>) -> Unit): Action =
            RuntimeWebFileChooser.create(context, options, complete)

        internal fun mediaPermissions(context: Context, origin: String, permissions: Array<String>, complete: (Boolean) -> Unit): Action = object : Action(context) {
            private var requested = false
            override fun attach(activity: Activity, state: Bundle?) {
                activity.title = origin
                if (!requested) { requested = true; activity.requestPermissions(permissions, REQUEST) }
            }
            override fun permissions(code: Int, results: IntArray) {
                if (code == REQUEST) finish { complete(permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) }
            }
            override fun close() = finish { complete(false) }
        }.launch()

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
    private data class Grant(var users: Int = 0, var acquired: Int = 0)
    private val grants = HashMap<String, Grant>()
    private val captures = HashMap<String, java.io.File>()
    @Synchronized fun capture(uri: Uri, file: java.io.File) { captures[uri.toString()] = file }
    @Synchronized fun isCapture(uri: Uri) = captures.containsKey(uri.toString())
    @Synchronized fun discardUnusedCapture(uri: Uri) { if (!grants.containsKey(uri.toString())) captures.remove(uri.toString())?.delete() }
    fun validate(context: Context, uri: Uri, writable: Boolean = false) {
        require(uri.scheme == "content") { "File selection requires a content URI" }
        val provider = context.packageManager.resolveContentProvider(uri.authority.orEmpty(), 0)
        require(provider != null && (provider.applicationInfo.uid != Process.myUid() || isCapture(uri))) { "A picker cannot expose this app's private provider" }
        if (provider.applicationInfo.uid == Process.myUid() && isCapture(uri)) return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        require(context.checkUriPermission(uri, Process.myPid(), Process.myUid(), flags) == PackageManager.PERMISSION_GRANTED) { "Document permission was not granted" }
    }
    @Synchronized fun acquire(context: Context, uri: Uri) {
        validate(context, uri)
        val grant = grants.getOrPut(uri.toString()) { Grant() }
        for (flag in listOf(Intent.FLAG_GRANT_READ_URI_PERMISSION, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
            val existing = context.contentResolver.persistedUriPermissions.any { it.uri == uri && if (flag == Intent.FLAG_GRANT_READ_URI_PERMISSION) it.isReadPermission else it.isWritePermission }
            if (!existing && context.checkUriPermission(uri, Process.myPid(), Process.myUid(), flag) == PackageManager.PERMISSION_GRANTED) {
                try { context.contentResolver.takePersistableUriPermission(uri, flag); grant.acquired = grant.acquired or flag }
                catch (_: SecurityException) { /* Temporary grants and capture files keep their Android lifetime. */ }
            }
        }
        grant.users++
    }
    @Synchronized fun release(context: Context, uri: Uri) {
        val grant = grants[uri.toString()] ?: return
        if (--grant.users != 0) return
        grants.remove(uri.toString())
        try { if (grant.acquired != 0) context.contentResolver.releasePersistableUriPermission(uri, grant.acquired) }
        finally { captures.remove(uri.toString())?.delete() }
    }
}
