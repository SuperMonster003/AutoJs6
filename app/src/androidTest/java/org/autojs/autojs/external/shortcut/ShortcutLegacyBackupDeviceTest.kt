package org.autojs.autojs.external.shortcut

import android.app.UiAutomation
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.shortcut.ShortcutBackupActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Disposable emulator only. Install the pre-migration APK and run with
 * -e shortcutBackupSandbox true -e legacyShortcutsPhase seed, then install the new
 * APK without clearing data and run with legacyShortcutsPhase verify.
 */
class ShortcutLegacyBackupDeviceTest {
    @Test fun adoptsOldCopiesAcrossAnActualAppUpgrade() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("shortcutBackupSandbox") == "true")
        val phase = arguments.getString("legacyShortcutsPhase")
        assumeTrue(phase == "seed" || phase == "verify")
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val context = instrumentation.targetContext
        val manager = context.getSystemService(ShortcutManager::class.java)
        val repository = ShortcutBackupRepository.get(context)
        val ids = listOf(100L, 200L).map { "${ShortcutActivity::class.java.name}\$Legacy twin@$it" }
        val activity = instrumentation.startActivitySync(Intent(context, ShortcutBackupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            if (phase == "seed") {
                assertTrue(repository.read().entries.isEmpty())
                val script = File(context.filesDir, "legacy-twin.js").apply { writeText("toast('Legacy twin');") }
                val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                ids.forEachIndexed { index, id ->
                    val icon = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(if (index == 0) Color.RED else Color.BLUE) }
                    val shortcut = ShortcutInfo.Builder(context, id)
                        .setShortLabel("Legacy twin")
                        .setLongLabel("Legacy twin")
                        .setIcon(Icon.createWithBitmap(icon))
                        .setIntent(ScriptShortcutIntentFactory.create(script.path)
                            .setClass(context, ShortcutActivity::class.java).setAction(Intent.ACTION_MAIN))
                        .build()
                    assertTrue(manager.requestPinShortcut(shortcut, null))
                    await("pin confirmation") {
                        val root = automation.rootInActiveWindow ?: return@await false
                        root.packageName != context.packageName && clickAdd(root)
                    }
                    await("system pin $id") { manager.pinnedShortcuts.any { it.id == id } }
                    instrumentation.waitForIdleSync()
                }
                assertTrue("Old version must leave these native shortcuts outside its archive", repository.read().entries.isEmpty())
                return
            }

            val adopted = repository.reconcile()
            assertEquals(ids.toSet(), adopted.entries.map { it.id }.toSet())
            assertEquals(1, adopted.entries.map { it.shortLabel }.toSet().size)
            assertEquals(1, adopted.entries.map { it.path }.toSet().size)
            assertTrue(adopted.entries.all { it.status == ShortcutBackupEntry.Status.PINNED && it.icon == null })
            assertEquals(adopted.entries, repository.reconcile().entries)
            val missingIcons = ByteArrayOutputStream().also(repository::export).toByteArray()
            assertTrue(ShortcutBackupArchive.read(missingIcons.inputStream()).entries.all { it.icon == null })

            val systemTimestamps = manager.pinnedShortcuts.associate { it.id to it.lastChangedTimestamp }
            val replacement = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            File(context.filesDir, "legacy-replacement.png").outputStream().use { replacement.compress(Bitmap.CompressFormat.PNG, 100, it) }
            repository.replaceIcon(ids[0], BitmapDrawable(context.resources, replacement))
            replacement.recycle()
            val chosen = requireNotNull(repository.read().entries.first { it.id == ids[0] }.icon)
            assertNull(repository.read().entries.first { it.id == ids[1] }.icon)
            repository.import(missingIcons.inputStream())
            assertArrayEquals(chosen, repository.read().entries.first { it.id == ids[0] }.icon)
            assertEquals(2, repository.read().entries.size)
            assertEquals("Choosing a backup icon must not rewrite or repin desktop copies", systemTimestamps,
                manager.pinnedShortcuts.associate { it.id to it.lastChangedTimestamp })
            File(context.filesDir, "legacy-shortcut-config.zip").outputStream().use(repository::export)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun clickAdd(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase().orEmpty()
        if (node.isClickable && text in setOf("add", "add automatically", "add to home screen")) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (index in 0 until node.childCount) if (node.getChild(index)?.let(::clickAdd) == true) return true
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
