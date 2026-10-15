package org.autojs.autojs.external.shortcut

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.os.UserManager
import android.util.AtomicFile
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.ui.doc.DocumentationActivity
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.autojs.autojs.ui.shortcut.ShortcutBackupActivity
import org.autojs.autojs6.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** All callers perform disk/Binder work off the UI thread. Mutations are serialized. */
internal class ShortcutBackupRepository private constructor(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "shortcut-backup.zip"))
    private var cached: ShortcutBackupState? = null
    val changes = MutableStateFlow(0L)

    @Synchronized
    fun read(): ShortcutBackupState = cached ?: (if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
        file.openRead().use(ShortcutBackupArchive::read)
    } else ShortcutBackupState()).also { cached = it }

    private fun save(state: ShortcutBackupState) {
        val output = file.startWrite()
        try {
            ShortcutBackupArchive.write(state, output)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
        cached = state
        changes.value += 1
    }

    @Synchronized
    fun reconcile(): ShortcutBackupState {
        var state = read()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || state.entries.isEmpty()) return state
        check(context.getSystemService(UserManager::class.java).isUserUnlocked) { "Device is locked" }
        val manager = context.getSystemService(ShortcutManager::class.java)
        val pinned = manager.pinnedShortcuts.mapTo(hashSetOf()) { it.id }
        val marker = manager.dynamicShortcuts.firstOrNull { it.id == MARKER_ID }
        val home = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.let { "${it.packageName}/${it.name}" }
        val activity = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        val owner = activity?.flattenToString()
        val markerPresent = marker?.extras?.getString("generation") == state.markerToken && state.markerToken != null
        val comparable = home != null && owner != null && state.launcher == home && state.activity == owner &&
            (marker == null || markerPresent)
        state = ShortcutBackupPolicy.reconcile(state, pinned, markerPresent, comparable)

        if (!markerPresent || state.activity != owner) {
            // Never evict another app shortcut just to install our marker. A device without
            // a spare dynamic slot still supports manual backup/restore, without guessing.
            val generation = UUID.randomUUID().toString()
            val armed = activity != null && runCatching {
                manager.addDynamicShortcuts(listOf(ShortcutInfo.Builder(context, MARKER_ID)
                    .setActivity(activity)
                    .setShortLabel(context.getString(R.string.shortcut_backup_title))
                    .setIntent(Intent(context, ShortcutBackupActivity::class.java).setAction(Intent.ACTION_VIEW))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_settings_backup_restore_black_48dp))
                    .setExtras(PersistableBundle().apply { putString("generation", generation) })
                    .setRank(4)
                    .build()))
            }.onFailure { Log.w(TAG, "Cannot publish shortcut recovery marker", it) }.getOrDefault(false)
            state = state.copy(markerToken = generation.takeIf { armed })
        }
        state = state.copy(launcher = home, activity = owner)
        if (state != read()) save(state)
        return state
    }

    @Synchronized
    fun capture(id: String, intent: Intent, shortLabel: CharSequence, longLabel: CharSequence, icon: IconCompat?): ShortcutBackupEntry {
        require(id != MARKER_ID)
        val target = targetFor(intent.component?.className) ?: error("Unsupported shortcut target")
        val drawable = requireNotNull(icon?.loadDrawable(context)) { "Shortcut icon is missing" }
        val bounds = Rect(drawable.bounds)
        // Keep the original aspect ratio: custom icons need not be square. Bounding the
        // longest edge also keeps the PNG comfortably below the archive's per-icon limit.
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 192
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 192
        val scale = (384.0 / max(width, height)).coerceAtMost(1.0)
        val bitmap = Bitmap.createBitmap(
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        try {
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
        } finally {
            drawable.bounds = bounds
        }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val entry = ShortcutBackupEntry(
            id = id,
            shortLabel = shortLabel.toString(),
            longLabel = longLabel.toString(),
            target = target,
            path = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PATH),
            sourceKind = intent.getStringExtra(ScriptIntents.EXTRA_KEY_SOURCE_KIND),
            projectRoot = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PROJECT_ROOT),
            icon = png,
        )
        val state = reconcile()
        save(state.copy(entries = state.entries.filterNot { it.id == id } + entry))
        // The first creation also needs a marker before the user can remove its desktop icon.
        reconcile()
        return entry
    }

    @Synchronized
    fun requestPin(id: String): Boolean {
        val entry = read().entries.first { it.id == id }
        val intent = intentFor(entry)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(entry.icon, 0, entry.icon.size)) { "Invalid shortcut icon" }
        val info = ShortcutInfoCompat.Builder(context, id)
            .setIntent(intent)
            .setShortLabel(entry.shortLabel)
            .setLongLabel(entry.longLabel)
            .setIcon(IconCompat.createWithBitmap(bitmap))
            .setAlwaysBadged()
            .build()
        val token = UUID.randomUUID().toString()
        val callback = PendingIntent.getBroadcast(context, 0,
            Intent(context, ShortcutPinnedReceiver::class.java)
                .setData(Uri.Builder().scheme("autojs6-shortcut").authority("pinned").appendPath(token).build())
                .putExtra("id", id).putExtra("token", token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        update(id) { it.copy(status = ShortcutBackupEntry.Status.PENDING, requestToken = token, notified = false) }
        val supported = ShortcutManagerCompat.requestPinShortcut(context, info, callback.intentSender)
        if (!supported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            update(id) { it.copy(status = ShortcutBackupEntry.Status.SAVED, requestToken = null) }
        }
        return supported
    }

    @Synchronized
    fun confirmPinned(id: String, token: String) {
        update(id) {
            if (it.requestToken != token) it else it.copy(
                status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ShortcutBackupEntry.Status.PINNED else ShortcutBackupEntry.Status.SAVED,
                requestToken = null, notified = false,
            )
        }
    }

    @Synchronized
    fun markNotified(ids: Set<String>) = transform(ids) { it.copy(notified = true) }

    @Synchronized
    fun ignore(ids: Set<String>) = transform(ids) { it.copy(status = ShortcutBackupEntry.Status.REMOVED, requestToken = null, notified = true) }

    private fun update(id: String, change: (ShortcutBackupEntry) -> ShortcutBackupEntry) = transform(setOf(id), change)

    private fun transform(ids: Set<String>, change: (ShortcutBackupEntry) -> ShortcutBackupEntry) {
        val state = read()
        val updated = state.copy(entries = state.entries.map { if (it.id in ids) change(it) else it })
        if (updated != state) save(updated)
    }

    fun export(output: OutputStream) {
        val state = reconcile()
        // Device-local marker generations and in-flight callback tokens are never exported.
        ShortcutBackupArchive.write(ShortcutBackupState(entries = state.entries.map { it.copy(requestToken = null, notified = false) }), output)
    }

    fun import(input: InputStream): List<String> {
        val incoming = ShortcutBackupArchive.read(input)
        incoming.entries.forEach {
            require(it.id != MARKER_ID)
            // Decode before modifying any local state, in addition to the archive bounds checks.
            requireNotNull(BitmapFactory.decodeByteArray(it.icon, 0, it.icon.size)) { "Invalid shortcut icon" }.recycle()
        }
        synchronized(this) {
            val state = ShortcutBackupPolicy.merge(reconcile(), incoming.entries)
            save(state)
            reconcile()
        }
        return incoming.entries.map { it.id }
    }

    fun intentFor(entry: ShortcutBackupEntry): Intent = when (entry.target) {
        "script" -> ScriptShortcutIntentFactory.create(requireNotNull(entry.path)).apply {
            setClass(context, ShortcutActivity::class.java)
            action = Intent.ACTION_MAIN
            entry.sourceKind?.let { putExtra(ScriptIntents.EXTRA_KEY_SOURCE_KIND, it) }
            entry.projectRoot?.let { putExtra(ScriptIntents.EXTRA_KEY_PROJECT_ROOT, it) }
        }
        else -> Intent(Intent.ACTION_VIEW).setClass(context, when (entry.target) {
            "settings" -> PreferencesActivity::class.java
            "docs" -> DocumentationActivity::class.java
            "log" -> LogActivity::class.java
            "plugins" -> PluginCenterActivity::class.java
            else -> error("Unsupported shortcut target")
        })
    }

    companion object {
        const val TAG = "ShortcutBackup"
        private const val MARKER_ID = "autojs6.shortcut.backup.marker.v1"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile private var instance: ShortcutBackupRepository? = null

        fun get(context: Context): ShortcutBackupRepository = instance ?: synchronized(this) {
            instance ?: ShortcutBackupRepository(context.applicationContext).also { instance = it }
        }

        private fun targetFor(className: String?): String? = when (className) {
            ShortcutActivity::class.java.name -> "script"
            PreferencesActivity::class.java.name -> "settings"
            DocumentationActivity::class.java.name -> "docs"
            LogActivity::class.java.name -> "log"
            PluginCenterActivity::class.java.name -> "plugins"
            else -> null
        }
    }
}

/** An explicit, immutable PendingIntent is the only way to reach this private receiver. */
class ShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val token = intent.getStringExtra("token") ?: return
        val pending = goAsync()
        ShortcutBackupRepository.scope.launch {
            try {
                ShortcutBackupRepository.get(context).confirmPinned(id, token)
            } catch (error: Exception) {
                Log.e(ShortcutBackupRepository.TAG, "Cannot record pinned shortcut", error)
            } finally {
                pending.finish()
            }
        }
    }
}
