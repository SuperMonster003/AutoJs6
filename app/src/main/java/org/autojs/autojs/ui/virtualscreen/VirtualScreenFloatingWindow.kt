package org.autojs.autojs.ui.virtualscreen

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.virtualscreen.FloatingWindowGeometry
import org.autojs.autojs.core.virtualscreen.VirtualScreenManager
import org.autojs.autojs.core.virtualscreen.VirtualScreenSession
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FloatingVirtualScreenBinding
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * A small live preview pinned to the bottom-right corner of the host Activity.
 *
 * It is an *in-app* overlay added to `android.R.id.content`, so it needs no
 * `SYSTEM_ALERT_WINDOW` permission and never leaks outside AutoJs6. It follows whichever virtual
 * display is alive (one created by a running script, by a scheduled task or by the viewer), shows
 * its display id, size and FPS, and can be dragged, collapsed into a bubble or tapped to open the
 * full [VirtualScreenViewerActivity] for interaction.
 *
 * Frames come from the same [org.autojs.autojs.core.virtualscreen.VirtualScreenFramePump] as the
 * full viewer: the session keeps one ImageReader consumer and every surface is just a subscriber,
 * so opening/closing one never blacks out the other. Script screenshots keep working because
 * `VirtualScreenSession.snapshotBitmap()` copies the pump's reusable bitmap.
 *
 * Lifecycle: [attach] once after `setContentView`, [resume]/[pause] from the Activity's
 * start/stop so background pages do not consume frames, [detach] on destroy.
 *
 * zh-CN: 固定在宿主界面右下角的实时小窗. 它是加到 `android.R.id.content` 的应用内浮层, 不需要
 * SYSTEM_ALERT_WINDOW 权限, 也不会出现在 AutoJs6 之外. 自动跟随当前存活的虚拟屏 (脚本、定时任务或查看器
 * 创建的均可), 显示 displayId、尺寸与帧率; 可拖动、收起成小浮标, 点击打开完整查看器进行操作.
 * 帧源与完整查看器共用同一个帧泵: 会话只有一个 ImageReader 消费者, 各界面只是订阅者, 打开或关闭其中
 * 一个不会让另一个黑屏; 脚本截图仍从帧泵的复用 Bitmap 复制, 互不抢帧.
 *
 * Created on Nov 14, 2026. See docs/virtual-screen/README.md.
 */
class VirtualScreenFloatingWindow(private val activity: Activity) {

    private val binding = FloatingVirtualScreenBinding.inflate(activity.layoutInflater)
    private val root: View = binding.root
    private val container: ViewGroup? = activity.findViewById(android.R.id.content)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = activity.resources.displayMetrics.density
    private val framePending = AtomicBoolean(false)

    private var attached = false
    private var running = false
    private var collapsed = false
    private var session: VirtualScreenSession? = null
    private var previewToken: Any? = null
    private var lastFrameCount = 0L

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragOriginX = 0f
    private var dragOriginY = 0f
    private var dragging = false

    private val statusTask = object : Runnable {
        override fun run() {
            refresh()
            if (running) mainHandler.postDelayed(this, STATUS_INTERVAL_MILLIS)
        }
    }

    /** Adds the view to the content container. Call once, after the Activity has a decor view. */
    fun attach() {
        if (attached) return
        val parent = container ?: return
        val params: ViewGroup.LayoutParams = if (parent is FrameLayout) {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END,
            ).apply {
                bottomMargin = dp(BOTTOM_MARGIN_DP)
                marginEnd = dp(END_MARGIN_DP)
            }
        } else {
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        parent.addView(root, params)
        attached = true
        bindViews()
        applyCollapsed()
    }

    /** Starts previewing (called from Activity.onStart). */
    fun resume() {
        if (!attached || running) return
        running = true
        refresh()
        mainHandler.removeCallbacks(statusTask)
        mainHandler.postDelayed(statusTask, STATUS_INTERVAL_MILLIS)
    }

    /** Stops previewing without removing the view (called from Activity.onStop). */
    fun pause() {
        running = false
        mainHandler.removeCallbacks(statusTask)
        dropObserver()
        // Force a fresh subscription when we come back: another page may have released the screen.
        session = null
        binding.preview.attach(null)
        lastFrameCount = 0L
    }

    /** Removes the view and all subscriptions (called from Activity.onDestroy). */
    fun detach() {
        pause()
        if (attached) {
            runCatching { container?.removeView(root) }
            attached = false
        }
    }

    // ---- UI ----

    private fun bindViews() {
        // The mini window is display-only; the full viewer is where taps and gestures are injected.
        binding.preview.interactionEnabled = false
        binding.frame.setOnClickListener { openViewer() }
        binding.btnOpen.setOnClickListener { openViewer() }
        binding.btnCollapse.setOnClickListener { setCollapsed(true) }
        binding.bubble.setOnClickListener { setCollapsed(false) }
        binding.tvTitle.setOnClickListener { openViewer() }
        binding.tvTitle.setOnTouchListener(dragListener)
    }

