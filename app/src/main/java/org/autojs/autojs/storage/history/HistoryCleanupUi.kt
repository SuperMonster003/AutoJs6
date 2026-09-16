package org.autojs.autojs.storage.history

import android.content.Context
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Clean up now" for the trash and version history pages: runs every cleanup pass on an I/O
 * thread, then reports on the main thread and lets the page reload.
 * zh-CN: 回收站与版本历史页面的 "立即清理": 在 I/O 线程执行全部清理过程, 然后在主线程提示并让页面重新加载.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object HistoryCleanupUi {

    private val running = AtomicBoolean(false)

    /**
     * @param onFinished invoked on the main thread after the run, whether it succeeded or failed.
     */
    fun runNow(context: Context, onFinished: () -> Unit = {}) {
        val appContext = context.applicationContext
        if (!running.compareAndSet(false, true)) {
            ViewUtils.showToast(appContext, R.string.text_cleaning_in_progress)
            return
        }
        Schedulers.io().scheduleDirect {
            val result = runCatching { HistoryCleanup.runAll(appContext) }
            running.set(false)
            AndroidSchedulers.mainThread().scheduleDirect {
                result.fold(
                    onSuccess = { ViewUtils.showToast(appContext, R.string.text_clean_completed) },
                    onFailure = { e ->
                        e.printStackTrace()
                        ViewUtils.showToast(appContext, R.string.text_clean_up_failed)
                    },
                )
                onFinished()
            }
        }
    }
}
