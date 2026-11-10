package org.autojs.autojs.ui.virtualscreen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.autojs.autojs.core.virtualscreen.DisplayCoordinateMapper
import org.autojs.autojs.core.virtualscreen.VirtualScreenManager
import org.autojs.autojs.core.virtualscreen.VirtualScreenSession
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils.setToolbarAsBack
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityVirtualScreenViewerBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * "View and operate a virtual screen": a live, letterboxed preview of a background display plus
 * direct control over it. Touches become taps / swipes / long presses in that display's logical
 * coordinates; the buttons cover navigation keys, text, app launch, PNG capture and screen
 * lifecycle (create / switch / release).
 *
 * A screen created here is *detached* (no ScriptRuntime): it survives leaving this activity and
 * can also be reused by a task through `virtualScreenDisplayId`. A screen created by a running
 * script can be attached to as well, and the frame pump makes preview and script screenshots
 * coexist without starving the ImageReader.
 *
 * zh-CN: 「虚拟屏幕查看和操作」: 实时等比预览后台虚拟屏, 并直接操作它. 预览上的触摸换算为该屏逻辑坐标的
 * 点击/滑动/长按; 工具栏提供导航键、文本输入、启动应用、保存 PNG 与屏幕生命周期 (新建/切换/释放).
 * 本页新建的屏是独立屏 (无 ScriptRuntime), 退出页面后仍然保留, 也可被任务通过 virtualScreenDisplayId 复用;
 * 脚本创建的屏同样可以附着查看, 帧泵保证预览与脚本截图互不抢帧.
 *
 * Created on Nov 8, 2026. See docs/virtual-screen/README.md.
 */
class VirtualScreenViewerActivity : BaseActivity(), VirtualScreenView.GestureListener {

    private lateinit var binding: ActivityVirtualScreenViewerBinding

    private val mainHandler = Handler(Looper.getMainLooper())
    private var io: ExecutorService = Executors.newSingleThreadExecutor()

    private var session: VirtualScreenSession? = null
    private var requestedDisplayId = 0
    private var previewPaused = false
    private var lastFrameCount = 0L

