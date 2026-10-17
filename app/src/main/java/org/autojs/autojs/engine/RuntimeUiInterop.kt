package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import org.autojs.autojs.core.plugin.compose.ComposeActivityInsets
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
internal class RuntimeUiInterop(private val context: Context) : Closeable {
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean()
    private val contextId = "context-${UUID.randomUUID()}"
    private val themed by lazy { ContextThemeWrapper(context, R.style.ScriptTheme) }
    private val records = LinkedHashMap<String, NativeView>()
    private val byView = IdentityHashMap<View, NativeView>()
    private var nextLease = 1
    private val windows = ConcurrentHashMap<String, Window>()
    val activeCount get() = windows.values.count { !it.closed }

    internal inner class NativeView(val id: String, val lease: Int, val view: View, val root: NativeView?, val session: NodeBridgeUiSession) {
        val events = ArrayDeque<JSONObject>()
        val queue = EventQueue(events)
        val disposeListeners = LinkedHashSet<() -> Unit>()
        var window: Window? = null
        val group get() = root ?: this
        fun json() = JSONObject().put("__autojs6ViewHandle", id).put("id", id)
            .put("type", if (view is RuntimeComposeContainer) "Compose" else view.javaClass.simpleName)
            .put("context", contextToken())
    }

    internal class EventQueue(private val events: ArrayDeque<JSONObject> = ArrayDeque()) {
        private val main = Handler(Looper.getMainLooper())
        private var pending: Pair<NodeBridgeRequest, (NodeBridgeResponse) -> Unit>? = null
        private var timeout: Runnable? = null
        fun emit(value: JSONObject) {
            if (events.size == 128) events.removeFirst()
            events.add(JSONObject(value.toString())); reply()
        }
        fun read(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
            check(pending == null) { "Only one reader may drain this UI event queue" }
            pending = request to callback
            if (events.isNotEmpty()) reply() else {
                timeout = Runnable { reply() }.also { main.postDelayed(it, 1000) }
            }
        }
        private fun reply() {
            val call = pending ?: return
            pending = null; timeout?.let(main::removeCallbacks); timeout = null
            val values = JSONArray(); repeat(minOf(events.size, 32)) { values.put(events.removeFirst()) }
            call.second(NodeBridgeResponse.success(call.first.id, values.toString()))
        }
    }

