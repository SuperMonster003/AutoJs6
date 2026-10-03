package org.autojs.test.codegen.fixture

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.AtomicFile
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local-only, debuggable fixture. Oracle tags are deliberately absent from accessibility data. */
class MainActivity : Activity() {
    private lateinit var scenario: FixtureScenario
    private lateinit var stateId: String
    private lateinit var oracleRoot: View
    private val targets = linkedMapOf<String, View>()
    private var ready = false
    private var dialog: Dialog? = null
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        launchScenario()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchScenario()
    }

    private fun launchScenario() {
        val requestGeneration = ++generation
        ready = false
        targets.clear()
        dialog?.dismiss()
        dialog = null
        File(filesDir, "ready.json").delete()
        File(filesDir, "error.json").delete()
        File(filesDir, "receipts.jsonl").createNewFile()
        try {
            scenario = FixtureScenario.create(
                intent.getStringExtra("screen") ?: "login",
                intent.getLongExtra("seed", 0),
                intent.getStringExtra("mutations").orEmpty().split(',').filter { it.isNotEmpty() },
            )
            stateId = FixtureScenario.safeStateId(intent.getStringExtra("stateId") ?: scenario.defaultStateId)
            File(filesDir, "truth/$stateId.json").delete()
            val content = buildScreen()
            content.isFocusableInTouchMode = true
            content.requestFocus()
            if (scenario.screen == "dialog") {
                setContentView(label(localized("Account preferences", "账户偏好"), 18))
                dialog = Dialog(this).apply {
                    setTitle(localized("Permission request", "权限请求"))
                    setContentView(content)
                    setCancelable(false)
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                    show()
                    window?.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                oracleRoot = requireNotNull(dialog?.window).decorView
            } else {
                // Keeping content inside the system insets also works when edge-to-edge is enforced.
                val insetRoot = FrameLayout(this).apply {
                    @Suppress("DEPRECATION")
                    setOnApplyWindowInsetsListener { view, insets ->
                        view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                            insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                        insets
                    }
                    addView(content, FrameLayout.LayoutParams(-1, -1))
                }
                setContentView(insetRoot)
                oracleRoot = window.decorView
            }
            oracleRoot.post { awaitStableTruth(null, 0, 0, requestGeneration) }
        } catch (error: IllegalArgumentException) {
            fail(error.message ?: "Invalid scenario")
            setContentView(label("Invalid fixture request", 18))
        }
    }

    private fun buildScreen(): LinearLayout = column().apply {
        assignId(this, R.id.screen_content)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        addView(label(title(), 17).apply { assignId(this, R.id.screen_title) }, LinearLayout.LayoutParams(-1, dp(30)))
        when (scenario.screen) {
            "list" -> addView(RecyclerView(this@MainActivity).apply {
                assignId(this, R.id.panel_content)
                layoutManager = LinearLayoutManager(this@MainActivity)
                itemAnimator = null
                isNestedScrollingEnabled = false
                adapter = RowsAdapter()
            }, LinearLayout.LayoutParams(-1, -2))
            "grid" -> addView(GridLayout(this@MainActivity).apply {
                columnCount = 2
                scenario.rows.forEach { row ->
                    addView(renderRow(row), GridLayout.LayoutParams().apply {
                        width = 0
                        height = dp(if (row.key == null) 20 else 64)
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    })
                }
            }, LinearLayout.LayoutParams(-1, -2))
            "nested-scroll" -> addView(ScrollView(this@MainActivity).apply {
                assignId(this, R.id.panel_content)
                addView(column().apply { addRows(this) }, ViewGroup.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(-1, -2))
            else -> addRows(this)
        }
    }

    private fun addRows(parent: LinearLayout) {
        // Tabs remain a visible navigation strip; all three content actions persist below it.
        if (scenario.screen == "tabs") {
            val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            scenario.rows.filter { it.key != null && it.slot < 3 }.forEach { row ->
                strip.addView(renderRow(row), LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            parent.addView(strip, LinearLayout.LayoutParams(-1, dp(44)))
            scenario.rows.filter { it.key == null || it.slot >= 3 }.forEach { addRow(parent, it) }
        } else scenario.rows.forEach { addRow(parent, it) }
    }

    private fun addRow(parent: LinearLayout, row: FixtureScenario.Row) {
        parent.addView(renderRow(row), LinearLayout.LayoutParams(-1,
            dp(if (row.key == null) 18 else if (scenario.screen == "nested-scroll") 48 else 44)))
    }

    private fun renderRow(row: FixtureScenario.Row): View {
        if (row.key == null) return label(localized("Updated ${scenario.dynamicNumber(row.slot)}", "更新 ${scenario.dynamicNumber(row.slot)}"), 10)
        val control = when (scenario.screen) {
            "login" -> when (row.slot) {
                0, 1 -> input(row, if (row.slot == 0) R.id.email else R.id.password)
                2 -> toggle(row, R.id.remember)
                else -> button(row, if (row.slot == 3) R.id.action_primary else R.id.action_secondary)
            }
            "settings" -> toggle(row, R.id.setting_switch)
            "dialog" -> when (row.slot) {
                0 -> input(row, R.id.email)
                1 -> toggle(row, R.id.remember)
                else -> button(row, if (row.slot == 2) R.id.action_primary else R.id.action_secondary)
            }
            "nested-scroll" -> scroll(row)
            "no-id" -> if (row.slot == 0) input(row, R.id.email) else button(row, R.id.action_secondary)
            "list" -> button(row, R.id.item_action, localized("Remove", "删除"))
            "tabs" -> button(row, if (row.slot < 3) R.id.tab_item else R.id.action_secondary)
            else -> button(row, R.id.card_action)
        }
        register(row.key, control)
        var rendered: View = if (scenario.screen == "list") {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                assignId(this, R.id.item_row)
                addView(label("${rowLabel(row.slot)}\n${localized("Price", "价格")} ${scenario.dynamicNumber(row.slot)}", 11).apply {
                    assignId(this, R.id.item_title)
                }, LinearLayout.LayoutParams(0, -1, 1f))
                addView(control, LinearLayout.LayoutParams(dp(90), -1))
            }
        } else control
        if (scenario.sibling) {
            rendered = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                addView(label(localized("New", "新"), 9), LinearLayout.LayoutParams(dp(28), -1))
                addView(rendered, LinearLayout.LayoutParams(0, -1, 1f))
            }
        }
        if (scenario.wrap && row.slot % 2 == 0) {
            repeat(2) {
                rendered = FrameLayout(this).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    assignId(this, R.id.wrapper)
                    addView(rendered, FrameLayout.LayoutParams(-1, -1))
                }
            }
        }
        return rendered
    }

    private fun button(row: FixtureScenario.Row, resourceId: Int, caption: String = rowLabel(row.slot)): Button = Button(this).apply {
        assignId(this, resourceId)
        text = caption
        isAllCaps = false
        textSize = 12f
        minimumHeight = 0
        minHeight = 0
        minimumWidth = 0
        minWidth = 0
        setPadding(dp(3), 0, dp(3), 0)
        setOnClickListener { receipt(row.key!!, "click") }
        setOnLongClickListener { receipt(row.key!!, "long-click"); true }
    }

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun toggle(row: FixtureScenario.Row, resourceId: Int): Switch = Switch(this).apply {
        assignId(this, resourceId)
        text = rowLabel(row.slot)
        textSize = 12f
        isChecked = Math.floorMod(scenario.seed + row.slot, 2L) == 0L
        setOnClickListener { receipt(row.key!!, "click", JSONObject().put("checked", isChecked)) }
        setOnLongClickListener { receipt(row.key!!, "long-click"); true }
    }

    private fun input(row: FixtureScenario.Row, resourceId: Int): EditText = EditText(this).apply {
        assignId(this, resourceId)
        isSingleLine = true
        textSize = 12f
        setText("${rowLabel(row.slot)} ${scenario.dynamicNumber(row.slot)}")
        setSelectAllOnFocus(true)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(value: Editable?) {
                receipt(row.key!!, "set-text", JSONObject().put("text", value?.toString().orEmpty()))
            }
        })
        setOnClickListener { receipt(row.key!!, "click") }
        // Preserve standard text-selection long-click behavior; it is not a fixture action target.
    }

    private fun scroll(row: FixtureScenario.Row): ScrollView = ScrollView(this).apply {
        assignId(this, R.id.panel_content)
        contentDescription = rowLabel(row.slot)
        isNestedScrollingEnabled = true
        addView(column().apply {
            repeat(9) { index ->
                addView(label("${rowLabel(row.slot)} ${scenario.dynamicNumber(index + row.slot)}", 11),
                    LinearLayout.LayoutParams(-1, dp(24)))
            }
        }, ViewGroup.LayoutParams(-1, -2))
        // Both directions are available from the captured state.
        post { scrollTo(0, dp(48)) }
        setOnScrollChangeListener { _, x, y, oldX, oldY ->
            if (x != oldX || y != oldY) receipt(row.key!!,
                if (y > oldY || x > oldX) "scroll-forward" else "scroll-backward",
                JSONObject().put("x", x).put("y", y).put("oldX", oldX).put("oldY", oldY))
        }
    }

    private fun register(key: String, view: View) {
        view.setTag(R.id.codegen_truth, key)
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        targets[key] = view
    }

    private fun assignId(view: View, resourceId: Int) {
        view.id = when {
            scenario.stripIds -> View.NO_ID
            scenario.renameIds -> RENAMED_IDS[Math.floorMod(resourceId, RENAMED_IDS.size)]
            else -> resourceId
        }
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun label(value: String, size: Int) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun title() = localized(TITLES.getValue(scenario.screen)[0], TITLES.getValue(scenario.screen)[1])
    private fun localized(english: String, chinese: String) = if (scenario.chinese) chinese else english

    private fun rowLabel(slot: Int): String {
        val words = LABELS.getValue(scenario.screen)[slot]
        val base = localized(words[0], words[1])
        // Dynamic content changes every family without turning oracle keys into UI labels.
        return if (scenario.screen in setOf("grid", "tabs", "no-id", "settings", "dialog") && slot >= 2)
            "$base (${scenario.dynamicNumber(slot)})" else base
    }

    private fun truth(): String? {
        if (targets.keys.sorted() != scenario.targetKeys) return null
        // A service can request otherwise unimportant layout parents, while an in-process
        // parentForAccessibility call has no service fetch flags. Make all target ancestors
        // explicit so both traversals observe the same parent chain and depth.
        targets.values.forEach { target ->
            var ancestor = target.parent
            while (ancestor is View) {
                ancestor.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                ancestor = ancestor.parent
            }
        }
        val entries = JSONArray()
        for ((key, view) in targets.toSortedMap()) {
            val visible = Rect()
            if (!view.isAttachedToWindow || !view.getGlobalVisibleRect(visible) || visible.isEmpty) return null
            // getGlobalVisibleRect is relative to ViewRoot; dialogs have a nonzero window origin.
            val rootLocation = IntArray(2)
            view.rootView.getLocationOnScreen(rootLocation)
            visible.offset(rootLocation[0], rootLocation[1])
            val ownLocation = IntArray(2)
            view.getLocationOnScreen(ownLocation)
            val whole = Rect(ownLocation[0], ownLocation[1], ownLocation[0] + view.width, ownLocation[1] + view.height)
            // Partially clipped targets are unsuitable for the bounds-based oracle join.
            if (visible != whole) return null
            var depth = 0
            var parent = view.parentForAccessibility
            while (parent is View) { depth++; parent = parent.parentForAccessibility }
            val info = view.createAccessibilityNodeInfo()
            val capabilities = JSONArray()
            if (view.isClickable) capabilities.put("click")
            if (view.isLongClickable && view !is EditText) capabilities.put("long-click")
            if (view is EditText) capabilities.put("set-text")
            if (view is ScrollView) capabilities.put("scroll-forward").put("scroll-backward")
            entries.put(JSONObject().put("key", key)
                .put("bounds", JSONArray(listOf(visible.left, visible.top, visible.right, visible.bottom)))
                .put("depth", depth).put("className", info.className.toString()).put("capabilities", capabilities))
            @Suppress("DEPRECATION")
            info.recycle()
        }
        return JSONObject().put("format", 1).put("stateId", stateId).put("screenFamily", scenario.screen)
            .put("seed", scenario.seed).put("mutations", JSONArray(scenario.mutations))
            .put("appliedMutations", JSONArray(scenario.appliedMutations))
            .put("skippedMutations", JSONArray(scenario.skippedMutations)).put("targets", entries).toString() + "\n"
    }

    private fun awaitStableTruth(previous: String?, stableFrames: Int, attempt: Int, requestGeneration: Int) {
        if (isFinishing || isDestroyed || requestGeneration != generation) return
        val current = truth()
        val stable = if (current != null && current == previous) stableFrames + 1 else 0
        if (stable >= 2) {
            atomicWrite(File(filesDir, "truth/$stateId.json"), current!!)
            ready = true
            atomicWrite(File(filesDir, "ready.json"), JSONObject().put("format", 1).put("stateId", stateId)
                .put("runId", intent.getStringExtra("runId").orEmpty()).put("truth", "truth/$stateId.json").toString() + "\n")
        } else if (attempt >= 40) fail("Targets did not become fully visible and stable")
        else oracleRoot.postDelayed({ awaitStableTruth(current, stable, attempt + 1, requestGeneration) }, 50)
    }

    private fun receipt(key: String, action: String, args: JSONObject = JSONObject()) {
        if (!ready) return
        File(filesDir, "receipts.jsonl").appendText(JSONObject().put("key", key).put("action", action)
            .put("args", args).put("time", System.currentTimeMillis()).put("stateId", stateId).toString() + "\n", Charsets.UTF_8)
    }

    private fun fail(message: String) {
        atomicWrite(File(filesDir, "error.json"), JSONObject().put("error", message)
            .put("runId", intent.getStringExtra("runId").orEmpty())
            .put("stateId", intent.getStringExtra("stateId").orEmpty()).toString() + "\n")
    }

    private fun atomicWrite(file: File, text: String) {
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private inner class RowsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = scenario.rows.size
        override fun getItemViewType(position: Int) = position
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val row = scenario.rows[viewType]
            return object : RecyclerView.ViewHolder(renderRow(row).apply {
                layoutParams = RecyclerView.LayoutParams(-1, dp(if (row.key == null) 18 else 48))
            }) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }

    companion object {
        private val RENAMED_IDS = intArrayOf(R.id.a01, R.id.a02, R.id.a03, R.id.a04, R.id.a05, R.id.a06,
            R.id.a07, R.id.a08, R.id.a09, R.id.a10, R.id.a11, R.id.a12)
        private val TITLES = mapOf(
            "login" to listOf("Welcome back", "欢迎回来"), "list" to listOf("Shopping cart", "购物车"),
            "settings" to listOf("Settings", "设置"), "grid" to listOf("Library", "资料库"),
            "tabs" to listOf("Project", "项目"), "dialog" to listOf("Permission request", "权限请求"),
            "nested-scroll" to listOf("Dashboard", "概览"), "no-id" to listOf("Workspace", "工作区"),
        )
        private val LABELS = mapOf(
            "login" to listOf(listOf("Email", "邮箱"), listOf("Password", "密码"), listOf("Remember me", "记住我"),
                listOf("Sign in", "登录"), listOf("Help", "帮助"), listOf("Continue as guest", "访客访问")),
            "list" to listOf(listOf("Mouse", "鼠标"), listOf("Keyboard", "键盘"), listOf("Camera", "相机"),
                listOf("Speaker", "音箱"), listOf("Headphones", "耳机"), listOf("Charger", "充电器")),
            "settings" to listOf(listOf("Wi-Fi", "无线网络"), listOf("Bluetooth", "蓝牙"), listOf("Notifications", "通知"),
                listOf("Sync", "同步"), listOf("Location", "位置"), listOf("Dark mode", "深色模式")),
            "grid" to listOf(listOf("Photos", "照片"), listOf("Music", "音乐"), listOf("Videos", "视频"),
                listOf("Books", "图书"), listOf("Downloads", "下载"), listOf("Documents", "文档")),
            "tabs" to listOf(listOf("Overview", "概览"), listOf("Activity", "动态"), listOf("Files", "文件"),
                listOf("Open", "打开"), listOf("Share", "分享"), listOf("Archive", "归档")),
            "dialog" to listOf(listOf("Account", "账户"), listOf("Remember choice", "记住选择"), listOf("Allow", "允许"),
                listOf("Deny", "拒绝"), listOf("Details", "详情"), listOf("Later", "稍后")),
            "nested-scroll" to listOf(listOf("News", "新闻"), listOf("Messages", "消息"), listOf("Events", "活动"),
                listOf("Tasks", "任务"), listOf("Notes", "笔记"), listOf("History", "历史")),
            "no-id" to listOf(listOf("Search", "搜索"), listOf("Filter", "筛选"), listOf("Sort", "排序"),
                listOf("Favorite", "收藏"), listOf("Export", "导出"), listOf("Refresh", "刷新")),
        )
    }
}
