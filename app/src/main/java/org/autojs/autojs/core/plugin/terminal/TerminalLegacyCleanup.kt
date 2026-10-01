package org.autojs.autojs.core.plugin.terminal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.autojs.autojs.core.pref.Pref
import java.io.File
import java.util.concurrent.Executors

/**
 * One-time cleanup of the data the built-in terminal left behind before it moved into the
 * 3-Shell Terminal plugin (plugin roadmap D28): the `<filesDir>/terminal` tree (home, usr with the
 * extracted Node.js toolchain and global npm packages) and the `key_$_terminal_*` preferences plus
 * the terminal tips dialog flag. Idempotent and without a marker: an `exists()` check and a key
 * scan cost nothing, so every start may run it. Failures are logged, never thrown. Nothing is
 * migrated into the plugin (D11); the changelog hint tells users to reinstall global packages there.
 */
object TerminalLegacyCleanup {

    private const val TAG = "TerminalLegacyCleanup"

    /** `TerminalPaths.ROOT_DIR_NAME` of the removed host terminal. */
    const val LEGACY_ROOT_DIR_NAME = "terminal"

    /** Prefix of every `key_terminal_*` preference the host terminal and its settings page wrote. */
    const val LEGACY_KEY_PREFIX = "key_\$_terminal_"

    /** The "do not show the terminal tips again" flag (`key_dialog_terminal_tips`). */
    const val LEGACY_TIPS_KEY = "key_\$_dialog_terminal_tips"

    data class Result(val directoryExisted: Boolean, val directoryDeleted: Boolean, val removedKeys: List<String>) {
        val changedAnything: Boolean get() = directoryExisted || removedKeys.isNotEmpty()
    }

    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "AutoJs6-TerminalLegacyCleanup") }

    fun isLegacyKey(key: String): Boolean = key.startsWith(LEGACY_KEY_PREFIX) || key == LEGACY_TIPS_KEY

    /** The subset of [keys] the cleanup removes, sorted for stable logs and tests. */
    fun selectLegacyKeys(keys: Collection<String>): List<String> = keys.filter(::isLegacyKey).sorted()

    /** Runs the cleanup on a background thread; the host calls it once per process start. */
    fun schedule(context: Context) {
        val applicationContext = context.applicationContext
        executor.execute {
            runCatching { run(applicationContext.filesDir, Pref.get()) }
                .onSuccess { result -> if (result.changedAnything) Log.i(TAG, "removed legacy terminal data: $result") }
                .onFailure { error -> Log.w(TAG, "legacy terminal cleanup failed", error) }
        }
    }

    /** Pure core: deletes `<filesDir>/terminal` when present and removes the legacy keys of [preferences]. */
    fun run(filesDir: File, preferences: SharedPreferences): Result {
        val root = File(filesDir, LEGACY_ROOT_DIR_NAME)
        val existed = root.exists()
        val deleted = existed && (root.deleteRecursively() || !root.exists())
        if (existed && !deleted) Log.w(TAG, "could not delete ${root.path} completely")
        val keys = selectLegacyKeys(preferences.all.keys)
        if (keys.isNotEmpty()) {
            val editor = preferences.edit()
            keys.forEach(editor::remove)
            editor.apply()
        }
        return Result(existed, deleted, keys)
    }
}
