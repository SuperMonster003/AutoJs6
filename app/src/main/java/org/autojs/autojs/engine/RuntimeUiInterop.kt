package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import org.autojs.autojs.core.plugin.compose.ComposeActivityInsets
import org.autojs.autojs.runtime.api.modules.colors.Colors
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.plugin.compose.api.interop.AndroidViewClaimsV1
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.Closeable
import java.io.StringReader
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Execution-owned native Views. Only identifiers and JSON values cross the process boundary. */
internal class RuntimeUiInterop(private val context: Context, private val image: (JSONObject) -> android.graphics.Bitmap) : Closeable {
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean()
    private val contextId = "context-${UUID.randomUUID()}"
    private val themed by lazy { ContextThemeWrapper(context, R.style.ScriptTheme) }
    private val records = LinkedHashMap<String, NativeView>()
    private val byView = IdentityHashMap<View, NativeView>()
    private var nextLease = 1
    private val windows = ConcurrentHashMap<String, Window>()
    // Floating windows of floaty.window / floaty.rawWindow (api-coverage P3.3); they share drainEvents / close with the windows.
    // zh-CN: floaty.window / floaty.rawWindow 的悬浮窗 (api-coverage P3.3); 与窗口共用 drainEvents / close.
    private val floaties = ConcurrentHashMap<String, Floaty>()
    // The execution's single content window of ui.layout / setContentView (main thread only), replaced in place (api-coverage P3.3, Q164 (a)).
    // zh-CN: 本执行唯一的 ui.layout / setContentView 内容窗口 (仅主线程访问), 原地替换 (api-coverage P3.3, Q164 (a)).
    private var contentWindow: Window? = null
    private var nextWindowOrder = 0L
    val activeCount get() = windows.values.count { !it.closed } + floaties.values.count { !it.closed }

    internal inner class NativeView(val id: String, val lease: Int, val view: View, val root: NativeView?, val session: NodeBridgeUiSession) {
        val events = ArrayDeque<JSONObject>()
        val queue = EventQueue(events)
        val disposeListeners = LinkedHashSet<() -> Unit>()
        var window: Window? = null
        var floaty: Floaty? = null
        // A renderer can be absent during Activity recreation while its session still borrows this View.
        var borrower: Any? = null
        val group get() = root ?: this
        fun json() = JSONObject().put("__autojs6ViewHandle", id).put("id", id)
            .put("type", if (view is RuntimeComposeContainer) "Compose" else view.javaClass.simpleName)
            .put("context", contextToken())
    }

    internal class EventQueue(private val events: ArrayDeque<JSONObject> = ArrayDeque()) {
        private val main = Handler(Looper.getMainLooper())
        private var pending: Pair<NodeBridgeRequest, (NodeBridgeResponse) -> Unit>? = null
        private var timeout: Runnable? = null
        private val subscribers = LinkedHashMap<String, EventQueue>()
        private var legacyReader = false
        fun subscribe(id: String) {
            require(id.isNotEmpty())
            if (id !in subscribers) {
                subscribers[id] = EventQueue(ArrayDeque(events.map { JSONObject(it.toString()) }))
                if (!legacyReader) events.clear()
            }
        }
        fun unsubscribe(id: String) { subscribers.remove(id)?.wake() }
        fun emit(value: JSONObject) {
            subscribers.values.toList().forEach { it.emit(value) }
            if (subscribers.isNotEmpty() && !legacyReader) return
            // Notifications may be dropped under pressure, but replies and cancellation must remain deliverable.
            fun required(event: JSONObject): Boolean {
                val type = event.optString("type")
                return event.has("requestId") || type == "webTaskResult" || type == "close"
            }
            if (events.size >= 128) {
                val notification = events.indexOfFirst { !required(it) }
                if (notification >= 0) events.removeAt(notification) else if (!required(value)) return
            }
            events.add(JSONObject(value.toString())); reply()
        }
        fun read(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit, subscriber: String? = null) {
            if (subscriber != null) {
                requireNotNull(subscribers[subscriber]) { "View event subscription is closed" }.read(request, callback)
                return
            }
            legacyReader = true
            check(pending == null) { "Only one reader may drain this UI event queue" }
            pending = request to callback
            if (events.isNotEmpty()) reply() else {
                timeout = Runnable { reply() }.also { main.postDelayed(it, 1000) }
            }
        }
        fun wake() { reply(); subscribers.values.toList().forEach { it.wake() } }
        private fun reply() {
            val call = pending ?: return
            pending = null; timeout?.let(main::removeCallbacks); timeout = null
            val values = JSONArray(); repeat(minOf(events.size, 32)) { values.put(events.removeFirst()) }
            call.second(NodeBridgeResponse.success(call.first.id, values.toString()))
        }
    }

