package org.autojs.autojs.external.shortcut

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.os.UserManager
import android.util.AtomicFile
import android.util.Log
import androidx.annotation.RequiresApi
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return state
        check(context.getSystemService(UserManager::class.java).isUserUnlocked) { "Device is locked" }
        val manager = context.getSystemService(ShortcutManager::class.java)
        val pinnedShortcuts = manager.pinnedShortcuts
        val known = state.entries.mapTo(hashSetOf()) { it.id }
        val discovered = pinnedShortcuts.asSequence().filter { it.id !in known && it.id != MARKER_ID }
            .mapNotNull(::adoptExisting)
            .take((ShortcutBackupArchive.MAX_ENTRIES - known.size).coerceAtLeast(0))
            .toList()
        state = ShortcutBackupPolicy.adoptPinned(state, discovered)
        if (state.entries.isEmpty()) return state
        val pinned = pinnedShortcuts.mapTo(hashSetOf()) { it.id }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            state = ShortcutBackupPolicy.reconcile(state, pinned, markerPresent = false, comparable = false)
            if (state != read()) save(state)
            return state
        }
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
        val entry = ShortcutBackupEntry(
            id = id,
            shortLabel = shortLabel.toString(),
            longLabel = longLabel.toString(),
            target = target,
            path = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PATH),
            sourceKind = intent.getStringExtra(ScriptIntents.EXTRA_KEY_SOURCE_KIND),
            projectRoot = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PROJECT_ROOT),
            icon = encodeIcon(drawable),
        )
        val state = reconcile()
        save(state.copy(entries = state.entries.filterNot { it.id == id } + entry))
        // The first creation also needs a marker before the user can remove its desktop icon.
        reconcile()
        return entry
    }

    /** Only adopt launch shapes understood by the restore path; never persist arbitrary intents. */
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun adoptExisting(shortcut: ShortcutInfo): ShortcutBackupEntry? = runCatching {
        val intent = shortcut.intent ?: return@runCatching null
        if (intent.component?.packageName != context.packageName || intent.data != null ||
            intent.hasExtra(ScriptIntents.EXTRA_KEY_JSON) || intent.hasExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT)) return@runCatching null
        val target = targetFor(intent.component?.className) ?: return@runCatching null
        val label = shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val entry = ShortcutBackupEntry(
            id = shortcut.id,
            shortLabel = label,
            longLabel = shortcut.longLabel?.toString()?.takeIf { it.isNotBlank() } ?: label,
            target = target,
            path = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PATH),
            sourceKind = intent.getStringExtra(ScriptIntents.EXTRA_KEY_SOURCE_KIND),
            projectRoot = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PROJECT_ROOT),
            icon = readExistingIcon(shortcut, target),
            status = ShortcutBackupEntry.Status.PINNED,
        )
        ShortcutBackupArchive.validate(entry)
        entry
    }.onFailure { Log.w(TAG, "Cannot archive existing shortcut ${shortcut.id}", it) }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun readExistingIcon(shortcut: ShortcutInfo, target: String): ByteArray? {
        // getPinnedShortcuts() deliberately strips icons. Do not guess that an old script
        // used the default image: its custom bitmap may only remain in the launcher's store.
        val drawable = runCatching {
            context.getSystemService(LauncherApps::class.java)?.takeIf { it.hasShortcutHostPermission() }
                ?.getShortcutIconDrawable(shortcut, 0)
        }.getOrNull()
        if (drawable != null) return runCatching { encodeIcon(drawable) }.getOrNull()
        return if (shortcut.isDeclaredInManifest && target != "script") {
            runCatching { encodeIcon(defaultIcon(target)) }.getOrNull()
        } else null
    }

    private fun defaultIcon(target: String): Drawable = requireNotNull(context.getDrawable(when (target) {
        "settings" -> if (Build.VERSION.SDK_INT >= 33) R.drawable.ic_app_shortcut_settings_green else R.mipmap.ic_app_shortcut_settings_adaptive
        "docs" -> if (Build.VERSION.SDK_INT >= 33) R.drawable.ic_app_shortcut_docs_green else R.mipmap.ic_app_shortcut_docs_adaptive
        "log" -> if (Build.VERSION.SDK_INT >= 33) R.drawable.ic_app_shortcut_log_green else R.mipmap.ic_app_shortcut_log_adaptive
        "plugins" -> if (Build.VERSION.SDK_INT >= 33) R.drawable.ic_app_shortcut_plugin_center_green else R.mipmap.ic_app_shortcut_plugin_center_adaptive
        else -> R.drawable.ic_file_type_js_dark_green
    }))

    @Synchronized
    fun replaceIcon(id: String, drawable: Drawable) {
        val icon = encodeIcon(drawable)
        update(id) { it.copy(icon = icon, iconNoticeShown = true) }
    }

    @Synchronized
    fun useDefaultIcon(id: String) {
        val entry = read().entries.first { it.id == id }
        replaceIcon(id, defaultIcon(entry.target))
    }

    @Synchronized
    fun useDefaultIconsForMissing(): Int {
        // Read under the same lock as replaceIcon/import, never from a stale UI selection.
        val state = read()
        val count = state.entries.count { it.icon == null }
        if (count == 0) return 0
        val icons = mutableMapOf<String, ByteArray>()
        val completed = ShortcutBackupPolicy.fillMissingIcons(state) { target ->
            icons.getOrPut(target) { encodeIcon(defaultIcon(target)) }
        }
        // One atomic write: an encoding or storage failure must not leave a partial batch.
        save(completed)
        return count
    }

    private fun encodeIcon(drawable: Drawable): ByteArray {
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
        return png
    }

    @Synchronized
    fun requestPin(id: String): Boolean {
        val entry = read().entries.first { it.id == id }
        val intent = intentFor(entry)
        val icon = requireNotNull(entry.icon) { context.getString(R.string.shortcut_backup_icon_required) }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(icon, 0, icon.size)) { "Invalid shortcut icon" }
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
    fun markIconsNotified(ids: Set<String>) = transform(ids) { it.copy(iconNoticeShown = true) }

    @Synchronized
    fun ignore(ids: Set<String>) = transform(ids) { it.copy(status = ShortcutBackupEntry.Status.REMOVED, requestToken = null, notified = true) }

    private fun update(id: String, change: (ShortcutBackupEntry) -> ShortcutBackupEntry) = transform(setOf(id), change)

    private fun transform(ids: Set<String>, change: (ShortcutBackupEntry) -> ShortcutBackupEntry) {
        val state = read()
        val updated = state.copy(entries = state.entries.map { if (it.id in ids) change(it) else it })
        if (updated != state) save(updated)
    }

    fun export(output: OutputStream) = ShortcutBackupArchive.writeExport(reconcile(), output)

    fun import(input: InputStream): List<String> {
        val incoming = ShortcutBackupArchive.read(input)
        incoming.entries.forEach {
            require(it.id != MARKER_ID)
            // Decode before modifying any local state, in addition to the archive bounds checks.
            it.icon?.let { icon -> requireNotNull(BitmapFactory.decodeByteArray(icon, 0, icon.size)) { "Invalid shortcut icon" }.recycle() }
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
