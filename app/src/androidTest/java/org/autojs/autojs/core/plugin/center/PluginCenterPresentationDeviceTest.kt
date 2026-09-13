package org.autojs.autojs.core.plugin.center

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class PluginCenterPresentationDeviceTest {

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