    private val statusTask = object : Runnable {
        override fun run() {
            refreshStatus()
            mainHandler.postDelayed(this, STATUS_INTERVAL_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVirtualScreenViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setToolbarAsBack(this, R.string.text_virtual_screen_viewer)

        binding.preview.gestureListener = this
        requestedDisplayId = intent.getIntExtra(EXTRA_DISPLAY_ID, 0)
        bindButtons()
        refreshHint()
        refreshStatus()
    }

    override fun onStart() {
        super.onStart()
        val target = when {
            requestedDisplayId > 0 -> VirtualScreenManager.get(requestedDisplayId)
            else -> VirtualScreenManager.liveSessions().firstOrNull()
        }
        attach(target)
        mainHandler.removeCallbacks(statusTask)
        mainHandler.post(statusTask)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(statusTask)
        // Only the frame feed is stopped; the display itself stays alive across activities.
        session?.stopPreview()
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        binding.preview.attach(null)
        super.onDestroy()
    }

    // ---- Attachment & preview ----

    private fun attach(target: VirtualScreenSession?) {
        session?.stopPreview()
        session = target?.takeIf { !it.isReleased() }
        // Keep the selection across background/foreground cycles.
        requestedDisplayId = session?.getDisplayId() ?: 0
        binding.preview.attach(session)
        lastFrameCount = 0L
        previewPaused = false
        binding.btnPause.text = getString(R.string.text_vs_pause)
        if (session != null) startPreview()
        refreshHint()
        refreshStatus()
    }

    private fun startPreview() {
        val target = session ?: return
        val ok = runCatching {
            target.startPreview {
                mainHandler.post {
                    binding.preview.renderFrame()
                    if (binding.tvHint.visibility != View.GONE) refreshHint()
                }
            }
        }.onFailure { showError(it) }.getOrDefault(false)
        if (!ok) toast(getString(R.string.text_vs_frame_error, target.previewError().orEmpty()))
        refreshStatus()
    }

    // ---- Status ----

    private fun refreshStatus() {
        val current = session
        if (current != null && current.isReleased()) {
            // A script exited and released the display underneath the viewer.
            session = null
            binding.preview.attach(null)
        }
        val live = session
        if (live == null) {
            binding.tvStatus.text = getString(R.string.text_vs_status_idle)
            binding.tvMetrics.text = when {
                !VirtualScreenManager.isAvailable() -> getString(R.string.text_vs_shizuku_required)
                else -> getString(R.string.text_vs_no_screen)
            }
            refreshHint()
            return
        }
        val frames = live.previewFrameCount()
        val fps = (frames - lastFrameCount).coerceAtLeast(0).toInt()
        lastFrameCount = frames
        val kind = getString(
            if (live.isDetached()) R.string.text_vs_detached else R.string.text_vs_script_bound,
        )
        binding.tvStatus.text = getString(
            R.string.text_vs_status, live.getDisplayId(), live.getWidth(), live.getHeight(), live.getDpi(), fps, kind,
        )
        binding.tvMetrics.text = buildString {
            append("frames=").append(frames)
            append(" · preview=").append(if (previewPaused) "paused" else "live")
            append(" · trusted=").append(live.isTrusted())
            append(" · ownFocus=").append(live.hasOwnFocus())
            append(" · err=").append(live.previewError() ?: "-")
            if (live.previewLastWriteMillis() == 0L) append(" · noFrameYet")
        }
        refreshHint()
    }

    private fun refreshHint() {
        val live = session
        val hasFrame = live?.previewBitmap() != null
        binding.tvHint.visibility = if (live != null && hasFrame && !previewPaused) View.GONE else View.VISIBLE
        binding.tvHint.text = when {
            live == null -> getString(R.string.text_vs_no_screen)
            previewPaused -> getString(R.string.text_vs_preview_paused)
            !VirtualScreenManager.isAvailable() -> getString(R.string.text_vs_shizuku_required)
            else -> live.previewError()?.let { getString(R.string.text_vs_frame_error, it) }
                ?: getString(R.string.text_vs_waiting_frame)
        }
    }

    // ---- Gestures ----

    override fun onTap(point: DisplayCoordinateMapper.Point) {
        execute("tap") { it.tap(point.x, point.y) }
    }

    override fun onSwipe(from: DisplayCoordinateMapper.Point, to: DisplayCoordinateMapper.Point, durationMs: Int) {
        execute("swipe") { it.swipe(from.x, from.y, to.x, to.y, durationMs) }
    }

    override fun onLongPress(point: DisplayCoordinateMapper.Point, durationMs: Int) {
        execute("long press") { it.longPress(point.x, point.y, durationMs) }
    }

    // ---- Buttons ----

    private fun bindButtons() {
        binding.btnBack.setOnClickListener { key(KeyEvent.KEYCODE_BACK) }
        binding.btnHome.setOnClickListener { key(KeyEvent.KEYCODE_HOME) }
        binding.btnRecents.setOnClickListener { key(KeyEvent.KEYCODE_APP_SWITCH) }
        binding.btnPower.setOnClickListener { key(KeyEvent.KEYCODE_POWER) }
        binding.btnInput.setOnClickListener { promptText() }
        binding.btnLaunch.setOnClickListener { promptLaunch() }
        binding.btnSave.setOnClickListener { savePng() }
        binding.btnPause.setOnClickListener { togglePreview() }
        binding.btnRefresh.setOnClickListener { retryFrame() }
        binding.btnNew.setOnClickListener { createScreen() }
        binding.btnSwitch.setOnClickListener { chooseScreen() }
        binding.btnRelease.setOnClickListener { confirmRelease() }
        binding.btnGrant.setOnClickListener { grantShizuku() }
    }

    private fun key(keyCode: Int) = execute("key $keyCode") { it.key(keyCode) }

    private fun togglePreview() {
        val live = session ?: return
        if (previewPaused) {
            previewPaused = false
            binding.btnPause.text = getString(R.string.text_vs_pause)
            startPreview()
        } else {
            previewPaused = true
            binding.btnPause.text = getString(R.string.text_vs_resume)
            live.stopPreview()
        }
        refreshHint()
        refreshStatus()
    }

    /** Re-drains the ImageReader, useful after putting content on a previously empty display. */
    private fun retryFrame() {
        val live = session ?: return
        if (previewPaused) {
            togglePreview()
            return
        }
        if (!live.isPreviewing()) {
            startPreview()
        } else {
            live.drainPreview()
            mainHandler.postDelayed({ live.drainPreview() }, 350L)
        }
        refreshStatus()
    }

    private fun createScreen() {
        if (!VirtualScreenManager.isAvailable()) {
            toast(getString(R.string.text_vs_shizuku_required))
            return
        }
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi
        runOnIo {
            val created = runCatching { VirtualScreenManager.createDetached(width, height, dpi, 0) }
            created.onSuccess { screen ->
                // A freshly created display hosts no window, so some ROMs never submit a frame.
                // Put a system app on it first; the polling pump then drains the pending frames.
                val launched = runCatching { screen.launch(DEFAULT_LAUNCH_PACKAGE) == 0 }.getOrDefault(false)
                mainHandler.post {
                    attach(screen)
                    toast(
                        if (launched) getString(R.string.text_vs_created_hint, screen.getDisplayId())
                        else getString(R.string.text_vs_auto_launch_failed),
                    )
                    screen.drainPreview()
                    mainHandler.postDelayed({ screen.drainPreview(); refreshStatus() }, 600L)
                }
            }.onFailure { showError(it) }
        }
    }

    private fun chooseScreen() {
        val screens = VirtualScreenManager.liveSessions()
        if (screens.isEmpty()) {
            toast(getString(R.string.text_vs_no_screen))
            return
        }
        val labels = screens.map {
            getString(
                R.string.text_vs_status, it.getDisplayId(), it.getWidth(), it.getHeight(), it.getDpi(), 0,
                getString(if (it.isDetached()) R.string.text_vs_detached else R.string.text_vs_script_bound),
            )
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.text_vs_screen_choices)
            .setItems(labels) { _, index -> attach(screens[index]) }
            .setNegativeButton(R.string.text_vs_cancel, null)
            .show()
    }

    private fun confirmRelease() {
        val live = session ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.text_vs_release_title)
            .setMessage(R.string.text_vs_release_confirm)
            .setPositiveButton(R.string.text_vs_ok) { _, _ ->
                val id = live.getDisplayId()
                runOnIo {
                    runCatching { live.release() }
                    mainHandler.post {
                        toast(getString(R.string.text_vs_released, id))
                        attach(VirtualScreenManager.liveSessions().firstOrNull())
                    }
                }
            }
            .setNegativeButton(R.string.text_vs_cancel, null)
            .show()
    }

    private fun promptText() {
        val input = EditText(this).apply { hint = getString(R.string.text_vs_input_hint) }
        AlertDialog.Builder(this)
            .setTitle(R.string.text_vs_input)
            .setView(input.inDialogContainer())
            .setPositiveButton(R.string.text_vs_ok) { _, _ ->
                val value = input.text.toString()
                if (value.isNotEmpty()) execute("text") { it.text(value) }
            }
            .setNegativeButton(R.string.text_vs_cancel, null)
            .show()
    }

    private fun promptLaunch() {
        val input = EditText(this).apply { hint = getString(R.string.text_vs_launch_hint) }
        AlertDialog.Builder(this)
            .setTitle(R.string.text_vs_launch)
            .setView(input.inDialogContainer())
            .setPositiveButton(R.string.text_vs_ok) { _, _ ->
                val target = input.text.toString().trim()
                if (target.isEmpty()) return@setPositiveButton
                execute("launch") {
                    if (target.contains('/')) it.launchComponent(target) == 0 else it.launch(target) == 0
                }
            }
            .setNegativeButton(R.string.text_vs_cancel, null)
            .show()
    }

    private fun savePng() {
        val live = session ?: return
        val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "virtual-screen-${live.getDisplayId()}-$stamp.png")
        runOnIo {
            val ok = runCatching { live.captureToFile(file.absolutePath) }.getOrDefault(false)
            mainHandler.post {
                toast(
                    if (ok) getString(R.string.text_vs_saved, file.absolutePath)
                    else getString(R.string.text_vs_action_failed, "save PNG"),
                )
            }
        }
    }

