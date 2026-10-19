package org.autojs.autojs.engine

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Dialog windows belong to an Activity; the script owns their lifetime. */
internal class NodeDialogs(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val sessions = linkedMapOf<String, NodeDialogSession>()
    private var closed = false
    private val closedScopes = hashSetOf<String>()
    private val cancelledRequests = hashSetOf<String>()

    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) = handler.post {
        try {
            check(!closed) { "The script has ended." }
            val args = JSONArray(request.argsJson)
            when (request.method) {
                "cancelRequest" -> {
                    val token = args.getString(0)
                    val scope = args.optString(1)
                    require(token.isNotBlank()) { "A dialog cancellation token is required." }
                    rememberCancellation("token:$scope:$token")
                    sessions.values.toList().filter { it.token == token && it.scope == scope }.forEach { it.cancel() }
                    callback(NodeBridgeResponse.success(request.id, "true"))
                }
                "closeScope" -> {
                    val scope = args.getString(0)
                    closedScopes.add(scope)
                    sessions.values.toList().filter { it.scope == scope }.forEach { it.cancel() }
                    callback(NodeBridgeResponse.success(request.id, "true"))
                }
                "progressUpdate" -> {
                    val session = sessions[args.getString(0)] ?: error("The progress dialog is closed.")
                    require(session.scope == args.optString(2)) { "The dialog belongs to another client." }
                    session.update(args.getJSONObject(1))
                    callback(NodeBridgeResponse.success(request.id, session.snapshot().toString()))
                }
                "progressDismiss" -> {
                    val session = sessions[args.getString(0)]
                    require(session == null || session.scope == args.optString(1)) { "The dialog belongs to another client." }
                    session?.finish(null)
                    callback(NodeBridgeResponse.success(request.id, (session != null).toString()))
                }
                else -> {
                    val session = NodeDialogSession(request, args, callback) { id -> sessions.remove(id) }
                    check(session.scope !in closedScopes && !cancelledRequests.remove("native:${request.id}") &&
                        !cancelledRequests.remove("token:${session.scope}:${session.token}")) { "The dialog request was cancelled." }
                    sessions[session.id] = session
                    NodeDialogActivity.sessions[session.id] = session
                    session.expire = Runnable { session.timeout() }
                    handler.postDelayed(session.expire, request.timeoutMs)
                    session.onClose = { handler.removeCallbacks(session.expire) }
                    try {
                        context.startActivity(Intent(context, NodeDialogActivity::class.java)
                            .putExtra(NodeDialogActivity.EXTRA_ID, session.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
                    } catch (error: Throwable) {
                        session.fail(error.message ?: "Android could not show the dialog.")
                    }
                }
            }
        } catch (error: Exception) {
            callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.invalidRequest(
                error.message ?: "Invalid dialog request.", request.module, request.method)))
        }
    }

    private fun rememberCancellation(key: String) {
        cancelledRequests.add(key)
        handler.postDelayed({ cancelledRequests.remove(key) }, 120000)
    }

    /** Bun transport cancellation also handles raw calls that do not carry SDK tokens. */
    fun cancelRequest(requestId: String) = handler.post {
        if (!closed) {
            rememberCancellation("native:$requestId")
            sessions.values.toList().filter { it.requestId == requestId }.forEach { it.cancel() }
        }
    }

    fun closeAll() = handler.post {
        closed = true
        sessions.values.toList().forEach { it.cancel() }
        cancelledRequests.clear()
        closedScopes.clear()
    }
}

