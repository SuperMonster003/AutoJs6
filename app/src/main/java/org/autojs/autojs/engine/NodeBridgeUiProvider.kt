package org.autojs.autojs.engine

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class NodeBridgeUiActivity : Activity() {

    private var session: NodeBridgeUiSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val currentSession = NodeBridgeUiSessionStore.get(sessionId)
        if (currentSession == null || currentSession.isClosed()) {
            finish()
            return
        }
        session = currentSession
        currentSession.attach(this)
        render(currentSession.rootDescriptor())
        currentSession.applyPendingPatches(this)
    }

    override fun onDestroy() {
        session?.detach(this)
        session = null
        super.onDestroy()
    }

    fun render(descriptor: JSONObject) {
        val currentSession = session ?: return
        val view = NodeBridgeUiRenderer(this, currentSession).createView(descriptor, isRoot = true)
        setContentView(view)
        currentSession.markShown()
    }

    fun applyPatch(patch: JSONObject): Boolean {
        val currentSession = session ?: return false
        if (patch.has("type") && patch.optString("id").isBlank()) {
            currentSession.replaceRootDescriptor(patch)
            render(patch)
            return true
        }
        val targetId = patch.optString("id").takeIf { it.isNotBlank() } ?: return false
        val target = window?.decorView?.findViewWithTag<View>(targetId) ?: return false
        NodeBridgeUiRenderer(this, currentSession).applyPatch(target, patch)
        return true
    }

    companion object {
        const val EXTRA_SESSION_ID = "org.autojs.autojs.engine.extra.NODE_BRIDGE_UI_SESSION_ID"
    }
}

