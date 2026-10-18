package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Stateful Android components. No Java object or JavaScript interface is exported to web content. */
internal object RuntimeViewComponents {
    fun create(type: String, context: Context, descriptor: JSONObject, session: NodeBridgeUiSession,
               child: (JSONObject) -> View): View? {
        val id = descriptor.optString("id")
        val emit: (String, JSONObject) -> Unit = { name, value -> session.emitComponentEvent(name, id, value) }
        val children = descriptor.optJSONArray("children") ?: JSONArray()
        return when (type) {
            "WebView" -> { require(children.length() == 0); RuntimeWebView(context, emit) }
            "Toolbar" -> Toolbar(context).apply {
                setNavigationOnClickListener { emit("navigationClick", JSONObject()) }
                setOnMenuItemClickListener { item ->
                    val spec = RuntimeViewAttributes.declared(this).optJSONArray("menu")?.optJSONObject(item.itemId - 1)
                    emit("menuClick", JSONObject().put("itemId", spec?.getString("id")).put("title", item.title)); true
                }
                repeat(children.length()) { RuntimeViewWidgets.addChild(this, child(children.getJSONObject(it))) }
            }
            "Drawer" -> DrawerLayout(context).apply {
                addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                    private fun data(view: View) = JSONObject().put("drawerId", view.tag ?: JSONObject.NULL)
                        .put("side", sideName(this@apply, view))
                    override fun onDrawerOpened(drawerView: View) { emit("drawerOpened", data(drawerView)) }
                    override fun onDrawerClosed(drawerView: View) { emit("drawerClosed", data(drawerView)) }
                    override fun onDrawerSlide(drawerView: View, slideOffset: Float) { emit("drawerSlide", data(drawerView).put("offset", slideOffset.toDouble())) }
                    override fun onDrawerStateChanged(newState: Int) { emit("drawerStateChanged", JSONObject().put("state", newState)) }
                })
                repeat(children.length()) { RuntimeViewWidgets.addChild(this, child(children.getJSONObject(it))) }
                validateDrawer(this)
            }
            "ViewPager" -> RuntimeViewPager(context).apply {
                repeat(children.length()) { pages.add(child(children.getJSONObject(it))) }
                adapter = object : PagerAdapter() {
                    override fun getCount() = pages.size
                    override fun isViewFromObject(view: View, item: Any) = view === item
                    override fun instantiateItem(container: ViewGroup, position: Int): Any = pages[position].also {
                        container.addView(it, ViewPager.LayoutParams())
                    }
                    override fun destroyItem(container: ViewGroup, position: Int, item: Any) { container.removeView(item as View) }
                    override fun getPageTitle(position: Int): CharSequence = pages[position].tag?.toString().orEmpty()
                }
                addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                    override fun onPageSelected(position: Int) { emit("pageSelected", JSONObject().put("position", position).put("pageId", pages[position].tag ?: JSONObject.NULL)) }
                    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                        emit("pageScrolled", JSONObject().put("position", position).put("offset", positionOffset.toDouble()).put("offsetPixels", positionOffsetPixels))
                    }
                    override fun onPageScrollStateChanged(state: Int) { emit("pageScrollStateChanged", JSONObject().put("state", state)) }
                })
            }
            else -> null
        }
    }

    fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        val children = when (view) {
            is RuntimeViewPager -> view.pages.toList()
            is ViewGroup -> (0 until view.childCount).map(view::getChildAt)
            else -> emptyList()
        }
        children.forEach { yieldAll(descendants(it)) }
    }
    fun find(view: View, name: String) = descendants(view).firstOrNull { it.tag == name }
    fun dispose(view: View) {
        descendants(view).toList().asReversed().forEach {
            if (it is RuntimeWebView) it.release()
            if (it is RuntimeViewPager) { it.clearOnPageChangeListeners(); it.adapter = null; it.pages.clear() }
        }
    }

    fun side(value: String) = when (value) { "start" -> GravityCompat.START; "end" -> GravityCompat.END; else -> error("Drawer side must be start or end") }
    fun validateDrawer(parent: DrawerLayout, changed: View? = null, gravity: Int = 0) {
        val edges = (0 until parent.childCount).map {
            val child = parent.getChildAt(it)
            if (child === changed) gravity else (child.layoutParams as DrawerLayout.LayoutParams).gravity
        }
        require(edges.count { it == 0 } == 1) { "Drawer requires exactly one content child without layout_gravity" }
        val absolute = edges.filter { it != 0 }.map { GravityCompat.getAbsoluteGravity(it, parent.layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK }
        require(absolute.all { it == Gravity.LEFT || it == Gravity.RIGHT } && absolute.distinct().size == absolute.size) { "Drawer allows one child per start/end edge" }
    }
    private fun sideName(parent: DrawerLayout, view: View): String {
        val gravity = (view.layoutParams as DrawerLayout.LayoutParams).gravity
        return if ((GravityCompat.getAbsoluteGravity(gravity, parent.layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK) ==
            (GravityCompat.getAbsoluteGravity(GravityCompat.START, parent.layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK)) "start" else "end"
    }

    fun icon(view: View, value: Any): android.graphics.drawable.Drawable? {
        if (value === JSONObject.NULL) return null
        val name = value.toString()
        require(name.startsWith("@drawable/") || name.startsWith("@android:drawable/")) { "Toolbar icon requires a drawable resource" }
        val resource = view.resources.getIdentifier(name.substringAfter('/'), "drawable", if (name.startsWith("@android:")) "android" else view.context.packageName)
        require(resource != 0) { "Unknown drawable: $name" }
        return androidx.core.content.ContextCompat.getDrawable(view.context, resource)
    }

    fun menu(view: Toolbar, value: Any): () -> Unit {
        require(value is JSONArray) { "Toolbar menu must be an array" }
        val ids = HashSet<String>()
        data class Entry(val title: String, val icon: android.graphics.drawable.Drawable?, val show: Int, val enabled: Boolean, val visible: Boolean)
        val entries = (0 until value.length()).map { index ->
            val item = value.getJSONObject(index)
            require(item.getString("id").isNotBlank() && ids.add(item.getString("id"))) { "Toolbar menu ids must be unique and nonempty" }
            val show = when (item.optString("showAsAction", "never")) { "never" -> MenuItem.SHOW_AS_ACTION_NEVER; "ifRoom" -> MenuItem.SHOW_AS_ACTION_IF_ROOM; "always" -> MenuItem.SHOW_AS_ACTION_ALWAYS; else -> error("Invalid showAsAction") }
            Entry(item.getString("title"), item.opt("icon")?.let { icon(view, it) }, show,
                if (item.has("enabled")) RuntimeViewAttributes.boolean(item.get("enabled")) else true,
                if (item.has("visible")) RuntimeViewAttributes.boolean(item.get("visible")) else true)
        }
        return {
            view.menu.clear()
            entries.forEachIndexed { index, entry -> view.menu.add(0, index + 1, index, entry.title).apply {
                icon = entry.icon; isEnabled = entry.enabled; isVisible = entry.visible; setShowAsAction(entry.show)
            } }
        }
    }

    /** Completion can be asynchronous for evaluateJavascript; callers receive its decoded JSON result. */
    fun command(view: View, name: String, args: JSONArray, complete: (Result<Any?>) -> Unit) {
        if (name == "evaluateJavascript") {
            require(view is RuntimeWebView) { "$name requires WebView" }
            view.evaluate(args.getString(0), complete); return
        }
        val result: Any? = when (name) {
            "loadUrl", "loadHtml", "goBack", "goForward", "reload", "stopLoading" -> {
                require(view is RuntimeWebView && !view.disposed) { "$name requires an active WebView" }
                when (name) {
                    "loadUrl" -> { view.loadUrl(args.getString(0).also { require(it.isNotBlank()) }); RuntimeViewAttributes.declared(view).remove("html") }
                    "loadHtml" -> {
                        val html = args.getString(0)
                        view.loadDataWithBaseURL(if (args.isNull(1)) null else args.getString(1), html, "text/html", "UTF-8", null)
                        RuntimeViewAttributes.declared(view).put("html", html)
                    }
                    "goBack" -> view.goBack()
                    "goForward" -> view.goForward()
                    "reload" -> view.reload()
                    else -> view.stopLoading()
                }; null
            }
            "openDrawer", "closeDrawer" -> {
                require(view is DrawerLayout) { "$name requires Drawer" }
                val edge = side(args.optString(0, "start"))
                val animate = if (args.isNull(1)) true else RuntimeViewAttributes.boolean(args.get(1))
                if (name == "openDrawer") view.openDrawer(edge, animate) else view.closeDrawer(edge, animate)
                null
            }
            "setCurrentItem" -> {
                require(view is RuntimeViewPager) { "$name requires ViewPager" }
                val index = RuntimeViewAttributes.integer(args.get(0), 0, view.pages.size - 1)
                view.setCurrentItem(index, if (args.isNull(1)) true else RuntimeViewAttributes.boolean(args.get(1))); null
            }
            "showOverflowMenu", "hideOverflowMenu" -> {
                require(view is Toolbar) { "$name requires Toolbar" }
                if (name == "showOverflowMenu") view.showOverflowMenu() else view.hideOverflowMenu()
            }
            else -> error("Unknown native View command: $name")
        }
        complete(Result.success(result))
    }
}

internal class RuntimeViewPager(context: Context) : ViewPager(context) {
    val pages = ArrayList<View>()
}

internal class RuntimeWebView(context: Context, private val emit: (String, JSONObject) -> Unit) : WebView(context) {
    var disposed = false; private set
    private val main = Handler(Looper.getMainLooper())
    private val evaluations = LinkedHashMap<Any, (Result<Any?>) -> Unit>()
    init {
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { if (!disposed) emit("pageStarted", JSONObject().put("url", url)) }
            override fun onPageFinished(view: WebView, url: String?) { if (!disposed) emit("pageFinished", JSONObject().put("url", url)) }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!disposed) emit("webError", JSONObject().put("url", request.url.toString()).put("mainFrame", request.isForMainFrame)
                    .put("errorCode", error.errorCode).put("message", error.description.toString()))
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (!disposed) emit("httpError", JSONObject().put("url", request.url.toString()).put("mainFrame", request.isForMainFrame).put("statusCode", response.statusCode))
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                emit("webError", JSONObject().put("message", "WebView renderer exited").put("didCrash", detail.didCrash()))
                release(); return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { if (!disposed) emit("progressChanged", JSONObject().put("progress", newProgress)) }
            override fun onReceivedTitle(view: WebView, title: String?) { if (!disposed) emit("titleChanged", JSONObject().put("title", title)) }
        }
    }

    fun evaluate(source: String, complete: (Result<Any?>) -> Unit) {
        check(!disposed) { "WebView is disposed" }
        require(settings.javaScriptEnabled) { "evaluateJavascript requires javaScriptEnabled" }
        val key = Any()
        val timeout = Runnable { evaluations.remove(key)?.invoke(Result.failure(IllegalStateException("WebView JavaScript result timed out"))) }
        evaluations[key] = { result -> main.removeCallbacks(timeout); complete(result) }
        main.postDelayed(timeout, 10000)
        try {
            evaluateJavascript(source) { json -> evaluations.remove(key)?.invoke(runCatching { JSONTokener(json ?: "null").nextValue() }) }
        } catch (failure: Throwable) { evaluations.remove(key)?.invoke(Result.failure(failure)) }
    }

    fun release() {
        if (disposed) return
        disposed = true
        evaluations.values.toList().also { evaluations.clear() }.forEach { it(Result.failure(IllegalStateException("WebView is disposed"))) }
        (parent as? ViewGroup)?.removeView(this)
        stopLoading(); webChromeClient = null; webViewClient = WebViewClient(); removeAllViews(); destroy()
    }
}
