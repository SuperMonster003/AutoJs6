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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch as AndroidSwitch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class NodeBridgeUiActivity : Activity() {

    private var session: NodeBridgeUiSession? = null
    private var renderedView: View? = null

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
        renderedView?.let(RuntimeViewComponents::dispose); renderedView = null
        session?.detach(this)
        session = null
        super.onDestroy()
    }

    fun render(descriptor: JSONObject) {
        val currentSession = session ?: return
        val view = NodeBridgeUiRenderer(this, currentSession).createView(descriptor, isRoot = true)
        renderedView?.let(RuntimeViewComponents::dispose); renderedView = view
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
        val target = renderedView?.let { RuntimeViewComponents.find(it, targetId) } ?: return false
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
            val request = runCatching { NodeBridgeRequest.fromJson(requestJson) }.getOrNull()
            val response = runCatching {
                handleRequest(request ?: NodeBridgeRequest.fromJson(requestJson))
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
            val sessionId = if (response.ok) runCatching { JSONObject(response.resultJson.orEmpty()).optString("id") }.getOrDefault("") else ""
            val stream = if (request?.events == true && request.method == "showLayout" && sessionId.isNotBlank() && receiver != null) {
                NodeBridgeEventStream(request.id, sessionId) { value -> receiver.send(RESULT_RESPONSE, value.toBundle()) }
            } else null
            if (stream != null) {
                NodeBridgeUiSessionStore.get(sessionId)?.setEventStream(stream)
                stream.acknowledge(JSONObject(response.resultJson.orEmpty()))
            } else {
                receiver?.send(RESULT_RESPONSE, response.toBundle())
            }
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
            "batchUpdate" -> batchUpdate(request)
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
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

    private fun batchUpdate(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val session = activeSession(request, args) ?: return invalidHandle(request)
        val patches = args.optJSONArray(1)
            ?: return invalid(request, "ui.batchUpdate requires a JSON array of patches.")
        if (patches.length() > MAX_BATCH_PATCHES) {
            return invalid(request, "ui.batchUpdate patch array exceeds $MAX_BATCH_PATCHES entries.")
        }
        val descriptors = mutableListOf<JSONObject>()
        for (index in 0 until patches.length()) {
            val patch = patches.optJSONObject(index)
                ?: return invalid(request, "ui.batchUpdate patches must be JSON object descriptors.")
            val validation = NodeBridgeUiRenderer.validateDescriptor(patch, requireType = false)
            if (validation != null) {
                return invalid(request, validation)
            }
            descriptors += patch
        }
        if (!session.batchUpdate(descriptors)) {
            return invalidHandle(request)
        }
        return NodeBridgeResponse.success(
            request.id,
            session.snapshot().toString(),
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
        if (!session.update(patch)) {
            return invalidHandle(request)
        }
        return NodeBridgeResponse.success(
            request.id,
            session.snapshot().toString(),
        )
    }

    private fun drainEvents(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val session = session(request, args, allowClosed = true) ?: return invalidHandle(request)
        val options = args.optJSONObject(1) ?: JSONObject()
        val maxEvents = options.optInt("maxEvents", DEFAULT_DRAIN_EVENTS).coerceIn(1, MAX_DRAIN_EVENTS)
        val events = session.drainEvents(maxEvents)
        if (session.isClosed() && events.hasTerminalCloseEvent(session.id)) {
            NodeBridgeUiSessionStore.remove(session.id, session)
        }
        return NodeBridgeResponse.success(request.id, events.toString())
    }

    private fun close(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val session = session(request, args, allowClosed = true) ?: return invalidHandle(request)
        session.close(args.optJSONObject(1)?.optString("reason").orEmpty().ifBlank { "close" })
        NodeBridgeUiSessionStore.remove(session.id, session)
        return NodeBridgeResponse.success(request.id, "null")
    }

    private fun activeSession(request: NodeBridgeRequest, args: JSONArray): NodeBridgeUiSession? {
        return session(request, args, allowClosed = false)
    }

    private fun session(
        request: NodeBridgeRequest,
        args: JSONArray,
        allowClosed: Boolean,
    ): NodeBridgeUiSession? {
        val id = args.optString(0).takeIf { it.isNotBlank() } ?: return null
        return NodeBridgeUiSessionStore.get(id)?.takeUnless {
            request.module != NodeBridgeModules.UI || (!allowClosed && it.isClosed())
        }
    }

    private fun JSONArray.hasTerminalCloseEvent(sessionId: String): Boolean = (0 until length()).any { index ->
        val event = optJSONObject(index) ?: return@any false
        event.optString("type") == "close" &&
            event.optString("handleId") == sessionId &&
            event.optJSONObject("snapshot")?.optBoolean("closed") == true
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
        private const val MAX_BATCH_PATCHES = 64

        /** [pending] holds the receivers that may still call [callback]; the owner closes them when it is destroyed. */
        fun dispatch(
            context: Context,
            request: NodeBridgeRequest,
            pending: MutableSet<AutoCloseable>? = null,
            callback: (NodeBridgeResponse) -> Unit,
        ) {
            val appContext = context.applicationContext
            val receiver = DispatchReceiver(request, callback, pending)
            val intent = Intent(appContext, NodeBridgeUiDispatchService::class.java).apply {
                action = ACTION_DISPATCH
                putExtra(EXTRA_REQUEST_JSON, request.toJson())
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
            }
            val component = runCatching { appContext.startService(intent) }.getOrElse { error ->
                receiver.close()
                callback(request.processDead(error.message ?: error.javaClass.name))
                return
            }
            if (component == null) {
                receiver.close()
                callback(request.processDead("Failed to start main-process UI bridge service."))
            }
        }

        // The receiver travels with the start intent through system_server, and its stub stays a JNI global root
        // until that process collects the proxy. Hold the callback (and the provider it captures) only until the
        // last result or the owner's destroy (C33).
        // zh-CN: 接收器随启动 Intent 经过 system_server, 其存根在该进程回收代理之前一直是 JNI 全局根.
        // zh-CN: 回调 (及其捕获的 provider) 只持有到最后一个结果或所有者 destroy 为止 (C33).
        private class DispatchReceiver(
            private val request: NodeBridgeRequest,
            callback: (NodeBridgeResponse) -> Unit,
            private val pending: MutableSet<AutoCloseable>?,
        ) : ResultReceiver(Handler(Looper.getMainLooper())), AutoCloseable {
            private val callback = AtomicReference<((NodeBridgeResponse) -> Unit)?>(callback)

            init {
                pending?.add(this)
            }

            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val target = callback.get() ?: return
                val response = if (resultCode != RESULT_RESPONSE) {
                    request.processDead("Main-process UI bridge returned an unexpected result.")
                } else {
                    val responseJson = resultData?.getString(EXTRA_RESPONSE_JSON).orEmpty()
                    runCatching { NodeBridgeResponse.fromJson(responseJson) }
                        .getOrElse { request.processDead("Main-process UI bridge returned a malformed response.") }
                }
                if (!response.continuesStream()) close()
                target(response)
            }

            // The showLayout acknowledgement of an event stream and every event before "close" are followed by more results.
            // zh-CN: 事件流的 showLayout 确认, 以及 "close" 之前的每个事件, 后面都还有结果.
            private fun NodeBridgeResponse.continuesStream(): Boolean = when {
                event -> runCatching { JSONObject(resultJson.orEmpty()).optString("type") }.getOrDefault("") != "close"
                else -> ok && request.events && request.method == "showLayout"
            }

            override fun close() {
                callback.set(null)
                pending?.remove(this)
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

    /** Open sessions, for the bridge leak gate (docs/memory-phase2/ROADMAP.md T1 (h)). zh-CN: 未关闭的会话数, 供桥接泄漏门禁使用 (docs/memory-phase2/ROADMAP.md T1 (h)). */
    internal fun sessionCount(): Int = sessions.size

    fun put(session: NodeBridgeUiSession) {
        sessions[session.id] = session
    }

    fun get(id: String): NodeBridgeUiSession? = sessions[id]

    fun remove(id: String): NodeBridgeUiSession? = sessions.remove(id)

    fun remove(id: String, session: NodeBridgeUiSession): Boolean = sessions.remove(id, session)

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
    private val eventLifecycleLock = Any()
    private var eventStream: NodeBridgeEventStream? = null
    private val eventQueue = ConcurrentLinkedQueue<JSONObject>()
    private val eventCount = AtomicInteger(0)
    private val stateLock = Any()
    private val state = linkedMapOf<String, JSONObject>()
    private val pendingPatches = mutableListOf<JSONObject>()
    private var interopObserver: ((JSONObject) -> Unit)? = null
    internal fun observeInteropEvents(observer: (JSONObject) -> Unit) { interopObserver = observer }

    @Volatile
    private var descriptorText = descriptor.toString()

    @Volatile
    private var activityRef: WeakReference<NodeBridgeUiActivity>? = null

    fun rootDescriptor(): JSONObject = JSONObject(descriptorText).also { root ->
        fun restore(node: JSONObject) {
            synchronized(stateLock) { state[node.optString("id")]?.let { saved ->
                saved.keys().forEach { key ->
                    val eventPayload = saved.optString("type") in setOf("List", "Tabs") && key in setOf("text", "value")
                    if (key !in setOf("type", "itemCount") && !eventPayload) node.put(key, saved.opt(key))
                }
            } }
            for (key in listOf("children", "tabs")) node.optJSONArray(key)?.let { children ->
                for (i in 0 until children.length()) children.optJSONObject(i)?.let(::restore)
            }
            node.optJSONObject("content")?.let(::restore)
        }
        restore(root)
    }

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
        if (!activity.isChangingConfigurations) close("activity_destroyed")
    }

    fun markShown() {
        synchronized(eventLifecycleLock) {
            if (closed.get() || !shown.compareAndSet(false, true)) {
                return
            }
            enqueueEventLocked(JSONObject().put("type", "shown"))
        }
    }

    fun replaceRootDescriptor(descriptor: JSONObject) {
        descriptorText = descriptor.toString()
        synchronized(stateLock) { state.clear() }
    }

    fun update(patch: JSONObject): Boolean {
        if (!applyOrQueuePatch(patch)) {
            return false
        }
        return enqueueEvent(JSONObject().put("type", "update").put("id", patch.optString("id")))
    }

    fun batchUpdate(patches: List<JSONObject>): Boolean {
        if (closed.get()) {
            return false
        }
        val ids = JSONArray()
        patches.forEach { patch ->
            if (!applyOrQueuePatch(patch)) {
                return false
            }
            patch.optString("id").takeIf { it.isNotBlank() }?.let(ids::put)
        }
        return enqueueEvent(
            JSONObject()
                .put("type", "batchUpdate")
                .put("count", patches.size)
                .put("ids", ids),
        )
    }

    private fun applyOrQueuePatch(patch: JSONObject): Boolean {
        if (closed.get()) {
            return false
        }
        val activity = activityRef?.get()
        if (activity != null) {
            activity.applyPatch(patch)
        } else {
            synchronized(pendingPatches) {
                if (closed.get()) {
                    return false
                }
                pendingPatches += JSONObject(patch.toString())
            }
        }
        return true
    }

    fun close(reason: String) {
        if (!enqueueTerminalEvent(JSONObject().put("type", "close").put("reason", reason))) {
            return
        }
        synchronized(pendingPatches) {
            pendingPatches.clear()
        }
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
        synchronized(eventLifecycleLock) {
            if (closed.get()) {
                return
            }
            synchronized(stateLock) {
                state[componentId] = JSONObject(value.toString())
            }
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

    internal fun setEventStream(stream: NodeBridgeEventStream) = synchronized(eventLifecycleLock) {
        eventStream = stream
        eventQueue.forEach(stream::publish)
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

    private fun enqueueEvent(rawEvent: JSONObject): Boolean = synchronized(eventLifecycleLock) {
        if (closed.get()) {
            return@synchronized false
        }
        enqueueEventLocked(rawEvent)
        true
    }

    private fun enqueueTerminalEvent(rawEvent: JSONObject): Boolean = synchronized(eventLifecycleLock) {
        if (!closed.compareAndSet(false, true)) {
            return@synchronized false
        }
        enqueueEventLocked(rawEvent)
        true
    }

    private fun enqueueEventLocked(rawEvent: JSONObject) {
        val event = JSONObject(rawEvent.toString())
            .put("handleId", id)
            .put("snapshot", snapshot())
        eventQueue.offer(event)
        if (eventCount.incrementAndGet() > MAX_EVENT_QUEUE_SIZE) {
            eventQueue.poll()
            eventCount.decrementAndGet()
        }
        eventStream?.publish(event)
        interopObserver?.invoke(JSONObject(event.toString()))
        if (event.optString("type") == "close" && eventStream != null) {
            eventStream = null
            NodeBridgeUiSessionStore.remove(id)
        }
    }

    companion object {
        private const val MAX_EVENT_QUEUE_SIZE = 128
    }
}

internal class NodeBridgeUiRenderer(
    private val activity: Context,
    private val session: NodeBridgeUiSession,
    private val image: ((JSONObject) -> android.graphics.Bitmap)? = null,
) {

    private val created = ArrayList<View>()
    private var creationDepth = 0
    fun createView(rawDescriptor: JSONObject, isRoot: Boolean = false): View {
        val outer = creationDepth++ == 0
        try { return createNativeView(rawDescriptor, isRoot) } catch (failure: Throwable) {
            if (outer) created.asReversed().forEach(RuntimeViewComponents::dispose)
            throw failure
        } finally { creationDepth--; if (outer) created.clear() }
    }
    private fun createNativeView(rawDescriptor: JSONObject, isRoot: Boolean): View {
        val descriptor = RuntimeViewAttributes.normalize(rawDescriptor)
        val type = normalizeType(descriptor.optString("type"))
        val id = descriptor.optString("id")
        val view = when (type) {
            "Frame", "Compose" -> (if(type == "Compose") RuntimeComposeContainer(activity) else android.widget.FrameLayout(activity)).apply {
                val children=descriptor.optJSONArray("children")
                if(type == "Compose") require(children == null || children.length() == 0) { "Compose containers must initially be empty" }
                if(children != null) repeat(children.length()){ RuntimeViewWidgets.addChild(this, createView(children.getJSONObject(it))) }
            }
            TYPE_COLUMN -> createLinearLayout(descriptor, LinearLayout.VERTICAL, isRoot)
            TYPE_ROW -> createLinearLayout(descriptor, LinearLayout.HORIZONTAL, isRoot)
            TYPE_TEXT -> createText(descriptor)
            TYPE_BUTTON -> createButton(descriptor)
            TYPE_INPUT -> createInput(descriptor)
            TYPE_IMAGE -> createImage(descriptor)
            TYPE_SCROLL_VIEW -> createScrollView(descriptor, isRoot)
            TYPE_LIST -> createList(descriptor)
            TYPE_SWITCH -> createSwitch(descriptor)
            TYPE_CHECKBOX -> createCheckbox(descriptor)
            TYPE_PROGRESS -> createProgress(descriptor)
            TYPE_TABS -> createTabs(descriptor)
            TYPE_TAB -> createTabPlaceholder(descriptor)
            else -> RuntimeViewWidgets.create(type, activity, descriptor, session) { createView(it) }
                ?: throw IllegalArgumentException("Unsupported ui component type: ${descriptor.optString("type")}")
        }
        created.add(view)
        if (id.isNotBlank()) {
            view.id = View.generateViewId()
            view.tag = id
            view.contentDescription = id
            session.recordState(id, componentState(type, descriptor, view))
        }
        if (view.layoutParams == null) {
            view.layoutParams = defaultLayoutParams(isRoot)
        }
        if (view !is CompoundButton && view !is android.widget.CheckedTextView && view !is Button && view !is android.widget.AdapterView<*>) {
            val clickable = view.isClickable
            view.setOnClickListener { session.emitComponentEvent("click", id, JSONObject()) }
            view.isClickable = clickable
        }
        // Tabs consumes its initial selection while constructing the content; it is not an AdapterView.
        val attributes = if (type == TYPE_TABS) JSONObject(descriptor.toString()).apply { remove("selectedIndex") } else descriptor
        RuntimeViewAttributes.apply(view, attributes, image = image)
        return view
    }

    fun applyPatch(target: View, patch: JSONObject) {
        val type = componentType(target)
        when (target) {
            is CompoundButton -> {
                if (patch.has("text")) {
                    target.text = patch.optString("text")
                }
                if (patch.has("checked") || patch.has("value")) {
                    target.isChecked = patch.optBoolean("checked", patch.optBoolean("value", target.isChecked))
                }
                if (patch.has("enabled")) {
                    target.isEnabled = patch.optBoolean("enabled", true)
                }
            }
            is ProgressBar -> {
                if (patch.has("max")) {
                    target.max = patch.optInt("max", target.max).coerceAtLeast(1)
                }
                if (patch.has("progress") || patch.has("value")) {
                    target.progress = patch.optInt("progress", patch.optInt("value", target.progress)).coerceIn(0, target.max)
                }
                if (patch.has("indeterminate")) {
                    target.isIndeterminate = patch.optBoolean("indeterminate", false)
                }
            }
            is ListView -> {
                if (patch.has("items")) {
                    target.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, listLabels(patch.optJSONArray("items")))
                }
                if (patch.has("enabled")) {
                    target.isEnabled = patch.optBoolean("enabled", true)
                }
            }
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
        RuntimeViewAttributes.apply(target, patch, image = image)
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
            RuntimeViewWidgets.addChild(layout, createView(child))
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
            RuntimeViewWidgets.addChild(content, createView(child))
        }
        scrollView.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scrollView
    }

    private fun createList(descriptor: JSONObject): ListView {
        val id = descriptor.optString("id")
        val items = descriptor.optJSONArray("items") ?: JSONArray()
        return ListView(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, listLabels(items))
            setOnItemClickListener { _, _, position, _ ->
                val item = listItem(RuntimeViewAttributes.declared(this).optJSONArray("items") ?: items, position)
                session.recordState(
                    id,
                    JSONObject()
                        .put("id", id)
                        .put("type", TYPE_LIST)
                        .put("selectedIndex", position)
                        .put("text", item.first)
                        .put("value", item.second),
                )
                session.emitComponentEvent(
                    "select",
                    id,
                    JSONObject()
                        .put("index", position)
                        .put("selectedIndex", position)
                        .put("text", item.first)
                        .put("value", item.second),
                )
            }
        }
    }

    private fun createSwitch(descriptor: JSONObject): AndroidSwitch {
        return AndroidSwitch(activity).apply {
            configureCompoundButton(descriptor, TYPE_SWITCH, this)
        }
    }

    private fun createCheckbox(descriptor: JSONObject): CheckBox {
        return CheckBox(activity).apply {
            configureCompoundButton(descriptor, TYPE_CHECKBOX, this)
        }
    }

    private fun configureCompoundButton(descriptor: JSONObject, type: String, button: CompoundButton) {
        val id = descriptor.optString("id")
        button.text = descriptor.optString("text", id)
        button.isChecked = descriptor.optBoolean("checked", descriptor.optBoolean("value", false))
        button.setOnCheckedChangeListener { _, checked ->
            session.recordState(
                id,
                JSONObject()
                    .put("id", id)
                    .put("type", type)
                    .put("checked", checked)
                    .put("value", checked),
            )
            session.emitComponentEvent(
                "change",
                id,
                JSONObject()
                    .put("checked", checked)
                    .put("value", checked),
            )
        }
    }

    private fun createProgress(descriptor: JSONObject): ProgressBar {
        val max = descriptor.optInt("max", 100).coerceAtLeast(1)
        return ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            this.max = max
            isIndeterminate = descriptor.optBoolean("indeterminate", false)
            progress = descriptor.optInt("progress", descriptor.optInt("value", 0)).coerceIn(0, max)
            setPadding(0, dp(8), 0, dp(8))
        }
    }

    private fun createTabs(descriptor: JSONObject): LinearLayout {
        val id = descriptor.optString("id")
        val tabs = descriptor.optJSONArray("tabs") ?: descriptor.optJSONArray("children") ?: JSONArray()
        val selected = descriptor.optInt("selectedIndex", 0).coerceIn(0, (tabs.length() - 1).coerceAtLeast(0))
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val tabRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        fun renderContent(index: Int) {
            RuntimeViewComponents.dispose(content)
            content.removeAllViews()
            val tab = tabs.optJSONObject(index) ?: return
            val tabChildren = tab.optJSONArray("children")
            val tabContent = tab.optJSONObject("content")
            if (tabChildren != null) {
                for (childIndex in 0 until tabChildren.length()) {
                    val child = tabChildren.optJSONObject(childIndex) ?: continue
                    RuntimeViewWidgets.addChild(content, createView(child))
                }
            } else if (tabContent != null) {
                RuntimeViewWidgets.addChild(content, createView(tabContent))
            }
        }
        for (index in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(index) ?: continue
            val title = tab.optString("title", tab.optString("text", tab.optString("id", "Tab ${index + 1}")))
            tabRow.addView(
                Button(activity).apply {
                    text = title
                    isAllCaps = false
                    setOnClickListener {
                        renderContent(index)
                        session.recordState(
                            id,
                            JSONObject()
                                .put("id", id)
                                .put("type", TYPE_TABS)
                                .put("selectedIndex", index)
                                .put("text", title)
                                .put("value", tab.opt("value") ?: tab.optString("id", title)),
                        )
                        session.emitComponentEvent(
                            "tab",
                            id,
                            JSONObject()
                                .put("index", index)
                                .put("selectedIndex", index)
                                .put("tabId", tab.optString("id"))
                                .put("text", title)
                                .put("value", tab.opt("value") ?: tab.optString("id", title)),
                        )
                    }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        renderContent(selected)
        layout.addView(tabRow, defaultLayoutParams(false))
        layout.addView(content, defaultLayoutParams(false))
        return layout
    }

    private fun createTabPlaceholder(descriptor: JSONObject): TextView {
        return TextView(activity).apply {
            text = descriptor.optString("title", descriptor.optString("text"))
            textSize = 16f
            setPadding(0, dp(6), 0, dp(6))
        }
    }

    private fun componentState(type: String, descriptor: JSONObject, view: View): JSONObject {
        val state = JSONObject()
            .put("id", descriptor.optString("id", (view.tag as? String).orEmpty()))
            .put("type", type)
        when (view) {
            is CompoundButton -> state
                .put("text", view.text?.toString().orEmpty())
                .put("checked", view.isChecked)
                .put("value", view.isChecked)
            is ProgressBar -> state
                .put("progress", view.progress)
                .put("value", view.progress)
                .put("max", view.max)
                .put("indeterminate", view.isIndeterminate)
            is ListView -> state
                .put("selectedIndex", descriptor.optInt("selectedIndex", -1))
                .put("itemCount", descriptor.optJSONArray("items")?.length() ?: 0)
            is EditText -> {
                val text = view.text?.toString().orEmpty()
                state.put("text", text).put("value", text)
            }
            is TextView -> state.put("text", view.text?.toString().orEmpty())
            is ImageView -> state.put("contentDescription", view.contentDescription?.toString().orEmpty())
            is LinearLayout -> if (type == TYPE_TABS) {
                state.put("selectedIndex", descriptor.optInt("selectedIndex", 0))
            }
        }
        return state
    }

    private fun componentType(view: View): String = when (view) {
        is AndroidSwitch -> TYPE_SWITCH
        is CheckBox -> TYPE_CHECKBOX
        is ProgressBar -> TYPE_PROGRESS
        is ListView -> TYPE_LIST
        is EditText -> TYPE_INPUT
        is Button -> TYPE_BUTTON
        is TextView -> TYPE_TEXT
        is ImageView -> TYPE_IMAGE
        is ScrollView -> TYPE_SCROLL_VIEW
        is LinearLayout -> if (view.orientation == LinearLayout.HORIZONTAL) TYPE_ROW else TYPE_COLUMN
        else -> "View"
    }

    private fun listLabels(items: JSONArray?): List<String> {
        if (items == null) {
            return emptyList()
        }
        return (0 until items.length()).map { index -> listItem(items, index).first }
    }

    private fun listItem(items: JSONArray, index: Int): Pair<String, Any> {
        val raw = items.opt(index)
        val item = raw as? JSONObject
        val text = item?.optString("text", item.optString("label", item.optString("value")))
            ?: raw?.toString().orEmpty()
        val value = item?.opt("value") ?: raw?.toString().orEmpty()
        return text to value
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
        private const val TYPE_LIST = "List"
        private const val TYPE_SWITCH = "Switch"
        private const val TYPE_CHECKBOX = "Checkbox"
        private const val TYPE_PROGRESS = "Progress"
        private const val TYPE_TABS = "Tabs"
        private const val TYPE_TAB = "Tab"
        private const val TYPE_WEB_VIEW = "WebView"

        private val supportedTypes = setOf(
            "Frame", "Compose",
            TYPE_COLUMN,
            TYPE_ROW,
            TYPE_TEXT,
            TYPE_BUTTON,
            TYPE_INPUT,
            TYPE_IMAGE,
            TYPE_SCROLL_VIEW,
            TYPE_LIST,
            TYPE_SWITCH,
            TYPE_CHECKBOX,
            TYPE_PROGRESS,
            TYPE_TABS,
            TYPE_TAB,
            "Vertical",
            "Horizontal",
        )

        fun validateDescriptor(descriptor: JSONObject, requireType: Boolean = true): String? {
            val rawType = descriptor.optString("type")
            if (requireType && rawType.isBlank()) {
                return "ui descriptor requires a component type."
            }
            val normalizedType = normalizeType(rawType)
            if (rawType.isNotBlank() && normalizedType !in supportedTypes && normalizedType !in RuntimeViewWidgets.types) {
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
            val tabs = descriptor.optJSONArray("tabs")
            if (tabs != null) {
                for (index in 0 until tabs.length()) {
                    val tab = tabs.optJSONObject(index)
                        ?: return "ui descriptor tabs must be JSON object components."
                    validateDescriptor(tab, requireType = false)?.let { return it }
                    val content = tab.optJSONObject("content")
                    if (content != null) {
                        validateDescriptor(content)?.let { return it }
                    }
                }
            }
            return null
        }

        private fun normalizeType(rawType: String): String = when (rawType) {
            "frame", "Frame", "FrameLayout" -> "Frame"
            "compose", "Compose" -> "Compose"
            "Vertical", "vertical", "LinearLayout", "linearLayout", "Column", "column" -> TYPE_COLUMN
            "Horizontal", "horizontal", "Row", "row" -> TYPE_ROW
            "Text", "text", "TextView" -> TYPE_TEXT
            "Button", "button" -> TYPE_BUTTON
            "Input", "input", "EditText", "editText" -> TYPE_INPUT
            "Image", "image", "ImageView" -> TYPE_IMAGE
            "ScrollView", "scrollView" -> TYPE_SCROLL_VIEW
            "List", "list", "ListView" -> TYPE_LIST
            "Switch", "switch" -> TYPE_SWITCH
            "Checkbox", "CheckBox", "checkbox", "checkBox" -> TYPE_CHECKBOX
            "Progress", "ProgressBar", "progress", "progressBar" -> TYPE_PROGRESS
            "Tabs", "tabs" -> TYPE_TABS
            "Tab", "tab" -> TYPE_TAB
            "WebView", "webView", "webview" -> TYPE_WEB_VIEW
            else -> RuntimeViewWidgets.aliases[rawType] ?: rawType
        }
    }
}