class NodeBridgeUiDispatchService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val requestJson = intent?.getStringExtra(EXTRA_REQUEST_JSON).orEmpty()
        mainHandler.post {
            val response = runCatching {
                handleRequest(NodeBridgeRequest.fromJson(requestJson))
            }.getOrElse { error ->
                NodeBridgeResponse.failure(
                    NodeBridgeRequest.recoverId(requestJson).orEmpty(),
                    NodeBridgeError.providerFailed(
                        message = error.message ?: error.javaClass.name,
                        module = NodeBridgeRequest.recoverModule(requestJson),
                        method = NodeBridgeRequest.recoverMethod(requestJson),
                    ),
                )
            }
            receiver?.send(RESULT_RESPONSE, response.toBundle())
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleRequest(request: NodeBridgeRequest): NodeBridgeResponse {
        if (request.module != NodeBridgeModules.UI) {
            return NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
        }
        return when (request.method) {
            "showLayout" -> showLayout(request)
            "update" -> update(request)
            "drainEvents" -> drainEvents(request)
            "close" -> close(request)
            else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
        }
    }

    private fun showLayout(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val descriptor = args.optJSONObject(0)
            ?: return invalid(request, "ui.showLayout requires a JSON object layout descriptor.")
        val validation = NodeBridgeUiRenderer.validateDescriptor(descriptor)
        if (validation != null) {
            return invalid(request, validation)
        }
        val session = NodeBridgeUiSession(
            id = "ui-${UUID.randomUUID()}",
            descriptor = descriptor,
        )
        NodeBridgeUiSessionStore.put(session)
        val intent = Intent(applicationContext, NodeBridgeUiActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(NodeBridgeUiActivity.EXTRA_SESSION_ID, session.id)
        }
        val started = runCatching { applicationContext.startActivity(intent) }
            .onFailure { NodeBridgeUiSessionStore.remove(session.id)?.close("start_failed") }
            .isSuccess
        if (!started) {
            return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.providerFailed(
                    message = "AutoJs6 UI provider could not start its Activity.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        return NodeBridgeResponse.success(
            request.id,
            JSONObject()
                .put("id", session.id)
                .put("state", session.snapshot())
                .toString(),
        )
    }

    private fun update(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val session = activeSession(request, args) ?: return invalidHandle(request)
        val patch = args.optJSONObject(1)
            ?: return invalid(request, "ui.update requires a JSON object patch.")
        val validation = NodeBridgeUiRenderer.validateDescriptor(patch, requireType = false)
        if (validation != null) {
            return invalid(request, validation)
        }
        session.update(patch)
        return NodeBridgeResponse.success(
            request.id,
            session.snapshot().toString(),
        )
    }

    private fun drainEvents(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val session = activeSession(request, args) ?: return invalidHandle(request)
        val options = args.optJSONObject(1) ?: JSONObject()
        val maxEvents = options.optInt("maxEvents", DEFAULT_DRAIN_EVENTS).coerceIn(1, MAX_DRAIN_EVENTS)
        return NodeBridgeResponse.success(request.id, session.drainEvents(maxEvents).toString())
    }

    private fun close(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val session = activeSession(request, args) ?: return invalidHandle(request)
        session.close(args.optJSONObject(1)?.optString("reason").orEmpty().ifBlank { "close" })
        NodeBridgeUiSessionStore.remove(session.id)
        return NodeBridgeResponse.success(request.id, "null")
    }

    private fun activeSession(request: NodeBridgeRequest, args: JSONArray): NodeBridgeUiSession? {
        val id = args.optString(0).takeIf { it.isNotBlank() } ?: return null
        return NodeBridgeUiSessionStore.get(id)?.takeUnless { it.isClosed() || request.module != NodeBridgeModules.UI }
    }

    private fun invalidHandle(request: NodeBridgeRequest): NodeBridgeResponse {
        return invalid(request, "ui.${request.method} requires an active provider-owned UI handle.")
    }

    private fun invalid(request: NodeBridgeRequest, message: String): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.invalidRequest(
                message = message,
                module = request.module,
                method = request.method,
            ),
        )
    }

    private fun NodeBridgeRequest.argsArray(): JSONArray = runCatching { JSONArray(argsJson) }.getOrDefault(JSONArray())

    private fun NodeBridgeResponse.toBundle(): Bundle = Bundle().apply {
        putString(EXTRA_RESPONSE_JSON, toJson())
    }

    companion object {
        private const val ACTION_DISPATCH = "org.autojs.autojs.engine.action.DISPATCH_NODE_BRIDGE_UI"
        private const val EXTRA_REQUEST_JSON = "request_json"
        private const val EXTRA_RESPONSE_JSON = "response_json"
        private const val EXTRA_RESULT_RECEIVER = "result_receiver"
        private const val RESULT_RESPONSE = 1
        private const val DEFAULT_DRAIN_EVENTS = 16
        private const val MAX_DRAIN_EVENTS = 64

        fun dispatch(context: Context, request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
            val appContext = context.applicationContext
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode != RESULT_RESPONSE) {
                        callback(request.processDead("Main-process UI bridge returned an unexpected result."))
                        return
                    }
                    val responseJson = resultData?.getString(EXTRA_RESPONSE_JSON).orEmpty()
                    callback(
                        runCatching { NodeBridgeResponse.fromJson(responseJson) }
                            .getOrElse { request.processDead("Main-process UI bridge returned a malformed response.") },
                    )
                }
            }
            val intent = Intent(appContext, NodeBridgeUiDispatchService::class.java).apply {
                action = ACTION_DISPATCH
                putExtra(EXTRA_REQUEST_JSON, request.toJson())
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
            }
            val component = runCatching { appContext.startService(intent) }.getOrElse { error ->
                callback(request.processDead(error.message ?: error.javaClass.name))
                return
            }
            if (component == null) {
                callback(request.processDead("Failed to start main-process UI bridge service."))
            }
        }

        @Suppress("DEPRECATION")
        private fun Intent?.resultReceiverExtra(name: String): ResultReceiver? = this?.getParcelableExtra(name)

        private fun NodeBridgeRequest.processDead(message: String): NodeBridgeResponse {
            return NodeBridgeResponse.failure(
                id,
                NodeBridgeError.providerFailed(
                    message = message,
                    module = module,
                    method = method,
                ),
            )
        }
    }
}

object NodeBridgeUiSessionStore {

    private val sessions = ConcurrentHashMap<String, NodeBridgeUiSession>()

