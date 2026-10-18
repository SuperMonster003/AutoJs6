package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import org.autojs.autojs.core.plugin.compose.*
import org.autojs.autojs.runtime.api.augment.compose.ComposeScriptValues
import org.autojs.autojs6.R
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.catalog.ComponentInvocation
import org.autojs.plugin.compose.api.dialog.DialogOptionsV1
import org.autojs.plugin.compose.api.interop.AndroidViewBindingV1
import org.autojs.plugin.compose.api.model.*
import org.autojs.plugin.compose.api.v2.ComposeUiV2
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Data-only bridge. All renderer operations and ownership changes are confined to Android main. */
internal class RuntimeComposeBridge(
    private val context: Context,
    private val interop: RuntimeUiInterop,
    private val image: (JSONObject) -> Bitmap,
) : Closeable {
    private val main = Handler(Looper.getMainLooper())
    private val destroyed = AtomicBoolean()
    private val sessions = ConcurrentHashMap<String, Session>()
    val activeCount: Int get() = sessions.values.count { !it.closed }
    private val catalog = ComposeUiV2.interopCatalog
    private var plugin: ComposeUiPluginHost? = null
    private fun host() = plugin ?: ComposeUiPluginHost(context).also { plugin = it }

    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        main.post {
            try {
                check(!destroyed.get()) { "Compose execution has finished" }
                val args = JSONArray(request.argsJson)
                val result: Any? = when (request.method) {
                    "isAvailable" -> host().isAvailable()
                    "version" -> host().load().let { loaded -> JSONObject()
                        .put("plugin", loaded.identity.version).put("contract", loaded.factory.contractVersion())
                        .put("compose", loaded.factory.capabilities().getString(ComposeUiCapabilityKeys.COMPOSE_VERSION)) }
                    "mount", "dialog", "floaty", "attach" -> {
                        require(sessions.values.count { !it.closed } < ComposeUiLimits.MAX_SESSIONS_PER_ENGINE) { "At most ${ComposeUiLimits.MAX_SESSIONS_PER_ENGINE} Compose sessions may be active" }
                        val loaded = host().load()
                        val container=if(request.method=="attach")interop.container(args.getJSONObject(0))else null
                        val rootIndex=if(container==null)0 else 1
                        val session = Session("compose-${UUID.randomUUID()}", loaded, request.method, args.optJSONObject(rootIndex+1) ?: JSONObject(),container)
                        try {
                            session.prepare(args.getJSONObject(rootIndex))
                            sessions[session.id] = session
                            RuntimeComposeActivity.sessions[session.id] = session
                            if (request.method == "floaty" || container != null) session.attach(null) else context.startActivity(
                                Intent(context, RuntimeComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                    .putExtra("session", session.id))
                            session.snapshot()
                        } catch (error: Throwable) { session.close(); sessions.remove(session.id); throw error }
                    }
                    else -> {
                        val session = sessions[args.getString(0)] ?: error("Compose handle belongs to another execution or is closed")
                        when (request.method) {
                            "render" -> { session.prepare(args.getJSONObject(1)); session.snapshot() }
                            "setTheme" -> { session.setTheme(args.getJSONObject(1)); JSONObject.NULL }
                            "command" -> { session.command(args.getJSONObject(1)); JSONObject.NULL }
                            "window" -> session.window(args.optJSONObject(1) ?: JSONObject())
                            "drainEvents" -> {
                                if (session.events.isEmpty() && !session.closed) {
                                    session.waitForEvents(request, callback)
                                    return@post
                                }
                                session.drain()
                            }
                            "close" -> { session.close(); sessions.remove(session.id); JSONObject.NULL }
                            else -> error("Unsupported Compose operation: ${request.method}")
                        }
                    }
                }
                callback(NodeBridgeResponse.success(request.id, json(result)))
            } catch (error: Throwable) { callback(failure(request, error)) }
        }
    }

    private fun failure(request: NodeBridgeRequest, error: Throwable): NodeBridgeResponse {
        val contract = error as? ComposeUiContractException
        val code = contract?.code ?: (error as? ComposeUiError)?.code
        return NodeBridgeResponse.failure(request.id, NodeBridgeError.providerFailed(
            message = listOfNotNull(code, error.message, contract?.prop?.let { "prop=$it" }, contract?.nodeId?.let { "nodeId=$it" })
                .joinToString(": "), module = "compose", method = request.method))
    }

    override fun close() {
        if (!destroyed.compareAndSet(false, true)) return
        main.post { sessions.values.toList().forEach { it.close() }; sessions.clear(); plugin?.close(); plugin = null }
    }

    internal inner class Session(
        val id: String, private val loaded: ComposeUiPluginLoader.Loaded,
        private val mode: String, private val options: JSONObject,
        val container: RuntimeUiInterop.NativeView? = null,
    ) {
        @Volatile var closed = false; private set
        val events = ArrayDeque<JSONObject>()
        private var waiter: (() -> Unit)? = null
        private var renderer: ComposeAndroidRenderer? = null
        private var activity: RuntimeComposeActivity? = null
        private var overlay: View? = null
        private var watch: Closeable? = null
        private var tree: UiTree? = null
        private var bindings: List<AndroidViewBindingV1> = emptyList()
        private var borrowedViews: List<RuntimeUiInterop.NativeView> = emptyList()
        private var mountedView: View? = null
        private var renderOwner: ComponentActivity? = null
        private var containerListener: View.OnAttachStateChangeListener? = null
        private var containerDisposal: Closeable? = null
        private var ids: Map<Int, Int> = emptyMap()
        private var generation = 0L
        private var rendererEpoch = 0L
        private val edits = HashMap<Int, Pair<Long, String>>()
        private var theme: ThemeSpec? = null
        private val reconciler = TreeReconciler(catalog)
        // Copies isolate renderer images from explicit images.recycle() in another JS thread.
        private val images = ArrayList<Bitmap>()
        private var collectingImages = ArrayList<Bitmap>()
        private val values = ComposeScriptValues(imageResolver = { raw ->
            val bitmap = if (raw is Map<*, *>) image(JSONObject(raw)) else {
                require(raw is String && raw.startsWith("@drawable/")) { "Compose Image requires an image handle or @drawable/name" }
                val resource = context.resources.getIdentifier(raw.removePrefix("@drawable/"), "drawable", context.packageName)
                require(resource != 0) { "Unknown host drawable" }
                requireNotNull(ContextCompat.getDrawable(context, resource)).toBitmap()
            }
            collectingImages += bitmap
            ComposeScriptValues.ResolvedImage(UiValue.BitmapRef(bitmap), bitmap)
        }, colorParser = { if (it is Number) it.toInt() else Color.parseColor(it.toString()) })

        fun snapshot() = JSONObject().put("id", id).put("generation", generation).put("closed", closed)
        fun prepare(descriptor: JSONObject) {
            check(!closed)
            val nodes = ArrayList<UiNode>()
            val nativeViews = HashMap<Int,RuntimeUiInterop.NativeView>()
            val visiting = HashSet<Int>()
            collectingImages = ArrayList()
            fun node(raw: JSONObject, depth: Int): Int {
                require(depth <= ComposeUiLimits.MAX_DEPTH && nodes.size < ComposeUiLimits.MAX_NODES_PER_SESSION)
                val number = raw.getInt("nodeId")
                require(number > 0 && visiting.add(number)) { "Repeated Compose node" }
                val type = raw.getString("type")
                val spec = catalog.component(type) ?: error("Unknown Compose component: $type")
                require(spec.invocation == ComponentInvocation.NODE && type in loaded.components) { "Component unavailable in this runtime: $type" }
                val fields=JSONObject((raw.optJSONObject("props")?:JSONObject()).toString())
                if(type=="AndroidView"){
                    require(loaded.androidViewFactory!=null){"The renderer has no AndroidView interop extension"}
                    val record=interop.resolve(fields.getJSONObject("view"));nativeViews[number]=record
                    fields.put("view",record.lease)
                }
                val props = values.normalize(type, objectMap(fields))
                val modifiers = raw.optJSONArray("modifier") ?: JSONArray()
                val operations = (0 until modifiers.length()).map { index ->
                    val item = modifiers.getJSONObject(index)
                    val name = item.getString("name")
                    val modifier = catalog.modifier(name) ?: error("Unknown modifier: $name")
                    val args = item.getJSONArray("args")
                    val signature = modifier.signatures.firstOrNull { args.length() in it.requiredCount..it.args.size }
                        ?: error("Invalid modifier arguments: $name")
                    ModifierOp(name, (0 until args.length()).map { i ->
                        if (signature.args[i].callback) UiValue.Num(args.getInt(i).also { require(it > 0) }.toDouble())
                        else values.modifierArgument("Modifier", name, rawValue(args.opt(i)), signature.args[i].type)
                    }, modifier.scope)
                }
                val children = raw.optJSONArray("children") ?: JSONArray()
                val slots = raw.optJSONObject("slots") ?: JSONObject()
                val callbacks = raw.optJSONObject("callbacks") ?: JSONObject()
                val result = UiNode(number, type, raw.optString("key").takeIf { raw.has("key") }, props.props,
                    props.modifier + operations,
                    (0 until children.length()).map { node(children.getJSONObject(it), depth + 1) },
                    slots.keys().asSequence().associateWith { node(slots.getJSONObject(it), depth + 1) },
                    callbacks.keys().asSequence().associate { values.eventName(type, it) to callbacks.getInt(it) })
                nodes += result
                return number
            }
            try {
                val root = node(descriptor, 1)
                val result = reconciler.reconcile(tree, UiTree(root, nodes))
                val previous = tree?.nodes?.associateBy { it.nodeId }.orEmpty()
                val commands = ArrayList<UiCommand.Edit>()
                val next = UiTree(result.tree.rootId, result.tree.nodes.map { node ->
                    val old = previous[node.nodeId]
                    if (old != null && node.type in setOf("TextField", "OutlinedTextField")) {
                        val desired = (node.props["text"] as? UiValue.Str)?.value.orEmpty()
                        val observed = edits[node.nodeId] ?: (0L to (old.props["text"] as? UiValue.Str)?.value.orEmpty())
                        if (desired != observed.second) commands += UiCommand.Edit(node.nodeId, desired, editSeq = observed.first)
                        // Renderer text fields retain their initial declaration. Controlled text
                        // changes use the current native edit sequence, never a SetProps patch.
                        UiNode(node.nodeId, node.type, node.key, node.props + ("text" to (old.props["text"] ?: UiValue.Str(""))),
                            node.modifier, node.children, node.slots, node.callbacks)
                    } else node
                })
                val nextGeneration = generation + 1
                val nextBindings=nativeViews.map { (draft,record)->AndroidViewBindingV1(result.draftToStable.getValue(draft),record.lease,record.view) }
                val nextViews = nativeViews.values.toList()
                require(nextViews.distinct().size == nextViews.size) { "An AndroidView cannot be bound twice" }
                nextViews.forEach { record ->
                    require(record.borrower == null || record.borrower === this) { "AndroidView belongs to another Compose session" }
                    require(record.view.parent == null || record.borrower === this) { "AndroidView already has a parent" }
                }
                nextBindings.forEach { binding ->
                    var ancestor:View?=container?.view
                    while(ancestor!=null){require(ancestor!==binding.view){"An AndroidView cannot contain its own Compose container"};ancestor=ancestor.parent as? View}
                }
                renderer?.validateViews(nextBindings)
                renderer?.apply(UiPatchBatch(1, nextGeneration, TreeReconciler.diff(tree, next)),nextBindings)
                borrowedViews.filter { it !in nextViews }.forEach { if (it.borrower === this) it.borrower = null }
                nextViews.forEach { it.borrower = this }; borrowedViews = nextViews
                bindings=nextBindings
                tree = next; ids = result.draftToStable; generation = nextGeneration
                commands.forEach { command -> renderer?.execute(command); edits[command.nodeId] = command.editSeq to command.text.orEmpty() }
                edits.keys.retainAll(next.nodes.map { it.nodeId }.toSet())
                // The renderer no longer borrows the old tree after a successful atomic apply.
                // Compose may still be finishing the previous draw. Let its references keep old
                // bitmaps alive until GC instead of recycling pixels underneath that draw.
                images.clear(); images.addAll(collectingImages)
            } catch (error: Throwable) { collectingImages.forEach { it.recycle() }; throw error }
            finally { collectingImages = ArrayList() }
        }

        fun attach(owner: RuntimeComposeActivity?) {
            if (closed) { owner?.finish(); return }
            if (container != null && containerDisposal == null) containerDisposal = interop.onDispose(container) { close() }
            activity = owner
            val lifecycle=owner?:container?.let(interop::activity)
            renderOwner=lifecycle
            val epoch = ++rendererEpoch
            try {
                val base = lifecycle ?: container?.view?.context ?: ContextThemeWrapper(context, R.style.ScriptTheme)
                if (mode == "floaty") check(Settings.canDrawOverlays(context)) { "Android overlay permission is required" }
                val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val owners = if (lifecycle == null) ComposeFloatyOwners() else null
                val previous=container?.let { target->sessions.values.firstOrNull{it!==this&&!it.closed&&it.container?.view===target.view} }
                val target = ComposeAndroidRenderer(base, loaded, 1, { view ->
                    mountedView=view
                    if(container!=null){
                        interop.container(container.json())
                        val parent=container.view as ViewGroup
                        require(parent.childCount==0 || previous?.mountedView?.parent===parent&&parent.childCount==1){"Compose container already has unrelated content"}
                        previous?.close()
                        parent.addView(view,ViewGroup.LayoutParams(-1,-1))
                    } else if (owner != null) owner.setContentView(view) else {
                        val params = WindowManager.LayoutParams(
                            options.optInt("width", -2), options.optInt("height", -2),
                            org.autojs.autojs.ui.enhancedfloaty.util.WindowTypeCompat.getPhoneWindowType(),
                            if (options.optBoolean("focusable", false)) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            android.graphics.PixelFormat.TRANSLUCENT)
                        params.gravity = Gravity.TOP or Gravity.START
                        params.x = options.optInt("x", 0); params.y = options.optInt("y", 0)
                        if (!options.optBoolean("touchable", true)) params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        manager.addView(view, params); overlay = view
                    }
                }, lifecycle, owners, onDestroyed = { if (lifecycle?.isChangingConfigurations != true) close() }, rendererFactory = if (mode != "dialog") null else { environment ->
                    requireNotNull(loaded.dialogFactory) { "Compose dialog extension is unavailable" }.create(environment,
                        DialogOptionsV1(options.optString("type", "alert"), options.optBoolean("cancelable", true)))
                }, useActivityInsets = mode != "dialog" && container == null)
                renderer = target
                target.open { event ->
                    main.post {
                        if (epoch != rendererEpoch) return@post
                        if (event.type == "valueChange" && event.payload.containsKey("editSeq"))
                            edits[event.nodeId] = event.payload.getLong("editSeq") to event.payload.getString("text").orEmpty()
                        if (!closed) {
                            if (event.type == "dialogDismissRequest") { close(); return@post }
                            // A draw may still hold an older callback after a newer tree was
                            // accepted. Route live node events to that tree's current handler.
                            val callback = if (event.nodeId == ComposeUiContract.NO_NODE_ID) event.callbackId else {
                                val node = tree?.nodes?.firstOrNull { it.nodeId == event.nodeId } ?: return@post
                                node.callbacks[event.type] ?: if (event.type == "click")
                                    (node.modifier.firstOrNull { it.name == "clickable" }?.args?.firstOrNull() as? UiValue.Num)?.value?.toInt()
                                else null
                            } ?: return@post
                            val data = JSONObject().put("type", event.type).put("callbackId", callback).put("generation", generation)
                            event.payload.keySet().forEach { key -> data.put(key, event.payload.get(key)) }
                            emit(data)
                        }
                    }
                }
                theme = theme ?: options.optJSONObject("theme")?.let { values.theme(objectMap(it)) }
                theme?.let(target::setTheme)
                tree = requireNotNull(tree).let { saved -> UiTree(saved.rootId, saved.nodes.map { node ->
                    val edited = edits[node.nodeId]
                    if (edited == null) node else {
                        edits[node.nodeId] = 0L to edited.second
                        UiNode(node.nodeId, node.type, node.key, node.props + ("text" to UiValue.Str(edited.second)),
                            node.modifier, node.children, node.slots, node.callbacks)
                    }
                }) }
                target.validateViews(bindings)
                target.apply(UiPatchBatch(1, generation, listOf(UiPatch.SetRoot(requireNotNull(tree)))),bindings)
                if(container!=null&&containerListener==null){
                    containerListener=object:View.OnAttachStateChangeListener{
                        override fun onViewDetachedFromWindow(view:View){
                            if(closed)return
                            if(interop.recreating(container)||renderOwner?.isChangingConfigurations==true)pauseRenderer()else close()
                        }
                        override fun onViewAttachedToWindow(view:View){
                            if(!closed){
                                if(renderer!=null&&renderOwner==null&&interop.activity(container)!=null)pauseRenderer()
                                if(renderer==null)attach(null)
                            }
                        }
                    }.also{container.view.addOnAttachStateChangeListener(it)}
                }
                target.attach()
                watch = host().watch(loaded.identity) { error -> main.post { emit(JSONObject().put("type", "error").put("message", error.message)); close() } }
                emit(JSONObject().put("type", "shown"))
            } catch (error: Throwable) { emit(JSONObject().put("type", "error").put("message", error.message)); close() }
        }

        fun detach(owner: RuntimeComposeActivity) {
            if (activity !== owner) return
            if (owner.isChangingConfigurations && !closed) {
                pauseRenderer(); activity = null
            } else close()
        }
        private fun pauseRenderer(){rendererEpoch++;watch?.close();watch=null;renderer?.dispose();renderer=null;mountedView=null;renderOwner=null}
        fun setTheme(value: JSONObject) { check(!closed); val next = values.theme(objectMap(value)); renderer?.setTheme(next); theme = next }
        fun command(value: JSONObject) {
            check(!closed)
            if (value.getString("type") == "snackbar") {
                requireNotNull(renderer).execute(UiCommand.ShowSnackbar(value.getString("message"),
                    value.optString("actionLabel").takeIf { value.has("actionLabel") && !value.isNull("actionLabel") },
                    SnackbarDuration.valueOf(value.optString("duration", "short").uppercase(java.util.Locale.ROOT)),
                    value.optInt("callbackId").takeIf { it > 0 }))
                return
            }
            val id = ids[value.getInt("nodeId")] ?: error("Compose node is detached")
            val command = when (value.getString("type")) {
                "focus" -> UiCommand.Focus(id)
                "blur" -> UiCommand.Blur(id)
                "scrollTo" -> UiCommand.ScrollTo(id, if (value.has("index")) value.getInt("index") else null,
                    if (value.has("offset")) value.getInt("offset") else null)
                "edit" -> UiCommand.Edit(id, if (value.has("text")) value.getString("text") else null,
                    value.optJSONObject("selection")?.let { TextSelection(it.getInt("start"), it.getInt("end")) }, edits[id]?.first ?: 0L)
                else -> error("Unsupported Compose command")
            }
            requireNotNull(renderer) { "Compose has not been shown" }.execute(command)
        }
        fun window(patch: JSONObject): JSONObject {
            check(!closed && mode == "floaty") { "Window controls require an active floating session" }
            val view = requireNotNull(overlay)
            val params = view.layoutParams as WindowManager.LayoutParams
            require(patch.keys().asSequence().all { it in setOf("x", "y", "width", "height", "focusable", "touchable") })
            fun integer(name: String, default: Int): Int {
                if (!patch.has(name)) return default
                val number = (patch.get(name) as? Number)?.toDouble() ?: error("$name must be an integer")
                require(number.isFinite() && number % 1 == 0.0 && number >= Int.MIN_VALUE && number <= Int.MAX_VALUE)
                return number.toInt()
            }
            val x = integer("x", params.x); val y = integer("y", params.y)
            val width = integer("width", params.width); val height = integer("height", params.height)
            require(width > 0 || width == -1 || width == -2); require(height > 0 || height == -1 || height == -2)
            var flags = params.flags
            for ((name, flag) in listOf("focusable" to WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                "touchable" to WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)) if (patch.has(name)) {
                val enabled = patch.get(name) as? Boolean ?: error("$name must be boolean")
                flags = if (enabled) flags and flag.inv() else flags or flag
            }
            params.x=x; params.y=y; params.width=width; params.height=height; params.flags=flags
            if (patch.length() > 0) (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(view, params)
            return JSONObject().put("x", params.x).put("y", params.y).put("width", view.width).put("height", view.height)
        }
        private fun emit(value: JSONObject) {
            if (events.size >= 128) {
                events.clear(); events.add(JSONObject().put("type", "error").put("message", "Compose event queue overflow"))
            }
            events.add(value); waiter?.invoke()
        }
        fun waitForEvents(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
            check(waiter == null) { "Only one Compose event reader is allowed" }
            lateinit var done: Runnable
            val reply = {
                waiter = null; main.removeCallbacks(done)
                callback(NodeBridgeResponse.success(request.id, drain().toString()))
            }
            done = Runnable { reply() }; waiter = reply
            main.postDelayed(done, 1000)
        }
        fun drain(): JSONArray {
            val batch = JSONArray()
            repeat(minOf(events.size, 32)) { batch.put(events.removeFirst()) }
            if (!closed && events.isEmpty()) batch.put(JSONObject().put("type", "frame").put("generation", generation))
            if (closed && events.isEmpty()) sessions.remove(id)
            return batch
        }
        fun close() {
            if (closed) return
            closed = true; RuntimeComposeActivity.sessions.remove(id)
            rendererEpoch++
            watch?.close(); watch = null
            containerListener?.let { container?.view?.removeOnAttachStateChangeListener(it) };containerListener=null
            containerDisposal?.close(); containerDisposal = null
            runCatching { overlay?.let { (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(it) } }
                .onFailure { emit(JSONObject().put("type", "error").put("message", it.message)) }
            overlay = null
            runCatching { renderer?.dispose() }.onFailure { emit(JSONObject().put("type", "error").put("message", it.message)) }
            renderer = null
            borrowedViews.forEach { if (it.borrower === this) it.borrower = null }; borrowedViews = emptyList()
            mountedView=null;bindings=emptyList()
            images.forEach { it.recycle() }; images.clear(); tree = null; ids = emptyMap(); edits.clear()
            activity?.finish(); activity = null
            emit(JSONObject().put("type", "close"))
        }
    }

    companion object {
        val methods = setOf("isAvailable", "version", "mount", "dialog", "floaty", "attach", "render", "setTheme", "command", "window", "drainEvents", "close")
        private fun rawValue(value: Any?): Any? = when (value) {
            is JSONObject -> objectMap(value)
            is JSONArray -> (0 until value.length()).map { rawValue(value.opt(it)) }
            JSONObject.NULL -> null
            else -> value
        }
        private fun objectMap(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { rawValue(value.opt(it)) }
        private fun json(value: Any?) = if (value == null) "null" else if (value is String) JSONObject.quote(value) else value.toString()
    }
}

/** Separate Activity gives each runtime session its own lifecycle and saved-state owners. */
class RuntimeComposeActivity : ComponentActivity(), ComposeInsetsHost {
    private var session: RuntimeComposeBridge.Session? = null
    private lateinit var insets: ComposeActivityInsets
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        insets = ComposeActivityInsets(window, findViewById(android.R.id.content))
        session = sessions[intent.getStringExtra("session")]
        session?.attach(this) ?: finish()
    }
    override fun acquireComposeInsets(): Closeable = insets.acquire()
    override fun onDestroy() { session?.detach(this); session = null; super.onDestroy() }
    companion object { internal val sessions = ConcurrentHashMap<String, RuntimeComposeBridge.Session>() }
}
