package org.autojs.autojs.ui.shortcut

import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.external.shortcut.ShortcutBackupRepository
import org.autojs.autojs.ui.BaseActivity

/**
 * Keeps the shortcut backup in step with the launcher whenever the main page resumes: pinned shortcuts are adopted and
 * saved ones that disappeared are marked. It never shows a dialog (maintainer decision of 2026-10-16): restorable
 * shortcuts and icons still to choose wait on the shortcut backup page.
 * zh-CN: 主页面恢复显示时使快捷方式备份与启动器保持一致: 纳入已固定的快捷方式, 标记消失的已存档快捷方式. 从不弹出对话框 (维护者
 * 2026-10-16 的决定): 可恢复的快捷方式与待补选的图标都在快捷方式备份页面中处理.
 */
internal class ShortcutBackupReconciler(private val activity: BaseActivity) {

    private var reconciling = false

    fun reconcile() {
        if (reconciling) return
        reconciling = true
        activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { ShortcutBackupRepository.get(activity).reconcile() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A locked profile, unavailable launcher or corrupt archive is never evidence
                // that the user deleted a shortcut. Keep the previous state for a later retry.
                Log.w(ShortcutBackupRepository.TAG, "Cannot reconcile the shortcut backup", error)
            } finally {
                reconciling = false
            }
        }
    }
}