    fun put(session: NodeBridgeUiSession) {
        sessions[session.id] = session
    }

    fun get(id: String): NodeBridgeUiSession? = sessions[id]

    fun remove(id: String): NodeBridgeUiSession? = sessions.remove(id)

    fun activeCount(): Int = sessions.size

    fun closeAll(reason: String = "close_all") {
        sessions.values.forEach { it.close(reason) }
        sessions.clear()
    }
}

class NodeBridgeUiSession(
    val id: String,
    descriptor: JSONObject,
) {

    private val closed = AtomicBoolean(false)
    private val shown = AtomicBoolean(false)
    private val eventQueue = ConcurrentLinkedQueue<JSONObject>()
    private val eventCount = AtomicInteger(0)
    private val stateLock = Any()
    private val state = linkedMapOf<String, JSONObject>()
    private val pendingPatches = mutableListOf<JSONObject>()

    @Volatile
    private var descriptorText = descriptor.toString()

    @Volatile
    private var activityRef: WeakReference<NodeBridgeUiActivity>? = null

    fun rootDescriptor(): JSONObject = JSONObject(descriptorText)

    fun isClosed(): Boolean = closed.get()

    fun attach(activity: NodeBridgeUiActivity) {
        activityRef = WeakReference(activity)
    }

    fun applyPendingPatches(activity: NodeBridgeUiActivity) {
        val patches = synchronized(pendingPatches) {
            pendingPatches.toList().also { pendingPatches.clear() }
        }
        patches.forEach { activity.applyPatch(it) }
    }

    fun detach(activity: NodeBridgeUiActivity) {
        if (activityRef?.get() === activity) {
            activityRef = null
        }
        if (!closed.get()) {
            enqueueEvent(JSONObject().put("type", "close").put("reason", "activity_destroyed"))
        }
    }

    fun markShown() {
        if (shown.compareAndSet(false, true)) {
            enqueueEvent(JSONObject().put("type", "shown"))
        }
    }

    fun replaceRootDescriptor(descriptor: JSONObject) {
        descriptorText = descriptor.toString()
    }

    fun update(patch: JSONObject) {
        val activity = activityRef?.get()
        if (activity != null) {
            activity.applyPatch(patch)
        } else {
            synchronized(pendingPatches) {
                pendingPatches += JSONObject(patch.toString())
            }
        }
        enqueueEvent(JSONObject().put("type", "update").put("id", patch.optString("id")))
    }

    fun close(reason: String) {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        enqueueEvent(JSONObject().put("type", "close").put("reason", reason))
        val activity = activityRef?.get()
        activity?.runOnUiThread {
            activity.finish()
        }
        activityRef = null
    }

    fun recordState(componentId: String, value: JSONObject) {
        if (componentId.isBlank()) {
            return
        }
        synchronized(stateLock) {
            state[componentId] = JSONObject(value.toString())
        }
    }

    fun snapshot(): JSONObject {
        val stateJson = JSONObject()
        synchronized(stateLock) {
            state.forEach { (id, value) -> stateJson.put(id, JSONObject(value.toString())) }
        }
        return JSONObject()
            .put("handleId", id)
            .put("shown", shown.get())
            .put("closed", closed.get())
            .put("state", stateJson)
    }

    fun emitComponentEvent(type: String, componentId: String, fields: JSONObject = JSONObject()) {
        val event = JSONObject(fields.toString())
            .put("type", type)
            .put("id", componentId)
        enqueueEvent(event)
    }

    fun drainEvents(maxEvents: Int): JSONArray {
        val events = JSONArray()
        repeat(maxEvents.coerceAtLeast(0)) {
            val event = eventQueue.poll() ?: return@repeat
            eventCount.decrementAndGet()
            events.put(event)
        }
        return events
    }

    private fun enqueueEvent(rawEvent: JSONObject) {
        val event = JSONObject(rawEvent.toString())
            .put("handleId", id)
            .put("snapshot", snapshot())
        eventQueue.offer(event)
        if (eventCount.incrementAndGet() > MAX_EVENT_QUEUE_SIZE) {
            eventQueue.poll()
            eventCount.decrementAndGet()
        }
    }

    companion object {
        private const val MAX_EVENT_QUEUE_SIZE = 128
    }
}