    fun ownsWindow(id: String) = windows.containsKey(id)
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
                        val input = args.get(0)
                        val borrowed = input is JSONObject && input.has("__autojs6ViewHandle")
                        val root = if (borrowed) resolve(input as JSONObject) else inflate(input)
                        require(root.root == null && root.view.parent == null && !AndroidViewClaimsV1.isClaimed(root.view)) { "View is already parented or borrowed" }
                        check(root.window == null) { "View already owns a window" }
                        val window = Window("ui-interop-${UUID.randomUUID()}", root, !borrowed)
                        windows[window.id] = window; root.window = window; RuntimeInteropActivity.windows[window.id] = window
                        try {
                            context.startActivity(Intent(context, RuntimeInteropActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK).putExtra("window", window.id))
                        } catch (failure: Throwable) { window.close(); throw failure }
                        JSONObject().put("id", window.id).put("rootView", root.json())
                    }
                    "findView" -> {
                        val source = args.get(0)
                        val root = if (source is String) requireNotNull(windows[source]) { "Window is not owned by this execution" }.root else resolve(source as JSONObject)
                        val found = root.view.findViewWithTag<View>(args.getString(1))
                        if (found == null) JSONObject.NULL else record(found, root.group).json()
                    }
                    "viewGet" -> {
                        val view = resolve(args.getJSONObject(0))
                        val snapshot = snapshot(view.view)
                        if (args.length() > 1) { val key = args.getString(1); require(snapshot.has(key)) { "Unknown View attribute: $key" }; snapshot.get(key) } else snapshot
                    }
                    "viewUpdate" -> { val view = resolve(args.getJSONObject(0)); update(view, args.getJSONObject(1)); snapshot(view.view) }
                    "viewEvents" -> { resolve(args.getJSONObject(0)).queue.read(request, callback); return@post }
                    "disposeView" -> { dispose(resolve(args.getJSONObject(0))); JSONObject.NULL }
                    else -> {
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
                callback(NodeBridgeResponse.success(request.id, if(value is String)JSONObject.quote(value)else(value?:JSONObject.NULL).toString()))
            } catch (failure: Throwable) {
                callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.invalidRequest(failure.message ?: "Invalid UI interop request", "ui", request.method)))
            }
        }
    }

    private fun inflate(input: Any): NativeView {
        val descriptor = when (input) { is String -> xml(input); is JSONObject -> input; else -> error("inflate requires XML or a layout descriptor") }
        val pending=ArrayDeque<Pair<JSONObject,Int>>();pending.add(descriptor to 1);var count=0
        while(pending.isNotEmpty()){
            val (node,depth)=pending.removeFirst();require(depth<=16&&++count<=5000){"Layout exceeds node or depth limits"}
            node.optJSONArray("children")?.let{children->require(children.length()<=128);repeat(children.length()){pending.add(children.getJSONObject(it) to depth+1)}}
        }
        NodeBridgeUiRenderer.validateDescriptor(descriptor)?.let { error(it) }
        val session = NodeBridgeUiSession("view-events-${UUID.randomUUID()}", descriptor)
        // Install the observer after construction; widget initialization is not a user event.
        val view = NodeBridgeUiRenderer(themed, session).createView(descriptor, true)
        val root = record(view, null, session)
        session.observeInteropEvents { event ->
            event.remove("snapshot")
            val id = event.optString("id")
            records.values.filter { it.group === root && (it === root || it.view.tag == id) }.forEach { it.queue.emit(event) }
            root.window?.queue?.emit(event)
        }
        return root
    }

    private fun record(view: View, root: NativeView?, session: NodeBridgeUiSession? = null): NativeView = byView[view] ?: run {
        check(nextLease > 0) { "View identity space exhausted" }
        NativeView("view-${UUID.randomUUID()}", nextLease++, view, root, session ?: requireNotNull(root).session).also { records[it.id]=it;byView[view]=it }
    }
    private fun update(record: NativeView, patch: JSONObject) {
        require(!patch.has("type") && !patch.has("children")) { "Native View updates cannot replace identity or children" }
        NodeBridgeUiRenderer(record.view.context, record.session).applyPatch(record.view, patch)
        applyAttributes(record.view, patch)
    }
    private fun snapshot(view: View) = JSONObject().put("id",view.tag).put("type",view.javaClass.simpleName)
        .put("width",view.width).put("height",view.height).put("enabled",view.isEnabled)
        .put("visible",view.visibility==View.VISIBLE).put("attached",view.isAttachedToWindow)
        .put("contentDescription",view.contentDescription?.toString() ?: JSONObject.NULL).apply {
            if(view is TextView)put("text",view.text.toString())
            if(view is CompoundButton)put("checked",view.isChecked)
        }
    private fun dispose(record: NativeView, force: Boolean = false) {
        require(record.root == null) { "Dispose the inflated root, not a child alias" }
        if(!force)require(!AndroidViewClaimsV1.isClaimed(record.view)) { "Close the borrowing Compose session before disposing its View" }
        record.disposeListeners.toList().forEach { it() }
        record.disposeListeners.clear()
        record.window?.close(disposeRoot=false)
        (record.view.parent as? ViewGroup)?.removeView(record.view)
        record.session.close("disposed")
        records.values.filter { it.group===record }.toList().forEach {
            it.queue.emit(JSONObject().put("type","close"));records.remove(it.id);byView.remove(it.view)
        }
    }
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        main.post { windows.values.toList().forEach { it.close() };records.values.filter{it.root==null}.toList().forEach{dispose(it,true)};records.clear();byView.clear() }
    }

    internal inner class Window(val id:String,val root:NativeView,private val ownsRoot:Boolean) {
        @Volatile var closed=false;private set
        var recreating=false;private set
        var activity:RuntimeInteropActivity?=null;private set
        val queue=EventQueue()
        fun attach(owner:RuntimeInteropActivity) {
            if(closed){owner.finish();return}
            activity=owner;recreating=false
            (root.view.parent as? ViewGroup)?.removeView(root.view)
            root.view.setViewTreeLifecycleOwner(owner);root.view.setViewTreeSavedStateRegistryOwner(owner);root.view.setViewTreeOnBackPressedDispatcherOwner(owner)
            owner.setContentView(root.view)
            queue.emit(JSONObject().put("type","shown").put("handleId",id))
        }
        fun detach(owner:RuntimeInteropActivity) {
            if(activity!==owner)return
            recreating=owner.isChangingConfigurations
            (root.view.parent as? ViewGroup)?.removeView(root.view)
            root.view.setViewTreeLifecycleOwner(null);root.view.setViewTreeSavedStateRegistryOwner(null);root.view.setTag(androidx.activity.R.id.view_tree_on_back_pressed_dispatcher_owner, null)
            activity=null
            if(!recreating)close()
        }
        fun update(patch:JSONObject) {
            check(!closed)
            val name=patch.optString("id")
            val target=if(name.isBlank())root else record(requireNotNull(root.view.findViewWithTag<View>(name)){"Unknown View id: $name"},root)
            update(target,patch)
        }
        fun close(disposeRoot:Boolean=true) {
            if(closed)return
            closed=true;recreating=false
            (root.view.parent as? ViewGroup)?.removeView(root.view)
            root.view.setViewTreeLifecycleOwner(null);root.view.setViewTreeSavedStateRegistryOwner(null);root.view.setTag(androidx.activity.R.id.view_tree_on_back_pressed_dispatcher_owner, null)
            activity?.finish();activity=null;root.window=null
            RuntimeInteropActivity.windows.remove(id)
            queue.emit(JSONObject().put("type","close").put("handleId",id))
            // Keep a short terminal-event tombstone for the client's in-flight drain.
            main.postDelayed({windows.remove(id)},2000)
            if(ownsRoot&&disposeRoot)dispose(root,true)
        }
    }

    companion object {
        val methods=setOf("context","inflate","findView","viewGet","viewUpdate","viewEvents","disposeView")
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
        internal fun applyAttributes(view:View,props:JSONObject) {
            val density=view.resources.displayMetrics.density
            fun dimension(raw:Any):Int=when(raw.toString()){"*","match_parent","fill_parent"->-1;"wrap_content","auto"->-2;else->{val text=raw.toString();val pixels=text.endsWith("px");val value=text.removeSuffix("dp").removeSuffix("px").toDouble();require(value.isFinite()&&value>=0&&value<=1_000_000);(value*(if(pixels)1f else density)).toInt()}}
            if(props.has("width")||props.has("height")){
                val params=view.layoutParams?:ViewGroup.LayoutParams(-2,-2)
                if(props.has("width"))params.width=dimension(props.get("width"))
                if(props.has("height"))params.height=dimension(props.get("height"))
                view.layoutParams=params
            }
            if(props.has("weight")){val old=view.layoutParams;view.layoutParams=LinearLayout.LayoutParams(old?.width?:-2,old?.height?:-2,props.getDouble("weight").toFloat())}
            if(props.has("padding")){val n=dimension(props.get("padding"));require(n>=0);view.setPadding(n,n,n,n)}
            if(props.has("enabled"))view.isEnabled=props.getBoolean("enabled")
            if(props.has("visible"))view.visibility=if(props.getBoolean("visible"))View.VISIBLE else View.GONE
            if(props.has("contentDescription"))view.contentDescription=props.getString("contentDescription")
            if(props.has("backgroundColor")||props.has("bg")){val value=props.opt("backgroundColor")?:props.get("bg");view.setBackgroundColor(if(value is Number)value.toInt()else Color.parseColor(value.toString()))}
            if(view is TextView){
                if(props.has("textColor")){val value=props.get("textColor");view.setTextColor(if(value is Number)value.toInt()else Color.parseColor(value.toString()))}
                if(props.has("textSize"))view.textSize=props.getString("textSize").removeSuffix("sp").toFloat()
            }
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
    override fun onDestroy(){entry?.detach(this);entry=null;insets?.close();insets=null;super.onDestroy()}
    companion object{internal val windows=ConcurrentHashMap<String,RuntimeUiInterop.Window>()}
}