    private val dragListener = View.OnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                dragOriginX = root.translationX
                dragOriginY = root.translationY
                dragging = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragStartX
                val dy = event.rawY - dragStartY
                if (!dragging && (abs(dx) > TOUCH_SLOP_PX * density || abs(dy) > TOUCH_SLOP_PX * density)) {
                    dragging = true
                }
                if (dragging) {
                    val maxLeft = -((container?.width ?: root.width) - root.width).coerceAtLeast(0).toFloat()
                    val maxTop = -((container?.height ?: root.height) - root.height).coerceAtLeast(0).toFloat()
                    root.translationX = (dragOriginX + dx).coerceIn(maxLeft, 0f)
                    root.translationY = (dragOriginY + dy).coerceIn(maxTop, 0f)
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) view.performClick()
                dragging = false
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                true
            }
            else -> false
        }
    }

    private fun setCollapsed(value: Boolean) {
        collapsed = value
        applyCollapsed()
    }

    private fun applyCollapsed() {
        binding.card.visibility = if (collapsed) View.GONE else View.VISIBLE
        binding.bubble.visibility = if (collapsed) View.VISIBLE else View.GONE
    }

    private fun openViewer() {
        val id = session?.takeIf { !it.isReleased() }?.getDisplayId() ?: 0
        runCatching { VirtualScreenViewerActivity.launch(activity, id) }
    }

    // ---- Session & frames ----

    private fun refresh() {
        if (!attached) return
        val enabled = runCatching { Pref.getBoolean(PREF_FLOATING_WINDOW, true) }.getOrDefault(true)
        if (!enabled) {
            root.visibility = View.GONE
            dropObserver()
            return
        }
        root.visibility = View.VISIBLE
        val candidate = currentSession()
        if (session?.isReleased() == true || candidate?.getDisplayId() != session?.getDisplayId()) {
            switchSession(candidate)
        }
        // Retry a pump that could not start, and re-subscribe after a pause.
        if (session != null && previewToken == null) {
            previewToken = runCatching { session?.addPreviewObserver(::onFrame) }.getOrNull()
        }
        updateLabels()
    }

    private fun currentSession(): VirtualScreenSession? {
        session?.takeIf { !it.isReleased() }?.let { return it }
        return runCatching { VirtualScreenManager.liveSessions().firstOrNull() }.getOrNull()
    }

    private fun switchSession(target: VirtualScreenSession?) {
        dropObserver()
        session = target
        binding.preview.attach(target)
        lastFrameCount = 0L
        val heightDp = target?.let { FloatingWindowGeometry.previewHeightDp(it.getWidth(), it.getHeight()) }
            ?: FloatingWindowGeometry.DEFAULT_PREVIEW_HEIGHT_DP
        val heightPx = dp(heightDp)
        val params = binding.frame.layoutParams
        if (params != null && params.height != heightPx) {
            params.height = heightPx
            binding.frame.layoutParams = params
        }
    }

    private fun dropObserver() {
        session?.removePreviewObserver(previewToken)
        previewToken = null
    }

    /** Called on the frame pump's background thread; coalesce posts so a slow UI cannot pile up. */
    private fun onFrame() {
        if (!framePending.compareAndSet(false, true)) return
        mainHandler.post {
            framePending.set(false)
            if (running && attached && !collapsed) binding.preview.renderFrame()
        }
    }

    private fun updateLabels() {
        val live = session?.takeIf { !it.isReleased() }
        if (live == null) {
            binding.tvTitle.text = activity.getString(R.string.text_vs_float_idle)
            binding.tvHint.text = activity.getString(R.string.text_vs_float_hint_idle)
            binding.tvHint.visibility = View.VISIBLE
            return
        }
        val frames = live.previewFrameCount()
        val fps = (frames - lastFrameCount).coerceAtLeast(0).toInt()
        lastFrameCount = frames
        val hasFrame = live.previewBitmap() != null
        binding.tvTitle.text = if (hasFrame) {
            activity.getString(
                R.string.text_vs_float_status,
                live.getDisplayId(), live.getWidth(), live.getHeight(), fps,
            )
        } else {
            activity.getString(R.string.text_vs_float_waiting, live.getDisplayId())
        }
        binding.tvHint.visibility = if (hasFrame) View.GONE else View.VISIBLE
        binding.tvHint.text = live.previewError()
            ?.let { activity.getString(R.string.text_vs_float_frame_error, it) }
            ?: activity.getString(R.string.text_vs_float_hint_waiting)
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {

        /** Setting key of the home-screen mini preview switch (see fragment_preferences.xml). */
        const val PREF_FLOATING_WINDOW = "virtual_screen_floating_window"

        private const val STATUS_INTERVAL_MILLIS = 1_000L
        private const val END_MARGIN_DP = 16
        /** Above the 56dp FAB + its 16dp margin + 16dp gap. zh-CN: 避开右下角主 FAB. */
        private const val BOTTOM_MARGIN_DP = 88
        private const val TOUCH_SLOP_PX = 8f
    }

}
