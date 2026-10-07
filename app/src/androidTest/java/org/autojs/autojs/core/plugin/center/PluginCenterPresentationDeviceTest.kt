package org.autojs.autojs.core.plugin.center

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCenterPresentationDeviceTest {

    @Test fun circularBackgroundIsSeparateFromArtworkAndResetsWhenRecycled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalNight = ViewUtils.isNightModeEnabled
        val originalAutoNight = ViewUtils.isAutoNightModeEnabled
        try {
            instrumentation.runOnMainSync {
                ViewUtils.isAutoNightModeEnabled = false
                val themed = ContextThemeWrapper(context, context.applicationInfo.theme)
                val adapter = PluginCenterItemAdapter(object : PluginCenterItemAdapter.Listener {
                    override fun onInstall(item: PluginCenterItem) = Unit
                    override fun onToggleEnable(item: PluginCenterItem, enabled: Boolean) = Unit
                    override fun onUninstall(item: PluginCenterItem) = Unit
                    override fun onDetails(item: PluginCenterItem) = Unit
                    override fun onUpdate(item: PluginCenterItem) = Unit
                    override fun onSettings(item: PluginCenterItem) = Unit
                })
                val holder = adapter.onCreateViewHolder(FrameLayout(themed), 0)
                val glyph = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                    android.graphics.Canvas(this).drawRect(24f, 24f, 40f, 40f, android.graphics.Paint().apply { color = Color.BLACK })
                }
                val art = BitmapDrawable(context.resources, glyph)
                val item = PluginCenterItem(title = "Background fixture", packageName = "example.plugin", versionName = "1",
                    isInstalled = false, icon = art, iconBackgroundColor = "#e4b2ff", nightIconBackgroundColor = "transparent")
                val icon = holder.itemView.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.icon)
                fun draw(): Bitmap {
                    icon.layout(0, 0, 64, 64)
                    return Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { icon.draw(android.graphics.Canvas(it)) }
                }
                try {
                    for (night in listOf(false, true, false)) {
                        ViewUtils.isNightModeEnabled = night
                        holder.bind(item)
                        val before = draw()
                        assertEquals(if (night) Color.TRANSPARENT else Color.rgb(228, 178, 255), before.getPixel(8, 32))
                        assertEquals(Color.TRANSPARENT, before.getPixel(0, 0))
                        assertEquals(Color.BLACK, before.getPixel(32, 32))
                        holder.bind(item.copy(isInstalled = true))
                        val after = draw()
                        assertTrue("Installation must not change circular artwork", before.sameAs(after))
                        before.recycle(); after.recycle()
                    }
                    holder.bind(item.copy(packageName = "another.plugin", iconBackgroundColor = null, nightIconBackgroundColor = null))
                    assertEquals(Color.TRANSPARENT, icon.circleBackgroundColor)
                    holder.bind(item.copy(packageName = "io.github.supermonster003.autojs6.plugin.three.maple.image"))
                    assertEquals(Color.rgb(250, 250, 250), icon.circleBackgroundColor)
                    assertEquals(Color.TRANSPARENT, glyph.getPixel(8, 32))
                } finally { holder.recycle(); glyph.recycle() }
            }
        } finally {
            instrumentation.runOnMainSync {
                Pref.putBooleanSync(R.string.key_night_mode_enabled, originalNight)
                Pref.putBooleanSync(R.string.key_auto_night_mode_enabled, originalAutoNight)
            }
        }
    }

    @Test
    fun retainedDayIconsUseNightArtworkOnTheFirstBind() {
        assertRetainedIconFollowsTheme(initialNight = false)
    }

    @Test
    fun retainedNightIconsUseDayArtworkOnTheFirstBind() {
        assertRetainedIconFollowsTheme(initialNight = true)
    }

    private fun assertRetainedIconFollowsTheme(initialNight: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val originalNight = ViewUtils.isNightModeEnabled
        val originalAutoNight = ViewUtils.isAutoNightModeEnabled
        val appInfo = ApplicationInfo(testContext.applicationInfo).apply {
            icon = testContext.resources.getIdentifier("plugin_center_themed_icon", "drawable", testContext.packageName)
        }
        assertTrue("The test APK must contain the themed icon fixture", appInfo.icon != 0)
        try {
            instrumentation.runOnMainSync {
                ViewUtils.isAutoNightModeEnabled = false
                ViewUtils.isNightModeEnabled = initialNight
            }
            // Discovery happens off the UI thread. The same snapshot survives the theme change.
            val retainedIcon = requireNotNull(PluginInstalledIconResolver.load(context, appInfo))
            val retainedItem = PluginCenterItem(
                title = "Themed icon fixture", packageName = appInfo.packageName, versionName = "1.0.0",
                isInstalled = true, icon = retainedIcon,
                iconSignature = PluginInstalledIconResolver.signatureOf(appInfo.packageName, 1L, 1L),
            )
            for (night in listOf(!initialNight, initialNight, !initialNight)) {
                instrumentation.runOnMainSync {
                    ViewUtils.isNightModeEnabled = night
                    val configuration = Configuration(context.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), context.applicationInfo.theme)
                    val adapter = PluginCenterItemAdapter(object : PluginCenterItemAdapter.Listener {
                        override fun onInstall(item: PluginCenterItem) = Unit
                        override fun onToggleEnable(item: PluginCenterItem, enabled: Boolean) = Unit
                        override fun onUninstall(item: PluginCenterItem) = Unit
                        override fun onDetails(item: PluginCenterItem) = Unit
                        override fun onUpdate(item: PluginCenterItem) = Unit
                        override fun onSettings(item: PluginCenterItem) = Unit
                    })
                    val holder = adapter.onCreateViewHolder(FrameLayout(themed), 0)
                    try {
                        holder.bind(retainedItem)
                        val displayed = holder.itemView.findViewById<ImageView>(R.id.icon).drawable.toBitmap(16, 16)
                        assertEquals("The first bind must use the current theme without waiting for rediscovery",
                            if (night) Color.BLUE else Color.RED, displayed.getPixel(8, 8))
                    } finally {
                        holder.recycle()
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                // Finish persisting before the instrumentation process can be terminated.
                Pref.putBooleanSync(R.string.key_night_mode_enabled, originalNight)
                Pref.putBooleanSync(R.string.key_auto_night_mode_enabled, originalAutoNight)
            }
        }
    }

    @Test
    fun versionSummariesNormalizeSeptemberOnAndroid() {
        for (date in listOf("Sep 13, 2026", "Sept 13, 2026", "September 13, 2026")) {
            val item = PluginCenterItem(
                title = "MCP Server", packageName = "org.autojs.fixture.plugin.icon",
                versionName = "1.0.0", versionCode = 27L, versionDate = date,
                updatableVersionName = "1.0.1", updatableVersionCode = 28L, updatableVersionDate = date,
                isInstalled = true,
            )
            assertEquals(date, "1.0.0 (27) | 2026-09-13", item.versionSummary)
            assertEquals(date, "1.0.1 (28) | 2026-09-13", item.updatableVersionSummary)
        }
    }

    @Test
    fun installedIconsReplaceRemoteFallbackAfterReinstall() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val themed = ContextThemeWrapper(context, context.applicationInfo.theme)
            val adapter = PluginCenterItemAdapter(object : PluginCenterItemAdapter.Listener {
                override fun onInstall(item: PluginCenterItem) = Unit
                override fun onToggleEnable(item: PluginCenterItem, enabled: Boolean) = Unit
                override fun onUninstall(item: PluginCenterItem) = Unit
                override fun onDetails(item: PluginCenterItem) = Unit
                override fun onUpdate(item: PluginCenterItem) = Unit
                override fun onSettings(item: PluginCenterItem) = Unit
            })
            val holder = adapter.onCreateViewHolder(FrameLayout(themed), 0)
            val packageName = "org.autojs.fixture.plugin.icon"
            val stale = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val installed = BitmapDrawable(context.resources,
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) })
            val updated = BitmapDrawable(context.resources,
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) })

            // Simulate a previously displayed online icon whose index entry is now absent.
            // zh-CN: 模拟之前显示过在线图标, 但当前索引已不含该插件的情况.
            val cacheField = PluginCenterItemViewHolder::class.java.getDeclaredField("remoteItemIconCache")
                .apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val cache = cacheField.get(null) as LruCache<String, Bitmap>
            val keys = listOf("package:day:$packageName", "package:night:$packageName")
            keys.forEach { cache.put(it, stale) }
            try {
                val before = PluginCenterItem(
                    title = "Icon fixture", packageName = packageName, versionName = "1.0.0",
                    versionCode = 27L, isInstalled = true, icon = installed, lastUpdateTime = 1L,
                    iconSignature = PluginInstalledIconResolver.signatureOf(packageName, 27L, 1L),
                )
                val iconView = holder.itemView.findViewById<ImageView>(R.id.icon)
                holder.bind(before)
                assertSame("The installed icon must win over an old remote fallback", installed, iconView.drawable)

                val after = before.copy(
                    icon = updated, lastUpdateTime = 2L,
                    iconSignature = PluginInstalledIconResolver.signatureOf(packageName, 27L, 2L),
                )
                assertFalse("A reinstall with the same version code must update the row",
                    PluginCenterItemSemantics.areContentsTheSame(before, after))
                holder.bind(after)
                assertSame("Rebinding must display the updated local icon", updated, iconView.drawable)
            } finally {
                holder.recycle()
                keys.forEach { cache.remove(it) }
            }
        }
    }
}
