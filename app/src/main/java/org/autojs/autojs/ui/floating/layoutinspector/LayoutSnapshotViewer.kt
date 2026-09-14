package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.core.accessibility.LayoutSnapshotFiles
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import kotlin.concurrent.thread

/**
 * Opens a saved layout file in the layout bounds window. The window is marked offline, so the
 * actions that need a live capture (switching windows, saving again) are left out of its menu.
 * zh-CN: 在布局范围窗口中打开已保存的布局文件. 该窗口标记为离线, 需要实时捕获的动作 (切换窗口, 再次保存) 不会出现在菜单中.
 *
 * Created by SuperMonster003 on Sep 14, 2026.
 */
object LayoutSnapshotViewer {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun open(context: Context, path: String) {
        thread(name = "LayoutSnapshotViewer") {
            val result = runCatching { LayoutSnapshotFiles.load(File(path)) }
            mainHandler.post {
                result.onSuccess { root ->
                    val window = LayoutBoundsFloatyWindow(Capture(emptyList(), root), context, false)
                    window.isOffline = true
                    FloatyWindowManger.addWindow(context, window)
                }.onFailure { e ->
                    e.printStackTrace()
                    ViewUtils.showToast(context, R.string.text_failed_to_open_layout_file, true)
                }
            }
        }
    }

}
