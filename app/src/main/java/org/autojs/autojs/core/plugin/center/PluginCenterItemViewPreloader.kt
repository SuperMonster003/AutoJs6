package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.view.View
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs6.R

/**
 * Inflates the cold Plugin Center viewport away from the UI thread.
 *
 * [AsyncLayoutInflater] performs view construction on its worker and returns completed,
 * unattached rows on the main thread. RecyclerView can then create its first holders from
 * those rows without running recursive XML inflation inside a transition frame.
 *
 * zh-CN: 在 UI 线程之外预先 inflate 插件中心的冷启动视口.
 *
 * [AsyncLayoutInflater] 在工作线程构造 View, 再将已完成且尚未挂载的条目返回主线程.
 * RecyclerView 随后可直接使用这些条目创建首批 ViewHolder, 避免在过渡动画帧内执行
 * 递归 XML inflate.
 */
internal class PluginCenterItemViewPreloader(
    context: Context,
    private val parent: RecyclerView,
    private val onItemViewReady: (View) -> Unit,
    private val onViewportReady: () -> Unit,
    private val preloadItemCount: Int = DEFAULT_PRELOAD_ITEM_COUNT,
    private val viewportReadyItemCount: Int = DEFAULT_VIEWPORT_READY_ITEM_COUNT,
) {

    private val inflater = AsyncLayoutInflater(context)
    private var started = false
    private var active = true
    private var completedItemCount = 0

    init {
        require(preloadItemCount > 0) { "preloadItemCount must be positive" }
        require(viewportReadyItemCount in 1..preloadItemCount) {
            "viewportReadyItemCount must be within the preload range"
        }
    }

    fun start() {
        if (started || !active) return
        started = true
        repeat(preloadItemCount) {
            inflater.inflate(R.layout.plugin_center_recycler_view_item, parent) { view, _, _ ->
                if (!active) return@inflate
                onItemViewReady(view)
                completedItemCount += 1
                if (completedItemCount == viewportReadyItemCount) {
                    onViewportReady()
                }
            }
        }
    }

    fun cancel() {
        active = false
    }

    private companion object {
        const val DEFAULT_PRELOAD_ITEM_COUNT = 8
        const val DEFAULT_VIEWPORT_READY_ITEM_COUNT = 6
    }
}
