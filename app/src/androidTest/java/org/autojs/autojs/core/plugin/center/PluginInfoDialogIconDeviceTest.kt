package org.autojs.autojs.core.plugin.center

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bumptech.glide.Glide
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PluginInfoDialogIconDeviceTest {
    @Test
    fun detailsKeepBackgroundSeparateFromDisabledArtworkInBothThemes() = withAppearanceRestored {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val glyph = glyph()
                try {
                    for (night in listOf(false, true)) {
                        ViewUtils.isNightModeEnabled = night
                        for ((installed, enabled) in listOf(false to false, true to false, true to true)) {
                            val item = PluginCenterItem(
                                title = "Dialog icon fixture", packageName = "example.dialog.plugin", versionName = "1",
                                isInstalled = installed, isEnabled = enabled,
                                icon = BitmapDrawable(activity.resources, glyph),
                                iconBackgroundColor = "#e4b2ff", nightIconBackgroundColor = "#123456",
                            )
                            val dialog = PluginInfoDialogManager.showPluginInfoDialog(activity, item).dialog
                            try {
                                val snapshot = snapshot(dialog.iconView)
                                assertEquals(if (night) Color.rgb(18, 52, 86) else Color.rgb(228, 178, 255), snapshot.getPixel(8, 32))
                                assertEquals(Color.TRANSPARENT, snapshot.getPixel(0, 0))
                                assertNull("The circular background must not inherit the grayscale filter", dialog.iconView.background.colorFilter)
                                if (enabled) assertNull(dialog.iconView.colorFilter) else assertNotNull(dialog.iconView.colorFilter)
                                assertEquals("The source artwork remains transparent", Color.TRANSPARENT, glyph.getPixel(8, 32))
                                snapshot.recycle()
                            } finally {
                                dialog.dismiss()
                            }
                        }
                    }
                } finally {
                    glyph.recycle()
                }
            }
        }
    }

    @Test
    fun remoteArtworkKeepsCurrentBackgroundOnFirstLoadAndCacheReuse() = withAppearanceRestored {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val image = File(context.cacheDir, "dialog-background-${System.nanoTime()}.png")
        val glyph = glyph()
        image.outputStream().use { glyph.compress(Bitmap.CompressFormat.PNG, 100, it) }
        glyph.recycle()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                for ((night, background) in listOf(false to "#e4b2ff", true to "#123456", false to "transparent")) {
                    lateinit var icon: ImageView
                    lateinit var dialog: com.afollestad.materialdialogs.MaterialDialog
                    scenario.onActivity { activity ->
                        ViewUtils.isNightModeEnabled = night
                        dialog = PluginInfoDialogManager.showPluginInfoDialog(activity, PluginCenterItem(
                            title = "Remote dialog fixture", packageName = "example.remote.dialog.plugin", versionName = "1",
                            isInstalled = false, isEnabled = true, remoteIconUrl = image.toURI().toString(),
                            iconBackgroundColor = background, nightIconBackgroundColor = background,
                        )).dialog
                        icon = dialog.iconView
                    }
                    try {
                        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
                        var loaded = false
                        while (!loaded && android.os.SystemClock.uptimeMillis() < deadline) {
                            instrumentation.runOnMainSync {
                                val bitmap = icon.drawable.toBitmap(64, 64)
                                loaded = bitmap.getPixel(32, 32) == Color.RED
                            }
                            if (!loaded) android.os.SystemClock.sleep(50)
                        }
                        assertTrue("Remote fixture must finish loading", loaded)
                        instrumentation.runOnMainSync {
                            val bitmap = snapshot(icon)
                            assertEquals(if (background == "transparent") Color.TRANSPARENT else Color.parseColor(background), bitmap.getPixel(8, 32))
                            assertEquals(Color.RED, bitmap.getPixel(32, 32))
                            bitmap.recycle()
                            Glide.with(icon).clear(icon)
                        }
                    } finally {
                        instrumentation.runOnMainSync { dialog.dismiss() }
                    }
                }
            }
        } finally {
            image.delete()
        }
    }

    private fun glyph(): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).drawRect(24f, 24f, 40f, 40f, Paint().apply { color = Color.RED })
    }

    private fun snapshot(icon: ImageView): Bitmap {
        val exact = View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY)
        icon.measure(exact, exact)
        icon.layout(0, 0, 64, 64)
        return Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { icon.draw(Canvas(it)) }
    }

    private fun withAppearanceRestored(test: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preferences = Pref.get()
        val keys = listOf(R.string.key_night_mode, R.string.key_night_mode_enabled, R.string.key_auto_night_mode_enabled)
            .map(instrumentation.targetContext::getString)
        val original = keys.associateWith { preferences.all[it] }
        val originalMode = AppCompatDelegate.getDefaultNightMode()
        try {
            instrumentation.runOnMainSync { ViewUtils.isAutoNightModeEnabled = false }
            test()
        } finally {
            instrumentation.runOnMainSync {
                val editor = preferences.edit()
                for ((key, value) in original) {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is String -> editor.putString(key, value)
                        null -> editor.remove(key)
                        else -> error("Unexpected appearance preference type")
                    }
                }
                assertTrue("Appearance preferences must be restored", editor.commit())
                AppCompatDelegate.setDefaultNightMode(originalMode)
            }
        }
    }
}