internal class NodeDialogSession(
    private val request: NodeBridgeRequest,
    private val args: JSONArray,
    private var callback: ((NodeBridgeResponse) -> Unit)?,
    private val remove: (String) -> Unit,
) {
    val id = "dialog-${UUID.randomUUID()}"
    val requestId = request.id
    private val isProgress = request.method == "progressShow"
    private val options = if (isProgress) args.getJSONObject(0) else args.optJSONObject(
        if (request.method == "singleChoice" || request.method == "multiChoice") 3 else 2) ?: JSONObject()
    val scope = options.optString("__scope")
    val token = options.optString("__requestId")
    private var activity: NodeDialogActivity? = null
    private var finished = false
    private var progressBar: ProgressBar? = null
    private var messageView: TextView? = null
    private var state = if (isProgress) JSONObject(options.toString()) else JSONObject()
    lateinit var expire: Runnable
    var onClose: () -> Unit = {}

    init {
        if (isProgress) validateProgress(state)
        else if (request.method in listOf("select", "singleChoice", "multiChoice")) {
            val count = args.getJSONArray(1).length()
            if (request.method == "singleChoice") require(args.optInt(2, -1) in -1 until count) { "The selected index is out of range." }
            if (request.method == "multiChoice") {
                val selected = args.optJSONArray(2) ?: JSONArray()
                for (i in 0 until selected.length()) require(selected.getInt(i) in 0 until count) { "A selected index is out of range." }
            }
        }
    }

    fun attach(owner: NodeDialogActivity) {
        if (finished) { owner.finish(); return }
        activity = owner
        val title = if (isProgress) state.optString("title") else args.optString(0)
        val builder = AlertDialog.Builder(owner).setTitle(title)
        val positive = options.optString("positive").ifBlank { owner.getString(android.R.string.ok) }
        val negative = options.optString("negative").ifBlank { owner.getString(android.R.string.cancel) }
        var cancelled: Any? = null
        when (request.method) {
            "alert" -> builder.setMessage(args.optString(1)).setPositiveButton(positive) { _, _ -> finish(null) }
            "confirm" -> {
                cancelled = false
                builder.setMessage(args.optString(1)).setPositiveButton(positive) { _, _ -> finish(true) }
                    .setNegativeButton(negative) { _, _ -> finish(false) }
            }
            "input", "rawInput" -> {
                val input = EditText(owner).apply { setText(args.optString(1)); tag = "node-dialog-input"; setSingleLine(false) }
                builder.setView(input).setPositiveButton(positive) { _, _ -> finish(input.text.toString()) }
                    .setNegativeButton(negative) { _, _ -> finish(null) }
            }
            "select", "singleChoice", "multiChoice" -> {
                val values = args.getJSONArray(1)
                val items = Array(values.length()) { values.optString(it) }
                when (request.method) {
                    "select" -> {
                        cancelled = -1
                        builder.setItems(items) { _, index -> finish(index) }
                    }
                    "singleChoice" -> {
                        cancelled = -1
                        var selected = args.optInt(2, -1)
                        builder.setSingleChoiceItems(items, selected) { _, index -> selected = index }
                            .setPositiveButton(positive) { _, _ -> finish(selected) }
                            .setNegativeButton(negative) { _, _ -> finish(-1) }
                    }
                    else -> {
                        cancelled = JSONArray()
                        val selected = BooleanArray(items.size)
                        args.optJSONArray(2)?.let { initial -> for (i in 0 until initial.length()) selected[initial.getInt(i)] = true }
                        builder.setMultiChoiceItems(items, selected) { _, index, checked -> selected[index] = checked }
                            .setPositiveButton(positive) { _, _ -> finish(JSONArray(selected.indices.filter { selected[it] })) }
                            .setNegativeButton(negative) { _, _ -> finish(JSONArray()) }
                    }
                }
            }
            "progressShow" -> {
                messageView = TextView(owner)
                progressBar = ProgressBar(owner, null, android.R.attr.progressBarStyleHorizontal)
                val padding = (24 * owner.resources.displayMetrics.density).toInt()
                val layout = LinearLayout(owner).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(padding, padding / 2, padding, padding)
                    addView(messageView); addView(progressBar)
                }
                builder.setView(layout).setCancelable(false)
            }
            else -> throw IllegalArgumentException("Unknown dialog method: ${request.method}")
        }
        if (!isProgress && options.optString("neutral").isNotBlank()) builder.setNeutralButton(options.getString("neutral")) { _, _ -> finish(cancelled) }
        val dialog = builder.create()
        owner.dialog = dialog
        dialog.setOnCancelListener { finish(cancelled) }
        dialog.setOnDismissListener { if (!finished) finish(cancelled) }
        dialog.show()
        if (isProgress) {
            renderProgress()
            onClose() // The show call has completed; the script now owns this window until dismiss/exit.
            respond(NodeBridgeResponse.success(request.id, snapshot().toString()))
        }
    }

    fun update(patch: JSONObject) {
        require(isProgress && !finished) { "The progress dialog is closed." }
        val next = JSONObject(state.toString())
        patch.keys().forEach { next.put(it, patch.get(it)) }
        validateProgress(next)
        state = next
        renderProgress()
    }

    private fun renderProgress() {
        activity?.dialog?.setTitle(state.optString("title"))
        messageView?.text = state.optString("message")
        progressBar?.apply {
            isIndeterminate = state.optBoolean("indeterminate", false)
            max = state.optInt("max", 100)
            progress = state.optInt("value", 0)
        }
    }

    fun snapshot(): JSONObject = JSONObject(state.toString()).apply { remove("__scope"); remove("__requestId") }.put("id", id).put("shown", !finished)
        .put("max", state.optInt("max", 100)).put("value", state.optInt("value", 0))
        .put("indeterminate", state.optBoolean("indeterminate", false))

    fun finish(value: Any?) {
        if (finished) return
        respond(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(value)))
        dispose()
    }

    fun cancel() = finish(when (request.method) {
        "confirm" -> false
        "select", "singleChoice" -> -1
        "multiChoice" -> JSONArray()
        else -> null
    })

    fun fail(message: String) {
        if (finished) return
        respond(NodeBridgeResponse.failure(request.id, NodeBridgeError.capabilityProviderMissing(request.module, request.method, message)))
        dispose()
    }

    fun timeout() {
        if (finished) return
        respond(NodeBridgeResponse.failure(request.id, NodeBridgeError.timeout(request.module, request.method)))
        dispose()
    }

    private fun respond(response: NodeBridgeResponse) { val pending = callback; callback = null; pending?.invoke(response) }
    private fun dispose() {
        finished = true
        onClose()
        NodeDialogActivity.sessions.remove(id)
        remove(id)
        val owner = activity
        activity = null
        owner?.dialog?.dismiss()
        owner?.finish()
    }

    private fun validateProgress(value: JSONObject) {
        val max = value.optInt("max", 100)
        require(max > 0 && value.optInt("value", 0) in 0..max) { "Progress requires max > 0 and a value between 0 and max." }
    }
}

class NodeDialogActivity : Activity() {
    internal var dialog: AlertDialog? = null
    private var session: NodeDialogSession? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val current = sessions[intent.getStringExtra(EXTRA_ID).orEmpty()]
        if (current == null) { finish(); return }
        session = current
        try { current.attach(this) } catch (error: Exception) { current.fail(error.message ?: "Android could not show the dialog.") }
    }
    override fun onDestroy() {
        session?.cancel()
        session = null
        dialog = null
        super.onDestroy()
    }
    companion object {
        const val EXTRA_ID = "org.autojs.autojs.engine.extra.NODE_DIALOG_ID"
        internal val sessions = ConcurrentHashMap<String, NodeDialogSession>()
    }
}
