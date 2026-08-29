package org.autojs.autojs.core.plugin.center

import kotlin.math.min

/**
 * Spreads the first viewport's structural list growth across animation frames.
 *
 * RecyclerView creates every initially visible holder in one layout pass. Plugin Center
 * rows are deliberately information-dense, so publishing a cold list all at once can
 * monopolize the UI thread while a page or Activity transition is still moving. This
 * submitter grows the list one row per frame until enough rows have been presented to
 * cover a normal viewport. Later snapshots are then submitted normally.
 *
 * Small lists remain in the guarded mode. If a later snapshot grows them, only the newly
 * visible rows are introduced per frame; same-size content refreshes still use one diff.
 *
 * zh-CN: 将首个视口的结构性列表增长分散到连续动画帧中.
 *
 * RecyclerView 会在一次布局中创建首屏全部 ViewHolder. 插件中心条目信息密度较高,
 * 若冷启动列表一次性发布, 页面或 Activity 动画期间可能长时间占用 UI 线程.
 * 此提交器逐帧增加一行, 直到已展示的行数足以覆盖常规视口, 后续快照再正常提交.
 *
 * 短列表会继续处于保护模式. 若后续快照增长, 仅逐帧引入新出现的行;
 * 条目数量不变的内容刷新仍只执行一次差异更新.
 */
internal class PluginCenterFrameChunkedListSubmitter<T>(
    private val submitList: (List<T>, (() -> Unit)?) -> Unit,
    private val postNextFrame: (Runnable) -> Unit,
    private val removeFrameCallback: (Runnable) -> Unit,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    private val guardedItemCount: Int = DEFAULT_GUARDED_ITEM_COUNT,
) {

    private var targetItems: List<T> = emptyList()
    private var targetVersion = 0L
    private var submittedCount = 0
    private var submissionInFlight = false
    private var framePosted = false
    private var guardCompleted = false
    private var cancelled = false

    private val nextFrameCallback = Runnable {
        framePosted = false
        publishNextChunk()
    }

    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
        require(guardedItemCount > 0) { "guardedItemCount must be positive" }
    }

    fun submit(items: List<T>) {
        if (cancelled) return

        targetItems = items
        targetVersion += 1

        if (guardCompleted) {
            submitList(items, null)
            return
        }

        if (!submissionInFlight && !framePosted) {
            publishNextChunk()
        }
    }

    fun cancel() {
        if (cancelled) return
        cancelled = true
        if (framePosted) {
            removeFrameCallback(nextFrameCallback)
            framePosted = false
        }
    }

    private fun publishNextChunk() {
        if (cancelled || guardCompleted || submissionInFlight) return

        val items = targetItems
        if (items.isEmpty()) {
            submittedCount = 0
            submitList(emptyList(), null)
            return
        }

        val submittedVersion = targetVersion
        val nextCount = min(items.size, submittedCount + chunkSize)
        val nextItems = items.take(nextCount)
        submissionInFlight = true
        submitList(nextItems) {
            if (cancelled) return@submitList

            submissionInFlight = false
            submittedCount = nextCount

            if (submittedCount >= guardedItemCount) {
                guardCompleted = true
                if (submittedVersion != targetVersion || submittedCount != targetItems.size) {
                    submitList(targetItems, null)
                }
                return@submitList
            }

            val targetChanged = submittedVersion != targetVersion
            val hasMoreItems = submittedCount < targetItems.size
            val hasTooManyItems = submittedCount > targetItems.size
            if (targetChanged || hasMoreItems || hasTooManyItems) {
                postFrame()
            }
        }
    }

    private fun postFrame() {
        if (cancelled || framePosted) return
        framePosted = true
        postNextFrame(nextFrameCallback)
    }

    private companion object {
        const val DEFAULT_CHUNK_SIZE = 1
        const val DEFAULT_GUARDED_ITEM_COUNT = 8
    }
}