    private fun grantShizuku() {
        runCatching { WrappedShizuku.requestPermission() }.onFailure { showError(it) }
    }

    // ---- Helpers ----

    /** Runs a Binder call off the main thread; only a failure produces a toast. */
    private fun execute(label: String, block: (VirtualScreenSession) -> Boolean) {
        val live = session ?: return
        runOnIo {
            val ok = runCatching { block(live) }.onFailure { showError(it) }.getOrDefault(false)
            if (!ok) {
                mainHandler.post { toast(getString(R.string.text_vs_action_failed, label)) }
            }
        }
    }

    private fun runOnIo(block: () -> Unit) {
        if (io.isShutdown || io.isTerminated) return
        io.execute {
            runCatching { block() }.onFailure { showError(it) }
        }
    }

    private fun showError(error: Throwable) {
        mainHandler.post { toast(getString(R.string.text_vs_error, error.message.orEmpty())) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun EditText.inDialogContainer(): View = FrameLayout(context).apply {
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding / 2, padding, 0)
        addView(this@inDialogContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
        ))
    }

    companion object {

        /** Optional: attach straight to one display instead of the first live one. */
        const val EXTRA_DISPLAY_ID = "virtual_screen_display_id"

        private const val STATUS_INTERVAL_MILLIS = 1_000L

        /** Present on virtually every Android build; gives a new display visible content. */
        private const val DEFAULT_LAUNCH_PACKAGE = "com.android.settings"

        @JvmStatic
        fun launch(context: Context, displayId: Int = 0) {
            val intent = Intent(context, VirtualScreenViewerActivity::class.java)
                .putExtra(EXTRA_DISPLAY_ID, displayId)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

    }

}
