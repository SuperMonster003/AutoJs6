package org.autojs.autojs.core.plugin.center

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.graphics.drawable.toBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Uses installed APK resources and the production Plugin Center resolver; no app data is changed. */
class PluginInstalledThreeIconTest {
    @Test fun installedThreeIconsAreTransparentAndFollowTheHostTheme() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("autojs.three.icons") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packages = listOf(
            "io.github.supermonster003.autojs6.plugin.threestoneai",
            "io.github.supermonster003.autojs6.plugin.audioplayer",
            "io.github.supermonster003.autojs6.plugin.three.amber.player",
            "io.github.supermonster003.autojs6.plugin.three.stove.agent",
            "io.github.supermonster003.autojs6.plugin.three.adapt.a11y",
            "io.github.supermonster003.autojs6.plugin.three.color.picker",
            "io.github.supermonster003.autojs6.plugin.three.setup.installer",
            "io.github.supermonster003.autojs6.plugin.three.shell.terminal",
            "io.github.supermonster003.autojs6.plugin.three.stamp.mail",
            "io.github.supermonster003.autojs6.plugin.three.hanzi.cc",
            "io.github.supermonster003.autojs6.plugin.r8compiler",
            "io.github.supermonster003.autojs6.plugin.three.folio.epub",
            "io.github.supermonster003.autojs6.plugin.three.maple.image",
            "io.github.supermonster003.autojs6.plugin.three.stack.archive",
        )
        val labels = listOf("3-Stone AI", "3-Terra Player", "3-Amber Player", "3-Stove Agent",
            "3-Adapt A11y", "3-Color Picker", "3-Setup Installer", "3-Shell Terminal",
            "3-Stamp Mail", "3-Hanzi CC", "R8 Compiler", "3-Folio EPUB", "3-Maple Image", "3-Stack Archive")
        val preview = Bitmap.createBitmap(1200, ((packages.size + 2) / 3) * 176, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview).apply { drawColor(0xffeeeeee.toInt()) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 19f }
        for ((index, packageName) in packages.withIndex()) {
            val app = context.packageManager.getApplicationInfo(packageName, 0)
            val packageContext = context.createPackageContext(packageName, 0)
            @Suppress("DiscouragedApi")
            val dedicatedId = packageContext.resources.getIdentifier("ic_plugin_center", "mipmap", packageName)
            assertNotEquals("Install the updated APK for $packageName", 0, dedicatedId)
            val icon = checkNotNull(PluginInstalledIconResolver.load(context, app))
            val x = (index % 3) * 400f + 16
            val y = (index / 3) * 176f
            canvas.drawText(labels[index], x, y + 28, text)
            canvas.drawText("Plugin Center           Launcher", x, y + 156, text)
            var dayAlpha: List<Int>? = null
            for ((modeIndex, night) in listOf(false, true).withIndex()) {
                val bitmap = PluginInstalledIconResolver.forNightMode(icon, night).toBitmap(128, 128)
                val pixels = IntArray(128 * 128)
                bitmap.getPixels(pixels, 0, 128, 0, 0, 128, 128)
                val label = "$packageName night=$night"
                assertEquals(label, 0, Color.alpha(pixels.first()))
                assertTrue(label, pixels.count { Color.alpha(it) == 0 } > pixels.size / 2)
                val alpha = pixels.map { Color.alpha(it) }
                if (night) assertEquals("$label shape", dayAlpha, alpha) else dayAlpha = alpha
                val opaque = pixels.filter { Color.alpha(it) == 255 }
                assertTrue(label, opaque.isNotEmpty())
                if (!packageName.endsWith(".r8compiler")) {
                    assertTrue(label, opaque.all { Color.red(it) == Color.green(it) && Color.green(it) == Color.blue(it) })
                    val mean = opaque.map { Color.red(it) }.average()
                    assertTrue(label, if (night) mean > 128 else mean < 128)
                }
                val paint = Paint().apply { this.color = if (night) 0xff212121.toInt() else 0xfffafafa.toInt() }
                val slotX = x + modeIndex * 86
                canvas.drawRect(slotX, y + 44, slotX + 80, y + 124, paint)
                // An explicit destination avoids Bitmap density scaling on high-density devices.
                canvas.drawBitmap(PluginInstalledIconResolver.forNightMode(icon, night).toBitmap(80, 80),
                    null, RectF(slotX, y + 44, slotX + 80, y + 124), null)
                if (packageName.endsWith(".r8compiler")) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(packageName)
                    assertTrue(context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS).isEmpty())
                    if (!night) canvas.drawText("No launcher entry", x + 192, y + 90, text)
                    continue
                }
                val alias = if (night) "AdaptiveDarkIconAlias" else "AdaptiveLightIconAlias"
                val info = context.packageManager.getActivityInfo(
                    android.content.ComponentName(packageName, "$packageName.launcher.$alias"),
                    PackageManager.MATCH_DISABLED_COMPONENTS)
                val config = Configuration(packageContext.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                }
                val themed = packageContext.createConfigurationContext(config)
                val launcher = themed.resources.getDrawable(info.icon, themed.theme).toBitmap(80, 80)
                val launcherX = x + 192 + modeIndex * 86
                canvas.drawBitmap(launcher, null, RectF(launcherX, y + 44, launcherX + 80, y + 124), null)
            }
        }
        if (InstrumentationRegistry.getArguments().getString("autojs.icons.preview") == "true") {
            File(context.cacheDir, "standalone-icons-preview.png").outputStream().use {
                preview.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        preview.recycle()
    }
}
