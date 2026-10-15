package org.autojs.autojs.external.shortcut

import android.app.UiAutomation
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.graphics.drawable.IconCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.shortcut.ShortcutBackupActivity
import org.autojs.autojs.util.ShortcutUtils
import org.autojs.autojs6.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Opt-in launcher integration fixture for a disposable, English-language emulator:
 * -e shortcutBackupSandbox true -e class ...ShortcutBackupDeviceTest
 * Leaves two pinned copies and an exported archive for disable/enable and reinstall checks.
 * Never run on a user's home screen. Ordinary instrumentation runs skip this fixture.
 */
class ShortcutBackupDeviceTest {
    @Test fun pinsDistinctCopiesAndExportsAReusableArchive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("shortcutBackupSandbox") == "true")
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val context = instrumentation.targetContext
        val repository = ShortcutBackupRepository.get(context)
        assertTrue("Use a fresh disposable app installation", repository.read().entries.isEmpty())
        val activity = instrumentation.startActivitySync(Intent(context, ShortcutBackupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        try {
            val script = File(context.filesDir, "shortcut-backup-fixture.js").apply { writeText("toast('Shortcut backup test');") }
            listOf("backup-copy-one", "backup-copy-two").forEachIndexed { index, id ->
                val icon = if (index == 0) IconCompat.createWithResource(context, R.drawable.ic_file_type_js_dark_green)
                else IconCompat.createWithBitmap(Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) })
                ShortcutUtils.requestPinShortcut(context, id,
                    ScriptShortcutIntentFactory.create(script.path).setClass(context, ShortcutActivity::class.java).setAction(Intent.ACTION_MAIN),
                    "Backup copy ${index + 1}", "Backup copy ${index + 1}",
                    icon)
                await("Launcher confirmation") {
                    val root = automation.rootInActiveWindow ?: return@await false
                    root.packageName != context.packageName && clickAdd(root)
                }
                await("Pinned shortcut callback: $id") {
                    repository.read().entries.any { it.id == id && it.status == ShortcutBackupEntry.Status.PINNED }
                }
                instrumentation.waitForIdleSync()
            }
            val manager = context.getSystemService(ShortcutManager::class.java)
            assertTrue("Recovery marker must be present", manager.dynamicShortcuts.any { it.id == "autojs6.shortcut.backup.marker.v1" })
            assertEquals(2, repository.reconcile().entries.count { it.status == ShortcutBackupEntry.Status.PINNED })
            val bytes = ByteArrayOutputStream().also(repository::export).toByteArray()
            File(context.filesDir, "shortcut-backup-export.zip").writeBytes(bytes)
            val archive = ShortcutBackupArchive.read(bytes.inputStream())
            assertEquals(2, archive.entries.size)
            assertFalse("Each copy must retain its own icon", archive.entries[0].icon.contentEquals(archive.entries[1].icon))
            val customIcon = archive.entries[1].icon.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            assertEquals("Non-square icons must keep their aspect ratio", 2, customIcon.width / customIcon.height)
            customIcon.recycle()
            assertNull(archive.markerToken)
            assertTrue(archive.entries.all { it.requestToken == null })
            repository.import(bytes.inputStream())
            assertEquals("Reimport must not duplicate instances", 2, repository.read().entries.size)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun clickAdd(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase().orEmpty()
        if (node.isClickable && (text == "add automatically" || text == "add to home screen" || text == "add")) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickAdd(child)) return true
        }
        return false
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