private class NodeBridgeUiRenderer(
    private val activity: Activity,
    private val session: NodeBridgeUiSession,
) {

    fun createView(descriptor: JSONObject, isRoot: Boolean = false): View {
        val type = normalizeType(descriptor.optString("type"))
        val id = descriptor.optString("id")
        val view = when (type) {
            TYPE_COLUMN -> createLinearLayout(descriptor, LinearLayout.VERTICAL, isRoot)
            TYPE_ROW -> createLinearLayout(descriptor, LinearLayout.HORIZONTAL, isRoot)
            TYPE_TEXT -> createText(descriptor)
            TYPE_BUTTON -> createButton(descriptor)
            TYPE_INPUT -> createInput(descriptor)
            TYPE_IMAGE -> createImage(descriptor)
            TYPE_SCROLL_VIEW -> createScrollView(descriptor, isRoot)
            else -> throw IllegalArgumentException("Unsupported ui component type: ${descriptor.optString("type")}")
        }
        if (id.isNotBlank()) {
            view.tag = id
            view.contentDescription = id
            session.recordState(id, componentState(type, descriptor, view))
        }
        if (view.layoutParams == null) {
            view.layoutParams = defaultLayoutParams(isRoot)
        }
        return view
    }

    fun applyPatch(target: View, patch: JSONObject) {
        val type = componentType(target)
        when (target) {
            is TextView -> {
                if (target !is EditText && patch.has("text")) {
                    target.text = patch.optString("text")
                }
                if (target is Button && patch.has("enabled")) {
                    target.isEnabled = patch.optBoolean("enabled", true)
                }
            }
            is ImageView -> {
                if (patch.has("contentDescription") || patch.has("alt")) {
                    target.contentDescription = patch.optString("contentDescription", patch.optString("alt"))
                }
            }
        }
        if (target is EditText) {
            if (patch.has("text") || patch.has("value")) {
                val text = patch.optString("value", patch.optString("text"))
                if (target.text.toString() != text) {
                    target.setText(text)
                    target.setSelection(target.text.length)
                }
            }
            if (patch.has("hint")) {
                target.hint = patch.optString("hint")
            }
        }
        val id = patch.optString("id").takeIf { it.isNotBlank() } ?: (target.tag as? String).orEmpty()
        if (id.isNotBlank()) {
            session.recordState(id, componentState(type, patch, target))
        }
    }

    private fun createLinearLayout(descriptor: JSONObject, orientation: Int, isRoot: Boolean): LinearLayout {
        val layout = LinearLayout(activity).apply {
            this.orientation = orientation
            gravity = Gravity.START
            if (isRoot) {
                setBackgroundColor(Color.WHITE)
                val padding = dp(16)
                setPadding(padding, padding, padding, padding)
            }
        }
        val children = descriptor.optJSONArray("children") ?: JSONArray()
        for (index in 0 until children.length()) {
            val child = children.optJSONObject(index) ?: continue
            layout.addView(createView(child), defaultLayoutParams(false))
        }
        return layout
    }

    private fun createText(descriptor: JSONObject): TextView {
        return TextView(activity).apply {
            text = descriptor.optString("text")
            textSize = descriptor.optDouble("textSize", 16.0).toFloat().coerceIn(8f, 48f)
            setTextColor(Color.rgb(32, 32, 32))
            setPadding(0, dp(6), 0, dp(6))
        }
    }

    private fun createButton(descriptor: JSONObject): Button {
        val id = descriptor.optString("id")
        return Button(activity).apply {
            text = descriptor.optString("text", id.ifBlank { "Button" })
            isAllCaps = false
            setOnClickListener {
                session.emitComponentEvent(
                    "click",
                    id,
                    JSONObject().put("text", text.toString()),
                )
            }
        }
    }

    private fun createInput(descriptor: JSONObject): EditText {
        val id = descriptor.optString("id")
        return EditText(activity).apply {
            setSingleLine(!descriptor.optBoolean("multiline", false))
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = descriptor.optString("hint")
            setText(descriptor.optString("value", descriptor.optString("text")))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val text = s?.toString().orEmpty()
                    session.recordState(
                        id,
                        JSONObject()
                            .put("id", id)
                            .put("type", TYPE_INPUT)
                            .put("text", text)
                            .put("value", text),
                    )
                    session.emitComponentEvent(
                        "input",
                        id,
                        JSONObject()
                            .put("text", text)
                            .put("value", text),
                    )
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
    }

    private fun createImage(descriptor: JSONObject): ImageView {
        return ImageView(activity).apply {
            contentDescription = descriptor.optString("contentDescription", descriptor.optString("alt"))
            setBackgroundColor(Color.rgb(238, 238, 238))
            minimumHeight = dp(64)
            adjustViewBounds = true
        }
    }

    private fun createScrollView(descriptor: JSONObject, isRoot: Boolean): ScrollView {
        val scrollView = ScrollView(activity)
        val children = descriptor.optJSONArray("children") ?: JSONArray()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            if (isRoot) {
                setBackgroundColor(Color.WHITE)
                val padding = dp(16)
                setPadding(padding, padding, padding, padding)
            }
        }
        for (index in 0 until children.length()) {
            val child = children.optJSONObject(index) ?: continue
            content.addView(createView(child), defaultLayoutParams(false))
        }
        scrollView.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scrollView
    }

    private fun componentState(type: String, descriptor: JSONObject, view: View): JSONObject {
        val state = JSONObject()
            .put("id", descriptor.optString("id", (view.tag as? String).orEmpty()))
            .put("type", type)
        when (view) {
            is EditText -> {
                val text = view.text?.toString().orEmpty()
                state.put("text", text).put("value", text)
            }
            is TextView -> state.put("text", view.text?.toString().orEmpty())
            is ImageView -> state.put("contentDescription", view.contentDescription?.toString().orEmpty())
        }
        return state
    }

    private fun componentType(view: View): String = when (view) {
        is EditText -> TYPE_INPUT
        is Button -> TYPE_BUTTON
        is TextView -> TYPE_TEXT
        is ImageView -> TYPE_IMAGE
        is ScrollView -> TYPE_SCROLL_VIEW
        is LinearLayout -> if (view.orientation == LinearLayout.HORIZONTAL) TYPE_ROW else TYPE_COLUMN
        else -> "View"
    }

    private fun defaultLayoutParams(isRoot: Boolean): ViewGroup.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (isRoot) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt().coerceAtLeast(value)

    companion object {
        private const val TYPE_COLUMN = "Column"
        private const val TYPE_ROW = "Row"
        private const val TYPE_TEXT = "Text"
        private const val TYPE_BUTTON = "Button"
        private const val TYPE_INPUT = "Input"
        private const val TYPE_IMAGE = "Image"
        private const val TYPE_SCROLL_VIEW = "ScrollView"

        private val supportedTypes = setOf(
            TYPE_COLUMN,
            TYPE_ROW,
            TYPE_TEXT,
            TYPE_BUTTON,
            TYPE_INPUT,
            TYPE_IMAGE,
            TYPE_SCROLL_VIEW,
            "Vertical",
            "Horizontal",
        )

        fun validateDescriptor(descriptor: JSONObject, requireType: Boolean = true): String? {
            val rawType = descriptor.optString("type")
            if (requireType && rawType.isBlank()) {
                return "ui descriptor requires a component type."
            }
            if (rawType.isNotBlank() && rawType !in supportedTypes) {
                return "ui descriptor uses an unsupported component type: $rawType."
            }
            val children = descriptor.optJSONArray("children")
            if (children != null) {
                for (index in 0 until children.length()) {
                    val child = children.optJSONObject(index)
                        ?: return "ui descriptor children must be JSON object components."
                    validateDescriptor(child)?.let { return it }
                }
            }
            return null
        }

        private fun normalizeType(rawType: String): String = when (rawType) {
            "Vertical" -> TYPE_COLUMN
            "Horizontal" -> TYPE_ROW
            else -> rawType
        }
    }
}
