package org.autojs.autojs.core.plugin.center

import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Uses installed APK resources and the production Plugin Center resolver; no app data is changed. */
class PluginInstalledThreeIconTest {
    @Test fun installedThreeIconsAreTransparentAndFollowTheHostTheme() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("autojs.three.icons") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packages = listOf(
            "io.github.supermonster003.autojs6.plugin.threestoneai",
            "io.github.supermonster003.autojs6.plugin.audioplayer",
            "io.github.supermonster003.autojs6.plugin.videoplayer",
            "io.github.supermonster003.autojs6.plugin.three.stove.agent",
            "io.github.supermonster003.autojs6.plugin.three.adapt.a11y",
        )
        for (packageName in packages) {
            val app = context.packageManager.getApplicationInfo(packageName, 0)
            val icon = checkNotNull(PluginInstalledIconResolver.load(context, app))
            for (night in listOf(false, true)) {
                val bitmap = PluginInstalledIconResolver.forNightMode(icon, night).toBitmap(128, 128)
                val pixels = IntArray(128 * 128)
                bitmap.getPixels(pixels, 0, 128, 0, 0, 128, 128)
                val expected = if (night) 0xD8 else 0x27
                val label = "$packageName night=$night"
                assertEquals(label, 0, Color.alpha(pixels.first()))
                assertTrue(label, pixels.count { Color.alpha(it) == 0 } > pixels.size / 2)
                val opaque = pixels.filter { Color.alpha(it) == 255 }
                assertTrue(label, opaque.isNotEmpty())
                assertTrue(label, opaque.all { Color.red(it) == expected && Color.green(it) == expected && Color.blue(it) == expected })
            }
        }
    }
}