    fun ownsWindow(id: String) = windows.containsKey(id) || floaties.containsKey(id)
    fun contextToken() = JSONObject().put("__autojs6UiContext", contextId)
        .put("density", context.resources.displayMetrics.density.toDouble())
        .put("scaledDensity", context.resources.displayMetrics.scaledDensity.toDouble())
        .put("packageName", context.packageName)
        .put("runtimeVersionCode", org.autojs.autojs6.BuildConfig.VERSION_CODE)
        .put("applicationVersionCode", androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)))

    fun resolve(value: JSONObject): NativeView {
        check(Looper.myLooper() == Looper.getMainLooper())
        val id = value.optString("__autojs6ViewHandle")
        return records[id] ?: error("View handle is closed or belongs to another execution")
    }
    fun container(value: JSONObject): NativeView = resolve(value).also {
        require(it.view is RuntimeComposeContainer) { "compose.attach requires an execution-owned XML compose container" }
    }
    fun activity(view: NativeView): ComponentActivity? = view.group.window?.activity ?: view.view.findViewTreeLifecycleOwner() as? ComponentActivity
    fun recreating(view: NativeView) = view.group.window?.recreating == true
    fun onDispose(view: NativeView, listener: () -> Unit): Closeable {
        view.group.disposeListeners.add(listener)
        return Closeable { view.group.disposeListeners.remove(listener) }
    }

    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        main.post {
            try {
                check(!closed.get()) { "UI execution has finished" }
                val args = JSONArray(request.argsJson)
                val value: Any? = when (request.method) {
                    "context" -> contextToken()
                    "inflate" -> {
                        args.optJSONObject(1)?.let { require(it.optString("__autojs6UiContext") == contextId) { "UI context belongs to another execution" } }
                        inflate(args.get(0)).json()
                    }
                    "showLayout" -> {
                        val options = args.opt(1).let { if (it == null || it == JSONObject.NULL) JSONObject() else it as? JSONObject ?: error("showLayout options must be an object") }
                        options.keys().forEach { key -> require(key == "contentView") { "Unknown showLayout option '$key'; expected contentView" } }
                        val asContent = options.opt("contentView").let { if (it == null || it == JSONObject.NULL) false else it as? Boolean ?: error("showLayout contentView must be a boolean") }
                        val input = args.get(0)
                        val borrowed = input is JSONObject && input.has("__autojs6ViewHandle")
                        val root = if (borrowed) resolve(input as JSONObject) else inflate(input)
                        try { claimRoot(root) } catch (failure: Throwable) { if (!borrowed) dispose(root, true); throw failure }
                        val current = contentWindow?.takeUnless { it.closed }
                        if (asContent && current != null) {
                            // The content window keeps its id and Activity; only its root changes (api-coverage P3.3).
                            // zh-CN: 内容窗口保留其 id 与 Activity, 只更换根视图 (api-coverage P3.3).
                            current.replaceRoot(root, !borrowed)
                            JSONObject().put("id", current.id).put("rootView", root.json()).put("replaced", true)
                        } else {
                            val window = Window("ui-interop-${UUID.randomUUID()}", root, !borrowed)
                            windows[window.id] = window; root.window = window; RuntimeInteropActivity.windows[window.id] = window
                            try {
                                context.startActivity(Intent(context, RuntimeInteropActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK).putExtra("window", window.id))
                            } catch (failure: Throwable) { window.close(); throw failure }
                            if (asContent) contentWindow = window
                            JSONObject().put("id", window.id).put("rootView", root.json()).also { if (asContent) it.put("replaced", false) }
                        }
                    }
                    "windowControl" -> {
                        require(args.length() in 2..3) { "windowControl expects [windowId, op, value]" }
                        val window = windows[args.getString(0)]?.takeUnless { it.closed } ?: error("Window is closed or belongs to another execution")
                        window.control(args.getString(1), args.opt(2)); JSONObject.NULL
                    }
                    "barHeight" -> {
                        val (kind, bar) = RuntimeUiWindowControls.barRequest(args)
                        val target = barContext()
                        if (kind == "status") ViewUtils.getStatusBarHeight(target, bar.withComputed, bar.withDimen, bar.ignoreVisibility)
                        else ViewUtils.getNavigationBarHeight(target, bar.withComputed, bar.withDimen, bar.ignoreVisibility)
                    }
                    "floatyWindow", "floatyRawWindow" -> {
                        require(args.length() in 1..2 && (args.isNull(1) || args.opt(1) is JSONObject)) { "${request.method} expects [layout, options?]" }
                        if (!Settings.canDrawOverlays(context)) throw SecurityException("${request.method} requires the host's draw-over-other-apps permission")
                        val input = args.get(0)
                        val borrowed = input is JSONObject && input.has("__autojs6ViewHandle")
                        val root = if (borrowed) resolve(input as JSONObject) else inflate(input)
                        try { claimRoot(root) } catch (failure: Throwable) { if (!borrowed) dispose(root, true); throw failure }
                        val floaty = Floaty("floaty-${UUID.randomUUID()}", root, !borrowed, request.method == "floatyRawWindow")
                        floaties[floaty.id] = floaty; root.floaty = floaty
                        // The reply waits for the window to be created, as Rhino's floaty.window waits for its creation.
                        // zh-CN: 应答等到窗口创建完成, 与 Rhino 的 floaty.window 等待创建一致.
                        floaty.start { failure ->
                            callback(if (failure == null) NodeBridgeResponse.success(request.id, JSONObject().put("id", floaty.id).put("rootView", root.json()).toString())
                            else NodeBridgeResponse.failure(request.id, NodeBridgeError.providerFailed(failure.message ?: "Floating window could not be created", "ui", request.method)))
                        }
                        return@post
                    }
                    "floatyControl" -> {
                        val op = RuntimeUiWindowControls.checkFloatyControl(args)
                        val floaty = floaties[args.getString(0)]?.takeUnless { it.closed } ?: error("Floating window is closed or belongs to another execution")
                        floaty.control(op, args)
                    }
                    "floatyCloseAll" -> {
                        require(args.length() == 0) { "floatyCloseAll takes no arguments" }
                        floaties.values.filter { !it.closed }.onEach { it.close() }.size
                    }
                    "findView" -> {
                        val source = args.get(0)
                        val root = if (source is String) floaties[source]?.root ?: requireNotNull(windows[source]) { "Window is not owned by this execution" }.root else resolve(source as JSONObject)
                        val found = RuntimeViewComponents.find(root.view, args.getString(1))
                        if (found == null) JSONObject.NULL else record(found, root.group).json()
                    }
                    "viewGet" -> {
                        val view = resolve(args.getJSONObject(0))
                        val snapshot = snapshot(view.view)
                        if (args.length() > 1) { val key = args.getString(1); require(snapshot.has(key)) { "Unknown View attribute: $key" }; snapshot.get(key) } else snapshot
                    }
                    "viewUpdate" -> { val view = resolve(args.getJSONObject(0)); update(view, args.getJSONObject(1)); snapshot(view.view) }
                    "viewCommand" -> {
                        val view = resolve(args.getJSONObject(0))
                        if (args.getString(1) in listOf("subscribeViewEvents", "releaseViewEvents")) {
                            val subscriber = args.getJSONArray(2).getString(0)
                            if (args.getString(1) == "subscribeViewEvents") view.queue.subscribe(subscriber) else view.queue.unsubscribe(subscriber)
                            callback(NodeBridgeResponse.success(request.id, "true")); return@post
                        }
                        RuntimeViewComponents.command(view.view, args.getString(1), args.getJSONArray(2)) { result ->
                            if (args.getString(1) == "clearWebClient" && result.getOrNull() == true) view.queue.wake()
                            result.fold({ value -> callback(NodeBridgeResponse.success(request.id, if (value is String) JSONObject.quote(value) else (value ?: JSONObject.NULL).toString())) },
                                { failure -> callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.invalidRequest(failure.message ?: "Native View command failed", "ui", request.method))) })
                        }
                        return@post
                    }
                    "viewEvents" -> { resolve(args.getJSONObject(0)).queue.read(request, callback, if (args.isNull(1)) null else args.getString(1)); return@post }
                    "disposeView" -> { dispose(resolve(args.getJSONObject(0))); JSONObject.NULL }
                    else -> {
                        val floaty = floaties[args.getString(0)]
                        if (floaty != null) when (request.method) {
                            "update" -> { floaty.update(args.getJSONObject(1)); JSONObject.NULL }
                            "batchUpdate" -> { val patches=args.getJSONArray(1);require(patches.length()<=64);repeat(patches.length()){floaty.update(patches.getJSONObject(it))};JSONObject.NULL }
                            "drainEvents" -> { floaty.queue.read(request, callback); return@post }
                            "close" -> { floaty.close(); JSONObject.NULL }
                            else -> error("Unsupported floating window operation")
                        } else {
                        val window = windows[args.getString(0)] ?: error("Window is closed or belongs to another execution")
                        when (request.method) {
                            "update" -> { window.update(args.getJSONObject(1)); JSONObject.NULL }
                            "batchUpdate" -> { val patches=args.getJSONArray(1);require(patches.length()<=64);repeat(patches.length()){window.update(patches.getJSONObject(it))};JSONObject.NULL }
                            "drainEvents" -> { window.queue.read(request, callback); return@post }
                            "close" -> { window.close(); JSONObject.NULL }
                            else -> error("Unsupported UI interop operation")
                        }
                        }
                    }
                }
                callback(NodeBridgeResponse.success(request.id, if(value is String)JSONObject.quote(value)else(value?:JSONObject.NULL).toString()))
            } catch (failure: SecurityException) {
                callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.permissionDenied(failure.message ?: "UI permission denied", "ui", request.method)))
            } catch (failure: Throwable) {
                callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.invalidRequest(failure.message ?: "Invalid UI interop request", "ui", request.method)))
            }
        }
    }

    private fun inflate(input: Any): NativeView {
        val descriptor = when (input) { is String -> xml(input); is JSONObject -> input; else -> error("inflate requires XML or a layout descriptor") }
        val pending=ArrayDeque<Pair<JSONObject,Int>>();pending.add(descriptor to 1);var count=0
        val ids = HashSet<String>()
        while(pending.isNotEmpty()){
            val (node,depth)=pending.removeFirst();require(depth<=16&&++count<=5000){"Layout exceeds node or depth limits"}
            val id = node.optString("id")
            require(id.isBlank() || ids.add(id)) { "Duplicate native View id: $id" }
            node.optJSONArray("children")?.let{children->require(children.length()<=128);repeat(children.length()){pending.add(children.getJSONObject(it) to depth+1)}}
            node.optJSONArray("tabs")?.let { tabs -> require(tabs.length() <= 128); repeat(tabs.length()) { pending.add(tabs.getJSONObject(it) to depth + 1) } }
            node.optJSONObject("content")?.let { pending.add(it to depth + 1) }
        }
        NodeBridgeUiRenderer.validateDescriptor(descriptor)?.let { error(it) }
        val session = NodeBridgeUiSession("view-events-${UUID.randomUUID()}", descriptor)
        // Install the observer after construction; widget initialization is not a user event.
        val view = NodeBridgeUiRenderer(themed, session, image).createView(descriptor, true)
        val root = record(view, null, session)
        session.observeInteropEvents { event ->
            event.remove("snapshot")
            val id = event.optString("id")
            records.values.filter { it.group === root && (it === root || it.view.tag == id) }.forEach { it.queue.emit(event) }
            root.window?.queue?.emit(event)
            root.floaty?.queue?.emit(event)
        }
        return root
    }

    /** A root shown by a window or a floating window: not parented, borrowed or claimed by Compose, and not shown elsewhere. zh-CN: 由窗口或悬浮窗显示的根视图: 未被挂载, 借用或被 Compose 认领, 且未在别处显示. */
    private fun claimRoot(root: NativeView) {
        require(root.root == null && root.view.parent == null && root.borrower == null && !AndroidViewClaimsV1.isClaimed(root.view)) { "View is already parented or borrowed" }
        check(root.window == null && root.floaty == null) { "View already owns a window" }
    }

    /**
     * The context of `barHeight`: the Activity of the open content window, else of the most recently opened window that is
     * still open, else the application context (Rhino's `barContext` order: activity, then GlobalAppContext). api-coverage P3.3.
     * zh-CN: `barHeight` 的上下文: 打开中的内容窗口的 Activity, 否则为最近打开且仍未关闭的窗口的 Activity, 再否则为应用上下文 (同 Rhino `barContext` 的
     * 顺序: activity, 然后 GlobalAppContext). api-coverage P3.3.
     */
    private fun barContext(): Context = contentWindow?.takeUnless { it.closed }?.activity
        ?: windows.values.filter { !it.closed && it.activity != null }.maxByOrNull { it.order }?.activity
        ?: context

    private fun record(view: View, root: NativeView?, session: NodeBridgeUiSession? = null): NativeView = byView[view] ?: run {
        check(nextLease > 0) { "View identity space exhausted" }
        NativeView("view-${UUID.randomUUID()}", nextLease++, view, root, session ?: requireNotNull(root).session).also {
            records[it.id]=it;byView[view]=it
            if (view is RuntimeWebView) view.callbacks.resolveView = { token ->
                (resolve(token).view as? RuntimeWebView)?.also { target -> check(!target.disposed) } ?: error("A popup requires a WebView handle")
            }
        }
    }
    private fun update(record: NativeView, patch: JSONObject) {
        RuntimeViewAttributes.apply(record.view, patch, strict = true, image = image)
    }
    private fun snapshot(view: View) = RuntimeViewAttributes.snapshot(view)
    private fun dispose(record: NativeView, force: Boolean = false) {
        require(record.root == null) { "Dispose the inflated root, not a child alias" }
        if(!force)require(records.values.none { it.group === record && (it.borrower != null || AndroidViewClaimsV1.isClaimed(it.view)) }) { "Close the borrowing Compose session before disposing its View" }
        record.disposeListeners.toList().forEach { it() }
        record.disposeListeners.clear()
        record.window?.close(disposeRoot=false)
        record.floaty?.close(disposeRoot=false)
        (record.view.parent as? ViewGroup)?.removeView(record.view)
        RuntimeViewComponents.dispose(record.view)
        record.session.close("disposed")
        records.values.filter { it.group===record }.toList().forEach {
            it.queue.emit(JSONObject().put("type","close"));records.remove(it.id);byView.remove(it.view)
        }
    }
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        // Floating windows of the execution close with it, as Rhino's floaty.closeAll on script exit (api-coverage P3.3).
        // zh-CN: 本执行的悬浮窗随之关闭, 同 Rhino 脚本退出时的 floaty.closeAll (api-coverage P3.3).
        main.post { floaties.values.toList().forEach { it.close() };windows.values.toList().forEach { it.close() };records.values.filter{it.root==null}.toList().forEach{dispose(it,true)};records.clear();byView.clear() }
    }

    /**
     * An Activity window showing an execution-owned root. Since api-coverage P3.3 it also pushes lifecycle events
     * (`{type: "lifecycle", name, handleId}`), remembers the `windowControl` options and applies them on every attach,
     * intercepts the back key while `interceptBack` is on, and can swap its root in place when it is the content window.
     * zh-CN: 显示执行拥有的根视图的 Activity 窗口. 自 api-coverage P3.3 起还推送生命周期事件 (`{type: "lifecycle", name, handleId}`),
     * 记住 `windowControl` 选项并在每次 attach 时应用, `interceptBack` 开启时拦截返回键, 作为内容窗口时可原地更换根视图.
     */
    internal inner class Window(val id:String,root:NativeView,private var ownsRoot:Boolean) {
        var root=root;private set
        @Volatile var closed=false;private set
        var recreating=false;private set
        var activity:RuntimeInteropActivity?=null;private set
        val queue=EventQueue()
        val order=nextWindowOrder++
        private var attachedOnce=false
        // windowControl options by option key, applied in the order they were last set. zh-CN: 按选项键记录的 windowControl 选项, 按最后设置的顺序应用.
        private val controls=LinkedHashMap<String,Any>()
        private var backCallback:OnBackPressedCallback?=null
        fun attach(owner:RuntimeInteropActivity) {
            if(closed){owner.finish();return}
            activity=owner;recreating=false;attachedOnce=true
            install(owner)
            lifecycle(owner,"create")
            queue.emit(JSONObject().put("type","shown").put("handleId",id))
        }
        private fun install(owner:RuntimeInteropActivity) {
            (root.view.parent as? ViewGroup)?.removeView(root.view)
            root.view.setViewTreeLifecycleOwner(owner);root.view.setViewTreeSavedStateRegistryOwner(owner);root.view.setViewTreeOnBackPressedDispatcherOwner(owner)
            owner.setContentView(root.view)
            controls.forEach { (key,value) -> applyControl(owner,key,value) }
        }
        private fun uninstall(view:View) {
            (view.parent as? ViewGroup)?.removeView(view)
            view.setViewTreeLifecycleOwner(null);view.setViewTreeSavedStateRegistryOwner(null);view.setTag(androidx.activity.R.id.view_tree_on_back_pressed_dispatcher_owner, null)
        }
        fun detach(owner:RuntimeInteropActivity) {
            if(activity!==owner)return
            recreating=owner.isChangingConfigurations
            uninstall(root.view)
            backCallback?.remove();backCallback=null
            activity=null
            if(!recreating)close()
        }
        /** A lifecycle event of [owner], ignored once the window closed or another Activity took it over. zh-CN: [owner] 的生命周期事件; 窗口已关闭或已被其他 Activity 接管时忽略. */
        fun lifecycle(owner:RuntimeInteropActivity,name:String) {
            if(closed||activity!==owner)return
            queue.emit(JSONObject().put("type","lifecycle").put("name",name).put("handleId",id))
        }
        /**
         * `windowControl [windowId, op, value]` (api-coverage P3.3): the value is checked and converted now (colors as Rhino's
         * `ui.statusBarColor` and friends do), remembered, and applied to the current Activity, if any.
         * zh-CN: `windowControl [windowId, op, value]` (api-coverage P3.3): 立即检查并转换值 (颜色按 Rhino `ui.statusBarColor` 等的方式), 记住它, 并应用到当前
         * Activity (若有).
         */
        fun control(op:String,raw:Any?) {
            check(!closed)
            val checked=RuntimeUiWindowControls.checkControl(op,raw)
            if(op.startsWith("navigationBarIconLight"))check(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){"$op requires Android 8.0 (API 26) or later"}
            val value:Any=when(op){
                "statusBarColor","navigationBarColor"->Colors.toIntRhino(checked)
                "backgroundColor"->Colors.setAlphaRhino(checked,1.0)
                "statusBarIconLightBy","navigationBarIconLightBy"->ViewUtils.isLuminanceDark(Colors.toIntRhino(checked))
                else->checked
            }
            val key=RuntimeUiWindowControls.optionKey(op)
            controls.remove(key);controls[key]=value
            activity?.let { applyControl(it,key,value) }
        }
        private fun applyControl(owner:RuntimeInteropActivity,key:String,value:Any) {
            when(key){
                "interceptBack"->if(value as Boolean){
                    if(backCallback==null)backCallback=object:OnBackPressedCallback(true){
                        override fun handleOnBackPressed(){lifecycle(owner,"back_pressed")}
                    }.also { owner.onBackPressedDispatcher.addCallback(owner,it) }
                }else{backCallback?.remove();backCallback=null}
                "statusBarColor"->ViewUtils.setStatusBarBackgroundColor(owner,value as Int)
                "navigationBarColor"->ViewUtils.setNavigationBarBackgroundColor(owner,value as Int)
                "backgroundColor"->owner.window.setBackgroundDrawable(ColorDrawable(value as Int))
                "statusBarIconLight"->ViewUtils.setStatusBarIconLight(owner,value as Boolean)
                "navigationBarIconLight"->ViewUtils.setNavigationBarIconLight(owner,value as Boolean)
                "keepScreenOn"->if(value as Boolean)owner.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else owner.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        /**
         * Replaces the root of the content window inside the same Activity (api-coverage P3.3): the old root leaves the
         * Activity and is disposed when the window owned it (a borrowed handle stays alive), then the remembered options are
         * applied again.
         * zh-CN: 在同一 Activity 内更换内容窗口的根视图 (api-coverage P3.3): 旧根视图离开 Activity, 窗口拥有它时将其释放 (借用的句柄保持存活), 然后重新应用
         * 记住的选项.
         */
        fun replaceRoot(next:NativeView,owns:Boolean) {
            check(!closed)
            val previous=root;val ownedPrevious=ownsRoot
            RuntimeViewComponents.cancelWebUi(previous.view)
            uninstall(previous.view);previous.window=null
            root=next;ownsRoot=owns;next.window=this
            activity?.let(::install)
            if(ownedPrevious)dispose(previous,true)
        }
        fun update(patch:JSONObject) {
            check(!closed)
            val name=patch.optString("id")
            val target=if(name.isBlank())root else record(requireNotNull(RuntimeViewComponents.find(root.view,name)){"Unknown View id: $name"},root)
            update(target,JSONObject(patch.toString()).apply { remove("id") })
        }
        fun close(disposeRoot:Boolean=true) {
            if(closed)return
            closed=true;recreating=false
            if(contentWindow===this)contentWindow=null
            RuntimeViewComponents.cancelWebUi(root.view)
            uninstall(root.view)
            backCallback?.remove();backCallback=null
            activity?.finish();activity=null;root.window=null
            RuntimeInteropActivity.windows.remove(id)
            // A window that was shown reports destroy before close, whoever closed it (api-coverage P3.3).
            // zh-CN: 显示过的窗口无论由谁关闭, 都先报告 destroy 再报告 close (api-coverage P3.3).
            if(attachedOnce)queue.emit(JSONObject().put("type","lifecycle").put("name","destroy").put("handleId",id))
            queue.emit(JSONObject().put("type","close").put("handleId",id))
            // Keep a short terminal-event tombstone for the client's in-flight drain.
            main.postDelayed({windows.remove(id)},2000)
            if(ownsRoot&&disposeRoot)dispose(root,true)
        }
    }

    /**
     * A floating window of `floatyWindow` / `floatyRawWindow` (api-coverage P3.3) showing an execution-owned root through
     * [RuntimeFloatyWindow]. Its queue carries the View events of the root plus `{type: "shown", handleId}` once created
     * and `{type: "close", handleId}` when the user closes it, the script closes it or the execution ends.
     * zh-CN: `floatyWindow` / `floatyRawWindow` 的悬浮窗 (api-coverage P3.3), 经 [RuntimeFloatyWindow] 显示执行拥有的根视图. 其队列承载根视图的
     * View 事件, 创建后的 `{type: "shown", handleId}`, 以及用户关闭, 脚本关闭或执行结束时的 `{type: "close", handleId}`.
     */
    internal inner class Floaty(val id:String,val root:NativeView,private val ownsRoot:Boolean,raw:Boolean) {
        @Volatile var closed=false;private set
        val queue=EventQueue()
        private var disposeOnClose=true
        private var pending:((Throwable?)->Unit)?=null
        private val startupTimeout=Runnable { pending?.let { reply -> pending=null;reply(IllegalStateException("Floating window service did not become ready"));close() } }
        private val window=RuntimeFloatyWindow(themed,raw,root.view,
            { failure -> main.removeCallbacks(startupTimeout)
                if(failure==null)queue.emit(JSONObject().put("type","shown").put("handleId",id))
                pending?.let { reply -> pending=null;reply(failure) } },
            ::finished)
        fun start(reply:(Throwable?)->Unit) {
            pending=reply
            main.postDelayed(startupTimeout,10_000)
            window.start()
        }
        fun update(patch:JSONObject) {
            check(!closed)
            val name=patch.optString("id")
            val target=if(name.isBlank())root else record(requireNotNull(RuntimeViewComponents.find(root.view,name)){"Unknown View id: $name"},root)
            update(target,JSONObject(patch.toString()).apply { remove("id") })
        }
        fun control(op:String,args:JSONArray):Any {
            fun int(index:Int):Int=(args.get(index) as? Number)?.toDouble()?.takeIf(Double::isFinite)?.toInt() ?: throw IllegalArgumentException("floatyControl '$op' requires finite numbers")
            fun bool(index:Int):Boolean=args.get(index) as? Boolean ?: throw IllegalArgumentException("floatyControl '$op' requires a boolean")
            when(op){
                "setPosition"->window.setPosition(int(2),int(3))
                "setSize"->window.setSize(int(2),int(3))
                "setTouchable"->window.setTouchable(bool(2))
                "requestFocus"->window.requestFocus()
                "disableFocus"->window.disableFocus()
                "setAdjustEnabled"->window.setAdjustEnabled(bool(2))
                "isAdjustEnabled"->return window.isAdjustEnabled()
                "geometry"->return window.currentGeometry.let { JSONObject().put("x",it.x).put("y",it.y).put("width",it.width).put("height",it.height) }
            }
            return JSONObject.NULL
        }
        fun close(disposeRoot:Boolean=true) {
            if(closed)return
            disposeOnClose=disposeRoot
            window.close()
        }
        private fun finished() {
            if(closed)return
            closed=true
            main.removeCallbacks(startupTimeout)
            pending?.let { reply -> pending=null;reply(IllegalStateException("Floating window was closed before it was shown")) }
            root.floaty=null
            queue.emit(JSONObject().put("type","close").put("handleId",id))
            // Keep a short terminal-event tombstone for the client's in-flight drain.
            main.postDelayed({floaties.remove(id)},2000)
            if(ownsRoot&&disposeOnClose)dispose(root,true)
        }
    }

    companion object {
        val methods=setOf("context","inflate","findView","viewGet","viewUpdate","viewCommand","viewEvents","disposeView")
        fun isLayout(value:Any?)=value is String || value is JSONObject&&value.has("__autojs6ViewHandle")
        private val tags=mapOf("vertical" to "Column","horizontal" to "Row","frame" to "Frame","compose" to "Compose",
            "text" to "Text","button" to "Button","input" to "Input","image" to "Image","scroll" to "ScrollView",
            "checkbox" to "Checkbox","switch" to "Switch","progressbar" to "Progress","TextView" to "Text","EditText" to "Input",
            "LinearLayout" to "Column","FrameLayout" to "Frame")
        internal fun xml(source:String):JSONObject {
            require(source.toByteArray().size<=65536 && !Regex("<!\\s*(DOCTYPE|ENTITY)",RegexOption.IGNORE_CASE).containsMatchIn(source)) { "XML is too large or contains a document/entity declaration" }
            val parser=android.util.Xml.newPullParser();parser.setInput(StringReader(source))
            val stack=ArrayDeque<JSONObject>();var root:JSONObject?=null;var count=0
            while(parser.eventType!=XmlPullParser.END_DOCUMENT){
                when(parser.eventType){
                    XmlPullParser.START_TAG->{
                        require(stack.size<16&&++count<=5000)
                        val tag=parser.name.substringAfterLast('.')
                        val node=JSONObject().put("type",tags[tag]?:tag)
                        for(i in 0 until parser.attributeCount){
                            val key=when(val name=parser.getAttributeName(i).substringAfter(':')){"layout_width","w"->"width";"layout_height","h"->"height";"layout_weight"->"weight";"textSize"->"textSize";else->name}
                            val value=parser.getAttributeValue(i)
                            node.put(key,if(key=="id")value.removePrefix("@+id/").removePrefix("@id/")else when(value){"true"->true;"false"->false;else->value})
                        }
                        if(stack.isEmpty()){require(root==null);root=node}else{val parent=stack.last();val children=parent.optJSONArray("children")?:JSONArray().also{parent.put("children",it)};require(children.length()<128);children.put(node)}
                        stack.add(node)
                    }
                    XmlPullParser.END_TAG->stack.removeLast()
                    XmlPullParser.TEXT->if(!parser.text.isNullOrBlank()){require(stack.isNotEmpty());val node=stack.last();require(node.optString("type")=="Text"&&!node.has("text"));node.put("text",parser.text)}
                }
                parser.next()
            }
            return requireNotNull(root){"XML layout has no root"}
        }

    }
}

internal class RuntimeComposeContainer(context:Context):FrameLayout(context)

class RuntimeInteropActivity:ComponentActivity(){
    private var entry:RuntimeUiInterop.Window?=null
    private var insets:Closeable?=null
    override fun onCreate(state:Bundle?){super.onCreate(state);androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        insets=ComposeActivityInsets(window,findViewById(android.R.id.content)).acquire()
        entry=windows[intent.getStringExtra("window")];entry?.attach(this)?:finish()}
    // Lifecycle events for the runtime's ui.emitter (api-coverage P3.3); create and destroy come from attach and close.
    // zh-CN: 供运行时 ui.emitter 使用的生命周期事件 (api-coverage P3.3); create 与 destroy 由 attach 与 close 发出.
    override fun onResume(){super.onResume();entry?.lifecycle(this,"resume")}
    override fun onPause(){entry?.lifecycle(this,"pause");super.onPause()}
    override fun onStop(){entry?.lifecycle(this,"stop");super.onStop()}
    override fun onDestroy(){entry?.detach(this);entry=null;insets?.close();insets=null;super.onDestroy()}
    companion object{internal val windows=ConcurrentHashMap<String,RuntimeUiInterop.Window>()}
}
