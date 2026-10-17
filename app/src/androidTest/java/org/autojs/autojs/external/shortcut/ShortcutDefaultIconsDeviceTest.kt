package org.autojs.autojs.external.shortcut

import android.app.UiAutomation
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.shortcut.ShortcutBackupActivity
import org.autojs.autojs6.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Fresh disposable installation only: -e shortcutBackupSandbox true -e shortcutDefaultIcons true. */
class ShortcutDefaultIconsDeviceTest {
    @Test fun settingsBatchCompletesOnlyMissingIconsAndExportsEveryCopy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("shortcutBackupSandbox") == "true")
        assumeTrue(arguments.getString("shortcutDefaultIcons") == "true")
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val context = instrumentation.targetContext
        val repository = ShortcutBackupRepository.get(context)
        assertTrue("Use a fresh disposable installation", repository.read().entries.isEmpty())

        val savedBitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val savedIcon = ByteArrayOutputStream().also { savedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        savedBitmap.recycle()
        fun entry(id: String) = ShortcutBackupEntry(id, "Same script", "Same script", "script",
            "/sdcard/scripts/twins.js", icon = null, status = ShortcutBackupEntry.Status.SAVED)
        val imported = listOf(
            entry("saved").copy(icon = savedIcon),
            entry("chosen"),
            entry("twin-one"),
            entry("twin-two"),
            entry("removed").copy(status = ShortcutBackupEntry.Status.REMOVED),
            entry("settings").copy(target = "settings", path = null),
        )
        val archive = ByteArrayOutputStream().also { ShortcutBackupArchive.write(ShortcutBackupState(imported), it) }.toByteArray()
        repository.import(archive.inputStream())
        val chosenBitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        repository.replaceIcon("chosen", BitmapDrawable(context.resources, chosenBitmap))
        chosenBitmap.recycle()
        val before = repository.read()
        assertEquals(4, before.entries.count { it.icon == null })
        val manager = context.getSystemService(ShortcutManager::class.java)
        val pinnedBefore = manager.pinnedShortcuts.associate { it.id to it.lastChangedTimestamp }

        val activity = instrumentation.startActivitySync(Intent(context, ShortcutBackupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val title = context.getString(R.string.shortcut_backup_default_icons)
            await("batch default icon action") {
                val root = automation.rootInActiveWindow ?: return@await false
                find(root, title)?.let(::clickPreference) == true
            }
            await("all missing icons completed") { repository.read().entries.all { it.icon != null } }
            await("batch action disabled when complete") {
                automation.rootInActiveWindow?.let { find(it, title)?.isEnabled == false } == true
            }
            val completed = repository.read()
            assertEquals(before.entries.map { it.id }, completed.entries.map { it.id })
            before.entries.zip(completed.entries).forEach { (original, updated) ->
                if (original.icon != null) {
                    assertArrayEquals(original.icon, updated.icon)
                    assertEquals(original, updated)
                } else {
                    assertEquals(original.copy(icon = updated.icon, iconNoticeShown = true), updated)
                }
            }
            val twins = completed.entries.filter { it.id.startsWith("twin-") }
            assertEquals(2, twins.size)
            assertArrayEquals(twins[0].icon, twins[1].icon)
            assertFalse(requireNotNull(twins[0].icon).contentEquals(requireNotNull(completed.entries.last().icon)))
            assertEquals("Completing backup icons must not repin or rewrite desktop shortcuts", pinnedBefore,
                manager.pinnedShortcuts.associate { it.id to it.lastChangedTimestamp })
            val exported = ByteArrayOutputStream().also(repository::export).toByteArray()
            val restored = ShortcutBackupArchive.read(exported.inputStream())
            assertEquals(completed.entries.map { it.id }, restored.entries.map { it.id })
            completed.entries.zip(restored.entries).forEach { (original, restoredEntry) ->
                assertTrue(original.sameDefinition(restoredEntry))
                assertEquals(original.status, restoredEntry.status)
            }
            val revision = repository.changes.value
            assertEquals(0, repository.useDefaultIconsForMissing())
            assertEquals("An already complete batch must not rewrite the archive", revision, repository.changes.value)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun find(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == text) return node
        for (index in 0 until node.childCount) node.getChild(index)?.let { find(it, text) }?.let { return it }
        return null
    }

    private fun clickPreference(node: AccessibilityNodeInfo): Boolean {
        if (!node.isEnabled) return false
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return node.parent?.let(::clickPreference) == true
    }

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(200)
        }
        fail("Timed out: $description")
    }
}
