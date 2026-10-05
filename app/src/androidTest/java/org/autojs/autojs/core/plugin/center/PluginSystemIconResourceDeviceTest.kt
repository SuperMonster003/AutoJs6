package org.autojs.autojs.core.plugin.center

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Validates real installed APK resources without changing system or plugin preferences. */
@RunWith(AndroidJUnit4::class)
class PluginSystemIconResourceDeviceTest {
    @Test
    fun applicationIconsResolveThemedBackgroundsAndKeepCatalogArtworkSeparate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packages = requireNotNull(InstrumentationRegistry.getArguments().getString("systemIconPackages")) {
            "Pass the packages of the newly built plugins via systemIconPackages"
        }.split(',').filter(String::isNotBlank)
        assertTrue(packages.isNotEmpty())
        for (name in packages) {
            val packageContext = context.createPackageContext(name, 0)
            val application = context.packageManager.getApplicationInfo(name, 0)
            @Suppress("DiscouragedApi")
            val systemId = packageContext.resources.getIdentifier("ic_icon_studio_application", "mipmap", name)
            assertTrue("$name must contain the new system resource", systemId != 0)
            assertEquals("$name application wiring", systemId, application.icon)
            for (night in listOf(false, true)) {
                val configuration = Configuration(packageContext.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val themed = packageContext.createConfigurationContext(configuration)
                @Suppress("DiscouragedApi")
                val backgroundId = themed.resources.getIdentifier("ic_plugin_center_background", "color", name)
                val background = themed.getColor(backgroundId)
                val icon = requireNotNull(themed.getDrawable(systemId))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    assertTrue("$name night=$night must use an adaptive icon, not the legacy night PNG", icon is AdaptiveIconDrawable)
                    val adaptive = icon as AdaptiveIconDrawable
                    assertEquals("$name night=$night background", background, (adaptive.background as ColorDrawable).color)
                    assertTrue(adaptive.foreground is BitmapDrawable)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) assertNotNull(adaptive.monochrome)
                } else {
                    assertTrue(icon is BitmapDrawable)
                    val bitmap = (icon as BitmapDrawable).bitmap
                    assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
                }
                @Suppress("DiscouragedApi")
                val catalogId = themed.resources.getIdentifier("ic_plugin_center", "mipmap", name)
                assertTrue("$name keeps its dedicated catalog resource", catalogId != 0 && catalogId != systemId)
                assertTrue(themed.getDrawable(catalogId) is BitmapDrawable)
            }
        }
    }
}
