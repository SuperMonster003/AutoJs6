package org.autojs.autojs.ui.shortcut

import android.content.Intent
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.external.shortcut.ShortcutBackupEntry
import org.autojs.autojs.external.shortcut.ShortcutBackupRepository
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R

internal class ShortcutRecoveryPrompt(private val activity: BaseActivity) {
    private var checking = false
    private var dialog: MaterialDialog? = null

    fun check() {
        if (checking || dialog?.isShowing == true) return
        checking = true
        activity.lifecycleScope.launch {
            try {
                val repository = ShortcutBackupRepository.get(activity)
                val missing = withContext(Dispatchers.IO) {
                    repository.reconcile().entries.filter { it.status == ShortcutBackupEntry.Status.MISSING && !it.notified }
                }
                if (missing.isEmpty() || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                val ids = missing.mapTo(hashSetOf()) { it.id }
                dialog = MaterialDialog.Builder(activity)
                    .title(R.string.shortcut_backup_title)
                    .content(activity.getString(R.string.shortcut_backup_restore_prompt, missing.size))
                    .positiveText(R.string.dialog_button_restore)
                    .negativeText(R.string.shortcut_backup_later)
                    .neutralText(R.string.shortcut_backup_ignore)
                    .onPositive { _, _ -> activity.startActivity(Intent(activity, ShortcutBackupActivity::class.java)) }
                    .onNeutral { _, _ ->
                        ShortcutBackupRepository.scope.launch {
                            runCatching { repository.ignore(ids) }
                                .onFailure { Log.e(ShortcutBackupRepository.TAG, "Cannot dismiss recovery reminder", it) }
                        }
                    }
                    .show()
                withContext(Dispatchers.IO) { repository.markNotified(ids) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A locked profile, unavailable launcher or corrupt archive is never evidence
                // that the user deleted a shortcut. Keep the previous state for a later retry.
                Log.w(ShortcutBackupRepository.TAG, "Cannot check shortcut recovery", error)
            } finally {
                checking = false
            }
        }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
